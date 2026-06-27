import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  callStack: { en: 'Call stack for one thread', ru: 'Стек вызовов одного потока' },
  heap: { en: 'Heap objects', ru: 'Объекты в heap' },
  topFrame: { en: 'top frame', ru: 'верхний кадр' },
  bottomFrame: { en: 'bottom frame', ru: 'нижний кадр' },
  frameCount: { en: 'frames', ru: 'кадры' },
  heapCount: { en: 'heap objects', ru: 'объекты heap' },
  noVariables: { en: 'no locals yet', ru: 'локальных переменных пока нет' },
  noObjects: { en: 'no heap objects yet', ru: 'объектов в heap пока нет' },
  garbage: { en: 'garbage', ru: 'мусор' },
  reachable: { en: 'reachable', ru: 'достижим' },
  runHint: {
    en: 'Run the code to see method calls push frames onto the current thread stack.',
    ru: 'Запустите код, чтобы увидеть, как вызовы методов добавляют кадры в стек текущего потока.',
  },
};

interface Variable {
  name: string;
  kind: 'primitive' | 'reference';
  type: string;
  value: string | null;
  ref: string | null;
}

interface Frame {
  name: string;
  vars: Variable[];
}

interface HeapField {
  name: string;
  value: string;
}

interface HeapObject {
  id: string;
  type: string;
  fields: HeapField[];
  referenced: boolean;
}

interface StackState {
  frames: Frame[];
  heap: HeapObject[];
}

export default function MethodCallStackFramesVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as StackState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const framesTopFirst = state.frames
    .map((frame, depth) => ({ frame, depth }))
    .reverse();

  const cells: ArrayCell[] = framesTopFirst.map(({ frame, depth }, index) => {
    const isTop = index === 0;
    const isBottom = depth === 0;
    const highlighted = frame.vars.some((v) => highlight.has(`var:${v.name}`));

    return {
      key: `${depth}-${frame.name}`,
      label: isTop ? tl(LABELS.topFrame, lang) : isBottom ? tl(LABELS.bottomFrame, lang) : `#${depth}`,
      highlighted,
      content: (
        <div style={frameContentStyle}>
          <div style={frameTitleStyle}>{frame.name}()</div>
          <div style={varsStyle}>
            {frame.vars.length === 0 && <span style={emptyStyle}>{tl(LABELS.noVariables, lang)}</span>}
            {frame.vars.map((v) => (
              <div
                key={`${frame.name}-${v.name}`}
                style={{
                  ...varBoxStyle,
                  ...(highlight.has(`var:${v.name}`) ? varHighlightStyle : {}),
                }}
              >
                <span style={varNameStyle}>{`${v.type} ${v.name}`}</span>
                <span style={arrowStyle}>{v.kind === 'primitive' ? '=' : '->'}</span>
                <span style={varValueStyle}>{slotText(v)}</span>
              </div>
            ))}
          </div>
        </div>
      ),
    };
  });

  return (
    <div style={wrapStyle}>
      <div style={statsStyle}>
        <Stat label={tl(LABELS.frameCount, lang)} value={state.frames.length} />
        <Stat label={tl(LABELS.heapCount, lang)} value={state.heap.length} />
      </div>

      <div style={sectionLabelStyle}>{tl(LABELS.callStack, lang)}</div>
      <ArrayGrid cells={cells} />

      <div style={sectionLabelStyle}>{tl(LABELS.heap, lang)}</div>
      <div style={heapStyle}>
        {state.heap.length === 0 && <span style={emptyStyle}>{tl(LABELS.noObjects, lang)}</span>}
        {state.heap.map((obj) => (
          <div
            key={obj.id}
            style={{
              ...heapObjectStyle,
              ...(highlight.has(`obj:${obj.id}`) ? heapHighlightStyle : {}),
              ...(!obj.referenced ? heapGarbageStyle : {}),
            }}
          >
            <div style={heapTitleStyle}>{`${obj.type} #${obj.id}`}</div>
            <div style={heapFieldsStyle}>{formatFields(obj.fields)}</div>
            <div style={heapStatusStyle}>
              {obj.referenced ? tl(LABELS.reachable, lang) : tl(LABELS.garbage, lang)}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={statValueStyle}>{value}</div>
    </div>
  );
}

function slotText(v: Variable): string {
  if (v.kind === 'primitive') return v.value ?? '';
  return v.ref == null ? 'null' : `#${v.ref}`;
}

function formatFields(fields: HeapField[]): string {
  if (fields.length === 0) return '{}';
  return `{ ${fields.map((f) => `${f.name}=${f.value}`).join(', ')} }`;
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 8 };
const statsStyle: CSSProperties = { display: 'flex', gap: 12, flexWrap: 'wrap' };
const statStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '4px 8px',
  minWidth: 76,
  textAlign: 'center',
};
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = {
  fontSize: 11,
  textTransform: 'uppercase',
  letterSpacing: 0,
  opacity: 0.58,
};
const frameContentStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 5, width: '100%' };
const frameTitleStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700 };
const varsStyle: CSSProperties = { display: 'flex', flexWrap: 'wrap', gap: 6 };
const varBoxStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 6,
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '3px 8px',
  background: 'var(--viz-box)',
};
const varHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.35)',
};
const varNameStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700 };
const arrowStyle: CSSProperties = { opacity: 0.65 };
const varValueStyle: CSSProperties = { fontFamily: 'monospace', opacity: 0.9 };
const heapStyle: CSSProperties = { display: 'flex', flexWrap: 'wrap', gap: 6 };
const heapObjectStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  background: 'var(--viz-box)',
  padding: '5px 8px',
  minWidth: 120,
};
const heapHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.35)',
};
const heapGarbageStyle: CSSProperties = { opacity: 0.45, borderStyle: 'dashed' };
const heapTitleStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700 };
const heapFieldsStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 12, opacity: 0.82 };
const heapStatusStyle: CSSProperties = { fontSize: 11, opacity: 0.65 };
const emptyStyle: CSSProperties = { fontSize: 12, opacity: 0.45, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
