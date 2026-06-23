import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize Thread and Runnable roles.',
    ru: 'Запустите код, чтобы увидеть роли Thread и Runnable.',
  },
  currentThread: { en: 'current thread', ru: 'текущий thread' },
  runnableTasks: { en: 'Runnable tasks', ru: 'задачи Runnable' },
  threadObjects: { en: 'Thread objects', ru: 'объекты Thread' },
  recentActions: { en: 'recent actions', ru: 'последние действия' },
  task: { en: 'task', ru: 'задача' },
  runs: { en: 'runs', ru: 'запусков' },
  attachedTo: { en: 'attached to', ru: 'привязан к' },
  noTarget: { en: 'no Runnable target', ru: 'нет Runnable target' },
  execution: { en: 'executes on', ru: 'выполняется в' },
  started: { en: 'started', ru: 'запущен' },
  notStarted: { en: 'not started', ru: 'не запущен' },
};

const STATE_LABELS: Record<string, Localized> = {
  READY: { en: 'ready', ru: 'готов' },
  ATTACHED: { en: 'attached', ru: 'привязан' },
  RUNNING: { en: 'running', ru: 'выполняется' },
  RUNNABLE: { en: 'RUNNABLE', ru: 'RUNNABLE' },
  DONE: { en: 'done', ru: 'готово' },
  NEW: { en: 'NEW', ru: 'NEW' },
  DIRECT_RUN: { en: 'direct run()', ru: 'прямой run()' },
  TERMINATED: { en: 'TERMINATED', ru: 'TERMINATED' },
};

const KIND_LABELS: Record<string, Localized> = {
  THREAD_WITH_RUNNABLE: { en: 'Thread with Runnable', ru: 'Thread с Runnable' },
  THREAD_SUBCLASS: { en: 'Thread subclass', ru: 'подкласс Thread' },
};

const ACTION_LABELS: Record<string, Localized> = {
  CREATE_RUNNABLE: { en: 'created Runnable', ru: 'создал Runnable' },
  CREATE_THREAD: { en: 'created Thread', ru: 'создал Thread' },
  CREATE_SUBCLASS: { en: 'created Thread subclass', ru: 'создал подкласс Thread' },
  START_THREAD: { en: 'called start()', ru: 'вызвал start()' },
  RUN_TASK: { en: 'ran Runnable', ru: 'выполнил Runnable' },
  ENTER_RUN: { en: 'entered run()', ru: 'вошел в run()' },
  TERMINATE_THREAD: { en: 'finished Thread', ru: 'завершил Thread' },
  CALL_RUN_DIRECTLY: { en: 'called run() directly', ru: 'вызвал run() напрямую' },
  RETURN_RUN: { en: 'returned from run()', ru: 'вернулся из run()' },
  REUSE_RUNNABLE: { en: 'reused Runnable', ru: 'переиспользовал Runnable' },
};

interface RunnableSnapshot {
  name: string;
  state: string;
  attachedTo: string[];
  runs: number;
}

interface ThreadSnapshot {
  name: string;
  state: string;
  kind: string;
  task: string;
  started: boolean;
  executionThread?: string;
}

interface HistoryItem {
  actor: string;
  action: string;
  target: string;
}

interface ThreadState {
  name: string;
  currentThread: string;
  runnables: RunnableSnapshot[];
  threads: ThreadSnapshot[];
  history: HistoryItem[];
}

export default function ThreadVsRunnableVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as ThreadState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  const runnableNodes: LinkedNode[] = state.runnables.map((task) => {
    const status = STATE_LABELS[task.state] ?? { en: task.state, ru: task.state };
    const attached = task.attachedTo.length === 0
      ? tl(LABELS.noTarget, lang)
      : `${tl(LABELS.attachedTo, lang)} ${task.attachedTo.join(', ')}`;
    return {
      id: task.name,
      title: task.name,
      subtitle: `${tl(status, lang)} · ${attached} · ${tl(LABELS.runs, lang)} ${task.runs}`,
      highlighted: highlight.has(`task:${task.name}`),
    };
  });

  const threadNodes: LinkedNode[] = state.threads.map((thread) => {
    const status = STATE_LABELS[thread.state] ?? { en: thread.state, ru: thread.state };
    const kind = KIND_LABELS[thread.kind] ?? { en: thread.kind, ru: thread.kind };
    const started = thread.started ? tl(LABELS.started, lang) : tl(LABELS.notStarted, lang);
    const execution = thread.executionThread
      ? ` · ${tl(LABELS.execution, lang)} ${thread.executionThread}`
      : '';
    return {
      id: thread.name,
      title: thread.name,
      subtitle: `${tl(kind, lang)} · ${tl(status, lang)} · ${tl(LABELS.task, lang)} ${thread.task} · ${started}${execution}`,
      highlighted: highlight.has(`thread:${thread.name}`),
    };
  });

  const cells: ArrayCell[] = [
    {
      key: 'current',
      label: tl(LABELS.currentThread, lang),
      highlighted: highlight.has('current'),
      content: <strong style={monoStyle}>{state.currentThread}</strong>,
    },
    {
      key: 'tasks',
      label: tl(LABELS.runnableTasks, lang),
      content: <LinkedNodes nodes={runnableNodes} />,
    },
    {
      key: 'threads',
      label: tl(LABELS.threadObjects, lang),
      content: <LinkedNodes nodes={threadNodes} />,
    },
  ];

  return (
    <div style={wrapStyle}>
      <ArrayGrid cells={cells} />
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

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
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
  minWidth: 56,
};
const monoStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700 };
