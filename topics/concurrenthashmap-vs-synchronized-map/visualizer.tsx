import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to compare a synchronized map with a ConcurrentHashMap.',
    ru: 'Запустите код, чтобы сравнить synchronized map и ConcurrentHashMap.',
  },
  map: { en: 'map', ru: 'map' },
  strategy: { en: 'strategy', ru: 'стратегия' },
  tableLock: { en: 'table lock', ru: 'lock всей таблицы' },
  free: { en: 'free', ru: 'свободен' },
  heldBy: { en: 'held by', ru: 'держит' },
  waiting: { en: 'waiting', ru: 'ожидают' },
  bins: { en: 'bins', ru: 'bins' },
  locked: { en: 'locked', ru: 'заблокирован' },
  bin: { en: 'bin', ru: 'bin' },
  threads: { en: 'threads', ru: 'threads' },
  recentActions: { en: 'recent actions', ru: 'последние действия' },
};

const KIND_LABELS: Record<string, Localized> = {
  SYNCHRONIZED_MAP: { en: 'synchronized HashMap', ru: 'synchronized HashMap' },
  CONCURRENT_HASH_MAP: { en: 'ConcurrentHashMap', ru: 'ConcurrentHashMap' },
};

const STRATEGY_LABELS: Record<string, Localized> = {
  SYNCHRONIZED_MAP: {
    en: 'one table-wide monitor, reads included',
    ru: 'один monitor на всю таблицу, включая чтения',
  },
  CONCURRENT_HASH_MAP: {
    en: 'per-bin lock striping, lock-free reads',
    ru: 'lock striping по bins, чтения без lock',
  },
};

const STATUS_LABELS: Record<string, Localized> = {
  READY: { en: 'ready', ru: 'готов' },
  OWNS_LOCK: { en: 'owns lock', ru: 'владеет lock' },
  BLOCKED: { en: 'blocked', ru: 'заблокирован' },
  RUNNING: { en: 'running', ru: 'работает' },
  DONE: { en: 'done', ru: 'завершил' },
};

const ACTION_LABELS: Record<string, Localized> = {
  SYNC_LOCK_ACQUIRED: { en: 'lock acquired', ru: 'lock получен' },
  SYNC_BLOCKED: { en: 'blocked', ru: 'заблокирован' },
  SYNC_PUT: { en: 'put', ru: 'put' },
  SYNC_GET: { en: 'get', ru: 'get' },
  SYNC_LOCK_RELEASED: { en: 'lock released', ru: 'lock освобождён' },
  CHM_BIN_LOCK_ACQUIRED: { en: 'bin locked', ru: 'bin заблокирован' },
  CHM_BIN_BLOCKED: { en: 'bin blocked', ru: 'bin занят' },
  CHM_PUT: { en: 'put', ru: 'put' },
  CHM_BIN_LOCK_RELEASED: { en: 'bin released', ru: 'bin освобождён' },
  CHM_GET: { en: 'lock-free get', ru: 'get без lock' },
  CHM_ATOMIC: { en: 'atomic op', ru: 'атомарная операция' },
};

interface MapEntry {
  key: string;
  value: string;
}

interface BinSnapshot {
  index: number;
  lockOwner: string | null;
  entries: MapEntry[];
}

interface ThreadSnapshot {
  name: string;
  status: string;
  operation?: string;
}

interface HistoryItem {
  thread: string;
  action: string;
  detail?: string;
}

interface MapState {
  name: string;
  kind: string;
  strategy: string;
  capacity: number;
  mapLockOwner: string | null;
  waitingQueue: string[];
  bins: BinSnapshot[];
  threads: ThreadSnapshot[];
  history: HistoryItem[];
}

export default function ConcurrentMapVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as MapState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const kindLabel = KIND_LABELS[state.kind] ?? { en: state.kind, ru: state.kind };
  const strategyLabel = STRATEGY_LABELS[state.kind] ?? { en: state.strategy, ru: state.strategy };
  const isSync = state.kind === 'SYNCHRONIZED_MAP';

  const waitingNodes: LinkedNode[] = state.waitingQueue.map((thread) => ({
    id: thread,
    title: thread,
    subtitle: tl(LABELS.waiting, lang),
    highlighted: highlight.has(`queue:${thread}`) || highlight.has(`thread:${thread}`),
  }));

  // Only the bins that hold entries or are currently locked are worth showing.
  const visibleBins = state.bins.filter(
    (b) => b.entries.length > 0 || b.lockOwner !== null,
  );

  const binCells: ArrayCell[] = visibleBins.map((b) => {
    const entryBoxes: Box[] = b.entries.map((entry) => ({
      id: `${b.index}-${entry.key}`,
      title: `${entry.key} → ${entry.value}`,
      highlighted: highlight.has(`entry:${entry.key}`),
    }));
    return {
      key: b.index,
      label: `${tl(LABELS.bin, lang)} ${b.index}`,
      highlighted: highlight.has(`bin:${b.index}`),
      content: (
        <div style={binRowStyle}>
          <span
            style={{
              ...lockBadgeStyle,
              ...(b.lockOwner ? lockHeldStyle : lockFreeStyle),
            }}
          >
            {b.lockOwner
              ? `🔒 ${b.lockOwner}`
              : tl(LABELS.free, lang)}
          </span>
          <BoxGroup boxes={entryBoxes} />
        </div>
      ),
    };
  });

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
        <Stat label={tl(LABELS.map, lang)} value={`${tl(kindLabel, lang)}: ${state.name}`} wide />
        <Stat label={tl(LABELS.strategy, lang)} value={tl(strategyLabel, lang)} wide />
      </div>

      {isSync && (
        <section style={sectionStyle}>
          <div style={titleStyle}>{tl(LABELS.tableLock, lang)}</div>
          <div
            style={{
              ...tableLockStyle,
              ...(highlight.has('mapLock') ? tableLockHotStyle : {}),
            }}
          >
            <strong>
              {state.mapLockOwner
                ? `🔒 ${tl(LABELS.heldBy, lang)}: ${state.mapLockOwner}`
                : `🔓 ${tl(LABELS.free, lang)}`}
            </strong>
            <LinkedNodes nodes={waitingNodes} />
          </div>
        </section>
      )}

      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.bins, lang)}</div>
        <ArrayGrid cells={binCells} />
        {!isSync && waitingNodes.length > 0 && (
          <div style={binWaitStyle}>
            <span style={titleStyle}>{tl(LABELS.waiting, lang)}:</span>
            <LinkedNodes nodes={waitingNodes} />
          </div>
        )}
      </section>

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
const sectionStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const titleStyle: CSSProperties = { fontSize: 12, fontWeight: 700, opacity: 0.7 };
const tableLockStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 10,
  flexWrap: 'wrap',
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '6px 10px',
  background: 'var(--viz-box)',
};
const tableLockHotStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.30)',
};
const binRowStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' };
const binWaitStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' };
const lockBadgeStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 11,
  padding: '2px 6px',
  borderRadius: 4,
  minWidth: 52,
  textAlign: 'center',
};
const lockHeldStyle: CSSProperties = {
  background: 'rgba(255,107,107,0.18)',
  border: '1px solid rgba(255,107,107,0.5)',
};
const lockFreeStyle: CSSProperties = {
  background: 'var(--viz-badge)',
  border: '1px solid var(--border)',
  opacity: 0.7,
};
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
