import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  protocolOauth: { en: 'OAuth 2.0 — delegated access', ru: 'OAuth 2.0 — делегированный доступ' },
  protocolOidc: { en: 'OpenID Connect — access + identity', ru: 'OpenID Connect — доступ и личность' },
  confidential: { en: 'confidential client', ru: 'конфиденциальный клиент' },
  publicClient: { en: 'public client (no secret)', ru: 'публичный клиент (без секрета)' },
  pkceOn: { en: 'PKCE on', ru: 'PKCE включён' },
  pkceOff: { en: 'PKCE off', ru: 'PKCE выключен' },
  stateOn: { en: 'state checked', ru: 'state проверяется' },
  stateOff: { en: 'no state check', ru: 'state не проверяется' },
  clock: { en: 'minute', ru: 'минута' },
  lifetime: { en: 'access token lifetime', ru: 'срок жизни access-токена' },
  stepsTitle: { en: 'the authorization code flow', ru: 'поток authorization code' },
  partiesTitle: { en: 'who holds what', ru: 'у кого что есть' },
  scopesTitle: { en: 'scopes', ru: 'scope' },
  noScopes: { en: 'nothing has been asked for yet', ru: 'пока ничего не запрошено' },
  identityTitle: { en: 'who the client thinks the user is', ru: 'кем клиент считает пользователя' },
  noIdentity: { en: 'nobody — no basis for an opinion yet', ru: 'никем — оснований для мнения пока нет' },
  fromIdToken: { en: 'read from a validated id_token', ru: 'прочитано из проверенного id_token' },
  fromAccessToken: { en: 'guessed from an access token', ru: 'угадано по access-токену' },
  noMessage: { en: 'nothing is being sent', ru: 'ничего не отправляется' },
  running: { en: 'in progress…', ru: 'выполняется…' },
  allowed: { en: 'ok', ru: 'ок' },
  denied: { en: 'refused', ru: 'отказ' },
  risky: { en: 'works — and should not be built this way', ru: 'работает — и так строить не надо' },
  breach: { en: 'worked — and should not have', ru: 'сработало — а не должно было' },
  granted: { en: 'granted', ru: 'выдан' },
  notGranted: { en: 'refused', ru: 'не выдан' },
  runHint: {
    en: 'Run the code to watch a code travel through the browser, become tokens on the back channel, and open exactly one API.',
    ru: 'Запустите код, чтобы увидеть, как код проходит через браузер, превращается в токены на бэк-канале и открывает ровно один API.',
  },
  channels: {
    front: {
      en: 'front channel — a browser redirect anyone can read',
      ru: 'фронт-канал — редирект браузера, который может прочитать любой',
    },
    back: {
      en: 'back channel — direct server to server',
      ru: 'бэк-канал — напрямую сервер к серверу',
    },
    none: { en: 'local — nothing leaves the party', ru: 'локально — ничего не покидает участника' },
  },
  steps: {
    authorize: { en: 'authorize', ru: 'запрос' },
    authenticate: { en: 'authenticate', ru: 'вход' },
    consent: { en: 'consent', ru: 'согласие' },
    code: { en: 'code', ru: 'код' },
    exchange: { en: 'exchange', ru: 'обмен' },
    call: { en: 'call API', ru: 'вызов API' },
  },
  roles: {
    'resource-owner': { en: 'resource owner', ru: 'владелец ресурса' },
    client: { en: 'client', ru: 'клиент' },
    'authorization-server': { en: 'authorization server', ru: 'сервер авторизации' },
    'resource-server': { en: 'resource server', ru: 'сервер ресурсов' },
    attacker: { en: 'attacker', ru: 'злоумышленник' },
  },
  reasons: {
    'consent-denied': { en: 'the user said no', ru: 'пользователь отказал' },
    'state-mismatch': { en: 'this callback is not ours', ru: 'этот callback не наш' },
    'code-injected': { en: "somebody else's code was delivered", ru: 'подсунули чужой код' },
    'code-reused': { en: 'this code was already redeemed', ru: 'этот код уже обменяли' },
    'code-expired': { en: 'the code is past its minute', ru: 'минута кода истекла' },
    'code-stolen': { en: 'the code was read off the front channel', ru: 'код прочитали из фронт-канала' },
    'pkce-blocked': { en: 'no code_verifier to show', ru: 'предъявить code_verifier нечем' },
    'client-auth-blocked': { en: 'no client_secret to show', ru: 'предъявить client_secret нечем' },
    'stolen-code-redeemed': { en: 'the thief got the tokens', ru: 'токены достались вору' },
    'no-id-token': { en: 'nothing here says who the user is', ru: 'здесь ничто не говорит, кто пользователь' },
    'token-not-identity': { en: 'an access token is not proof of identity', ru: 'access-токен не доказывает личность' },
    'no-token': { en: 'nothing was presented', ru: 'ничего не предъявлено' },
    expired: { en: 'past its expiry', ru: 'срок действия истёк' },
    'insufficient-scope': { en: 'this scope was never granted', ru: 'этот scope не выдавали' },
    'no-refresh-token': { en: 'this grant never had one', ru: 'в этом grant его и не было' },
    'refresh-expired': { en: 'the renewal window has closed', ru: 'окно обновления закрылось' },
    'password-seen-by-client': { en: 'the app now holds the password', ru: 'теперь пароль у приложения' },
    'token-in-url': { en: 'the token came back in the URL', ru: 'токен вернулся в URL' },
  },
  stats: {
    tokensIssued: { en: 'tokens issued', ru: 'выдано токенов' },
    apiServed: { en: 'API calls served', ru: 'вызовов API обслужено' },
    apiRefused: { en: 'API calls refused', ru: 'вызовов API отклонено' },
    warnings: { en: 'risky moves', ru: 'рискованных ходов' },
    breaches: { en: 'should not have worked', ru: 'не должно было сработать' },
  },
};

type StepId = keyof typeof LABELS.steps;
type RoleId = keyof typeof LABELS.roles;
type ChannelId = keyof typeof LABELS.channels;
type Reason = keyof typeof LABELS.reasons;
type StepStatus = 'pending' | 'passed' | 'partial' | 'denied' | 'skipped' | 'breach';

interface Holding {
  name: string;
  value: string;
  kind: 'secret' | 'token' | 'code' | 'param' | 'key' | 'identity';
}
interface Party {
  id: 'user' | 'client' | 'provider' | 'api' | 'attacker';
  role: RoleId;
  label: string;
  holdings: Holding[];
}
interface Param {
  name: string;
  value: string;
}
interface Message {
  from: string;
  to: string;
  channel: ChannelId;
  label: string;
  params: Param[];
}
interface Step {
  id: StepId;
  status: StepStatus;
  detail: string;
}
interface Scope {
  name: string;
  granted: boolean;
}
interface Identity {
  subject: string;
  source: 'id_token' | 'access_token';
  verified: boolean;
}
interface Outcome {
  status: number | null;
  decision: 'idle' | 'pending' | 'allowed' | 'denied' | 'risky' | 'breach';
  reason: Reason | null;
  detail: string;
}
interface Stats {
  tokensIssued: number;
  apiServed: number;
  apiRefused: number;
  warnings: number;
  breaches: number;
}
interface OAuthState {
  protocol: 'oauth2' | 'oidc';
  clientType: 'confidential' | 'public';
  pkce: boolean;
  stateCheck: boolean;
  clock: number;
  accessLifetime: number;
  parties: Party[];
  message: Message;
  steps: Step[];
  scopes: Scope[];
  identity: Identity | null;
  outcome: Outcome;
  stats: Stats;
}

const STEP_COLORS: Record<StepStatus, string> = {
  pending: 'var(--border)',
  passed: 'var(--good)',
  partial: 'var(--accent)',
  denied: 'var(--bad)',
  skipped: 'var(--border)',
  breach: 'var(--bad)',
};

const CHANNEL_COLORS: Record<ChannelId, string> = {
  front: 'var(--accent)',
  back: 'var(--good)',
  none: 'var(--border)',
};

export default function OAuthVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as OAuthState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  return (
    <div style={wrapStyle}>
      <div style={policyRowStyle}>
        <span style={{ ...tagStyle, color: 'var(--accent)' }}>
          {tl(state.protocol === 'oidc' ? LABELS.protocolOidc : LABELS.protocolOauth, lang)}
        </span>
        <span style={chipStyle}>
          {tl(state.clientType === 'public' ? LABELS.publicClient : LABELS.confidential, lang)}
        </span>
        <span style={state.pkce ? chipStyle : warnChipStyle}>
          {tl(state.pkce ? LABELS.pkceOn : LABELS.pkceOff, lang)}
        </span>
        <span style={state.stateCheck ? chipStyle : warnChipStyle}>
          {tl(state.stateCheck ? LABELS.stateOn : LABELS.stateOff, lang)}
        </span>
        <span style={{ ...chipStyle, ...(highlight.has('clock') ? highlightChipStyle : {}) }}>
          {tl(LABELS.clock, lang)} {state.clock}
        </span>
        <span style={chipStyle}>
          {tl(LABELS.lifetime, lang)}: {state.accessLifetime}
        </span>
      </div>

      <MessageBanner state={state} lang={lang} />

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.stepsTitle, lang)}</div>
        <div style={chainStyle}>
          {state.steps.map((step, i) => (
            <StepBox
              key={step.id}
              step={step}
              lang={lang}
              last={i === state.steps.length - 1}
              highlighted={highlight.has(`step:${step.id}`)}
            />
          ))}
        </div>
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.partiesTitle, lang)}</div>
        <div style={partiesStyle}>
          {state.parties.map((party) => (
            <PartyCard
              key={party.id}
              party={party}
              lang={lang}
              highlight={highlight}
              message={state.message}
            />
          ))}
        </div>
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.scopesTitle, lang)}</div>
        {state.scopes.length === 0 ? (
          <div style={mutedStyle}>{tl(LABELS.noScopes, lang)}</div>
        ) : (
          <div style={policyRowStyle}>
            {state.scopes.map((scope) => (
              <span key={scope.name} style={scope.granted ? grantedChipStyle : refusedChipStyle}>
                {scope.name} · {tl(scope.granted ? LABELS.granted : LABELS.notGranted, lang)}
              </span>
            ))}
          </div>
        )}
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.identityTitle, lang)}</div>
        {state.identity ? (
          <IdentityCard identity={state.identity} lang={lang} />
        ) : (
          <div style={mutedStyle}>{tl(LABELS.noIdentity, lang)}</div>
        )}
      </div>

      <div style={statsStyle}>
        <Stat label={tl(LABELS.stats.tokensIssued, lang)} value={state.stats.tokensIssued} />
        <Stat label={tl(LABELS.stats.apiServed, lang)} value={state.stats.apiServed} />
        <Stat label={tl(LABELS.stats.apiRefused, lang)} value={state.stats.apiRefused} />
        <Stat
          label={tl(LABELS.stats.warnings, lang)}
          value={state.stats.warnings}
          color={state.stats.warnings > 0 ? 'var(--accent)' : undefined}
        />
        <Stat
          label={tl(LABELS.stats.breaches, lang)}
          value={state.stats.breaches}
          color={state.stats.breaches > 0 ? 'var(--bad)' : undefined}
        />
      </div>
    </div>
  );
}

function MessageBanner({ state, lang }: { state: OAuthState; lang: Lang }) {
  const { message, outcome } = state;
  if (!message.label) {
    return <div style={{ ...bannerStyle, opacity: 0.5 }}>{tl(LABELS.noMessage, lang)}</div>;
  }
  const verdict = verdictOf(outcome, lang);
  const partyName = (id: string) =>
    state.parties.find((party) => party.id === id)?.label ?? id;
  return (
    <div style={{ ...bannerStyle, borderColor: CHANNEL_COLORS[message.channel] }}>
      <div style={bannerHeadStyle}>
        {message.from && (
          <span style={hopStyle}>
            {partyName(message.from)} <span style={arrowStyle}>→</span> {partyName(message.to)}
          </span>
        )}
        <span style={{ ...channelStyle, color: CHANNEL_COLORS[message.channel] }}>
          {tl(LABELS.channels[message.channel], lang)}
        </span>
        <span style={{ ...verdictStyle, color: verdict.color }}>
          {outcome.status !== null ? `${outcome.status} · ` : ''}
          {verdict.text}
        </span>
      </div>
      <div style={requestLineStyle}>{message.label}</div>
      {message.params.length > 0 && (
        <div style={paramsStyle}>
          {message.params.map((param) => (
            <span key={param.name} style={paramStyle}>
              <span style={paramNameStyle}>{param.name}</span>={param.value}
            </span>
          ))}
        </div>
      )}
      {outcome.reason && (
        <div style={reasonStyle}>
          {tl(LABELS.reasons[outcome.reason], lang)}
          {outcome.detail ? ` (${outcome.detail})` : ''}
        </div>
      )}
    </div>
  );
}

function verdictOf(outcome: Outcome, lang: Lang) {
  if (outcome.decision === 'allowed') return { text: tl(LABELS.allowed, lang), color: 'var(--good)' };
  if (outcome.decision === 'denied') return { text: tl(LABELS.denied, lang), color: 'var(--bad)' };
  if (outcome.decision === 'risky') return { text: tl(LABELS.risky, lang), color: 'var(--accent)' };
  if (outcome.decision === 'breach') return { text: tl(LABELS.breach, lang), color: 'var(--bad)' };
  return { text: tl(LABELS.running, lang), color: 'var(--accent)' };
}

function StepBox({
  step,
  lang,
  last,
  highlighted,
}: {
  step: Step;
  lang: Lang;
  last: boolean;
  highlighted: boolean;
}) {
  const color = STEP_COLORS[step.status];
  const faded = step.status === 'pending' || step.status === 'skipped';
  return (
    <>
      <div
        style={{
          ...stepStyle,
          borderColor: color,
          opacity: faded ? 0.45 : 1,
          background: highlighted ? 'var(--viz-highlight)' : 'var(--viz-box)',
          boxShadow: highlighted ? '0 0 0 2px rgba(255,204,102,0.35)' : undefined,
        }}
      >
        <div style={stepNameStyle}>{tl(LABELS.steps[step.id], lang)}</div>
        <div style={{ ...stepMarkStyle, color }}>{stepMark(step.status)}</div>
        {step.detail && <div style={stepDetailStyle}>{step.detail}</div>}
      </div>
      {!last && <span style={chainArrowStyle}>→</span>}
    </>
  );
}

function stepMark(status: StepStatus) {
  if (status === 'passed') return '✓';
  if (status === 'partial') return '±';
  if (status === 'denied') return '✗';
  if (status === 'breach') return '!';
  if (status === 'skipped') return '–';
  return '·';
}

function PartyCard({
  party,
  lang,
  highlight,
  message,
}: {
  party: Party;
  lang: Lang;
  highlight: Set<string>;
  message: Message;
}) {
  const active =
    highlight.has(`party:${party.id}`) || message.from === party.id || message.to === party.id;
  return (
    <div
      style={{
        ...partyStyle,
        borderColor: active ? 'var(--viz-active)' : 'var(--border)',
        opacity: active ? 1 : 0.75,
      }}
    >
      <div style={partyHeadStyle}>
        <span style={partyNameStyle}>{party.label}</span>
        <span style={partyRoleStyle}>{tl(LABELS.roles[party.role], lang)}</span>
      </div>
      {party.holdings.length === 0 ? (
        <div style={mutedStyle}>—</div>
      ) : (
        <BoxGroup boxes={holdingBoxes(party, highlight)} />
      )}
    </div>
  );
}

function holdingBoxes(party: Party, highlight: Set<string>): Box[] {
  return party.holdings.map((holding) => ({
    id: `${party.id}-${holding.name}`,
    title: holding.name,
    subtitle: holding.value,
    highlighted:
      highlight.has(`token:${holding.value}`) || highlight.has(`code:${holding.value}`),
    dim: holding.kind === 'secret' && holding.value === 'unknown',
  }));
}

function IdentityCard({ identity, lang }: { identity: Identity; lang: Lang }) {
  return (
    <div
      style={{
        ...identityStyle,
        borderColor: identity.verified ? 'var(--border)' : 'var(--bad)',
      }}
    >
      <span style={subjectStyle}>{identity.subject}</span>
      <span
        style={{
          ...identitySourceStyle,
          color: identity.verified ? 'var(--good)' : 'var(--bad)',
        }}
      >
        {tl(identity.source === 'id_token' ? LABELS.fromIdToken : LABELS.fromAccessToken, lang)}
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
const grantedChipStyle: CSSProperties = {
  ...chipStyle,
  fontFamily: 'monospace',
  border: '1px solid var(--good)',
};
const refusedChipStyle: CSSProperties = {
  ...chipStyle,
  fontFamily: 'monospace',
  opacity: 0.6,
  border: '1px dashed var(--border)',
};
const highlightChipStyle: CSSProperties = {
  background: 'var(--viz-highlight)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.35)',
};
const bannerStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 4,
  padding: '8px 12px',
  borderRadius: 8,
  background: 'var(--viz-box)',
  border: '1px solid var(--viz-active)',
};
const bannerHeadStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 10,
  flexWrap: 'wrap',
};
const hopStyle: CSSProperties = { fontSize: 13, fontWeight: 700 };
const arrowStyle: CSSProperties = { opacity: 0.5 };
const channelStyle: CSSProperties = { fontSize: 11 };
const verdictStyle: CSSProperties = { marginLeft: 'auto', fontSize: 12, fontWeight: 600 };
const requestLineStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 12, opacity: 0.85 };
const paramsStyle: CSSProperties = { display: 'flex', gap: 6, flexWrap: 'wrap' };
const paramStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 10,
  padding: '1px 5px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const paramNameStyle: CSSProperties = { opacity: 0.6 };
const reasonStyle: CSSProperties = { fontSize: 11, opacity: 0.75 };
const chainStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'stretch',
  gap: 4,
  flexWrap: 'wrap',
};
const stepStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '4px 8px',
  minWidth: 84,
  textAlign: 'center',
};
const stepNameStyle: CSSProperties = { fontSize: 10, opacity: 0.7 };
const stepMarkStyle: CSSProperties = { fontSize: 15, fontWeight: 700, lineHeight: 1.1 };
const stepDetailStyle: CSSProperties = { fontSize: 10, opacity: 0.65, fontFamily: 'monospace' };
const chainArrowStyle: CSSProperties = { alignSelf: 'center', opacity: 0.4, fontSize: 13 };
const partiesStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 8 };
const partyStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '6px 10px',
  display: 'flex',
  flexDirection: 'column',
  gap: 6,
};
const partyHeadStyle: CSSProperties = { display: 'flex', alignItems: 'baseline', gap: 10 };
const partyNameStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13, fontWeight: 700 };
const partyRoleStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const identityStyle: CSSProperties = {
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
const identitySourceStyle: CSSProperties = { fontSize: 11, marginLeft: 'auto' };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const mutedStyle: CSSProperties = { fontSize: 12, opacity: 0.5, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
