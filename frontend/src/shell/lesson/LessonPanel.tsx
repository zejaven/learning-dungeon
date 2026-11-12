import { useEffect, useState } from 'react';
import { useGeneration } from '@app/engine/generationStore';
import { previousPosition, useLesson } from '@app/engine/lessonStore';
import { useCodeTasks } from '@app/engine/codeTaskStore';
import { navigate, routeForTheory } from '@app/engine/router';
import { useStore } from '@app/engine/store';
import { tl, ui, useLang } from '@app/i18n';
import { orderLangs } from '@app/languages';
import { GenerationView } from '@app/shell/GenerationView';
import { MissingLanguage } from '@app/shell/MissingLanguage';
import { BossFightUnit } from './BossFightUnit';
import { CodeTaskView } from './CodeTaskView';
import { GenerateTasksButton } from './GenerateTasksButton';
import { RegenerateLessonButton } from './RegenerateLessonButton';
import { ExerciseCard } from './ExerciseCard';
import { UnitTrack } from './UnitTrack';

/**
 * The "Learn by micro-actions" lesson: the current unit's exercise (or boss
 * question) above a horizontal unit track. Rendered in the home screen's right
 * panel as the default view of a topic that has a generated lesson; the full
 * theory stays reachable via the Reference button. Code tasks are units of the
 * same chain (kind `task`), shown with the editor in place of an exercise.
 */
export function LessonPanel() {
  const lang = useLang((s) => s.lang);
  const topic = useStore((s) => s.topic);
  const topicCompleted = useStore((s) => s.topicCompleted);
  // Code tasks are the topic's other gate; loadLesson loads them for the open topic.
  const taskItems = useCodeTasks((s) => (s.topicId === topic?.id ? s.items : null));
  const tasksLeft = taskItems?.filter((i) => !i.progress.solved).length ?? 0;

  const topicId = topic?.id ?? '';
  const lesson = useLesson();
  const genKey = `atoms:${topicId}`;
  const genTask = useGeneration((s) => (topicId ? s.tasks[genKey] : undefined));
  const tasksKey = `tasks:${topicId}`;
  const tasksGen = useGeneration((s) => (topicId ? s.tasks[tasksKey] : undefined));
  // The generation's last word: what running the new code exercises confirmed,
  // fixed or could not settle. Shown until dismissed (per generation task).
  const [dismissedGen, setDismissedGen] = useState('');

  useEffect(() => {
    if (topicId) void useLesson.getState().loadLesson(topicId);
    return () => useLesson.getState().reset();
  }, [topicId]);

  // A finished regeneration replaces the file: reload the lesson.
  useEffect(() => {
    if (genTask?.status === 'done' && topicId) void useLesson.getState().loadLesson(topicId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [genTask?.status]);

  // New code tasks are new units: re-read the topic (hasTasks) and rebuild the chain.
  useEffect(() => {
    if (tasksGen?.status !== 'done' || !topicId) return;
    void useStore
      .getState()
      .selectTopic(topicId)
      .then(() => useLesson.getState().loadLesson(topicId));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tasksGen?.status]);

  if (!topic) return null;

  const unit = lesson.units.find((u) => u.id === lesson.currentUnitId) ?? null;
  const isBoss = unit?.kind === 'boss';
  const isMistakes = unit?.kind === 'mistakes';
  const isTask = unit?.kind === 'task';
  const taskItem = isTask ? taskItems?.find((i) => i.task.id === unit!.id.slice(2)) : undefined;
  const generating = genTask?.status === 'running';
  const generatingTasks = tasksGen?.status === 'running' || tasksGen?.status === 'error';
  const genResult =
    genTask && (genTask.status === 'done' || genTask.status === 'error') && genTask.message &&
    dismissedGen !== genTask.taskId
      ? genTask
      : null;
  const genResultWarns =
    !!genResult &&
    (genResult.status === 'error' || /STILL CONTRADICTED|rolled back/.test(genResult.message));

  // The exercise on screen: for a normal unit it is the indexed one; for the
  // mistakes loop it is the head of the requeue queue.
  const normalExerciseId =
    unit && !isBoss && !isMistakes && !isTask ? unit.exerciseIds[lesson.exerciseIndex] : null;
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
        : isTask
          ? taskItem?.task.kind === 'final'
            ? `🏁 ${ui('finalTaskPhase', lang)}`
            : `⌨ ${ui('taskPhase', lang)}`
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
        {unit && !isBoss && !isMistakes && !isTask && (
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
        {topic.domainId === 'java' && <GenerateTasksButton topicId={topicId} />}
        <RegenerateLessonButton topicId={topicId} />
      </div>

      <div className="lesson-body">
        {generating && <GenerationView taskKey={genKey} />}
        {!generating && generatingTasks && <GenerationView taskKey={tasksKey} />}

        {!generating && (
          <>
            {genResult && (
              <div className={`lesson-banner gen-result ${genResultWarns ? 'warn' : 'done'}`}>
                <span>
                  {ui('lessonGenResult', lang)} {genResult.message}
                </span>
                <button onClick={() => setDismissedGen(genResult.taskId)} title={ui('close', lang)}>
                  ✕
                </button>
              </div>
            )}
            {lesson.loading && <p className="home-hint">{ui('loading', lang)}</p>}

            {!lesson.loading && lesson.lessonCompleted && (
              <div className="lesson-banner done">
                {tasksLeft > 0 && !topicCompleted
                  ? ui('lessonDoneTasksLeft', lang).replace('{n}', String(tasksLeft))
                  : ui('lessonCompleted', lang)}
              </div>
            )}

            {!lesson.loading && isBoss && unit && <BossFightUnit unit={unit} />}

            {!lesson.loading && isTask && (
              <>
                {/* Running code is refused on the phone, so there the task only
                    says where it is solved and lets the lesson move on. */}
                <div className="desktop-only">
                  {taskItem ? (
                    <CodeTaskView key={taskItem.task.id} topicId={topicId} item={taskItem} />
                  ) : (
                    <p className="home-hint">{ui('loading', lang)}</p>
                  )}
                </div>
                <div className={`mobile-only lesson-banner${taskItem?.progress.solved ? ' done' : ''}`}>
                  {ui(taskItem?.progress.solved ? 'taskSolved' : 'taskOnPc', lang)}
                </div>
                <div className="ex-actions">
                  <button
                    className={taskItem?.progress.solved ? 'primary' : ''}
                    onClick={() => void lesson.continueNext()}
                  >
                    {ui('continueBtn', lang)}
                  </button>
                </div>
              </>
            )}

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

            {!lesson.loading && unit && !isBoss && !isMistakes && !isTask && activeExercise && (
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
