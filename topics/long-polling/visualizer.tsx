import type { CSSProperties, ReactNode } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid } from '@app/primitives/ArrayGrid';
import { BoxGroup } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to see when the response is written, and what holding a request costs.',
    ru: 'Запустите код, чтобы увидеть, когда записывается ответ и во что обходится придержанный запрос.',
  },
  styles: {
    IMMEDIATE: { en: 'answers immediately', ru: 'отвечает сразу' },
    HELD_BLOCKING: { en: 'holds the request on its thread', ru: 'держит запрос на своём потоке' },
    HELD_ASYNC: { en: 'holds the request, frees the thread', ru: 'держит запрос, освобождает поток' },
  },
  tick: { en: 'tick', ru: 'тик' },
  hold: { en: 'hold up to', ru: 'удержание до' },
  ticks: { en: 'tick(s)', ru: 'тик(ов)' },
  cursorOn: { en: 'client sends since=', ru: 'клиент присылает since=' },
  cursorOff: { en: 'no cursor', ru: 'без курсора' },
  pool: { en: 'worker threads', ru: 'рабочие потоки' },
  worker: { en: 'worker', ru: 'воркер' },
  free: { en: 'free', ru: 'свободен' },
  blockedBy: { en: 'blocked by', ru: 'занят' },
  inflight: { en: 'requests not answered yet', ru: 'ещё не отвеченные запросы' },
  nothingParked: { en: 'nothing is being held open', ru: 'ничего не держится открытым' },
  parkedFor: { en: 'parked', ru: 'припаркован' },
  since: { en: 'since', ru: 'since' },
  onThread: { en: 'on a thread', ru: 'на потоке' },
  noThread: { en: 'no thread', ru: 'без потока' },
  log: { en: 'server event log', ru: 'журнал событий сервера' },
  emptyLog: { en: 'nothing has happened yet', ru: 'пока ничего не произошло' },
  at: { en: 'at tick', ru: 'на тике' },
  sentTo: { en: 'sent to', ru: 'отправлено' },
  clientsCount: { en: 'client(s)', ru: 'клиент(ов)' },
  missedLabel: { en: 'never asked for', ru: 'никто не спросит' },
  clients: { en: 'clients', ru: 'клиенты' },
  cursorAt: { en: 'cursor', ru: 'курсор' },
  got: { en: 'got', ru: 'получил' },
  timeline: { en: 'timeline', ru: 'шкала времени' },
  legendEvent: { en: 'server event', ru: 'событие сервера' },
  legendRequest: { en: 'request arrived', ru: 'пришёл запрос' },
  legendEmpty: { en: 'answered with nothing', ru: 'ответ ни о чём' },
  legendDelivery: { en: 'response with events', ru: 'ответ с событиями' },
  legendParked: { en: 'a request was held open', ru: 'запрос держали открытым' },
  statRequests: { en: 'requests', ru: 'запросов' },
  statEmpty: { en: 'came back empty', ru: 'вернулись пустыми' },
  statDelivered: { en: 'events delivered', ru: 'событий доставлено' },
  statMissed: { en: 'never delivered', ru: 'не доставлено' },
  statRefused: { en: 'refused, pool full', ru: 'отказано, пул занят' },
  statAvg: { en: 'average delay', ru: 'средняя задержка' },
  statMax: { en: 'worst delay', ru: 'худшая задержка' },
  statPeak: { en: 'peak held requests', ru: 'пик придержанных' },
  statWorker: { en: 'worker-ticks spent', ru: 'воркер-тиков' },
  comparison: { en: 'the same events, priced three ways', ru: 'одни события, три ценника' },
  colStyle: { en: 'style', ru: 'стиль' },
  colRequests: { en: 'requests', ru: 'запросов' },
  colEmpty: { en: 'empty', ru: 'пустых' },
  colDelivered: { en: 'delivered', ru: 'доставлено' },
  colAvg: { en: 'avg delay', ru: 'ср. задержка' },
  colMax: { en: 'worst', ru: 'худшая' },
  colWorker: { en: 'worker-ticks', ru: 'воркер-тиков' },
  colParked: { en: 'held per client', ru: 'держит на клиента' },
};

type StyleCode = keyof typeof LABELS.styles;

interface Pool {
  size: number;
  busy: number;
  parked: number;
}
interface Inflight {
  client: string;
  openedAt: number;
  waited: number;
  since: number;
  holdsWorker: boolean;
}
interface LogEntry {
  seq: number;
  label: string;
  createdAt: number;
  deliveries: number;
  missed: boolean;
}
interface ClientRow {
  name: string;
  cursor: number;
  received: number;
  lastLatency: number | null;
}
interface Slot {
  tick: number;
  request: boolean;
  empty: boolean;
  delivery: boolean;
  serverEvent: boolean;
  parked: boolean;
  busy: number;
}
interface Stats {
  requests: number;
  empty: number;
  delivered: number;
  missed: number;
  refused: number;
  avgLatency: number;
  maxLatency: number;
  peakParked: number;
  workerTicks: number;
}
interface ComparisonRow {
  style: StyleCode;
  requests: number;
  empty: number;
  delivered: number;
  avgLatency: number;
  maxLatency: number;
  workerTicks: number;
  parkedPeak: number;
}
interface LongPollState {
  endpoint: string;
  style: StyleCode | null;
  now: number;
  holdTicks: number | null;
  cursor: boolean;
  pool: Pool;
  inflight: Inflight[];
  log: LogEntry[];
  clients: ClientRow[];
  timeline: Slot[];
  stats: Stats;
  comparison?: ComparisonRow[];
}

export default function LongPollVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as LongPollState | undefined;
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

      <Pane title={tl(LABELS.pool, lang)} highlighted={highlight.has('pool')}>
        <BoxGroup boxes={workerBoxes(state, lang)} />
      </Pane>

      <div style={sidesStyle}>
        <Pane
          title={tl(LABELS.inflight, lang)}
          highlighted={highlight.has('parked') || highlight.has('request')}
        >
          {state.inflight.length === 0 ? (
            <div style={mutedStyle}>{tl(LABELS.nothingParked, lang)}</div>
          ) : (
            <BoxGroup
              boxes={state.inflight.map((request) => ({
                id: `${request.client}@${request.openedAt}`,
                title: request.client,
                subtitle:
                  `${tl(LABELS.parkedFor, lang)} ${request.waited} ${tl(LABELS.ticks, lang)}` +
                  ` · ${tl(LABELS.since, lang)}=${request.since}` +
                  ` · ${tl(request.holdsWorker ? LABELS.onThread : LABELS.noThread, lang)}`,
                highlighted: true,
              }))}
            />
          )}
        </Pane>

        <Pane
          title={tl(LABELS.clients, lang)}
          highlighted={highlight.has('client') || highlight.has('delivered')}
        >
          {state.clients.length === 0 ? (
            <div style={mutedStyle}>—</div>
          ) : (
            <BoxGroup
              boxes={state.clients.map((client) => ({
                id: client.name,
                title: client.name,
                subtitle:
                  `${tl(LABELS.cursorAt, lang)}=${client.cursor}` +
                  ` · ${tl(LABELS.got, lang)} ${client.received}`,
              }))}
            />
          )}
        </Pane>
      </div>

      <Pane
        title={tl(LABELS.log, lang)}
        highlighted={highlight.has('log') || highlight.has('server')}
      >
        {state.log.length === 0 ? (
          <div style={mutedStyle}>{tl(LABELS.emptyLog, lang)}</div>
        ) : (
          <ArrayGrid
            cells={state.log.map((entry) => ({
              key: entry.seq,
              label: `#${entry.seq}`,
              highlighted: entry.deliveries === 0 && !entry.missed,
              content: (
                <span style={entry.missed ? logMissedStyle : logStyle}>
                  {entry.label}
                  <span style={logMetaStyle}>
                    {` · ${tl(LABELS.at, lang)} ${entry.createdAt}`}
                    {entry.missed
                      ? ` · ${tl(LABELS.missedLabel, lang)}`
                      : ` · ${tl(LABELS.sentTo, lang)} ${entry.deliveries} ${tl(LABELS.clientsCount, lang)}`}
                  </span>
                </span>
              ),
            }))}
          />
        )}
      </Pane>

      <div style={statsStyle}>
        <Stat label={tl(LABELS.statRequests, lang)} value={state.stats.requests} />
        <Stat
          label={tl(LABELS.statEmpty, lang)}
          value={state.stats.empty}
          color={state.stats.empty > 0 ? 'var(--accent)' : undefined}
        />
        <Stat
          label={tl(LABELS.statDelivered, lang)}
          value={state.stats.delivered}
          color={state.stats.delivered > 0 ? 'var(--good)' : undefined}
        />
        <Stat
          label={tl(LABELS.statMissed, lang)}
          value={state.stats.missed}
          color={state.stats.missed > 0 ? 'var(--bad)' : undefined}
        />
        <Stat
          label={tl(LABELS.statRefused, lang)}
          value={state.stats.refused}
          color={state.stats.refused > 0 ? 'var(--bad)' : undefined}
        />
        <Stat label={tl(LABELS.statAvg, lang)} value={state.stats.avgLatency} />
        <Stat label={tl(LABELS.statMax, lang)} value={state.stats.maxLatency} />
        <Stat label={tl(LABELS.statPeak, lang)} value={state.stats.peakParked} />
        <Stat label={tl(LABELS.statWorker, lang)} value={state.stats.workerTicks} />
      </div>
    </div>
  );
}

/** One box per worker thread: who is blocked inside it, or that it is free. */
function workerBoxes(state: LongPollState, lang: Lang) {
  const blocked = state.inflight.filter((request) => request.holdsWorker);
  return Array.from({ length: state.pool.size }, (_, index) => {
    const holder = blocked[index];
    return {
      id: `worker-${index}`,
      title: `${tl(LABELS.worker, lang)} ${index}`,
      subtitle: holder ? `${tl(LABELS.blockedBy, lang)} ${holder.client}` : tl(LABELS.free, lang),
      highlighted: Boolean(holder),
    };
  });
}

function Header({ state, lang }: { state: LongPollState; lang: Lang }) {
  return (
    <div style={headerStyle}>
      <span style={badgeStyle}>{state.endpoint}</span>
      {state.style && <span style={headNameStyle}>{tl(LABELS.styles[state.style], lang)}</span>}
      <span style={pillStyle}>{`${tl(LABELS.tick, lang)} ${state.now}`}</span>
      {state.holdTicks !== null && (
        <span style={pillStyle}>
          {`${tl(LABELS.hold, lang)} ${state.holdTicks} ${tl(LABELS.ticks, lang)}`}
        </span>
      )}
      <span style={{ ...pillStyle, color: state.cursor ? 'var(--good)' : 'var(--bad)' }}>
        {tl(state.cursor ? LABELS.cursorOn : LABELS.cursorOff, lang)}
      </span>
      <span style={pillStyle}>
        {`${tl(LABELS.pool, lang)}: ${state.pool.busy}/${state.pool.size}`}
      </span>
    </div>
  );
}

function Timeline({ slots, lang }: { slots: Slot[]; lang: Lang }) {
  return (
    <div>
      <div style={sectionLabelStyle}>{tl(LABELS.timeline, lang)}</div>
      <div style={stripStyle}>
        {slots.map((slot) => (
          <div key={slot.tick} style={{ ...cellStyle, ...(slot.parked ? cellParkedStyle : {}) }}>
            <div style={markStyle}>{slot.serverEvent ? '●' : ''}</div>
            <div
              style={{
                ...markStyle,
                color: slot.delivery ? 'var(--good)' : slot.empty ? 'var(--bad)' : 'var(--accent)',
              }}
            >
              {slot.delivery ? '▼' : slot.empty ? '×' : slot.request ? '↑' : ''}
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
        <span style={{ opacity: 0.7 }}>▬ {tl(LABELS.legendParked, lang)}</span>
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
          <span style={cellTextStyle}>{tl(LABELS.colStyle, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colRequests, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colEmpty, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colDelivered, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colAvg, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colMax, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colWorker, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colParked, lang)}</span>
        </div>
        {rows.map((row) => (
          <div key={row.style} style={tableRowStyle}>
            <span style={{ ...cellTextStyle, fontWeight: 700 }}>{row.style}</span>
            <span style={cellTextStyle}>{row.requests}</span>
            <span style={{ ...cellTextStyle, color: row.empty > 0 ? 'var(--bad)' : undefined }}>
              {row.empty}
            </span>
            <span style={cellTextStyle}>{row.delivered}</span>
            <span
              style={{
                ...cellTextStyle,
                color: row.avgLatency === 0 ? 'var(--good)' : 'var(--accent)',
              }}
            >
              {row.avgLatency}
            </span>
            <span style={cellTextStyle}>{row.maxLatency}</span>
            <span style={cellTextStyle}>{row.workerTicks}</span>
            <span style={cellTextStyle}>{row.parkedPeak}</span>
          </div>
        ))}
      </div>
      <div style={tableFootStyle}>
        {rows.map((row) => (
          <div key={`note-${row.style}`}>{`${row.style} — ${tl(LABELS.styles[row.style], lang)}`}</div>
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
const headerStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' };
const badgeStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 12,
  fontWeight: 700,
  padding: '2px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const headNameStyle: CSSProperties = { fontSize: 13, fontWeight: 600 };
const pillStyle: CSSProperties = {
  fontSize: 11,
  opacity: 0.75,
  border: '1px solid var(--border)',
  borderRadius: 10,
  padding: '1px 7px',
};
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
const cellParkedStyle: CSSProperties = {
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
const sidesStyle: CSSProperties = {
  display: 'flex',
  gap: 10,
  alignItems: 'stretch',
  flexWrap: 'wrap',
};
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
const logStyle: CSSProperties = { fontSize: 12 };
const logMissedStyle: CSSProperties = { fontSize: 12, opacity: 0.45, textDecoration: 'line-through' };
const logMetaStyle: CSSProperties = { fontSize: 10, opacity: 0.6, fontFamily: 'monospace' };
const tableStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 2 };
const tableHeadStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1.6fr repeat(7, 0.8fr)',
  fontSize: 10,
  opacity: 0.6,
};
const tableRowStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1.6fr repeat(7, 0.8fr)',
  fontSize: 11,
  fontFamily: 'monospace',
  padding: '3px 0',
  borderTop: '1px solid var(--border)',
  alignItems: 'center',
};
const cellTextStyle: CSSProperties = { paddingRight: 6, overflowWrap: 'anywhere' };
const tableFootStyle: CSSProperties = { fontSize: 10, opacity: 0.6, display: 'flex', flexDirection: 'column' };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 2 };
const mutedStyle: CSSProperties = { fontSize: 12, opacity: 0.5, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
