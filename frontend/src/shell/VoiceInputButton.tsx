import { useEffect, useRef, useState } from 'react';
import { fetchVoiceStatus, transcribeVoice, warmupVoice, type VoiceStatus } from '@app/engine/api';
import { ui, useLang } from '@app/i18n';

/** Long enough for any spoken answer; a forgotten recording stops by itself. */
const MAX_SECONDS = 10 * 60;

/** Asked once per page load: whether the PC has a working speech-to-text worker. */
let statusPromise: Promise<VoiceStatus | null> | null = null;
function voiceStatus(): Promise<VoiceStatus | null> {
  statusPromise ??= fetchVoiceStatus().catch(() => {
    statusPromise = null; // offline now; ask again next time
    return null;
  });
  return statusPromise;
}

type Phase = 'idle' | 'recording' | 'transcribing';

/**
 * Dictate an answer: records until Stop (pauses never end it), then the PC
 * transcribes the whole recording at once with Whisper, which punctuates by
 * meaning rather than by silence. The model follows the UI language.
 *
 * The microphone is only offered in a secure context (https via Tailscale, or
 * localhost); over plain http on the LAN the button explains why it is off.
 */
export function VoiceInputButton({
  question,
  disabled,
  onText,
}: {
  question: string;
  disabled: boolean;
  onText: (text: string) => void;
}) {
  const lang = useLang((s) => s.lang);
  const [available, setAvailable] = useState(false);
  const [phase, setPhase] = useState<Phase>('idle');
  const [seconds, setSeconds] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const recorder = useRef<MediaRecorder | null>(null);
  const timer = useRef<number | null>(null);
  const startedAt = useRef(0);

  useEffect(() => {
    let alive = true;
    void voiceStatus().then((s) => alive && setAvailable(!!s?.available));
    return () => {
      alive = false;
    };
  }, []);

  // Leaving the question mid-recording must release the microphone.
  useEffect(() => () => stopTracks(), []);

  if (!available) return null;

  const canRecord = typeof window !== 'undefined' && window.isSecureContext && !!navigator.mediaDevices?.getUserMedia;

  function stopTracks() {
    if (timer.current != null) window.clearInterval(timer.current);
    timer.current = null;
    const r = recorder.current;
    r?.stream.getTracks().forEach((t) => t.stop());
    if (r && r.state !== 'inactive') {
      r.onstop = null; // unmounting: nothing to transcribe into
      r.stop();
    }
    recorder.current = null;
  }

  async function start() {
    setError(null);
    let stream: MediaStream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch {
      setError(ui('voiceMicDenied', lang));
      return;
    }
    const chunks: Blob[] = [];
    const r = new MediaRecorder(stream);
    r.ondataavailable = (e) => {
      if (e.data.size > 0) chunks.push(e.data);
    };
    r.onstop = () => {
      stream.getTracks().forEach((t) => t.stop());
      const audio = new Blob(chunks, { type: r.mimeType || 'audio/webm' });
      void finish(audio);
    };
    recorder.current = r;
    r.start();
    warmupVoice(lang);
    startedAt.current = Date.now();
    setSeconds(0);
    setPhase('recording');
    timer.current = window.setInterval(() => {
      const elapsed = Math.floor((Date.now() - startedAt.current) / 1000);
      setSeconds(elapsed);
      if (elapsed >= MAX_SECONDS) stop();
    }, 1000);
  }

  function stop() {
    if (timer.current != null) window.clearInterval(timer.current);
    timer.current = null;
    const r = recorder.current;
    recorder.current = null;
    if (r && r.state !== 'inactive') r.stop(); // onstop hands the audio over
  }

  async function finish(audio: Blob) {
    if (audio.size === 0) {
      setPhase('idle');
      return;
    }
    setPhase('transcribing');
    try {
      const text = (await transcribeVoice(audio, lang, question)).trim();
      if (text) onText(text);
      else setError(ui('voiceNothingHeard', lang));
    } catch (e) {
      setError(`${ui('voiceFailed', lang)}: ${(e as Error).message}`);
    } finally {
      setPhase('idle');
    }
  }

  const clock = `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;

  return (
    <>
      {phase === 'recording' ? (
        <button className="voice-btn recording" onClick={stop}>
          ⏹ {ui('voiceStop', lang)} · {clock}
        </button>
      ) : (
        <button
          className="voice-btn"
          onClick={() => void start()}
          disabled={disabled || phase === 'transcribing' || !canRecord}
          title={canRecord ? ui('voiceHint', lang) : ui('voiceNeedsHttps', lang)}
        >
          {phase === 'transcribing' ? ui('voiceTranscribing', lang) : `🎤 ${ui('voiceStart', lang)}`}
        </button>
      )}
      {/* A title is invisible on a touch screen, so the reason is spelled out. */}
      {!canRecord && <span className="voice-note">{ui('voiceNeedsHttps', lang)}</span>}
      {error && <span className="voice-error">{error}</span>}
    </>
  );
}
