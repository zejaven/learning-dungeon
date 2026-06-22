import { useEffect, useState } from 'react';
import { saveBossAnswer, streamSse } from '@app/engine/api';
import { useAi } from '@app/engine/aiStore';
import { parseTextDelta } from '@app/engine/aiStream';
import { useStore } from '@app/engine/store';
import { questionLabel, statusLabel, tl, ui, useLang } from '@app/i18n';
import { Markdown } from './Markdown';

const PASS_SCORE = 6;

/** Extracts the `SCORE: <n>/10` integer the examiner is told to emit first. */
function parseScore(text: string): number | null {
  const m = text.match(/SCORE:\s*(\d+)\s*\/\s*10/i);
  if (!m) return null;
  const n = parseInt(m[1], 10);
  return Number.isFinite(n) ? Math.max(0, Math.min(10, n)) : null;
}

/** Hides the machine-readable score line so only the prose verdict shows. */
function stripScoreLine(text: string): string {
  const stripped = text.replace(/^\s*SCORE:\s*\d+\s*\/\s*10[ \t]*\r?\n?/i, '');
  return stripped === text ? text : stripped.replace(/^\s+/, '');
}

export function BossFightDialog({ onClose }: { onClose: () => void }) {
  const topic = useStore((s) => s.topic);
  const results = useStore((s) => s.bossFightResults);
  const setResult = useStore((s) => s.setBossFightResult);
  const markTopicCompleted = useStore((s) => s.markTopicCompleted);
  const alreadyCompleted = useStore((s) => s.topicCompleted);
  const provider = useAi((s) => s.selectedProvider);
  const lang = useLang((s) => s.lang);

  const questions = topic?.bossFight ?? [];
  const total = questions.length;

  const [index, setIndex] = useState(0);
  const [draft, setDraft] = useState('');
  const [stream, setStream] = useState('');
  const [liveScore, setLiveScore] = useState<number | null>(null);
  const [status, setStatus] = useState('');
  const [busy, setBusy] = useState(false);
  const [evalError, setEvalError] = useState(false);

  const currentQuestion = questions[index];
  const qid = currentQuestion?.id ?? '';
  const stored = qid ? results[qid] : undefined;

  // When the question changes, show that question's saved answer + verdict.
  // Questions are stable per topic, so an index change implies a qid change.
  useEffect(() => {
    setDraft(stored?.answer ?? '');
    setStream(stored?.evaluation ?? '');
    setLiveScore(stored?.score ?? null);
    setStatus('');
    setBusy(false);
    setEvalError(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [index]);

  if (!topic || total === 0) return null;

  const topicId = topic.id;
  const questionText = tl(currentQuestion.text, lang);
  const displayedScore = busy ? liveScore : stored?.score ?? null;
  const passed = !busy && !!stored?.passed;
  const failed = !busy && stored != null && !stored.passed;
  const canNext = index < total - 1 && !!stored?.passed;
  const canPrev = index > 0;

  async function evaluate() {
    if (!draft.trim() || busy) return;
    const answer = draft;
    setStream('');
    setLiveScore(null);
    setStatus('running');
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
            setStatus(s);
          },
          onDone: () => {
            setBusy(false);
            const score = parseScore(acc);
            const verdict = stripScoreLine(acc).trim();
            // A missing score (or an errored/empty run) means grading did not
            // actually happen. Surface it as a retryable failure instead of
            // silently recording a "fail" with no score and no feedback.
            if (lastStatus === 'error' || score == null) {
              setStatus('error');
              setEvalError(true);
              return;
            }
            const passed = score >= PASS_SCORE;
            setResult(qid, { answer, evaluation: verdict, score, passed });
            // Persist the answer (with full history) and pick up topic completion.
            const wasCompleted = alreadyCompleted;
            saveBossAnswer(topicId, {
              questionId: qid,
              questionText,
              answer,
              verdict,
              score,
              passed,
            })
              .then((res) => {
                if (res.topicCompleted && !wasCompleted) markTopicCompleted();
              })
              .catch(() => {
                /* persistence is best-effort */
              });
          },
        },
      );
    } catch (e) {
      setStream((prev) => prev + `\n[error] ${(e as Error).message}`);
      setStatus('error');
      setEvalError(true);
      setBusy(false);
    }
  }

  return (
    <div className="dialog-backdrop" onClick={busy ? undefined : onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <div className="dialog-head">
          <h2>
            {ui('bossFightTitle', lang)}
            {tl(topic.title, lang)}
          </h2>
          {status && <span className={`status-pill ${status}`}>{statusLabel(lang, status)}</span>}
          <button onClick={onClose} disabled={busy}>
            ✕
          </button>
        </div>

        <div className="dialog-body">
          <div className="boss-qhead">
            <span className="boss-qnum">{questionLabel(lang, index + 1, total)}</span>
            <ScoreBadge score={displayedScore} lang={lang} />
          </div>

          <div className="boss-question">{questionText}</div>

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

          {evalError && (
            <div className="boss-verdict-pill fail">⚠️ {ui('evaluateFailed', lang)}</div>
          )}

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
        </div>

        <div className="dialog-foot">
          <button onClick={() => setIndex((i) => i - 1)} disabled={busy || !canPrev}>
            {ui('prev', lang)}
          </button>
          <button
            onClick={() => setIndex((i) => i + 1)}
            disabled={busy || !canNext}
            title={!canNext && index < total - 1 ? ui('passHint', lang) : undefined}
          >
            {ui('next', lang)}
          </button>
          <span className="spacer" style={{ flex: 1 }} />
          <button onClick={onClose} disabled={busy}>
            {ui('close', lang)}
          </button>
          <button className="primary" onClick={evaluate} disabled={busy || !draft.trim()}>
            {busy ? ui('evaluating', lang) : stored ? ui('reEvaluate', lang) : ui('submitAnswer', lang)}
          </button>
        </div>
      </div>
    </div>
  );
}

function ScoreBadge({ score, lang }: { score: number | null; lang: 'en' | 'ru' }) {
  const tone = score == null ? 'none' : score >= PASS_SCORE ? 'pass' : 'fail';
  return (
    <span className={`boss-score ${tone}`}>
      {ui('score', lang)}: {score == null ? ui('notScored', lang) : `${score}/10`}
    </span>
  );
}
