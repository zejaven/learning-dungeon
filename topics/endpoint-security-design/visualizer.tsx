import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  stanceDeny: { en: 'default: deny', ru: 'по умолчанию: запрет' },
  stancePermit: { en: 'default: permit', ru: 'по умолчанию: разрешить' },
  rateLimit: { en: 'rate limit', ru: 'лимит частоты' },
  trustsHeaders: { en: 'trusts X-User-* headers', ru: 'доверяет заголовкам X-User-*' },
  trustsUnverified: { en: 'does not verify signatures', ru: 'не проверяет подписи' },
  rulesTitle: { en: 'rule list (first match wins)', ru: 'список правил (решает первое совпадение)' },
  noRules: { en: 'no rules configured', ru: 'правил не задано' },
  chainTitle: { en: 'the chain', ru: 'цепочка проверок' },
  principalTitle: { en: 'caller', ru: 'вызывающий' },
  noPrincipal: { en: 'nobody identified yet', ru: 'личность ещё не установлена' },
  roles: { en: 'roles', ru: 'роли' },
  scopes: { en: 'scopes', ru: 'scope' },
  fromToken: { en: 'from a verified token', ru: 'из проверенного токена' },
  fromHeader: { en: 'from a header the client set', ru: 'из заголовка, который выставил клиент' },
  headersTitle: { en: 'request headers', ru: 'заголовки запроса' },
  noRequest: { en: 'no request made yet', ru: 'запросов ещё не было' },
  inChain: { en: 'in the chain…', ru: 'идёт по цепочке…' },
  allowed: { en: 'served', ru: 'обслужен' },
  denied: { en: 'refused', ru: 'отклонён' },
  breach: { en: 'served — and should not have been', ru: 'обслужен — а не должен был' },
  none: { en: 'none', ru: 'нет' },
  shadowed: { en: 'unreachable', ru: 'недостижимо' },
  ownerOnly: { en: 'owner only', ru: 'только владелец' },
  statRequests: { en: 'requests', ru: 'запросов' },
  statAllowed: { en: 'served', ru: 'обслужено' },
  statUnauthenticated: { en: '401', ru: '401' },
  statForbidden: { en: '403 / 404', ru: '403 / 404' },
  statRateLimited: { en: '429', ru: '429' },
  statInsecure: { en: 'plaintext', ru: 'открытым текстом' },
  statLeaked: { en: 'credentials leaked', ru: 'учётных данных засвечено' },
  statBreaches: { en: 'should not have been served', ru: 'не следовало обслуживать' },
  runHint: {
    en: 'Run the code to watch each request walk the chain: transport, limits, rules, who you are, what you may do, and whose record it is.',
    ru: 'Запустите код, чтобы увидеть, как каждый запрос идёт по цепочке: транспорт, лимиты, правила, кто вы, что вам можно и чья это запись.',
  },
  gates: {
    transport: { en: 'transport', ru: 'транспорт' },
    rateLimit: { en: 'rate limit', ru: 'частота' },
    routing: { en: 'rule match', ru: 'подбор правила' },
    authentication: { en: 'authentication', ru: 'аутентификация' },
    authorization: { en: 'authorization', ru: 'авторизация' },
    ownership: { en: 'ownership', ru: 'владелец' },
    handler: { en: 'handler', ru: 'обработчик' },
  },
  requirements: {
    permitAll: { en: 'anyone', ru: 'кто угодно' },
    authenticated: { en: 'authenticated', ru: 'аутентифицирован' },
    hasRole: { en: 'role', ru: 'роль' },
  },
  reasons: {
    'insecure-transport': {
      en: 'the connection was not encrypted',
      ru: 'соединение не зашифровано',
    },
    'rate-limited': { en: 'too many requests from this client', ru: 'слишком много запросов от клиента' },
    'no-rule': { en: 'no rule matched, and the default is deny', ru: 'ни одно правило не подошло, по умолчанию запрет' },
    'no-credential': { en: 'no credential was presented', ru: 'учётные данные не предъявлены' },
    'bad-signature': { en: 'the token signature does not verify', ru: 'подпись токена не проходит проверку' },
    'wrong-issuer': { en: 'the token comes from an untrusted issuer', ru: 'токен выпущен недоверенным издателем' },
    'wrong-audience': { en: 'the token was minted for another API', ru: 'токен выпущен для другого API' },
    expired: { en: 'the token is past its expiry', ru: 'токен просрочен' },
    role: { en: 'the caller lacks the required role', ru: 'у вызывающего нет требуемой роли' },
    scope: { en: 'this token lacks the required scope', ru: 'у этого токена нет требуемого scope' },
    'not-owner': { en: 'the record belongs to somebody else', ru: 'запись принадлежит другому' },
  },
};

type Reason = keyof typeof LABELS.reasons;
type GateId = keyof typeof LABELS.gates;
type Requirement = keyof typeof LABELS.requirements;
type GateStatus = 'pending' | 'passed' | 'denied' | 'skipped' | 'breach';

interface Rule {
  method: string;
  pattern: string;
  requirement: Requirement;
  role: string | null;
  scopes: string[];
  ownerOnly: boolean;
  shadowed: boolean;
  matched: boolean;
}
interface Header {
  name: string;
  value: string;
  clientControlled: boolean;
}
interface Request {
  method: string;
  path: string;
  scheme: 'https' | 'http';
  credential: string;
  headers: Header[];
}
interface Principal {
  subject: string;
  roles: string[];
  scopes: string[];
  source: 'token' | 'header';
}
interface Gate {
  id: GateId;
  status: GateStatus;
  detail: string;
}
interface Outcome {
  status: number | null;
  decision: 'idle' | 'pending' | 'allowed' | 'denied' | 'breach';
  reason: Reason | null;
  detail: string;
}
interface Stats {
  requests: number;
  allowed: number;
  unauthenticated: number;
  forbidden: number;
  rateLimited: number;
  insecure: number;
  leakedCredentials: number;
  breaches: number;
}
interface SecurityState {
  defaultStance: 'deny' | 'permit';
  trustsIdentityHeaders: boolean;
  trustsUnverifiedTokens: boolean;
  rateLimit: number;
  rules: Rule[];
  request?: Request;
  principal: Principal | null;
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

export default function EndpointSecurityVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as SecurityState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  return (
    <div style={wrapStyle}>
      <div style={policyRowStyle}>
        <span
          style={{
            ...tagStyle,
            color: state.defaultStance === 'deny' ? 'var(--good)' : 'var(--bad)',
          }}
        >
          {tl(state.defaultStance === 'deny' ? LABELS.stanceDeny : LABELS.stancePermit, lang)}
        </span>
        {state.rateLimit > 0 && (
          <span style={chipStyle}>
            {tl(LABELS.rateLimit, lang)}: {state.rateLimit}
          </span>
        )}
        {state.trustsIdentityHeaders && (
          <span style={warnChipStyle}>{tl(LABELS.trustsHeaders, lang)}</span>
        )}
        {state.trustsUnverifiedTokens && (
          <span style={warnChipStyle}>{tl(LABELS.trustsUnverified, lang)}</span>
        )}
      </div>

      <RequestBanner state={state} lang={lang} />

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
        <div style={sectionLabelStyle}>{tl(LABELS.rulesTitle, lang)}</div>
        {state.rules.length === 0 ? (
          <div style={mutedStyle}>{tl(LABELS.noRules, lang)}</div>
        ) : (
          <BoxGroup boxes={ruleBoxes(state.rules, lang)} />
        )}
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.principalTitle, lang)}</div>
        {state.principal ? (
          <PrincipalCard principal={state.principal} lang={lang} />
        ) : (
          <div style={mutedStyle}>{tl(LABELS.noPrincipal, lang)}</div>
        )}
      </div>

      {state.request && state.request.headers.length > 0 && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.headersTitle, lang)}</div>
          <BoxGroup boxes={headerBoxes(state.request.headers, lang)} />
        </div>
      )}

      <div style={statsStyle}>
        <Stat label={tl(LABELS.statRequests, lang)} value={state.stats.requests} />
        <Stat label={tl(LABELS.statAllowed, lang)} value={state.stats.allowed} />
        <Stat label={tl(LABELS.statUnauthenticated, lang)} value={state.stats.unauthenticated} />
        <Stat label={tl(LABELS.statForbidden, lang)} value={state.stats.forbidden} />
        <Stat label={tl(LABELS.statRateLimited, lang)} value={state.stats.rateLimited} />
        <Stat label={tl(LABELS.statInsecure, lang)} value={state.stats.insecure} />
        <Stat
          label={tl(LABELS.statLeaked, lang)}
          value={state.stats.leakedCredentials}
          color={state.stats.leakedCredentials > 0 ? 'var(--bad)' : undefined}
        />
        <Stat
          label={tl(LABELS.statBreaches, lang)}
          value={state.stats.breaches}
          color={state.stats.breaches > 0 ? 'var(--bad)' : undefined}
        />
      </div>
    </div>
  );
}

function ruleBoxes(rules: Rule[], lang: Lang): Box[] {
  return rules.map((rule, i) => {
    const parts = [
      rule.requirement === 'hasRole'
        ? `${tl(LABELS.requirements.hasRole, lang)} ${rule.role ?? ''}`.trim()
        : tl(LABELS.requirements[rule.requirement], lang),
    ];
    if (rule.scopes.length > 0) parts.push(rule.scopes.join(', '));
    if (rule.ownerOnly) parts.push(tl(LABELS.ownerOnly, lang));
    if (rule.shadowed) parts.push(tl(LABELS.shadowed, lang));
    return {
      id: `rule-${i}`,
      title: `${rule.method} ${rule.pattern}`,
      subtitle: parts.join(' · '),
      highlighted: rule.matched,
      dim: rule.shadowed,
    };
  });
}

function headerBoxes(headers: Header[], lang: Lang): Box[] {
  return headers.map((header) => ({
    id: `header-${header.name}`,
    title: `${header.name}: ${header.value}`,
    subtitle: header.clientControlled ? tl(LABELS.fromHeader, lang) : undefined,
    highlighted: header.clientControlled,
  }));
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
          boxShadow: highlighted ? '0 0 0 2px rgba(255,204,102,0.35)' : undefined,
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

function RequestBanner({ state, lang }: { state: SecurityState; lang: Lang }) {
  const { request, outcome } = state;
  if (!request || !outcome) {
    return <div style={{ ...bannerStyle, opacity: 0.5 }}>{tl(LABELS.noRequest, lang)}</div>;
  }
  const verdict = verdictOf(outcome, lang);
  return (
    <div style={bannerStyle}>
      <span style={methodStyle}>{request.method}</span>
      <span style={pathStyle}>{request.path}</span>
      <span
        style={{
          ...chipStyle,
          color: request.scheme === 'http' ? 'var(--bad)' : undefined,
        }}
      >
        {request.scheme}
      </span>
      <span style={chipStyle}>{request.credential}</span>
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
  return { text: tl(LABELS.inChain, lang), color: 'var(--accent)' };
}

function PrincipalCard({ principal, lang }: { principal: Principal; lang: Lang }) {
  const spoofed = principal.source === 'header';
  return (
    <div style={{ ...principalStyle, borderColor: spoofed ? 'var(--bad)' : 'var(--border)' }}>
      <span style={subjectStyle}>{principal.subject}</span>
      <span style={principalFieldStyle}>
        {tl(LABELS.roles, lang)}:{' '}
        {principal.roles.length > 0 ? principal.roles.join(', ') : tl(LABELS.none, lang)}
      </span>
      <span style={principalFieldStyle}>
        {tl(LABELS.scopes, lang)}:{' '}
        {principal.scopes.length > 0 ? principal.scopes.join(', ') : tl(LABELS.none, lang)}
      </span>
      <span style={{ ...principalSourceStyle, color: spoofed ? 'var(--bad)' : 'var(--good)' }}>
        {tl(spoofed ? LABELS.fromHeader : LABELS.fromToken, lang)}
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
const gateStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '4px 8px',
  minWidth: 78,
  textAlign: 'center',
};
const gateNameStyle: CSSProperties = { fontSize: 10, opacity: 0.7 };
const gateMarkStyle: CSSProperties = { fontSize: 15, fontWeight: 700, lineHeight: 1.1 };
const gateDetailStyle: CSSProperties = { fontSize: 10, opacity: 0.65, fontFamily: 'monospace' };
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
const verdictStyle: CSSProperties = { marginLeft: 'auto', fontSize: 13, fontWeight: 600 };
const reasonStyle: CSSProperties = { width: '100%', fontSize: 11, opacity: 0.75 };
const principalStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 12,
  flexWrap: 'wrap',
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '4px 10px',
  background: 'var(--viz-box)',
};
const subjectStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13, fontWeight: 700 };
const principalFieldStyle: CSSProperties = { fontSize: 11, opacity: 0.8 };
const principalSourceStyle: CSSProperties = { fontSize: 11, marginLeft: 'auto' };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const mutedStyle: CSSProperties = { fontSize: 12, opacity: 0.5, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
