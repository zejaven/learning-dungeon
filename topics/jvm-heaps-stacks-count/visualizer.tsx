import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  stacks: { en: 'Stacks — one per live thread', ru: 'Стеки — по одному на живой поток' },
  heap: { en: 'Heap — exactly one, shared by all threads', ru: 'Куча — ровно одна, общая для всех потоков' },
  heapCount: { en: 'heaps', ru: 'куч' },
  stackCount: { en: 'stacks', ru: 'стеков' },
  by: { en: 'by', ru: 'от' },
  runHint: {
    en: 'Run the code to count the heaps and stacks in one JVM instance.',
    ru: 'Запустите код, чтобы посчитать кучи и стеки в одном инстансе JVM.',
  },
};

interface Frame {
  depth: number;
  name: string;
}
interface ThreadStack {
  thread: string;
  frames: Frame[];
}
interface HeapObject {
  id: string;
  type: string;
  owner: string;
}
interface SceneState {
  heapCount: number;
  stackCount: number;
  stacks: ThreadStack[];
  heap: HeapObject[];
}

export default function JvmHeapsStacksVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as SceneState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  const heapBoxes: Box[] = state.heap.map((o) => ({
    id: o.id,
    title: `${o.type} #${o.id}`,
    subtitle: `${tl(LABELS.by, lang)} ${o.owner}`,
    highlighted: highlight.has(`obj:${o.id}`),
  }));

  return (
    <div style={wrapStyle}>
      <div style={sectionLabelStyle}>
        <span>{tl(LABELS.stacks, lang)}</span>
        <span style={countStyle}>
          {state.stackCount} {tl(LABELS.stackCount, lang)}
        </span>
      </div>
      <div style={stacksRowStyle}>
        {state.stacks.map((st) => {
          const frames = [...st.frames].reverse(); // top-of-stack first
          const stackHot = highlight.has(`stack:${st.thread}`);
          return (
            <div
              key={st.thread}
              style={{ ...stackColStyle, ...(stackHot ? stackHotStyle : {}) }}
            >
              <div style={threadNameStyle}>{st.thread}</div>
              {frames.map((f) => (
                <div
                  key={f.depth}
                  style={{
                    ...frameStyle,
                    ...(highlight.has(`frame:${st.thread}:${f.depth}`) ? frameHotStyle : {}),
                  }}
                >
                  {f.name}()
                </div>
              ))}
            </div>
          );
        })}
      </div>

      <div style={sectionLabelStyle}>
        <span>{tl(LABELS.heap, lang)}</span>
        <span style={{ ...countStyle, ...(highlight.has('heap') ? countHotStyle : {}) }}>
          {state.heapCount} {tl(LABELS.heapCount, lang)}
        </span>
      </div>
      <div style={{ ...heapBoxStyle, ...(highlight.has('heap') ? heapHotStyle : {}) }}>
        <BoxGroup boxes={heapBoxes} />
      </div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 8 };
const sectionLabelStyle: CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'baseline',
  fontSize: 11,
  textTransform: 'uppercase',
  letterSpacing: 0.5,
  opacity: 0.7,
};
const countStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontWeight: 700,
  opacity: 0.8,
};
const countHotStyle: CSSProperties = { color: 'var(--accent)', opacity: 1 };
const stacksRowStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'flex-start',
  flexWrap: 'wrap',
  gap: 8,
};
const stackColStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 4,
  border: '1px dashed var(--border)',
  borderRadius: 8,
  padding: 6,
  minWidth: 96,
};
const stackHotStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.35)',
};
const threadNameStyle: CSSProperties = {
  fontSize: 11,
  fontFamily: 'monospace',
  fontWeight: 700,
  opacity: 0.65,
  textAlign: 'center',
};
const frameStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '3px 8px',
  background: 'var(--viz-box)',
  fontFamily: 'monospace',
  fontSize: 12,
  textAlign: 'center',
};
const frameHotStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
};
const heapBoxStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: 8,
};
const heapHotStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.35)',
};
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
