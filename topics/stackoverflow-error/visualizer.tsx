import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  callStack: { en: 'Thread call stack', ru: 'Стек вызовов потока' },
  topFrame: { en: 'top frame', ru: 'верхний кадр' },
  bottomFrame: { en: 'bottom frame', ru: 'нижний кадр' },
  used: { en: 'frames used', ru: 'кадров занято' },
  limit: { en: 'stack limit', ru: 'предел стека' },
  free: { en: 'free slot', ru: 'свободный слот' },
  overflow: { en: 'StackOverflowError — stack full', ru: 'StackOverflowError — стек переполнен' },
  runHint: {
    en: 'Run the code to watch method calls push frames until the stack overflows.',
    ru: 'Запустите код, чтобы увидеть, как вызовы методов добавляют кадры до переполнения стека.',
  },
};

interface Frame {
  depth: number;
  name: string;
}

interface StackState {
  limit: number;
  overflowed: boolean;
  frames: Frame[];
}

export default function StackOverflowVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as StackState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const used = state.frames.length;
  // Render the stack top-down: top frame first, then free slots below the count.
  const framesTopFirst = [...state.frames].reverse();
  const freeSlots = Math.max(0, state.limit - used);

  return (
    <div style={wrapStyle}>
      <div style={statsStyle}>
        <Stat label={tl(LABELS.used, lang)} value={`${used} / ${state.limit}`} />
        <Stat label={tl(LABELS.limit, lang)} value={state.limit} />
      </div>

      {state.overflowed && (
        <div style={overflowBannerStyle}>{tl(LABELS.overflow, lang)}</div>
      )}

      <div style={sectionLabelStyle}>{tl(LABELS.callStack, lang)}</div>
      <div style={stackStyle}>
        {Array.from({ length: freeSlots }).map((_, i) => (
          <div key={`free-${i}`} style={freeSlotStyle}>
            {tl(LABELS.free, lang)}
          </div>
        ))}
        {framesTopFirst.map((frame, index) => {
          const isTop = index === 0;
          const isBottom = frame.depth === 0;
          const hot = highlight.has(`frame:${frame.depth}`);
          return (
            <div
              key={`${frame.depth}-${frame.name}`}
              style={{
                ...frameStyle,
                ...(hot ? frameHotStyle : {}),
                ...(state.overflowed && hot ? frameOverflowStyle : {}),
              }}
            >
              <span style={frameNameStyle}>{`${frame.name}()`}</span>
              <span style={frameTagStyle}>
                {isTop ? tl(LABELS.topFrame, lang) : isBottom ? tl(LABELS.bottomFrame, lang) : `#${frame.depth}`}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={statValueStyle}>{value}</div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 8 };
const statsStyle: CSSProperties = { display: 'flex', gap: 12, flexWrap: 'wrap' };
const statStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '4px 8px',
  minWidth: 90,
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
const overflowBannerStyle: CSSProperties = {
  border: '1px solid var(--accent)',
  borderRadius: 6,
  background: 'rgba(229,83,75,0.16)',
  color: 'var(--accent)',
  fontWeight: 700,
  padding: '6px 10px',
  textAlign: 'center',
};
const stackStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 4 };
const frameStyle: CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  border: '1px solid var(--border)',
  borderRadius: 6,
  background: 'var(--viz-box)',
  padding: '7px 12px',
};
const frameHotStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.35)',
};
const frameOverflowStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'rgba(229,83,75,0.22)',
  boxShadow: '0 0 0 2px rgba(229,83,75,0.4)',
};
const frameNameStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700 };
const frameTagStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const freeSlotStyle: CSSProperties = {
  border: '1px dashed var(--border)',
  borderRadius: 6,
  padding: '7px 12px',
  textAlign: 'center',
  fontSize: 12,
  opacity: 0.4,
  fontStyle: 'italic',
};
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
