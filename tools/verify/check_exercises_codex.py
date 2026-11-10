"""Stage two of verifying lesson exercises: an independent check by Codex.

Reads the run.json written by ExerciseRunCheck (stage one) and asks Codex,
one topic per call, to answer every exercise BLIND: it sees the question, the
code, the options WITHOUT their `correct` flags and, where the snippet ran, the
real output of the wrapped program (evidence, not the answer key). It must
reason first and only then pick an option, so its pick is its own answer.
Its pick is then compared with the option the lesson marks correct.

  python tools/verify/check_exercises_codex.py backend/build/exercise-verify/collections-streams

Writes codex.json and report.md next to run.json. A topic already present in
codex.json is not asked again (delete the file to start over), so an
interrupted run resumes where it stopped.
"""
import argparse
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

# A ChatGPT account accepts only the models it lists (~/.codex/models_cache.json);
# anything else, including a stale `model` in ~/.codex/config.toml, is refused
# with "not supported when using Codex with a ChatGPT account". --model overrides.
MODEL = "gpt-6.1-sol"
EFFORT = "high"
TIMEOUT_SECONDS = 20 * 60

INSTRUCTIONS = """You are checking multiple-choice exercises from a Java interview trainer.
For every exercise below, work it out YOURSELF first, then choose an option.

Exercise types:
- predict_output: what the snippet prints or does when run (as if wrapped in a
  main method with java.util.*, java.util.stream.* etc. imported). An option
  may describe an exception or a property instead of literal output.
- spot_bug: the snippet has one defect; pick the option that names it.

Some snippets are fragments: undeclared helpers (a User class, a `tasks` list)
are as the question describes them. Where "actual_run" is given, it is the
real output of that exact code wrapped in a main on Java 21: trust it as
evidence. "EXCEPTION X" means the program died with X; "TIMEOUT" means it did
not finish within 10 seconds.

For each exercise reply with:
- "id": the exercise id
- "own_answer": your own answer in one or two sentences, BEFORE looking at
  which option fits
- "pick": the id of the single correct option, or "none" if no option is
  correct, or "several" if more than one is defensibly correct
- "env_dependent": true if the right answer depends on the JVM, hardware,
  timing or unspecified iteration order rather than on the code alone
- "reason": one or two sentences on why the picked option is right and the
  others are not; if "none" or "several", say exactly what is wrong

Reply with ONLY a JSON array of these objects, no prose, no code fences.
Do not read or modify any files; everything you need is below.

EXERCISES:
"""


def codex_command(model: str) -> list:
    exe = shutil.which("codex") or shutil.which("codex.cmd")
    if not exe:
        sys.exit("codex CLI not found on PATH")
    cmd = [exe, "-a", "never", "exec", "--json", "--ephemeral", "--sandbox", "read-only"]
    if model:
        cmd += ["--model", model]
    return cmd + ["-c", f'model_reasoning_effort="{EFFORT}"', "-"]


def ask_codex(prompt: str, cwd: Path, model: str) -> str:
    proc = subprocess.run(codex_command(model), input=prompt.encode("utf-8"), capture_output=True,
                          cwd=str(cwd), timeout=TIMEOUT_SECONDS)
    text = None
    errors = []
    for line in proc.stdout.decode("utf-8", "replace").splitlines():
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            continue
        item = event.get("item") or {}
        if event.get("type") == "item.completed" and item.get("type") == "agent_message":
            text = item.get("text")
        elif event.get("type") in ("turn.failed", "error"):
            errors.append(str(event.get("message") or event.get("error")))
    if text is None:
        tail = proc.stderr.decode("utf-8", "replace")[-800:]
        raise RuntimeError(f"no answer from codex (exit {proc.returncode}): {errors} {tail}")
    return text


def parse_answers(text: str) -> list:
    cleaned = re.sub(r"^```(?:json)?\s*|\s*```$", "", text.strip())
    start, end = cleaned.find("["), cleaned.rfind("]")
    return json.loads(cleaned[start:end + 1])


def blind(ex: dict) -> dict:
    """The exercise as Codex sees it: no correct flags."""
    item = {
        "id": ex["id"],
        "type": ex["type"],
        "question": ex["prompt"],
        "code": ex["code"],
        "options": [{"id": o["id"], "text": o["text"]} for o in ex["options"]],
    }
    if ex.get("group") in ("runnable", "env-dependent") and "output" in ex:
        item["actual_run"] = ex["output"]
        if "output2" in ex:
            item["actual_run_second"] = ex["output2"]
    return item


def verdict(ex: dict, answer: dict) -> str:
    if answer is None:
        return "NO-ANSWER"
    if answer.get("env_dependent"):
        return "ENV"
    return "agree" if answer.get("pick") == ex["correctId"] else "DISAGREE"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("dir", type=Path, help="folder holding run.json from ExerciseRunCheck")
    parser.add_argument("--model", default=MODEL, help=f"Codex model (default {MODEL})")
    args = parser.parse_args()
    run = json.loads((args.dir / "run.json").read_text(encoding="utf-8"))
    out_file = args.dir / "codex.json"
    answers = json.loads(out_file.read_text(encoding="utf-8")) if out_file.exists() else {}
    repo = Path(__file__).resolve().parents[2]

    by_topic = {}
    for ex in run["exercises"]:
        by_topic.setdefault(ex["topic"], []).append(ex)

    for topic, exercises in by_topic.items():
        if topic in answers:
            continue
        print(f"asking codex about {topic} ({len(exercises)} exercises)...", flush=True)
        prompt = INSTRUCTIONS + json.dumps([blind(e) for e in exercises], ensure_ascii=False, indent=1)
        try:
            parsed = parse_answers(ask_codex(prompt, repo, args.model))
        except Exception as e:  # keep what is done; a rerun resumes here
            print(f"  failed: {e}", flush=True)
            continue
        answers[topic] = {a["id"]: a for a in parsed}
        out_file.write_text(json.dumps(answers, ensure_ascii=False, indent=1), encoding="utf-8")
        agree = sum(1 for e in exercises if verdict(e, answers[topic].get(e["id"])) == "agree")
        print(f"  agrees on {agree}/{len(exercises)}", flush=True)

    write_report(args.dir, run, answers)


def write_report(folder: Path, run: dict, answers: dict) -> None:
    rows = []
    for ex in run["exercises"]:
        answer = answers.get(ex["topic"], {}).get(ex["id"])
        rows.append((ex, answer, verdict(ex, answer)))

    def count(pred):
        return sum(1 for r in rows if pred(r))

    lines = [f"# Exercise verification: {run['label']}", ""]
    lines.append(f"{len(rows)} exercises ({count(lambda r: r[0]['type'] == 'predict_output')} predict_output, "
                 f"{count(lambda r: r[0]['type'] == 'spot_bug')} spot_bug).")
    lines.append("")
    lines.append("| | count |")
    lines.append("|---|---|")
    for label, pred in [
        ("run: confirmed by the real output", lambda r: r[0].get("verdict") == "confirmed"),
        ("run: CONTRADICTS the marked option", lambda r: r[0].get("verdict") == "CONTRADICTS"),
        ("run: ran, nothing literal to compare", lambda r: r[0].get("group") == "runnable"
         and r[0].get("verdict") not in ("confirmed", "CONTRADICTS")),
        ("run: does not run as written (needs stubs)", lambda r: r[0].get("group") == "needs-stubs"),
        ("run: output differs between runs", lambda r: r[0].get("group") == "env-dependent"),
        ("codex: agrees", lambda r: r[2] == "agree"),
        ("codex: DISAGREES", lambda r: r[2] == "DISAGREE"),
        ("codex: environment-dependent", lambda r: r[2] == "ENV"),
        ("codex: no answer", lambda r: r[2] == "NO-ANSWER"),
    ]:
        lines.append(f"| {label} | {count(pred)} |")
    lines.append("")
    lines.append("## To review")
    lines.append("")
    for ex, answer, v in rows:
        if v == "agree" and ex.get("verdict") != "CONTRADICTS":
            continue
        lines.append(f"### {ex['topic']} / {ex['id']}  (run: {ex.get('verdict')}, codex: {v})")
        lines.append("")
        lines.append(f"{ex['prompt']}")
        lines.append("")
        lines.append("```java")
        lines.append(ex["code"])
        lines.append("```")
        for o in ex["options"]:
            mark = "✔" if o["correct"] else " "
            lines.append(f"- {mark} `{o['id']}` {o['text']}")
        if "output" in ex:
            lines.append("")
            lines.append(f"Actual run: `{ex['output'].replace(chr(10), ' / ')}`")
        if answer:
            lines.append("")
            lines.append(f"Codex: pick `{answer.get('pick')}`, env={answer.get('env_dependent')}. "
                         f"Own answer: {answer.get('own_answer')} Reason: {answer.get('reason')}")
        lines.append("")
    (folder / "report.md").write_text("\n".join(lines), encoding="utf-8")
    print(f"report -> {folder / 'report.md'}")


if __name__ == "__main__":
    main()
