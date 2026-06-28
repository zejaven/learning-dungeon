import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  title: { en: 'Naive grow-by-one Object[]', ru: 'Наивный Object[] с ростом на одну ячейку' },
  size: { en: 'size', ru: 'размер' },
  capacity: { en: 'capacity', ru: 'ёмкость' },
  copiedNow: { en: 'copied now', ru: 'скопировано сейчас' },
  totalCopies: { en: 'total copies', ru: 'всего копирований' },
  totalTouches: { en: 'copies + writes', ru: 'копии + записи' },
  complexity: { en: 'batch cost', ru: 'цена пачки' },
  formula: { en: 'copy sum', ru: 'сумма копирований' },
  emptyArray: { en: 'empty backing array', ru: 'пустой внутренний массив' },
  empty: { en: 'empty', ru: 'пусто' },
  logical: { en: 'logical element', ru: 'логический элемент' },
  next: { en: 'new slot', ru: 'новая ячейка' },
  phase: { en: 'phase', ru: 'фаза' },
  runHint: {
    en: 'Run the code to visualize naive ArrayList growth.',
    ru: 'Запустите код, чтобы увидеть наивный рост ArrayList.',
  },
};

const PHASE: Record<string, Localized> = {
  created: { en: 'created', ru: 'создан' },
  grow: { en: 'allocate and copy', ru: 'выделить и копировать' },
  write: { en: 'write new value', ru: 'записать новое значение' },
  total: { en: 'total work', ru: 'общая работа' },
};

interface Slot {
  index: number;
  value: string | null;
}

interface LastOp {
  phase: string;
  copied: number;
  write: number;
  totalCopies: number;
  totalWrites: number;
  totalTouches: number;
  formula: string;
  complexity: string;
}

interface NaiveArrayListState {
  kind: 'naive-arraylist';
  name: string;
  capacity: number;
  size: number;
  slots: Slot[];
  lastOp: LastOp;
}

export default function NaiveArrayListGrowthVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as NaiveArrayListState | undefined;

  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const cells: ArrayCell[] = state.slots.map((slot) => {
    const occupied = slot.index < state.size;
    return {
      key: slot.index,
      label: `[${slot.index}]`,
      highlighted: highlight.has(`slot:${slot.index}`),
      content: (
        <div style={slotContentStyle}>
          <span style={slot.value === null ? emptyStyle : valueStyle}>
            {slot.value ?? tl(LABELS.empty, lang)}
          </span>
          <span style={tagStyle}>
            {occupied ? tl(LABELS.logical, lang) : tl(LABELS.next, lang)}
          </span>
        </div>
      ),
    };
  });

  return (
    <div style={wrapStyle}>
      <div style={titleStyle}>{tl(LABELS.title, lang)}</div>
      <div style={statsStyle}>
        <Stat label={tl(LABELS.size, lang)} value={String(state.size)} />
        <Stat label={tl(LABELS.capacity, lang)} value={String(state.capacity)} />
        <Stat label={tl(LABELS.phase, lang)} value={tl(PHASE[state.lastOp.phase] ?? PHASE.created, lang)} />
        <Stat label={tl(LABELS.copiedNow, lang)} value={String(state.lastOp.copied)} highlight />
        <Stat label={tl(LABELS.totalCopies, lang)} value={String(state.lastOp.totalCopies)} highlight />
        <Stat label={tl(LABELS.totalTouches, lang)} value={String(state.lastOp.totalTouches)} />
        <Stat label={tl(LABELS.complexity, lang)} value={state.lastOp.complexity} highlight />
      </div>
      <div style={formulaStyle}>
        <span style={formulaLabelStyle}>{tl(LABELS.formula, lang)}</span>
        <code style={formulaCodeStyle}>{state.lastOp.formula}</code>
      </div>
      {cells.length > 0 ? <ArrayGrid cells={cells} /> : <div style={emptyArrayStyle}>{tl(LABELS.emptyArray, lang)}</div>}
    </div>
  );
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
const statsStyle: CSSProperties = { display: 'flex', gap: 16, alignItems: 'flex-start', flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 15, fontWeight: 700, fontFamily: 'monospace' };
const formulaStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 10,
  padding: '6px 10px',
  borderRadius: 6,
  background: 'var(--viz-active)',
  borderLeft: '3px solid var(--accent)',
  flexWrap: 'wrap',
};
const formulaLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, flexShrink: 0 };
const formulaCodeStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13, fontWeight: 700 };
const slotContentStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  gap: 10,
  width: '100%',
};
const valueStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700, fontSize: 14 };
const emptyStyle: CSSProperties = { opacity: 0.35, fontSize: 13 };
const tagStyle: CSSProperties = { opacity: 0.55, fontSize: 11 };
const emptyArrayStyle: CSSProperties = {
  padding: '10px 12px',
  borderRadius: 6,
  background: 'var(--viz-box)',
  opacity: 0.7,
  fontSize: 13,
};
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
