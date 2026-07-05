import { useMemo } from 'react';
import { shuffled } from '@app/engine/grading';
import type { AnswerValue, ChoiceExercise } from '@app/engine/lessonTypes';
import { tl, useLang } from '@app/i18n';

interface Props {
  exercise: ChoiceExercise;
  answer: AnswerValue | null;
  onChange: (answer: AnswerValue) => void;
  /** Feedback phase: inputs locked, correctness highlighted. */
  showResult: boolean;
}

/** Options list for multiple_choice / predict_output / spot_bug. */
export function MultipleChoice({ exercise, answer, onChange, showResult }: Props) {
  const lang = useLang((s) => s.lang);
  // Shuffle once per exercise so options don't jump while answering.
  const options = useMemo(() => shuffled(exercise.options), [exercise.id]);
  const chosenId = answer?.kind === 'option' ? answer.optionId : null;

  return (
    <div className="ex-options">
      {options.map((o) => {
        let cls = 'ex-option';
        if (showResult) {
          if (o.correct) cls += ' correct';
          else if (o.id === chosenId) cls += ' wrong';
        } else if (o.id === chosenId) {
          cls += ' selected';
        }
        return (
          <button
            key={o.id}
            className={cls}
            disabled={showResult}
            onClick={() => onChange({ kind: 'option', optionId: o.id })}
          >
            {tl(o.text, lang)}
          </button>
        );
      })}
    </div>
  );
}
