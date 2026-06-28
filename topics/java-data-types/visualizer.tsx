import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  variables: { en: 'Variables', ru: 'Переменные' },
  primitive: { en: 'primitive', ru: 'примитив' },
  wrapper: { en: 'wrapper (object)', ru: 'обёртка (объект)' },
  reference: { en: 'reference', ru: 'ссылка' },
  bits: { en: 'bits', ru: 'бит' },
  range: { en: 'range', ru: 'диапазон' },
  heap: { en: 'on heap', ru: 'в куче' },
  result: { en: 'result', ru: 'результат' },
  runHint: {
    en: 'Run the code to see each type, its size, and its value.',
    ru: 'Запустите код, чтобы увидеть каждый тип, его размер и значение.',
  },
};

type Category = 'primitive' | 'wrapper' | 'reference';

interface Slot {
  name: string;
  category: Category;
  type: string;
  bits: number | null;
  value: string;
  range: string | null;
}
interface Note {
  expr: string;
  result: string;
}
interface SceneState {
  slots: Slot[];
  note: Note | null;
}

const CATEGORY_LABEL: Record<Category, keyof typeof LABELS> = {
  primitive: 'primitive',
  wrapper: 'wrapper',
  reference: 'reference',
};

export default function DataTypesVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as SceneState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  return (
    <div style={wrapStyle}>
      <div style={sectionLabelStyle}>{tl(LABELS.variables, lang)}</div>
      <div style={slotsStyle}>
        {state.slots.length === 0 && <span style={dashStyle}>—</span>}
        {state.slots.map((slot) => {
          const isHi = highlight.has(`slot:${slot.name}`);
          const onHeap = slot.category !== 'primitive';
          return (
            <div
              key={slot.name}
              style={{
                ...slotStyle,
                ...(onHeap ? slotRefStyle : {}),
                ...(isHi ? slotHighlightStyle : {}),
              }}
            >
              <div style={slotHeadStyle}>
                <span style={typeStyle}>{slot.type}</span>
                <span style={nameStyle}>{slot.name}</span>
              </div>
              <div style={valueStyle}>{slot.value}</div>
              <div style={metaRowStyle}>
                <span style={badgeStyle}>{tl(LABELS[CATEGORY_LABEL[slot.category]], lang)}</span>
                {slot.bits != null && (
                  <span style={badgeStyle}>
                    {slot.bits} {tl(LABELS.bits, lang)}
                  </span>
                )}
                {onHeap && <span style={badgeStyle}>{tl(LABELS.heap, lang)}</span>}
              </div>
              {slot.range && (
                <div style={rangeStyle}>
                  {tl(LABELS.range, lang)}: {slot.range}
                </div>
              )}
            </div>
          );
        })}
      </div>

      {state.note && (
        <div style={noteStyle}>
          <code style={noteExprStyle}>{state.note.expr}</code>
          <span style={noteArrowStyle}>→</span>
          <code style={noteResultStyle}>{state.note.result}</code>
        </div>
      )}
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 10 };
const sectionLabelStyle: CSSProperties = {
  fontSize: 11,
  textTransform: 'uppercase',
  letterSpacing: 0.5,
  opacity: 0.55,
};
const slotsStyle: CSSProperties = { display: 'flex', flexWrap: 'wrap', gap: 8 };
const dashStyle: CSSProperties = { opacity: 0.4 };
const slotStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '6px 10px',
  background: 'var(--viz-box)',
  minWidth: 96,
  display: 'flex',
  flexDirection: 'column',
  gap: 4,
};
const slotRefStyle: CSSProperties = { borderStyle: 'dashed' };
const slotHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.35)',
};
const slotHeadStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 6,
  fontFamily: 'monospace',
};
const typeStyle: CSSProperties = { fontSize: 12, opacity: 0.7 };
const nameStyle: CSSProperties = { fontWeight: 700, fontSize: 14 };
const valueStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 15,
  fontWeight: 600,
  wordBreak: 'break-all',
};
const metaRowStyle: CSSProperties = { display: 'flex', flexWrap: 'wrap', gap: 4 };
const badgeStyle: CSSProperties = {
  fontSize: 10,
  padding: '1px 6px',
  borderRadius: 10,
  background: 'var(--viz-badge, rgba(127,127,127,0.18))',
  opacity: 0.85,
};
const rangeStyle: CSSProperties = { fontSize: 10, opacity: 0.6, fontFamily: 'monospace' };
const noteStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  flexWrap: 'wrap',
  padding: '6px 10px',
  border: '1px dashed var(--border)',
  borderRadius: 8,
};
const noteExprStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13 };
const noteArrowStyle: CSSProperties = { opacity: 0.6 };
const noteResultStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13, fontWeight: 700 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
