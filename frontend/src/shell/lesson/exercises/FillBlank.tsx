import { useMemo } from 'react';
import { fillBlanks, textValues } from '@app/engine/grading';
import type { AnswerValue, FillBlankExercise } from '@app/engine/lessonTypes';
import { tl, ui, useLang } from '@app/i18n';

interface Props {
  exercise: FillBlankExercise;
  answer: AnswerValue | null;
  onChange: (answer: AnswerValue) => void;
  showResult: boolean;
  correct: boolean;
  /** Submits when the learner hits Enter with every blank filled. */
  onEnter: () => void;
}

/**
 * The statement with each `___` replaced by an inline text input. Supports one
 * or more blanks (multi-blank fills = "type each part of the answer").
 */
export function FillBlank({ exercise, answer, onChange, showResult, correct, onEnter }: Props) {
  const lang = useLang((s) => s.lang);
  const blanks = fillBlanks(exercise);
  const text = tl(exercise.text, lang);
  // N blanks split the text into N+1 segments.
  const segments = useMemo(() => text.split('___'), [text]);
  const values = answer && answer.kind === 'text' ? textValues(answer) : [];
  const filled = (i: number) => values[i] ?? '';

  function setValue(i: number, v: string) {
    const next = blanks.map((_, idx) => (idx === i ? v : filled(idx)));
    onChange({ kind: 'text', values: next });
  }

  const allFilled = blanks.length > 0 && blanks.every((_, i) => filled(i).trim() !== '');

  return (
    <div className="ex-fill-blank">
      {segments.map((seg, i) => (
        <span key={i} className="ex-fb-seg">
          <span>{seg}</span>
          {i < blanks.length && (
            <input
              type="text"
              className={showResult ? (correct ? 'correct' : 'wrong') : ''}
              placeholder={ui('typeAnswerPlaceholder', lang)}
              value={filled(i)}
              disabled={showResult}
              onChange={(e) => setValue(i, e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && allFilled) onEnter();
              }}
            />
          )}
        </span>
      ))}
      {showResult && !correct && (
        <div className="ex-fill-answer">
          {ui('yourAnswer', lang)}:{' '}
          {blanks.map((b, i) => (
            <span key={i}>
              {i > 0 && ', '}
              <code>{filled(i) || '∅'}</code> → <code>{b[lang]?.[0] ?? b.en?.[0] ?? b.ru?.[0]}</code>
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
