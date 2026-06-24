import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  title: { en: 'ArrayList backing Object[]', ru: 'Внутренний Object[] ArrayList' },
  capacity: { en: 'capacity', ru: 'ёмкость' },
  size: { en: 'size', ru: 'размер' },
  free: { en: 'free slots', ru: 'свободные ячейки' },
  cost: { en: 'last op cost', ru: 'цена операции' },
  empty: { en: 'empty', ru: 'пусто' },
  logical: { en: 'logical list', ru: 'логический список' },
  reserve: { en: 'reserved capacity', ru: 'запас ёмкости' },
  runHint: {
    en: 'Run the code to visualize the backing array.',
    ru: 'Запустите код, чтобы увидеть внутренний массив.',
  },
};

const COST_KIND: Record<string, Localized> = {
  none: { en: 'O(1), no copy or shift', ru: 'O(1), без копирования и сдвига' },
  direct: { en: 'O(1), direct slot access', ru: 'O(1), прямой доступ к ячейке' },
  shifts: { en: 'element(s) shifted', ru: 'элемент(ов) сдвинуто' },
  copies: { en: 'element(s) copied', ru: 'элемент(ов) скопировано' },
};

interface Slot {
  index: number;
  value: string | null;
}

interface LastOp {
  cost: number;
  costKind: keyof typeof COST_KIND;
}

interface ArrayListState {
  kind: 'arraylist';
  name: string;
  capacity: number;
  size: number;
  slots: Slot[];
  lastOp: LastOp;
}

export default function ArrayListInternalsVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as ArrayListState | undefined;

  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const free = Math.max(0, state.capacity - state.size);
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
            {occupied ? tl(LABELS.logical, lang) : tl(LABELS.reserve, lang)}
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
        <Stat label={tl(LABELS.free, lang)} value={String(free)} />
        <Stat label={tl(LABELS.cost, lang)} value={costText(state.lastOp, lang)} highlight />
      </div>
      <ArrayGrid cells={cells} />
    </div>
  );
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
const statsStyle: CSSProperties = { display: 'flex', gap: 16, alignItems: 'flex-start', flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 16, fontWeight: 700, fontFamily: 'monospace' };
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
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
