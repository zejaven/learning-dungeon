import { useEffect, useState } from 'react';
import type { AnswerValue, Exercise } from '@app/engine/lessonTypes';
import { tl, ui, useLang } from '@app/i18n';
import { Markdown } from '@app/shell/Markdown';
import { MermaidBlock } from '@app/shell/MermaidBlock';
import { FillBlank } from './exercises/FillBlank';
import { MatchPairs } from './exercises/MatchPairs';
import { MultipleChoice } from './exercises/MultipleChoice';
import { SortSteps } from './exercises/SortSteps';
import { TrueFalse } from './exercises/TrueFalse';
import { WordBank } from './exercises/WordBank';

interface Props {
  exercise: Exercise;
  /** 'answering' | 'feedback' — feedback locks inputs and shows the verdict. */
  phase: 'answering' | 'feedback';
  lastCorrect: boolean;
  /** Answer to display when entering feedback for a revisited exercise. */
  presetAnswer?: AnswerValue | null;
  onSubmit: (answer: AnswerValue) => void;
  onContinue: () => void;
  /** Label override for the continue button (e.g. while saving). */
  continueLabel?: string;
}

/**
 * One micro-exercise: prompt, optional code/diagram, the type-specific input,
 * then Check → feedback banner → Continue. Wrong answers always allow
 * continuing (errors are part of discovery/practice).
 */
export function ExerciseCard({
  exercise,
  phase,
  lastCorrect,
  presetAnswer,
  onSubmit,
  onContinue,
  continueLabel,
}: Props) {
  const lang = useLang((s) => s.lang);
  const [draft, setDraft] = useState<AnswerValue | null>(presetAnswer ?? null);

  // Answering: start blank. Feedback: seed the draft from the preset answer so
  // a revisited exercise shows what was chosen (on a fresh submit the parent
  // supplies the same answer as preset, so nothing is lost).
  useEffect(() => {
    setDraft(phase === 'feedback' ? presetAnswer ?? null : null);
  }, [exercise.id, phase, presetAnswer]);

  const showResult = phase === 'feedback';
  const submittable = draft != null && !showResult;

  function submit() {
    if (draft != null) onSubmit(draft);
  }

  const feedbackText = feedbackFor(exercise, draft, lastCorrect);

  return (
    <div className="exercise-card">
      <div className="ex-prompt">
        <Markdown>{tl(exercise.prompt, lang)}</Markdown>
      </div>

      {exercise.code && (
        <pre className="ex-code">
          <code>{exercise.code}</code>
        </pre>
      )}
      {exercise.mermaid && <MermaidBlock code={tl(exercise.mermaid, lang)} />}

      {renderInput(exercise, draft, setDraft, showResult, lastCorrect, submit)}

      {showResult && (
        <div className={`ex-feedback ${lastCorrect ? 'correct' : 'wrong'}`}>
          <div className="ex-feedback-title">
            {lastCorrect ? `✅ ${ui('correct', lang)}` : `❌ ${ui('incorrect', lang)}`}
          </div>
          {feedbackText && <Markdown>{tl(feedbackText, lang)}</Markdown>}
        </div>
      )}

      {showResult && exercise.reveal && (
        <div className="ex-reveal">
          <Markdown>{tl(exercise.reveal, lang)}</Markdown>
        </div>
      )}

      <div className="ex-actions">
        {showResult ? (
          <button className="primary" onClick={onContinue} autoFocus>
            {continueLabel ?? ui('continueBtn', lang)}
          </button>
        ) : (
          <button className="primary" onClick={submit} disabled={!submittable}>
            {ui('check', lang)}
          </button>
        )}
      </div>
    </div>
  );
}

function renderInput(
  exercise: Exercise,
  draft: AnswerValue | null,
  onChange: (a: AnswerValue) => void,
  showResult: boolean,
  correct: boolean,
  submit: () => void,
) {
  switch (exercise.type) {
    case 'multiple_choice':
    case 'predict_output':
    case 'spot_bug':
      return <MultipleChoice exercise={exercise} answer={draft} onChange={onChange} showResult={showResult} />;
    case 'true_false':
      return <TrueFalse exercise={exercise} answer={draft} onChange={onChange} showResult={showResult} />;
    case 'fill_blank':
      return (
        <FillBlank
          exercise={exercise}
          answer={draft}
          onChange={onChange}
          showResult={showResult}
          correct={correct}
          onEnter={submit}
        />
      );
    case 'word_bank':
      return (
        <WordBank exercise={exercise} answer={draft} onChange={onChange} showResult={showResult} correct={correct} />
      );
    case 'sort_steps':
      return <SortSteps exercise={exercise} answer={draft} onChange={onChange} showResult={showResult} />;
    case 'match_pairs':
      return <MatchPairs exercise={exercise} answer={draft} onChange={onChange} showResult={showResult} />;
    default:
      return null;
  }
}

/**
 * The feedback to show: on a wrong choice the chosen option's misconception
 * feedback wins over the generic incorrect text.
 */
function feedbackFor(exercise: Exercise, draft: AnswerValue | null, correct: boolean) {
  if (correct) return exercise.feedback?.correct ?? null;
  if (
    (exercise.type === 'multiple_choice' || exercise.type === 'predict_output' || exercise.type === 'spot_bug')
    && draft?.kind === 'option'
  ) {
    const chosen = exercise.options.find((o) => o.id === draft.optionId);
    if (chosen?.feedback) return chosen.feedback;
  }
  return exercise.feedback?.incorrect ?? null;
}
