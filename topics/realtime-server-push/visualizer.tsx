import type { CSSProperties, ReactNode } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to see what each transport delivers, how late it is, and what it costs.',
    ru: 'Запустите код, чтобы увидеть, что доставляет каждый транспорт, насколько поздно и какой ценой.',
  },
  transports: {
    SHORT_POLL: { en: 'short polling', ru: 'короткий опрос' },
    LONG_POLL: { en: 'long polling', ru: 'длинный опрос' },
    SSE: { en: 'Server-Sent Events', ru: 'Server-Sent Events' },
    WEBSOCKET: { en: 'WebSocket', ru: 'WebSocket' },
  },
  duplexes: {
    'client-pull': { en: 'client asks, server answers', ru: 'клиент спрашивает, сервер отвечает' },
    'server-push': { en: 'server to browser only', ru: 'только от сервера к браузеру' },
    'full-duplex': { en: 'both sides may send', ru: 'отправлять может любая сторона' },
  },
  kinds: {
    none: { en: 'nothing is open', ru: 'ничего не открыто' },
    'held-request': { en: 'a request is parked on the server', ru: 'запрос припаркован на сервере' },
    stream: { en: 'a stream is open', ru: 'поток открыт' },
    socket: { en: 'a socket is open', ru: 'сокет открыт' },
  },
  online: { en: 'reachable', ru: 'на связи' },
  offline: { en: 'unreachable', ru: 'связи нет' },
  tick: { en: 'tick', ru: 'тик' },
  interval: { en: 'every', ru: 'каждые' },
  hold: { en: 'held up to', ru: 'удержание до' },
  ticks: { en: 'tick(s)', ru: 'тик(ов)' },
  lastEventId: { en: 'last id the client saw', ru: 'последний id, увиденный клиентом' },
  instances: { en: 'server instances', ru: 'экземпляров сервера' },
  sharedBus: { en: 'shared bus attached', ru: 'подключена общая шина' },
  noBus: { en: 'no shared bus', ru: 'общей шины нет' },
  timeline: { en: 'timeline', ru: 'шкала времени' },
  waitingOnServer: { en: 'waiting on the server', ru: 'ждёт на сервере' },
  onScreen: { en: 'reached the browser', ru: 'дошло до браузера' },
  nothingWaiting: { en: 'nothing is waiting', ru: 'ничего не ждёт' },
  waited: { en: 'waited', ru: 'ждало' },
  late: { en: 'late by', ru: 'опоздало на' },
  legendEvent: { en: 'server event', ru: 'событие сервера' },
  legendRequest: { en: 'request', ru: 'запрос' },
  legendEmpty: { en: 'empty answer', ru: 'пустой ответ' },
  legendDelivery: { en: 'delivered', ru: 'доставлено' },
  legendHeld: { en: 'something held open', ru: 'что-то держится открытым' },
  statRequests: { en: 'HTTP requests', ru: 'HTTP-запросов' },
  statEmpty: { en: 'came back empty', ru: 'вернулись пустыми' },
  statFrames: { en: 'pushed frames', ru: 'кадров отправлено' },
  statDelivered: { en: 'events delivered', ru: 'событий доставлено' },
  statLost: { en: 'never delivered', ru: 'не доставлено' },
  statAvg: { en: 'average delay', ru: 'средняя задержка' },
  statMax: { en: 'worst delay', ru: 'худшая задержка' },
  comparison: { en: 'same events, four transports', ru: 'одни события, четыре транспорта' },
  colTransport: { en: 'transport', ru: 'транспорт' },
  colRequests: { en: 'requests', ru: 'запросов' },
  colEmpty: { en: 'empty', ru: 'пустых' },
  colFrames: { en: 'frames', ru: 'кадров' },
  colAvg: { en: 'avg delay', ru: 'ср. задержка' },
  colMax: { en: 'worst', ru: 'худшая' },
  colDuplex: { en: 'direction', ru: 'направление' },
  colHeld: { en: 'keeps a connection', ru: 'держит соединение' },
  yes: { en: 'yes', ru: 'да' },
  no: { en: 'no', ru: 'нет' },
};

type TransportCode = keyof typeof LABELS.transports;
type DuplexCode = keyof typeof LABELS.duplexes;
type KindCode = keyof typeof LABELS.kinds;

interface Connection {
  open: boolean;
  kind: KindCode;
  openedAt: number | null;
  lastEventId: string | null;
}
interface QueuedEvent {
  id: string;
  label: string;
  createdAt: number;
  waited: number;
}
interface DeliveredEvent {
  id: string;
  label: string;
  createdAt: number;
  deliveredAt: number;
  latency: number;
}
interface Slot {
  tick: number;
  serverEvent: boolean;
  request: boolean;
  empty: boolean;
  delivery: boolean;
  held: boolean;
}
interface Stats {
  requests: number;
  empty: number;
  frames: number;
  delivered: number;
  lost: number;
  avgLatency: number;
  maxLatency: number;
}
interface ComparisonRow {
  transport: TransportCode;
  requests: number;
  empty: number;
  frames: number;
  delivered: number;
  avgLatency: number;
  maxLatency: number;
  duplex: DuplexCode;
  held: boolean;
}
interface ChannelState {
  transport: TransportCode | null;
  app: string;
  now: number;
  online: boolean;
  duplex: DuplexCode | null;
  interval: number | null;
  hold: number | null;
  connection: Connection;
  sharedBus: boolean;
  instances: number;
  queue: QueuedEvent[];
  delivered: DeliveredEvent[];
  timeline: Slot[];
  stats: Stats;
  comparison?: ComparisonRow[];
}

export default function PushChannelVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as ChannelState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const comparison = state.comparison ?? [];
  if (comparison.length > 0) {
    return <ComparisonTable rows={comparison} lang={lang} />;
  }

  return (
    <div style={wrapStyle}>
      <Header state={state} lang={lang} />
      <Timeline slots={state.timeline} lang={lang} />

      <div style={sidesStyle}>
        <Pane
          title={tl(LABELS.waitingOnServer, lang)}
          highlighted={highlight.has('queue') || highlight.has('server')}
        >
          {state.queue.length === 0 ? (
            <div style={mutedStyle}>{tl(LABELS.nothingWaiting, lang)}</div>
          ) : (
            <BoxGroup
              boxes={state.queue.map((item) => ({
                id: item.id,
                title: item.label,
                subtitle: `${tl(LABELS.waited, lang)} ${item.waited} ${tl(LABELS.ticks, lang)}`,
                highlighted: true,
              }))}
            />
          )}
          <div style={factStyle}>
            {`${tl(LABELS.instances, lang)}: ${state.instances} · `}
            {tl(state.sharedBus ? LABELS.sharedBus : LABELS.noBus, lang)}
          </div>
        </Pane>

        <Pane
          title={tl(LABELS.onScreen, lang)}
          highlighted={highlight.has('delivered') || highlight.has('client')}
        >
          <BoxGroup
            boxes={state.delivered.map((item) => ({
              id: item.id,
              title: item.label,
              subtitle: `${tl(LABELS.late, lang)} ${item.latency} ${tl(LABELS.ticks, lang)}`,
              dim: false,
            }))}
          />
        </Pane>
      </div>

      <div style={statsStyle}>
        <Stat label={tl(LABELS.statRequests, lang)} value={state.stats.requests} />
        <Stat
          label={tl(LABELS.statEmpty, lang)}
          value={state.stats.empty}
          color={state.stats.empty > 0 ? 'var(--accent)' : undefined}
        />
        <Stat label={tl(LABELS.statFrames, lang)} value={state.stats.frames} />
        <Stat
          label={tl(LABELS.statDelivered, lang)}
          value={state.stats.delivered}
          color={state.stats.delivered > 0 ? 'var(--good)' : undefined}
        />
        <Stat
          label={tl(LABELS.statLost, lang)}
          value={state.stats.lost}
          color={state.stats.lost > 0 ? 'var(--bad)' : undefined}
        />
        <Stat label={tl(LABELS.statAvg, lang)} value={state.stats.avgLatency} />
        <Stat label={tl(LABELS.statMax, lang)} value={state.stats.maxLatency} />
      </div>
    </div>
  );
}

function Header({ state, lang }: { state: ChannelState; lang: Lang }) {
  const transport = state.transport;
  return (
    <div style={headerStyle}>
      <span style={badgeStyle}>{transport ?? '—'}</span>
      <span style={headNameStyle}>
        {transport ? tl(LABELS.transports[transport], lang) : ''}
      </span>
      {state.duplex && <span style={subBadgeStyle}>{tl(LABELS.duplexes[state.duplex], lang)}</span>}
      <span style={{ ...pillStyle, color: state.online ? 'var(--good)' : 'var(--bad)' }}>
        {tl(state.online ? LABELS.online : LABELS.offline, lang)}
      </span>
      <span style={{ ...pillStyle, color: state.connection.open ? 'var(--good)' : undefined }}>
        {tl(LABELS.kinds[state.connection.kind], lang)}
      </span>
      <span style={pillStyle}>{`${tl(LABELS.tick, lang)} ${state.now}`}</span>
      {state.interval !== null && (
        <span style={pillStyle}>
          {`${tl(LABELS.interval, lang)} ${state.interval} ${tl(LABELS.ticks, lang)}`}
        </span>
      )}
      {state.hold !== null && (
        <span style={pillStyle}>
          {`${tl(LABELS.hold, lang)} ${state.hold} ${tl(LABELS.ticks, lang)}`}
        </span>
      )}
      {state.connection.lastEventId && (
        <span style={pillStyle}>
          {`${tl(LABELS.lastEventId, lang)}: ${state.connection.lastEventId}`}
        </span>
      )}
      <span style={appStyle}>{state.app}</span>
    </div>
  );
}

function Timeline({ slots, lang }: { slots: Slot[]; lang: Lang }) {
  return (
    <div>
      <div style={sectionLabelStyle}>{tl(LABELS.timeline, lang)}</div>
      <div style={stripStyle}>
        {slots.map((slot) => (
          <div key={slot.tick} style={{ ...cellStyle, ...(slot.held ? cellHeldStyle : {}) }}>
            <div style={markStyle}>{slot.serverEvent ? '●' : ''}</div>
            <div
              style={{
                ...markStyle,
                color: slot.delivery ? 'var(--good)' : slot.empty ? 'var(--bad)' : 'var(--accent)',
              }}
            >
              {slot.delivery ? '▼' : slot.request ? (slot.empty ? '×' : '↑') : ''}
            </div>
            <div style={tickLabelStyle}>{slot.tick}</div>
          </div>
        ))}
      </div>
      <div style={legendStyle}>
        <span>● {tl(LABELS.legendEvent, lang)}</span>
        <span>↑ {tl(LABELS.legendRequest, lang)}</span>
        <span style={{ color: 'var(--bad)' }}>× {tl(LABELS.legendEmpty, lang)}</span>
        <span style={{ color: 'var(--good)' }}>▼ {tl(LABELS.legendDelivery, lang)}</span>
        <span style={{ opacity: 0.7 }}>▬ {tl(LABELS.legendHeld, lang)}</span>
      </div>
    </div>
  );
}

function ComparisonTable({ rows, lang }: { rows: ComparisonRow[]; lang: Lang }) {
  return (
    <div style={wrapStyle}>
      <div style={sectionLabelStyle}>{tl(LABELS.comparison, lang)}</div>
      <div style={tableStyle}>
        <div style={tableHeadStyle}>
          <span style={cellTextStyle}>{tl(LABELS.colTransport, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colRequests, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colEmpty, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colFrames, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colAvg, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colMax, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colDuplex, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colHeld, lang)}</span>
        </div>
        {rows.map((row) => (
          <div key={row.transport} style={tableRowStyle}>
            <span style={{ ...cellTextStyle, fontWeight: 700 }}>{row.transport}</span>
            <span style={cellTextStyle}>{row.requests}</span>
            <span style={{ ...cellTextStyle, color: row.empty > 0 ? 'var(--bad)' : undefined }}>
              {row.empty}
            </span>
            <span style={cellTextStyle}>{row.frames}</span>
            <span
              style={{ ...cellTextStyle, color: row.avgLatency === 0 ? 'var(--good)' : 'var(--accent)' }}
            >
              {row.avgLatency}
            </span>
            <span style={cellTextStyle}>{row.maxLatency}</span>
            <span style={cellTextStyle}>{tl(LABELS.duplexes[row.duplex], lang)}</span>
            <span style={cellTextStyle}>{tl(row.held ? LABELS.yes : LABELS.no, lang)}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function Pane({
  title,
  highlighted,
  children,
}: {
  title: string;
  highlighted: boolean;
  children: ReactNode;
}) {
  return (
    <div style={{ ...paneStyle, ...(highlighted ? paneHighlightStyle : {}) }}>
      <div style={sectionLabelStyle}>{title}</div>
      {children}
    </div>
  );
}

function Stat({ label, value, color }: { label: string; value: number; color?: string }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={{ ...statValueStyle, color: color ?? 'inherit' }}>{value}</div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const headerStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  flexWrap: 'wrap',
};
const badgeStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 12,
  fontWeight: 700,
  padding: '2px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const headNameStyle: CSSProperties = { fontSize: 13, fontWeight: 600 };
const subBadgeStyle: CSSProperties = { fontSize: 11, opacity: 0.8 };
const pillStyle: CSSProperties = {
  fontSize: 11,
  opacity: 0.75,
  border: '1px solid var(--border)',
  borderRadius: 10,
  padding: '1px 7px',
};
const appStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 11, opacity: 0.55 };
const stripStyle: CSSProperties = { display: 'flex', gap: 2, flexWrap: 'wrap' };
const cellStyle: CSSProperties = {
  width: 22,
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  border: '1px solid var(--border)',
  borderRadius: 4,
  padding: '2px 0',
  background: 'var(--viz-box)',
};
const cellHeldStyle: CSSProperties = {
  background: 'var(--viz-highlight)',
  borderColor: 'var(--viz-active)',
};
const markStyle: CSSProperties = { fontSize: 11, lineHeight: '13px', minHeight: 13 };
const tickLabelStyle: CSSProperties = { fontSize: 9, opacity: 0.5, fontFamily: 'monospace' };
const legendStyle: CSSProperties = {
  display: 'flex',
  gap: 12,
  flexWrap: 'wrap',
  fontSize: 10,
  opacity: 0.7,
  marginTop: 4,
};
const sidesStyle: CSSProperties = { display: 'flex', gap: 10, alignItems: 'stretch', flexWrap: 'wrap' };
const paneStyle: CSSProperties = {
  flex: '1 1 240px',
  minWidth: 220,
  display: 'flex',
  flexDirection: 'column',
  gap: 6,
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '8px 10px',
  background: 'var(--viz-box)',
};
const paneHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
};
const factStyle: CSSProperties = { fontSize: 11, opacity: 0.7, fontFamily: 'monospace' };
const tableStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 2 };
const tableHeadStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1.4fr repeat(5, 0.7fr) 1.4fr 1fr',
  fontSize: 10,
  opacity: 0.6,
};
const tableRowStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1.4fr repeat(5, 0.7fr) 1.4fr 1fr',
  fontSize: 11,
  fontFamily: 'monospace',
  padding: '3px 0',
  borderTop: '1px solid var(--border)',
  alignItems: 'center',
};
const cellTextStyle: CSSProperties = { paddingRight: 6, overflowWrap: 'anywhere' };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 2 };
const mutedStyle: CSSProperties = { fontSize: 12, opacity: 0.5, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
