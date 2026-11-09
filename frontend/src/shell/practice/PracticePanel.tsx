import { useEffect, useState } from 'react';
import { useAi } from '@app/engine/aiStore';
import { fetchTasksNoneReason, startTasksGeneration } from '@app/engine/api';
import { useCodeTasks } from '@app/engine/codeTaskStore';
import { useGeneration } from '@app/engine/generationStore';
import { navigate, routeForTasks } from '@app/engine/router';
import { useStore } from '@app/engine/store';
import { tl, ui, useLang } from '@app/i18n';
import { GenerationView } from '@app/shell/GenerationView';
import { CodeTaskView } from './CodeTaskView';

/** The Practice tab: the topic's code tasks, the one that is open, or a way to generate them. */
export function PracticePanel({ questionId, taskId }: { questionId: string; taskId: string | null }) {
  const topicId = useCodeTasks((s) => s.topicId);
  const items = useCodeTasks((s) => s.items);
  const loadError = useCodeTasks((s) => s.loadError);
  const lang = useLang((s) => s.lang);

  if (loadError) return <div className="output error">{loadError}</div>;
  if (!items || !topicId) return <p className="home-hint">{ui('loading', lang)}</p>;

  const open = taskId ? items.find((i) => i.task.id === taskId) : undefined;
  if (open) {
    return (
      <div className="practice-panel">
        <button onClick={() => navigate(routeForTasks(questionId))}>{ui('backToTasks', lang)}</button>
        <CodeTaskView key={open.task.id} topicId={topicId} item={open} />
      </div>
    );
  }

  return (
    <div className="practice-panel">
      {items.length > 0 && <p className="home-hint">{ui('practiceIntro', lang)}</p>}
      {items.map(({ task, atomTitle, progress }) => (
        <button
          key={task.id}
          className="practice-task"
          onClick={() => navigate(routeForTasks(questionId, task.id))}
        >
          <span className="practice-task-mark">{progress.solved ? '✅' : '○'}</span>
          <span className="practice-task-title">{tl(task.title, lang)}</span>
          {/* Atom titles state the rule the task trains, so they wait for the solution. */}
          {progress.solved && <span className="practice-task-atom">{tl(atomTitle, lang)}</span>}
        </button>
      ))}
      <GenerateTasks topicId={topicId} hasTasks={items.length > 0} />
    </div>
  );
}

/**
 * Generates code tasks for the topic. They are only ever added: existing tasks
 * and their progress stay, so there is no "regenerate". The server keeps only
 * tasks whose reference passes and whose wrong solutions fail.
 */
function GenerateTasks({ topicId, hasTasks }: { topicId: string; hasTasks: boolean }) {
  const lang = useLang((s) => s.lang);
  const topic = useStore((s) => s.topic);
  const key = `tasks:${topicId}`;
  const genTask = useGeneration((s) => s.tasks[key]);
  const [comment, setComment] = useState('');
  const [startError, setStartError] = useState<string | null>(null);
  const [noneReason, setNoneReason] = useState<string | null>(null);
  const decided = !!topic?.tasksDecided;
  const running = genTask?.status === 'running';

  useEffect(() => {
    setNoneReason(null);
    if (!hasTasks && decided) void fetchTasksNoneReason(topicId).then(setNoneReason).catch(() => {});
  }, [topicId, hasTasks, decided]);

  // A finished run changed the folder: re-read the topic (tasksDecided) and its tasks.
  useEffect(() => {
    if (genTask?.status !== 'done') return;
    void useStore.getState().selectTopic(topicId);
    void useCodeTasks.getState().load(topicId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [genTask?.status]);

  async function generate() {
    setStartError(null);
    try {
      const ref = await startTasksGeneration(topicId, useAi.getState().selectedProvider, comment.trim());
      useGeneration.getState().attach(ref.taskId, ref.key);
      setComment('');
    } catch (e) {
      setStartError((e as Error).message);
    }
  }

  return (
    <div className="practice-generate">
      {!hasTasks && !running && (
        <p className="home-hint">{noneReason ? `${ui('tasksNone', lang)} ${noneReason}` : ui('tasksEmpty', lang)}</p>
      )}
      {genTask && (running || genTask.status === 'error') && <GenerationView taskKey={key} />}
      {!running && (
        <div className="practice-generate-row">
          <input
            type="text"
            value={comment}
            placeholder={ui('tasksCommentPlaceholder', lang)}
            onChange={(e) => setComment(e.target.value)}
          />
          <button className={hasTasks ? '' : 'primary'} onClick={() => void generate()}>
            {ui(hasTasks ? 'generateMoreTasks' : 'generateTasks', lang)}
          </button>
        </div>
      )}
      {startError && <div className="output error">{startError}</div>}
    </div>
  );
}
