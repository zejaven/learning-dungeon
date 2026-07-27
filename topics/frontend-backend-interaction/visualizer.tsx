import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  browser: { en: 'browser', ru: 'браузер' },
  server: { en: 'server', ru: 'сервер' },
  screen: { en: 'on screen', ru: 'на экране' },
  emptyScreen: { en: 'nothing rendered yet', ru: 'пока ничего не нарисовано' },
  token: { en: 'token held by the frontend', ru: 'токен, который держит фронтенд' },
  noToken: { en: 'no token', ru: 'токена нет' },
  socketOpen: { en: 'WebSocket open', ru: 'WebSocket открыт' },
  socketClosed: { en: 'no open connection', ru: 'открытого соединения нет' },
  routes: { en: 'routing table', ru: 'таблица маршрутов' },
  store: { en: 'rows stored on the server', ru: 'строки, хранимые на сервере' },
  sessions: { en: 'sessions stored on the server', ru: 'сессий хранится на сервере' },
  request: { en: 'request', ru: 'запрос' },
  response: { en: 'response', ru: 'ответ' },
  body: { en: 'body', ru: 'тело' },
  noBody: { en: 'no body', ru: 'без тела' },
  noHeaders: { en: 'no headers', ru: 'без заголовков' },
  noRequest: { en: 'no call made yet', ru: 'вызовов ещё не было' },
  networkDown: { en: 'the connection is down', ru: 'связи нет' },
  statusIdle: { en: 'idle', ru: 'бездействует' },
  statusLoading: { en: 'loading…', ru: 'загрузка…' },
  statusReady: { en: 'rendered', ru: 'отрисовано' },
  statusError: { en: 'error state', ru: 'состояние ошибки' },
  payload: { en: 'what crossed the wire', ru: 'что пересекло провод' },
  colField: { en: 'field', ru: 'поле' },
  colJava: { en: 'in Java', ru: 'в Java' },
  colJson: { en: 'in JSON', ru: 'в JSON' },
  colJs: { en: 'in JavaScript', ru: 'в JavaScript' },
  statRoundTrips: { en: 'round trips', ru: 'обменов' },
  statFailed: { en: 'no answer', ru: 'без ответа' },
  statCreated: { en: 'records created', ru: 'создано записей' },
  statDuplicates: { en: 'duplicates', ru: 'дублей' },
  statPolls: { en: 'empty polls', ru: 'пустых опросов' },
  statPushes: { en: 'pushed by server', ru: 'отправил сервер' },
  runHint: {
    en: 'Run the code to see what the frontend sends, what the server answers, and what each side ends up holding.',
    ru: 'Запустите код, чтобы увидеть, что отправляет фронтенд, что отвечает сервер и что в итоге остаётся у каждой стороны.',
  },
  failures: {
    network: {
      en: 'never delivered — no status code at all',
      ru: 'не доставлено — кода состояния нет вовсе',
    },
    'response-lost': {
      en: 'delivered and executed, answer lost on the way back',
      ru: 'доставлено и выполнено, ответ потерян на обратном пути',
    },
  },
  notes: {
    'no-date-type': { en: 'JSON has no date type', ru: 'в JSON нет типа даты' },
    precision: { en: 'exactness is not guaranteed', ru: 'точность не гарантируется' },
    'null-vs-missing': { en: 'null is not the same as missing', ru: 'null и отсутствие — разное' },
  },
};

type Failure = keyof typeof LABELS.failures;
type Note = keyof typeof LABELS.notes;

interface Header {
  name: string;
  value: string;
}
interface AppState {
  name: string;
  status: 'idle' | 'loading' | 'ready' | 'error';
  items: string[];
  token: string | null;
  error: 'network' | 'http' | null;
  errorDetail: string;
  socket: boolean;
  lastPush: string | null;
}
interface ApiState {
  name: string;
  sessions: number;
  routes: string[];
  matchedRoute: string | null;
  store: { id: string; label: string }[];
}
interface RequestState {
  method: string;
  path: string;
  headers: Header[];
  body: { field: string; value: string | null }[];
}
interface ResponseState {
  status: number;
  statusText: string;
  kind: 'ok' | 'created' | 'no-content' | 'bad-request' | 'unauthorized' | 'not-found' | 'server-error';
  headers: Header[];
  body: string;
}
interface PayloadField {
  field: string;
  java: string;
  json: string;
  js: string;
  note: Note | '';
}
interface Stats {
  roundTrips: number;
  failed: number;
  created: number;
  duplicates: number;
  polls: number;
  pushes: number;
}
interface InteractionState {
  app: AppState;
  api: ApiState;
  network: boolean;
  request?: RequestState;
  response?: ResponseState;
  failure: Failure | null;
  payload: PayloadField[];
  stats: Stats;
}

export default function InteractionVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as InteractionState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  return (
    <div style={wrapStyle}>
      <div style={sidesStyle}>
        <BrowserPane state={state} lang={lang} highlighted={highlight.has('app')} />
        <div style={wireStyle}>
          <Exchange state={state} lang={lang} highlight={highlight} />
        </div>
        <ServerPane state={state} lang={lang} highlighted={highlight.has('api')} />
      </div>

      {state.payload.length > 0 && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.payload, lang)}</div>
          <PayloadTable fields={state.payload} lang={lang} />
        </div>
      )}

      <div style={statsStyle}>
        <Stat label={tl(LABELS.statRoundTrips, lang)} value={state.stats.roundTrips} />
        <Stat
          label={tl(LABELS.statFailed, lang)}
          value={state.stats.failed}
          color={state.stats.failed > 0 ? 'var(--bad)' : undefined}
        />
        <Stat label={tl(LABELS.statCreated, lang)} value={state.stats.created} />
        <Stat
          label={tl(LABELS.statDuplicates, lang)}
          value={state.stats.duplicates}
          color={state.stats.duplicates > 0 ? 'var(--bad)' : undefined}
        />
        <Stat label={tl(LABELS.statPolls, lang)} value={state.stats.polls} />
        <Stat
          label={tl(LABELS.statPushes, lang)}
          value={state.stats.pushes}
          color={state.stats.pushes > 0 ? 'var(--good)' : undefined}
        />
      </div>
    </div>
  );
}

function BrowserPane({
  state,
  lang,
  highlighted,
}: {
  state: InteractionState;
  lang: Lang;
  highlighted: boolean;
}) {
  const app = state.app;
  return (
    <div style={{ ...paneStyle, ...(highlighted ? paneHighlightStyle : {}) }}>
      <div style={paneHeadStyle}>
        <span style={paneKindStyle}>{tl(LABELS.browser, lang)}</span>
        <span style={paneNameStyle}>{app.name}</span>
      </div>
      <div style={{ ...statusStyle, color: statusColor(app.status) }}>
        {tl(statusLabel(app.status), lang)}
        {app.error === 'http' && app.errorDetail ? ` · ${app.errorDetail}` : ''}
      </div>

      <div style={sectionLabelStyle}>{tl(LABELS.screen, lang)}</div>
      {app.items.length === 0 ? (
        <div style={mutedStyle}>{tl(LABELS.emptyScreen, lang)}</div>
      ) : (
        <BoxGroup boxes={app.items.map((item, i) => ({ id: `item-${i}`, title: item }))} />
      )}

      <div style={factStyle}>
        {app.token
          ? `${tl(LABELS.token, lang)}: ${app.token}`
          : tl(LABELS.noToken, lang)}
      </div>
      <div style={factStyle}>
        {tl(app.socket ? LABELS.socketOpen : LABELS.socketClosed, lang)}
        {app.lastPush ? ` · ${app.lastPush}` : ''}
      </div>
    </div>
  );
}

function ServerPane({
  state,
  lang,
  highlighted,
}: {
  state: InteractionState;
  lang: Lang;
  highlighted: boolean;
}) {
  const api = state.api;
  return (
    <div style={{ ...paneStyle, ...(highlighted ? paneHighlightStyle : {}) }}>
      <div style={paneHeadStyle}>
        <span style={paneKindStyle}>{tl(LABELS.server, lang)}</span>
        <span style={paneNameStyle}>{api.name}</span>
      </div>

      <div style={sectionLabelStyle}>{tl(LABELS.routes, lang)}</div>
      <div style={routesStyle}>
        {api.routes.map((route) => (
          <div
            key={route}
            style={{ ...routeStyle, ...(route === api.matchedRoute ? routeMatchedStyle : {}) }}
          >
            {route}
          </div>
        ))}
      </div>

      <div style={sectionLabelStyle}>{tl(LABELS.store, lang)}</div>
      <BoxGroup boxes={api.store.map((row) => ({ id: `row-${row.id}`, title: row.label }))} />

      <div style={factStyle}>{`${tl(LABELS.sessions, lang)}: ${api.sessions}`}</div>
    </div>
  );
}

function Exchange({
  state,
  lang,
  highlight,
}: {
  state: InteractionState;
  lang: Lang;
  highlight: Set<string>;
}) {
  if (!state.request) {
    return <div style={mutedStyle}>{tl(LABELS.noRequest, lang)}</div>;
  }
  const request = state.request;
  const response = state.response;

  return (
    <>
      <div style={{ ...messageStyle, ...(highlight.has('request') ? messageActiveStyle : {}) }}>
        <div style={messageHeadStyle}>
          <span style={arrowStyle}>▼</span>
          <span style={methodStyle}>{request.method}</span>
          <span style={pathStyle}>{request.path}</span>
        </div>
        {request.headers.length === 0 ? (
          <div style={mutedStyle}>{tl(LABELS.noHeaders, lang)}</div>
        ) : (
          <div style={headerListStyle}>
            {request.headers.map((header) => (
              <div key={header.name} style={headerLineStyle}>
                {header.name}: {header.value}
              </div>
            ))}
          </div>
        )}
        <div style={bodyStyle}>
          {request.body.length === 0
            ? tl(LABELS.noBody, lang)
            : `{ ${request.body
                .map((field) => `${field.field}: ${field.value ?? 'null'}`)
                .join(', ')} }`}
        </div>
      </div>

      {!state.network && <div style={brokenWireStyle}>{tl(LABELS.networkDown, lang)}</div>}

      {state.failure ? (
        <div style={failureStyle}>{tl(LABELS.failures[state.failure], lang)}</div>
      ) : (
        response && (
          <div style={{ ...messageStyle, ...(highlight.has('response') ? messageActiveStyle : {}) }}>
            <div style={messageHeadStyle}>
              <span style={arrowStyle}>▲</span>
              <span style={{ ...statusCodeStyle, color: kindColor(response.kind) }}>
                {response.status}
              </span>
              <span style={pathStyle}>{response.statusText}</span>
            </div>
            {response.headers.length > 0 && (
              <div style={headerListStyle}>
                {response.headers.map((header) => (
                  <div key={header.name} style={headerLineStyle}>
                    {header.name}: {header.value}
                  </div>
                ))}
              </div>
            )}
            <div style={bodyStyle}>{response.body || tl(LABELS.noBody, lang)}</div>
          </div>
        )
      )}
    </>
  );
}

function PayloadTable({ fields, lang }: { fields: PayloadField[]; lang: Lang }) {
  return (
    <div style={tableStyle}>
      <div style={tableHeadStyle}>
        <span style={cellStyle}>{tl(LABELS.colField, lang)}</span>
        <span style={cellStyle}>{tl(LABELS.colJava, lang)}</span>
        <span style={cellStyle}>{tl(LABELS.colJson, lang)}</span>
        <span style={cellStyle}>{tl(LABELS.colJs, lang)}</span>
        <span style={cellStyle} />
      </div>
      {fields.map((field) => (
        <div key={field.field} style={tableRowStyle}>
          <span style={cellStyle}>{field.field}</span>
          <span style={cellStyle}>{field.java}</span>
          <span style={cellStyle}>{field.json}</span>
          <span style={cellStyle}>{field.js}</span>
          <span style={{ ...cellStyle, color: 'var(--accent)' }}>
            {field.note ? tl(LABELS.notes[field.note], lang) : ''}
          </span>
        </div>
      ))}
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

function statusLabel(status: AppState['status']) {
  if (status === 'loading') return LABELS.statusLoading;
  if (status === 'ready') return LABELS.statusReady;
  if (status === 'error') return LABELS.statusError;
  return LABELS.statusIdle;
}

function statusColor(status: AppState['status']) {
  if (status === 'error') return 'var(--bad)';
  if (status === 'ready') return 'var(--good)';
  if (status === 'loading') return 'var(--accent)';
  return 'inherit';
}

function kindColor(kind: ResponseState['kind']) {
  if (kind === 'ok' || kind === 'created' || kind === 'no-content') return 'var(--good)';
  if (kind === 'server-error') return 'var(--bad)';
  return 'var(--accent)';
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const sidesStyle: CSSProperties = {
  display: 'flex',
  gap: 10,
  alignItems: 'stretch',
  flexWrap: 'wrap',
};
const paneStyle: CSSProperties = {
  flex: '1 1 220px',
  minWidth: 200,
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
const wireStyle: CSSProperties = {
  flex: '1 1 260px',
  minWidth: 230,
  display: 'flex',
  flexDirection: 'column',
  gap: 6,
  justifyContent: 'center',
};
const paneHeadStyle: CSSProperties = { display: 'flex', alignItems: 'baseline', gap: 6 };
const paneKindStyle: CSSProperties = { fontSize: 10, opacity: 0.6, textTransform: 'uppercase' };
const paneNameStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 12, fontWeight: 600 };
const statusStyle: CSSProperties = { fontSize: 12, fontWeight: 600 };
const factStyle: CSSProperties = { fontSize: 11, opacity: 0.75, fontFamily: 'monospace' };
const routesStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 2 };
const routeStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 11,
  opacity: 0.45,
  padding: '1px 4px',
  borderRadius: 3,
};
const routeMatchedStyle: CSSProperties = {
  opacity: 1,
  fontWeight: 700,
  background: 'var(--viz-badge)',
};
const messageStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '6px 8px',
  background: 'var(--viz-box)',
  display: 'flex',
  flexDirection: 'column',
  gap: 3,
};
const messageActiveStyle: CSSProperties = {
  borderColor: 'var(--viz-active)',
  background: 'var(--viz-highlight)',
};
const messageHeadStyle: CSSProperties = { display: 'flex', alignItems: 'baseline', gap: 6 };
const arrowStyle: CSSProperties = { opacity: 0.5, fontSize: 11 };
const methodStyle: CSSProperties = { fontWeight: 700, fontSize: 13, fontFamily: 'monospace' };
const statusCodeStyle: CSSProperties = { fontWeight: 700, fontSize: 13, fontFamily: 'monospace' };
const pathStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 11, opacity: 0.8 };
const headerListStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 1 };
const headerLineStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 10, opacity: 0.7 };
const bodyStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 10,
  opacity: 0.85,
  wordBreak: 'break-all',
};
const brokenWireStyle: CSSProperties = {
  fontSize: 11,
  fontWeight: 600,
  color: 'var(--bad)',
  textAlign: 'center',
};
const failureStyle: CSSProperties = {
  border: '1px dashed var(--bad)',
  borderRadius: 8,
  padding: '6px 8px',
  fontSize: 11,
  color: 'var(--bad)',
  textAlign: 'center',
};
const tableStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 2 };
const tableHeadStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(5, 1fr)',
  fontSize: 10,
  opacity: 0.6,
};
const tableRowStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(5, 1fr)',
  fontSize: 11,
  fontFamily: 'monospace',
  padding: '2px 0',
  borderTop: '1px solid var(--border)',
};
const cellStyle: CSSProperties = { paddingRight: 6, overflowWrap: 'anywhere' };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 2 };
const mutedStyle: CSSProperties = { fontSize: 12, opacity: 0.5, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
