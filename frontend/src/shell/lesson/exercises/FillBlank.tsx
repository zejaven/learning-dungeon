import type { AnswerValue, FillBlankExercise } from '@app/engine/lessonTypes';
import { tl, ui, useLang } from '@app/i18n';

interface Props {
  exercise: FillBlankExercise;
  answer: AnswerValue | null;
  onChange: (answer: AnswerValue) => void;
  showResult: boolean;
  correct: boolean;
  /** Submits when the learner hits Enter with a non-empty answer. */
  onEnter: () => void;
}

/** The statement with its single `___` replaced by an inline text input. */
export function FillBlank({ exercise, answer, onChange, showResult, correct, onEnter }: Props) {
  const lang = useLang((s) => s.lang);
  const text = tl(exercise.text, lang);
  const [before, after] = text.split('___', 2);
  const value = answer?.kind === 'text' ? answer.value : '';

  return (
    <div className="ex-fill-blank">
      <span>{before}</span>
      <input
        type="text"
        className={showResult ? (correct ? 'correct' : 'wrong') : ''}
        placeholder={ui('typeAnswerPlaceholder', lang)}
        value={value}
        disabled={showResult}
        onChange={(e) => onChange({ kind: 'text', value: e.target.value })}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && value.trim()) onEnter();
        }}
      />
      <span>{after ?? ''}</span>
      {showResult && !correct && (
        <div className="ex-fill-answer">
          {ui('yourAnswer', lang)}: <code>{value}</code> → <code>{exercise.answers[lang]?.[0] ?? exercise.answers.en[0]}</code>
        </div>
      )}
    </div>
  );
}
