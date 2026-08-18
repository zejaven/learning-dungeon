import { useEffect, useRef, useState } from 'react';
import { saveBossAnswer, streamSse } from '@app/engine/api';
import { useAi } from '@app/engine/aiStore';
import { parseTextDelta } from '@app/engine/aiStream';
import { useOffline } from '@app/engine/offlineStore';
import { useStore } from '@app/engine/store';
import type { BossQuestion } from '@app/engine/traceTypes';
import { langsOf, tlStrict, ui, useLang, type Lang } from '@app/i18n';
import { MissingLanguage } from '@app/shell/MissingLanguage';
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
  // AI grading runs on the PC, so this one screen genuinely needs the network.
  const online = useOffline((s) => s.online);

  const qid = question.id;
  const stored = results[qid];

  const [draft, setDraft] = useState('');
  const [stream, setStream] = useState('');
  const [liveScore, setLiveScore] = useState<number | null>(null);
  const [busy, setBusyState] = useState(false);
  const [evalError, setEvalError] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  // Abort an in-flight evaluation when the form unmounts (navigation etc.).
  useEffect(() => () => abortRef.current?.abort(), []);

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

  // Strict: answering a question shown in another language would be graded
  // against a question the learner did not actually read.
  const questionText = tlStrict(question.text, lang);
  const displayedScore = busy ? liveScore : stored?.score ?? null;
  const passed = !busy && !!stored?.passed;
  const failed = !busy && stored != null && !stored.passed;

  async function evaluate() {
    // questionText is null when the topic lacks this language; the form renders
    // the empty state then, so there is nothing to grade.
    if (!draft.trim() || busy || questionText === null) return;
    const answer = draft;
    setStream('');
    setLiveScore(null);
    setBusy(true);
    setEvalError(false);
    let acc = '';
    let lastStatus = '';
    abortRef.current?.abort();
    const ctrl = new AbortController();
    abortRef.current = ctrl;
    try {
      await streamSse(
        '/api/assistant/evaluate',
        { topicId, question: questionText, answer, lang, provider },
        {
          signal: ctrl.signal,
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
      if ((e as Error).name === 'AbortError') return; // unmounted mid-stream
      setStream((prev) => prev + `\n[error] ${(e as Error).message}`);
      setEvalError(true);
      setBusy(false);
    }
  }

  if (questionText === null) {
    return (
      <div className="boss-question-form">
        <MissingLanguage available={langsOf(question.text)} />
      </div>
    );
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

      {!online && <div className="boss-verdict-pill fail">📴 {ui('offlineBossUnavailable', lang)}</div>}

      <div className="boss-form-actions">
        <button className="primary" onClick={evaluate} disabled={busy || !draft.trim() || !online}>
          {busy ? ui('evaluating', lang) : stored ? ui('reEvaluate', lang) : ui('submitAnswer', lang)}
        </button>
      </div>
    </div>
  );
}
