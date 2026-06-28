import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  title: { en: 'Backing Object[] — index to address', ru: 'Внутренний Object[] — индекс в адрес' },
  base: { en: 'base', ru: 'база' },
  header: { en: 'header', ru: 'заголовок' },
  scale: { en: 'ref scale', ru: 'размер ссылки' },
  size: { en: 'size', ru: 'размер' },
  capacity: { en: 'capacity', ru: 'ёмкость' },
  bytes: { en: 'B', ru: 'Б' },
  empty: { en: 'empty', ru: 'пусто' },
  heap: { en: 'heap object', ru: 'объект в куче' },
  formula: { en: 'address arithmetic', ru: 'арифметика адреса' },
  oob: {
    en: 'Out of bounds — the bounds check fails, so no address is computed.',
    ru: 'За границей — проверка границ не проходит, адрес не вычисляется.',
  },
  runHint: {
    en: 'Run the code to visualize index-to-address resolution.',
    ru: 'Запустите код, чтобы увидеть преобразование индекса в адрес.',
  },
};

interface Slot {
  index: number;
  address: string;
  ref: string | null;
  value: string | null;
}

interface LastOp {
  kind: 'store' | 'bounds' | 'address' | 'read' | 'oob';
  index: number;
  formula: string | null;
  address: string | null;
}

interface IndexingState {
  kind: 'array-indexing';
  name: string;
  base: string;
  header: number;
  scale: number;
  size: number;
  capacity: number;
  slots: Slot[];
  lastOp: LastOp | null;
}

export default function ArrayIndexAddressingVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as IndexingState | undefined;

  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const op = state.lastOp;

  const cells: ArrayCell[] = state.slots.map((slot) => ({
    key: slot.index,
    label: `[${slot.index}] ${slot.address}`,
    highlighted: highlight.has(`slot:${slot.index}`),
    content: (
      <div style={slotContentStyle}>
        {slot.value === null ? (
          <span style={emptyStyle}>{tl(LABELS.empty, lang)}</span>
        ) : (
          <>
            <span style={refStyle}>{slot.ref}</span>
            <span style={arrowStyle}>→</span>
            <span style={valueStyle}>{slot.value}</span>
            <span style={tagStyle}>{tl(LABELS.heap, lang)}</span>
          </>
        )}
      </div>
    ),
  }));

  const showFormula = op && op.formula;
  const showOob = op && op.kind === 'oob';

  return (
    <div style={wrapStyle}>
      <div style={titleStyle}>{tl(LABELS.title, lang)}</div>
      <div style={statsStyle}>
        <Stat label={tl(LABELS.base, lang)} value={state.base} />
        <Stat label={tl(LABELS.header, lang)} value={`${state.header} ${tl(LABELS.bytes, lang)}`} />
        <Stat label={tl(LABELS.scale, lang)} value={`${state.scale} ${tl(LABELS.bytes, lang)}`} />
        <Stat label={tl(LABELS.size, lang)} value={String(state.size)} />
        <Stat label={tl(LABELS.capacity, lang)} value={String(state.capacity)} />
      </div>
      {showFormula && (
        <div style={formulaStyle}>
          <span style={formulaLabelStyle}>{tl(LABELS.formula, lang)}</span>
          <code style={formulaCodeStyle}>{op!.formula}</code>
        </div>
      )}
      {showOob && <div style={oobStyle}>{tl(LABELS.oob, lang)}</div>}
      <ArrayGrid cells={cells} />
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={statValueStyle}>{value}</div>
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
};
const formulaLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, flexShrink: 0 };
const formulaCodeStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 14, fontWeight: 700 };
const oobStyle: CSSProperties = {
  fontSize: 13,
  padding: '6px 10px',
  borderRadius: 6,
  background: 'rgba(229,115,115,0.12)',
  borderLeft: '3px solid #e57373',
};
const slotContentStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 8, width: '100%' };
const refStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13, opacity: 0.75 };
const arrowStyle: CSSProperties = { opacity: 0.5 };
const valueStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700, fontSize: 14 };
const tagStyle: CSSProperties = { opacity: 0.5, fontSize: 11, marginLeft: 'auto' };
const emptyStyle: CSSProperties = { opacity: 0.35, fontSize: 13 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
