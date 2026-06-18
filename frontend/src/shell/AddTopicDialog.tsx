import { useState } from 'react';
import { useGeneration } from '@app/engine/generationStore';
import { useStyle } from '@app/engine/styleStore';
import { ui, useLang } from '@app/i18n';
import { GenerationView } from './GenerationView';
import { StyleSelector } from './StyleSelector';

const ADD_TOPIC_KEY = 'add-topic';

/**
 * Free-form "Add topic" generation. The run is tracked server-side, so the dialog
 * can be closed and reopened mid-generation and will show the same continuing
 * stream (the Add-topic button reflects the running state).
 */
export function AddTopicDialog({ onClose }: { onClose: () => void }) {
  const lang = useLang((s) => s.lang);
  const task = useGeneration((s) => s.tasks[ADD_TOPIC_KEY]);
  const start = useGeneration((s) => s.start);
  const [question, setQuestion] = useState('');

  const running = task?.status === 'running';
  const showForm = !task; // once a task exists, show its stream instead of the form

  function generate() {
    if (!question.trim() || running) return;
    start(ADD_TOPIC_KEY, { question, style: useStyle.getState().instruction() });
  }

  return (
    <div className="dialog-backdrop" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <div className="dialog-head">
          <h2>{ui('addTopicTitle', lang)}</h2>
          <button onClick={onClose}>✕</button>
        </div>
        <div className="dialog-body">
          {showForm ? (
            <>
              <p style={{ marginTop: 0, color: 'var(--muted)', fontSize: 13 }}>
                {ui('addTopicDesc', lang)}
              </p>
              <textarea
                rows={4}
                placeholder={ui('addTopicPlaceholder', lang)}
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
              />
              <StyleSelector />
            </>
          ) : (
            <GenerationView taskKey={ADD_TOPIC_KEY} />
          )}
        </div>
        <div className="dialog-foot">
          <button onClick={onClose}>{ui('close', lang)}</button>
          {showForm && (
            <button className="primary" onClick={generate} disabled={!question.trim()}>
              {ui('generate', lang)}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
