import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  sharedSection: {
    en: 'Shared — one of each per JVM instance',
    ru: 'Общие — по одной на инстанс JVM',
  },
  perThreadSection: {
    en: 'Per thread — one of each per live thread',
    ru: 'На поток — по одной у каждого живого потока',
  },
  areasCount: { en: 'areas × 1', ru: 'области × 1' },
  threadsCount: { en: 'threads', ru: 'потоков' },
  areasEach: { en: 'areas each', ru: 'области у каждого' },
  insideHeap: { en: 'inside the heap', ru: 'внутри кучи' },
  by: { en: 'by', ru: 'от' },
  jvmStack: { en: 'JVM stack', ru: 'JVM-стек' },
  nativeStack: { en: 'native method stack', ru: 'стек нативных методов' },
  runHint: {
    en: 'Run the code to see every JVM memory area and how many of each there are.',
    ru: 'Запустите код, чтобы увидеть все области памяти JVM и сколько их каждого вида.',
  },
};

const AREA_TITLES: Record<string, Localized> = {
  heap: { en: 'Heap', ru: 'Куча' },
  'string-pool': { en: 'String pool', ru: 'Пул строк' },
  metaspace: { en: 'Metaspace', ru: 'Metaspace' },
  'code-cache': { en: 'Code cache', ru: 'Кэш кода' },
};

const AREA_NOTES: Record<string, Localized> = {
  heap: { en: 'objects and arrays, managed by the GC', ru: 'объекты и массивы, управляется GC' },
  'string-pool': { en: 'interned literals, since Java 7', ru: 'интернированные литералы, с Java 7' },
  metaspace: { en: 'class metadata, native memory', ru: 'метаданные классов, нативная память' },
  'code-cache': { en: 'native code produced by the JIT', ru: 'нативный код от JIT' },
};

interface AreaItem {
  id: string;
  label: string;
  owner?: string | null;
}
interface SharedArea {
  id: string;
  insideHeap: boolean;
  items: AreaItem[];
}
interface Frame {
  depth: number;
  name: string;
}
interface ThreadAreas {
  name: string;
  pc: string;
  frames: Frame[];
  nativeFrames: Frame[];
}
interface AreasState {
  threadCount: number;
  sharedAreaCount: number;
  perThreadAreaCount: number;
  shared: SharedArea[];
  threads: ThreadAreas[];
}

export default function JvmMemoryStructuresVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as AreasState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  return (
    <div style={wrapStyle}>
      <div style={sectionLabelStyle}>
        <span>{tl(LABELS.sharedSection, lang)}</span>
        <span style={countStyle}>
          {state.sharedAreaCount} {tl(LABELS.areasCount, lang)}
        </span>
      </div>

      <div style={areasColStyle}>
        {state.shared.map((area) => {
          const hot = highlight.has(`area:${area.id}`);
          const boxes: Box[] = area.items.map((item) => ({
            id: item.id,
            title: item.label,
            subtitle: item.owner ? `${tl(LABELS.by, lang)} ${item.owner}` : undefined,
            highlighted: highlight.has(`item:${item.id}`),
          }));
          return (
            <div
              key={area.id}
              style={{
                ...areaStyle,
                ...(area.insideHeap ? nestedAreaStyle : {}),
                ...(hot ? hotBorderStyle : {}),
              }}
            >
              <div style={areaHeadStyle}>
                <span style={areaTitleStyle}>{tl(AREA_TITLES[area.id], lang)}</span>
                <span style={areaNoteStyle}>
                  {area.insideHeap ? `${tl(LABELS.insideHeap, lang)} · ` : ''}
                  {tl(AREA_NOTES[area.id], lang)}
                </span>
              </div>
              <BoxGroup boxes={boxes} />
            </div>
          );
        })}
      </div>

      <div style={sectionLabelStyle}>
        <span>{tl(LABELS.perThreadSection, lang)}</span>
        <span style={countStyle}>
          {state.threadCount} {tl(LABELS.threadsCount, lang)} × {state.perThreadAreaCount}{' '}
          {tl(LABELS.areasEach, lang)}
        </span>
      </div>

      <div style={threadsRowStyle}>
        {state.threads.map((t) => {
          const frames = [...t.frames].reverse(); // top-of-stack first
          const hot = highlight.has(`thread:${t.name}`);
          return (
            <div key={t.name} style={{ ...threadColStyle, ...(hot ? hotBorderStyle : {}) }}>
              <div style={threadNameStyle}>{t.name}</div>

              <div
                style={{
                  ...pcStyle,
                  ...(highlight.has(`pc:${t.name}`) ? hotChipStyle : {}),
                }}
              >
                PC: {t.pc}
              </div>

              <div style={subLabelStyle}>{tl(LABELS.jvmStack, lang)}</div>
              {frames.map((f) => (
                <div
                  key={f.depth}
                  style={{
                    ...frameStyle,
                    ...(highlight.has(`frame:${t.name}:${f.depth}`) ? hotChipStyle : {}),
                  }}
                >
                  {f.name}()
                </div>
              ))}

              <div style={subLabelStyle}>{tl(LABELS.nativeStack, lang)}</div>
              {t.nativeFrames.length === 0 ? (
                <div style={emptyNativeStyle}>—</div>
              ) : (
                [...t.nativeFrames].reverse().map((f) => (
                  <div
                    key={f.depth}
                    style={{
                      ...nativeFrameStyle,
                      ...(highlight.has(`native:${t.name}:${f.depth}`) ? hotChipStyle : {}),
                    }}
                  >
                    {f.name}()
                  </div>
                ))
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 8 };
const sectionLabelStyle: CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'baseline',
  gap: 8,
  fontSize: 11,
  textTransform: 'uppercase',
  letterSpacing: 0.5,
  opacity: 0.7,
};
const countStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700, opacity: 0.8 };
const areasColStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const areaStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: 8,
  display: 'flex',
  flexDirection: 'column',
  gap: 6,
};
const nestedAreaStyle: CSSProperties = { marginLeft: 20, borderStyle: 'dashed' };
const hotBorderStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.35)',
};
const areaHeadStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  flexWrap: 'wrap',
  gap: 6,
};
const areaTitleStyle: CSSProperties = { fontSize: 12, fontWeight: 700 };
const areaNoteStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const threadsRowStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'flex-start',
  flexWrap: 'wrap',
  gap: 8,
};
const threadColStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 4,
  border: '1px dashed var(--border)',
  borderRadius: 8,
  padding: 6,
  minWidth: 132,
};
const threadNameStyle: CSSProperties = {
  fontSize: 11,
  fontFamily: 'monospace',
  fontWeight: 700,
  opacity: 0.65,
  textAlign: 'center',
};
const subLabelStyle: CSSProperties = { fontSize: 10, opacity: 0.5, textAlign: 'center' };
const pcStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '2px 6px',
  fontFamily: 'monospace',
  fontSize: 11,
  textAlign: 'center',
  opacity: 0.9,
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
const nativeFrameStyle: CSSProperties = { ...frameStyle, borderStyle: 'dashed' };
const emptyNativeStyle: CSSProperties = { fontSize: 12, opacity: 0.35, textAlign: 'center' };
const hotChipStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
};
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
