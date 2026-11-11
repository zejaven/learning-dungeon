import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang, type Lang, type Localized } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to watch the retries.',
    ru: 'Запустите код, чтобы увидеть повторы.',
  },
  policy: { en: 'policy', ru: 'политика' },
  attempts: { en: 'attempts', ru: 'попытки' },
  backoff: { en: 'backoff', ru: 'backoff' },
  cap: { en: 'cap', ru: 'потолок' },
  jitter: { en: 'jitter', ru: 'jitter' },
  deadline: { en: 'deadline', ru: 'deadline' },
  budget: { en: 'retry budget', ru: 'retry budget' },
  clock: { en: 'time', ru: 'время' },
  serverCalls: { en: 'server calls', ru: 'вызовов сервера' },
  peak: { en: 'peak per window', ru: 'пик на окно' },
  idempotent: { en: 'idempotent', ru: 'идемпотентно' },
  notIdempotent: { en: 'not idempotent', ru: 'не идемпотентно' },
  key: { en: 'key', ru: 'key' },
  wait: { en: 'then wait', ru: 'затем пауза' },
  at: { en: 'at', ru: 'в' },
  none: { en: 'none', ru: 'нет' },
  retries: { en: 'retries', ru: 'повторяет' },
  passes: { en: 'passes through', ru: 'пропускает' },
  calls: { en: 'calls', ru: 'вызовов' },
  arrivals: { en: 'retries arriving per window', ru: 'повторов в окне' },
  noAttempts: { en: 'no call yet', ru: 'вызова пока нет' },
};

const STATUS: Record<string, Localized> = {
  IDLE: { en: 'idle', ru: 'ожидает' },
  CALLING: { en: 'calling', ru: 'вызов' },
  BACKING_OFF: { en: 'backing off', ru: 'пауза' },
  SUCCEEDED: { en: 'succeeded', ru: 'успех' },
  FAILED: { en: 'failed', ru: 'ошибка' },
  DONE: { en: 'done', ru: 'готово' },
};

const VERDICT: Record<string, Localized> = {
  PENDING: { en: 'in flight', ru: 'в полёте' },
  OK: { en: 'ok', ru: 'ok' },
  TRANSIENT: { en: 'transient · retry', ru: 'временная · повтор' },
  AMBIGUOUS: { en: 'ambiguous · only if idempotent', ru: 'неоднозначная · только если идемпотентно' },
  PERMANENT: { en: 'permanent · no retry', ru: 'постоянная · без повтора' },
};

interface Policy {
  maxAttempts: number;
  baseDelayMs: number;
  multiplier: string;
  maxDelayMs: number;
  jitter: string;
  attemptTimeoutMs: number;
  deadlineMs: number;
  budgetMax: number;
  budgetTokens: number;
}

interface Attempt {
  n: number;
  atMs: number;
  outcome: string;
  verdict: string;
  delayMs: number;
}

interface Slot {
  fromMs: number;
  count: number;
}

interface Layer {
  name: string;
  retries: boolean;
  calls: number;
}

interface RetryState {
  name: string;
  policy: Policy;
  operation: string;
  idempotent: boolean;
  idempotencyKey: string;
  status: string;
  clockMs: number;
  serverCalls: number;
  peak: number;
  attempts: Attempt[];
  slots: Slot[];
  layers: Layer[];
}

export default function RetryVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as RetryState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }
  const highlight = new Set(event?.highlight ?? []);

  return (
    <div style={wrapStyle}>
      <Header state={state} lang={lang} highlighted={highlight.has('client')} />
      <PolicyRow policy={state.policy} lang={lang} highlighted={highlight.has('policy')} />
      {state.layers.length > 0 ? (
        <Layers layers={state.layers} highlight={highlight} lang={lang} />
      ) : state.slots.length > 0 ? (
        <Histogram slots={state.slots} peak={state.peak} lang={lang} />
      ) : (
        <Attempts attempts={state.attempts} highlight={highlight} lang={lang} />
      )}
    </div>
  );
}

function Header({ state, lang, highlighted }: { state: RetryState; lang: Lang; highlighted: boolean }) {
  const showSafety = state.attempts.length > 0;
  return (
    <div style={{ ...bannerStyle, ...(highlighted ? bannerHighlightStyle : {}) }}>
      <span style={tagStyle}>{state.name}</span>
      <span style={{ ...statusStyle, color: colorFor(state.status) }}>
        {tl(STATUS[state.status] ?? state.status, lang)}
      </span>
      {state.operation ? <span style={monoStyle}>{state.operation}</span> : null}
      {showSafety ? (
        <span style={smallStyle}>
          {state.idempotent ? tl(LABELS.idempotent, lang) : tl(LABELS.notIdempotent, lang)}
          {state.idempotencyKey ? ` · ${tl(LABELS.key, lang)}: ${state.idempotencyKey}` : ''}
        </span>
      ) : null}
      <span style={smallStyle}>
        {tl(LABELS.clock, lang)}: {state.clockMs} ms · {tl(LABELS.serverCalls, lang)}: {state.serverCalls}
      </span>
    </div>
  );
}

function PolicyRow({ policy, lang, highlighted }: { policy: Policy; lang: Lang; highlighted: boolean }) {
  return (
    <div style={{ ...statsStyle, ...(highlighted ? policyHighlightStyle : {}) }}>
      <Stat label={tl(LABELS.attempts, lang)} value={String(policy.maxAttempts)} />
      <Stat label={tl(LABELS.backoff, lang)} value={`${policy.baseDelayMs} ms ×${policy.multiplier}`} />
      <Stat label={tl(LABELS.cap, lang)} value={`${policy.maxDelayMs} ms`} />
      <Stat label={tl(LABELS.jitter, lang)} value={policy.jitter} />
      <Stat
        label={tl(LABELS.deadline, lang)}
        value={policy.deadlineMs > 0 ? `${policy.deadlineMs} ms` : tl(LABELS.none, lang)}
      />
      <Stat
        label={tl(LABELS.budget, lang)}
        value={policy.budgetMax >= 0 ? `${policy.budgetTokens}/${policy.budgetMax}` : tl(LABELS.none, lang)}
      />
    </div>
  );
}

function Attempts({ attempts, highlight, lang }: { attempts: Attempt[]; highlight: Set<string>; lang: Lang }) {
  if (attempts.length === 0) {
    return <div style={hintStyle}>{tl(LABELS.noAttempts, lang)}</div>;
  }
  const cells: ArrayCell[] = attempts.map((a) => ({
    key: a.n,
    label: `#${a.n}`,
    highlighted: highlight.has(`attempt:${a.n}`),
    content: (
      <div style={rowStyle}>
        <span style={smallStyle}>
          {tl(LABELS.at, lang)} {a.atMs} ms
        </span>
        <span style={{ ...monoStyle, color: colorFor(a.verdict) }}>{a.outcome}</span>
        <span style={{ ...smallStyle, color: colorFor(a.verdict) }}>
          {tl(VERDICT[a.verdict] ?? a.verdict, lang)}
        </span>
        {a.delayMs > 0 ? (
          <span style={smallStyle}>
            → {tl(LABELS.wait, lang)} {a.delayMs} ms
          </span>
        ) : null}
      </div>
    ),
  }));
  return <ArrayGrid cells={cells} />;
}

function Histogram({ slots, peak, lang }: { slots: Slot[]; peak: number; lang: Lang }) {
  const max = Math.max(1, peak);
  const cells: ArrayCell[] = slots.map((s) => ({
    key: s.fromMs,
    label: `${s.fromMs} ms`,
    highlighted: s.count === peak && peak > 0,
    content: (
      <div style={rowStyle}>
        <div style={{ ...barStyle, width: `${(s.count / max) * 100}%` }} />
        <span style={monoStyle}>{s.count}</span>
      </div>
    ),
  }));
  return (
    <div style={columnStyle}>
      <div style={smallStyle}>
        {tl(LABELS.arrivals, lang)} · {tl(LABELS.peak, lang)}: {peak}
      </div>
      <ArrayGrid cells={cells} />
    </div>
  );
}

function Layers({ layers, highlight, lang }: { layers: Layer[]; highlight: Set<string>; lang: Lang }) {
  const max = Math.max(1, ...layers.map((l) => l.calls));
  const cells: ArrayCell[] = layers.map((l, i) => ({
    key: l.name,
    label: l.name,
    highlighted: highlight.has(`layer:${l.name}`),
    content: (
      <div style={rowStyle}>
        <div style={{ ...barStyle, width: `${Math.max(4, (l.calls / max) * 60)}%` }} />
        <span style={monoStyle}>
          {l.calls} {tl(LABELS.calls, lang)}
        </span>
        {i < layers.length - 1 ? (
          <span style={{ ...smallStyle, color: l.retries ? 'var(--bad)' : 'var(--text)' }}>
            {l.retries ? tl(LABELS.retries, lang) : tl(LABELS.passes, lang)}
          </span>
        ) : null}
      </div>
    ),
  }));
  return <ArrayGrid cells={cells} />;
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={statValueStyle}>{value}</div>
    </div>
  );
}

function colorFor(status: string) {
  if (status === 'OK' || status === 'SUCCEEDED') return 'var(--good)';
  if (status === 'FAILED' || status === 'PERMANENT') return 'var(--bad)';
  if (status === 'TRANSIENT' || status === 'AMBIGUOUS' || status === 'BACKING_OFF') return 'var(--accent-ink)';
  return 'var(--text)';
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const columnStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const bannerStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 10,
  padding: '8px 12px',
  borderRadius: 8,
  background: 'var(--viz-box)',
  border: '1px solid var(--viz-active)',
  flexWrap: 'wrap',
};
const bannerHighlightStyle: CSSProperties = { borderColor: 'var(--accent)' };
const policyHighlightStyle: CSSProperties = {
  outline: '1px solid var(--accent)',
  borderRadius: 6,
  padding: 4,
};
const tagStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const statusStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 12,
  fontWeight: 700,
  textTransform: 'uppercase',
};
const monoStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 12, fontWeight: 600 };
const smallStyle: CSSProperties = { fontSize: 12, opacity: 0.8 };
const statsStyle: CSSProperties = { display: 'flex', gap: 16, flexWrap: 'wrap' };
const statStyle: CSSProperties = { minWidth: 80 };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 15, fontWeight: 700, fontFamily: 'monospace' };
const rowStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 10,
  flexWrap: 'wrap',
  width: '100%',
};
const barStyle: CSSProperties = {
  height: 12,
  minWidth: 2,
  borderRadius: 3,
  background: 'var(--accent)',
};
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
