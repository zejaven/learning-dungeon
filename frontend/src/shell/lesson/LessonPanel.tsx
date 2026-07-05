import { useEffect } from 'react';
import { useAi } from '@app/engine/aiStore';
import { startAtomsGeneration } from '@app/engine/api';
import { useGeneration } from '@app/engine/generationStore';
import { useLesson } from '@app/engine/lessonStore';
import { navigate, routeForTheory } from '@app/engine/router';
import { useStore } from '@app/engine/store';
import { tl, ui, useLang } from '@app/i18n';
import { GenerationView } from '@app/shell/GenerationView';
import { BossFightUnit } from './BossFightUnit';
import { ExerciseCard } from './ExerciseCard';
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

  async function regenerate() {
    if (!window.confirm(ui('regenerateLessonWarning', lang))) return;
    try {
      const ref = await startAtomsGeneration(topicId, useAi.getState().selectedProvider, activeVersionNo);
      useGeneration.getState().attach(ref.taskId, ref.key);
    } catch {
      /* the button stays; the learner can retry */
    }
  }

  const unit = lesson.units.find((u) => u.id === lesson.currentUnitId) ?? null;
  const exerciseId = unit && unit.kind !== 'boss' ? unit.exerciseIds[lesson.exerciseIndex] : null;
  const exercise = exerciseId ? lesson.exerciseById[exerciseId] : null;
  const atom = exercise ? lesson.atoms?.atoms.atoms.find((a) => a.id === lesson.atomIdByExerciseId[exercise.id]) : null;
  const generating = genTask?.status === 'running';

  // A saved answer for the current exercise means we are revisiting (or just
  // answered) it — show it in the feedback phase; otherwise ask.
  const result = exercise ? lesson.results[exercise.id] : undefined;
  const phase = result ? 'feedback' : 'answering';

  return (
    <div className="lesson-panel">
      <div className="lesson-head">
        <span className="lesson-phase">
          {unit
            ? unit.kind === 'discovery'
              ? `◆ ${ui('discoveryPhase', lang)}`
              : unit.kind === 'practice'
                ? `● ${ui('practicePhase', lang)}`
                : `⚔ ${ui('bossPhase', lang)}`
            : ui('lesson', lang)}
          {atom && <span className="lesson-atom-title"> — {tl(atom.title, lang)}</span>}
        </span>
        <span className="spacer" style={{ flex: 1 }} />
        {unit && unit.kind !== 'boss' && (
          <span className="lesson-progress">
            {lesson.exerciseIndex + 1} / {unit.exerciseIds.length}
          </span>
        )}
        <button onClick={() => navigate(routeForTheory(topicId))}>{ui('reference', lang)}</button>
        <button onClick={regenerate} disabled={generating} title={ui('regenerateLesson', lang)}>
          {generating ? ui('generating', lang) : '↻'}
        </button>
      </div>

      {generating && <GenerationView taskKey={genKey} />}

      {!generating && lesson.stale && (
        <div className="lesson-banner warn">
          {ui('lessonStale', lang)}{' '}
          <button onClick={() => void useLesson.getState().loadLesson(topicId)}>
            {ui('reloadLesson', lang)}
          </button>
        </div>
      )}

      {!generating && !lesson.stale && (
        <>
          {lesson.loading && <p className="home-hint">{ui('loading', lang)}</p>}

          {!lesson.loading && lesson.lessonCompleted && (
            <div className="lesson-banner done">{ui('lessonCompleted', lang)}</div>
          )}

          {!lesson.loading && unit && unit.kind === 'boss' && <BossFightUnit unit={unit} />}

          {!lesson.loading && unit && unit.kind !== 'boss' && exercise && (
            <ExerciseCard
              key={exercise.id}
              exercise={exercise}
              phase={phase}
              lastCorrect={result?.correct ?? false}
              presetAnswer={result?.answer ?? null}
              onSubmit={(answer) => lesson.submitAnswer(answer)}
              onContinue={() => void lesson.continueNext()}
            />
          )}
        </>
      )}

      <UnitTrack
        units={lesson.units}
        completedUnits={lesson.completedUnits}
        results={lesson.results}
        currentUnitId={lesson.currentUnitId}
        onSelect={(id) => lesson.goToUnit(id)}
      />
    </div>
  );
}
