import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  strategy: { en: 'strategy', ru: 'стратегия' },
  stratCall: { en: 'call the owner at decision time', ru: 'вызов владельца в момент решения' },
  stratCache: { en: 'local cache with TTL', ru: 'локальный кэш с TTL' },
  stratEvents: { en: 'event-carried state transfer', ru: 'event-carried state transfer' },
  clock: { en: 'clock', ru: 'часы' },
  ttl: { en: 'TTL', ru: 'TTL' },
  limit: { en: 'freshness limit', ru: 'предел свежести' },
  noLimit: { en: 'none declared', ru: 'не объявлен' },
  failOpen: { en: 'fail open', ru: 'fail open' },
  failClosed: { en: 'fail closed', ru: 'fail closed' },
  ownerUp: { en: 'rate-service up', ru: 'rate-service доступен' },
  ownerDown: { en: 'rate-service DOWN', ru: 'rate-service НЕДОСТУПЕН' },
  feedOn: { en: 'feed flowing', ru: 'поток идёт' },
  feedOff: { en: 'feed STOPPED', ru: 'поток ОСТАНОВЛЕН' },
  owner: { en: 'rate-service (owns the data)', ru: 'rate-service (владеет данными)' },
  feed: { en: 'published events', ru: 'опубликованные события' },
  replica: { en: 'local copy (what the decision reads)', ru: 'локальная копия (её и читает решение)' },
  quotes: { en: 'quotes, each pinned to its rate', ru: 'расчёты, у каждого зафиксирован свой курс' },
  noDecision: { en: 'no decision in progress', ru: 'решений в обработке нет' },
  version: { en: 'v', ru: 'v' },
  asOf: { en: 'as of t=', ru: 'на t=' },
  age: { en: 'age', ru: 'возраст' },
  seconds: { en: 's', ru: 'с' },
  quoted: { en: 'quoted', ru: 'рассчитано' },
  staleQuoted: { en: 'on stale data', ru: 'по устаревшим' },
  refused: { en: 'refused', ru: 'отклонено' },
  remoteCalls: { en: 'calls inside a decision', ru: 'вызовов внутри решения' },
  oldest: { en: 'oldest rate used', ru: 'самый старый курс' },
  statusApplied: { en: 'applied', ru: 'применено' },
  statusIgnored: { en: 'ignored', ru: 'отброшено' },
  statusPending: { en: 'not delivered', ru: 'не доставлено' },
  stateFresh: { en: 'fresh', ru: 'свежо' },
  stateStale: { en: 'stale', ru: 'устарело' },
  stateExpired: { en: 'expired', ru: 'просрочено' },
  srcCall: { en: 'from rate-service', ru: 'из rate-service' },
  srcCache: { en: 'from the cache', ru: 'из кэша' },
  srcReplica: { en: 'from the replica', ru: 'из реплики' },
  outPending: { en: 'deciding…', ru: 'принимается решение…' },
  outQuoted: { en: 'quoted ✓', ru: 'рассчитано ✓' },
  outStale: { en: 'quoted on stale data ⚠', ru: 'рассчитано по устаревшим ⚠' },
  outBlocked: { en: 'refused', ru: 'отклонено' },
  whyCold: { en: 'nothing local yet', ru: 'локально ещё ничего нет' },
  whyUnreachable: { en: 'owner unreachable', ru: 'владелец недоступен' },
  whyTooStale: { en: 'past the freshness limit', ru: 'за пределом свежести' },
  whyUnknownPair: { en: 'no such pair', ru: 'такой пары нет' },
  runHint: {
    en: 'Run the code to see how a synchronous decision gets asynchronously delivered data.',
    ru: 'Запустите код, чтобы увидеть, как синхронное решение получает асинхронно доставленные данные.',
  },
};

type Strategy = 'sync-call' | 'ttl-cache' | 'event-carried';
type Outcome = 'pending' | 'quoted' | 'stale' | 'blocked';
type Reason = '' | 'cold' | 'unreachable' | 'too-stale' | 'unknown-pair';

interface OwnerRate {
  pair: string;
  rateText: string;
  version: number;
  publishedAt: number;
}
interface FeedEvent {
  pair: string;
  version: number;
  rateText: string;
  publishedAt: number;
  status: 'applied' | 'ignored' | 'pending';
}
interface LocalCopy {
  pair: string;
  rateText: string;
  version: number;
  asOf: number;
  ageSeconds: number;
  state: 'fresh' | 'stale' | 'expired';
}
interface Decision {
  pair: string;
  amountText: string;
  source: 'none' | 'sync-call' | 'cache' | 'replica';
  rateText: string;
  version: number;
  ageSeconds: number;
  totalText: string;
  outcome: Outcome;
  reason: Reason;
}
interface PinnedQuote {
  id: number;
  pair: string;
  amountText: string;
  rateText: string;
  version: number;
  asOf: number;
  totalText: string;
  stale: boolean;
}
interface FeedState {
  strategy: Strategy;
  clock: number;
  ttlSeconds: number;
  budgetSeconds: number;
  policy: 'serve' | 'refuse';
  rateService: { up: boolean; delivering: boolean; rates: OwnerRate[] };
  feed: FeedEvent[];
  replica: LocalCopy[];
  decision?: Decision;
  quotes: PinnedQuote[];
  counters: {
    quoted: number;
    staleQuoted: number;
    refused: number;
    remoteCalls: number;
    cacheHits: number;
    eventsApplied: number;
    eventsIgnored: number;
    oldestRateUsed: number;
  };
}

export default function RateFeedVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as FeedState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const s = tl(LABELS.seconds, lang);

  const ownerBoxes: Box[] = state.rateService.rates.map((rate) => ({
    id: `owner-${rate.pair}`,
    title: `${rate.pair} ${rate.rateText}`,
    subtitle: `${tl(LABELS.version, lang)}${rate.version} · ${tl(LABELS.asOf, lang)}${rate.publishedAt}${s}`,
    highlighted: highlight.has('service') || highlight.has(`pair:${rate.pair}`),
    dim: !state.rateService.up,
  }));

  const feedBoxes: Box[] = state.feed.map((ev, i) => ({
    id: `feed-${i}-${ev.pair}-${ev.version}`,
    title: `${tl(LABELS.version, lang)}${ev.version} ${ev.rateText}`,
    subtitle: `${tl(statusLabel(ev.status), lang)} · ${tl(LABELS.asOf, lang)}${ev.publishedAt}${s}`,
    highlighted: highlight.has(`event:${ev.pair}:${ev.version}`),
    dim: ev.status !== 'applied',
  }));

  const replicaBoxes: Box[] = state.replica.map((copy) => ({
    id: `replica-${copy.pair}`,
    title: `${copy.pair} ${copy.rateText}`,
    subtitle:
      `${tl(LABELS.version, lang)}${copy.version} · ${tl(LABELS.age, lang)} ${copy.ageSeconds}${s}` +
      ` · ${tl(stateLabel(copy.state), lang)}`,
    highlighted: highlight.has(`replica:${copy.pair}`),
    dim: copy.state !== 'fresh',
  }));

  const quoteBoxes: Box[] = state.quotes.map((quote) => ({
    id: `quote-${quote.id}`,
    title: `#${quote.id} ${quote.totalText}${quote.stale ? ' ⚠' : ''}`,
    subtitle: `${quote.amountText} × ${quote.rateText} · ${tl(LABELS.version, lang)}${quote.version}`,
    highlighted: highlight.has(`quote:${quote.id}`),
  }));

  return (
    <div style={wrapStyle}>
      <div style={headerStyle}>
        <span style={pillStyle}>
          {tl(LABELS.strategy, lang)}: {tl(strategyLabel(state.strategy), lang)}
        </span>
        <span style={pillStyle}>
          {tl(LABELS.clock, lang)} t={state.clock}
          {s}
          {state.ttlSeconds > 0 ? ` · ${tl(LABELS.ttl, lang)} ${state.ttlSeconds}${s}` : ''}
        </span>
        <span style={state.budgetSeconds > 0 ? pillStyle : warnPillStyle}>
          {tl(LABELS.limit, lang)}:{' '}
          {state.budgetSeconds > 0
            ? `${state.budgetSeconds}${s} · ${tl(
                state.policy === 'refuse' ? LABELS.failClosed : LABELS.failOpen,
                lang,
              )}`
            : tl(LABELS.noLimit, lang)}
        </span>
        <span style={state.rateService.up ? pillStyle : warnPillStyle}>
          {tl(state.rateService.up ? LABELS.ownerUp : LABELS.ownerDown, lang)}
        </span>
        {state.strategy === 'event-carried' && (
          <span style={state.rateService.delivering ? pillStyle : warnPillStyle}>
            {tl(state.rateService.delivering ? LABELS.feedOn : LABELS.feedOff, lang)}
          </span>
        )}
      </div>

      <DecisionBanner decision={state.decision} lang={lang} />

      <div style={statsStyle}>
        <Stat label={tl(LABELS.quoted, lang)} value={state.counters.quoted} color="var(--good)" />
        <Stat
          label={tl(LABELS.staleQuoted, lang)}
          value={state.counters.staleQuoted}
          color={state.counters.staleQuoted > 0 ? 'var(--bad)' : undefined}
        />
        <Stat
          label={tl(LABELS.refused, lang)}
          value={state.counters.refused}
          color={state.counters.refused > 0 ? 'var(--accent-2)' : undefined}
        />
        <Stat label={tl(LABELS.remoteCalls, lang)} value={state.counters.remoteCalls} />
        <Stat
          label={tl(LABELS.oldest, lang)}
          value={`${state.counters.oldestRateUsed}${s}`}
          color={state.counters.oldestRateUsed > 0 ? 'var(--accent-2)' : undefined}
        />
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.owner, lang)}</div>
        <BoxGroup boxes={ownerBoxes} />
      </div>

      {feedBoxes.length > 0 && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.feed, lang)}</div>
          <BoxGroup boxes={feedBoxes} />
        </div>
      )}

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.replica, lang)}</div>
        <BoxGroup boxes={replicaBoxes} />
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.quotes, lang)}</div>
        <BoxGroup boxes={quoteBoxes} />
      </div>
    </div>
  );
}

function strategyLabel(strategy: Strategy) {
  if (strategy === 'sync-call') return LABELS.stratCall;
  if (strategy === 'ttl-cache') return LABELS.stratCache;
  return LABELS.stratEvents;
}

function statusLabel(status: FeedEvent['status']) {
  if (status === 'applied') return LABELS.statusApplied;
  if (status === 'ignored') return LABELS.statusIgnored;
  return LABELS.statusPending;
}

function stateLabel(state: LocalCopy['state']) {
  if (state === 'expired') return LABELS.stateExpired;
  if (state === 'stale') return LABELS.stateStale;
  return LABELS.stateFresh;
}

const OUTCOMES: Record<Outcome, { label: { en: string; ru: string }; color: string }> = {
  pending: { label: LABELS.outPending, color: 'var(--accent)' },
  quoted: { label: LABELS.outQuoted, color: 'var(--good)' },
  stale: { label: LABELS.outStale, color: 'var(--bad)' },
  blocked: { label: LABELS.outBlocked, color: 'var(--accent-2)' },
};

const REASONS: Record<Exclude<Reason, ''>, { en: string; ru: string }> = {
  cold: LABELS.whyCold,
  unreachable: LABELS.whyUnreachable,
  'too-stale': LABELS.whyTooStale,
  'unknown-pair': LABELS.whyUnknownPair,
};

const SOURCES: Record<Decision['source'], { en: string; ru: string } | null> = {
  none: null,
  'sync-call': LABELS.srcCall,
  cache: LABELS.srcCache,
  replica: LABELS.srcReplica,
};

function DecisionBanner({ decision, lang }: { decision: Decision | undefined; lang: Lang }) {
  if (!decision) {
    return <div style={{ ...bannerStyle, opacity: 0.5 }}>{tl(LABELS.noDecision, lang)}</div>;
  }
  const outcome = OUTCOMES[decision.outcome] ?? OUTCOMES.pending;
  const source = SOURCES[decision.source];
  const reason = decision.reason ? REASONS[decision.reason] : null;
  return (
    <div style={bannerStyle}>
      <span style={tagStyle}>price {decision.pair}</span>
      <span style={keyStyle}>
        {decision.amountText}
        {decision.rateText ? ` × ${decision.rateText}` : ''}
        {decision.totalText ? ` = ${decision.totalText}` : ''}
      </span>
      <span style={bodyStyle}>
        {source ? tl(source, lang) : ''}
        {source && decision.outcome !== 'blocked'
          ? `, ${tl(LABELS.age, lang)} ${decision.ageSeconds}${tl(LABELS.seconds, lang)}`
          : ''}
        {reason ? tl(reason, lang) : ''}
      </span>
      <span style={{ ...outcomeStyle, color: outcome.color }}>{tl(outcome.label, lang)}</span>
    </div>
  );
}

function Stat({
  label,
  value,
  color,
}: {
  label: string;
  value: number | string;
  color?: string;
}) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={{ ...statValueStyle, color: color ?? 'inherit' }}>{value}</div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const headerStyle: CSSProperties = { display: 'flex', gap: 8, flexWrap: 'wrap' };
const pillStyle: CSSProperties = {
  fontSize: 11,
  padding: '2px 8px',
  borderRadius: 999,
  border: '1px solid var(--border)',
  fontFamily: 'monospace',
};
const warnPillStyle: CSSProperties = {
  ...pillStyle,
  color: 'var(--bad)',
  borderColor: 'var(--bad)',
};
const bannerStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 10,
  flexWrap: 'wrap',
  padding: '8px 12px',
  borderRadius: 8,
  background: 'var(--viz-box)',
  border: '1px solid var(--viz-active)',
};
const tagStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const keyStyle: CSSProperties = { fontWeight: 700, fontSize: 15, fontFamily: 'monospace' };
const bodyStyle: CSSProperties = { fontSize: 12, opacity: 0.7 };
const outcomeStyle: CSSProperties = { marginLeft: 'auto', fontSize: 13, fontWeight: 600 };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
