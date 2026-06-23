import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize lock ownership.',
    ru: 'Запустите код, чтобы увидеть владение lock.',
  },
  lock: { en: 'lock', ru: 'lock' },
  free: { en: 'free', ru: 'свободен' },
  owner: { en: 'owner', ru: 'владелец' },
  waiting: { en: 'waiting queue', ru: 'очередь ожидания' },
  sharedValue: { en: 'shared value', ru: 'общее значение' },
  threads: { en: 'threads', ru: 'threads' },
  recentActions: { en: 'recent actions', ru: 'последние действия' },
  lastRead: { en: 'last read', ru: 'последнее чтение' },
};

const STATUS_LABELS: Record<string, Localized> = {
  READY: { en: 'ready', ru: 'готов' },
  IN_SECTION: { en: 'in critical section', ru: 'в критической секции' },
  WAITING: { en: 'waiting', ru: 'ждёт' },
  DONE: { en: 'done', ru: 'завершил' },
  RUNNING_UNPROTECTED: { en: 'unprotected', ru: 'без защиты' },
  OUTSIDE_WORK: { en: 'outside work', ru: 'работа вне секции' },
};

const ACTION_LABELS: Record<string, Localized> = {
  ENTER: { en: 'entered', ru: 'вошёл' },
  WAIT: { en: 'waits', ru: 'ждёт' },
  READ: { en: 'read', ru: 'прочитал' },
  WRITE: { en: 'wrote', ru: 'записал' },
  EXIT: { en: 'exited', ru: 'вышел' },
  UNSAFE_READ: { en: 'unsafe read', ru: 'чтение без lock' },
  UNSAFE_WRITE: { en: 'unsafe write', ru: 'запись без lock' },
  LOST_UPDATE: { en: 'lost update', ru: 'потерянное обновление' },
  OUTSIDE_WORK: { en: 'outside work', ru: 'работа вне секции' },
};

interface ThreadSnapshot {
  name: string;
  status: string;
  lastRead?: number;
}

interface HistoryItem {
  thread: string;
  action: string;
  value?: number;
}

interface CriticalSectionState {
  name: string;
  sharedValue: number;
  owner: string | null;
  waitingQueue: string[];
  threads: ThreadSnapshot[];
  history: HistoryItem[];
}

export default function CriticalSectionVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as CriticalSectionState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const queueNodes: LinkedNode[] = state.waitingQueue.map((thread) => ({
    id: thread,
    title: thread,
    subtitle: tl(STATUS_LABELS.WAITING, lang),
    highlighted: highlight.has(`queue:${thread}`) || highlight.has(`thread:${thread}`),
  }));

  const cells: ArrayCell[] = [
    {
      key: 'lock',
      label: tl(LABELS.lock, lang),
      highlighted: state.owner ? highlight.has(`owner:${state.owner}`) : false,
      content: (
        <strong>
          {state.owner
            ? `${tl(LABELS.owner, lang)}: ${state.owner}`
            : tl(LABELS.free, lang)}
        </strong>
      ),
    },
    {
      key: 'waiting',
      label: tl(LABELS.waiting, lang),
      content: <LinkedNodes nodes={queueNodes} />,
    },
    {
      key: 'value',
      label: tl(LABELS.sharedValue, lang),
      highlighted: highlight.has('value'),
      content: <strong style={valueStyle}>{state.sharedValue}</strong>,
    },
  ];

  const threadBoxes: Box[] = state.threads.map((thread) => {
    const status = STATUS_LABELS[thread.status] ?? { en: thread.status, ru: thread.status };
    const lastRead =
      thread.lastRead === undefined ? '' : ` · ${tl(LABELS.lastRead, lang)} ${thread.lastRead}`;
    return {
      id: thread.name,
      title: thread.name,
      subtitle: `${tl(status, lang)}${lastRead}`,
      highlighted: highlight.has(`thread:${thread.name}`) || highlight.has(`owner:${thread.name}`),
      dim: thread.status === 'DONE',
    };
  });

  return (
    <div style={wrapStyle}>
      <ArrayGrid cells={cells} />
      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.threads, lang)}</div>
        <BoxGroup boxes={threadBoxes} />
      </section>
      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.recentActions, lang)}</div>
        <div style={historyStyle}>
          {state.history.map((item, index) => {
            const label = ACTION_LABELS[item.action] ?? { en: item.action, ru: item.action };
            const value = item.value === undefined ? '' : ` = ${item.value}`;
            return (
              <div key={`${item.thread}-${item.action}-${index}`} style={historyItemStyle}>
                <span style={threadStyle}>{item.thread}</span>
                <span>{tl(label, lang)}{value}</span>
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
const valueStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 18 };
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
const threadStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontWeight: 700,
  minWidth: 28,
};
