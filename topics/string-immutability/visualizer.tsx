import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  stack: { en: 'References (stack)', ru: 'Ссылки (стек)' },
  heap: { en: 'Heap objects', ru: 'Объекты в куче' },
  pool: { en: 'pool', ru: 'пул' },
  garbage: { en: 'garbage', ru: 'мусор' },
  runHint: {
    en: 'Run the code to visualize the strings in memory.',
    ru: 'Запустите код, чтобы увидеть строки в памяти.',
  },
};

interface Variable {
  name: string;
  ref: string | null;
}
interface HeapObject {
  id: string;
  type: 'String' | 'byte[]' | 'StringBuilder';
  value: string;
  backing: string | null;
  pooled: boolean;
  referenced: boolean;
}
interface SceneState {
  variables: Variable[];
  objects: HeapObject[];
  pool?: string[];
}

export default function StringVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as SceneState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const byId = new Map(state.objects.map((o) => [o.id, o]));

  const boxes: Box[] = state.objects.map((o) => {
    const subtitleParts = [`${o.type} #${o.id}`];
    if (o.pooled) subtitleParts.push(tl(LABELS.pool, lang));
    if (!o.referenced) subtitleParts.push(tl(LABELS.garbage, lang));
    return {
      id: o.id,
      title: o.type === 'byte[]' ? charArray(o.value) : `"${o.value}"`,
      subtitle: subtitleParts.join(' · '),
      highlighted: highlight.has(`obj:${o.id}`),
      dim: !o.referenced,
    };
  });

  return (
    <div style={wrapStyle}>
      <div style={sectionLabelStyle}>{tl(LABELS.stack, lang)}</div>
      <div style={varsStyle}>
        {state.variables.length === 0 && <span style={dashStyle}>—</span>}
        {state.variables.map((v) => {
          const target = v.ref ? byId.get(v.ref) : undefined;
          return (
            <div
              key={v.name}
              style={{
                ...varBoxStyle,
                ...(highlight.has(`var:${v.name}`) ? varHighlightStyle : {}),
              }}
            >
              <span style={varNameStyle}>{v.name}</span>
              <span style={arrowStyle}>→</span>
              <span style={varTargetStyle}>
                {target ? `"${target.value}" #${target.id}` : 'null'}
              </span>
            </div>
          );
        })}
      </div>

      <div style={sectionLabelStyle}>{tl(LABELS.heap, lang)}</div>
      <BoxGroup boxes={boxes} />
    </div>
  );
}

/** Renders a byte[]'s content as spaced characters, e.g. [ h e l l o ]. */
function charArray(value: string): string {
  return value.length === 0 ? '[ ]' : `[ ${[...value].join(' ')} ]`;
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 8 };
const sectionLabelStyle: CSSProperties = {
  fontSize: 11,
  textTransform: 'uppercase',
  letterSpacing: 0.5,
  opacity: 0.55,
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
