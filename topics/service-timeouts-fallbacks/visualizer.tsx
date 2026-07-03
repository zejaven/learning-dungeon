import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang, type Lang, type Localized } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize service waits.',
    ru: 'Запустите код, чтобы визуализировать ожидание сервисов.',
  },
  client: { en: 'client', ru: 'клиент' },
  elapsed: { en: 'elapsed', ru: 'прошло' },
  deadline: { en: 'deadline', ru: 'deadline' },
  saved: { en: 'saved', ru: 'сэкономлено' },
  strategy: { en: 'strategy', ru: 'стратегия' },
  response: { en: 'response', ru: 'ответ' },
  latency: { en: 'latency', ru: 'задержка' },
  timeout: { en: 'timeout', ru: 'timeout' },
  failures: { en: 'failures', ru: 'сбои' },
  circuit: { en: 'circuit', ru: 'circuit' },
  fallback: { en: 'fallback', ru: 'fallback' },
  result: { en: 'result', ru: 'результат' },
  noResponse: { en: 'no response yet', ru: 'ответа пока нет' },
};

const STATUS_LABELS: Record<string, Localized> = {
  READY: { en: 'ready', ru: 'готов' },
  WAITING: { en: 'waiting', ru: 'ожидание' },
  RUNNING: { en: 'running', ru: 'работает' },
  RESPONDED: { en: 'responded', ru: 'ответил' },
  DEGRADED: { en: 'degraded', ru: 'деградация' },
  IDLE: { en: 'idle', ru: 'ожидает' },
  CALLING: { en: 'calling', ru: 'вызов' },
  OK: { en: 'ok', ru: 'ok' },
  TIMEOUT: { en: 'timeout', ru: 'timeout' },
  FALLBACK: { en: 'fallback', ru: 'fallback' },
  SKIPPED: { en: 'skipped', ru: 'пропущен' },
  NONE: { en: 'none', ru: 'нет' },
  PARTIAL: { en: 'partial', ru: 'частично' },
};

interface ServiceState {
  name: string;
  latencyMs: number;
  timeoutMs: number;
  available: boolean;
  status: string;
  result: string;
  failures: number;
  circuit: string;
  lastWaitMs: number;
  fallback: string;
}

interface ClientState {
  status: string;
  responseStatus: string;
  responseValue: string;
}

interface ServiceCallsState {
  name: string;
  elapsedMs: number;
  deadlineMs: number;
  savedMs: number;
  strategy: string;
  client: ClientState;
  services: ServiceState[];
}

export default function ServiceTimeoutsVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as ServiceCallsState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const cells: ArrayCell[] = state.services.map((service) => ({
    key: service.name,
    label: service.name,
    highlighted: highlight.has(`service:${service.name}`),
    content: <ServiceRow service={service} lang={lang} />,
  }));

  return (
    <div style={wrapStyle}>
      <ClientBanner state={state} highlighted={highlight.has('client')} lang={lang} />
      <div style={statsStyle}>
        <Stat label={tl(LABELS.elapsed, lang)} value={`${state.elapsedMs} ms`} />
        <Stat label={tl(LABELS.deadline, lang)} value={state.deadlineMs ? `${state.deadlineMs} ms` : '-'} />
        <Stat label={tl(LABELS.saved, lang)} value={`${state.savedMs} ms`} />
        <Stat label={tl(LABELS.strategy, lang)} value={state.strategy} />
      </div>
      <ArrayGrid cells={cells} />
    </div>
  );
}

function ClientBanner({
  state,
  highlighted,
  lang,
}: {
  state: ServiceCallsState;
  highlighted: boolean;
  lang: Lang;
}) {
  const response = state.client.responseValue || tl(LABELS.noResponse, lang);
  return (
    <div style={{ ...bannerStyle, ...(highlighted ? bannerHighlightStyle : {}) }}>
      <span style={tagStyle}>{tl(LABELS.client, lang)}</span>
      <span style={{ ...statusStyle, color: colorForStatus(state.client.status) }}>
        {labelFor(STATUS_LABELS, state.client.status, lang)}
      </span>
      <span style={responseStyle}>
        {tl(LABELS.response, lang)}: {labelFor(STATUS_LABELS, state.client.responseStatus, lang)}
      </span>
      <span style={valueStyle}>{response}</span>
    </div>
  );
}

function ServiceRow({ service, lang }: { service: ServiceState; lang: Lang }) {
  return (
    <div style={rowContentStyle}>
      <span style={{ ...statusStyle, color: colorForStatus(service.status) }}>
        {labelFor(STATUS_LABELS, service.status, lang)}
      </span>
      <span>{tl(LABELS.latency, lang)}: {service.latencyMs} ms</span>
      <span>{tl(LABELS.timeout, lang)}: {service.timeoutMs || '-'} ms</span>
      <span>{tl(LABELS.failures, lang)}: {service.failures}</span>
      <span>{tl(LABELS.circuit, lang)}: {service.circuit}</span>
      {service.fallback ? <span>{tl(LABELS.fallback, lang)}: {service.fallback}</span> : null}
      {service.result && !service.fallback ? <span>{tl(LABELS.result, lang)}: {service.result}</span> : null}
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={statValueStyle}>{value}</div>
    </div>
  );
}

function labelFor(labels: Record<string, Localized>, key: string, lang: Lang) {
  return tl(labels[key] ?? key, lang);
}

function colorForStatus(status: string) {
  if (status === 'OK' || status === 'RESPONDED' || status === 'RUNNING') {
    return 'var(--good)';
  }
  if (status === 'TIMEOUT' || status === 'SKIPPED') {
    return 'var(--bad)';
  }
  if (status === 'FALLBACK' || status === 'DEGRADED' || status === 'PARTIAL') {
    return 'var(--accent)';
  }
  return 'var(--text)';
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
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
const bannerHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.30)',
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
const responseStyle: CSSProperties = { fontSize: 12, opacity: 0.75 };
const valueStyle: CSSProperties = { fontSize: 12, fontFamily: 'monospace' };
const statsStyle: CSSProperties = { display: 'flex', gap: 16, flexWrap: 'wrap' };
const statStyle: CSSProperties = { minWidth: 90 };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 16, fontWeight: 700, fontFamily: 'monospace' };
const rowContentStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 10,
  flexWrap: 'wrap',
  fontSize: 12,
};
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
