import { useState } from 'react';
import { navigate, routeForQuestion } from '@app/engine/router';
import { useStore } from '@app/engine/store';
import { ui, useLang } from '@app/i18n';

/**
 * Adds a question to the catalog by hand. On confirm, the AI classifies it
 * (category — existing or new — + difficulty) and translates it; the question is
 * stored and selected in the tree.
 */
export function AddQuestionDialog({ onClose }: { onClose: () => void }) {
  const lang = useLang((s) => s.lang);
  const addManualQuestion = useStore((s) => s.addManualQuestion);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function add() {
    if (!text.trim() || busy) return;
    setBusy(true);
    setError(null);
    try {
      const q = await addManualQuestion(text.trim());
      onClose();
      navigate(routeForQuestion(q.id));
    } catch (e) {
      setError((e as Error).message);
      setBusy(false);
    }
  }

  return (
    <div className="dialog-backdrop" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <div className="dialog-head">
          <h2>{ui('addQuestionTitle', lang)}</h2>
          <button onClick={onClose}>✕</button>
        </div>
        <div className="dialog-body">
          <p style={{ marginTop: 0, color: 'var(--muted)', fontSize: 13 }}>
            {ui('addQuestionDesc', lang)}
          </p>
          <textarea
            rows={4}
            placeholder={ui('addQuestionPlaceholder', lang)}
            value={text}
            onChange={(e) => setText(e.target.value)}
            disabled={busy}
          />
          {busy && <p className="home-hint">{ui('classifying', lang)}</p>}
          {error && <div className="output error">{error}</div>}
        </div>
        <div className="dialog-foot">
          <button onClick={onClose}>{ui('close', lang)}</button>
          <button className="primary" onClick={add} disabled={!text.trim() || busy}>
            {busy ? ui('classifying', lang) : ui('addQuestionConfirm', lang)}
          </button>
        </div>
      </div>
    </div>
  );
}
