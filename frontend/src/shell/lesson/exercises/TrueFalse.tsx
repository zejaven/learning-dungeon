import type { AnswerValue, TrueFalseExercise } from '@app/engine/lessonTypes';
import { ui, useLang } from '@app/i18n';

interface Props {
  exercise: TrueFalseExercise;
  answer: AnswerValue | null;
  onChange: (answer: AnswerValue) => void;
  showResult: boolean;
}

export function TrueFalse({ exercise, answer, onChange, showResult }: Props) {
  const lang = useLang((s) => s.lang);
  const chosen = answer?.kind === 'bool' ? answer.value : null;

  const btn = (value: boolean, label: string) => {
    let cls = 'ex-option ex-tf';
    if (showResult) {
      if (value === exercise.answer) cls += ' correct';
      else if (value === chosen) cls += ' wrong';
    } else if (value === chosen) {
      cls += ' selected';
    }
    return (
      <button className={cls} disabled={showResult} onClick={() => onChange({ kind: 'bool', value })}>
        {label}
      </button>
    );
  };

  return (
    <div className="ex-options ex-tf-row">
      {btn(true, ui('trueLabel', lang))}
      {btn(false, ui('falseLabel', lang))}
    </div>
  );
}
