import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize Singleton creation and publication.',
    ru: 'Запустите код, чтобы увидеть создание и публикацию Singleton.',
  },
  strategy: { en: 'strategy', ru: 'стратегия' },
  instance: { en: 'instance', ru: 'экземпляр' },
  none: { en: 'none', ru: 'нет' },
  constructorCalls: { en: 'constructor calls', ru: 'вызовы конструктора' },
  lock: { en: 'class lock', ru: 'class lock' },
  free: { en: 'free', ru: 'свободен' },
  owner: { en: 'owner', ru: 'владелец' },
  volatileField: { en: 'volatile field', ru: 'volatile поле' },
  enumBased: { en: 'enum based', ru: 'на основе enum' },
  yes: { en: 'yes', ru: 'да' },
  no: { en: 'no', ru: 'нет' },
  threads: { en: 'threads', ru: 'threads' },
  recentActions: { en: 'recent actions', ru: 'последние действия' },
  observed: { en: 'observed', ru: 'увидел' },
};

const STATUS_LABELS: Record<string, Localized> = {
  READY: { en: 'ready', ru: 'готов' },
  CHECKING: { en: 'checking field', ru: 'проверяет поле' },
  CHECKED_NULL: { en: 'saw null', ru: 'увидел null' },
  CONSTRUCTED: { en: 'constructed', ru: 'создал' },
  IN_SYNCHRONIZED_METHOD: { en: 'inside synchronized method', ru: 'внутри synchronized method' },
  IN_DCL_LOCK: { en: 'inside DCL lock', ru: 'внутри DCL lock' },
  FIRST_CHECK: { en: 'first check', ru: 'первая проверка' },
  SECOND_CHECK: { en: 'second check', ru: 'вторая проверка' },
  REUSED: { en: 'reused instance', ru: 'переиспользовал экземпляр' },
  ENUM_ACCESS: { en: 'read enum constant', ru: 'прочитал enum-константу' },
  DONE: { en: 'done', ru: 'завершил' },
};

const ACTION_LABELS: Record<string, Localized> = {
  UNSAFE_CHECK: { en: 'unsafe null check', ru: 'небезопасная проверка null' },
  CONSTRUCT: { en: 'constructs instance', ru: 'создаёт экземпляр' },
  ENTER_LOCK: { en: 'enters lock', ru: 'входит в lock' },
  EXIT_LOCK: { en: 'exits lock', ru: 'выходит из lock' },
  DCL_FIRST_CHECK: { en: 'DCL first check', ru: 'первая проверка DCL' },
  DCL_SECOND_CHECK: { en: 'DCL second check', ru: 'вторая проверка DCL' },
  VOLATILE_PUBLISH: { en: 'volatile publish', ru: 'volatile публикация' },
  REUSE: { en: 'reuses instance', ru: 'переиспользует экземпляр' },
  ENUM_INIT: { en: 'JVM initializes enum', ru: 'JVM инициализирует enum' },
  ENUM_ACCESS: { en: 'reads enum constant', ru: 'читает enum-константу' },
};

interface ThreadSnapshot {
  name: string;
  status: string;
  observedInstance?: string;
}

interface HistoryItem {
  thread: string;
  action: string;
  value?: string;
}

interface SingletonState {
  name: string;
  strategy: string;
  instance: string | null;
  constructorCalls: number;
  lockOwner: string | null;
  volatileField: boolean;
  enumBased: boolean;
  threads: ThreadSnapshot[];
  history: HistoryItem[];
}

export default function SingletonVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as SingletonState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const summaryCells: ArrayCell[] = [
    {
      key: 'strategy',
      label: tl(LABELS.strategy, lang),
      content: <strong>{state.strategy}</strong>,
    },
    {
      key: 'instance',
      label: tl(LABELS.instance, lang),
      highlighted: highlight.has('instance') || event?.event === 'SINGLETON_DUPLICATE_CREATED',
      content: <strong style={monoStyle}>{state.instance ?? tl(LABELS.none, lang)}</strong>,
    },
    {
      key: 'constructorCalls',
      label: tl(LABELS.constructorCalls, lang),
      highlighted: state.constructorCalls > 1,
      content: <strong style={monoStyle}>{state.constructorCalls}</strong>,
    },
    {
      key: 'lock',
      label: tl(LABELS.lock, lang),
      highlighted: highlight.has('lock'),
      content: (
        <strong>
          {state.lockOwner
            ? `${tl(LABELS.owner, lang)}: ${state.lockOwner}`
            : tl(LABELS.free, lang)}
        </strong>
      ),
    },
    {
      key: 'volatileField',
      label: tl(LABELS.volatileField, lang),
      highlighted: event?.event === 'SINGLETON_VOLATILE_PUBLISH',
      content: <span>{tl(state.volatileField ? LABELS.yes : LABELS.no, lang)}</span>,
    },
    {
      key: 'enumBased',
      label: tl(LABELS.enumBased, lang),
      highlighted: event?.event === 'SINGLETON_ENUM_READY' || event?.event === 'SINGLETON_ENUM_ACCESS',
      content: <span>{tl(state.enumBased ? LABELS.yes : LABELS.no, lang)}</span>,
    },
  ];

  const threadCells: ArrayCell[] = state.threads.map((thread) => {
    const status = STATUS_LABELS[thread.status] ?? { en: thread.status, ru: thread.status };
    const observed = thread.observedInstance
      ? ` · ${tl(LABELS.observed, lang)} ${thread.observedInstance}`
      : '';
    return {
      key: thread.name,
      label: thread.name,
      highlighted: highlight.has(`thread:${thread.name}`),
      content: <span>{tl(status, lang)}{observed}</span>,
    };
  });

  return (
    <div style={wrapStyle}>
      <ArrayGrid cells={summaryCells} />
      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.threads, lang)}</div>
        <ArrayGrid cells={threadCells} />
      </section>
      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.recentActions, lang)}</div>
        <div style={historyStyle}>
          {state.history.map((item, index) => {
            const label = ACTION_LABELS[item.action] ?? { en: item.action, ru: item.action };
            const value = item.value ? ` = ${item.value}` : '';
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
const monoStyle: CSSProperties = { fontFamily: 'monospace' };
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
  minWidth: 70,
};
