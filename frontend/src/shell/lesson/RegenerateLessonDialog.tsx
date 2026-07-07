import { useState } from 'react';
import { ui, useLang } from '@app/i18n';

export interface RegenerateOptions {
  mode: 'full' | 'augment';
  comment: string;
}

/**
 * Confirmation dialog for regenerating a topic's micro-actions lesson. Lets the
 * learner choose between a full regeneration (replaces everything, resets
 * progress) and augmenting the existing lesson (default). A comment is required
 * when augmenting (what to add) and optional when regenerating fully (things the
 * lesson must cover — not the basis for the whole lesson).
 */
export function RegenerateLessonDialog({
  onConfirm,
  onClose,
}: {
  onConfirm: (options: RegenerateOptions) => void;
  onClose: () => void;
}) {
  const lang = useLang((s) => s.lang);
  const [full, setFull] = useState(false);
  const [comment, setComment] = useState('');

  const commentRequired = !full;
  const canConfirm = !commentRequired || comment.trim().length > 0;

  function confirm() {
    if (!canConfirm) return;
    onConfirm({ mode: full ? 'full' : 'augment', comment: comment.trim() });
  }

  return (
    <div className="dialog-backdrop" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <div className="dialog-head">
          <h2>{ui('regenerateTitle', lang)}</h2>
          <button onClick={onClose}>✕</button>
        </div>
        <div className="dialog-body">
          <label style={{ display: 'flex', gap: 8, alignItems: 'flex-start', cursor: 'pointer' }}>
            <input type="checkbox" checked={full} onChange={(e) => setFull(e.target.checked)} />
            <span>{ui('regenerateFullMode', lang)}</span>
          </label>

          <p style={{ color: 'var(--muted)', fontSize: 13 }}>
            {full ? ui('regenerateFullWarning', lang) : ui('regenerateAugmentHint', lang)}
          </p>

          <label style={{ display: 'block', fontSize: 13, marginBottom: 4 }}>
            {full ? ui('regenerateCommentFullLabel', lang) : ui('regenerateCommentAugmentLabel', lang)}
          </label>
          <textarea
            rows={4}
            placeholder={
              full
                ? ui('regenerateCommentFullPlaceholder', lang)
                : ui('regenerateCommentAugmentPlaceholder', lang)
            }
            value={comment}
            onChange={(e) => setComment(e.target.value)}
          />
          {full && (
            <p style={{ color: 'var(--muted)', fontSize: 12, marginTop: 4 }}>
              {ui('regenerateCommentFullHint', lang)}
            </p>
          )}
        </div>
        <div className="dialog-foot">
          <button onClick={onClose}>{ui('close', lang)}</button>
          <button className="primary" onClick={confirm} disabled={!canConfirm}>
            {ui('regenerateConfirm', lang)}
          </button>
        </div>
      </div>
    </div>
  );
}
