import type { CSSProperties, ReactNode } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid } from '@app/primitives/ArrayGrid';
import { BoxGroup } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to see where the surplus goes and what each way of scaling buys.',
    ru: 'Запустите код, чтобы увидеть, куда девается излишек и что даёт каждый способ масштабирования.',
  },
  topologies: {
    SINGLE: { en: 'one server', ru: 'один сервер' },
    BALANCED: { en: 'replicas behind a balancer', ru: 'реплики за балансировщиком' },
  },
  strategies: {
    NONE: { en: 'no balancer', ru: 'без балансировщика' },
    ROUND_ROBIN: { en: 'round robin', ru: 'round robin' },
    LEAST_BUSY: { en: 'least busy', ru: 'наименее занятый' },
    STICKY: { en: 'sticky sessions', ru: 'sticky-сессии' },
  },
  tick: { en: 'tick', ru: 'тик' },
  capacity: { en: 'capacity', ru: 'мощность' },
  perTick: { en: '/tick', ru: '/тик' },
  timeout: { en: 'client gives up after', ru: 'клиент сдаётся через' },
  ticks: { en: 'tick(s)', ru: 'тик(ов)' },
  healthOn: { en: 'health checks', ru: 'health-check' },
  healthOff: { en: 'no health checks', ru: 'без health-check' },
  localState: { en: 'state in replica memory', ru: 'состояние в памяти реплики' },
  replicas: { en: 'replicas', ru: 'реплики' },
  serving: { en: 'serving', ru: 'в работе' },
  queue: { en: 'queue', ru: 'очередь' },
  done: { en: 'done', ru: 'готово' },
  refused: { en: 'refused', ru: 'отказов' },
  sessions: { en: 'sessions', ru: 'сессий' },
  dead: { en: 'dead', ru: 'мёртв' },
  stillRouted: { en: 'still receiving traffic', ru: 'трафик всё ещё идёт' },
  evicted: { en: 'out of rotation', ru: 'вне ротации' },
  slow: { en: 'degraded', ru: 'деградировал' },
  shared: { en: 'shared dependency', ru: 'общая зависимость' },
  usedThisTick: { en: 'used this tick', ru: 'использовано в тике' },
  traffic: { en: 'demand vs capacity, tick by tick', ru: 'спрос против мощности, по тикам' },
  legendArrived: { en: 'arrived', ru: 'пришло' },
  legendServed: { en: 'served', ru: 'обслужено' },
  legendRejected: { en: 'refused', ru: 'отказано' },
  legendFailed: { en: 'never reached a replica', ru: 'не дошло до реплики' },
  legendQueued: { en: 'waiting in a queue', ru: 'ждёт в очереди' },
  statArrived: { en: 'arrived', ru: 'пришло' },
  statServed: { en: 'served', ru: 'обслужено' },
  statRejected: { en: 'refused', ru: 'отказано' },
  statFailed: { en: 'failed', ru: 'провалено' },
  statTimedOut: { en: 'clients gave up', ru: 'клиентов сдалось' },
  statWasted: { en: 'finished for nobody', ru: 'доделано в никуда' },
  statSessionMiss: { en: 'wrong replica', ru: 'не та реплика' },
  statBlackholed: { en: 'sent to a dead node', ru: 'ушло на мёртвый узел' },
  statAvg: { en: 'average latency', ru: 'средняя задержка' },
  statMax: { en: 'worst latency', ru: 'худшая задержка' },
  statPeakQueue: { en: 'peak queue', ru: 'пик очереди' },
  comparison: { en: 'the same traffic, four topologies', ru: 'один трафик, четыре топологии' },
  colTopology: { en: 'topology', ru: 'топология' },
  colNodes: { en: 'nodes', ru: 'узлов' },
  colCapacity: { en: 'capacity', ru: 'мощность' },
  colArrived: { en: 'arrived', ru: 'пришло' },
  colServed: { en: 'served', ru: 'обслужено' },
  colRejected: { en: 'refused', ru: 'отказано' },
  colAvg: { en: 'avg latency', ru: 'ср. задержка' },
  colMax: { en: 'worst', ru: 'худшая' },
  colSurvives: { en: 'survives a node loss', ru: 'переживает потерю узла' },
  yes: { en: 'yes', ru: 'да' },
  no: { en: 'no', ru: 'нет' },
};

type TopologyCode = keyof typeof LABELS.topologies;
type StrategyCode = keyof typeof LABELS.strategies;
type ComparisonCode = 'SINGLE' | 'BIGGER_BOX' | 'THREE_NODES' | 'THREE_NODES_ONE_DB';

interface Bottleneck {
  name: string;
  capacity: number;
  used: number;
}
interface NodeRow {
  name: string;
  alive: boolean;
  inRotation: boolean;
  degraded: boolean;
  concurrency: number;
  serviceTicks: number;
  serving: number;
  queue: number;
  queueLimit: number;
  capacity: number;
  served: number;
  rejected: number;
  sessions: number;
}
interface Slot {
  tick: number;
  arrived: number;
  served: number;
  rejected: number;
  failed: number;
  queued: number;
  nodes: number;
}
interface Stats {
  arrived: number;
  served: number;
  rejected: number;
  failed: number;
  timedOut: number;
  wasted: number;
  sessionMisses: number;
  blackholed: number;
  avgLatency: number;
  maxLatency: number;
  peakQueue: number;
}
interface ComparisonRow {
  topology: ComparisonCode;
  nodes: number;
  capacityPerTick: number;
  arrived: number;
  served: number;
  rejected: number;
  avgLatency: number;
  maxLatency: number;
  survivesLoss: boolean;
}
interface ScalingState {
  service: string;
  topology: TopologyCode;
  strategy: StrategyCode;
  now: number;
  clientTimeout: number;
  healthChecks: number | null;
  localSessions: boolean;
  capacityPerTick: number;
  bottleneck: Bottleneck | null;
  nodes: NodeRow[];
  timeline: Slot[];
  stats: Stats;
  comparison?: ComparisonRow[];
}

export default function ScalingVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as ScalingState | undefined;
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

      <Pane
        title={tl(LABELS.replicas, lang)}
        highlighted={highlight.has('nodes') || highlight.has('balancer')}
      >
        <BoxGroup boxes={state.nodes.map((node) => nodeBox(node, highlight, lang))} />
      </Pane>

      {state.bottleneck && (
        <Pane title={tl(LABELS.shared, lang)} highlighted={highlight.has('bottleneck')}>
          <BoxGroup
            boxes={[
              {
                id: 'bottleneck',
                title: state.bottleneck.name,
                subtitle:
                  `${state.bottleneck.capacity}${tl(LABELS.perTick, lang)}` +
                  ` · ${tl(LABELS.usedThisTick, lang)} ${state.bottleneck.used}`,
                highlighted: state.bottleneck.used >= state.bottleneck.capacity,
              },
            ]}
          />
        </Pane>
      )}

      <Pane
        title={tl(LABELS.traffic, lang)}
        highlighted={highlight.has('traffic') || highlight.has('queue')}
      >
        <TrafficStrip slots={state.timeline} capacity={state.capacityPerTick} lang={lang} />
      </Pane>

      <div style={statsStyle}>
        <Stat label={tl(LABELS.statArrived, lang)} value={state.stats.arrived} />
        <Stat
          label={tl(LABELS.statServed, lang)}
          value={state.stats.served}
          color={state.stats.served > 0 ? 'var(--good)' : undefined}
        />
        <Stat
          label={tl(LABELS.statRejected, lang)}
          value={state.stats.rejected}
          color={state.stats.rejected > 0 ? 'var(--bad)' : undefined}
        />
        <Stat
          label={tl(LABELS.statFailed, lang)}
          value={state.stats.failed}
          color={state.stats.failed > 0 ? 'var(--bad)' : undefined}
        />
        <Stat
          label={tl(LABELS.statTimedOut, lang)}
          value={state.stats.timedOut}
          color={state.stats.timedOut > 0 ? 'var(--accent)' : undefined}
        />
        <Stat
          label={tl(LABELS.statWasted, lang)}
          value={state.stats.wasted}
          color={state.stats.wasted > 0 ? 'var(--bad)' : undefined}
        />
        {state.localSessions && (
          <Stat
            label={tl(LABELS.statSessionMiss, lang)}
            value={state.stats.sessionMisses}
            color={state.stats.sessionMisses > 0 ? 'var(--bad)' : undefined}
          />
        )}
        {state.stats.blackholed > 0 && (
          <Stat
            label={tl(LABELS.statBlackholed, lang)}
            value={state.stats.blackholed}
            color="var(--bad)"
          />
        )}
        <Stat label={tl(LABELS.statAvg, lang)} value={state.stats.avgLatency} />
        <Stat label={tl(LABELS.statMax, lang)} value={state.stats.maxLatency} />
        <Stat label={tl(LABELS.statPeakQueue, lang)} value={state.stats.peakQueue} />
      </div>
    </div>
  );
}

/** One tile per replica: what it is holding, and whether it should be there. */
function nodeBox(node: NodeRow, highlight: Set<string>, lang: Lang) {
  const detail = node.alive
    ? `${tl(LABELS.serving, lang)} ${node.serving}/${node.concurrency}` +
      ` · ${tl(LABELS.queue, lang)} ${node.queue}/${node.queueLimit}` +
      ` · ${node.capacity}${tl(LABELS.perTick, lang)}`
    : `${tl(LABELS.dead, lang)} · ${tl(node.inRotation ? LABELS.stillRouted : LABELS.evicted, lang)}`;
  const extra = [
    node.degraded ? tl(LABELS.slow, lang) : '',
    `${tl(LABELS.done, lang)} ${node.served}`,
    node.rejected > 0 ? `${tl(LABELS.refused, lang)} ${node.rejected}` : '',
    node.sessions > 0 ? `${tl(LABELS.sessions, lang)} ${node.sessions}` : '',
  ]
    .filter(Boolean)
    .join(' · ');
  return {
    id: node.name,
    title: node.name,
    subtitle: extra ? `${detail} · ${extra}` : detail,
    highlighted:
      highlight.has(`node:${node.name}`) ||
      (node.alive && node.queue > 0) ||
      (!node.alive && node.inRotation),
    dim: !node.alive,
  };
}

function Header({ state, lang }: { state: ScalingState; lang: Lang }) {
  return (
    <div style={headerStyle}>
      <span style={badgeStyle}>{state.service}</span>
      <span style={headNameStyle}>{tl(LABELS.topologies[state.topology], lang)}</span>
      <span style={pillStyle}>{tl(LABELS.strategies[state.strategy], lang)}</span>
      <span style={pillStyle}>{`${tl(LABELS.tick, lang)} ${state.now}`}</span>
      <span style={pillStyle}>
        {`${tl(LABELS.capacity, lang)} ${state.capacityPerTick}${tl(LABELS.perTick, lang)}`}
      </span>
      <span style={pillStyle}>
        {`${tl(LABELS.timeout, lang)} ${state.clientTimeout} ${tl(LABELS.ticks, lang)}`}
      </span>
      <span style={{ ...pillStyle, color: state.healthChecks ? 'var(--good)' : 'var(--bad)' }}>
        {state.healthChecks
          ? `${tl(LABELS.healthOn, lang)} ${state.healthChecks}${tl(LABELS.perTick, lang)}`
          : tl(LABELS.healthOff, lang)}
      </span>
      {state.localSessions && (
        <span style={{ ...pillStyle, color: 'var(--bad)' }}>{tl(LABELS.localState, lang)}</span>
      )}
    </div>
  );
}

/**
 * One column per tick. The bar above the line is what arrived, the bar below is
 * what came out — so a gap between them is the surplus the queue absorbed.
 */
function TrafficStrip({
  slots,
  capacity,
  lang,
}: {
  slots: Slot[];
  capacity: number;
  lang: Lang;
}) {
  const peak = Math.max(1, capacity, ...slots.map((slot) => slot.arrived));
  const height = 34;
  return (
    <div>
      <div style={stripStyle}>
        {slots.map((slot) => (
          <div key={slot.tick} style={columnStyle}>
            <div style={{ ...barAreaStyle, height }}>
              <Bar value={slot.arrived} peak={peak} height={height} color="var(--accent)" />
              <Bar value={slot.served} peak={peak} height={height} color="var(--good)" />
              <Bar
                value={slot.rejected + slot.failed}
                peak={peak}
                height={height}
                color="var(--bad)"
              />
            </div>
            <div style={queuedStyle}>{slot.queued > 0 ? slot.queued : ''}</div>
            <div style={tickLabelStyle}>{slot.tick}</div>
          </div>
        ))}
      </div>
      <div style={legendStyle}>
        <span style={{ color: 'var(--accent)' }}>■ {tl(LABELS.legendArrived, lang)}</span>
        <span style={{ color: 'var(--good)' }}>■ {tl(LABELS.legendServed, lang)}</span>
        <span style={{ color: 'var(--bad)' }}>
          ■ {tl(LABELS.legendRejected, lang)} / {tl(LABELS.legendFailed, lang)}
        </span>
        <span style={{ opacity: 0.7 }}>n — {tl(LABELS.legendQueued, lang)}</span>
      </div>
    </div>
  );
}

function Bar({
  value,
  peak,
  height,
  color,
}: {
  value: number;
  peak: number;
  height: number;
  color: string;
}) {
  return (
    <div
      style={{
        width: 5,
        height: Math.round((value / peak) * height),
        background: color,
        borderRadius: 1,
        opacity: value === 0 ? 0.15 : 1,
        minHeight: 1,
      }}
    />
  );
}

function ComparisonTable({ rows, lang }: { rows: ComparisonRow[]; lang: Lang }) {
  return (
    <div style={wrapStyle}>
      <div style={sectionLabelStyle}>{tl(LABELS.comparison, lang)}</div>
      <ArrayGrid
        cells={rows.map((row) => ({
          key: row.topology,
          label: `${row.nodes}×`,
          highlighted: row.rejected === 0,
          content: (
            <div style={rowContentStyle}>
              <span style={rowTitleStyle}>{row.topology}</span>
              <span style={rowMetaStyle}>
                {`${tl(LABELS.colCapacity, lang)} ${row.capacityPerTick}${tl(LABELS.perTick, lang)}`}
                {` · ${tl(LABELS.colServed, lang)} ${row.served}/${row.arrived}`}
                {` · ${tl(LABELS.colRejected, lang)} ${row.rejected}`}
                {` · ${tl(LABELS.colAvg, lang)} ${row.avgLatency}`}
                {` · ${tl(LABELS.colMax, lang)} ${row.maxLatency}`}
              </span>
              <span
                style={{
                  ...rowMetaStyle,
                  color: row.survivesLoss ? 'var(--good)' : 'var(--bad)',
                }}
              >
                {`${tl(LABELS.colSurvives, lang)}: ${tl(row.survivesLoss ? LABELS.yes : LABELS.no, lang)}`}
              </span>
            </div>
          ),
        }))}
      />
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
const pillStyle: CSSProperties = {
  fontSize: 11,
  opacity: 0.75,
  border: '1px solid var(--border)',
  borderRadius: 10,
  padding: '1px 7px',
};
const paneStyle: CSSProperties = {
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
const stripStyle: CSSProperties = { display: 'flex', gap: 2, flexWrap: 'wrap' };
const columnStyle: CSSProperties = {
  width: 22,
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  gap: 1,
};
const barAreaStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'flex-end',
  justifyContent: 'center',
  gap: 1,
};
const queuedStyle: CSSProperties = {
  fontSize: 9,
  opacity: 0.7,
  fontFamily: 'monospace',
  minHeight: 11,
};
const tickLabelStyle: CSSProperties = { fontSize: 9, opacity: 0.5, fontFamily: 'monospace' };
const legendStyle: CSSProperties = {
  display: 'flex',
  gap: 12,
  flexWrap: 'wrap',
  fontSize: 10,
  opacity: 0.75,
  marginTop: 4,
};
const rowContentStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 1 };
const rowTitleStyle: CSSProperties = { fontSize: 12, fontWeight: 700, fontFamily: 'monospace' };
const rowMetaStyle: CSSProperties = { fontSize: 10, opacity: 0.75, fontFamily: 'monospace' };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 2 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
