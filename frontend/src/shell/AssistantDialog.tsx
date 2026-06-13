import { useState } from 'react';
import { streamSse } from '@app/engine/api';
import { parseTextDelta } from '@app/engine/claudeStream';
import { useStore } from '@app/engine/store';

export function AssistantDialog({ onClose }: { onClose: () => void }) {
  const topic = useStore((s) => s.topic);
  const code = useStore((s) => s.code);
  const [question, setQuestion] = useState('');
  const [answer, setAnswer] = useState('');
  const [status, setStatus] = useState<string>('');
  const [busy, setBusy] = useState(false);

  async function ask() {
    if (!question.trim() || busy) return;
    setAnswer('');
    setStatus('running');
    setBusy(true);
    try {
      await streamSse(
        '/api/assistant/ask',
        { topicId: topic?.id, question, code },
        {
          onClaude: (line) => {
            const delta = parseTextDelta(line);
            if (delta) setAnswer((prev) => prev + delta);
          },
          onStatus: (s) => setStatus(s),
          onDone: () => setBusy(false),
        },
      );
    } catch (e) {
      setStatus('error');
      setAnswer((prev) => prev + `\n[error] ${(e as Error).message}`);
      setBusy(false);
    }
  }

  return (
    <div className="dialog-backdrop" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <div className="dialog-head">
          <h2>Ask AI about {topic?.title ?? 'this topic'}</h2>
          {status && <span className={`status-pill ${status}`}>{status}</span>}
          <button onClick={onClose}>✕</button>
        </div>
        <div className="dialog-body">
          <textarea
            rows={3}
            placeholder="e.g. Why does a mutable key break get()?"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) ask();
            }}
          />
          {answer && <div className="stream">{answer}</div>}
        </div>
        <div className="dialog-foot">
          <button onClick={onClose}>Close</button>
          <button className="primary" onClick={ask} disabled={busy || !question.trim()}>
            {busy ? 'Thinking…' : 'Ask (Ctrl+Enter)'}
          </button>
        </div>
      </div>
    </div>
  );
}
