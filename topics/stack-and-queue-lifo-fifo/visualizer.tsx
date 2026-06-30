import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize LIFO and FIFO order.',
    ru: 'Запустите код, чтобы увидеть порядок LIFO и FIFO.',
  },
  size: { en: 'size', ru: 'размер' },
  operation: { en: 'operation', ru: 'операция' },
  result: { en: 'result', ru: 'результат' },
  rule: { en: 'rule', ru: 'правило' },
  cost: { en: 'cost', ru: 'стоимость' },
  none: { en: 'none', ru: 'нет' },
};

const STRUCTURES: Record<string, Localized> = {
  stack: { en: 'Stack', ru: 'Stack' },
  queue: { en: 'Queue', ru: 'Queue' },
};

const ENDS: Record<string, Localized> = {
  bottom: { en: 'bottom', ru: 'bottom' },
  top: { en: 'top', ru: 'top' },
  front: { en: 'front', ru: 'front' },
  back: { en: 'back', ru: 'back' },
};

const ROLES: Record<string, Localized> = {
  bottom: { en: 'bottom', ru: 'bottom' },
  top: { en: 'top', ru: 'top' },
  front: { en: 'front', ru: 'front' },
  back: { en: 'back', ru: 'back' },
  middle: { en: 'waiting', ru: 'ожидает' },
};

const RULES: Record<string, Localized> = {
  CREATE: { en: 'ready', ru: 'готово' },
  LIFO: { en: 'LIFO: last in, first out', ru: 'LIFO: последним вошел - первым вышел' },
  FIFO: { en: 'FIFO: first in, first out', ru: 'FIFO: первым вошел - первым вышел' },
  PEEK: { en: 'peek reads only', ru: 'peek только читает' },
  EMPTY: { en: 'empty structure', ru: 'пустая структура' },
};

interface Item {
  index: number;
  value: string;
  role: keyof typeof ROLES;
}

interface LastOperation {
  method: string;
  result: string | null;
  rule: keyof typeof RULES;
  cost: string;
}

interface StackQueueState {
  kind: 'stackQueue';
  name: string;
  structure: keyof typeof STRUCTURES;
  size: number;
  leftEnd: keyof typeof ENDS;
  rightEnd: keyof typeof ENDS;
  items: Item[];
  lastOperation: LastOperation;
}

export default function StackQueueVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as StackQueueState | undefined;

  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const nodes: LinkedNode[] = state.items.map((item) => ({
    id: String(item.index),
    title: item.value,
    subtitle: tl(ROLES[item.role] ?? ROLES.middle, lang),
    highlighted: highlight.has(`slot:${item.index}`) || highlight.has(`item:${item.value}`),
  }));

  return (
    <div style={wrapStyle}>
      <div style={titleStyle}>
        <span>{state.name}</span>
        <span style={badgeStyle}>{tl(STRUCTURES[state.structure], lang)}</span>
      </div>

      <div style={statsStyle}>
        <Stat label={tl(LABELS.size, lang)} value={String(state.size)} />
        <Stat label={tl(LABELS.operation, lang)} value={state.lastOperation.method} />
        <Stat
          label={tl(LABELS.result, lang)}
          value={state.lastOperation.result ?? tl(LABELS.none, lang)}
        />
        <Stat label={tl(LABELS.rule, lang)} value={tl(RULES[state.lastOperation.rule], lang)} highlight />
        <Stat label={tl(LABELS.cost, lang)} value={state.lastOperation.cost} />
      </div>

      <div style={lineStyle}>
        <EndBadge
          label={tl(ENDS[state.leftEnd], lang)}
          highlight={highlight.has(`end:${state.leftEnd}`)}
        />
        <div style={nodesStyle}>
          <LinkedNodes nodes={nodes} />
        </div>
        <EndBadge
          label={tl(ENDS[state.rightEnd], lang)}
          highlight={highlight.has(`end:${state.rightEnd}`)}
        />
      </div>
    </div>
  );
}

function Stat({ label, value, highlight }: { label: string; value: string; highlight?: boolean }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={{ ...statValueStyle, color: highlight ? 'var(--accent)' : 'var(--text)' }}>
        {value}
      </div>
    </div>
  );
}

function EndBadge({ label, highlight }: { label: string; highlight?: boolean }) {
  return (
    <div style={{ ...endStyle, ...(highlight ? endHighlightStyle : {}) }}>
      {label}
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const titleStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 8, fontWeight: 700 };
const badgeStyle: CSSProperties = {
  fontSize: 11,
  padding: '2px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
  fontFamily: 'monospace',
};
const statsStyle: CSSProperties = { display: 'flex', flexWrap: 'wrap', gap: 16 };
const statStyle: CSSProperties = { minWidth: 72 };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 14, fontWeight: 700, fontFamily: 'monospace' };
const lineStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 10 };
const nodesStyle: CSSProperties = { flex: 1, minWidth: 0 };
const endStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 12,
  padding: '4px 8px',
  borderRadius: 6,
  border: '1px solid var(--border)',
  background: 'var(--viz-box)',
};
const endHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
};
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
