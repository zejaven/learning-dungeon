import { useEffect, useState } from 'react';
import {
  deleteAssistantQa,
  fetchAssistantHistory,
  saveAssistantQa,
  streamSse,
  type AssistantQa,
} from '@app/engine/api';
import { useAi } from '@app/engine/aiStore';
import { parseTextDelta } from '@app/engine/aiStream';
import { useStore } from '@app/engine/store';
import { questionLabel, statusLabel, tl, ui, useLang } from '@app/i18n';
import { Markdown } from './Markdown';

export function AssistantDialog({ onClose }: { onClose: () => void }) {
  const topic = useStore((s) => s.topic);
  // Theory topics have no editor; don't leak code from a previously opened practice topic.
  const code = useStore((s) => (s.topic?.mode === 'theory' ? '' : s.code));
  const provider = useAi((s) => s.selectedProvider);
  const lang = useLang((s) => s.lang);

  const [history, setHistory] = useState<AssistantQa[]>([]);
  // null = compose mode (a fresh, empty question). A number = the id of the
  // history entry being viewed read-only. Selection is keyed by id, not index,
  // so it stays valid across appends and deletes.
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [question, setQuestion] = useState('');
  const [answer, setAnswer] = useState('');
  const [status, setStatus] = useState('');
  const [busy, setBusy] = useState(false);

  const topicId = topic?.id;

  // Load this topic's question history once; always open on an empty composer.
  useEffect(() => {
    if (!topicId) return;
    fetchAssistantHistory(topicId)
      .then(setHistory)
      .catch(() => {
        /* best-effort: an unreachable history just shows an empty composer */
      });
  }, [topicId]);

  const total = history.length;
  const selectedIndex = selectedId == null ? -1 : history.findIndex((q) => q.id === selectedId);
  const viewing = selectedIndex >= 0 ? history[selectedIndex] : undefined;

  function compose() {
    if (busy) return;
    setSelectedId(null);
    setQuestion('');
    setAnswer('');
    setStatus('');
  }

  function select(id: number) {
    if (busy) return;
    setSelectedId(id);
    setStatus('');
  }

  const canPrev = !busy && (selectedIndex >= 0 ? selectedIndex > 0 : total > 0);
  const canNext = !busy && selectedIndex >= 0 && selectedIndex < total - 1;

  function prev() {
    if (!canPrev) return;
    // From the empty composer, step back into the most recent saved question.
    select(history[selectedIndex >= 0 ? selectedIndex - 1 : total - 1].id);
  }

  function next() {
    if (!canNext) return;
    select(history[selectedIndex + 1].id);
  }

  async function ask() {
    if (!question.trim() || busy || !topicId) return;
    const asked = question;
    setAnswer('');
    setStatus('running');
    setBusy(true);
    let acc = '';
    try {
      await streamSse(
        '/api/assistant/ask',
        { topicId, question: asked, code, lang, provider },
        {
          onAi: (line) => {
            const delta = parseTextDelta(line);
            if (delta) {
              acc += delta;
              setAnswer(acc);
            }
          },
          onStatus: (s) => setStatus(s),
          onDone: () => {
            setBusy(false);
            if (!acc.trim()) {
              setStatus('error');
              return;
            }
            // Persist, then move the just-answered Q&A into the read-only history.
            saveAssistantQa(topicId, { question: asked, answer: acc })
              .then((saved) => {
                setHistory((prev) => [...prev, saved]);
                setSelectedId(saved.id);
                setStatus('done');
              })
              .catch(() => {
                // Keep the answer on screen even if persistence failed.
                setStatus('error');
              });
          },
        },
      );
    } catch (e) {
      setStatus('error');
      setAnswer((prev) => prev + `\n[error] ${(e as Error).message}`);
      setBusy(false);
    }
  }

  async function remove() {
    if (busy || !topicId || !viewing) return;
    const id = viewing.id;
    const remaining = history.filter((q) => q.id !== id);
    // Move selection to a neighbouring entry, or back to the composer if empty.
    const fallback = remaining.length === 0
      ? null
      : remaining[Math.min(selectedIndex, remaining.length - 1)].id;
    setHistory(remaining);
    setSelectedId(fallback);
    if (fallback == null) {
      setQuestion('');
      setAnswer('');
    }
    deleteAssistantQa(topicId, id).catch(() => {
      /* best-effort; the row will reappear on next reload if the delete failed */
    });
  }

  if (!topic) return null;

  const shownAnswer = viewing ? viewing.answer : answer;
  const counter = viewing ? questionLabel(lang, selectedIndex + 1, total) : ui('newQuestion', lang);

  return (
    <div className="dialog-backdrop" onClick={busy ? undefined : onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <div className="dialog-head">
          <h2>
            {ui('askAbout', lang)}
            {tl(topic.title, lang)}
          </h2>
          {status && <span className={`status-pill ${status}`}>{statusLabel(lang, status)}</span>}
          <button onClick={onClose} disabled={busy}>
            ✕
          </button>
        </div>

        <div className="dialog-body">
          <div className="boss-qhead">
            <button onClick={compose} disabled={busy || !viewing}>
              {ui('askNew', lang)}
            </button>
            <span className="spacer" style={{ flex: 1 }} />
            <button onClick={prev} disabled={!canPrev}>
              {ui('prev', lang)}
            </button>
            <span className="boss-qnum">{counter}</span>
            <button onClick={next} disabled={!canNext}>
              {ui('next', lang)}
            </button>
          </div>

          {viewing ? (
            <div className="boss-question">{viewing.question}</div>
          ) : (
            <textarea
              rows={3}
              placeholder={tl(topic?.assistantExample, lang) || ui('assistantPlaceholder', lang)}
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) ask();
              }}
              disabled={busy}
            />
          )}

          {shownAnswer && <Markdown>{shownAnswer}</Markdown>}
        </div>

        <div className="dialog-foot">
          {viewing && (
            <button onClick={remove} disabled={busy}>
              {ui('deleteQa', lang)}
            </button>
          )}
          <span className="spacer" style={{ flex: 1 }} />
          <button onClick={onClose} disabled={busy}>
            {ui('close', lang)}
          </button>
          {!viewing && (
            <button className="primary" onClick={ask} disabled={busy || !question.trim()}>
              {busy ? ui('thinking', lang) : ui('ask', lang)}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
