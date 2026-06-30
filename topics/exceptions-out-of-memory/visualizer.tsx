import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize exception flow and memory pressure.',
    ru: 'Запустите код, чтобы увидеть поток исключений и давление на память.',
  },
  throwable: { en: 'Throwable in flight', ru: 'Throwable в полёте' },
  noException: { en: 'No Throwable in flight.', ru: 'Throwable в полёте нет.' },
  callStack: { en: 'Call stack', ru: 'Стек вызовов' },
  emptyStack: { en: '(no frames)', ru: '(нет фреймов)' },
  top: { en: 'top', ru: 'верх' },
  catches: { en: 'catches', ru: 'ловит' },
  noCatch: { en: 'no catch', ru: 'без catch' },
  finally: { en: 'finally', ru: 'finally' },
  heapBudget: { en: 'Heap budget', ru: 'Бюджет heap' },
  used: { en: 'used', ru: 'занято' },
  free: { en: 'free', ru: 'свободно' },
  phase: { en: 'phase', ru: 'фаза' },
  allocations: { en: 'Allocations', ru: 'Выделения' },
  noAllocations: { en: 'No retained allocations yet.', ru: 'Удержанных выделений пока нет.' },
  retained: { en: 'retained', ru: 'удержано' },
  released: { en: 'released', ru: 'освобождено' },
  ERROR: { en: 'Error', ru: 'Error' },
  CHECKED: { en: 'checked Exception', ru: 'checked Exception' },
  UNCHECKED: { en: 'unchecked RuntimeException', ru: 'unchecked RuntimeException' },
  'in-flight': { en: 'in flight', ru: 'в полёте' },
  caught: { en: 'caught', ru: 'поймано' },
  uncaught: { en: 'UNCAUGHT', ru: 'НЕ ПОЙМАНО' },
  info: { en: 'type info', ru: 'тип' },
};

const KIND_LABELS: Record<string, Localized> = {
  essential: { en: 'essential', ru: 'обязательное' },
  cache: { en: 'cache', ru: 'cache' },
};

const PHASE_LABELS: Record<string, Localized> = {
  normal: { en: 'normal', ru: 'норма' },
  pressure: { en: 'pressure', ru: 'давление' },
  exhausted: { en: 'exhausted', ru: 'исчерпано' },
  recovering: { en: 'recovering', ru: 'восстановление' },
  failed: { en: 'failed', ru: 'сбой' },
};

interface Frame {
  method: string;
  catches: string | null;
  hasFinally: boolean;
}

type Category = 'ERROR' | 'CHECKED' | 'UNCHECKED';
type Status = 'in-flight' | 'caught' | 'uncaught' | 'info';

interface ExceptionState {
  type: string;
  message: string;
  category: Category;
  status: Status;
}

interface Allocation {
  name: string;
  megabytes: number;
  kind: 'essential' | 'cache';
  retained: boolean;
}

interface MemoryState {
  limitMb: number;
  usedMb: number;
  freeMb: number;
  phase: 'normal' | 'pressure' | 'exhausted' | 'recovering' | 'failed';
  allocations: Allocation[];
}

interface State {
  stack: Frame[];
  exception: ExceptionState | null;
  memory?: MemoryState;
}

const CATEGORY_COLOR: Record<Category, string> = {
  ERROR: '#e06c75',
  CHECKED: '#61afef',
  UNCHECKED: '#e5a04c',
};

const STATUS_COLOR: Record<Status, string> = {
  'in-flight': '#e5a04c',
  caught: '#98c379',
  uncaught: '#e06c75',
  info: 'var(--text)',
};

const PHASE_COLOR: Record<MemoryState['phase'], string> = {
  normal: '#98c379',
  pressure: '#e5a04c',
  exhausted: '#e06c75',
  recovering: '#61afef',
  failed: '#e06c75',
};

export default function ExceptionsOutOfMemoryVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as State | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const topIndex = state.stack.length - 1;
  const stackCells: ArrayCell[] = state.stack
    .map((frame, idx) => ({ frame, idx }))
    .reverse()
    .map(({ frame, idx }) => ({
      key: idx,
      label: idx === topIndex ? `> ${tl(LABELS.top, lang)}` : '',
      highlighted: highlight.has(`frame:${idx}`),
      content: (
        <div style={frameStyle}>
          <span style={methodStyle}>{frame.method}()</span>
          <span style={metaStyle}>
            {frame.catches
              ? `${tl(LABELS.catches, lang)} ${frame.catches}`
              : tl(LABELS.noCatch, lang)}
            {frame.hasFinally ? ` · ${tl(LABELS.finally, lang)}` : ''}
          </span>
        </div>
      ),
    }));

  return (
    <div style={wrapStyle}>
      <ThrowablePanel exception={state.exception} />
      {state.memory && <MemoryPanel memory={state.memory} highlighted={highlight.has('memory')} />}

      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.callStack, lang)}</div>
        {stackCells.length > 0 ? (
          <ArrayGrid cells={stackCells} />
        ) : (
          <div style={emptyStyle}>{tl(LABELS.emptyStack, lang)}</div>
        )}
      </section>
    </div>
  );
}

function ThrowablePanel({ exception }: { exception: ExceptionState | null }) {
  const lang = useLang((s) => s.lang);
  if (!exception) {
    return <div style={emptyStyle}>{tl(LABELS.noException, lang)}</div>;
  }
  return (
    <section style={{ ...bannerStyle, borderColor: STATUS_COLOR[exception.status] }}>
      <div style={bannerTopStyle}>
        <span style={typeStyle}>{exception.type}</span>
        <span style={{ ...badgeStyle, background: CATEGORY_COLOR[exception.category] }}>
          {tl(LABELS[exception.category], lang)}
        </span>
        <span style={{ ...statusStyle, color: STATUS_COLOR[exception.status] }}>
          {tl(LABELS[exception.status], lang)}
        </span>
      </div>
      {exception.message && <div style={messageStyle}>{exception.message}</div>}
    </section>
  );
}

function MemoryPanel({ memory, highlighted }: { memory: MemoryState; highlighted: boolean }) {
  const lang = useLang((s) => s.lang);
  const usedPercent = Math.min(100, Math.round((memory.usedMb / memory.limitMb) * 100));
  const allocationCells: ArrayCell[] = memory.allocations.map((allocation) => ({
    key: allocation.name,
    label: allocation.name,
    highlighted: false,
    content: (
      <div style={{ ...allocationStyle, opacity: allocation.retained ? 1 : 0.45 }}>
        <span style={methodStyle}>{allocation.megabytes} MB</span>
        <span style={metaStyle}>{tl(KIND_LABELS[allocation.kind], lang)}</span>
        <span style={allocation.retained ? retainedStyle : releasedStyle}>
          {tl(allocation.retained ? LABELS.retained : LABELS.released, lang)}
        </span>
      </div>
    ),
  }));

  return (
    <section style={{ ...memoryStyle, ...(highlighted ? memoryHighlightedStyle : {}) }}>
      <div style={titleStyle}>{tl(LABELS.heapBudget, lang)}</div>
      <div style={meterOuterStyle}>
        <div
          style={{
            ...meterInnerStyle,
            width: `${usedPercent}%`,
            background: PHASE_COLOR[memory.phase],
          }}
        />
      </div>
      <div style={statsStyle}>
        <span>
          {tl(LABELS.used, lang)}: <strong>{memory.usedMb} MB</strong>
        </span>
        <span>
          {tl(LABELS.free, lang)}: <strong>{memory.freeMb} MB</strong>
        </span>
        <span>
          {tl(LABELS.phase, lang)}:{' '}
          <strong style={{ color: PHASE_COLOR[memory.phase] }}>
            {tl(PHASE_LABELS[memory.phase], lang)}
          </strong>
        </span>
      </div>
      <div style={subTitleStyle}>{tl(LABELS.allocations, lang)}</div>
      {allocationCells.length > 0 ? (
        <ArrayGrid cells={allocationCells} />
      ) : (
        <div style={emptyStyle}>{tl(LABELS.noAllocations, lang)}</div>
      )}
    </section>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
const sectionStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const titleStyle: CSSProperties = { fontSize: 12, fontWeight: 700, opacity: 0.72 };
const subTitleStyle: CSSProperties = { fontSize: 11, fontWeight: 700, opacity: 0.62 };
const bannerStyle: CSSProperties = {
  border: '2px solid var(--border)',
  borderRadius: 8,
  padding: '8px 12px',
  background: 'var(--viz-box)',
};
const bannerTopStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' };
const typeStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700, fontSize: 15 };
const badgeStyle: CSSProperties = {
  fontSize: 11,
  fontWeight: 700,
  color: '#1b1b1b',
  padding: '1px 8px',
  borderRadius: 10,
};
const statusStyle: CSSProperties = { fontSize: 12, fontWeight: 700, marginLeft: 'auto' };
const messageStyle: CSSProperties = { fontSize: 12, opacity: 0.75, marginTop: 4, fontFamily: 'monospace' };
const emptyStyle: CSSProperties = { fontSize: 13, opacity: 0.55, padding: 4, fontStyle: 'italic' };
const frameStyle: CSSProperties = { display: 'flex', alignItems: 'baseline', gap: 10, flexWrap: 'wrap' };
const methodStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700, fontSize: 13 };
const metaStyle: CSSProperties = { fontSize: 12, opacity: 0.7 };
const memoryStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 8,
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: 10,
  background: 'var(--viz-box)',
};
const memoryHighlightedStyle: CSSProperties = { boxShadow: 'inset 2px 0 0 var(--accent)' };
const meterOuterStyle: CSSProperties = {
  height: 12,
  borderRadius: 6,
  background: 'var(--bg)',
  overflow: 'hidden',
  border: '1px solid var(--border)',
};
const meterInnerStyle: CSSProperties = { height: '100%', transition: 'width 180ms ease' };
const statsStyle: CSSProperties = { display: 'flex', gap: 16, flexWrap: 'wrap', fontSize: 12 };
const allocationStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' };
const retainedStyle: CSSProperties = { fontSize: 11, color: '#98c379', fontWeight: 700 };
const releasedStyle: CSSProperties = { fontSize: 11, color: '#61afef', fontWeight: 700 };
