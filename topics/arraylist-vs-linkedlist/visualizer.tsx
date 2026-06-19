import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  capacity: { en: 'capacity', ru: 'ёмкость' },
  size: { en: 'size', ru: 'размер' },
  cost: { en: 'last op cost', ru: 'цена операции' },
  arraylist: { en: 'ArrayList — backing array', ru: 'ArrayList — массив' },
  linkedlist: { en: 'LinkedList — node chain', ru: 'LinkedList — цепочка узлов' },
  head: { en: 'head', ru: 'head' },
  tail: { en: 'tail', ru: 'tail' },
  empty: { en: '∅', ru: '∅' },
  runHint: {
    en: 'Run the code to visualize the list.',
    ru: 'Запустите код, чтобы визуализировать список.',
  },
};

const COST_KIND: Record<string, Localized> = {
  none: { en: 'O(1) — nothing touched', ru: 'O(1) — ничего не задето' },
  direct: { en: 'O(1) — direct index', ru: 'O(1) — прямой доступ' },
  shifts: { en: 'elements shifted', ru: 'элементов сдвинуто' },
  copies: { en: 'elements copied', ru: 'элементов скопировано' },
  hops: { en: 'hops walked', ru: 'шагов пройдено' },
};

interface LastOp {
  cost: number;
  costKind: keyof typeof COST_KIND;
}
interface Slot {
  index: number;
  value: string | null;
}
interface NodeItem {
  index: number;
  value: string;
}
interface ListState {
  kind: 'arraylist' | 'linkedlist';
  name: string;
  size: number;
  capacity?: number;
  slots?: Slot[];
  nodes?: NodeItem[];
  lastOp: LastOp;
}

export default function ListVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as ListState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const isArray = state.kind === 'arraylist';

  return (
    <div style={wrapStyle}>
      <div style={titleStyle}>
        {isArray ? tl(LABELS.arraylist, lang) : tl(LABELS.linkedlist, lang)}
      </div>

      <div style={statsStyle}>
        {isArray && <Stat label={tl(LABELS.capacity, lang)} value={String(state.capacity)} />}
        <Stat label={tl(LABELS.size, lang)} value={String(state.size)} />
        <Stat label={tl(LABELS.cost, lang)} value={costText(state.lastOp, lang)} highlight />
      </div>

      {isArray ? (
        <ArrayView slots={state.slots ?? []} highlight={highlight} lang={lang} />
      ) : (
        <ChainView nodes={state.nodes ?? []} highlight={highlight} lang={lang} />
      )}
    </div>
  );
}

function ArrayView({
  slots,
  highlight,
  lang,
}: {
  slots: Slot[];
  highlight: Set<string>;
  lang: 'en' | 'ru';
}) {
  const cells: ArrayCell[] = slots.map((slot) => ({
    key: slot.index,
    label: `[${slot.index}]`,
    highlighted: highlight.has(`slot:${slot.index}`),
    content:
      slot.value === null ? (
        <span style={emptySlotStyle}>{tl(LABELS.empty, lang)}</span>
      ) : (
        <span style={valueStyle}>{slot.value}</span>
      ),
  }));
  return <ArrayGrid cells={cells} />;
}

function ChainView({
  nodes,
  highlight,
  lang,
}: {
  nodes: NodeItem[];
  highlight: Set<string>;
  lang: 'en' | 'ru';
}) {
  const linked: LinkedNode[] = nodes.map((n) => ({
    id: String(n.index),
    title: n.value,
    subtitle:
      n.index === 0
        ? tl(LABELS.head, lang)
        : n.index === nodes.length - 1
          ? tl(LABELS.tail, lang)
          : `[${n.index}]`,
    highlighted: highlight.has(`node:${n.index}`),
  }));
  return <LinkedNodes nodes={linked} />;
}

function costText(op: LastOp, lang: 'en' | 'ru'): string {
  if (op.costKind === 'none' || op.costKind === 'direct') {
    return tl(COST_KIND[op.costKind], lang);
  }
  return `${op.cost} ${tl(COST_KIND[op.costKind], lang)}`;
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
const titleStyle: CSSProperties = { fontSize: 13, fontWeight: 700, opacity: 0.8 };
const statsStyle: CSSProperties = { display: 'flex', gap: 16, alignItems: 'flex-start' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 16, fontWeight: 700, fontFamily: 'monospace' };
const valueStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 600, fontSize: 14 };
const emptySlotStyle: CSSProperties = { opacity: 0.3, fontSize: 14 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
