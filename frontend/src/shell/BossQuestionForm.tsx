import { useEffect, useState } from 'react';
import { saveBossAnswer, streamSse } from '@app/engine/api';
import { useAi } from '@app/engine/aiStore';
import { parseTextDelta } from '@app/engine/aiStream';
import { useStore } from '@app/engine/store';
import type { BossQuestion } from '@app/engine/traceTypes';
import { tl, ui, useLang, type Lang } from '@app/i18n';
import { Markdown } from './Markdown';

export const PASS_SCORE = 6;

/** Extracts the `SCORE: <n>/10` integer the examiner is told to emit first. */
export function parseScore(text: string): number | null {
  const m = text.match(/SCORE:\s*(\d+)\s*\/\s*10/i);
  if (!m) return null;
  const n = parseInt(m[1], 10);
  return Number.isFinite(n) ? Math.max(0, Math.min(10, n)) : null;
}

/** Hides the machine-readable score line so only the prose verdict shows. */
export function stripScoreLine(text: string): string {
  const stripped = text.replace(/^\s*SCORE:\s*\d+\s*\/\s*10[ \t]*\r?\n?/i, '');
  return stripped === text ? text : stripped.replace(/^\s+/, '');
}

export function ScoreBadge({ score, lang }: { score: number | null; lang: Lang }) {
  const tone = score == null ? 'none' : score >= PASS_SCORE ? 'pass' : 'fail';
  return (
    <span className={`boss-score ${tone}`}>
      {ui('score', lang)}: {score == null ? ui('notScored', lang) : `${score}/10`}
    </span>
  );
}

interface BossQuestionFormProps {
  topicId: string;
  question: BossQuestion;
  /** Fired after a passing answer has been persisted. */
  onPassed?: () => void;
  /** Lets the host disable its own chrome while an evaluation streams. */
  onBusyChange?: (busy: boolean) => void;
}

/**
 * One boss-fight question: free-text answer, AI-graded over SSE
 * (`/api/assistant/evaluate`, first line `SCORE: <n>/10`), persisted via
 * `saveBossAnswer`. Shared by the Boss Fight dialog and the lesson's inline
 * boss units so grading/persistence exist exactly once.
 */
export function BossQuestionForm({ topicId, question, onPassed, onBusyChange }: BossQuestionFormProps) {
  const results = useStore((s) => s.bossFightResults);
  const setResult = useStore((s) => s.setBossFightResult);
  const markTopicCompleted = useStore((s) => s.markTopicCompleted);
  const alreadyCompleted = useStore((s) => s.topicCompleted);
  const provider = useAi((s) => s.selectedProvider);
  const lang = useLang((s) => s.lang);

  const qid = question.id;
  const stored = results[qid];

  const [draft, setDraft] = useState('');
  const [stream, setStream] = useState('');
  const [liveScore, setLiveScore] = useState<number | null>(null);
  const [busy, setBusyState] = useState(false);
  const [evalError, setEvalError] = useState(false);

  // When the question changes, show that question's saved answer + verdict.
  useEffect(() => {
    setDraft(stored?.answer ?? '');
    setStream(stored?.evaluation ?? '');
    setLiveScore(stored?.score ?? null);
    setBusyState(false);
    setEvalError(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [qid]);

  function setBusy(value: boolean) {
    setBusyState(value);
    onBusyChange?.(value);
  }

  const questionText = tl(question.text, lang);
  const displayedScore = busy ? liveScore : stored?.score ?? null;
  const passed = !busy && !!stored?.passed;
  const failed = !busy && stored != null && !stored.passed;

  async function evaluate() {
    if (!draft.trim() || busy) return;
    const answer = draft;
    setStream('');
    setLiveScore(null);
    setBusy(true);
    setEvalError(false);
    let acc = '';
    let lastStatus = '';
    try {
      await streamSse(
        '/api/assistant/evaluate',
        { topicId, question: questionText, answer, lang, provider },
        {
          onAi: (line) => {
            const delta = parseTextDelta(line);
            if (!delta) return;
            acc += delta;
            setStream(stripScoreLine(acc));
            const s = parseScore(acc);
            if (s != null) setLiveScore(s);
          },
          onStatus: (s) => {
            lastStatus = s;
          },
          onDone: () => {
            setBusy(false);
            const score = parseScore(acc);
            const verdict = stripScoreLine(acc).trim();
            // A missing score (or an errored/empty run) means grading did not
            // actually happen. Surface it as a retryable failure instead of
            // silently recording a "fail" with no score and no feedback.
            if (lastStatus === 'error' || score == null) {
              setEvalError(true);
              return;
            }
            const isPassed = score >= PASS_SCORE;
            setResult(qid, { answer, evaluation: verdict, score, passed: isPassed });
            // Persist the answer (with full history) and pick up topic completion.
            const wasCompleted = alreadyCompleted;
            saveBossAnswer(topicId, {
              questionId: qid,
              questionText,
              answer,
              verdict,
              score,
              passed: isPassed,
            })
              .then((res) => {
                if (res.topicCompleted && !wasCompleted) markTopicCompleted();
                if (isPassed) onPassed?.();
              })
              .catch(() => {
                /* persistence is best-effort */
              });
          },
        },
      );
    } catch (e) {
      setStream((prev) => prev + `\n[error] ${(e as Error).message}`);
      setEvalError(true);
      setBusy(false);
    }
  }

  return (
    <div className="boss-question-form">
      <div className="boss-qhead">
        <span className="boss-question">{questionText}</span>
        <ScoreBadge score={displayedScore} lang={lang} />
      </div>

      <textarea
        rows={4}
        placeholder={ui('bossFightAnswerPlaceholder', lang)}
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) evaluate();
        }}
        disabled={busy}
      />

      {evalError && <div className="boss-verdict-pill fail">⚠️ {ui('evaluateFailed', lang)}</div>}

      {!evalError && (passed || failed) && (
        <div className={`boss-verdict-pill ${passed ? 'pass' : 'fail'}`}>
          {passed ? `✅ ${ui('passed', lang)}` : `🔁 ${ui('needMore', lang)}`}
          {failed && <span className="boss-pass-hint"> — {ui('passHint', lang)}</span>}
        </div>
      )}

      {stream && (
        <>
          <div className="boss-verdict-title">{ui('examinerVerdict', lang)}</div>
          <Markdown>{stream}</Markdown>
        </>
      )}

      <div className="boss-form-actions">
        <button className="primary" onClick={evaluate} disabled={busy || !draft.trim()}>
          {busy ? ui('evaluating', lang) : stored ? ui('reEvaluate', lang) : ui('submitAnswer', lang)}
        </button>
      </div>
    </div>
  );
}
