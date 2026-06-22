import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize @TransactionalEventListener phases.',
    ru: 'Запустите код, чтобы увидеть фазы @TransactionalEventListener.',
  },
  phase: { en: 'phase', ru: 'фаза' },
  method: { en: 'method', ru: 'метод' },
  completion: { en: 'completion', ru: 'завершение' },
  transaction: { en: 'transaction / staged rows', ru: 'transaction / подготовленные строки' },
  events: { en: 'published events', ru: 'опубликованные events' },
  deliveries: { en: 'listener queue', ru: 'очередь listeners' },
  listeners: { en: 'listeners', ru: 'listeners' },
  database: { en: 'committed database', ru: 'зафиксированная база' },
  fallback: { en: 'fallback', ru: 'fallback' },
  invocations: { en: 'runs', ru: 'запусков' },
  noRows: { en: 'no rows', ru: 'нет строк' },
  noEvents: { en: 'no events', ru: 'нет events' },
  noDeliveries: { en: 'no queued listeners', ru: 'нет listeners в очереди' },
  noListeners: { en: 'no listeners', ru: 'нет listeners' },
  failure: { en: 'listener failure', ru: 'ошибка listener' },
  exception: { en: 'exception', ru: 'exception' },
  effect: { en: 'effect', ru: 'эффект' },
};

const PHASE_LABELS: Record<string, { en: string; ru: string }> = {
  idle: { en: 'idle', ru: 'ожидание' },
  active: { en: 'active transaction', ru: 'активная транзакция' },
  before_commit: { en: 'before commit', ru: 'перед commit' },
  committed: { en: 'committed', ru: 'commit выполнен' },
  rolled_back: { en: 'rolled back', ru: 'rollback выполнен' },
  after_completion: { en: 'after completion', ru: 'после завершения' },
  no_transaction: { en: 'no transaction', ru: 'нет транзакции' },
};

const STATUS_LABELS: Record<string, { en: string; ru: string }> = {
  queued: { en: 'queued', ru: 'в очереди' },
  waiting: { en: 'waiting', ru: 'ждёт' },
  done: { en: 'done', ru: 'выполнен' },
  skipped: { en: 'skipped', ru: 'пропущен' },
  failed: { en: 'failed', ru: 'ошибка' },
  committed: { en: 'committed', ru: 'commit' },
  rolled_back: { en: 'rolled back', ru: 'rollback' },
  no_transaction: { en: 'no transaction', ru: 'нет транзакции' },
};

interface Row {
  id: string;
  value: string;
}

interface DomainEvent {
  name: string;
  status: string;
}

interface Delivery {
  id: string;
  event: string;
  listener: string;
  phase: string;
  status: string;
  fallbackExecution: boolean;
}

interface Listener {
  name: string;
  phase: string;
  fallbackExecution: boolean;
  invocations: number;
  failed: boolean;
}

interface Failure {
  listener: string;
  phase: string;
  exception: string;
  effect: string;
}

interface TxEventState {
  name: string;
  phase: string;
  currentMethod?: string;
  completion: string;
  staged: Row[];
  database: Row[];
  publishedEvents: DomainEvent[];
  deliveries: Delivery[];
  listeners: Listener[];
  failure?: Failure;
}

export default function TransactionalEventListenerVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as TxEventState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const cells: ArrayCell[] = [
    {
      key: 'tx',
      label: tl(LABELS.transaction, lang),
      highlighted: highlight.has('tx'),
      content: <Rows rows={state.staged ?? []} empty={tl(LABELS.noRows, lang)} highlight={highlight} prefix="staged" />,
    },
    {
      key: 'events',
      label: tl(LABELS.events, lang),
      highlighted: false,
      content: <Events events={state.publishedEvents ?? []} highlight={highlight} lang={lang} />,
    },
    {
      key: 'deliveries',
      label: tl(LABELS.deliveries, lang),
      highlighted: false,
      content: <Deliveries deliveries={state.deliveries ?? []} highlight={highlight} lang={lang} />,
    },
    {
      key: 'listeners',
      label: tl(LABELS.listeners, lang),
      highlighted: false,
      content: <Listeners listeners={state.listeners ?? []} highlight={highlight} lang={lang} />,
    },
    {
      key: 'db',
      label: tl(LABELS.database, lang),
      highlighted: false,
      content: <Rows rows={state.database ?? []} empty={tl(LABELS.noRows, lang)} highlight={highlight} prefix="db" />,
    },
  ];

  return (
    <div style={wrapStyle}>
      <div style={topBarStyle}>
        <Info label={tl(LABELS.phase, lang)} value={phaseLabel(state.phase, lang)} tone={phaseTone(state.phase)} />
        {state.currentMethod ? <Info label={tl(LABELS.method, lang)} value={state.currentMethod} /> : null}
        <Info label={tl(LABELS.completion, lang)} value={state.completion} />
      </div>

      {state.failure ? <FailureBanner failure={state.failure} lang={lang} highlighted={highlight.has('failure')} /> : null}

      <ArrayGrid cells={cells} />
    </div>
  );
}

function Rows({
  rows,
  empty,
  highlight,
  prefix,
}: {
  rows: Row[];
  empty: string;
  highlight: Set<string>;
  prefix: 'staged' | 'db';
}) {
  if (rows.length === 0) {
    return <span style={emptyStyle}>{empty}</span>;
  }
  return (
    <div style={rowWrapStyle}>
      {rows.map((row) => (
        <Pill
          key={row.id}
          text={`${row.id}: ${row.value}`}
          tone={prefix === 'db' ? 'good' : 'accent'}
          highlighted={highlight.has(`${prefix}:${row.id}`)}
        />
      ))}
    </div>
  );
}

function Events({ events, highlight, lang }: { events: DomainEvent[]; highlight: Set<string>; lang: Lang }) {
  if (events.length === 0) {
    return <span style={emptyStyle}>{tl(LABELS.noEvents, lang)}</span>;
  }
  return (
    <div style={rowWrapStyle}>
      {events.map((ev) => (
        <Pill
          key={ev.name}
          text={ev.name}
          subtitle={statusLabel(ev.status, lang)}
          tone={ev.status === 'rolled_back' ? 'bad' : ev.status === 'committed' ? 'good' : 'accent'}
          highlighted={highlight.has(`event:${ev.name}`)}
        />
      ))}
    </div>
  );
}

function Deliveries({ deliveries, highlight, lang }: { deliveries: Delivery[]; highlight: Set<string>; lang: Lang }) {
  if (deliveries.length === 0) {
    return <span style={emptyStyle}>{tl(LABELS.noDeliveries, lang)}</span>;
  }
  return (
    <div style={rowWrapStyle}>
      {deliveries.map((delivery) => (
        <Pill
          key={delivery.id}
          text={`${delivery.listener} <- ${delivery.event}`}
          subtitle={`${delivery.phase} / ${statusLabel(delivery.status, lang)}`}
          tone={statusTone(delivery.status)}
          highlighted={highlight.has(`delivery:${delivery.id}`) || highlight.has(`listener:${delivery.listener}`)}
        />
      ))}
    </div>
  );
}

function Listeners({ listeners, highlight, lang }: { listeners: Listener[]; highlight: Set<string>; lang: Lang }) {
  if (listeners.length === 0) {
    return <span style={emptyStyle}>{tl(LABELS.noListeners, lang)}</span>;
  }
  return (
    <div style={rowWrapStyle}>
      {listeners.map((listener) => (
        <Pill
          key={listener.name}
          text={listener.name}
          subtitle={`${listener.phase}${listener.fallbackExecution ? ` / ${tl(LABELS.fallback, lang)}` : ''} / ${tl(LABELS.invocations, lang)}: ${listener.invocations}`}
          tone={listener.failed ? 'bad' : 'neutral'}
          highlighted={highlight.has(`listener:${listener.name}`) || highlight.has(`phase:${listener.phase}`)}
        />
      ))}
    </div>
  );
}

function FailureBanner({ failure, lang, highlighted }: { failure: Failure; lang: Lang; highlighted: boolean }) {
  return (
    <div style={{ ...bannerStyle, ...(highlighted ? bannerHighlightStyle : {}) }}>
      <span style={tagStyle}>{tl(LABELS.failure, lang)}</span>
      <strong>{failure.listener}</strong>
      <span>{failure.phase}</span>
      <span>{tl(LABELS.exception, lang)}: {failure.exception}</span>
      <span>{tl(LABELS.effect, lang)}: {failure.effect}</span>
    </div>
  );
}

function Info({ label, value, tone }: { label: string; value: string; tone?: Tone }) {
  return (
    <div style={infoStyle}>
      <div style={infoLabelStyle}>{label}</div>
      <div style={{ ...infoValueStyle, ...toneStyle(tone) }}>{value}</div>
    </div>
  );
}

type Tone = 'good' | 'bad' | 'accent' | 'neutral';

function Pill({
  text,
  subtitle,
  highlighted,
  tone = 'neutral',
}: {
  text: string;
  subtitle?: string;
  highlighted: boolean;
  tone?: Tone;
}) {
  return (
    <span style={{ ...pillStyle, ...toneStyle(tone), ...(highlighted ? pillHighlightStyle : {}) }}>
      <span style={pillTitleStyle}>{text}</span>
      {subtitle ? <span style={pillSubtitleStyle}>{subtitle}</span> : null}
    </span>
  );
}

function phaseLabel(phase: string, lang: Lang): string {
  return tl(PHASE_LABELS[phase] ?? { en: phase, ru: phase }, lang);
}

function statusLabel(status: string, lang: Lang): string {
  return tl(STATUS_LABELS[status] ?? { en: status, ru: status }, lang);
}

function phaseTone(phase: string): Tone {
  if (phase === 'committed' || phase === 'after_completion') return 'good';
  if (phase === 'rolled_back') return 'bad';
  if (phase === 'active' || phase === 'before_commit') return 'accent';
  return 'neutral';
}

function statusTone(status: string): Tone {
  if (status === 'done' || status === 'committed') return 'good';
  if (status === 'failed' || status === 'rolled_back') return 'bad';
  if (status === 'waiting' || status === 'queued') return 'accent';
  return 'neutral';
}

function toneStyle(tone?: Tone): CSSProperties {
  if (tone === 'good') return { color: 'var(--good)', borderColor: 'var(--good)' };
  if (tone === 'bad') return { color: 'var(--bad)', borderColor: 'var(--bad)' };
  if (tone === 'accent') return { color: 'var(--accent)', borderColor: 'var(--accent)' };
  return {};
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const topBarStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const infoStyle: CSSProperties = { minWidth: 92 };
const infoLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const infoValueStyle: CSSProperties = { fontSize: 15, fontWeight: 700, fontFamily: 'monospace' };
const rowWrapStyle: CSSProperties = { display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' };
const pillStyle: CSSProperties = {
  display: 'inline-flex',
  flexDirection: 'column',
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '4px 8px',
  fontSize: 12,
  fontFamily: 'monospace',
  background: 'var(--viz-box)',
  minWidth: 92,
};
const pillTitleStyle: CSSProperties = { fontWeight: 700 };
const pillSubtitleStyle: CSSProperties = { fontSize: 11, opacity: 0.72 };
const pillHighlightStyle: CSSProperties = {
  boxShadow: '0 0 0 2px rgba(255,204,102,0.28)',
  borderColor: 'var(--accent)',
};
const bannerStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 10,
  flexWrap: 'wrap',
  padding: '8px 12px',
  borderRadius: 8,
  background: 'var(--viz-box)',
  border: '1px solid var(--viz-active)',
  fontSize: 12,
};
const bannerHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.22)',
};
const tagStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const emptyStyle: CSSProperties = { fontSize: 12, opacity: 0.45, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
