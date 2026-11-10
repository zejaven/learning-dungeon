"""Speech-to-text worker for the Boss Fight voice input.

A long-lived process the backend (voice/VoiceService) starts on demand and talks
to over stdin/stdout, one JSON object per line, so there is no port to guard:

  -> {"id": "1", "audio": "C:/.../rec.webm", "lang": "ru", "prompt": "HashMap, ..."}
  <- {"id": "1", "text": "..."}            or  {"id": "1", "error": "..."}
  -> {"id": "2", "cmd": "warmup", "lang": "en"}
  <- {"id": "2", "ok": true}

The whole recording is transcribed in one go: with silence cut out by VAD,
Whisper sees the speech as one stream and punctuates by meaning, so a long
pause in the middle of a thought does not end the sentence.

Only one model is held at a time (the one for the requested language), and the
process exits after --idle-minutes without requests to give the VRAM back: the
same GPU also serves the zeveon-transcriber bot. Logs go to stderr; stdout
carries replies only.

Needs faster-whisper (the zeveon-transcriber venv has it) and ffmpeg on PATH.
"""
import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
import threading
import time
from pathlib import Path

# Same filter as zeveon-transcriber: what Whisper "hears" in silence and music.
HALLUCINATION = re.compile(
    r"(?i)^\W*(?:"
    r"¡?suscr[ií]bete al canal|subscribe to (?:the|my|our) channel|thanks? (?:you )?for watching"
    r"|продолжение следует|спасибо за просмотр|подписывайтесь на (?:наш )?канал"
    r"|субтитры (?:сделал|создавал|подготовил).*|редактор субтитров.*|sous-titres?.*|.*amara\.org.*"
    r")\W*$")


def log(message: str) -> None:
    print(f"[voice] {message}", file=sys.stderr, flush=True)


def add_cuda_dll_dirs() -> None:
    """On Windows, make pip-installed NVIDIA runtime DLLs (cuBLAS, cuDNN) visible to CTranslate2."""
    if sys.platform != "win32":
        return
    import importlib.util
    for pkg in ("nvidia.cublas", "nvidia.cudnn", "nvidia.cuda_runtime"):
        spec = importlib.util.find_spec(pkg)
        if spec and spec.submodule_search_locations:
            bin_dir = Path(list(spec.submodule_search_locations)[0]) / "bin"
            if bin_dir.is_dir():
                os.add_dll_directory(str(bin_dir))
                os.environ["PATH"] = f"{bin_dir};{os.environ['PATH']}"


def read_audio(path: str):
    """Any recording (webm/opus, mp4, wav) as 16 kHz mono float32 samples.

    Decoded by ffmpeg to a 16 kHz s16 WAV and read with `wave`, bypassing
    faster_whisper.decode_audio, whose PyAV call breaks on newer PyAV releases.
    """
    import wave

    import numpy as np

    fd, wav = tempfile.mkstemp(suffix=".wav")
    os.close(fd)
    try:
        subprocess.run(
            ["ffmpeg", "-nostdin", "-loglevel", "error", "-y", "-i", path,
             "-vn", "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le", wav],
            check=True, capture_output=True)
        with wave.open(wav, "rb") as w:
            pcm = w.readframes(w.getnframes())
        return np.frombuffer(pcm, dtype=np.int16).astype(np.float32) / 32768.0
    except subprocess.CalledProcessError as e:
        raise RuntimeError("ffmpeg could not read the recording: "
                           + e.stderr.decode("utf-8", "replace").strip()[-300:]) from e
    finally:
        try:
            os.remove(wav)
        except OSError:
            pass


class Models:
    """Holds the model for one language at a time."""

    def __init__(self, names: dict, device: str, compute_type: str):
        self.names = names
        self.device = device
        self.compute_type = compute_type
        self.lang = None
        self.model = None

    def get(self, lang: str):
        lang = lang if lang in self.names else "en"
        if self.lang == lang:
            return self.model, lang
        from faster_whisper import WhisperModel

        self.model = None  # let the previous model go before the next one claims the VRAM
        name = self.names[lang]
        started = time.time()
        try:
            self.model = WhisperModel(name, device=self.device, compute_type=self.compute_type)
            log(f"loaded {name} on {self.device} ({self.compute_type}) in {time.time() - started:.1f}s")
        except Exception as e:  # e.g. CUDA out of memory while the transcriber bot holds the GPU
            if self.device == "cpu":
                raise
            log(f"{self.device} failed ({e}); falling back to cpu/int8, which is much slower")
            self.model = WhisperModel(name, device="cpu", compute_type="int8")
            log(f"loaded {name} on cpu (int8) in {time.time() - started:.1f}s")
        self.lang = lang
        return self.model, lang


def transcribe(models: Models, audio: str, lang: str, prompt: str) -> str:
    model, lang = models.get(lang)
    samples = read_audio(audio)
    started = time.time()
    segments, _ = model.transcribe(
        samples,
        language=lang,
        beam_size=5,
        vad_filter=True,                  # silence is cut out, so a pause does not split the text
        condition_on_previous_text=True,  # one answer: keep punctuation flowing across windows
        initial_prompt=prompt or None,    # the question's terms: English words inside Russian speech
    )
    parts = [s.text.strip() for s in segments if s.text.strip() and not HALLUCINATION.match(s.text)]
    text = " ".join(parts)
    log(f"{lang}: {len(samples) / 16000:.1f}s of audio -> {len(text)} chars in {time.time() - started:.1f}s")
    return text


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-ru", required=True)
    parser.add_argument("--model-en", required=True)
    parser.add_argument("--device", default="cuda")
    parser.add_argument("--compute-type", default="int8_float16")
    parser.add_argument("--idle-minutes", type=float, default=10)
    args = parser.parse_args()

    # Replies are JSON with Cyrillic text: never let Windows pick a codepage.
    sys.stdin.reconfigure(encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8")
    add_cuda_dll_dirs()
    models = Models({"ru": args.model_ru, "en": args.model_en}, args.device, args.compute_type)

    last_request = [time.time()]
    busy = threading.Event()

    def watch_idle() -> None:
        while True:
            time.sleep(5)
            if not busy.is_set() and time.time() - last_request[0] > args.idle_minutes * 60:
                log(f"idle for {args.idle_minutes:g} min, exiting to free the GPU")
                os._exit(0)

    threading.Thread(target=watch_idle, daemon=True).start()
    log("ready")

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        busy.set()
        reply = {}
        try:
            request = json.loads(line)
            reply["id"] = request.get("id")
            if request.get("cmd") == "warmup":
                models.get(request.get("lang", "en"))
                reply["ok"] = True
            else:
                reply["text"] = transcribe(models, request["audio"], request.get("lang", "en"),
                                           request.get("prompt", ""))
        except Exception as e:
            log(f"request failed: {e!r}")
            reply["error"] = str(e) or e.__class__.__name__
        finally:
            last_request[0] = time.time()
            busy.clear()
        print(json.dumps(reply, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
