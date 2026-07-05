import { useState } from 'react';
import { useStore } from '@app/engine/store';
import { questionLabel, tl, ui, useLang } from '@app/i18n';
import { BossQuestionForm } from './BossQuestionForm';

/**
 * Boss Fight modal: one AI-graded question at a time with locked progression
 * (the next question unlocks after scoring PASS_SCORE or higher). The grading
 * form itself is shared with the lesson's inline boss units
 * (see BossQuestionForm).
 */
export function BossFightDialog({ onClose }: { onClose: () => void }) {
  const topic = useStore((s) => s.topic);
  const results = useStore((s) => s.bossFightResults);
  const lang = useLang((s) => s.lang);

  const questions = topic?.bossFight ?? [];
  const total = questions.length;

  const [index, setIndex] = useState(0);
  const [busy, setBusy] = useState(false);

  if (!topic || total === 0) return null;

  const currentQuestion = questions[index];
  const stored = results[currentQuestion.id];
  const canNext = index < total - 1 && !!stored?.passed;
  const canPrev = index > 0;

  return (
    <div className="dialog-backdrop" onClick={busy ? undefined : onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <div className="dialog-head">
          <h2>
            {ui('bossFightTitle', lang)}
            {tl(topic.title, lang)}
          </h2>
          <button onClick={onClose} disabled={busy}>
            ✕
          </button>
        </div>

        <div className="dialog-body">
          <div className="boss-qhead">
            <span className="boss-qnum">{questionLabel(lang, index + 1, total)}</span>
          </div>
          <BossQuestionForm topicId={topic.id} question={currentQuestion} onBusyChange={setBusy} />
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
        </div>
      </div>
    </div>
  );
}
