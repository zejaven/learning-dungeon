import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  stored: { en: 'stored representation', ru: 'хранимое представление' },
  requestBody: { en: 'request body', ru: 'тело запроса' },
  removed: { en: 'no longer in the resource', ru: 'больше нет в ресурсе' },
  absent: { en: 'nothing stored at this URL', ru: 'по этому URL ничего не хранится' },
  noRequest: { en: 'no request sent yet', ru: 'запросов ещё не было' },
  requests: { en: 'requests', ru: 'запросов' },
  wiped: { en: 'wiped by an omitted field', ru: 'стёрто из-за пропуска поля' },
  fields: { en: 'fields', ru: 'полей' },
  kindFull: { en: 'full representation', ru: 'полное представление' },
  kindMerge: { en: 'merge patch', ru: 'merge patch' },
  kindJson: { en: 'json patch (operation)', ru: 'json patch (операция)' },
  statusAdded: { en: 'added', ru: 'добавлено' },
  statusChanged: { en: 'changed', ru: 'изменено' },
  statusSame: { en: 'rewritten, same value', ru: 'перезаписано тем же значением' },
  statusKept: { en: 'untouched', ru: 'не тронуто' },
  pending: { en: 'processing…', ru: 'обработка…' },
  runHint: {
    en: 'Run the code to see what PUT and PATCH leave behind.',
    ru: 'Запустите код, чтобы увидеть, что после себя оставляют PUT и PATCH.',
  },
};

type Status = 'added' | 'changed' | 'same' | 'kept';
type Kind = 'full' | 'merge-patch' | 'json-patch';

interface Field {
  field: string;
  value: string | null;
  status: Status;
}
interface Removed {
  field: string;
  value: string | null;
}
interface Request {
  method: 'PUT' | 'PATCH';
  kind: Kind;
  ifMatch: string | null;
  body: { field: string; value: string | null }[];
}
interface Response {
  status: string;
  applied: boolean;
}
interface ResourceState {
  path: string;
  exists: boolean;
  etag: string;
  document: Field[];
  removed: Removed[];
  request?: Request;
  response?: Response;
  requests: number;
  fieldsWiped: number;
}

export default function PutVsPatchVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as ResourceState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  const documentBoxes: Box[] = state.document.map((field) => ({
    id: `field-${field.field}`,
    title: `${field.field}: ${render(field.value)}`,
    subtitle: tl(statusLabel(field.status), lang),
    highlighted: highlight.has(`field:${field.field}`) && field.status !== 'kept',
  }));

  const removedBoxes: Box[] = state.removed.map((field) => ({
    id: `removed-${field.field}`,
    title: `${field.field}: ${render(field.value)}`,
    dim: true,
  }));

  const bodyBoxes: Box[] = (state.request?.body ?? []).map((field) => ({
    id: `body-${field.field}`,
    title: `${field.field}: ${render(field.value)}`,
    highlighted: field.value === null,
  }));

  return (
    <div style={wrapStyle}>
      <div style={headerStyle}>
        <span style={pillStyle}>{state.path}</span>
        <span style={pillStyle}>ETag: {state.etag === '' ? '—' : `"${state.etag}"`}</span>
        <span style={pillStyle}>
          {state.document.length} {tl(LABELS.fields, lang)}
        </span>
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
        {state.exists ? (
          <BoxGroup boxes={documentBoxes} />
        ) : (
          <div style={absentStyle}>{tl(LABELS.absent, lang)}</div>
        )}
      </div>

      {removedBoxes.length > 0 && (
        <div>
          <div style={{ ...sectionLabelStyle, color: 'var(--bad)' }}>{tl(LABELS.removed, lang)}</div>
          <BoxGroup boxes={removedBoxes} />
        </div>
      )}

      <div style={statsStyle}>
        <Stat label={tl(LABELS.requests, lang)} value={state.requests} />
        <Stat
          label={tl(LABELS.wiped, lang)}
          value={state.fieldsWiped}
          color={state.fieldsWiped > 0 ? 'var(--bad)' : undefined}
        />
      </div>
    </div>
  );
}

function render(value: string | null) {
  return value === null ? 'null' : value;
}

function statusLabel(status: Status) {
  if (status === 'added') return LABELS.statusAdded;
  if (status === 'changed') return LABELS.statusChanged;
  if (status === 'same') return LABELS.statusSame;
  return LABELS.statusKept;
}

function kindLabel(kind: Kind) {
  if (kind === 'merge-patch') return LABELS.kindMerge;
  if (kind === 'json-patch') return LABELS.kindJson;
  return LABELS.kindFull;
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
      <span style={methodStyle}>{request.method}</span>
      <span style={kindStyle}>{tl(kindLabel(request.kind), lang)}</span>
      {request.ifMatch && <span style={kindStyle}>If-Match: "{request.ifMatch}"</span>}
      <span style={{ ...statusStyle, color: statusColor(status) }}>
        {status === 'pending' ? tl(LABELS.pending, lang) : status}
      </span>
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
  alignItems: 'baseline',
  gap: 10,
  flexWrap: 'wrap',
  padding: '8px 12px',
  borderRadius: 8,
  background: 'var(--viz-box)',
  border: '1px solid var(--viz-active)',
};
const methodStyle: CSSProperties = { fontWeight: 700, fontSize: 15, fontFamily: 'monospace' };
const kindStyle: CSSProperties = {
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
