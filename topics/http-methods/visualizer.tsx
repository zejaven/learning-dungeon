import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  stored: { en: 'resources on the server', ru: 'ресурсы на сервере' },
  requestBody: { en: 'request body', ru: 'тело запроса' },
  gone: { en: 'removed by this request', ru: 'удалено этим запросом' },
  cached: { en: 'fresh in the client cache', ru: 'свежее в кэше клиента' },
  empty: { en: 'the collection is empty', ru: 'коллекция пуста' },
  noRequest: { en: 'no request sent yet', ru: 'запросов ещё не было' },
  requests: { en: 'requests', ru: 'запросов' },
  mutations: { en: 'state changes', ru: 'изменений состояния' },
  created: { en: 'created', ru: 'создано' },
  unsafeReads: { en: 'unsafe reads', ru: 'небезопасных чтений' },
  safe: { en: 'safe', ru: 'безопасный' },
  notSafe: { en: 'not safe', ru: 'небезопасный' },
  idempotent: { en: 'idempotent', ru: 'идемпотентный' },
  notIdempotent: { en: 'not idempotent', ru: 'неидемпотентный' },
  cacheable: { en: 'cacheable', ru: 'кэшируемый' },
  notCacheable: { en: 'not cacheable', ru: 'не кэшируемый' },
  fromCache: { en: 'from cache', ru: 'из кэша' },
  noBody: { en: 'no body', ru: 'без тела' },
  markCreated: { en: 'created', ru: 'создан' },
  markUpdated: { en: 'changed', ru: 'изменён' },
  markKept: { en: 'untouched', ru: 'не тронут' },
  pending: { en: 'processing…', ru: 'обработка…' },
  runHint: {
    en: 'Run the code to see what each HTTP method does to the server.',
    ru: 'Запустите код, чтобы увидеть, что каждый HTTP-метод делает с сервером.',
  },
};

type Mark = 'created' | 'updated' | 'kept';

interface Field {
  field: string;
  value: string;
}
interface Resource {
  path: string;
  fields: Field[];
  mark: Mark;
}
interface Request {
  method: string;
  path: string;
  safe: boolean;
  idempotent: boolean;
  cacheable: boolean;
  body: Field[];
}
interface Response {
  status: string;
  hasBody: boolean;
  fromCache: boolean;
  location: string | null;
  allow: string[];
}
interface Counters {
  requests: number;
  mutations: number;
  created: number;
  unsafeReads: number;
}
interface ServerState {
  base: string;
  resources: Resource[];
  gone: string[];
  cached: string[];
  request?: Request;
  response?: Response;
  counters: Counters;
}

export default function HttpMethodsVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as ServerState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  const resourceBoxes: Box[] = state.resources.map((resource) => ({
    id: `res-${resource.path}`,
    title: resource.path,
    subtitle: `${resource.fields.map((f) => `${f.field}=${f.value}`).join(', ')} · ${tl(
      markLabel(resource.mark),
      lang,
    )}`,
    highlighted: highlight.has(`res:${resource.path}`) && resource.mark !== 'kept',
  }));

  const goneBoxes: Box[] = state.gone.map((path) => ({ id: `gone-${path}`, title: path, dim: true }));

  const bodyBoxes: Box[] = (state.request?.body ?? []).map((field) => ({
    id: `body-${field.field}`,
    title: `${field.field}: ${field.value}`,
  }));

  return (
    <div style={wrapStyle}>
      <div style={headerStyle}>
        <span style={pillStyle}>{state.base}</span>
        <span style={pillStyle}>
          {state.resources.length} {tl(LABELS.stored, lang)}
        </span>
        {state.cached.length > 0 && (
          <span style={pillStyle}>
            {tl(LABELS.cached, lang)}: {state.cached.join(', ')}
          </span>
        )}
      </div>

      <RequestBanner request={state.request} response={state.response} lang={lang} />

      {bodyBoxes.length > 0 && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.requestBody, lang)}</div>
          <BoxGroup boxes={bodyBoxes} />
        </div>
      )}

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.stored, lang)}</div>
        {state.resources.length > 0 ? (
          <BoxGroup boxes={resourceBoxes} />
        ) : (
          <div style={absentStyle}>{tl(LABELS.empty, lang)}</div>
        )}
      </div>

      {goneBoxes.length > 0 && (
        <div>
          <div style={{ ...sectionLabelStyle, color: 'var(--bad)' }}>{tl(LABELS.gone, lang)}</div>
          <BoxGroup boxes={goneBoxes} />
        </div>
      )}

      <div style={statsStyle}>
        <Stat label={tl(LABELS.requests, lang)} value={state.counters.requests} />
        <Stat label={tl(LABELS.mutations, lang)} value={state.counters.mutations} />
        <Stat label={tl(LABELS.created, lang)} value={state.counters.created} />
        <Stat
          label={tl(LABELS.unsafeReads, lang)}
          value={state.counters.unsafeReads}
          color={state.counters.unsafeReads > 0 ? 'var(--bad)' : undefined}
        />
      </div>
    </div>
  );
}

function markLabel(mark: Mark) {
  if (mark === 'created') return LABELS.markCreated;
  if (mark === 'updated') return LABELS.markUpdated;
  return LABELS.markKept;
}

function statusColor(status: string) {
  if (status.startsWith('2')) return 'var(--good)';
  if (status === 'pending') return 'var(--accent)';
  return 'var(--bad)';
}

function RequestBanner({
  request,
  response,
  lang,
}: {
  request: Request | undefined;
  response: Response | undefined;
  lang: Lang;
}) {
  if (!request) {
    return <div style={{ ...bannerStyle, opacity: 0.5 }}>{tl(LABELS.noRequest, lang)}</div>;
  }
  const status = response?.status ?? 'pending';
  return (
    <div style={bannerStyle}>
      <div style={bannerRowStyle}>
        <span style={methodStyle}>{request.method}</span>
        <span style={pathStyle}>{request.path}</span>
        <span style={{ ...statusStyle, color: statusColor(status) }}>
          {status === 'pending' ? tl(LABELS.pending, lang) : status}
        </span>
      </div>
      <div style={bannerRowStyle}>
        <Flag on={request.safe} whenOn={LABELS.safe} whenOff={LABELS.notSafe} lang={lang} />
        <Flag
          on={request.idempotent}
          whenOn={LABELS.idempotent}
          whenOff={LABELS.notIdempotent}
          lang={lang}
        />
        <Flag
          on={request.cacheable}
          whenOn={LABELS.cacheable}
          whenOff={LABELS.notCacheable}
          lang={lang}
        />
        {response?.fromCache && <span style={badgeStyle}>{tl(LABELS.fromCache, lang)}</span>}
        {response && !response.hasBody && <span style={badgeStyle}>{tl(LABELS.noBody, lang)}</span>}
        {response?.location && <span style={badgeStyle}>Location: {response.location}</span>}
        {response && response.allow.length > 0 && (
          <span style={badgeStyle}>Allow: {response.allow.join(', ')}</span>
        )}
      </div>
    </div>
  );
}

function Flag({
  on,
  whenOn,
  whenOff,
  lang,
}: {
  on: boolean;
  whenOn: { en: string; ru: string };
  whenOff: { en: string; ru: string };
  lang: Lang;
}) {
  return (
    <span style={{ ...badgeStyle, color: on ? 'var(--good)' : 'var(--bad)' }}>
      {on ? '✓' : '✗'} {tl(on ? whenOn : whenOff, lang)}
    </span>
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
const headerStyle: CSSProperties = { display: 'flex', gap: 8, flexWrap: 'wrap' };
const pillStyle: CSSProperties = {
  fontSize: 11,
  padding: '2px 8px',
  borderRadius: 999,
  border: '1px solid var(--border)',
  fontFamily: 'monospace',
};
const bannerStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 6,
  padding: '8px 12px',
  borderRadius: 8,
  background: 'var(--viz-box)',
  border: '1px solid var(--viz-active)',
};
const bannerRowStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 10,
  flexWrap: 'wrap',
};
const methodStyle: CSSProperties = { fontWeight: 700, fontSize: 15, fontFamily: 'monospace' };
const pathStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13 };
const badgeStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const statusStyle: CSSProperties = { marginLeft: 'auto', fontSize: 13, fontWeight: 600 };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const absentStyle: CSSProperties = { fontSize: 12, opacity: 0.4, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
