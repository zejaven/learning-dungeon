import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize volatile visibility and ordering.',
    ru: 'Запустите код, чтобы увидеть visibility и ordering для volatile.',
  },
  mainMemory: { en: 'main memory', ru: 'main memory' },
  threadViews: { en: 'thread local views', ru: 'локальные виды threads' },
  recentActions: { en: 'recent actions', ru: 'последние действия' },
  status: { en: 'status', ru: 'статус' },
  localData: { en: 'local data', ru: 'локальное data' },
  localReady: { en: 'local ready', ru: 'локальное ready' },
  localCounter: { en: 'local counter', ru: 'локальный counter' },
};

const KIND_LABELS: Record<string, Localized> = {
  PLAIN_INT: { en: 'plain int', ru: 'обычный int' },
  PLAIN_BOOLEAN: { en: 'plain boolean', ru: 'обычный boolean' },
  VOLATILE_BOOLEAN: { en: 'volatile boolean', ru: 'volatile boolean' },
  VOLATILE_INT: { en: 'volatile int', ru: 'volatile int' },
};

const STATUS_LABELS: Record<string, Localized> = {
  READY: { en: 'ready', ru: 'готов' },
  WROTE_DATA: { en: 'wrote data', ru: 'записал data' },
  READ_DATA: { en: 'read data', ru: 'прочитал data' },
  VOLATILE_WRITE: { en: 'volatile write', ru: 'volatile-запись' },
  PLAIN_WRITE: { en: 'plain write', ru: 'обычная запись' },
  VOLATILE_READ: { en: 'volatile read', ru: 'volatile-чтение' },
  PLAIN_READ: { en: 'plain read', ru: 'обычное чтение' },
  REFRESHED: { en: 'refreshed', ru: 'обновлён' },
  VOLATILE_COUNTER_READ: { en: 'counter read', ru: 'чтение counter' },
  VOLATILE_COUNTER_WRITE: { en: 'counter write', ru: 'запись counter' },
};

const ACTION_LABELS: Record<string, Localized> = {
  WRITE_DATA: { en: 'wrote', ru: 'записал' },
  READ_DATA: { en: 'read', ru: 'прочитал' },
  READ_DATA_STALE: { en: 'read stale', ru: 'прочитал устаревшее' },
  VOLATILE_WRITE_READY: { en: 'volatile write', ru: 'volatile-запись' },
  PLAIN_WRITE_READY: { en: 'plain write', ru: 'обычная запись' },
  VOLATILE_READ_READY: { en: 'volatile read', ru: 'volatile-чтение' },
  PLAIN_READ_READY: { en: 'plain read', ru: 'обычное чтение' },
  PLAIN_READ_READY_STALE: { en: 'read stale', ru: 'прочитал устаревшее' },
  REFRESH_PLAIN: { en: 'refreshed', ru: 'обновил' },
  VOLATILE_COUNTER_READ: { en: 'volatile read', ru: 'volatile-чтение' },
  VOLATILE_COUNTER_WRITE: { en: 'volatile write', ru: 'volatile-запись' },
  LOST_UPDATE: { en: 'lost update', ru: 'потерянное обновление' },
};

interface VariableSnapshot {
  name: string;
  kind: string;
  value: string | number | boolean;
}

interface ThreadSnapshot {
  name: string;
  status: string;
  dataView: number;
  readyView: boolean;
  counterView: number;
}

interface HistoryItem {
  thread: string;
  action: string;
  target: string;
  value: string | number | boolean;
}

interface VolatileState {
  name: string;
  readyVolatile: boolean;
  variables: VariableSnapshot[];
  threads: ThreadSnapshot[];
  history: HistoryItem[];
}

export default function VolatileVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as VolatileState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  const memoryCells: ArrayCell[] = state.variables.map((variable) => {
    const kind = KIND_LABELS[variable.kind] ?? { en: variable.kind, ru: variable.kind };
    return {
      key: variable.name,
      label: variable.name,
      highlighted: highlight.has(`memory:${variable.name}`),
      content: (
        <div style={valueWrapStyle}>
          <strong style={valueStyle}>{formatValue(variable.value)}</strong>
          <span style={metaStyle}>{tl(kind, lang)}</span>
        </div>
      ),
    };
  });

  const threadCells: ArrayCell[] = state.threads.map((thread) => {
    const status = STATUS_LABELS[thread.status] ?? { en: thread.status, ru: thread.status };
    return {
      key: thread.name,
      label: thread.name,
      highlighted: highlight.has(`thread:${thread.name}`),
      content: (
        <div style={threadViewStyle}>
          <span>
            {tl(LABELS.status, lang)}: <strong>{tl(status, lang)}</strong>
          </span>
          <span>{tl(LABELS.localData, lang)}: {thread.dataView}</span>
          <span>{tl(LABELS.localReady, lang)}: {formatValue(thread.readyView)}</span>
          <span>{tl(LABELS.localCounter, lang)}: {thread.counterView}</span>
        </div>
      ),
    };
  });

  return (
    <div style={wrapStyle}>
      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.mainMemory, lang)}</div>
        <ArrayGrid cells={memoryCells} />
      </section>
      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.threadViews, lang)}</div>
        <ArrayGrid cells={threadCells} />
      </section>
      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.recentActions, lang)}</div>
        <div style={historyStyle}>
          {state.history.map((item, index) => {
            const action = ACTION_LABELS[item.action] ?? { en: item.action, ru: item.action };
            return (
              <div key={`${item.thread}-${item.action}-${index}`} style={historyItemStyle}>
                <span style={threadStyle}>{item.thread}</span>
                <span>{tl(action, lang)}</span>
                <span style={monoStyle}>{item.target} = {formatValue(item.value)}</span>
              </div>
            );
          })}
        </div>
      </section>
    </div>
  );
}

function formatValue(value: string | number | boolean) {
  return String(value);
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
const sectionStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const titleStyle: CSSProperties = { fontSize: 12, fontWeight: 700, opacity: 0.7 };
const valueWrapStyle: CSSProperties = { display: 'flex', alignItems: 'baseline', gap: 8 };
const valueStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 18 };
const metaStyle: CSSProperties = { fontSize: 12, opacity: 0.65 };
const threadViewStyle: CSSProperties = {
  display: 'flex',
  flexWrap: 'wrap',
  gap: '6px 12px',
  fontSize: 13,
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
  minWidth: 56,
};
const monoStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700 };
