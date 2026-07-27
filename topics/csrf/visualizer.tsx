import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to see who built each request, what the browser attached to it, and which defence stopped it.',
    ru: 'Запустите код, чтобы увидеть, кто собрал каждый запрос, что к нему прикрепил браузер и какая защита его остановила.',
  },
  defences: { en: 'defences', ru: 'защита' },
  noDefences: {
    en: 'a legacy cookie session: no SameSite, no token, no origin check',
    ru: 'унаследованная сессия в cookie: ни SameSite, ни токена, ни проверки origin',
  },
  csrfToken: { en: 'synchronizer token required', ru: 'требуется синхронизирующий токен' },
  originCheck: { en: 'Origin header checked', ru: 'проверяется заголовок Origin' },
  postOnly: { en: 'GET is read-only', ru: 'GET только читает' },
  bearer: { en: 'session in an Authorization header', ru: 'сессия в заголовке Authorization' },
  corsReflects: { en: 'CORS reflects any Origin', ru: 'CORS отражает любой Origin' },
  noRequest: { en: 'no request yet', ru: 'запросов пока нет' },
  crossSite: { en: 'cross-site', ru: 'кросс-сайтовый' },
  sameSiteRequest: { en: 'same-site', ru: 'same-site' },
  asked: { en: 'asked for by the user', ru: 'пользователь этого просил' },
  notAsked: { en: 'never asked for', ru: 'никто не просил' },
  causedBy: { en: 'what caused the request', ru: 'что вызвало запрос' },
  received: { en: 'what the server received', ru: 'что получил сервер' },
  sessionAttached: { en: 'session attached', ru: 'сессия прикреплена' },
  sessionAbsent: { en: 'no session', ru: 'сессии нет' },
  tokenPresent: { en: 'CSRF token', ru: 'CSRF-токен' },
  tokenAbsent: { en: 'no CSRF token', ru: 'CSRF-токена нет' },
  neverSent: { en: 'never left the browser', ru: 'из браузера не ушёл' },
  session: { en: 'the credential', ru: 'учётные данные' },
  ambient: { en: 'attached by the browser, automatically', ru: 'прикрепляет браузер, автоматически' },
  notAmbient: { en: 'attached by the application itself', ru: 'прикрепляет само приложение' },
  account: { en: 'the account', ru: 'счёт' },
  balance: { en: 'balance', ru: 'баланс' },
  inAttackersAccount: { en: "in the attacker's account", ru: 'у злоумышленника' },
  pending: { en: 'in flight…', ru: 'в пути…' },
  performedLegit: { en: 'the action the user asked for', ru: 'действие, о котором просил пользователь' },
  performedForged: { en: 'forged action performed', ru: 'поддельное действие выполнено' },
  stopped: { en: 'stopped', ru: 'остановлено' },
  byPreflight: {
    en: 'the browser asked permission first and never sent it',
    ru: 'браузер сначала спросил разрешение и так и не отправил его',
  },
  bySameSite: { en: 'SameSite kept the cookie out', ru: 'SameSite не пустил cookie' },
  byNoCredentials: {
    en: 'nothing identified the user, so the server saw a stranger',
    ru: 'пользователя ничто не опознало, и сервер увидел незнакомца',
  },
  byMethod: { en: 'a GET may not change state here', ru: 'GET здесь не меняет состояние' },
  byOrigin: { en: 'refused on the Origin header', ru: 'отказ по заголовку Origin' },
  byToken: { en: 'no synchronizer token', ru: 'нет синхронизирующего токена' },
  nothingToStop: { en: 'nothing had to stop it', ru: 'останавливать было нечего' },
  statAttempts: { en: 'requests', ru: 'запросов' },
  statReached: { en: 'reached the server', ru: 'дошло до сервера' },
  statChanged: { en: 'changed state', ru: 'изменили состояние' },
  statForged: { en: 'forged', ru: 'подделано' },
  statBlocked: { en: 'blocked', ru: 'заблокировано' },
  deliveries: {
    IMAGE_TAG: { en: 'an <img> tag', ru: 'тег <img>' },
    LINK_CLICK: { en: 'a clicked link', ru: 'клик по ссылке' },
    AUTO_FORM: { en: 'a self-submitting form', ru: 'самоотправляющаяся форма' },
    FETCH_JSON: { en: 'a scripted fetch()', ru: 'скриптовый fetch()' },
    OWN_PAGE_FORM: { en: "the site's own form", ru: 'собственная форма сайта' },
    INJECTED_SCRIPT: { en: 'an injected script', ru: 'внедрённый скрипт' },
  },
};

type DeliveryId = keyof typeof LABELS.deliveries;
type SameSite = 'None' | 'Lax' | 'Strict';

interface Defences {
  sameSite: SameSite;
  csrfToken: boolean;
  originCheck: boolean;
  postOnly: boolean;
  auth: 'cookie' | 'bearer';
  corsReflectsOrigin: boolean;
}
interface Session {
  name: string;
  value: string;
  sameSite: SameSite;
  ambient: boolean;
  token: string | null;
}
interface Request {
  origin: string;
  delivery: DeliveryId;
  method: 'GET' | 'POST';
  target: string;
  body: string | null;
  markup: string;
  crossSite: boolean;
  userInitiated: boolean;
  credentials: boolean;
  token: string | null;
  reached: boolean;
}
interface Outcome {
  stage: 'idle' | 'built' | 'sent' | 'settled';
  authenticated: boolean;
  performed: boolean;
  blockedBy: 'preflight' | 'samesite' | 'no-credentials' | 'method' | 'origin' | 'token' | null;
  responseReadable: boolean;
}
interface Account {
  balance: number;
  stolen: number;
}
interface Stats {
  attempts: number;
  reachedServer: number;
  changed: number;
  forged: number;
  blocked: number;
}
interface CsrfState {
  defences: Defences;
  session: Session;
  request?: Request | null;
  outcome?: Outcome | null;
  account: Account;
  stats: Stats;
}

export default function CsrfVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as CsrfState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const { request, outcome } = state;
  const defenceList = defenceBoxes(state.defences, lang, highlight.has('defences'));

  return (
    <div style={wrapStyle}>
      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.defences, lang)}</div>
        {defenceList.length === 0 ? (
          <div style={{ ...mutedStyle, color: 'var(--bad)' }}>{tl(LABELS.noDefences, lang)}</div>
        ) : (
          <BoxGroup boxes={defenceList} />
        )}
      </div>

      {request && outcome ? (
        <>
          <div style={bannerStyle}>
            <span style={chipStyle}>{request.origin}</span>
            <span style={chipStyle}>
              {tl(request.crossSite ? LABELS.crossSite : LABELS.sameSiteRequest, lang)}
            </span>
            <span style={chipStyle}>{tl(LABELS.deliveries[request.delivery], lang)}</span>
            <span style={chipStyle}>
              {tl(request.userInitiated ? LABELS.asked : LABELS.notAsked, lang)}
            </span>
            <span style={{ ...verdictStyle, color: verdictOf(outcome, request).color }}>
              {tl(verdictOf(outcome, request).label, lang)}
            </span>
            <span style={reasonStyle}>{tl(reasonOf(outcome), lang)}</span>
          </div>

          <div>
            <div style={sectionLabelStyle}>{tl(LABELS.causedBy, lang)}</div>
            <div style={{ ...codeStyle, ...(highlight.has('request') ? codeActiveStyle : {}) }}>
              {request.markup}
            </div>
          </div>

          <div>
            <div style={sectionLabelStyle}>{tl(LABELS.received, lang)}</div>
            {request.reached ? (
              <>
                <div style={codeStyle}>
                  {request.method} {request.target}
                  {request.body ? `  ${request.body}` : ''}
                </div>
                <div style={{ marginTop: 6 }}>
                  <BoxGroup
                    boxes={[
                      {
                        id: 'credentials',
                        title: request.credentials
                          ? tl(LABELS.sessionAttached, lang)
                          : tl(LABELS.sessionAbsent, lang),
                        highlighted: request.credentials && highlight.has('session'),
                        dim: !request.credentials,
                      },
                      {
                        id: 'token',
                        title: request.token ?? tl(LABELS.tokenAbsent, lang),
                        subtitle: request.token ? tl(LABELS.tokenPresent, lang) : undefined,
                        highlighted: Boolean(request.token) && highlight.has('session'),
                        dim: !request.token,
                      },
                    ]}
                  />
                </div>
              </>
            ) : (
              <div style={{ ...mutedStyle }}>{tl(LABELS.neverSent, lang)}</div>
            )}
          </div>
        </>
      ) : (
        <div style={{ ...bannerStyle, opacity: 0.5 }}>{tl(LABELS.noRequest, lang)}</div>
      )}

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.session, lang)}</div>
        <BoxGroup
          boxes={[
            {
              id: 'session',
              title: state.session.ambient
                ? `${state.session.name}=${state.session.value}; SameSite=${state.session.sameSite}`
                : `Authorization: Bearer ${state.session.value}`,
              subtitle: tl(state.session.ambient ? LABELS.ambient : LABELS.notAmbient, lang),
              highlighted: highlight.has('session'),
            },
          ]}
        />
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.account, lang)}</div>
        <BoxGroup
          boxes={[
            {
              id: 'balance',
              title: String(state.account.balance),
              subtitle: tl(LABELS.balance, lang),
              highlighted: highlight.has('account'),
            },
            {
              id: 'stolen',
              title: String(state.account.stolen),
              subtitle: tl(LABELS.inAttackersAccount, lang),
              highlighted: state.account.stolen > 0 && highlight.has('account'),
            },
          ]}
        />
      </div>

      <div style={statsStyle}>
        <Stat label={tl(LABELS.statAttempts, lang)} value={state.stats.attempts} />
        <Stat label={tl(LABELS.statReached, lang)} value={state.stats.reachedServer} />
        <Stat label={tl(LABELS.statChanged, lang)} value={state.stats.changed} />
        <Stat
          label={tl(LABELS.statForged, lang)}
          value={state.stats.forged}
          color={state.stats.forged > 0 ? 'var(--bad)' : undefined}
        />
        <Stat
          label={tl(LABELS.statBlocked, lang)}
          value={state.stats.blocked}
          color={state.stats.blocked > 0 ? 'var(--good)' : undefined}
        />
      </div>
    </div>
  );
}

function defenceBoxes(defences: Defences, lang: Lang, highlighted: boolean): Box[] {
  const boxes: Box[] = [];
  if (defences.sameSite !== 'None') {
    boxes.push({ id: 'samesite', title: `SameSite=${defences.sameSite}`, highlighted });
  }
  if (defences.csrfToken) {
    boxes.push({ id: 'token', title: tl(LABELS.csrfToken, lang), highlighted });
  }
  if (defences.originCheck) {
    boxes.push({ id: 'origin', title: tl(LABELS.originCheck, lang), highlighted });
  }
  if (defences.postOnly) {
    boxes.push({ id: 'postonly', title: tl(LABELS.postOnly, lang), highlighted });
  }
  if (defences.auth === 'bearer') {
    boxes.push({ id: 'bearer', title: tl(LABELS.bearer, lang), highlighted });
  }
  if (defences.corsReflectsOrigin) {
    boxes.push({ id: 'cors', title: tl(LABELS.corsReflects, lang), dim: true });
  }
  return boxes;
}

function verdictOf(outcome: Outcome, request: Request) {
  if (outcome.stage !== 'settled') {
    return { label: LABELS.pending, color: 'var(--accent)' };
  }
  if (outcome.performed) {
    return request.userInitiated
      ? { label: LABELS.performedLegit, color: 'var(--good)' }
      : { label: LABELS.performedForged, color: 'var(--bad)' };
  }
  return { label: LABELS.stopped, color: 'var(--good)' };
}

function reasonOf(outcome: Outcome) {
  switch (outcome.blockedBy) {
    case 'preflight':
      return LABELS.byPreflight;
    case 'samesite':
      return LABELS.bySameSite;
    case 'no-credentials':
      return LABELS.byNoCredentials;
    case 'method':
      return LABELS.byMethod;
    case 'origin':
      return LABELS.byOrigin;
    case 'token':
      return LABELS.byToken;
    default:
      return outcome.stage === 'settled' ? LABELS.nothingToStop : LABELS.pending;
  }
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
const chipStyle: CSSProperties = {
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const verdictStyle: CSSProperties = { marginLeft: 'auto', fontSize: 13, fontWeight: 600 };
const reasonStyle: CSSProperties = { width: '100%', fontSize: 11, opacity: 0.75 };
const codeStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 12,
  padding: '6px 10px',
  borderRadius: 6,
  border: '1px solid var(--border)',
  background: 'var(--viz-box)',
  wordBreak: 'break-all',
};
const codeActiveStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
};
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const mutedStyle: CSSProperties = { fontSize: 12, opacity: 0.5, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
