import { useEffect, useState } from 'react';
import { useAi } from '@app/engine/aiStore';
import { startAtomsGeneration } from '@app/engine/api';
import { genLanguages } from '@app/engine/genLangStore';
import { useGeneration } from '@app/engine/generationStore';
import { previousPosition, useLesson } from '@app/engine/lessonStore';
import { navigate, routeForTheory } from '@app/engine/router';
import { useStore } from '@app/engine/store';
import { tl, ui, useLang } from '@app/i18n';
import { orderLangs } from '@app/languages';
import { GenerationView } from '@app/shell/GenerationView';
import { MissingLanguage } from '@app/shell/MissingLanguage';
import { BossFightUnit } from './BossFightUnit';
import { ExerciseCard } from './ExerciseCard';
import { RegenerateLessonDialog, type RegenerateOptions } from './RegenerateLessonDialog';
import { UnitTrack } from './UnitTrack';

/**
 * The "Learn by micro-actions" lesson: the current unit's exercise (or boss
 * question) above a horizontal unit track. Rendered in the home screen's right
 * panel as the default view of a topic that has a generated lesson; the full
 * theory stays reachable via the Reference button.
 */
export function LessonPanel() {
  const lang = useLang((s) => s.lang);
  const topic = useStore((s) => s.topic);
  const activeVersionNo = useStore((s) => s.activeVersionNo);

  const topicId = topic?.id ?? '';
  const lesson = useLesson();
  const genKey = `atoms:${topicId}`;
  const genTask = useGeneration((s) => (topicId ? s.tasks[genKey] : undefined));
  const [regenOpen, setRegenOpen] = useState(false);

  useEffect(() => {
    if (topicId) void useLesson.getState().loadLesson(topicId);
    return () => useLesson.getState().reset();
  }, [topicId]);

  // A finished regeneration replaces the file: reload the lesson.
  useEffect(() => {
    if (genTask?.status === 'done' && topicId) void useLesson.getState().loadLesson(topicId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [genTask?.status]);

  if (!topic) return null;

  async function regenerate(options: RegenerateOptions) {
    setRegenOpen(false);
    try {
      const ref = await startAtomsGeneration(
        topicId,
        useAi.getState().selectedProvider,
        activeVersionNo,
        options.mode,
        options.comment,
        genLanguages(),
      );
      useGeneration.getState().attach(ref.taskId, ref.key);
    } catch {
      /* the button stays; the learner can retry */
    }
  }

  const unit = lesson.units.find((u) => u.id === lesson.currentUnitId) ?? null;
  const isBoss = unit?.kind === 'boss';
  const isMistakes = unit?.kind === 'mistakes';
  const generating = genTask?.status === 'running';

  // The exercise on screen: for a normal unit it is the indexed one; for the
  // mistakes loop it is the head of the requeue queue.
  const normalExerciseId = unit && !isBoss && !isMistakes ? unit.exerciseIds[lesson.exerciseIndex] : null;
  const mistakeExerciseId = isMistakes ? lesson.mistakesQueue[0] ?? null : null;
  const activeExerciseId = normalExerciseId ?? mistakeExerciseId;
  const activeExercise = activeExerciseId ? lesson.exerciseById[activeExerciseId] : null;
  const atom = activeExercise
    ? lesson.atoms?.atoms.find((a) => a.id === lesson.atomIdByExerciseId[activeExercise.id])
    : null;

  // A saved answer for a normal exercise means we are revisiting (or just
  // answered) it — show it in the feedback phase; otherwise ask.
  const result = normalExerciseId ? lesson.results[normalExerciseId] : undefined;
  const phase = result ? 'feedback' : 'answering';
  const backTarget = previousPosition(lesson.units, lesson.currentUnitId, lesson.exerciseIndex);

  const phaseLabel = !unit
    ? ui('lesson', lang)
    : isBoss
      ? `⚔ ${ui('bossPhase', lang)}`
      : isMistakes
        ? `🔁 ${ui('mistakesTitle', lang)}`
        : unit.kind === 'discovery'
          ? `◆ ${ui('discoveryPhase', lang)}`
          : unit.kind === 'capstone'
            ? `★ ${ui('capstonePhase', lang)}`
            : `● ${ui('practicePhase', lang)}`;

  // A lesson exists only in the languages its topic was generated in. Gate the
  // whole panel rather than each field: half-translated exercise cards would be
  // worse than an honest empty state.
  const topicLangs = orderLangs(topic?.languages ?? []);
  if (topicLangs.length && !topicLangs.includes(lang)) {
    return (
      <div className="lesson-panel">
        <MissingLanguage available={topicLangs} />
      </div>
    );
  }

  return (
    <div className="lesson-panel">
      <div className="lesson-head">
        <span className="lesson-phase">
          {phaseLabel}
          {atom && <span className="lesson-atom-title"> — {tl(atom.title, lang)}</span>}
        </span>
        <span className="spacer" style={{ flex: 1 }} />
        {unit && !isBoss && !isMistakes && (
          <span className="lesson-progress">
            {lesson.exerciseIndex + 1} / {unit.exerciseIds.length}
          </span>
        )}
        {isMistakes && lesson.mistakesQueue.length > 0 && (
          <span className="lesson-progress">
            {lesson.mistakesQueue.length} {ui('mistakesLeft', lang)}
          </span>
        )}
        <button onClick={() => navigate(routeForTheory(topicId))}>{ui('reference', lang)}</button>
        <button
          onClick={() => setRegenOpen(true)}
          disabled={generating}
          title={ui('regenerateLesson', lang)}
        >
          {generating ? ui('generating', lang) : '↻'}
        </button>
      </div>

      {regenOpen && (
        <RegenerateLessonDialog onConfirm={regenerate} onClose={() => setRegenOpen(false)} />
      )}

      <div className="lesson-body">
        {generating && <GenerationView taskKey={genKey} />}

        {!generating && (
          <>
            {lesson.loading && <p className="home-hint">{ui('loading', lang)}</p>}

            {!lesson.loading && lesson.lessonCompleted && (
              <div className="lesson-banner done">{ui('lessonCompleted', lang)}</div>
            )}

            {!lesson.loading && isBoss && unit && <BossFightUnit unit={unit} />}

            {!lesson.loading && isMistakes && !mistakeExerciseId && (
              <>
                <div className="lesson-banner done">{ui('noMistakes', lang)}</div>
                <div className="ex-actions">
                  <button className="primary" onClick={() => lesson.continueMistake()}>
                    {ui('continueBtn', lang)}
                  </button>
                </div>
              </>
            )}

            {!lesson.loading && isMistakes && activeExercise && mistakeExerciseId && (
              <ExerciseCard
                key={`m:${activeExercise.id}`}
                exercise={activeExercise}
                phase={lesson.mistakesPhase}
                lastCorrect={lesson.mistakesLastCorrect}
                presetAnswer={lesson.mistakesLastAnswer}
                onSubmit={(answer) => lesson.submitMistake(answer)}
                onContinue={() => lesson.continueMistake()}
              />
            )}

            {!lesson.loading && unit && !isBoss && !isMistakes && activeExercise && (
              <ExerciseCard
                key={activeExercise.id}
                exercise={activeExercise}
                phase={phase}
                lastCorrect={result?.correct ?? false}
                presetAnswer={result?.answer ?? null}
                onSubmit={(answer) => lesson.submitAnswer(answer)}
                onContinue={() => void lesson.continueNext()}
                onBack={() => lesson.goBack()}
                backDisabled={!backTarget}
              />
            )}
          </>
        )}
      </div>

      <UnitTrack
        units={lesson.units}
        results={lesson.results}
        currentUnitId={lesson.currentUnitId}
        onSelect={(id) => lesson.goToUnit(id)}
      />
    </div>
  );
}
