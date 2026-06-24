import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  stack: { en: 'Stack (call frames)', ru: 'Стек (кадры вызовов)' },
  heap: { en: 'Heap objects', ru: 'Объекты в куче' },
  garbage: { en: 'garbage', ru: 'мусор' },
  noVars: { en: 'no locals', ru: 'нет локальных' },
  runHint: {
    en: 'Run the code to see what each variable stores and where the objects live.',
    ru: 'Запустите код, чтобы увидеть, что хранит каждая переменная и где живут объекты.',
  },
};

interface Var {
  name: string;
  kind: 'primitive' | 'reference';
  type: string;
  value: string | null;
  ref: string | null;
}
interface Frame {
  name: string;
  vars: Var[];
}
interface HeapObject {
  id: string;
  type: string;
  fields: { name: string; value: string }[];
  referenced: boolean;
}
interface SceneState {
  frames: Frame[];
  heap: HeapObject[];
}

export default function MemoryVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as SceneState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const byId = new Map(state.heap.map((o) => [o.id, o]));

  const boxes: Box[] = state.heap.map((o) => {
    const parts = o.fields.map((f) => `${f.name}=${f.value}`);
    if (!o.referenced) parts.push(tl(LABELS.garbage, lang));
    return {
      id: o.id,
      title: `${o.type} #${o.id}`,
      subtitle: parts.join(' · '),
      highlighted: highlight.has(`obj:${o.id}`),
      dim: !o.referenced,
    };
  });

  // Render the most recent frame first so the active method is on top.
  const frames = [...state.frames].reverse();

  return (
    <div style={wrapStyle}>
      <div style={sectionLabelStyle}>{tl(LABELS.stack, lang)}</div>
      <div style={framesStyle}>
        {frames.map((frame, i) => (
          <div key={`${frame.name}-${i}`} style={frameStyle}>
            <div style={frameNameStyle}>{frame.name}()</div>
            {frame.vars.length === 0 && <span style={dashStyle}>{tl(LABELS.noVars, lang)}</span>}
            {frame.vars.map((v) => {
              const isRef = v.kind === 'reference';
              const target = isRef && v.ref ? byId.get(v.ref) : undefined;
              const detail = isRef
                ? v.ref
                  ? `→ ${target ? target.type : '?'} #${v.ref}`
                  : '→ null'
                : `= ${v.value}`;
              return (
                <div
                  key={v.name}
                  style={{
                    ...varBoxStyle,
                    ...(highlight.has(`var:${v.name}`) ? varHighlightStyle : {}),
                  }}
                >
                  <span style={varNameStyle}>{v.name}</span>
                  <span style={varTypeStyle}>{v.type}</span>
                  <span style={{ ...varDetailStyle, ...(isRef ? refDetailStyle : {}) }}>{detail}</span>
                </div>
              );
            })}
          </div>
        ))}
      </div>

      <div style={sectionLabelStyle}>{tl(LABELS.heap, lang)}</div>
      <BoxGroup boxes={boxes} />
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
const framesStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const frameStyle: CSSProperties = {
  border: '1px dashed var(--border)',
  borderRadius: 6,
  padding: '6px 8px',
  display: 'flex',
  flexWrap: 'wrap',
  alignItems: 'center',
  gap: 6,
};
const frameNameStyle: CSSProperties = {
  fontSize: 11,
  fontFamily: 'monospace',
  opacity: 0.6,
  marginRight: 4,
};
const dashStyle: CSSProperties = { opacity: 0.4, fontSize: 12 };
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
const varNameStyle: CSSProperties = { fontWeight: 700, fontFamily: 'monospace' };
const varTypeStyle: CSSProperties = { fontSize: 11, opacity: 0.5, fontFamily: 'monospace' };
const varDetailStyle: CSSProperties = { fontSize: 12, fontFamily: 'monospace', opacity: 0.85 };
const refDetailStyle: CSSProperties = { color: 'var(--accent)' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
