import { useEffect, useMemo } from 'react';
import { shuffled } from '@app/engine/grading';
import type { AnswerValue, SortStepsExercise } from '@app/engine/lessonTypes';
import { tl, ui, useLang } from '@app/i18n';

interface Props {
  exercise: SortStepsExercise;
  answer: AnswerValue | null;
  onChange: (answer: AnswerValue) => void;
  showResult: boolean;
}

/** Reorder shuffled steps with up/down arrows (no drag-and-drop in v1). */
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

  function move(index: number, delta: number) {
    const next = [...order];
    const j = index + delta;
    if (j < 0 || j >= next.length) return;
    [next[index], next[j]] = [next[j], next[index]];
    onChange({ kind: 'order', ids: next });
  }

  return (
    <div className="ex-sort-steps">
      <div className="ex-wb-hint">{ui('sortStepsHint', lang)}</div>
      {order.map((id, i) => {
        const step = exercise.steps.find((s) => s.id === id);
        const correctHere = exercise.steps[i]?.id === id;
        let cls = 'ex-sort-row';
        if (showResult) cls += correctHere ? ' correct' : ' wrong';
        return (
          <div key={id} className={cls}>
            <span className="ex-sort-num">{i + 1}.</span>
            <span className="ex-sort-text">{tl(step?.text, lang)}</span>
            {!showResult && (
              <span className="ex-sort-arrows">
                <button disabled={i === 0} onClick={() => move(i, -1)}>↑</button>
                <button disabled={i === order.length - 1} onClick={() => move(i, 1)}>↓</button>
              </span>
            )}
          </div>
        );
      })}
    </div>
  );
}
