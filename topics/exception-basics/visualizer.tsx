import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  callStack: { en: 'Call stack', ru: 'Стек вызовов' },
  emptyStack: { en: '(no frames)', ru: '(нет фреймов)' },
  top: { en: 'top', ru: 'вершина' },
  catches: { en: 'catches', ru: 'ловит' },
  finally: { en: 'finally', ru: 'finally' },
  noCatch: { en: 'no catch', ru: 'без catch' },
  runHint: {
    en: 'Run the code to visualize exceptions.',
    ru: 'Запустите код, чтобы визуализировать исключения.',
  },
  noException: { en: 'No exception in flight.', ru: 'Исключений в полёте нет.' },
  // categories
  ERROR: { en: 'Error', ru: 'Error' },
  CHECKED: { en: 'checked Exception', ru: 'проверяемое Exception' },
  UNCHECKED: { en: 'unchecked RuntimeException', ru: 'непроверяемое RuntimeException' },
  // statuses
  'in-flight': { en: 'in flight', ru: 'в полёте' },
  caught: { en: 'caught', ru: 'поймано' },
  uncaught: { en: 'UNCAUGHT', ru: 'НЕ ПОЙМАНО' },
  info: { en: 'type info', ru: 'о типе' },
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
interface State {
  stack: Frame[];
  exception: ExceptionState | null;
}

const CATEGORY_COLOR: Record<Category, string> = {
  ERROR: '#e06c75',
  UNCHECKED: '#e5a04c',
  CHECKED: '#61afef',
};
const STATUS_COLOR: Record<Status, string> = {
  'in-flight': '#e5a04c',
  caught: '#98c379',
  uncaught: '#e06c75',
  info: 'var(--text)',
};

export default function ExceptionVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as State | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const topIndex = state.stack.length - 1;

  // Render the stack with the top frame first (the way a stack trace reads).
  const cells: ArrayCell[] = state.stack
    .map((frame, idx) => ({ frame, idx }))
    .reverse()
    .map(({ frame, idx }) => {
      const tags: string[] = [];
      if (frame.catches) tags.push(`${tl(LABELS.catches, lang)} ${frame.catches}`);
      else tags.push(tl(LABELS.noCatch, lang));
      if (frame.hasFinally) tags.push(tl(LABELS.finally, lang));
      return {
        key: idx,
        label: idx === topIndex ? `▸ ${tl(LABELS.top, lang)}` : '',
        highlighted: highlight.has(`frame:${idx}`),
        content: (
          <div style={frameStyle}>
            <span style={methodStyle}>{frame.method}()</span>
            <span style={tagStyle}>{tags.join(' · ')}</span>
          </div>
        ),
      };
    });

  const ex = state.exception;

  return (
    <div style={wrapStyle}>
      {ex ? (
        <div style={{ ...bannerStyle, borderColor: STATUS_COLOR[ex.status] }}>
          <div style={bannerTopStyle}>
            <span style={typeStyle}>{ex.type}</span>
            <span style={{ ...catBadgeStyle, background: CATEGORY_COLOR[ex.category] }}>
              {tl(LABELS[ex.category], lang)}
            </span>
            <span style={{ ...statusStyle, color: STATUS_COLOR[ex.status] }}>
              {tl(LABELS[ex.status], lang)}
            </span>
          </div>
          {ex.message && <div style={messageStyle}>"{ex.message}"</div>}
        </div>
      ) : (
        <div style={noExStyle}>{tl(LABELS.noException, lang)}</div>
      )}

      <div style={stackLabelStyle}>{tl(LABELS.callStack, lang)}</div>
      {cells.length > 0 ? (
        <ArrayGrid cells={cells} />
      ) : (
        <div style={noExStyle}>{tl(LABELS.emptyStack, lang)}</div>
      )}
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 10 };
const bannerStyle: CSSProperties = {
  border: '2px solid var(--border)',
  borderRadius: 8,
  padding: '8px 12px',
  background: 'var(--viz-box)',
};
const bannerTopStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' };
const typeStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700, fontSize: 15 };
const catBadgeStyle: CSSProperties = {
  fontSize: 11,
  fontWeight: 600,
  color: '#1b1b1b',
  padding: '1px 8px',
  borderRadius: 10,
};
const statusStyle: CSSProperties = { fontSize: 12, fontWeight: 700, marginLeft: 'auto' };
const messageStyle: CSSProperties = { fontSize: 12, opacity: 0.75, marginTop: 4, fontFamily: 'monospace' };
const noExStyle: CSSProperties = { fontSize: 13, opacity: 0.5, padding: 4, fontStyle: 'italic' };
const stackLabelStyle: CSSProperties = { fontSize: 12, opacity: 0.6, fontWeight: 600 };
const frameStyle: CSSProperties = { display: 'flex', alignItems: 'baseline', gap: 10, flexWrap: 'wrap' };
const methodStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 600, fontSize: 14 };
const tagStyle: CSSProperties = { fontSize: 11, opacity: 0.65 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
