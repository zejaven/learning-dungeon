import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  stack: { en: 'Stack (frames)', ru: 'Стек (кадры)' },
  heap: { en: 'Heap (objects)', ru: 'Куча (объекты)' },
  garbage: { en: 'garbage', ru: 'мусор' },
  runHint: {
    en: 'Run the code to see where each variable lives.',
    ru: 'Запустите код, чтобы увидеть, где живёт каждая переменная.',
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
interface SceneState {
  frames: Frame[];
  heap: HeapObject[];
}

export default function VariableStorageVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as SceneState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const byId = new Map(state.heap.map((o) => [o.id, o]));

  // Render frames top-of-stack first, so the most recent call sits on top.
  const frames = [...state.frames].reverse();

  const boxes: Box[] = state.heap.map((o) => ({
    id: o.id,
    title: `${o.type} { ${o.fields.map((f) => `${f.name}=${f.value}`).join(', ')} }`,
    subtitle: o.referenced ? `#${o.id}` : `#${o.id} · ${tl(LABELS.garbage, lang)}`,
    highlighted: highlight.has(`obj:${o.id}`),
    dim: !o.referenced,
  }));

  return (
    <div style={wrapStyle}>
      <div style={sectionLabelStyle}>{tl(LABELS.stack, lang)}</div>
      <div style={framesStyle}>
        {frames.map((frame) => (
          <div key={frame.name} style={frameStyle}>
            <div style={frameNameStyle}>{frame.name}()</div>
            <div style={varsStyle}>
              {frame.vars.length === 0 && <span style={dashStyle}>—</span>}
              {frame.vars.map((v) => (
                <div
                  key={v.name}
                  style={{
                    ...varBoxStyle,
                    ...(highlight.has(`var:${v.name}`) ? varHighlightStyle : {}),
                  }}
                >
                  <span style={varNameStyle}>{`${v.type} ${v.name}`}</span>
                  <span style={arrowStyle}>{v.kind === 'primitive' ? '=' : '→'}</span>
                  <span style={varTargetStyle}>{slotText(v, byId)}</span>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>

      <div style={sectionLabelStyle}>{tl(LABELS.heap, lang)}</div>
      <BoxGroup boxes={boxes} />
    </div>
  );
}

/** What a slot displays: the literal value, an object handle, or null. */
function slotText(v: Variable, byId: Map<string, HeapObject>): string {
  if (v.kind === 'primitive') return v.value ?? '';
  if (v.ref == null) return 'null';
  const target = byId.get(v.ref);
  return target ? `${target.type} #${target.id}` : `#${v.ref}`;
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
  borderRadius: 8,
  padding: '6px 8px',
  display: 'flex',
  flexDirection: 'column',
  gap: 4,
};
const frameNameStyle: CSSProperties = {
  fontSize: 11,
  fontFamily: 'monospace',
  opacity: 0.6,
};
const varsStyle: CSSProperties = { display: 'flex', flexWrap: 'wrap', gap: 6 };
const dashStyle: CSSProperties = { opacity: 0.4 };
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
const arrowStyle: CSSProperties = { opacity: 0.6 };
const varTargetStyle: CSSProperties = { fontSize: 12, fontFamily: 'monospace', opacity: 0.85 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
