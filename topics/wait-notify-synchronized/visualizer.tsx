import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang, type Localized, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize the monitor, wait set and entry set.',
    ru: 'Запустите код, чтобы увидеть monitor, wait set и entry set.',
  },
  monitor: { en: 'monitor', ru: 'monitor' },
  owner: { en: 'owner', ru: 'владелец' },
  free: { en: 'free', ru: 'свободен' },
  condition: { en: 'condition', ru: 'condition' },
  ready: { en: 'ready', ru: 'готово' },
  notReady: { en: 'not ready', ru: 'не готово' },
  waitSet: { en: 'wait set', ru: 'wait set' },
  entrySet: { en: 'entry set', ru: 'entry set' },
  threads: { en: 'threads', ru: 'threads' },
  recentActions: { en: 'recent actions', ru: 'последние действия' },
  reason: { en: 'reason', ru: 'причина' },
};

const STATUS_LABELS: Record<string, Localized> = {
  OUTSIDE: { en: 'outside', ru: 'снаружи' },
  IN_SYNCHRONIZED: { en: 'in synchronized', ru: 'в synchronized' },
  BLOCKED: { en: 'blocked on entry', ru: 'блокирован на входе' },
  WAITING: { en: 'waiting', ru: 'ожидает' },
  BLOCKED_AFTER_NOTIFY: { en: 'notified, blocked', ru: 'разбужен, блокирован' },
  BLOCKED_AFTER_SPURIOUS: { en: 'spurious, blocked', ru: 'spurious, блокирован' },
  REACQUIRED_AFTER_WAIT: { en: 're-acquired', ru: 'захватил заново' },
};

const REASON_LABELS: Record<string, Localized> = {
  enter: { en: 'enter synchronized', ru: 'вход в synchronized' },
  notified: { en: 'notified', ru: 'разбужен' },
  spurious: { en: 'spurious wakeup', ru: 'spurious wakeup' },
};

const ACTION_LABELS: Record<string, Localized> = {
  CREATE: { en: 'created', ru: 'создан' },
  ENTER: { en: 'entered', ru: 'вошёл' },
  REENTER: { en: 're-entered', ru: 'вошёл повторно' },
  BLOCKED: { en: 'blocked', ru: 'блокирован' },
  CHECK_TRUE: { en: 'checked true', ru: 'проверил true' },
  CHECK_FALSE: { en: 'checked false', ru: 'проверил false' },
  CONDITION_TRUE: { en: 'condition true', ru: 'condition true' },
  CONDITION_FALSE: { en: 'condition false', ru: 'condition false' },
  WAIT: { en: 'called wait()', ru: 'вызвал wait()' },
  NOTIFY: { en: 'called notify()', ru: 'вызвал notify()' },
  NOTIFY_NONE: { en: 'notify() found nobody', ru: 'notify() никого не нашёл' },
  NOTIFY_ALL: { en: 'called notifyAll()', ru: 'вызвал notifyAll()' },
  SPURIOUS_WAKEUP: { en: 'spurious wakeup', ru: 'spurious wakeup' },
  EXIT: { en: 'exited', ru: 'вышел' },
  REACQUIRE: { en: 're-acquired', ru: 'захватил заново' },
  ENTER_FROM_ENTRY_SET: { en: 'entered from entry set', ru: 'вошёл из entry set' },
};

interface Waiter {
  thread: string;
}

interface Entry {
  thread: string;
  reason: string;
}

interface ThreadInfo {
  name: string;
  status: string;
  lastCheck?: boolean;
}

interface HistoryItem {
  actor: string;
  action: string;
  detail?: string;
}

interface MonitorState {
  name: string;
  conditionReady: boolean;
  owner: string | null;
  monitorState: 'FREE' | 'OWNED';
  waitSet: Waiter[];
  entrySet: Entry[];
  threads: ThreadInfo[];
  history: HistoryItem[];
}

export default function MonitorWaitNotifyVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as MonitorState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const cells: ArrayCell[] = [
    {
      key: 'monitor',
      label: tl(LABELS.monitor, lang),
      highlighted: highlight.has('monitor'),
      content: (
        <div style={lineStyle}>
          <strong style={monoStyle}>{state.name}</strong>
          <span style={{ ...badgeStyle, ...(state.owner ? activeBadgeStyle : {}) }}>
            {tl(LABELS.owner, lang)}: {state.owner ?? tl(LABELS.free, lang)}
          </span>
          <span style={{ ...badgeStyle, ...(state.conditionReady ? conditionReadyStyle : {}) }}>
            {tl(LABELS.condition, lang)}:{' '}
            {state.conditionReady ? tl(LABELS.ready, lang) : tl(LABELS.notReady, lang)}
          </span>
        </div>
      ),
    },
    {
      key: 'waitSet',
      label: tl(LABELS.waitSet, lang),
      highlighted: highlight.has('wait-set'),
      content: <LinkedNodes nodes={waitSetNodes(state.waitSet, highlight)} />,
    },
    {
      key: 'entrySet',
      label: tl(LABELS.entrySet, lang),
      content: <LinkedNodes nodes={entrySetNodes(state.entrySet, highlight, lang)} />,
    },
    {
      key: 'threads',
      label: tl(LABELS.threads, lang),
      content: <ThreadChips threads={state.threads} highlight={highlight} lang={lang} />,
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
              </div>
            );
          })}
        </div>
      </section>
    </div>
  );
}

function waitSetNodes(waiters: Waiter[], highlight: Set<string>): LinkedNode[] {
  return waiters.map((waiter) => ({
    id: waiter.thread,
    title: waiter.thread,
    subtitle: 'wait()',
    highlighted: highlight.has(`wait:${waiter.thread}`) || highlight.has(`thread:${waiter.thread}`),
  }));
}

function entrySetNodes(entries: Entry[], highlight: Set<string>, lang: Lang): LinkedNode[] {
  return entries.map((entry) => {
    const reason = REASON_LABELS[entry.reason] ?? { en: entry.reason, ru: entry.reason };
    return {
      id: entry.thread,
      title: entry.thread,
      subtitle: `${tl(LABELS.reason, lang)}: ${tl(reason, lang)}`,
      highlighted: highlight.has(`entry:${entry.thread}`) || highlight.has(`thread:${entry.thread}`),
    };
  });
}

function ThreadChips({
  threads,
  highlight,
  lang,
}: {
  threads: ThreadInfo[];
  highlight: Set<string>;
  lang: Lang;
}) {
  return (
    <div style={chipsStyle}>
      {threads.map((thread) => {
        const status = STATUS_LABELS[thread.status] ?? { en: thread.status, ru: thread.status };
        const isOwner = highlight.has(`owner:${thread.name}`);
        return (
          <span
            key={thread.name}
            style={{
              ...chipStyle,
              ...(highlight.has(`thread:${thread.name}`) || isOwner ? activeChipStyle : {}),
            }}
          >
            <strong style={monoStyle}>{thread.name}</strong>
            <span>{tl(status, lang)}</span>
          </span>
        );
      })}
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
const lineStyle: CSSProperties = {
  display: 'flex',
  flexWrap: 'wrap',
  alignItems: 'center',
  gap: 8,
};
const monoStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700 };
const badgeStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '3px 7px',
  background: 'var(--viz-box)',
  fontSize: 12,
};
const activeBadgeStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
};
const conditionReadyStyle: CSSProperties = {
  borderColor: 'rgba(112, 208, 144, 0.75)',
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
  minWidth: 92,
};
const chipsStyle: CSSProperties = {
  display: 'flex',
  flexWrap: 'wrap',
  gap: 6,
};
const chipStyle: CSSProperties = {
  display: 'inline-flex',
  gap: 6,
  alignItems: 'center',
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '4px 7px',
  background: 'var(--viz-box)',
  fontSize: 12,
};
const activeChipStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
};
