import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize a Java ThreadPool.',
    ru: 'Запустите код, чтобы визуализировать ThreadPool в Java.',
  },
  scene: { en: 'scene', ru: 'сцена' },
  pools: { en: 'ThreadPool instances', ru: 'экземпляры ThreadPool' },
  workers: { en: 'workers', ru: 'workers' },
  queueCapacity: { en: 'queue capacity', ru: 'емкость очереди' },
  accepting: { en: 'accepting tasks', ru: 'принимает задачи' },
  shutDown: { en: 'shut down', ru: 'остановлен' },
  workerRows: { en: 'workers', ru: 'workers' },
  queue: { en: 'queue', ru: 'очередь' },
  completed: { en: 'completed', ru: 'завершено' },
  rejected: { en: 'rejected', ru: 'отклонено' },
  recentActions: { en: 'recent actions', ru: 'последние действия' },
  current: { en: 'current', ru: 'текущая' },
  done: { en: 'done', ru: 'готово' },
  reason: { en: 'reason', ru: 'причина' },
};

const STATE_LABELS: Record<string, Localized> = {
  IDLE: { en: 'idle', ru: 'свободен' },
  BUSY: { en: 'busy', ru: 'занят' },
  SUBMITTED: { en: 'submitted', ru: 'отправлена' },
  QUEUED: { en: 'queued', ru: 'в очереди' },
  COMPLETED: { en: 'completed', ru: 'завершена' },
  REJECTED: { en: 'rejected', ru: 'отклонена' },
};

const REASON_LABELS: Record<string, Localized> = {
  QUEUE_FULL: { en: 'workers and queue are full', ru: 'workers и очередь заполнены' },
  SHUTDOWN: { en: 'pool is shut down', ru: 'pool остановлен' },
};

const ACTION_LABELS: Record<string, Localized> = {
  CREATE_POOL: { en: 'created pool', ru: 'создал pool' },
  SUBMIT_TASK: { en: 'submitted task', ru: 'отправил задачу' },
  ASSIGN_TASK: { en: 'assigned task', ru: 'назначил задачу' },
  QUEUE_TASK: { en: 'queued task', ru: 'поставил задачу в очередь' },
  COMPLETE_TASK: { en: 'completed task', ru: 'завершил задачу' },
  REJECT_TASK: { en: 'rejected task', ru: 'отклонил задачу' },
  SHUTDOWN_POOL: { en: 'shut down pool', ru: 'остановил pool' },
};

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
  rejectionReason?: string;
}

interface PoolSnapshot {
  name: string;
  workerCount: number;
  queueCapacity: number;
  shutdown: boolean;
  workers: WorkerSnapshot[];
  queue: TaskSnapshot[];
  completed: TaskSnapshot[];
  rejected: TaskSnapshot[];
}

interface HistoryItem {
  actor: string;
  action: string;
  target: string;
}

interface ThreadPoolState {
  name: string;
  pools: PoolSnapshot[];
  history: HistoryItem[];
}

export default function JavaThreadPoolVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as ThreadPoolState | undefined;
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
      key: 'pools',
      label: tl(LABELS.pools, lang),
      content: <strong style={monoStyle}>{state.pools.map((pool) => pool.name).join(', ') || '0'}</strong>,
    },
  ];

  return (
    <div style={wrapStyle}>
      <ArrayGrid cells={topCells} />

      {state.pools.map((pool) => (
        <section
          key={pool.name}
          style={{ ...poolStyle, ...(highlight.has(`pool:${pool.name}`) ? poolHighlightStyle : {}) }}
        >
          <div style={poolHeaderStyle}>
            <strong style={monoStyle}>{pool.name}</strong>
            <span>
              {tl(LABELS.workers, lang)} {pool.workerCount}
            </span>
            <span>
              {tl(LABELS.queueCapacity, lang)} {pool.queueCapacity}
            </span>
            <span>{pool.shutdown ? tl(LABELS.shutDown, lang) : tl(LABELS.accepting, lang)}</span>
          </div>
          <ArrayGrid cells={poolCells(pool, highlight, lang)} />
        </section>
      ))}

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

function poolCells(pool: PoolSnapshot, highlight: Set<string>, lang: 'en' | 'ru'): ArrayCell[] {
  return [
    {
      key: `${pool.name}-workers`,
      label: tl(LABELS.workerRows, lang),
      content: <LinkedNodes nodes={workerNodes(pool, highlight, lang)} />,
    },
    {
      key: `${pool.name}-queue`,
      label: tl(LABELS.queue, lang),
      highlighted: highlight.has(`queue:${pool.name}`),
      content: <LinkedNodes nodes={taskNodes(pool.queue, highlight, lang)} />,
    },
    {
      key: `${pool.name}-completed`,
      label: tl(LABELS.completed, lang),
      content: <LinkedNodes nodes={taskNodes(pool.completed, highlight, lang)} />,
    },
    {
      key: `${pool.name}-rejected`,
      label: tl(LABELS.rejected, lang),
      content: <LinkedNodes nodes={taskNodes(pool.rejected, highlight, lang)} />,
    },
  ];
}

function workerNodes(pool: PoolSnapshot, highlight: Set<string>, lang: 'en' | 'ru'): LinkedNode[] {
  return pool.workers.map((worker) => {
    const status = STATE_LABELS[worker.state] ?? { en: worker.state, ru: worker.state };
    const current = worker.currentTask
      ? `${tl(LABELS.current, lang)} ${worker.currentTask}`
      : `${tl(LABELS.done, lang)} ${worker.tasksCompleted}`;
    return {
      id: worker.name,
      title: worker.name,
      subtitle: `${tl(status, lang)} - ${current}`,
      highlighted: highlight.has(`worker:${pool.name}/${worker.name}`),
    };
  });
}

function taskNodes(tasks: TaskSnapshot[], highlight: Set<string>, lang: 'en' | 'ru'): LinkedNode[] {
  return tasks.map((task) => {
    const status = STATE_LABELS[task.state] ?? { en: task.state, ru: task.state };
    const reason = task.rejectionReason
      ? `${tl(LABELS.reason, lang)} ${tl(REASON_LABELS[task.rejectionReason] ?? { en: task.rejectionReason, ru: task.rejectionReason }, lang)}`
      : task.assignedWorker ?? '';
    return {
      id: task.name,
      title: task.name,
      subtitle: reason ? `${tl(status, lang)} - ${reason}` : tl(status, lang),
      highlighted: highlight.has(`task:${task.name}`),
    };
  });
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
const poolStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 8,
  padding: 8,
  borderRadius: 6,
  border: '1px solid var(--border)',
  background: 'var(--viz-box)',
};
const poolHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.20)',
};
const poolHeaderStyle: CSSProperties = {
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
  minWidth: 78,
};
const monoStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700 };
