import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  page: { en: 'page', ru: 'страница' },
  api: { en: 'API', ru: 'API' },
  clock: { en: 'clock', ru: 'часы' },
  seconds: { en: 's', ru: 'с' },
  noCall: { en: 'no call made yet', ru: 'вызовов ещё не было' },
  cookies: { en: 'with cookies', ru: 'с куками' },
  simple: { en: 'simple request', ru: 'простой запрос' },
  preflighted: { en: 'preflighted request', ru: 'запрос с предварительной проверкой' },
  callHeaders: { en: 'headers the call sets', ru: 'заголовки, которые ставит вызов' },
  noHeaders: { en: 'no extra headers', ru: 'без дополнительных заголовков' },
  safelisted: { en: 'safelisted', ru: 'из безопасного списка' },
  triggers: { en: 'triggers the preflight', ru: 'вызывает предварительный запрос' },
  optionsRequest: { en: 'OPTIONS request from the browser', ru: 'запрос OPTIONS от браузера' },
  optionsAnswer: { en: 'answer from the API', ru: 'ответ от API' },
  noAnswerYet: { en: 'no answer yet', ru: 'ответа ещё нет' },
  noCorsHeaders: { en: 'no Access-Control-* headers at all', ru: 'ни одного заголовка Access-Control-*' },
  fromCache: { en: 'answered from the cache — nothing was sent', ru: 'ответ из кеша — ничего не отправлялось' },
  cacheTitle: { en: 'preflight cache', ru: 'кеш предварительных запросов' },
  cacheEmpty: { en: 'nothing remembered', ru: 'ничего не запомнено' },
  remaining: { en: 'left', ru: 'осталось' },
  sent: { en: 'the real request was sent', ru: 'настоящий запрос отправлен' },
  neverSent: { en: 'the real request was never sent', ru: 'настоящий запрос не отправлен' },
  inFlight: { en: 'deciding…', ru: 'браузер решает…' },
  statCalls: { en: 'calls', ru: 'вызовов' },
  statPreflights: { en: 'preflights sent', ru: 'предв. запросов' },
  statCacheHits: { en: 'from cache', ru: 'из кеша' },
  statDenied: { en: 'denied', ru: 'отклонено' },
  statReal: { en: 'reached the API', ru: 'дошло до API' },
  statRoundTrips: { en: 'round trips', ru: 'обращений по сети' },
  runHint: {
    en: 'Run the code to see which calls are asked about first, what the OPTIONS exchange contains, and what the browser remembers.',
    ru: 'Запустите код, чтобы увидеть, про какие вызовы браузер спрашивает заранее, что содержит обмен OPTIONS и что браузер запоминает.',
  },
  triggers_: {
    method: { en: 'the method is not simple', ru: 'метод не из простых' },
    header: { en: 'a header is not safelisted', ru: 'заголовка нет в безопасном списке' },
    'content-type': { en: 'the Content-Type value is not safelisted', ru: 'значения Content-Type нет в безопасном списке' },
    none: { en: 'nothing to ask about', ru: 'спрашивать не о чем' },
  },
  reasons: {
    'bad-status': {
      en: 'the preflight was not answered with 2xx',
      ru: 'на предварительный запрос ответили не 2xx',
    },
    'no-allow-origin': {
      en: 'no Access-Control-Allow-Origin in the answer',
      ru: 'в ответе нет Access-Control-Allow-Origin',
    },
    'origin-mismatch': {
      en: 'Access-Control-Allow-Origin names another origin',
      ru: 'в Access-Control-Allow-Origin указан другой origin',
    },
    'method-not-allowed': {
      en: 'method not in Access-Control-Allow-Methods',
      ru: 'метода нет в Access-Control-Allow-Methods',
    },
    'header-not-allowed': {
      en: 'header not in Access-Control-Allow-Headers',
      ru: 'заголовка нет в Access-Control-Allow-Headers',
    },
    'authorization-not-listed': {
      en: 'the * wildcard never covers Authorization',
      ru: 'подстановочный знак * не покрывает Authorization',
    },
    'credentials-wildcard': {
      en: 'cookies cannot be combined with the * origin',
      ru: 'куки несовместимы с origin, заданным как *',
    },
    'credentials-not-allowed': {
      en: 'no Access-Control-Allow-Credentials: true',
      ru: 'нет Access-Control-Allow-Credentials: true',
    },
    'wildcard-literal-with-credentials': {
      en: 'with cookies, * is matched literally',
      ru: 'при куках * сравнивается буквально',
    },
  },
};

type Reason = keyof typeof LABELS.reasons;
type Trigger = keyof typeof LABELS.triggers_;

interface Api {
  corsEnabled: boolean;
  authFilterFirst: boolean;
  allowOrigin: string | null;
  allowMethods: string[];
  allowHeaders: string[];
  allowCredentials: boolean;
  maxAge: number;
}
interface Header {
  name: string;
  value: string;
  safelisted: boolean;
}
interface Call {
  method: string;
  path: string;
  headers: Header[];
  credentials: boolean;
  needsPreflight: boolean;
  trigger: Trigger;
  triggerDetail: string;
}
interface Preflight {
  status: 'none' | 'cached' | 'sent' | 'approved' | 'denied';
  requestMethod: string;
  requestHeaders: string[];
  responseStatus: number;
  effectiveMaxAge: number;
  reason: Reason | null;
  detail: string;
}
interface Outcome {
  stage: 'idle' | 'classified' | 'settled';
  realRequestSent: boolean;
}
interface CacheEntry {
  path: string;
  method: string;
  headers: string[];
  credentials: boolean;
  expiresAt: number;
  remaining: number;
}
interface Stats {
  calls: number;
  preflightsSent: number;
  cacheHits: number;
  denied: number;
  realRequests: number;
  roundTrips: number;
}
interface PreflightState {
  pageOrigin: string;
  apiOrigin: string;
  clock: number;
  api: Api;
  call?: Call;
  preflight?: Preflight;
  outcome?: Outcome;
  cache: CacheEntry[];
  stats: Stats;
}

export default function PreflightVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as PreflightState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const { call, preflight } = state;

  return (
    <div style={wrapStyle}>
      <div style={originsStyle}>
        <Origin label={tl(LABELS.page, lang)} origin={state.pageOrigin} />
        <span style={arrowStyle}>→</span>
        <Origin
          label={tl(LABELS.api, lang)}
          origin={state.apiOrigin}
          highlighted={highlight.has('api')}
        />
        <span style={tagStyle}>
          {tl(LABELS.clock, lang)}: {state.clock} {tl(LABELS.seconds, lang)}
        </span>
      </div>

      <CallBanner state={state} lang={lang} />

      {call && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.callHeaders, lang)}</div>
          {call.headers.length === 0 ? (
            <div style={mutedStyle}>{tl(LABELS.noHeaders, lang)}</div>
          ) : (
            <BoxGroup boxes={headerBoxes(call.headers, lang)} />
          )}
        </div>
      )}

      {call?.needsPreflight && preflight && preflight.status !== 'none' && (
        <div style={exchangeStyle}>
          <div style={columnStyle}>
            <div style={sectionLabelStyle}>{tl(LABELS.optionsRequest, lang)}</div>
            {preflight.status === 'cached' ? (
              <div style={mutedStyle}>{tl(LABELS.fromCache, lang)}</div>
            ) : (
              <BoxGroup
                boxes={optionsRequestBoxes(state, preflight, highlight.has('preflight'))}
              />
            )}
          </div>
          <div style={columnStyle}>
            <div style={sectionLabelStyle}>{tl(LABELS.optionsAnswer, lang)}</div>
            {preflight.responseStatus === 0 ? (
              <div style={mutedStyle}>{tl(LABELS.noAnswerYet, lang)}</div>
            ) : (
              <BoxGroup boxes={optionsAnswerBoxes(state.api, preflight, lang)} />
            )}
          </div>
        </div>
      )}

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.cacheTitle, lang)}</div>
        {state.cache.length === 0 ? (
          <div style={mutedStyle}>{tl(LABELS.cacheEmpty, lang)}</div>
        ) : (
          <BoxGroup boxes={cacheBoxes(state.cache, lang, highlight.has('cache'))} />
        )}
      </div>

      <div style={statsStyle}>
        <Stat label={tl(LABELS.statCalls, lang)} value={state.stats.calls} />
        <Stat label={tl(LABELS.statPreflights, lang)} value={state.stats.preflightsSent} />
        <Stat
          label={tl(LABELS.statCacheHits, lang)}
          value={state.stats.cacheHits}
          color={state.stats.cacheHits > 0 ? 'var(--good)' : undefined}
        />
        <Stat
          label={tl(LABELS.statDenied, lang)}
          value={state.stats.denied}
          color={state.stats.denied > 0 ? 'var(--bad)' : undefined}
        />
        <Stat label={tl(LABELS.statReal, lang)} value={state.stats.realRequests} />
        <Stat label={tl(LABELS.statRoundTrips, lang)} value={state.stats.roundTrips} />
      </div>
    </div>
  );
}

function headerBoxes(headers: Header[], lang: Lang): Box[] {
  return headers.map((header) => ({
    id: `header-${header.name}`,
    title: `${header.name}: ${header.value}`,
    subtitle: tl(header.safelisted ? LABELS.safelisted : LABELS.triggers, lang),
    highlighted: !header.safelisted,
  }));
}

function optionsRequestBoxes(
  state: PreflightState,
  preflight: Preflight,
  highlighted: boolean,
): Box[] {
  const boxes: Box[] = [
    { id: 'acr-origin', title: `Origin: ${state.pageOrigin}`, highlighted },
    {
      id: 'acr-method',
      title: `Access-Control-Request-Method: ${preflight.requestMethod}`,
      highlighted,
    },
  ];
  if (preflight.requestHeaders.length > 0) {
    boxes.push({
      id: 'acr-headers',
      title: `Access-Control-Request-Headers: ${preflight.requestHeaders.join(', ')}`,
      highlighted,
    });
  }
  return boxes;
}

function optionsAnswerBoxes(api: Api, preflight: Preflight, lang: Lang): Box[] {
  const boxes: Box[] = [
    {
      id: 'status',
      title: String(preflight.responseStatus),
      highlighted: preflight.responseStatus >= 400,
    },
  ];
  if (api.allowOrigin === null || preflight.responseStatus >= 400) {
    boxes.push({
      id: 'no-headers',
      title: tl(LABELS.noCorsHeaders, lang),
      dim: true,
    });
    return boxes;
  }
  boxes.push({ id: 'allow-origin', title: `Access-Control-Allow-Origin: ${api.allowOrigin}` });
  if (api.allowMethods.length > 0) {
    boxes.push({
      id: 'allow-methods',
      title: `Access-Control-Allow-Methods: ${api.allowMethods.join(', ')}`,
    });
  }
  if (api.allowHeaders.length > 0) {
    boxes.push({
      id: 'allow-headers',
      title: `Access-Control-Allow-Headers: ${api.allowHeaders.join(', ')}`,
    });
  }
  if (api.allowCredentials) {
    boxes.push({ id: 'allow-credentials', title: 'Access-Control-Allow-Credentials: true' });
  }
  if (api.maxAge > 0) {
    boxes.push({
      id: 'max-age',
      title: `Access-Control-Max-Age: ${api.maxAge}`,
      subtitle:
        preflight.effectiveMaxAge > 0 && preflight.effectiveMaxAge !== api.maxAge
          ? `→ ${preflight.effectiveMaxAge} ${tl(LABELS.seconds, lang)}`
          : undefined,
    });
  }
  return boxes;
}

function cacheBoxes(entries: CacheEntry[], lang: Lang, highlighted: boolean): Box[] {
  return entries.map((entry) => ({
    id: `cache-${entry.method}-${entry.path}-${entry.headers.join('-')}`,
    title: `${entry.method} ${entry.path}`,
    subtitle: `${entry.headers.length > 0 ? entry.headers.join(', ') + ' · ' : ''}${
      entry.remaining
    } ${tl(LABELS.seconds, lang)} ${tl(LABELS.remaining, lang)}`,
    highlighted,
    dim: entry.remaining === 0,
  }));
}

function Origin({
  label,
  origin,
  highlighted,
}: {
  label: string;
  origin: string;
  highlighted?: boolean;
}) {
  return (
    <div style={{ ...originStyle, ...(highlighted ? originHighlightStyle : {}) }}>
      <div style={originLabelStyle}>{label}</div>
      <div style={originValueStyle}>{origin}</div>
    </div>
  );
}

function CallBanner({ state, lang }: { state: PreflightState; lang: Lang }) {
  const { call, preflight, outcome } = state;
  if (!call || !outcome) {
    return <div style={{ ...bannerStyle, opacity: 0.5 }}>{tl(LABELS.noCall, lang)}</div>;
  }

  const verdict = verdictOf(outcome);
  return (
    <div style={bannerStyle}>
      <span style={methodStyle}>{call.method}</span>
      <span style={pathStyle}>{call.path}</span>
      {call.credentials && <span style={chipStyle}>{tl(LABELS.cookies, lang)}</span>}
      <span style={chipStyle}>
        {tl(call.needsPreflight ? LABELS.preflighted : LABELS.simple, lang)}
      </span>
      {call.needsPreflight && (
        <span style={chipStyle}>
          {tl(LABELS.triggers_[call.trigger], lang)}
          {call.triggerDetail ? `: ${call.triggerDetail}` : ''}
        </span>
      )}
      <span style={{ ...verdictStyle, color: verdict.color }}>{tl(verdict.label, lang)}</span>
      {preflight?.reason && (
        <span style={reasonStyle}>
          {tl(LABELS.reasons[preflight.reason], lang)}
          {preflight.detail ? ` (${preflight.detail})` : ''}
        </span>
      )}
    </div>
  );
}

function verdictOf(outcome: Outcome) {
  if (outcome.stage !== 'settled') {
    return { label: LABELS.inFlight, color: 'var(--accent)' };
  }
  return outcome.realRequestSent
    ? { label: LABELS.sent, color: 'var(--good)' }
    : { label: LABELS.neverSent, color: 'var(--bad)' };
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
const originsStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 10,
  flexWrap: 'wrap',
};
const originStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '4px 10px',
  background: 'var(--viz-box)',
};
const originHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
};
const originLabelStyle: CSSProperties = { fontSize: 10, opacity: 0.6 };
const originValueStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 12, fontWeight: 600 };
const arrowStyle: CSSProperties = { opacity: 0.5, fontSize: 16 };
const tagStyle: CSSProperties = { fontSize: 11, opacity: 0.6, fontFamily: 'monospace' };
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
const methodStyle: CSSProperties = { fontWeight: 700, fontSize: 15, fontFamily: 'monospace' };
const pathStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13, opacity: 0.8 };
const chipStyle: CSSProperties = {
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const verdictStyle: CSSProperties = { marginLeft: 'auto', fontSize: 13, fontWeight: 600 };
const reasonStyle: CSSProperties = { width: '100%', fontSize: 11, opacity: 0.75 };
const exchangeStyle: CSSProperties = { display: 'flex', gap: 16, flexWrap: 'wrap' };
const columnStyle: CSSProperties = { flex: '1 1 260px', minWidth: 240 };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const mutedStyle: CSSProperties = { fontSize: 12, opacity: 0.5, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
