import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize waiting for a task batch.',
    ru: 'Запустите код, чтобы визуализировать ожидание пачки задач.',
  },
  scene: { en: 'scene', ru: 'сцена' },
  main: { en: 'main', ru: 'main' },
  executor: { en: 'executor', ru: 'executor' },
  workers: { en: 'workers', ru: 'workers' },
  queue: { en: 'queue', ru: 'очередь' },
  completed: { en: 'completed', ru: 'завершено' },
  latch: { en: 'latch', ru: 'latch' },
  count: { en: 'count', ru: 'счетчик' },
  released: { en: 'released', ru: 'отпущен' },
  futures: { en: 'futures', ru: 'futures' },
  result: { en: 'result', ru: 'результат' },
  done: { en: 'done', ru: 'done' },
  current: { en: 'current', ru: 'текущая' },
  tasksDone: { en: 'done', ru: 'готово' },
  waitingFor: { en: 'waiting for', ru: 'ждет' },
  accepting: { en: 'accepting', ru: 'принимает' },
  shutDown: { en: 'shut down', ru: 'остановлен' },
  recentActions: { en: 'recent actions', ru: 'последние действия' },
};

const STATE_LABELS: Record<string, Localized> = {
  RUNNING: { en: 'running', ru: 'работает' },
  WAITING_ON_LATCH: { en: 'waiting on latch', ru: 'ждет latch' },
  WAITING_INVOKE_ALL: { en: 'waiting in invokeAll', ru: 'ждет в invokeAll' },
  WAITING_ON_FUTURE: { en: 'waiting on Future', ru: 'ждет Future' },
  IDLE: { en: 'idle', ru: 'свободен' },
  BUSY: { en: 'busy', ru: 'занят' },
  SUBMITTED: { en: 'submitted', ru: 'отправлена' },
  QUEUED: { en: 'queued', ru: 'в очереди' },
  COMPLETED: { en: 'completed', ru: 'завершена' },
};

const ACTION_LABELS: Record<string, Localized> = {
  CREATE_SCENE: { en: 'created scene', ru: 'создал сцену' },
  CREATE_EXECUTOR: { en: 'created executor', ru: 'создал executor' },
  SUBMIT_TASK: { en: 'submitted task', ru: 'отправил задачу' },
  START_TASK: { en: 'started task', ru: 'запустил задачу' },
  QUEUE_TASK: { en: 'queued task', ru: 'поставил в очередь' },
  FINISH_TASK: { en: 'finished task', ru: 'завершил задачу' },
  CREATE_LATCH: { en: 'created latch', ru: 'создал latch' },
  AWAIT_LATCH: { en: 'await latch', ru: 'ждет latch' },
  COUNT_DOWN: { en: 'countDown', ru: 'countDown' },
  LATCH_RELEASED: { en: 'latch released', ru: 'latch отпустил' },
  INVOKE_ALL: { en: 'called invokeAll', ru: 'вызвал invokeAll' },
  INVOKE_ALL_DONE: { en: 'invokeAll done', ru: 'invokeAll завершен' },
  CREATE_FUTURE: { en: 'created Future', ru: 'создал Future' },
  GET_FUTURE_WAIT: { en: 'Future.get waits', ru: 'Future.get ждет' },
  GET_FUTURE_OK: { en: 'Future.get returned', ru: 'Future.get вернул' },
  SHUTDOWN: { en: 'shutdown', ru: 'shutdown' },
};

interface MainSnapshot {
  state: string;
  waitingFor?: string;
}

interface WorkerSnapshot {
  name: string;
  state: string;
  tasksCompleted: number;
  currentTask?: string;
}

interface TaskSnapshot {
  name: string;
  state: string;
  assignedWorker?: string;
}

interface FutureSnapshot extends TaskSnapshot {
  done: boolean;
  result?: unknown;
}

interface ExecutorSnapshot {
  name: string;
  workerCount: number;
  shutdown: boolean;
  workers: WorkerSnapshot[];
  queue: TaskSnapshot[];
  completed: TaskSnapshot[];
}

interface LatchSnapshot {
  name: string;
  initialCount: number;
  count: number;
  released: boolean;
}

interface HistoryItem {
  actor: string;
  action: string;
  target: string;
}

interface WaitAllState {
  name: string;
  main: MainSnapshot;
  executor: ExecutorSnapshot | null;
  latch: LatchSnapshot | null;
  futures: FutureSnapshot[];
  history: HistoryItem[];
}

export default function ExecutorWaitAllVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as WaitAllState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const topCells: ArrayCell[] = [
    {
      key: 'scene',
      label: tl(LABELS.scene, lang),
      content: <strong style={monoStyle}>{state.name}</strong>,
    },
    {
      key: 'main',
      label: tl(LABELS.main, lang),
      highlighted: highlight.has('main'),
      content: <MainStatus main={state.main} lang={lang} />,
    },
  ];

  if (state.latch) {
    topCells.push({
      key: 'latch',
      label: tl(LABELS.latch, lang),
      highlighted: highlight.has('latch'),
      content: <LatchStatus latch={state.latch} lang={lang} />,
    });
  }

  return (
    <div style={wrapStyle}>
      <ArrayGrid cells={topCells} />

      {state.executor && (
        <section
          style={{ ...panelStyle, ...(highlight.has('executor') ? panelHighlightStyle : {}) }}
        >
          <div style={panelHeaderStyle}>
            <strong style={monoStyle}>{state.executor.name}</strong>
            <span>
              {tl(LABELS.workers, lang)} {state.executor.workerCount}
            </span>
            <span>{state.executor.shutdown ? tl(LABELS.shutDown, lang) : tl(LABELS.accepting, lang)}</span>
          </div>
          <ArrayGrid cells={executorCells(state.executor, highlight, lang)} />
        </section>
      )}

      {state.futures.length > 0 && (
        <section style={sectionStyle}>
          <div style={titleStyle}>{tl(LABELS.futures, lang)}</div>
          <LinkedNodes nodes={futureNodes(state.futures, highlight, lang)} />
        </section>
      )}

      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.recentActions, lang)}</div>
        <div style={historyStyle}>
          {state.history.map((item, index) => {
            const action = ACTION_LABELS[item.action] ?? { en: item.action, ru: item.action };
            return (
              <div key={`${item.actor}-${item.action}-${index}`} style={historyItemStyle}>
                <span style={actorStyle}>{item.actor}</span>
                <span>{tl(action, lang)}</span>
                <span style={monoStyle}>{item.target}</span>
              </div>
            );
          })}
        </div>
      </section>
    </div>
  );
}

function MainStatus({ main, lang }: { main: MainSnapshot; lang: 'en' | 'ru' }) {
  const label = STATE_LABELS[main.state] ?? { en: main.state, ru: main.state };
  return (
    <span>
      <strong style={monoStyle}>{tl(label, lang)}</strong>
      {main.waitingFor && (
        <span style={mutedStyle}>
          {' '}
          - {tl(LABELS.waitingFor, lang)} <span style={monoStyle}>{main.waitingFor}</span>
        </span>
      )}
    </span>
  );
}

function LatchStatus({ latch, lang }: { latch: LatchSnapshot; lang: 'en' | 'ru' }) {
  return (
    <span>
      <strong style={monoStyle}>{latch.name}</strong>
      <span style={mutedStyle}>
        {' '}
        - {tl(LABELS.count, lang)} {latch.count}/{latch.initialCount}
        {latch.released ? ` - ${tl(LABELS.released, lang)}` : ''}
      </span>
    </span>
  );
}

function executorCells(executor: ExecutorSnapshot, highlight: Set<string>, lang: 'en' | 'ru'): ArrayCell[] {
  return [
    {
      key: 'workers',
      label: tl(LABELS.workers, lang),
      content: <LinkedNodes nodes={workerNodes(executor.workers, highlight, lang)} />,
    },
    {
      key: 'queue',
      label: tl(LABELS.queue, lang),
      highlighted: highlight.has('queue'),
      content: <LinkedNodes nodes={taskNodes(executor.queue, highlight, lang)} />,
    },
    {
      key: 'completed',
      label: tl(LABELS.completed, lang),
      content: <LinkedNodes nodes={taskNodes(executor.completed, highlight, lang)} />,
    },
  ];
}

function workerNodes(workers: WorkerSnapshot[], highlight: Set<string>, lang: 'en' | 'ru'): LinkedNode[] {
  return workers.map((worker) => {
    const status = STATE_LABELS[worker.state] ?? { en: worker.state, ru: worker.state };
    const detail = worker.currentTask
      ? `${tl(LABELS.current, lang)} ${worker.currentTask}`
      : `${tl(LABELS.tasksDone, lang)} ${worker.tasksCompleted}`;
    return {
      id: worker.name,
      title: worker.name,
      subtitle: `${tl(status, lang)} - ${detail}`,
      highlighted: highlight.has(`worker:${worker.name}`),
    };
  });
}

function taskNodes(tasks: TaskSnapshot[], highlight: Set<string>, lang: 'en' | 'ru'): LinkedNode[] {
  return tasks.map((task) => {
    const status = STATE_LABELS[task.state] ?? { en: task.state, ru: task.state };
    return {
      id: task.name,
      title: task.name,
      subtitle: task.assignedWorker ? `${tl(status, lang)} - ${task.assignedWorker}` : tl(status, lang),
      highlighted: highlight.has(`task:${task.name}`),
    };
  });
}

function futureNodes(futures: FutureSnapshot[], highlight: Set<string>, lang: 'en' | 'ru'): LinkedNode[] {
  return futures.map((future) => {
    const status = future.done ? tl(LABELS.done, lang) : tl(STATE_LABELS[future.state] ?? { en: future.state, ru: future.state }, lang);
    const result = future.result === undefined ? '' : ` - ${tl(LABELS.result, lang)} ${String(future.result)}`;
    return {
      id: future.name,
      title: future.name,
      subtitle: `${status}${result}`,
      highlighted: highlight.has(`future:${future.name}`) || highlight.has(`task:${future.name}`),
    };
  });
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
const panelStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 8,
  padding: 8,
  borderRadius: 6,
  border: '1px solid var(--border)',
  background: 'var(--viz-box)',
};
const panelHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.20)',
};
const panelHeaderStyle: CSSProperties = {
  display: 'flex',
  flexWrap: 'wrap',
  gap: 10,
  alignItems: 'center',
  fontSize: 12,
  opacity: 0.85,
};
const sectionStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const titleStyle: CSSProperties = { fontSize: 12, fontWeight: 700, opacity: 0.7 };
const historyStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 4 };
const historyItemStyle: CSSProperties = {
  display: 'flex',
  gap: 8,
  alignItems: 'center',
  padding: '4px 8px',
  borderRadius: 6,
  background: 'var(--viz-box)',
  fontSize: 13,
};
const actorStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontWeight: 700,
  minWidth: 88,
};
const monoStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700 };
const mutedStyle: CSSProperties = { opacity: 0.75, fontSize: 12 };
