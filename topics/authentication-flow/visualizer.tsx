import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  styleSession: { en: 'server-side sessions', ru: 'серверные сессии' },
  styleToken: { en: 'stateless signed tokens', ru: 'подписанные токены без состояния' },
  storageHashed: { en: 'passwords: salted hash', ru: 'пароли: солёный хеш' },
  storagePlaintext: { en: 'passwords: stored as typed', ru: 'пароли: хранятся как есть' },
  clock: { en: 'minute', ru: 'минута' },
  lifetime: { en: 'credential lifetime', ru: 'срок жизни учётных данных' },
  throttle: { en: 'login throttle', ru: 'ограничение попыток входа' },
  phasesTitle: { en: 'the five phases', ru: 'пять фаз' },
  usersTitle: { en: 'user store (at rest)', ru: 'хранилище пользователей (в покое)' },
  noUsers: { en: 'nobody is registered yet', ru: 'пока никто не зарегистрирован' },
  serverTitle: { en: 'what the server remembers', ru: 'что помнит сервер' },
  clientTitle: { en: 'what the client holds', ru: 'что держит клиент' },
  serverStateless: {
    en: 'nothing — the token carries its own claims',
    ru: 'ничего — токен везёт свои поля с собой',
  },
  clientEmpty: { en: 'nothing yet', ru: 'пока ничего' },
  identityTitle: { en: 'who the server thinks is calling', ru: 'кем сервер считает вызывающего' },
  noIdentity: { en: 'nobody — the caller is anonymous', ru: 'никем — вызывающий анонимен' },
  fromSession: { en: 'looked up in the session store', ru: 'найдено в хранилище сессий' },
  fromToken: { en: 'read from a verified token', ru: 'прочитано из проверенного токена' },
  noAction: { en: 'nothing has happened yet', ru: 'пока ничего не произошло' },
  running: { en: 'in progress…', ru: 'выполняется…' },
  allowed: { en: 'ok', ru: 'ок' },
  denied: { en: 'refused', ru: 'отказ' },
  breach: { en: 'served — and should not have been', ru: 'обслужен — а не должен был' },
  salt: { en: 'salt', ru: 'соль' },
  mfaOn: { en: '2FA', ru: '2FA' },
  failed: { en: 'wrong tries', ru: 'неверных попыток' },
  expires: { en: 'until minute', ru: 'до минуты' },
  runHint: {
    en: 'Run the code to watch a password become a stored hash, a login become a credential, and every request be checked again.',
    ru: 'Запустите код, чтобы увидеть, как пароль превращается в хеш, вход — в учётные данные, а каждый запрос проверяется заново.',
  },
  phases: {
    claim: { en: 'claim', ru: 'заявка' },
    prove: { en: 'prove', ru: 'доказательство' },
    mfa: { en: '2nd factor', ru: '2-й фактор' },
    issue: { en: 'issue', ru: 'выдача' },
    identity: { en: 'identity', ru: 'личность' },
  },
  actions: {
    idle: { en: 'idle', ru: 'ожидание' },
    register: { en: 'register', ru: 'регистрация' },
    'mfa-setup': { en: 'enable 2FA', ru: 'включение 2FA' },
    login: { en: 'login', ru: 'вход' },
    mfa: { en: 'second factor', ru: 'второй фактор' },
    request: { en: 'request', ru: 'запрос' },
    refresh: { en: 'refresh', ru: 'обновление' },
    logout: { en: 'logout', ru: 'выход' },
    leak: { en: 'the store leaks', ru: 'утечка хранилища' },
    clock: { en: 'time passes', ru: 'идёт время' },
  },
  credentialStates: {
    valid: { en: 'valid', ru: 'действительны' },
    expired: { en: 'expired', ru: 'просрочены' },
    pending: { en: 'awaiting 2nd factor', ru: 'ждут 2-го фактора' },
    dropped: { en: 'dropped by the client', ru: 'выброшены клиентом' },
  },
  reasons: {
    throttled: { en: 'too many wrong passwords', ru: 'слишком много неверных паролей' },
    'unknown-user': { en: 'no such user', ru: 'такого пользователя нет' },
    'wrong-password': { en: 'the password does not match', ru: 'пароль не совпадает' },
    'wrong-code': { en: 'the second factor is wrong', ru: 'второй фактор неверен' },
    'no-credential': { en: 'nothing was presented', ru: 'ничего не предъявлено' },
    'mfa-incomplete': { en: 'the login never finished', ru: 'вход не был завершён' },
    'unknown-session': { en: 'no such session on the server', ru: 'такой сессии на сервере нет' },
    'logged-out': { en: 'the session was deleted at logout', ru: 'сессия удалена при выходе' },
    expired: { en: 'past its expiry', ru: 'срок действия истёк' },
    'refresh-expired': { en: 'this login can no longer be renewed', ru: 'этот вход больше нельзя продлить' },
    'revoked-token': { en: 'logged out, and the token still verifies', ru: 'вышли, а токен всё ещё проходит проверку' },
    'stolen-credential': { en: 'presented by somebody else', ru: 'предъявлены кем-то другим' },
  },
  stats: {
    loginsGranted: { en: 'logins', ru: 'входов' },
    loginsRefused: { en: 'logins refused', ru: 'отказов во входе' },
    requests: { en: 'requests', ru: 'запросов' },
    served: { en: 'served', ru: 'обслужено' },
    refused: { en: 'refused', ru: 'отклонено' },
    breaches: { en: 'should not have been served', ru: 'не следовало обслуживать' },
  },
};

type PhaseId = keyof typeof LABELS.phases;
type ActionKind = keyof typeof LABELS.actions;
type Reason = keyof typeof LABELS.reasons;
type CredentialState = keyof typeof LABELS.credentialStates;
type PhaseStatus = 'pending' | 'passed' | 'denied' | 'skipped' | 'breach';

interface User {
  name: string;
  stored: string;
  salt: string;
  secondFactor: boolean;
  failedAttempts: number;
}
interface Credential {
  id: string;
  user: string;
  kind: 'session' | 'token';
  issuedAt: number;
  expiresAt: number;
  state: CredentialState;
}
interface Phase {
  id: PhaseId;
  status: PhaseStatus;
  detail: string;
}
interface Action {
  kind: ActionKind;
  actor: string;
  detail: string;
}
interface Identity {
  user: string;
  source: 'session' | 'token';
  credentialId: string;
}
interface Outcome {
  status: number | null;
  decision: 'idle' | 'pending' | 'allowed' | 'denied' | 'breach';
  reason: Reason | null;
  detail: string;
}
interface Stats {
  loginsGranted: number;
  loginsRefused: number;
  requests: number;
  served: number;
  refused: number;
  breaches: number;
}
interface AuthState {
  credentialStyle: 'session' | 'token';
  passwordStorage: 'hashed' | 'plaintext';
  clock: number;
  accessLifetime: number;
  loginRateLimit: number;
  users: User[];
  serverStore: Credential[];
  clientStore: Credential[];
  action: Action;
  phases: Phase[];
  identity: Identity | null;
  outcome: Outcome;
  stats: Stats;
}

const PHASE_COLORS: Record<PhaseStatus, string> = {
  pending: 'var(--border)',
  passed: 'var(--good)',
  denied: 'var(--bad)',
  skipped: 'var(--border)',
  breach: 'var(--bad)',
};

export default function AuthenticationVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as AuthState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const stateless = state.credentialStyle === 'token';

  return (
    <div style={wrapStyle}>
      <div style={policyRowStyle}>
        <span style={{ ...tagStyle, color: 'var(--accent)' }}>
          {tl(stateless ? LABELS.styleToken : LABELS.styleSession, lang)}
        </span>
        <span style={state.passwordStorage === 'plaintext' ? warnChipStyle : chipStyle}>
          {tl(
            state.passwordStorage === 'plaintext' ? LABELS.storagePlaintext : LABELS.storageHashed,
            lang,
          )}
        </span>
        <span style={{ ...chipStyle, ...(highlight.has('clock') ? highlightChipStyle : {}) }}>
          {tl(LABELS.clock, lang)} {state.clock}
        </span>
        <span style={chipStyle}>
          {tl(LABELS.lifetime, lang)}: {state.accessLifetime}
        </span>
        {state.loginRateLimit > 0 && (
          <span style={chipStyle}>
            {tl(LABELS.throttle, lang)}: {state.loginRateLimit}
          </span>
        )}
      </div>

      <ActionBanner state={state} lang={lang} />

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.phasesTitle, lang)}</div>
        <div style={chainStyle}>
          {state.phases.map((phase, i) => (
            <PhaseBox
              key={phase.id}
              phase={phase}
              lang={lang}
              last={i === state.phases.length - 1}
              highlighted={highlight.has(`phase:${phase.id}`)}
            />
          ))}
        </div>
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.usersTitle, lang)}</div>
        {state.users.length === 0 ? (
          <div style={mutedStyle}>{tl(LABELS.noUsers, lang)}</div>
        ) : (
          <BoxGroup boxes={userBoxes(state, highlight, lang)} />
        )}
      </div>

      <div style={storesStyle}>
        <div style={storeColumnStyle}>
          <div style={sectionLabelStyle}>{tl(LABELS.serverTitle, lang)}</div>
          {state.serverStore.length === 0 ? (
            <div style={stateless ? statelessStyle : mutedStyle}>
              {tl(stateless ? LABELS.serverStateless : LABELS.clientEmpty, lang)}
            </div>
          ) : (
            <BoxGroup boxes={credentialBoxes(state.serverStore, 'srv', highlight, lang)} />
          )}
        </div>
        <div style={storeColumnStyle}>
          <div style={sectionLabelStyle}>{tl(LABELS.clientTitle, lang)}</div>
          {state.clientStore.length === 0 ? (
            <div style={mutedStyle}>{tl(LABELS.clientEmpty, lang)}</div>
          ) : (
            <BoxGroup boxes={credentialBoxes(state.clientStore, 'cli', highlight, lang)} />
          )}
        </div>
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.identityTitle, lang)}</div>
        {state.identity ? (
          <IdentityCard identity={state.identity} outcome={state.outcome} lang={lang} />
        ) : (
          <div style={mutedStyle}>{tl(LABELS.noIdentity, lang)}</div>
        )}
      </div>

      <div style={statsStyle}>
        <Stat label={tl(LABELS.stats.loginsGranted, lang)} value={state.stats.loginsGranted} />
        <Stat label={tl(LABELS.stats.loginsRefused, lang)} value={state.stats.loginsRefused} />
        <Stat label={tl(LABELS.stats.requests, lang)} value={state.stats.requests} />
        <Stat label={tl(LABELS.stats.served, lang)} value={state.stats.served} />
        <Stat label={tl(LABELS.stats.refused, lang)} value={state.stats.refused} />
        <Stat
          label={tl(LABELS.stats.breaches, lang)}
          value={state.stats.breaches}
          color={state.stats.breaches > 0 ? 'var(--bad)' : undefined}
        />
      </div>
    </div>
  );
}

function userBoxes(state: AuthState, highlight: Set<string>, lang: Lang): Box[] {
  return state.users.map((user) => {
    const parts = [user.stored];
    if (user.salt) parts.push(`${tl(LABELS.salt, lang)} ${user.salt}`);
    if (user.secondFactor) parts.push(tl(LABELS.mfaOn, lang));
    if (user.failedAttempts > 0) {
      parts.push(`${tl(LABELS.failed, lang)}: ${user.failedAttempts}`);
    }
    return {
      id: `user-${user.name}`,
      title: user.name,
      subtitle: parts.join(' · '),
      highlighted: highlight.has(`user:${user.name}`),
    };
  });
}

function credentialBoxes(
  credentials: Credential[],
  prefix: string,
  highlight: Set<string>,
  lang: Lang,
): Box[] {
  return credentials.map((credential) => ({
    id: `${prefix}-${credential.id}`,
    title: `${credential.id} → ${credential.user}`,
    subtitle: `${tl(LABELS.credentialStates[credential.state], lang)} · ${tl(
      LABELS.expires,
      lang,
    )} ${credential.expiresAt}`,
    highlighted: highlight.has(`credential:${credential.id}`),
    dim: credential.state === 'expired' || credential.state === 'dropped',
  }));
}

function PhaseBox({
  phase,
  lang,
  last,
  highlighted,
}: {
  phase: Phase;
  lang: Lang;
  last: boolean;
  highlighted: boolean;
}) {
  const color = PHASE_COLORS[phase.status];
  const faded = phase.status === 'pending' || phase.status === 'skipped';
  return (
    <>
      <div
        style={{
          ...phaseStyle,
          borderColor: color,
          opacity: faded ? 0.45 : 1,
          background: highlighted ? 'var(--viz-highlight)' : 'var(--viz-box)',
          boxShadow: highlighted ? '0 0 0 2px rgba(255,204,102,0.35)' : undefined,
        }}
      >
        <div style={phaseNameStyle}>{tl(LABELS.phases[phase.id], lang)}</div>
        <div style={{ ...phaseMarkStyle, color }}>{phaseMark(phase.status)}</div>
        {phase.detail && <div style={phaseDetailStyle}>{phase.detail}</div>}
      </div>
      {!last && <span style={arrowStyle}>→</span>}
    </>
  );
}

function phaseMark(status: PhaseStatus) {
  if (status === 'passed') return '✓';
  if (status === 'denied') return '✗';
  if (status === 'breach') return '!';
  if (status === 'skipped') return '–';
  return '·';
}

function ActionBanner({ state, lang }: { state: AuthState; lang: Lang }) {
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

function IdentityCard({
  identity,
  outcome,
  lang,
}: {
  identity: Identity;
  outcome: Outcome;
  lang: Lang;
}) {
  const wrong = outcome.decision === 'breach';
  return (
    <div style={{ ...identityStyle, borderColor: wrong ? 'var(--bad)' : 'var(--border)' }}>
      <span style={subjectStyle}>{identity.user}</span>
      <span style={identityFieldStyle}>{identity.credentialId}</span>
      <span style={{ ...identitySourceStyle, color: wrong ? 'var(--bad)' : 'var(--good)' }}>
        {tl(identity.source === 'session' ? LABELS.fromSession : LABELS.fromToken, lang)}
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
const chainStyle: CSSProperties = { display: 'flex', alignItems: 'stretch', gap: 4, flexWrap: 'wrap' };
const phaseStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '4px 8px',
  minWidth: 84,
  textAlign: 'center',
};
const phaseNameStyle: CSSProperties = { fontSize: 10, opacity: 0.7 };
const phaseMarkStyle: CSSProperties = { fontSize: 15, fontWeight: 700, lineHeight: 1.1 };
const phaseDetailStyle: CSSProperties = { fontSize: 10, opacity: 0.65, fontFamily: 'monospace' };
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
const storesStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const storeColumnStyle: CSSProperties = { flex: '1 1 220px', minWidth: 200 };
const statelessStyle: CSSProperties = {
  fontSize: 12,
  opacity: 0.75,
  fontStyle: 'italic',
  border: '1px dashed var(--border)',
  borderRadius: 6,
  padding: '6px 10px',
};
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
const identityFieldStyle: CSSProperties = { fontSize: 11, opacity: 0.8, fontFamily: 'monospace' };
const identitySourceStyle: CSSProperties = { fontSize: 11, marginLeft: 'auto' };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const mutedStyle: CSSProperties = { fontSize: 12, opacity: 0.5, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
