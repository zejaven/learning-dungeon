import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  stack: { en: 'Local variables', ru: 'Локальные переменные' },
  heap: { en: 'Heap objects', ru: 'Объекты в куче' },
  primitive: { en: 'primitive value', ru: 'примитивное значение' },
  reference: { en: 'reference', ru: 'ссылка' },
  garbage: { en: 'garbage', ru: 'мусор' },
  noObjects: { en: 'no objects', ru: 'объектов нет' },
  runHint: {
    en: 'Run the code to compare primitive slots with object references.',
    ru: 'Запустите код, чтобы сравнить слоты примитивов и объектные ссылки.',
  },
};

interface VarSlot {
  name: string;
  kind: 'primitive' | 'reference';
  type: string;
  value: string | null;
  ref: string | null;
}

interface Frame {
  name: string;
  vars: VarSlot[];
}

interface HeapObject {
  id: string;
  type: string;
  fields: { name: string; value: string }[];
  referenced: boolean;
}

interface TypeState {
  frames: Frame[];
  heap: HeapObject[];
}

export default function PrimitiveVsObjectVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as TypeState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const heapById = new Map(state.heap.map((obj) => [obj.id, obj]));
  const slots = state.frames.flatMap((frame) =>
    frame.vars.map((slot) => ({ ...slot, frame: frame.name })),
  );

  const boxes: Box[] = state.heap.map((obj) => ({
    id: obj.id,
    title: `${obj.type} #${obj.id}`,
    subtitle: [
      ...obj.fields.map((field) => `${field.name}=${field.value}`),
      obj.referenced ? '' : tl(LABELS.garbage, lang),
    ]
      .filter(Boolean)
      .join(' · '),
    highlighted: highlight.has(`obj:${obj.id}`),
    dim: !obj.referenced,
  }));

  return (
    <div style={wrapStyle}>
      <div style={sectionLabelStyle}>{tl(LABELS.stack, lang)}</div>
      <div style={slotsStyle}>
        {slots.map((slot) => {
          const target = slot.ref ? heapById.get(slot.ref) : undefined;
          const value =
            slot.kind === 'primitive'
              ? `= ${slot.value}`
              : slot.ref
                ? `-> ${target ? target.type : '?'} #${slot.ref}`
                : '-> null';
          return (
            <div
              key={`${slot.frame}-${slot.name}`}
              style={{
                ...slotStyle,
                ...(highlight.has(`var:${slot.name}`) ? slotHighlightStyle : {}),
              }}
            >
              <span style={slotTypeStyle}>{slot.type}</span>
              <span style={slotNameStyle}>{slot.name}</span>
              <span style={kindStyle}>
                {tl(slot.kind === 'primitive' ? LABELS.primitive : LABELS.reference, lang)}
              </span>
              <span style={slotValueStyle}>{value}</span>
            </div>
          );
        })}
      </div>

      <div style={sectionLabelStyle}>{tl(LABELS.heap, lang)}</div>
      {boxes.length === 0 ? (
        <span style={emptyStyle}>{tl(LABELS.noObjects, lang)}</span>
      ) : (
        <BoxGroup boxes={boxes} />
      )}
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 8 };
const sectionLabelStyle: CSSProperties = {
  fontSize: 11,
  textTransform: 'uppercase',
  letterSpacing: 0.5,
  opacity: 0.55,
};
const slotsStyle: CSSProperties = { display: 'flex', flexWrap: 'wrap', gap: 6 };
const slotStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'auto auto auto auto',
  alignItems: 'center',
  gap: 6,
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '4px 8px',
  background: 'var(--viz-box)',
};
const slotHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.35)',
};
const slotTypeStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 11, opacity: 0.55 };
const slotNameStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700 };
const kindStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const slotValueStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 12, color: 'var(--accent)' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
const emptyStyle: CSSProperties = { fontSize: 12, opacity: 0.4, fontStyle: 'italic' };
