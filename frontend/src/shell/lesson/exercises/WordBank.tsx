import { useMemo } from 'react';
import { shuffled } from '@app/engine/grading';
import type { AnswerValue, WordBankExercise } from '@app/engine/lessonTypes';
import { tlList, ui, useLang } from '@app/i18n';

interface Props {
  exercise: WordBankExercise;
  answer: AnswerValue | null;
  onChange: (answer: AnswerValue) => void;
  showResult: boolean;
  correct: boolean;
}

/**
 * Duolingo-style sentence assembly: tap chips to append them to the answer
 * line, tap an answer chip to put it back. Chips are indexed so duplicate
 * words stay distinguishable.
 */
export function WordBank({ exercise, answer, onChange, showResult, correct }: Props) {
  const lang = useLang((s) => s.lang);
  const pool = useMemo(() => {
    const tokens = tlList(exercise.tokens, lang);
    const distractors = tlList(exercise.distractors, lang);
    return shuffled([...tokens, ...distractors].map((word, i) => ({ key: `${i}:${word}`, word })));
  }, [exercise.id, lang]);

  const pickedKeys = answer?.kind === 'tokens' ? (answer as { keys?: string[] }).keys ?? [] : [];
  // Saved words, parallel to `pickedKeys`. On a revisit the saved keys come from
  // a previous shuffle and no longer resolve against this mount's pool, so we
  // fall back to these tokens by index to render the chip label.
  const answerTokens = answer?.kind === 'tokens' ? answer.tokens : [];
  // A restored answer (revisit) may carry tokens but no per-mount keys, so show
  // the saved tokens as static chips instead of an empty line.
  const restoredTokens = showResult && pickedKeys.length === 0 ? answerTokens : [];

  function setPicked(keys: string[]) {
    const words = keys.map((k) => pool.find((c) => c.key === k)?.word ?? '');
    // `keys` rides along for UI state; grading only reads `tokens`.
    onChange({ kind: 'tokens', tokens: words, keys } as AnswerValue);
  }

  return (
    <div className="ex-word-bank">
      <div className={`ex-wb-answer ${showResult ? (correct ? 'correct' : 'wrong') : ''}`}>
        {pickedKeys.length === 0 && restoredTokens.length === 0 && (
          <span className="ex-wb-hint">{ui('wordBankHint', lang)}</span>
        )}
        {restoredTokens.map((word, i) => (
          <span key={i} className="ex-chip picked">
            {word}
          </span>
        ))}
        {pickedKeys.map((key, i) => (
          <button
            key={key}
            className="ex-chip picked"
            disabled={showResult}
            onClick={() => setPicked(pickedKeys.filter((k) => k !== key))}
          >
            {pool.find((c) => c.key === key)?.word ?? answerTokens[i]}
          </button>
        ))}
      </div>
      <div className="ex-wb-pool">
        {pool.map((chip) => {
          const used = pickedKeys.includes(chip.key);
          return (
            <button
              key={chip.key}
              className={`ex-chip ${used ? 'used' : ''}`}
              disabled={showResult || used}
              onClick={() => setPicked([...pickedKeys, chip.key])}
            >
              {chip.word}
            </button>
          );
        })}
      </div>
      {showResult && !correct && (
        <div className="ex-fill-answer">
          → <code>{tlList(exercise.tokens, lang).join(' ')}</code>
        </div>
      )}
    </div>
  );
}
