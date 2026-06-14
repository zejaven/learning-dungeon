import { useEffect, useState } from 'react';
import { streamSse } from '@app/engine/api';
import { parseTextDelta } from '@app/engine/claudeStream';
import { useStore } from '@app/engine/store';
import { questionLabel, statusLabel, tl, ui, useLang } from '@app/i18n';

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
  const lang = useLang((s) => s.lang);

  const questions = topic?.bossFight ?? [];
  const total = questions.length;

  const [index, setIndex] = useState(0);
  const [draft, setDraft] = useState('');
  const [stream, setStream] = useState('');
  const [liveScore, setLiveScore] = useState<number | null>(null);
  const [status, setStatus] = useState('');
  const [busy, setBusy] = useState(false);

  const stored = results[index];

  // When the question changes, show that question's saved answer + verdict.
  useEffect(() => {
    setDraft(stored?.answer ?? '');
    setStream(stored?.evaluation ?? '');
    setLiveScore(stored?.score ?? null);
    setStatus('');
    setBusy(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [index]);

  if (!topic || total === 0) return null;

  const topicId = topic.id;
  const question = tl(questions[index], lang);
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
    let acc = '';
    try {
      await streamSse(
        '/api/assistant/evaluate',
        { topicId, question, answer, lang },
        {
          onClaude: (line) => {
            const delta = parseTextDelta(line);
            if (!delta) return;
            acc += delta;
            setStream(stripScoreLine(acc));
            const s = parseScore(acc);
            if (s != null) setLiveScore(s);
          },
          onStatus: (s) => setStatus(s),
          onDone: () => {
            const score = parseScore(acc);
            setResult(index, {
              answer,
              evaluation: stripScoreLine(acc),
              score,
              passed: score != null && score >= PASS_SCORE,
            });
            setBusy(false);
          },
        },
      );
    } catch (e) {
      setStream((prev) => prev + `\n[error] ${(e as Error).message}`);
      setStatus('error');
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

          <div className="boss-question">{question}</div>

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

          {(passed || failed) && (
            <div className={`boss-verdict-pill ${passed ? 'pass' : 'fail'}`}>
              {passed ? `✅ ${ui('passed', lang)}` : `🔁 ${ui('needMore', lang)}`}
              {failed && <span className="boss-pass-hint"> — {ui('passHint', lang)}</span>}
            </div>
          )}

          {stream && (
            <>
              <div className="boss-verdict-title">{ui('examinerVerdict', lang)}</div>
              <div className="stream">{stream}</div>
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
