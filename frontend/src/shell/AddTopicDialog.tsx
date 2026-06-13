import { useEffect, useRef, useState } from 'react';
import { streamSse } from '@app/engine/api';
import { parseActivity } from '@app/engine/claudeStream';
import { useStore } from '@app/engine/store';
import { statusLabel, ui, useLang } from '@app/i18n';

export function AddTopicDialog({ onClose }: { onClose: () => void }) {
  const loadTopics = useStore((s) => s.loadTopics);
  const lang = useLang((s) => s.lang);
  const [question, setQuestion] = useState('');
  const [log, setLog] = useState<string[]>([]);
  const [status, setStatus] = useState<string>('');
  const [statusMessage, setStatusMessage] = useState<string>('');
  const [busy, setBusy] = useState(false);
  const logRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    logRef.current?.scrollTo({ top: logRef.current.scrollHeight });
  }, [log]);

  function append(line: string) {
    setLog((prev) => [...prev, line]);
  }

  async function generate() {
    if (!question.trim() || busy) return;
    setLog([]);
    setStatus('running');
    setStatusMessage('…');
    setBusy(true);
    try {
      await streamSse(
        '/api/topics/generate',
        { question },
        {
          onClaude: (line) => {
            const activity = parseActivity(line);
            if (activity) append(activity);
          },
          onStatus: (s, message) => {
            setStatus(s);
            setStatusMessage(message);
            if (s === 'done') {
              append(ui('genFinished', lang));
              loadTopics();
            } else if (s === 'error') {
              append(`[error] ${message}`);
            }
          },
          onDone: () => setBusy(false),
        },
      );
    } catch (e) {
      setStatus('error');
      append(`[error] ${(e as Error).message}`);
      setBusy(false);
    }
  }

  const done = status === 'done';

  return (
    <div className="dialog-backdrop" onClick={busy ? undefined : onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <div className="dialog-head">
          <h2>{ui('addTopicTitle', lang)}</h2>
          {status && (
            <span className={`status-pill ${status}`} title={statusMessage}>
              {statusLabel(lang, status)}
            </span>
          )}
          <button onClick={onClose} disabled={busy}>
            ✕
          </button>
        </div>
        <div className="dialog-body">
          <p style={{ marginTop: 0, color: 'var(--muted)', fontSize: 13 }}>
            {ui('addTopicDesc', lang)}
          </p>
          <textarea
            rows={4}
            placeholder={ui('addTopicPlaceholder', lang)}
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            disabled={busy}
          />
          {log.length > 0 && (
            <div className="stream" ref={logRef}>
              {log.join('\n')}
            </div>
          )}
        </div>
        <div className="dialog-foot">
          {done && (
            <button className="accent" onClick={() => window.location.reload()}>
              {ui('reloadNew', lang)}
            </button>
          )}
          <button onClick={onClose} disabled={busy}>
            {ui('close', lang)}
          </button>
          <button className="primary" onClick={generate} disabled={busy || !question.trim()}>
            {busy ? ui('generating', lang) : ui('generate', lang)}
          </button>
        </div>
      </div>
    </div>
  );
}
