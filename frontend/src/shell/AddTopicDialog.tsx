import { useState } from 'react';
import { useDomain } from '@app/engine/domainStore';
import { useGeneration } from '@app/engine/generationStore';
import { useStyle } from '@app/engine/styleStore';
import { domainById } from '@app/domains';
import { tl, ui, useLang } from '@app/i18n';
import { GenerationView } from './GenerationView';
import { LanguageSelect } from './LanguageSelect';
import { StyleSelector } from './StyleSelector';

/** Task key of the free-form run, per domain (the backend keys it the same way). */
export function addTopicKey(domainId: string): string {
  return `add-topic:${domainId}`;
}

/**
 * Free-form "Add topic" generation. The run is tracked server-side, so the dialog
 * can be closed and reopened mid-generation and will show the same continuing
 * stream (the Add-topic button reflects the running state). The topic is created
 * in the domain currently open, so every domain can grow its own topics.
 */
export function AddTopicDialog({ onClose }: { onClose: () => void }) {
  const lang = useLang((s) => s.lang);
  const domainId = useDomain((s) => s.domainId);
  const key = addTopicKey(domainId);
  const task = useGeneration((s) => s.tasks[key]);
  const start = useGeneration((s) => s.start);
  const [question, setQuestion] = useState('');

  const running = task?.status === 'running';
  const showForm = !task; // once a task exists, show its stream instead of the form
  const domain = domainById(domainId);

  function generate() {
    if (!question.trim() || running) return;
    start(key, {
      question,
      style: useStyle.getState().instruction(),
      styleName: useStyle.getState().currentName(),
      domainId,
    });
  }

  return (
    <div className="dialog-backdrop" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <div className="dialog-head">
          <h2>
            {ui('addTopicTitle', lang)}
            {domain && ` — ${domain.icon} ${tl(domain.title, lang)}`}
          </h2>
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
              <LanguageSelect inline />
            </>
          ) : (
            <GenerationView taskKey={key} />
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
