import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  exchanges: { en: 'exchanges (route, never store)', ru: 'exchange (маршрутизируют, не хранят)' },
  queues: { en: 'queues (ready messages)', ru: 'очереди (готовые сообщения)' },
  consumers: { en: 'consumers (unacked messages)', ru: 'потребители (неподтверждённые)' },
  depth: { en: 'depth', ru: 'глубина' },
  prefetch: { en: 'prefetch', ru: 'prefetch' },
  deadLetter: { en: 'dead-letter →', ru: 'dead-letter →' },
  noExchanges: { en: 'nothing declared yet', ru: 'пока ничего не объявлено' },
  noConsumers: { en: 'no consumer attached', ru: 'нет подключённых потребителей' },
  redelivered: { en: 'redelivered', ru: 'повторно' },
  published: { en: 'published', ru: 'опубликовано' },
  delivered: { en: 'delivered', ru: 'доставлено' },
  acked: { en: 'acked', ru: 'подтверждено' },
  requeued: { en: 'requeued', ru: 'возвращено' },
  deadLettered: { en: 'dead-lettered', ru: 'в dead-letter' },
  dropped: { en: 'lost', ru: 'потеряно' },
  idle: { en: 'no message in flight', ru: 'нет сообщения в полёте' },
  runHint: {
    en: 'Run the code to visualize the broker.',
    ru: 'Запустите код, чтобы увидеть работу брокера.',
  },
};

const STAGES: Record<string, { label: { en: string; ru: string }; color: string }> = {
  published: { label: { en: 'published', ru: 'опубликовано' }, color: 'var(--accent)' },
  routed: { label: { en: 'routed → queue', ru: 'смаршрутизировано → очередь' }, color: 'var(--accent)' },
  unroutable: { label: { en: 'unroutable — discarded ✗', ru: 'недоставляемо — выброшено ✗' }, color: 'var(--bad)' },
  delivered: { label: { en: 'delivered, unacked', ru: 'доставлено, unacked' }, color: 'var(--accent-2)' },
  redelivered: { label: { en: 'redelivered, unacked', ru: 'доставлено повторно, unacked' }, color: 'var(--bad)' },
  waiting: { label: { en: 'waiting — prefetch full', ru: 'ждёт — prefetch заполнен' }, color: 'var(--accent-2)' },
  acked: { label: { en: 'acked — deleted ✓', ru: 'подтверждено — удалено ✓' }, color: 'var(--good)' },
  requeued: { label: { en: 'requeued to the head', ru: 'возвращено в голову' }, color: 'var(--bad)' },
  'dead-lettered': { label: { en: 'dead-lettered', ru: 'ушло в dead-letter' }, color: 'var(--bad)' },
  dropped: { label: { en: 'dropped — lost forever ✗', ru: 'выброшено — потеряно навсегда ✗' }, color: 'var(--bad)' },
};

interface Msg {
  id: string;
  payload: string;
  redelivered: boolean;
}
interface Binding {
  queue: string;
  key: string;
}
interface Exchange {
  name: string;
  type: string;
  bindings: Binding[];
}
interface Queue {
  name: string;
  deadLetterQueue: string;
  messages: Msg[];
}
interface Consumer {
  name: string;
  queue: string;
  prefetch: number;
  unacked: Msg[];
}
interface Flight {
  id: string;
  payload: string;
  stage: string;
  exchange?: string;
  routingKey?: string;
  queue?: string;
  consumer?: string;
}
interface BrokerState {
  exchanges: Exchange[];
  queues: Queue[];
  consumers: Consumer[];
  stats: {
    published: number;
    delivered: number;
    acked: number;
    requeued: number;
    deadLettered: number;
    dropped: number;
  };
  flight?: Flight;
}

export default function BrokerVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as BrokerState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  return (
    <div style={wrapStyle}>
      <FlightBanner flight={state.flight} highlighted={highlight.has('flight')} lang={lang} />

      <div style={statsStyle}>
        <Stat label={tl(LABELS.published, lang)} value={state.stats.published} color="var(--accent)" />
        <Stat label={tl(LABELS.delivered, lang)} value={state.stats.delivered} color="var(--accent-2)" />
        <Stat label={tl(LABELS.acked, lang)} value={state.stats.acked} color="var(--good)" />
        <Stat label={tl(LABELS.requeued, lang)} value={state.stats.requeued} color="var(--bad)" />
        <Stat label={tl(LABELS.deadLettered, lang)} value={state.stats.deadLettered} color="var(--bad)" />
        <Stat label={tl(LABELS.dropped, lang)} value={state.stats.dropped} color="var(--bad)" />
      </div>

      <section>
        <div style={sectionLabelStyle}>{tl(LABELS.exchanges, lang)}</div>
        {state.exchanges.length === 0 ? (
          <div style={emptyStyle}>{tl(LABELS.noExchanges, lang)}</div>
        ) : (
          state.exchanges.map((ex) => (
            <div
              key={ex.name}
              style={{ ...rowStyle, ...(highlight.has(`exchange:${ex.name}`) ? rowHighlightStyle : {}) }}
            >
              <span style={nameStyle}>{ex.name}</span>
              <span style={badgeStyle}>{ex.type}</span>
              <span style={bindingsStyle}>
                {ex.bindings.length === 0
                  ? '—'
                  : ex.bindings.map((b) => `${b.key === '' ? '*' : b.key} → ${b.queue}`).join('   ')}
              </span>
            </div>
          ))
        )}
      </section>

      <section>
        <div style={sectionLabelStyle}>{tl(LABELS.queues, lang)}</div>
        {state.queues.map((q) => (
          <div
            key={q.name}
            style={{ ...blockStyle, ...(highlight.has(`queue:${q.name}`) ? rowHighlightStyle : {}) }}
          >
            <div style={rowStyle}>
              <span style={nameStyle}>{q.name}</span>
              <span style={metaStyle}>
                {tl(LABELS.depth, lang)} {q.messages.length}
              </span>
              {q.deadLetterQueue && (
                <span style={metaStyle}>
                  {tl(LABELS.deadLetter, lang)} {q.deadLetterQueue}
                </span>
              )}
            </div>
            <BoxGroup boxes={q.messages.map((m) => toBox(m, highlight, lang))} />
          </div>
        ))}
      </section>

      <section>
        <div style={sectionLabelStyle}>{tl(LABELS.consumers, lang)}</div>
        {state.consumers.length === 0 ? (
          <div style={emptyStyle}>{tl(LABELS.noConsumers, lang)}</div>
        ) : (
          state.consumers.map((c) => (
            <div
              key={c.name}
              style={{ ...blockStyle, ...(highlight.has(`consumer:${c.name}`) ? rowHighlightStyle : {}) }}
            >
              <div style={rowStyle}>
                <span style={nameStyle}>{c.name}</span>
                <span style={metaStyle}>← {c.queue}</span>
                <span style={metaStyle}>
                  {tl(LABELS.prefetch, lang)} {c.unacked.length}/{c.prefetch}
                </span>
              </div>
              <BoxGroup boxes={c.unacked.map((m) => toBox(m, highlight, lang))} />
            </div>
          ))
        )}
      </section>
    </div>
  );
}

function toBox(m: Msg, highlight: Set<string>, lang: Lang): Box {
  return {
    id: m.id,
    title: m.id,
    subtitle: m.redelivered ? `${m.payload} · ${tl(LABELS.redelivered, lang)}` : m.payload,
    highlighted: highlight.has(`msg:${m.id}`),
  };
}

function FlightBanner({
  flight,
  highlighted,
  lang,
}: {
  flight: Flight | undefined;
  highlighted: boolean;
  lang: Lang;
}) {
  if (!flight) {
    return <div style={{ ...bannerStyle, opacity: 0.5 }}>{tl(LABELS.idle, lang)}</div>;
  }
  const stage = STAGES[flight.stage];
  const route = [
    flight.exchange && `${flight.exchange}${flight.routingKey ? ` : ${flight.routingKey}` : ''}`,
    flight.queue,
    flight.consumer,
  ].filter(Boolean) as string[];

  return (
    <div style={{ ...bannerStyle, ...(highlighted ? bannerHighlightStyle : {}) }}>
      <span style={idStyle}>{flight.id}</span>
      <span style={payloadStyle}>{flight.payload}</span>
      {route.length > 0 && <span style={payloadStyle}>{route.join(' → ')}</span>}
      <span style={{ ...outcomeStyle, color: stage ? stage.color : 'var(--accent)' }}>
        {stage ? tl(stage.label, lang) : flight.stage}
      </span>
    </div>
  );
}

function Stat({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={{ ...statValueStyle, color }}>{value}</div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const bannerStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 10,
  padding: '8px 12px',
  borderRadius: 8,
  background: 'var(--viz-box)',
  border: '1px solid var(--viz-active)',
};
const bannerHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.30)',
};
const idStyle: CSSProperties = { fontWeight: 700, fontSize: 15, fontFamily: 'monospace' };
const payloadStyle: CSSProperties = { fontSize: 13, opacity: 0.8 };
const outcomeStyle: CSSProperties = { marginLeft: 'auto', fontSize: 13, fontWeight: 600 };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const blockStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 4,
  padding: '6px 8px',
  borderRadius: 8,
  border: '1px solid transparent',
  marginBottom: 6,
};
const rowStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 10,
  padding: '2px 0',
  flexWrap: 'wrap',
};
const rowHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
};
const nameStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700, fontSize: 13 };
const badgeStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const bindingsStyle: CSSProperties = { fontSize: 12, opacity: 0.75, fontFamily: 'monospace' };
const metaStyle: CSSProperties = { fontSize: 12, opacity: 0.7 };
const emptyStyle: CSSProperties = { fontSize: 12, opacity: 0.4, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
