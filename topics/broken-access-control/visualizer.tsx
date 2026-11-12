import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to watch each request pass the checks login does not make: which endpoint, which record, which fields.',
    ru: 'Запустите код, чтобы увидеть, как каждый запрос проходит проверки, которых не делает вход в систему: какой эндпоинт, какая запись, какие поля.',
  },
  defensesTitle: { en: 'server-side defenses', ru: 'защиты на сервере' },
  chainTitle: { en: 'the checks', ru: 'проверки' },
  bodyTitle: { en: 'request body', ru: 'тело запроса' },
  recordsTitle: { en: 'orders in the store', ru: 'заказы в хранилище' },
  callersTitle: { en: 'users', ru: 'пользователи' },
  noRequest: { en: 'no request in flight', ru: 'запроса сейчас нет' },
  as: { en: 'as', ru: 'от' },
  inChain: { en: 'checking…', ru: 'проверяется…' },
  allowed: { en: 'served', ru: 'обслужен' },
  denied: { en: 'refused', ru: 'отклонён' },
  breach: { en: 'served — and should not have been', ru: 'обслужен — а не должен был' },
  clientField: { en: 'client may set', ru: 'клиенту можно' },
  serverField: { en: 'server-owned', ru: 'решает сервер' },
  inResponse: { en: 'in response', ru: 'в ответе' },
  exposed: { en: 'leaked', ru: 'утекла' },
  tampered: { en: 'tampered', ru: 'изменена чужим' },
  deleted: { en: 'deleted', ru: 'удалена' },
  owner: { en: 'owner', ru: 'владелец' },
  on: { en: 'on', ru: 'вкл' },
  off: { en: 'off', ru: 'выкл' },
  statRequests: { en: 'requests', ru: 'запросов' },
  statAllowed: { en: 'served', ru: 'обслужено' },
  statDenied: { en: 'refused', ru: 'отклонено' },
  statBreaches: { en: 'should not have been served', ru: 'обслужено зря' },
  statHorizontal: { en: 'horizontal', ru: 'горизонтальных' },
  statVertical: { en: 'vertical', ru: 'вертикальных' },
  statMass: { en: 'mass assignment', ru: 'mass assignment' },
  statLeaked: { en: 'foreign rows leaked', ru: 'чужих строк утекло' },
  defenses: {
    ownerOnRead: { en: 'owner check on reads', ru: 'владелец при чтении' },
    ownerOnWrite: { en: 'owner check on writes', ru: 'владелец при записи' },
    scopedLists: { en: 'scoped lists', ru: 'списки по сессии' },
    roleCheck: { en: 'role check on /admin', ru: 'проверка роли на /admin' },
    fieldAllowlist: { en: 'field allowlist (DTO)', ru: 'список полей (DTO)' },
    notFoundForForeign: { en: '404 for foreign', ru: '404 на чужое' },
  },
  gates: {
    authentication: { en: 'who? (authn)', ru: 'кто? (authn)' },
    functionLevel: { en: 'this endpoint?', ru: 'этот эндпоинт?' },
    objectLevel: { en: 'this record?', ru: 'эта запись?' },
    propertyLevel: { en: 'these fields?', ru: 'эти поля?' },
    handler: { en: 'handler', ru: 'обработчик' },
  },
  reasons: {
    'not-found': { en: 'no such order', ru: 'такого заказа нет' },
    'not-owner': { en: 'the order belongs to somebody else', ru: 'заказ принадлежит другому' },
    role: { en: 'the caller lacks the ADMIN role', ru: 'у вызывающего нет роли ADMIN' },
  },
  kinds: {
    horizontal: { en: 'horizontal (IDOR)', ru: 'горизонтальное (IDOR)' },
    vertical: { en: 'vertical', ru: 'вертикальное' },
    massAssignment: { en: 'mass assignment', ru: 'mass assignment' },
  },
};

type DefenseKey = keyof typeof LABELS.defenses;
type GateId = keyof typeof LABELS.gates;
type Reason = keyof typeof LABELS.reasons;
type Kind = keyof typeof LABELS.kinds;
type GateStatus = 'pending' | 'passed' | 'denied' | 'skipped' | 'breach';

interface Caller {
  subject: string;
  role: string;
  displayName: string;
}
interface OrderRecord {
  id: string;
  owner: string;
  price: number;
  status: string;
  note: string;
  exposed: boolean;
  tampered: boolean;
  deleted: boolean;
  inResponse: boolean;
}
interface BodyField {
  field: string;
  value: string;
  clientEditable: boolean;
}
interface Request {
  caller: string;
  method: string;
  path: string;
  body: BodyField[];
}
interface Gate {
  id: GateId;
  status: GateStatus;
  detail: string;
}
interface Outcome {
  status: number | null;
  decision: 'pending' | 'allowed' | 'denied' | 'breach';
  reason: Reason | null;
  detail: string;
  kinds: Kind[];
}
interface Stats {
  requests: number;
  allowed: number;
  denied: number;
  breaches: number;
  horizontal: number;
  vertical: number;
  massAssignment: number;
  leakedRows: number;
}
interface AccessState {
  defenses: Record<DefenseKey, boolean>;
  callers: Caller[];
  records: OrderRecord[];
  request?: Request;
  gates: Gate[];
  outcome?: Outcome;
  stats: Stats;
}

const GATE_COLORS: Record<GateStatus, string> = {
  pending: 'var(--border)',
  passed: 'var(--good)',
  denied: 'var(--bad)',
  skipped: 'var(--border)',
  breach: 'var(--bad)',
};

export default function AccessControlVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as AccessState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }
  const highlight = new Set(event?.highlight ?? []);

  return (
    <div style={wrapStyle}>
      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.defensesTitle, lang)}</div>
        <div style={chipRowStyle}>
          {(Object.keys(LABELS.defenses) as DefenseKey[]).map((key) => {
            const on = state.defenses[key];
            return (
              <span
                key={key}
                style={{
                  ...chipStyle,
                  color: on ? 'var(--good)' : 'var(--bad)',
                  borderColor: on ? 'var(--good)' : 'var(--border)',
                  opacity: on ? 1 : 0.75,
                  background: highlight.has(`defense:${key}`)
                    ? 'var(--viz-highlight)'
                    : 'var(--viz-badge)',
                }}
              >
                {on ? '✓' : '✗'} {tl(LABELS.defenses[key], lang)}
              </span>
            );
          })}
        </div>
      </div>

      <RequestBanner state={state} lang={lang} />

      {state.request && state.request.body.length > 0 && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.bodyTitle, lang)}</div>
          <BoxGroup boxes={bodyBoxes(state.request.body, highlight, lang)} />
        </div>
      )}

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.chainTitle, lang)}</div>
        <div style={chainStyle}>
          {state.gates.map((gate, i) => (
            <GateBox
              key={gate.id}
              gate={gate}
              lang={lang}
              last={i === state.gates.length - 1}
              highlighted={highlight.has(`gate:${gate.id}`)}
            />
          ))}
        </div>
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.recordsTitle, lang)}</div>
        <BoxGroup boxes={recordBoxes(state.records, highlight, lang)} />
      </div>

      {state.callers.length > 0 && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.callersTitle, lang)}</div>
          <BoxGroup
            boxes={state.callers.map((c) => ({
              id: `caller-${c.subject}`,
              title: c.subject,
              subtitle: c.displayName !== c.subject ? `${c.role} · ${c.displayName}` : c.role,
              highlighted: highlight.has(`caller:${c.subject}`),
            }))}
          />
        </div>
      )}

      <div style={statsStyle}>
        <Stat label={tl(LABELS.statRequests, lang)} value={state.stats.requests} />
        <Stat label={tl(LABELS.statAllowed, lang)} value={state.stats.allowed} />
        <Stat label={tl(LABELS.statDenied, lang)} value={state.stats.denied} />
        <Stat label={tl(LABELS.statBreaches, lang)} value={state.stats.breaches} bad />
        <Stat label={tl(LABELS.statHorizontal, lang)} value={state.stats.horizontal} bad />
        <Stat label={tl(LABELS.statVertical, lang)} value={state.stats.vertical} bad />
        <Stat label={tl(LABELS.statMass, lang)} value={state.stats.massAssignment} bad />
        <Stat label={tl(LABELS.statLeaked, lang)} value={state.stats.leakedRows} bad />
      </div>
    </div>
  );
}

function bodyBoxes(body: BodyField[], highlight: Set<string>, lang: Lang): Box[] {
  return body.map((f) => ({
    id: `field-${f.field}`,
    title: `${f.field}: ${f.value}`,
    subtitle: tl(f.clientEditable ? LABELS.clientField : LABELS.serverField, lang),
    highlighted: highlight.has(`field:${f.field}`),
  }));
}

function recordBoxes(records: OrderRecord[], highlight: Set<string>, lang: Lang): Box[] {
  return records.map((r) => {
    const parts = [`${tl(LABELS.owner, lang)} ${r.owner}`, `${r.price}`, r.status];
    if (r.note) parts.push(`“${r.note}”`);
    if (r.inResponse) parts.push(tl(LABELS.inResponse, lang));
    if (r.exposed) parts.push(`⚠ ${tl(LABELS.exposed, lang)}`);
    if (r.tampered) parts.push(`⚠ ${tl(LABELS.tampered, lang)}`);
    if (r.deleted) parts.push(tl(LABELS.deleted, lang));
    return {
      id: `record-${r.id}`,
      title: `#${r.id.length > 10 ? `${r.id.slice(0, 8)}…` : r.id}`,
      subtitle: parts.join(' · '),
      highlighted: highlight.has(`record:${r.id}`) || r.inResponse,
      dim: r.deleted,
    };
  });
}

function GateBox({
  gate,
  lang,
  last,
  highlighted,
}: {
  gate: Gate;
  lang: Lang;
  last: boolean;
  highlighted: boolean;
}) {
  const color = GATE_COLORS[gate.status];
  const faded = gate.status === 'pending' || gate.status === 'skipped';
  return (
    <>
      <div
        style={{
          ...gateStyle,
          borderColor: color,
          opacity: faded ? 0.45 : 1,
          background: highlighted ? 'var(--viz-highlight)' : 'var(--viz-box)',
        }}
      >
        <div style={gateNameStyle}>{tl(LABELS.gates[gate.id], lang)}</div>
        <div style={{ ...gateMarkStyle, color }}>{gateMark(gate.status)}</div>
        {gate.detail && <div style={gateDetailStyle}>{gate.detail}</div>}
      </div>
      {!last && <span style={arrowStyle}>→</span>}
    </>
  );
}

function gateMark(status: GateStatus) {
  if (status === 'passed') return '✓';
  if (status === 'denied') return '✗';
  if (status === 'breach') return '!';
  if (status === 'skipped') return '–';
  return '·';
}

function RequestBanner({ state, lang }: { state: AccessState; lang: Lang }) {
  const { request, outcome } = state;
  if (!request || !outcome) {
    return <div style={{ ...bannerStyle, opacity: 0.5 }}>{tl(LABELS.noRequest, lang)}</div>;
  }
  const verdict =
    outcome.decision === 'allowed'
      ? { text: tl(LABELS.allowed, lang), color: 'var(--good)' }
      : outcome.decision === 'denied'
        ? { text: tl(LABELS.denied, lang), color: 'var(--bad)' }
        : outcome.decision === 'breach'
          ? { text: tl(LABELS.breach, lang), color: 'var(--bad)' }
          : { text: tl(LABELS.inChain, lang), color: 'var(--accent-ink)' };
  return (
    <div style={bannerStyle}>
      <span style={methodStyle}>{request.method}</span>
      <span style={pathStyle}>{request.path}</span>
      <span style={chipStyle}>
        {tl(LABELS.as, lang)} {request.caller}
      </span>
      <span style={{ ...verdictStyle, color: verdict.color }}>
        {outcome.status !== null ? `${outcome.status} · ` : ''}
        {verdict.text}
      </span>
      {outcome.reason && (
        <span style={reasonStyle}>{tl(LABELS.reasons[outcome.reason], lang)}</span>
      )}
      {outcome.kinds.length > 0 && (
        <span style={{ ...reasonStyle, color: 'var(--bad)' }}>
          {outcome.kinds.map((k) => tl(LABELS.kinds[k], lang)).join(' · ')}
        </span>
      )}
    </div>
  );
}

function Stat({ label, value, bad }: { label: string; value: number; bad?: boolean }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={{ ...statValueStyle, color: bad && value > 0 ? 'var(--bad)' : 'inherit' }}>
        {value}
      </div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const chipRowStyle: CSSProperties = { display: 'flex', gap: 6, flexWrap: 'wrap' };
const chipStyle: CSSProperties = {
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  border: '1px solid var(--border)',
  background: 'var(--viz-badge)',
};
const chainStyle: CSSProperties = { display: 'flex', alignItems: 'stretch', gap: 4, flexWrap: 'wrap' };
const gateStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '4px 8px',
  minWidth: 84,
  textAlign: 'center',
};
const gateNameStyle: CSSProperties = { fontSize: 10, opacity: 0.7 };
const gateMarkStyle: CSSProperties = { fontSize: 15, fontWeight: 700, lineHeight: 1.1 };
const gateDetailStyle: CSSProperties = { fontSize: 10, opacity: 0.65, fontFamily: 'monospace' };
const arrowStyle: CSSProperties = { alignSelf: 'center', opacity: 0.4, fontSize: 13 };
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
const pathStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13, opacity: 0.8, wordBreak: 'break-all' };
const verdictStyle: CSSProperties = { marginLeft: 'auto', fontSize: 13, fontWeight: 600 };
const reasonStyle: CSSProperties = { width: '100%', fontSize: 11, opacity: 0.8 };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
