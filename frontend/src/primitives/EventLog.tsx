import type { CSSProperties } from 'react';
import type { TraceEvent } from '@app/engine/traceTypes';
import { tl, ui, useLang } from '@app/i18n';

/**
 * The step-by-step ledger of trace events. The current step is highlighted and
 * each row is clickable to scrub playback. The event's description is the
 * built-in "why did this happen?" explanation, shown in the active language.
 */
export function EventLog({
  events,
  currentStep,
  onSelect,
}: {
  events: TraceEvent[];
  currentStep: number;
  onSelect: (index: number) => void;
}) {
  const lang = useLang((s) => s.lang);
  if (events.length === 0) {
    return <div style={emptyStyle}>{ui('eventLogEmpty', lang)}</div>;
  }
  return (
    <ol style={listStyle}>
      {events.map((ev, i) => (
        <li
          key={i}
          onClick={() => onSelect(i)}
          style={{ ...itemStyle, ...(i === currentStep ? activeStyle : {}) }}
        >
          <span style={badgeStyle}>{ev.event}</span>
          <span>{tl(ev.description, lang)}</span>
        </li>
      ))}
    </ol>
  );
}

const listStyle: CSSProperties = {
  listStyle: 'none',
  margin: 0,
  padding: 0,
  display: 'flex',
  flexDirection: 'column',
  gap: 3,
};
const itemStyle: CSSProperties = {
  display: 'flex',
  gap: 8,
  alignItems: 'baseline',
  padding: '5px 8px',
  borderRadius: 6,
  cursor: 'pointer',
  fontSize: 13,
  background: '#16202b',
};
const activeStyle: CSSProperties = {
  background: '#26384a',
  boxShadow: 'inset 2px 0 0 #ffcc66',
};
const badgeStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  background: '#324456',
  flexShrink: 0,
};
const emptyStyle: CSSProperties = { fontSize: 13, opacity: 0.5, padding: 8 };
