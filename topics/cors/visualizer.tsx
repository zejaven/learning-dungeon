import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  page: { en: 'page', ru: 'страница' },
  api: { en: 'API', ru: 'API' },
  sameOrigin: { en: 'same origin', ru: 'тот же origin' },
  crossOrigin: { en: 'cross-origin', ru: 'разные origin' },
  policyTitle: { en: 'response headers from the API', ru: 'заголовки ответа от API' },
  noPolicy: { en: 'no CORS headers at all', ru: 'никаких заголовков CORS' },
  requestHeaders: { en: 'request headers', ru: 'заголовки запроса' },
  noHeaders: { en: 'no extra headers', ru: 'без дополнительных заголовков' },
  safelisted: { en: 'safelisted', ru: 'из безопасного списка' },
  triggersPreflight: { en: 'triggers a preflight', ru: 'вызывает предварительный запрос' },
  hidden: { en: 'response headers JavaScript may not read', ru: 'заголовки ответа, недоступные JavaScript' },
  noRequest: { en: 'no request made yet', ru: 'запросов ещё не было' },
  cookies: { en: 'with cookies', ru: 'с куками' },
  preflightNeeded: { en: 'preflight required', ru: 'нужен предварительный запрос' },
  preflightSimple: { en: 'simple request', ru: 'простой запрос' },
  preflightSent: { en: 'OPTIONS sent', ru: 'OPTIONS отправлен' },
  preflightAllowed: { en: 'OPTIONS allowed it', ru: 'OPTIONS разрешил' },
  preflightBlocked: { en: 'OPTIONS refused it', ru: 'OPTIONS отказал' },
  preflightCached: { en: 'OPTIONS reused from cache', ru: 'OPTIONS взят из кеша' },
  inFlight: { en: 'in flight…', ru: 'в пути…' },
  reachedServer: { en: 'reached the server', ru: 'дошло до сервера' },
  notDelivered: { en: 'never left the browser', ru: 'не покинуло браузер' },
  readable: { en: 'JavaScript can read the response', ru: 'JavaScript может прочитать ответ' },
  blocked: { en: 'blocked by the browser', ru: 'заблокировано браузером' },
  statRequests: { en: 'requests', ru: 'запросов' },
  statReached: { en: 'reached the server', ru: 'дошло до сервера' },
  statBlocked: { en: 'blocked', ru: 'заблокировано' },
  statPreflights: { en: 'preflights', ru: 'предв. запросов' },
  statSaved: { en: 'preflights saved', ru: 'сэкономлено' },
  runHint: {
    en: 'Run the code to see what the browser sends, what the server runs, and what JavaScript gets to read.',
    ru: 'Запустите код, чтобы увидеть, что отправляет браузер, что выполняет сервер и что достаётся JavaScript.',
  },
  reasons: {
    'no-allow-origin': {
      en: 'no Access-Control-Allow-Origin header',
      ru: 'нет заголовка Access-Control-Allow-Origin',
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
    'credentials-wildcard': {
      en: 'cookies cannot be combined with the * wildcard',
      ru: 'куки несовместимы с подстановочным знаком *',
    },
    'credentials-not-allowed': {
      en: 'no Access-Control-Allow-Credentials: true',
      ru: 'нет Access-Control-Allow-Credentials: true',
    },
  },
};

type Reason = keyof typeof LABELS.reasons;

interface Policy {
  allowOrigin: string | null;
  allowMethods: string[];
  allowHeaders: string[];
  exposeHeaders: string[];
  allowCredentials: boolean;
  maxAge: number;
}
interface Header {
  name: string;
  value: string;
  safelisted: boolean;
}
interface Request {
  method: string;
  path: string;
  headers: Header[];
  credentials: boolean;
  wantsHeaders: string[];
  needsPreflight: boolean;
}
interface Outcome {
  stage: 'idle' | 'issued' | 'delivered' | 'settled';
  preflight: 'sent' | 'allowed' | 'blocked' | 'cached' | null;
  delivered: boolean;
  readable: boolean;
  reason: Reason | null;
  detail: string;
}
interface Stats {
  requests: number;
  reachedServer: number;
  blockedByBrowser: number;
  preflights: number;
  preflightsSaved: number;
}
interface CorsState {
  pageOrigin: string;
  apiOrigin: string;
  crossOrigin: boolean;
  policy: Policy;
  request?: Request;
  outcome?: Outcome;
  hiddenHeaders: string[];
  stats: Stats;
}

export default function CorsVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as CorsState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  return (
    <div style={wrapStyle}>
      <div style={originsStyle}>
        <Origin label={tl(LABELS.page, lang)} origin={state.pageOrigin} />
        <span style={arrowStyle}>→</span>
        <Origin
          label={tl(LABELS.api, lang)}
          origin={state.apiOrigin}
          highlighted={highlight.has('server')}
        />
        <span style={{ ...tagStyle, color: state.crossOrigin ? 'var(--accent)' : 'var(--good)' }}>
          {tl(state.crossOrigin ? LABELS.crossOrigin : LABELS.sameOrigin, lang)}
        </span>
      </div>

      <RequestBanner state={state} lang={lang} />

      {state.request && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.requestHeaders, lang)}</div>
          {state.request.headers.length === 0 ? (
            <div style={mutedStyle}>{tl(LABELS.noHeaders, lang)}</div>
          ) : (
            <BoxGroup boxes={headerBoxes(state.request.headers, lang)} />
          )}
        </div>
      )}

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.policyTitle, lang)}</div>
        {policyBoxes(state.policy).length === 0 ? (
          <div style={{ ...mutedStyle, color: 'var(--bad)' }}>{tl(LABELS.noPolicy, lang)}</div>
        ) : (
          <BoxGroup boxes={policyBoxes(state.policy, highlight.has('policy'))} />
        )}
      </div>

      {state.hiddenHeaders.length > 0 && (
        <div>
          <div style={{ ...sectionLabelStyle, color: 'var(--bad)' }}>{tl(LABELS.hidden, lang)}</div>
          <BoxGroup
            boxes={state.hiddenHeaders.map((name) => ({
              id: `hidden-${name}`,
              title: name,
              subtitle: 'null',
              dim: true,
            }))}
          />
        </div>
      )}

      <div style={statsStyle}>
        <Stat label={tl(LABELS.statRequests, lang)} value={state.stats.requests} />
        <Stat label={tl(LABELS.statReached, lang)} value={state.stats.reachedServer} />
        <Stat
          label={tl(LABELS.statBlocked, lang)}
          value={state.stats.blockedByBrowser}
          color={state.stats.blockedByBrowser > 0 ? 'var(--bad)' : undefined}
        />
        <Stat label={tl(LABELS.statPreflights, lang)} value={state.stats.preflights} />
        <Stat
          label={tl(LABELS.statSaved, lang)}
          value={state.stats.preflightsSaved}
          color={state.stats.preflightsSaved > 0 ? 'var(--good)' : undefined}
        />
      </div>
    </div>
  );
}

function headerBoxes(headers: Header[], lang: Lang): Box[] {
  return headers.map((header) => ({
    id: `header-${header.name}`,
    title: `${header.name}: ${header.value}`,
    subtitle: tl(header.safelisted ? LABELS.safelisted : LABELS.triggersPreflight, lang),
    highlighted: !header.safelisted,
  }));
}

function policyBoxes(policy: Policy, highlighted = false): Box[] {
  const boxes: Box[] = [];
  if (policy.allowOrigin !== null) {
    boxes.push({
      id: 'allow-origin',
      title: `Access-Control-Allow-Origin: ${policy.allowOrigin}`,
      highlighted,
    });
  }
  if (policy.allowMethods.length > 0) {
    boxes.push({
      id: 'allow-methods',
      title: `Access-Control-Allow-Methods: ${policy.allowMethods.join(', ')}`,
    });
  }
  if (policy.allowHeaders.length > 0) {
    boxes.push({
      id: 'allow-headers',
      title: `Access-Control-Allow-Headers: ${policy.allowHeaders.join(', ')}`,
    });
  }
  if (policy.exposeHeaders.length > 0) {
    boxes.push({
      id: 'expose-headers',
      title: `Access-Control-Expose-Headers: ${policy.exposeHeaders.join(', ')}`,
    });
  }
  if (policy.allowCredentials) {
    boxes.push({ id: 'allow-credentials', title: 'Access-Control-Allow-Credentials: true' });
  }
  if (policy.maxAge > 0) {
    boxes.push({ id: 'max-age', title: `Access-Control-Max-Age: ${policy.maxAge}` });
  }
  return boxes;
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

function RequestBanner({ state, lang }: { state: CorsState; lang: Lang }) {
  const { request, outcome } = state;
  if (!request || !outcome) {
    return <div style={{ ...bannerStyle, opacity: 0.5 }}>{tl(LABELS.noRequest, lang)}</div>;
  }

  const verdict = verdictOf(outcome);
  return (
    <div style={bannerStyle}>
      <span style={methodStyle}>{request.method}</span>
      <span style={pathStyle}>{request.path}</span>
      {request.credentials && <span style={chipStyle}>{tl(LABELS.cookies, lang)}</span>}
      <span style={chipStyle}>{tl(preflightLabel(request, outcome), lang)}</span>
      <span style={{ ...verdictStyle, color: verdict.color }}>{tl(verdict.label, lang)}</span>
      {outcome.reason && (
        <span style={reasonStyle}>
          {tl(LABELS.reasons[outcome.reason], lang)}
          {outcome.detail ? ` (${outcome.detail})` : ''}
        </span>
      )}
    </div>
  );
}

function preflightLabel(request: Request, outcome: Outcome) {
  if (outcome.preflight === 'sent') return LABELS.preflightSent;
  if (outcome.preflight === 'allowed') return LABELS.preflightAllowed;
  if (outcome.preflight === 'blocked') return LABELS.preflightBlocked;
  if (outcome.preflight === 'cached') return LABELS.preflightCached;
  if (request.needsPreflight) return LABELS.preflightNeeded;
  return LABELS.preflightSimple;
}

function verdictOf(outcome: Outcome) {
  if (outcome.stage !== 'settled') {
    return { label: LABELS.inFlight, color: 'var(--accent)' };
  }
  if (outcome.readable) {
    return { label: LABELS.readable, color: 'var(--good)' };
  }
  if (outcome.delivered) {
    return { label: LABELS.blocked, color: 'var(--bad)' };
  }
  return { label: LABELS.notDelivered, color: 'var(--bad)' };
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
const tagStyle: CSSProperties = { fontSize: 11, fontWeight: 600 };
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
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const mutedStyle: CSSProperties = { fontSize: 12, opacity: 0.5, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
