import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to compare synchronized and concurrent collection behavior.',
    ru: 'Запустите код, чтобы сравнить поведение synchronized и concurrent collections.',
  },
  monitor: { en: 'monitor', ru: 'monitor' },
  free: { en: 'free', ru: 'свободен' },
  owner: { en: 'owner', ru: 'владелец' },
  waiting: { en: 'waiting', ru: 'ожидают' },
  entries: { en: 'entries', ru: 'entries' },
  iterators: { en: 'iterators', ru: 'iterators' },
  threads: { en: 'threads', ru: 'threads' },
  recentActions: { en: 'recent actions', ru: 'последние действия' },
  collection: { en: 'collection', ru: 'collection' },
  strategy: { en: 'strategy', ru: 'стратегия' },
  version: { en: 'version', ru: 'version' },
  key: { en: 'key', ru: 'key' },
  index: { en: 'index', ru: 'index' },
  cursor: { en: 'cursor', ru: 'cursor' },
  snapshot: { en: 'snapshot', ru: 'snapshot' },
};

const KIND_LABELS: Record<string, Localized> = {
  SYNCHRONIZED_LIST: { en: 'synchronized list', ru: 'synchronized list' },
  CONCURRENT_HASH_MAP: { en: 'concurrent map', ru: 'concurrent map' },
  COPY_ON_WRITE_ARRAY_LIST: { en: 'copy-on-write list', ru: 'copy-on-write list' },
};

const STRATEGY_LABELS: Record<string, Localized> = {
  SYNCHRONIZED_LIST: {
    en: 'one monitor around every method',
    ru: 'один monitor вокруг каждого метода',
  },
  CONCURRENT_HASH_MAP: {
    en: 'internal concurrency without one global monitor',
    ru: 'внутренняя concurrency без одного общего monitor',
  },
  COPY_ON_WRITE_ARRAY_LIST: {
    en: 'snapshot reads, array copy on write',
    ru: 'snapshot reads, копия array при записи',
  },
};

const STATUS_LABELS: Record<string, Localized> = {
  READY: { en: 'ready', ru: 'готов' },
  OWNS_MONITOR: { en: 'owns monitor', ru: 'владеет monitor' },
  BLOCKED: { en: 'blocked', ru: 'заблокирован' },
  DONE: { en: 'done', ru: 'завершил' },
  RUNNING: { en: 'running', ru: 'работает' },
  ITERATING: { en: 'iterating', ru: 'обходит' },
};

const ITERATOR_MODE_LABELS: Record<string, Localized> = {
  FAIL_FAST: { en: 'fail-fast', ru: 'fail-fast' },
  WEAKLY_CONSISTENT: { en: 'weakly consistent', ru: 'weakly consistent' },
  SNAPSHOT: { en: 'snapshot', ru: 'snapshot' },
};

const ACTION_LABELS: Record<string, Localized> = {
  LOCK_ACQUIRED: { en: 'lock acquired', ru: 'lock получен' },
  BLOCKED: { en: 'blocked', ru: 'заблокирован' },
  SYNC_WRITE: { en: 'sync write', ru: 'sync write' },
  LOCK_RELEASED: { en: 'lock released', ru: 'lock освобождён' },
  ITERATOR_CREATED: { en: 'iterator created', ru: 'iterator создан' },
  FAIL_FAST: { en: 'fail-fast', ru: 'fail-fast' },
  CONCURRENT_WRITE: { en: 'concurrent write', ru: 'concurrent write' },
  CONCURRENT_READ: { en: 'concurrent read', ru: 'concurrent read' },
  PUT_IF_ABSENT: { en: 'putIfAbsent', ru: 'putIfAbsent' },
  WEAK_ITERATOR_CREATED: { en: 'weak iterator', ru: 'weak iterator' },
  WEAK_ITERATOR_CONTINUES: { en: 'weak iterator continues', ru: 'weak iterator продолжает' },
  SNAPSHOT_ITERATOR_CREATED: { en: 'snapshot iterator', ru: 'snapshot iterator' },
  COPY_ON_WRITE: { en: 'copy-on-write', ru: 'copy-on-write' },
  SNAPSHOT_ITERATOR_READ: { en: 'snapshot read', ru: 'snapshot read' },
  ITERATOR_NEXT: { en: 'iterator next', ru: 'iterator next' },
  ITERATOR_DONE: { en: 'iterator done', ru: 'iterator завершён' },
};

interface CollectionEntry {
  key: string;
  value: string;
}

interface ThreadSnapshot {
  name: string;
  status: string;
  operation?: string;
}

interface IteratorSnapshot {
  name: string;
  thread: string;
  mode: string;
  cursor: number;
  expectedVersion: number;
  status: string;
  snapshot: CollectionEntry[];
}

interface HistoryItem {
  thread: string;
  action: string;
  detail?: string;
}

interface CollectionState {
  name: string;
  kind: string;
  strategy: string;
  owner: string | null;
  version: number;
  waitingQueue: string[];
  entries: CollectionEntry[];
  threads: ThreadSnapshot[];
  iterators: IteratorSnapshot[];
  history: HistoryItem[];
}

export default function ConcurrentCollectionsVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as CollectionState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const kindLabel = KIND_LABELS[state.kind] ?? { en: state.kind, ru: state.kind };
  const strategyLabel = STRATEGY_LABELS[state.kind] ?? { en: state.strategy, ru: state.strategy };

  const waitingNodes: LinkedNode[] = state.waitingQueue.map((thread) => ({
    id: thread,
    title: thread,
    subtitle: tl(LABELS.waiting, lang),
    highlighted: highlight.has(`queue:${thread}`) || highlight.has(`thread:${thread}`),
  }));

  const entryBoxes: Box[] = state.entries.map((entry) => ({
    id: entry.key,
    title: state.kind === 'CONCURRENT_HASH_MAP' ? `${entry.key} => ${entry.value}` : entry.value,
    subtitle:
      state.kind === 'CONCURRENT_HASH_MAP'
        ? `${tl(LABELS.key, lang)} ${entry.key}`
        : `${tl(LABELS.index, lang)} ${entry.key}`,
    highlighted: highlight.has(`entry:${entry.key}`),
  }));

  const iteratorBoxes: Box[] = state.iterators.map((iterator) => {
    const mode = ITERATOR_MODE_LABELS[iterator.mode] ?? { en: iterator.mode, ru: iterator.mode };
    return {
      id: iterator.name,
      title: iterator.name,
      subtitle: `${tl(mode, lang)} | ${iterator.thread} | ${tl(LABELS.cursor, lang)} ${iterator.cursor}`,
      highlighted: highlight.has(`iterator:${iterator.name}`),
      dim: iterator.status === 'INVALIDATED' || iterator.status === 'EXHAUSTED',
    };
  });

  const cells: ArrayCell[] = [
    {
      key: 'monitor',
      label: tl(LABELS.monitor, lang),
      highlighted: highlight.has('lock'),
      content: (
        <div style={monitorStyle}>
          <strong>
            {state.owner
              ? `${tl(LABELS.owner, lang)}: ${state.owner}`
              : tl(LABELS.free, lang)}
          </strong>
          <LinkedNodes nodes={waitingNodes} />
        </div>
      ),
    },
    {
      key: 'entries',
      label: tl(LABELS.entries, lang),
      highlighted: highlight.has('array'),
      content: <BoxGroup boxes={entryBoxes} />,
    },
    {
      key: 'iterators',
      label: tl(LABELS.iterators, lang),
      content: <BoxGroup boxes={iteratorBoxes} />,
    },
  ];

  const threadBoxes: Box[] = state.threads.map((thread) => {
    const status = STATUS_LABELS[thread.status] ?? { en: thread.status, ru: thread.status };
    return {
      id: thread.name,
      title: thread.name,
      subtitle: thread.operation
        ? `${tl(status, lang)} | ${thread.operation}`
        : tl(status, lang),
      highlighted: highlight.has(`thread:${thread.name}`) || highlight.has(`owner:${thread.name}`),
      dim: thread.status === 'DONE',
    };
  });

  return (
    <div style={wrapStyle}>
      <div style={summaryStyle}>
        <Stat label={tl(LABELS.collection, lang)} value={`${tl(kindLabel, lang)}: ${state.name}`} />
        <Stat label={tl(LABELS.strategy, lang)} value={tl(strategyLabel, lang)} wide />
        <Stat label={tl(LABELS.version, lang)} value={state.version} />
      </div>

      <ArrayGrid cells={cells} />

      {state.iterators.some((iterator) => iterator.snapshot.length > 0) && (
        <section style={sectionStyle}>
          <div style={titleStyle}>{tl(LABELS.snapshot, lang)}</div>
          <div style={snapshotGridStyle}>
            {state.iterators
              .filter((iterator) => iterator.snapshot.length > 0)
              .map((iterator) => (
                <div key={iterator.name} style={snapshotRowStyle}>
                  <span style={threadStyle}>{iterator.name}</span>
                  <BoxGroup
                    boxes={iterator.snapshot.map((entry) => ({
                      id: `${iterator.name}-${entry.key}`,
                      title: entry.value,
                      subtitle: `${tl(LABELS.index, lang)} ${entry.key}`,
                      highlighted: highlight.has(`iterator:${iterator.name}`),
                    }))}
                  />
                </div>
              ))}
          </div>
        </section>
      )}

      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.threads, lang)}</div>
        <BoxGroup boxes={threadBoxes} />
      </section>

      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.recentActions, lang)}</div>
        <div style={historyStyle}>
          {state.history.map((item, index) => {
            const label = ACTION_LABELS[item.action] ?? { en: item.action, ru: item.action };
            return (
              <div key={`${item.thread}-${item.action}-${index}`} style={historyItemStyle}>
                <span style={threadStyle}>{item.thread}</span>
                <span>
                  {tl(label, lang)}
                  {item.detail ? `: ${item.detail}` : ''}
                </span>
              </div>
            );
          })}
        </div>
      </section>
    </div>
  );
}

function Stat({ label, value, wide }: { label: string; value: string | number; wide?: boolean }) {
  return (
    <div style={{ ...statStyle, ...(wide ? wideStatStyle : {}) }}>
      <div style={statLabelStyle}>{label}</div>
      <div style={statValueStyle}>{value}</div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
const summaryStyle: CSSProperties = { display: 'flex', gap: 10, flexWrap: 'wrap' };
const statStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '6px 8px',
  background: 'var(--viz-box)',
  minWidth: 96,
};
const wideStatStyle: CSSProperties = { flex: '1 1 220px' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.65 };
const statValueStyle: CSSProperties = { fontSize: 13, fontWeight: 700 };
const monitorStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' };
const sectionStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const titleStyle: CSSProperties = { fontSize: 12, fontWeight: 700, opacity: 0.7 };
const snapshotGridStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const snapshotRowStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' };
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
  minWidth: 44,
};
