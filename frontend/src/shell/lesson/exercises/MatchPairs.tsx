import { useMemo, useState } from 'react';
import { shuffled } from '@app/engine/grading';
import type { AnswerValue, MatchPairsExercise } from '@app/engine/lessonTypes';
import { tl, ui, useLang } from '@app/i18n';

interface Props {
  exercise: MatchPairsExercise;
  answer: AnswerValue | null;
  onChange: (answer: AnswerValue) => void;
  showResult: boolean;
}

/**
 * Select a left item, then its right match. Correct matches lock; a wrong
 * click counts as a mistake (any mistake fails the exercise, Duolingo-style).
 * The full mapping is reported once everything is matched.
 */
export function MatchPairs({ exercise, answer, onChange, showResult }: Props) {
  const lang = useLang((s) => s.lang);
  const rights = useMemo(() => shuffled(exercise.pairs.map((p) => p.id)), [exercise.id]);

  const [selectedLeft, setSelectedLeft] = useState<string | null>(null);
  // Seed from a restored answer (revisit) so a completed atom shows its matches
  // instead of an empty grid; ExerciseCard remounts per exercise (keyed by id),
  // so this initializer re-runs for each exercise.
  const [matched, setMatched] = useState<Record<string, string>>(
    answer?.kind === 'pairs' ? answer.matches : {},
  );
  const [mistakes, setMistakes] = useState(answer?.kind === 'pairs' ? answer.mistakes : 0);
  const [flashWrong, setFlashWrong] = useState<string | null>(null);

  const done = answer?.kind === 'pairs';

  function clickRight(rightId: string) {
    if (!selectedLeft || matched[selectedLeft] || done) return;
    if (rightId === selectedLeft) {
      const next = { ...matched, [selectedLeft]: rightId };
      setMatched(next);
      setSelectedLeft(null);
      if (Object.keys(next).length === exercise.pairs.length) {
        onChange({ kind: 'pairs', matches: next, mistakes });
      }
    } else {
      setMistakes((m) => m + 1);
      setFlashWrong(rightId);
      setTimeout(() => setFlashWrong(null), 400);
    }
  }

  return (
    <div className="ex-match-pairs">
      <div className="ex-wb-hint">{ui('matchPairsHint', lang)}</div>
      <div className="ex-mp-columns">
        <div className="ex-mp-col">
          {exercise.pairs.map((p) => {
            let cls = 'ex-option ex-mp-item';
            if (matched[p.id]) cls += ' matched';
            else if (selectedLeft === p.id) cls += ' selected';
            return (
              <button
                key={p.id}
                className={cls}
                disabled={showResult || !!matched[p.id] || done}
                onClick={() => setSelectedLeft(p.id)}
              >
                {tl(p.left, lang)}
              </button>
            );
          })}
        </div>
        <div className="ex-mp-col">
          {rights.map((id) => {
            const pair = exercise.pairs.find((p) => p.id === id)!;
            const isMatched = Object.values(matched).includes(id);
            let cls = 'ex-option ex-mp-item';
            if (isMatched) cls += ' matched';
            if (flashWrong === id) cls += ' wrong';
            return (
              <button
                key={id}
                className={cls}
                disabled={showResult || isMatched || done || !selectedLeft}
                onClick={() => clickRight(id)}
              >
                {tl(pair.right, lang)}
              </button>
            );
          })}
        </div>
      </div>
      {mistakes > 0 && !showResult && <div className="ex-mp-mistakes">✗ {mistakes}</div>}
    </div>
  );
}
