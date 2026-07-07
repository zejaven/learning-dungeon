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
 * Select a left item, then its right match — freely reassignable (picking a
 * right item already used elsewhere moves it), so nothing reveals correctness
 * mid-exercise. Right/wrong for each pair only shows once Check is pressed.
 */
export function MatchPairs({ exercise, answer, onChange, showResult }: Props) {
  const lang = useLang((s) => s.lang);
  const rights = useMemo(() => shuffled(exercise.pairs.map((p) => p.id)), [exercise.id]);

  const [selectedLeft, setSelectedLeft] = useState<string | null>(null);
  const [matches, setMatches] = useState<Record<string, string>>(
    answer?.kind === 'pairs' ? answer.matches : {},
  );

  function assign(rightId: string) {
    if (!selectedLeft || showResult) return;
    const next = { ...matches };
    // A right item can only be claimed by one left at a time: free it from
    // whichever left held it before handing it to the newly selected one.
    for (const [leftId, r] of Object.entries(next)) {
      if (r === rightId && leftId !== selectedLeft) delete next[leftId];
    }
    next[selectedLeft] = rightId;
    setMatches(next);
    setSelectedLeft(null);
    onChange({ kind: 'pairs', matches: next });
  }

  return (
    <div className="ex-match-pairs">
      <div className="ex-wb-hint">{ui('matchPairsHint', lang)}</div>
      <div className="ex-mp-columns">
        <div className="ex-mp-col">
          {exercise.pairs.map((p) => {
            let cls = 'ex-option ex-mp-item';
            if (showResult) cls += matches[p.id] === p.id ? ' correct' : ' wrong';
            else {
              if (matches[p.id] != null) cls += ' matched';
              if (selectedLeft === p.id) cls += ' selected';
            }
            return (
              <button
                key={p.id}
                className={cls}
                disabled={showResult}
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
            const claimed = Object.values(matches).includes(id);
            let cls = 'ex-option ex-mp-item';
            if (showResult) cls += matches[id] === id ? ' correct' : ' wrong';
            else if (claimed) cls += ' matched';
            return (
              <button
                key={id}
                className={cls}
                disabled={showResult || !selectedLeft}
                onClick={() => assign(id)}
              >
                {tl(pair.right, lang)}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
