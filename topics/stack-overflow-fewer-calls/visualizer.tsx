import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  stackBudget: { en: 'stack budget', ru: 'бюджет стека' },
  used: { en: 'used', ru: 'занято' },
  remaining: { en: 'remaining', ru: 'осталось' },
  frames: { en: 'active frames', ru: 'активные кадры' },
  callStack: { en: 'Thread call stack', ru: 'Стек вызовов потока' },
  heapObjects: { en: 'Heap objects', ru: 'Объекты в heap' },
  topFrame: { en: 'top frame', ru: 'верхний кадр' },
  bottomFrame: { en: 'bottom frame', ru: 'нижний кадр' },
  frameBytes: { en: 'frame bytes', ru: 'байты кадра' },
  locals: { en: 'locals', ru: 'локальные слоты' },
  attempted: { en: 'attempted frame', ru: 'попытка кадра' },
  wouldOverflow: { en: 'does not fit', ru: 'не помещается' },
  noLocals: { en: 'no local slots', ru: 'нет локальных слотов' },
  noHeap: { en: 'no heap objects in this example', ru: 'в этом примере нет heap-объектов' },
  overflow: { en: 'StackOverflowError: no stack budget left', ru: 'StackOverflowError: бюджет стека закончился' },
  runHint: {
    en: 'Run the code to see how frame size changes the number of calls before StackOverflowError.',
    ru: 'Запустите код, чтобы увидеть, как размер кадра меняет число вызовов до StackOverflowError.',
  },
};

interface Frame {
  depth: number;
  name: string;
  bytes: number;
  locals: string[];
}

interface HeapObject {
  id: number;
  label: string;
  bytes: number;
}

interface Attempt {
  name: string;
  bytes: number;
  locals: string[];
  fits: boolean;
}

interface StackPressureState {
  stackBytes: number;
  usedBytes: number;
  remainingBytes: number;
  overflowed: boolean;
  frames: Frame[];
  heapObjects: HeapObject[];
  attempt: Attempt | null;
}

export default function StackOverflowFewerCallsVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as StackPressureState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const usedPercent = state.stackBytes === 0
    ? 0
    : Math.min(100, Math.round((state.usedBytes / state.stackBytes) * 100));
  const framesTopFirst = [...state.frames].reverse();

  const cells: ArrayCell[] = framesTopFirst.map((frame, index) => {
    const isTop = index === 0;
    const isBottom = frame.depth === 0;
    return {
      key: `${frame.depth}-${frame.name}`,
      label: isTop ? tl(LABELS.topFrame, lang) : isBottom ? tl(LABELS.bottomFrame, lang) : `#${frame.depth}`,
      highlighted: highlight.has(`frame:${frame.depth}`),
      content: <FrameBlock frame={frame} lang={lang} highlighted={highlight.has(`frame:${frame.depth}`)} />,
    };
  });

  return (
    <div style={wrapStyle}>
      <div style={statsStyle}>
        <Stat label={tl(LABELS.stackBudget, lang)} value={`${state.stackBytes} B`} />
        <Stat label={tl(LABELS.used, lang)} value={`${state.usedBytes} B`} hot={highlight.has('budget')} />
        <Stat label={tl(LABELS.remaining, lang)} value={`${state.remainingBytes} B`} hot={state.overflowed} />
        <Stat label={tl(LABELS.frames, lang)} value={state.frames.length} />
      </div>

      <div style={barTrackStyle}>
        <div
          style={{
            ...barFillStyle,
            width: `${usedPercent}%`,
            background: state.overflowed ? 'var(--accent)' : 'var(--viz-active)',
          }}
        />
      </div>

      {state.overflowed && <div style={overflowStyle}>{tl(LABELS.overflow, lang)}</div>}

      {state.attempt && (
        <div style={attemptStyle}>
          <span style={attemptTitleStyle}>{tl(LABELS.attempted, lang)}</span>
          <span style={monoStyle}>{`${state.attempt.name}() · ${state.attempt.bytes} B`}</span>
          {!state.attempt.fits && <span style={failTagStyle}>{tl(LABELS.wouldOverflow, lang)}</span>}
        </div>
      )}

      <div style={sectionLabelStyle}>{tl(LABELS.callStack, lang)}</div>
      <ArrayGrid cells={cells} />

      <div style={sectionLabelStyle}>{tl(LABELS.heapObjects, lang)}</div>
      <div style={heapStyle}>
        {state.heapObjects.length === 0 && <span style={emptyStyle}>{tl(LABELS.noHeap, lang)}</span>}
        {state.heapObjects.map((obj) => (
          <div
            key={obj.id}
            style={{
              ...heapObjectStyle,
              ...(highlight.has(`heap:${obj.id}`) ? heapHighlightStyle : {}),
            }}
          >
            <div style={monoStrongStyle}>{obj.label}</div>
            <div style={mutedStyle}>{`${obj.bytes} B`}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

function FrameBlock({ frame, lang, highlighted }: { frame: Frame; lang: Lang; highlighted: boolean }) {
  return (
    <div style={{ ...frameContentStyle, ...(highlighted ? frameHotStyle : {}) }}>
      <div style={frameHeaderStyle}>
        <span style={monoStrongStyle}>{`${frame.name}()`}</span>
        <span style={byteBadgeStyle}>{`${frame.bytes} B`}</span>
      </div>
      <div style={mutedStyle}>{tl(LABELS.locals, lang)}</div>
      <div style={localsStyle}>
        {frame.locals.length === 0 && <span style={emptyStyle}>{tl(LABELS.noLocals, lang)}</span>}
        {frame.locals.map((local) => (
          <span key={`${frame.depth}-${local}`} style={localStyle}>{local}</span>
        ))}
      </div>
    </div>
  );
}

function Stat({ label, value, hot }: { label: string; value: string | number; hot?: boolean }) {
  return (
    <div style={{ ...statStyle, ...(hot ? statHotStyle : {}) }}>
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
  minWidth: 92,
  textAlign: 'center',
};
const statHotStyle: CSSProperties = { borderColor: 'var(--accent)', background: 'var(--viz-highlight)' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const barTrackStyle: CSSProperties = {
  height: 9,
  borderRadius: 6,
  border: '1px solid var(--border)',
  overflow: 'hidden',
  background: 'var(--viz-box)',
};
const barFillStyle: CSSProperties = { height: '100%', transition: 'width 160ms ease-out' };
const sectionLabelStyle: CSSProperties = {
  fontSize: 11,
  textTransform: 'uppercase',
  letterSpacing: 0,
  opacity: 0.58,
};
const overflowStyle: CSSProperties = {
  border: '1px solid var(--accent)',
  borderRadius: 6,
  background: 'rgba(229,83,75,0.16)',
  color: 'var(--accent)',
  fontWeight: 700,
  padding: '6px 10px',
};
const attemptStyle: CSSProperties = {
  display: 'flex',
  gap: 8,
  alignItems: 'center',
  flexWrap: 'wrap',
  border: '1px dashed var(--accent)',
  borderRadius: 6,
  padding: '6px 10px',
  background: 'rgba(229,83,75,0.10)',
};
const attemptTitleStyle: CSSProperties = { fontSize: 12, opacity: 0.7 };
const failTagStyle: CSSProperties = {
  borderRadius: 4,
  padding: '1px 6px',
  background: 'rgba(229,83,75,0.18)',
  color: 'var(--accent)',
  fontSize: 11,
  fontWeight: 700,
};
const frameContentStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 5, width: '100%' };
const frameHotStyle: CSSProperties = { color: 'var(--text)' };
const frameHeaderStyle: CSSProperties = { display: 'flex', justifyContent: 'space-between', gap: 8, alignItems: 'center' };
const monoStyle: CSSProperties = { fontFamily: 'monospace' };
const monoStrongStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700 };
const byteBadgeStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 12,
  borderRadius: 4,
  padding: '1px 6px',
  background: 'var(--viz-badge)',
};
const localsStyle: CSSProperties = { display: 'flex', flexWrap: 'wrap', gap: 5 };
const localStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 12,
  border: '1px solid var(--border)',
  borderRadius: 4,
  padding: '2px 6px',
  background: 'var(--viz-box)',
};
const heapStyle: CSSProperties = { display: 'flex', flexWrap: 'wrap', gap: 6 };
const heapObjectStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '5px 8px',
  background: 'var(--viz-box)',
  minWidth: 130,
};
const heapHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.35)',
};
const mutedStyle: CSSProperties = { fontSize: 12, opacity: 0.65 };
const emptyStyle: CSSProperties = { fontSize: 12, opacity: 0.45, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
