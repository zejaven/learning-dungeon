import { useEffect, useMemo, useState } from 'react';
import { shuffled } from '@app/engine/grading';
import type { AnswerValue, SortStepsExercise } from '@app/engine/lessonTypes';
import { tl, ui, useLang } from '@app/i18n';

interface Props {
  exercise: SortStepsExercise;
  answer: AnswerValue | null;
  onChange: (answer: AnswerValue) => void;
  showResult: boolean;
}

/**
 * Reorder shuffled steps by dragging rows — or with the per-row arrow buttons,
 * which are the only way that works on touch (HTML5 drag-and-drop does not fire
 * for touch input at all).
 */
export function SortSteps({ exercise, answer, onChange, showResult }: Props) {
  const lang = useLang((s) => s.lang);
  const initial = useMemo(
    () => shuffled(exercise.steps.map((s) => s.id)),
    [exercise.id],
  );
  const order = answer?.kind === 'order' ? answer.ids : initial;

  // The shuffled order IS an answer (the learner may submit it untouched), so
  // register it as the draft right away.
  useEffect(() => {
    if (answer == null) onChange({ kind: 'order', ids: initial });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [exercise.id]);

  const [dragIndex, setDragIndex] = useState<number | null>(null);
  const [overIndex, setOverIndex] = useState<number | null>(null);

  function reorder(from: number, to: number) {
    if (from === to) return;
    const next = [...order];
    const [moved] = next.splice(from, 1);
    next.splice(to, 0, moved);
    onChange({ kind: 'order', ids: next });
  }

  return (
    <div className="ex-sort-steps">
      <div className="ex-wb-hint">{ui('sortStepsHint', lang)}</div>
      {order.map((id, i) => {
        const step = exercise.steps.find((s) => s.id === id);
        const correctHere = exercise.steps[i]?.id === id;
        let cls = 'ex-sort-row';
        if (showResult) {
          cls += correctHere ? ' correct' : ' wrong';
        } else {
          cls += ' draggable';
          if (dragIndex === i) cls += ' dragging';
          if (overIndex === i && dragIndex !== null && dragIndex !== i) cls += ' drag-over';
        }
        return (
          <div
            key={id}
            className={cls}
            draggable={!showResult}
            onDragStart={(e) => {
              setDragIndex(i);
              e.dataTransfer.effectAllowed = 'move';
            }}
            onDragOver={(e) => {
              if (dragIndex === null) return;
              e.preventDefault();
              e.dataTransfer.dropEffect = 'move';
              if (overIndex !== i) setOverIndex(i);
            }}
            onDrop={(e) => {
              e.preventDefault();
              if (dragIndex !== null) reorder(dragIndex, i);
              setDragIndex(null);
              setOverIndex(null);
            }}
            onDragEnd={() => {
              setDragIndex(null);
              setOverIndex(null);
            }}
          >
            <span className="ex-sort-num">{i + 1}.</span>
            <span className="ex-sort-text">{tl(step?.text, lang)}</span>
            {!showResult && (
              <span className="ex-sort-move">
                <button
                  className="ex-sort-btn"
                  title={ui('moveUp', lang)}
                  disabled={i === 0}
                  onClick={() => reorder(i, i - 1)}
                >
                  ↑
                </button>
                <button
                  className="ex-sort-btn"
                  title={ui('moveDown', lang)}
                  disabled={i === order.length - 1}
                  onClick={() => reorder(i, i + 1)}
                >
                  ↓
                </button>
              </span>
            )}
            {!showResult && <span className="ex-sort-handle">⠿</span>}
          </div>
        );
      })}
    </div>
  );
}
