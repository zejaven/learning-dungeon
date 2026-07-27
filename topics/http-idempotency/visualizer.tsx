import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  stored: { en: 'resources on the server', ru: 'ресурсы на сервере' },
  requestBody: { en: 'request body', ru: 'тело запроса' },
  gone: { en: 'removed by this request', ru: 'удалено этим запросом' },
  keys: { en: 'idempotency keys the server remembers', ru: 'ключи идемпотентности, которые помнит сервер' },
  effects: { en: 'side effects fired', ru: 'сработавшие побочные эффекты' },
  ledger: { en: 'attempts', ru: 'попытки' },
  empty: { en: 'the collection is empty', ru: 'коллекция пуста' },
  noRequest: { en: 'no request sent yet', ru: 'запросов ещё не было' },
  attempts: { en: 'attempts', ru: 'попыток' },
  lost: { en: 'answers lost', ru: 'ответов потеряно' },
  effectsCount: { en: 'real effects', ru: 'реальных эффектов' },
  duplicates: { en: 'duplicates', ru: 'дублей' },
  replays: { en: 'replayed by key', ru: 'повторов по ключу' },
  idempotentYes: { en: 'idempotent by spec', ru: 'идемпотентен по спецификации' },
  idempotentNo: { en: 'not idempotent by spec', ru: 'неидемпотентен по спецификации' },
  idempotentMaybe: { en: 'not guaranteed by spec', ru: 'спецификация не гарантирует' },
  relative: { en: 'relative body', ru: 'относительное тело' },
  attemptN: { en: 'attempt', ru: 'попытка' },
  delivered: { en: 'answer received', ru: 'ответ получен' },
  answerLost: { en: 'answer lost', ru: 'ответ потерян' },
  replayed: { en: 'replayed from key', ru: 'повтор по ключу' },
  pending: { en: 'processing…', ru: 'обработка…' },
  markCreated: { en: 'created', ru: 'создан' },
  markUpdated: { en: 'changed', ru: 'изменён' },
  markKept: { en: 'untouched', ru: 'не тронут' },
  outcomeApplied: { en: 'applied', ru: 'применено' },
  outcomeRepeat: { en: 'no change', ru: 'без изменений' },
  outcomeDuplicate: { en: 'applied again', ru: 'применено снова' },
  outcomeLeak: { en: 'side effect again', ru: 'эффект снова' },
  outcomeReplayed: { en: 'replayed', ru: 'ответ повторён' },
  outcomeRead: { en: 'read', ru: 'чтение' },
  outcomeNone: { en: 'nothing', ru: 'ничего' },
  outcomePending: { en: 'in flight', ru: 'в полёте' },
  runHint: {
    en: 'Run the code to see what a repeated request does to the server.',
    ru: 'Запустите код, чтобы увидеть, что повторный запрос делает с сервером.',
  },
};

type Mark = 'created' | 'updated' | 'kept';
type Outcome =
  | 'applied'
  | 'repeat'
  | 'duplicate'
  | 'leak'
  | 'replayed'
  | 'read'
  | 'none'
  | 'pending';

interface Field {
  field: string;
  value: string;
}
interface Resource {
  path: string;
  fields: Field[];
  mark: Mark;
}
interface KeyRecord {
  key: string;
  path: string;
  status: string;
}
interface Request {
  method: string;
  path: string;
  attempt: number;
  key: string | null;
  idempotentBySpec: 'yes' | 'no' | 'unspecified';
  relative: boolean;
  body: Field[];
}
interface Response {
  status: string;
  delivered: boolean;
  replayed: boolean;
}
interface LedgerRow {
  seq: number;
  method: string;
  path: string;
  attempt: number;
  key: string | null;
  status: string;
  delivered: boolean;
  outcome: Outcome;
}
interface Counters {
  attempts: number;
  lost: number;
  effects: number;
  duplicates: number;
  replays: number;
}
interface IdempotencyState {
  base: string;
  resources: Resource[];
  gone: string[];
  keys: KeyRecord[];
  sideEffects: string[];
  request?: Request;
  response?: Response;
  ledger: LedgerRow[];
  counters: Counters;
}

export default function IdempotencyVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as IdempotencyState | undefined;
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

  const keyBoxes: Box[] = state.keys.map((record) => ({
    id: `key-${record.key}`,
    title: record.key,
    subtitle: `${record.status} · ${record.path}`,
    highlighted: highlight.has(`key:${record.key}`),
  }));

  const effectBoxes: Box[] = state.sideEffects.map((effect, index) => ({
    id: `effect-${index}`,
    title: `#${index + 1}`,
    subtitle: effect,
    highlighted: highlight.has('effects') && index === state.sideEffects.length - 1,
  }));

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

      {keyBoxes.length > 0 && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.keys, lang)}</div>
          <BoxGroup boxes={keyBoxes} />
        </div>
      )}

      {effectBoxes.length > 0 && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.effects, lang)}</div>
          <BoxGroup boxes={effectBoxes} />
        </div>
      )}

      {state.ledger.length > 0 && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.ledger, lang)}</div>
          <div style={ledgerStyle}>
            {state.ledger.map((row) => (
              <div key={row.seq} style={ledgerRowStyle}>
                <span style={seqStyle}>#{row.seq}</span>
                <span style={methodStyle}>{row.method}</span>
                <span style={pathStyle}>{row.path}</span>
                {row.attempt > 1 && (
                  <span style={badgeStyle}>
                    {tl(LABELS.attemptN, lang)} {row.attempt}
                  </span>
                )}
                {row.key && <span style={badgeStyle}>{row.key}</span>}
                <span style={{ ...statusStyle, color: statusColor(row.status) }}>{row.status}</span>
                {!row.delivered && (
                  <span style={{ ...badgeStyle, color: 'var(--bad)' }}>
                    {tl(LABELS.answerLost, lang)}
                  </span>
                )}
                <span style={{ ...badgeStyle, color: outcomeColor(row.outcome) }}>
                  {tl(outcomeLabel(row.outcome), lang)}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      <div style={statsStyle}>
        <Stat label={tl(LABELS.attempts, lang)} value={state.counters.attempts} />
        <Stat
          label={tl(LABELS.lost, lang)}
          value={state.counters.lost}
          color={state.counters.lost > 0 ? 'var(--accent)' : undefined}
        />
        <Stat label={tl(LABELS.effectsCount, lang)} value={state.counters.effects} />
        <Stat
          label={tl(LABELS.duplicates, lang)}
          value={state.counters.duplicates}
          color={state.counters.duplicates > 0 ? 'var(--bad)' : undefined}
        />
        <Stat
          label={tl(LABELS.replays, lang)}
          value={state.counters.replays}
          color={state.counters.replays > 0 ? 'var(--good)' : undefined}
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

function outcomeLabel(outcome: Outcome) {
  switch (outcome) {
    case 'applied':
      return LABELS.outcomeApplied;
    case 'repeat':
      return LABELS.outcomeRepeat;
    case 'duplicate':
      return LABELS.outcomeDuplicate;
    case 'leak':
      return LABELS.outcomeLeak;
    case 'replayed':
      return LABELS.outcomeReplayed;
    case 'read':
      return LABELS.outcomeRead;
    case 'none':
      return LABELS.outcomeNone;
    default:
      return LABELS.outcomePending;
  }
}

function outcomeColor(outcome: Outcome) {
  if (outcome === 'duplicate' || outcome === 'leak') return 'var(--bad)';
  if (outcome === 'repeat' || outcome === 'replayed') return 'var(--good)';
  return 'inherit';
}

function statusColor(status: string) {
  if (status.startsWith('2')) return 'var(--good)';
  if (status === 'pending') return 'var(--accent)';
  return 'var(--bad)';
}

function specLabel(spec: Request['idempotentBySpec']) {
  if (spec === 'yes') return LABELS.idempotentYes;
  if (spec === 'no') return LABELS.idempotentNo;
  return LABELS.idempotentMaybe;
}

function specColor(spec: Request['idempotentBySpec']) {
  if (spec === 'yes') return 'var(--good)';
  if (spec === 'no') return 'var(--bad)';
  return 'var(--accent)';
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
        <span style={bigMethodStyle}>{request.method}</span>
        <span style={pathStyle}>{request.path}</span>
        {request.attempt > 1 && (
          <span style={badgeStyle}>
            {tl(LABELS.attemptN, lang)} {request.attempt}
          </span>
        )}
        <span style={{ ...bannerStatusStyle, color: statusColor(status) }}>
          {status === 'pending' ? tl(LABELS.pending, lang) : status}
        </span>
      </div>
      <div style={bannerRowStyle}>
        <span style={{ ...badgeStyle, color: specColor(request.idempotentBySpec) }}>
          {tl(specLabel(request.idempotentBySpec), lang)}
        </span>
        {request.relative && (
          <span style={{ ...badgeStyle, color: 'var(--bad)' }}>{tl(LABELS.relative, lang)}</span>
        )}
        {request.key && <span style={badgeStyle}>Idempotency-Key: {request.key}</span>}
        {response?.replayed && (
          <span style={{ ...badgeStyle, color: 'var(--good)' }}>{tl(LABELS.replayed, lang)}</span>
        )}
        {response && (
          <span style={{ ...badgeStyle, color: response.delivered ? 'inherit' : 'var(--bad)' }}>
            {response.delivered ? tl(LABELS.delivered, lang) : tl(LABELS.answerLost, lang)}
          </span>
        )}
      </div>
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
const bigMethodStyle: CSSProperties = { fontWeight: 700, fontSize: 15, fontFamily: 'monospace' };
const methodStyle: CSSProperties = { fontWeight: 700, fontSize: 12, fontFamily: 'monospace' };
const pathStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13 };
const badgeStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const bannerStatusStyle: CSSProperties = { marginLeft: 'auto', fontSize: 13, fontWeight: 600 };
const ledgerStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 4 };
const ledgerRowStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 8,
  flexWrap: 'wrap',
  padding: '3px 8px',
  borderRadius: 6,
  border: '1px solid var(--border)',
  background: 'var(--viz-box)',
};
const seqStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 11, opacity: 0.5, minWidth: 22 };
const statusStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 11 };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const absentStyle: CSSProperties = { fontSize: 12, opacity: 0.4, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
