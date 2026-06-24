import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  order: { en: 'order', ru: 'порядок' },
  size: { en: 'size', ru: 'размер' },
  lastOp: { en: 'last operation', ru: 'последняя операция' },
  result: { en: 'result', ru: 'результат' },
  range: { en: 'range', ru: 'диапазон' },
  empty: { en: 'empty', ru: 'пусто' },
  runHint: {
    en: 'Run the code to visualize the TreeSet.',
    ru: 'Запустите код, чтобы визуализировать TreeSet.',
  },
};

const OP_LABELS: Record<string, Localized> = {
  created: { en: 'created', ru: 'создан' },
  add: { en: 'add', ru: 'add' },
  duplicate: { en: 'duplicate ignored', ru: 'дубликат проигнорирован' },
  contains: { en: 'contains', ru: 'contains' },
  remove: { en: 'remove', ru: 'remove' },
  range: { en: 'range query', ru: 'запрос диапазона' },
};

interface ValueItem {
  index: number;
  value: string;
}

interface LastOp {
  kind: string;
  probe?: string | null;
  result?: string | null;
}

interface RangeState {
  from: string;
  fromInclusive: boolean;
  to: string;
  toInclusive: boolean;
  values: string[];
}

interface TreeSetState {
  name: string;
  order: string;
  orderEn?: string;
  orderRu?: string;
  size: number;
  values: ValueItem[];
  lastOp: LastOp;
  range?: RangeState;
}

export default function TreeSetVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as TreeSetState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const cells: ArrayCell[] =
    state.values.length === 0
      ? [
          {
            key: 'empty',
            label: '[]',
            content: <span style={emptyStyle}>{tl(LABELS.empty, lang)}</span>,
          },
        ]
      : state.values.map((item) => ({
          key: item.index,
          label: `[${item.index}]`,
          highlighted:
            highlight.has(`value:${item.value}`) ||
            highlight.has(`range:${item.value}`) ||
            highlight.has(`probe:${item.value}`),
          content: <span style={valueStyle}>{item.value}</span>,
        }));

  return (
    <div style={wrapStyle}>
      <div style={statsStyle}>
        <Stat label={tl(LABELS.order, lang)} value={lang === 'ru' ? state.orderRu ?? state.order : state.orderEn ?? state.order} />
        <Stat label={tl(LABELS.size, lang)} value={String(state.size)} />
        <Stat label={tl(LABELS.lastOp, lang)} value={operationLabel(state.lastOp.kind, lang)} highlight />
        {state.lastOp.result && <Stat label={tl(LABELS.result, lang)} value={state.lastOp.result} />}
      </div>

      {state.range && (
        <div style={rangeStyle}>
          <span>{tl(LABELS.range, lang)} </span>
          <code>{formatRange(state.range)}</code>
        </div>
      )}

      <ArrayGrid cells={cells} />
    </div>
  );
}

function operationLabel(kind: string, lang: 'en' | 'ru'): string {
  if (kind.startsWith('navigate:')) {
    return kind.slice('navigate:'.length);
  }
  return tl(OP_LABELS[kind] ?? { en: kind, ru: kind }, lang);
}

function formatRange(range: RangeState): string {
  const left = range.fromInclusive ? '[' : '(';
  const right = range.toInclusive ? ']' : ')';
  return `${left}${range.from}, ${range.to}${right} -> ${range.values.join(', ')}`;
}

function Stat({ label, value, highlight }: { label: string; value: string; highlight?: boolean }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={{ ...statValueStyle, color: highlight ? 'var(--accent)' : 'var(--text)' }}>{value}</div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const statsStyle: CSSProperties = { display: 'flex', gap: 16, alignItems: 'flex-start', flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 15, fontWeight: 700, fontFamily: 'monospace' };
const valueStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700, fontSize: 14 };
const emptyStyle: CSSProperties = { opacity: 0.4, fontStyle: 'italic' };
const rangeStyle: CSSProperties = { fontSize: 13, opacity: 0.8 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
