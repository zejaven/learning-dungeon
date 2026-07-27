import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  schemeJwt: { en: 'stateless JWT', ru: 'JWT без состояния' },
  schemeSession: { en: 'opaque session id', ru: 'непрозрачный идентификатор сессии' },
  clock: { en: 'minute', ru: 'минута' },
  ttl: { en: 'lifetime', ru: 'срок жизни' },
  audienceOn: { en: "'aud' checked", ru: "«aud» проверяется" },
  denyListOn: { en: 'deny-list kept', ru: 'ведётся список отозванных' },
  trustAlgOn: { en: "algorithm read from the token", ru: 'алгоритм берётся из токена' },
  credentialTitle: { en: 'what the client sends', ru: 'что отправляет клиент' },
  noCredential: { en: 'nothing has been issued yet', ru: 'пока ничего не выдано' },
  header: { en: 'header', ru: 'header' },
  payload: { en: 'payload', ru: 'payload' },
  signature: { en: 'signature', ru: 'подпись' },
  noSignature: { en: 'none — the signature was removed', ru: 'нет — подпись удалили' },
  opaqueNote: {
    en: 'meaningless outside the server: no user, no role, no expiry',
    ru: 'бессмыслен вне сервера: ни пользователя, ни роли, ни срока',
  },
  tampered: { en: 'edited after signing', ru: 'изменён после подписания' },
  bytes: { en: 'bytes per request', ru: 'байт на запрос' },
  checksTitle: { en: 'what the verifier checks', ru: 'что проверяет проверяющий' },
  storeTitle: { en: 'session store', ru: 'хранилище сессий' },
  storeEmpty: {
    en: 'nothing — the token carries its own claims',
    ru: 'ничего — токен везёт свои claims с собой',
  },
  storeEmptySession: { en: 'no live sessions', ru: 'живых сессий нет' },
  deniedTitle: { en: 'deny-list', ru: 'список отозванных' },
  deniedEmpty: { en: 'nothing revoked', ru: 'ничего не отозвано' },
  truthTitle: { en: 'what the database says right now', ru: 'что говорит база прямо сейчас' },
  truthEmpty: { en: 'nobody yet', ru: 'пока никого' },
  servicesTitle: { en: 'services that verify', ru: 'сервисы, которые проверяют' },
  servicesEmpty: { en: 'none registered', ru: 'ни одного не зарегистрировано' },
  verifications: { en: 'checks', ru: 'проверок' },
  disabled: { en: 'disabled', ru: 'отключён' },
  expires: { en: 'until minute', ru: 'до минуты' },
  noAction: { en: 'nothing has happened yet', ru: 'пока ничего не произошло' },
  running: { en: 'in progress…', ru: 'выполняется…' },
  allowed: { en: 'ok', ru: 'ок' },
  denied: { en: 'refused', ru: 'отказ' },
  breach: { en: 'served — and should not have been', ru: 'обслужен — а не должен был' },
  runHint: {
    en: 'Run the code to watch a JWT be signed, read by anyone, verified without a lookup — and keep working after logout.',
    ru: 'Запустите код, чтобы увидеть, как JWT подписывают, читают без ключа, проверяют без обращения к хранилищу — и как он продолжает работать после выхода.',
  },
  checks: {
    parse: { en: 'parse', ru: 'разбор' },
    source: { en: 'source', ru: 'источник' },
    trust: { en: 'trust', ru: 'доверие' },
    claims: { en: 'exp / aud', ru: 'exp / aud' },
    revoked: { en: 'revoked?', ru: 'отозван?' },
  },
  actions: {
    idle: { en: 'idle', ru: 'ожидание' },
    issue: { en: 'issue', ru: 'выдача' },
    decode: { en: 'decode', ru: 'расшифровка' },
    tamper: { en: 'forge', ru: 'подделка' },
    verify: { en: 'verify', ru: 'проверка' },
    truth: { en: 'the database changes', ru: 'база меняется' },
    logout: { en: 'logout', ru: 'выход' },
    clock: { en: 'time passes', ru: 'идёт время' },
    audit: { en: 'audit', ru: 'итоги' },
  },
  reasons: {
    'no-credential': { en: 'nothing was presented', ru: 'ничего не предъявлено' },
    'unknown-session': { en: 'no such session on the server', ru: 'такой сессии на сервере нет' },
    'bad-signature': { en: 'the signature does not match', ru: 'подпись не совпадает' },
    expired: { en: "past 'exp'", ru: 'срок «exp» истёк' },
    'wrong-audience': { en: 'addressed to another service', ru: 'адресован другому сервису' },
    revoked: { en: 'on the deny-list', ru: 'в списке отозванных' },
    'account-disabled': { en: 'the account is switched off', ru: 'аккаунт отключён' },
    'alg-none': { en: 'an unsigned token was believed', ru: 'поверили неподписанному токену' },
    'revoked-token': { en: 'logged out, and it still verifies', ru: 'вышли, а он всё ещё проходит проверку' },
    'stale-claim': { en: 'the claim is out of date', ru: 'claim устарел' },
  },
  stats: {
    issued: { en: 'issued', ru: 'выдано' },
    verifications: { en: 'checks', ru: 'проверок' },
    accepted: { en: 'served', ru: 'обслужено' },
    refused: { en: 'refused', ru: 'отклонено' },
    storeLookups: { en: 'store lookups', ru: 'обращений к хранилищу' },
    bytesOnTheWire: { en: 'credential bytes', ru: 'байт учётных данных' },
    breaches: { en: 'should not have been served', ru: 'не следовало обслуживать' },
  },
};

type CheckId = keyof typeof LABELS.checks;
type ActionKind = keyof typeof LABELS.actions;
type Reason = keyof typeof LABELS.reasons;
type CheckStatus = 'pending' | 'passed' | 'failed' | 'skipped' | 'breach';

interface Claim {
  k: string;
  v: string;
}
interface Credential {
  kind: 'jwt' | 'opaque';
  raw: string;
  size: number;
  revealed: boolean;
  tampered: boolean;
  signature: string;
  header: Claim[];
  payload: Claim[];
}
interface Service {
  name: string;
  verifications: number;
}
interface SessionRecord {
  id: string;
  user: string;
  expiresAt: number;
}
interface TruthRow {
  user: string;
  role: string;
  active: boolean;
}
interface Check {
  id: CheckId;
  status: CheckStatus;
  detail: string;
}
interface Action {
  kind: ActionKind;
  actor: string;
  detail: string;
}
interface Outcome {
  status: number | null;
  decision: 'idle' | 'pending' | 'allowed' | 'denied' | 'breach';
  reason: Reason | null;
  detail: string;
}
interface Stats {
  issued: number;
  verifications: number;
  accepted: number;
  refused: number;
  storeLookups: number;
  bytesOnTheWire: number;
  breaches: number;
}
interface JwtState {
  scheme: 'jwt' | 'session';
  clock: number;
  accessTtl: number;
  checkAudience: boolean;
  denyListEnabled: boolean;
  trustAlgHeader: boolean;
  services: Service[];
  credential: Credential | null;
  sessionStore: SessionRecord[];
  denied: string[];
  truth: TruthRow[];
  action: Action;
  checks: Check[];
  outcome: Outcome;
  stats: Stats;
}

const CHECK_COLORS: Record<CheckStatus, string> = {
  pending: 'var(--border)',
  passed: 'var(--good)',
  failed: 'var(--bad)',
  skipped: 'var(--border)',
  breach: 'var(--bad)',
};

export default function JwtVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as JwtState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const stateless = state.scheme === 'jwt';

  return (
    <div style={wrapStyle}>
      <div style={policyRowStyle}>
        <span style={{ ...tagStyle, color: 'var(--accent)' }}>
          {tl(stateless ? LABELS.schemeJwt : LABELS.schemeSession, lang)}
        </span>
        <span style={{ ...chipStyle, ...(highlight.has('clock') ? highlightChipStyle : {}) }}>
          {tl(LABELS.clock, lang)} {state.clock}
        </span>
        <span style={chipStyle}>
          {tl(LABELS.ttl, lang)}: {state.accessTtl}
        </span>
        {state.checkAudience && <span style={chipStyle}>{tl(LABELS.audienceOn, lang)}</span>}
        {state.denyListEnabled && (
          <span style={{ ...chipStyle, ...(highlight.has('denylist') ? highlightChipStyle : {}) }}>
            {tl(LABELS.denyListOn, lang)}
          </span>
        )}
        {state.trustAlgHeader && <span style={warnChipStyle}>{tl(LABELS.trustAlgOn, lang)}</span>}
      </div>

      <ActionBanner state={state} lang={lang} />

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.credentialTitle, lang)}</div>
        {state.credential ? (
          <CredentialCard credential={state.credential} highlight={highlight} lang={lang} />
        ) : (
          <div style={mutedStyle}>{tl(LABELS.noCredential, lang)}</div>
        )}
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.checksTitle, lang)}</div>
        <div style={chainStyle}>
          {state.checks.map((check, i) => (
            <CheckBox
              key={check.id}
              check={check}
              lang={lang}
              last={i === state.checks.length - 1}
              highlighted={highlight.has(`check:${check.id}`)}
            />
          ))}
        </div>
      </div>

      <div style={columnsStyle}>
        <div style={columnStyle}>
          <div style={sectionLabelStyle}>{tl(LABELS.storeTitle, lang)}</div>
          {state.sessionStore.length === 0 ? (
            <div style={stateless ? statelessStyle : mutedStyle}>
              {tl(stateless ? LABELS.storeEmpty : LABELS.storeEmptySession, lang)}
            </div>
          ) : (
            <BoxGroup boxes={sessionBoxes(state.sessionStore, highlight, lang)} />
          )}
        </div>
        <div style={columnStyle}>
          <div style={sectionLabelStyle}>{tl(LABELS.truthTitle, lang)}</div>
          {state.truth.length === 0 ? (
            <div style={mutedStyle}>{tl(LABELS.truthEmpty, lang)}</div>
          ) : (
            <BoxGroup boxes={truthBoxes(state.truth, highlight, lang)} />
          )}
        </div>
      </div>

      <div style={columnsStyle}>
        <div style={columnStyle}>
          <div style={sectionLabelStyle}>{tl(LABELS.servicesTitle, lang)}</div>
          {state.services.length === 0 ? (
            <div style={mutedStyle}>{tl(LABELS.servicesEmpty, lang)}</div>
          ) : (
            <BoxGroup boxes={serviceBoxes(state.services, highlight, lang)} />
          )}
        </div>
        <div style={columnStyle}>
          <div style={sectionLabelStyle}>{tl(LABELS.deniedTitle, lang)}</div>
          {state.denied.length === 0 ? (
            <div style={mutedStyle}>{tl(LABELS.deniedEmpty, lang)}</div>
          ) : (
            <BoxGroup
              boxes={state.denied.map((id) => ({
                id: `denied-${id}`,
                title: id,
                highlighted: highlight.has('denylist'),
              }))}
            />
          )}
        </div>
      </div>

      <div style={statsStyle}>
        <Stat label={tl(LABELS.stats.issued, lang)} value={state.stats.issued} />
        <Stat label={tl(LABELS.stats.verifications, lang)} value={state.stats.verifications} />
        <Stat label={tl(LABELS.stats.accepted, lang)} value={state.stats.accepted} />
        <Stat label={tl(LABELS.stats.refused, lang)} value={state.stats.refused} />
        <Stat
          label={tl(LABELS.stats.storeLookups, lang)}
          value={state.stats.storeLookups}
          color={highlight.has('store') ? 'var(--accent)' : undefined}
        />
        <Stat label={tl(LABELS.stats.bytesOnTheWire, lang)} value={state.stats.bytesOnTheWire} />
        <Stat
          label={tl(LABELS.stats.breaches, lang)}
          value={state.stats.breaches}
          color={state.stats.breaches > 0 ? 'var(--bad)' : undefined}
        />
      </div>
    </div>
  );
}

function CredentialCard({
  credential,
  highlight,
  lang,
}: {
  credential: Credential;
  highlight: Set<string>;
  lang: Lang;
}) {
  const focused = highlight.has('credential');
  return (
    <div
      style={{
        ...cardStyle,
        borderColor: credential.tampered ? 'var(--bad)' : 'var(--border)',
        background: focused ? 'var(--viz-highlight)' : 'var(--viz-box)',
      }}
    >
      <div style={rawRowStyle}>
        <span style={rawStyle}>{credential.raw}</span>
        <span style={sizeStyle}>
          {credential.size} {tl(LABELS.bytes, lang)}
        </span>
      </div>
      {credential.tampered && <div style={tamperedStyle}>{tl(LABELS.tampered, lang)}</div>}
      {credential.revealed ? (
        <div style={segmentsStyle}>
          <Segment
            title={tl(LABELS.header, lang)}
            claims={credential.header}
            highlighted={highlight.has('header')}
          />
          <Segment
            title={tl(LABELS.payload, lang)}
            claims={credential.payload}
            highlighted={highlight.has('payload')}
          />
          <div style={{ ...segmentStyle, minWidth: 110 }}>
            <div style={segmentTitleStyle}>{tl(LABELS.signature, lang)}</div>
            <div style={credential.signature ? signatureStyle : missingSignatureStyle}>
              {credential.signature || tl(LABELS.noSignature, lang)}
            </div>
          </div>
        </div>
      ) : (
        <div style={opaqueNoteStyle}>{tl(LABELS.opaqueNote, lang)}</div>
      )}
    </div>
  );
}

function Segment({
  title,
  claims,
  highlighted,
}: {
  title: string;
  claims: Claim[];
  highlighted: boolean;
}) {
  return (
    <div
      style={{
        ...segmentStyle,
        ...(highlighted ? { borderColor: 'var(--accent)' } : {}),
      }}
    >
      <div style={segmentTitleStyle}>{title}</div>
      {claims.map((claim) => (
        <div key={claim.k} style={claimRowStyle}>
          <span style={claimKeyStyle}>{claim.k}</span>
          <span style={claimValueStyle}>{claim.v}</span>
        </div>
      ))}
    </div>
  );
}

function sessionBoxes(records: SessionRecord[], highlight: Set<string>, lang: Lang): Box[] {
  return records.map((record) => ({
    id: `session-${record.id}`,
    title: `${record.id} → ${record.user}`,
    subtitle: `${tl(LABELS.expires, lang)} ${record.expiresAt}`,
    highlighted: highlight.has('store'),
  }));
}

function truthBoxes(rows: TruthRow[], highlight: Set<string>, lang: Lang): Box[] {
  return rows.map((row) => ({
    id: `truth-${row.user}`,
    title: row.user,
    subtitle: row.active ? row.role : tl(LABELS.disabled, lang),
    highlighted: highlight.has('truth'),
    dim: !row.active,
  }));
}

function serviceBoxes(services: Service[], highlight: Set<string>, lang: Lang): Box[] {
  return services.map((service) => ({
    id: `service-${service.name}`,
    title: service.name,
    subtitle: `${service.verifications} ${tl(LABELS.verifications, lang)}`,
    highlighted: highlight.has(`service:${service.name}`),
  }));
}

function CheckBox({
  check,
  lang,
  last,
  highlighted,
}: {
  check: Check;
  lang: Lang;
  last: boolean;
  highlighted: boolean;
}) {
  const color = CHECK_COLORS[check.status];
  const faded = check.status === 'pending' || check.status === 'skipped';
  return (
    <>
      <div
        style={{
          ...checkStyle,
          borderColor: color,
          opacity: faded ? 0.45 : 1,
          background: highlighted ? 'var(--viz-highlight)' : 'var(--viz-box)',
          boxShadow: highlighted ? '0 0 0 2px rgba(255,204,102,0.35)' : undefined,
        }}
      >
        <div style={checkNameStyle}>{tl(LABELS.checks[check.id], lang)}</div>
        <div style={{ ...checkMarkStyle, color }}>{checkMark(check.status)}</div>
        {check.detail && <div style={checkDetailStyle}>{check.detail}</div>}
      </div>
      {!last && <span style={arrowStyle}>→</span>}
    </>
  );
}

function checkMark(status: CheckStatus) {
  if (status === 'passed') return '✓';
  if (status === 'failed') return '✗';
  if (status === 'breach') return '!';
  if (status === 'skipped') return '–';
  return '·';
}

function ActionBanner({ state, lang }: { state: JwtState; lang: Lang }) {
  const { action, outcome } = state;
  if (action.kind === 'idle') {
    return <div style={{ ...bannerStyle, opacity: 0.5 }}>{tl(LABELS.noAction, lang)}</div>;
  }
  const verdict = verdictOf(outcome, lang);
  return (
    <div style={bannerStyle}>
      <span style={actionStyle}>{tl(LABELS.actions[action.kind], lang)}</span>
      {action.actor && <span style={actorStyle}>{action.actor}</span>}
      {action.detail && <span style={detailStyle}>{action.detail}</span>}
      <span style={{ ...verdictStyle, color: verdict.color }}>
        {outcome.status !== null ? `${outcome.status} · ` : ''}
        {verdict.text}
      </span>
      {outcome.reason && (
        <span style={reasonStyle}>
          {tl(LABELS.reasons[outcome.reason], lang)}
          {outcome.detail ? ` (${outcome.detail})` : ''}
        </span>
      )}
    </div>
  );
}

function verdictOf(outcome: Outcome, lang: Lang) {
  if (outcome.decision === 'allowed') {
    return { text: tl(LABELS.allowed, lang), color: 'var(--good)' };
  }
  if (outcome.decision === 'denied') {
    return { text: tl(LABELS.denied, lang), color: 'var(--bad)' };
  }
  if (outcome.decision === 'breach') {
    return { text: tl(LABELS.breach, lang), color: 'var(--bad)' };
  }
  return { text: tl(LABELS.running, lang), color: 'var(--accent)' };
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
const policyRowStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  flexWrap: 'wrap',
};
const chainStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'stretch',
  gap: 4,
  flexWrap: 'wrap',
};
const checkStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '4px 8px',
  minWidth: 88,
  textAlign: 'center',
};
const checkNameStyle: CSSProperties = { fontSize: 10, opacity: 0.7 };
const checkMarkStyle: CSSProperties = { fontSize: 15, fontWeight: 700, lineHeight: 1.1 };
const checkDetailStyle: CSSProperties = { fontSize: 10, opacity: 0.65, fontFamily: 'monospace' };
const arrowStyle: CSSProperties = { alignSelf: 'center', opacity: 0.4, fontSize: 13 };
const tagStyle: CSSProperties = { fontSize: 11, fontWeight: 700 };
const chipStyle: CSSProperties = {
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const warnChipStyle: CSSProperties = {
  ...chipStyle,
  color: 'var(--bad)',
  border: '1px solid var(--bad)',
};
const highlightChipStyle: CSSProperties = {
  background: 'var(--viz-highlight)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.35)',
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
const actionStyle: CSSProperties = { fontWeight: 700, fontSize: 14 };
const actorStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13, fontWeight: 600 };
const detailStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 12, opacity: 0.75 };
const verdictStyle: CSSProperties = { marginLeft: 'auto', fontSize: 13, fontWeight: 600 };
const reasonStyle: CSSProperties = { width: '100%', fontSize: 11, opacity: 0.75 };
const cardStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '8px 10px',
  display: 'flex',
  flexDirection: 'column',
  gap: 8,
};
const rawRowStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 10,
  flexWrap: 'wrap',
};
const rawStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 11,
  wordBreak: 'break-all',
  flex: '1 1 240px',
};
const sizeStyle: CSSProperties = { fontSize: 11, opacity: 0.6, whiteSpace: 'nowrap' };
const tamperedStyle: CSSProperties = { fontSize: 11, color: 'var(--bad)', fontWeight: 600 };
const segmentsStyle: CSSProperties = { display: 'flex', gap: 8, flexWrap: 'wrap' };
const segmentStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '4px 8px',
  minWidth: 140,
  flex: '0 1 auto',
};
const segmentTitleStyle: CSSProperties = { fontSize: 10, opacity: 0.6, marginBottom: 3 };
const claimRowStyle: CSSProperties = { display: 'flex', gap: 6, fontSize: 11 };
const claimKeyStyle: CSSProperties = { fontFamily: 'monospace', opacity: 0.65, minWidth: 30 };
const claimValueStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 600 };
const signatureStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 12,
  fontWeight: 700,
  color: 'var(--good)',
};
const missingSignatureStyle: CSSProperties = { fontSize: 11, color: 'var(--bad)' };
const opaqueNoteStyle: CSSProperties = { fontSize: 12, opacity: 0.6, fontStyle: 'italic' };
const columnsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const columnStyle: CSSProperties = { flex: '1 1 220px', minWidth: 200 };
const statelessStyle: CSSProperties = {
  fontSize: 12,
  opacity: 0.75,
  fontStyle: 'italic',
  border: '1px dashed var(--border)',
  borderRadius: 6,
  padding: '6px 10px',
};
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const mutedStyle: CSSProperties = { fontSize: 12, opacity: 0.5, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
