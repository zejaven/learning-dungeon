import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to watch a certificate be checked, a key be agreed, and an HTTP request turn into ciphertext.',
    ru: 'Запустите код, чтобы увидеть, как сертификат проверяют, о ключе договариваются, а HTTP-запрос превращается в шифротекст.',
  },
  day: { en: 'day', ru: 'день' },
  requested: { en: 'address bar', ru: 'адресная строка' },
  trustStoreTitle: { en: 'trust store (root CAs)', ru: 'хранилище доверия (корневые CA)' },
  chainTitle: { en: 'certificate chain', ru: 'цепочка сертификатов' },
  chainEmpty: { en: 'no certificate seen yet', ru: 'сертификат ещё не предъявлен' },
  handshakeTitle: { en: 'handshake', ru: 'рукопожатие' },
  checksTitle: { en: 'what the client checks', ru: 'что проверяет клиент' },
  cryptoTitle: { en: 'cryptography in use', ru: 'используемая криптография' },
  recordsTitle: { en: 'on the wire', ru: 'на проводе' },
  recordsEmpty: { en: 'nothing sent yet', ru: 'пока ничего не отправлено' },
  attackerTitle: { en: 'the party on the path', ru: 'тот, кто на пути' },
  preinstalled: { en: 'preinstalled', ru: 'предустановлен' },
  added: { en: 'installed here', ru: 'установлен здесь' },
  notSent: { en: 'not sent - already on the client', ru: 'не присылался — уже на клиенте' },
  selfSigned: { en: 'self-signed', ru: 'самоподписанный' },
  revoked: { en: 'revoked', ru: 'отозван' },
  issuedBy: { en: 'signed by', ru: 'подписан' },
  validDays: { en: 'valid', ru: 'действителен' },
  privateKeyHeld: { en: 'private key held', ru: 'приватный ключ есть' },
  privateKeyMissing: { en: 'no private key - a copy', ru: 'приватного ключа нет — это копия' },
  protocol: { en: 'protocol', ru: 'протокол' },
  keyExchange: { en: 'key agreement', ru: 'согласование ключа' },
  authentication: { en: 'authentication', ru: 'аутентификация' },
  cipher: { en: 'record cipher', ru: 'шифр записей' },
  sessionKey: { en: 'session key', ru: 'ключ сессии' },
  noSessionKey: { en: 'not agreed', ru: 'не согласован' },
  forwardSecrecyOn: { en: 'forward secrecy', ru: 'прямая секретность' },
  forwardSecrecyOff: { en: 'no forward secrecy', ru: 'без прямой секретности' },
  plaintext: { en: 'plaintext', ru: 'открытый текст' },
  ciphertext: { en: 'on the wire', ru: 'на проводе' },
  tag: { en: 'tag', ru: 'тег' },
  broken: { en: 'tag check failed', ru: 'проверка тега не прошла' },
  readable: { en: 'readable by a third party', ru: 'читается третьей стороной' },
  recorded: { en: 'records stored', ru: 'записей сохранено' },
  hasKey: { en: 'holds the server private key', ru: 'владеет приватным ключом сервера' },
  canRead: { en: 'can read the recording', ru: 'может прочитать запись' },
  rootInstalled: { en: 'root installed on the client', ru: 'корень установлен на клиенте' },
  idle: { en: 'nothing has happened yet', ru: 'пока ничего не произошло' },
  running: { en: 'in progress…', ru: 'выполняется…' },
  secure: { en: 'secure', ru: 'безопасно' },
  refused: { en: 'refused', ru: 'отказ' },
  breach: { en: 'looks secure - and is not', ru: 'выглядит безопасно — и таковым не является' },
  exposed: { en: 'readable on the wire', ru: 'читается на проводе' },
  checks: {
    chain: { en: 'chain', ru: 'цепочка' },
    hostname: { en: 'hostname', ru: 'имя хоста' },
    validity: { en: 'dates', ru: 'даты' },
    revocation: { en: 'revocation', ru: 'отзыв' },
    possession: { en: 'private key', ru: 'приватный ключ' },
  },
  phases: {
    hello: { en: 'ClientHello', ru: 'ClientHello' },
    certificate: { en: 'Certificate', ru: 'Certificate' },
    validate: { en: 'validate', ru: 'проверка' },
    keys: { en: 'key agreement', ru: 'согласование ключа' },
    finished: { en: 'Finished', ru: 'Finished' },
  },
  roles: {
    leaf: { en: 'leaf', ru: 'конечный' },
    intermediate: { en: 'intermediate', ru: 'промежуточный' },
    root: { en: 'root', ru: 'корневой' },
  },
  reasons: {
    'incomplete-chain': {
      en: 'the intermediate was not sent',
      ru: 'промежуточный сертификат не отправлен',
    },
    'untrusted-issuer': {
      en: 'the chain ends outside the trust store',
      ru: 'цепочка заканчивается вне хранилища доверия',
    },
    'hostname-mismatch': {
      en: 'issued for a different name',
      ru: 'выпущен на другое имя',
    },
    expired: { en: 'outside its validity window', ru: 'вне окна действительности' },
    revoked: { en: 'withdrawn by the CA', ru: 'отозван центром сертификации' },
    'no-private-key': {
      en: 'could not sign the transcript',
      ru: 'не смог подписать стенограмму',
    },
    'rogue-root': {
      en: 'the trusted root belongs to the interceptor',
      ru: 'доверенный корень принадлежит перехватчику',
    },
    tampered: { en: 'a byte was changed in flight', ru: 'байт изменили на лету' },
    plaintext: { en: 'no TLS at all', ru: 'TLS нет вовсе' },
    'no-session': { en: 'no established session', ru: 'установленной сессии нет' },
    'no-forward-secrecy': {
      en: 'the session secret was locked to a long-lived key',
      ru: 'секрет сессии был заперт долгоживущим ключом',
    },
  },
  stats: {
    handshakes: { en: 'handshakes', ru: 'рукопожатий' },
    refused: { en: 'refused', ru: 'отклонено' },
    records: { en: 'records encrypted', ru: 'зашифровано записей' },
    bytesEncrypted: { en: 'bytes protected', ru: 'защищено байт' },
    breaches: { en: 'secure-looking and wrong', ru: 'выглядели безопасными и не были' },
  },
};

type CheckId = keyof typeof LABELS.checks;
type PhaseId = keyof typeof LABELS.phases;
type RoleId = keyof typeof LABELS.roles;
type Reason = keyof typeof LABELS.reasons;
type StepStatus = 'pending' | 'passed' | 'failed';

interface Root {
  name: string;
  source: 'preinstalled' | 'added';
}
interface Cert {
  role: RoleId;
  subject: string;
  names: string[];
  issuer: string;
  notBefore: number;
  notAfter: number;
  keyType: string;
  selfSigned: boolean;
  revoked: boolean;
  sent: boolean;
  trusted: boolean;
}
interface Phase {
  id: PhaseId;
  status: StepStatus;
  detail: string;
}
interface Check {
  id: CheckId;
  status: StepStatus;
  detail: string;
}
interface Crypto {
  protocol: string;
  keyExchange: string;
  authentication: string;
  cipher: string;
  sessionKey: string | null;
  forwardSecrecy: boolean;
  established: boolean;
}
interface WireRecord {
  seq: number;
  plaintext: string;
  ciphertext: string;
  tag: string;
  intact: boolean;
  exposed: boolean;
}
interface Attacker {
  recorded: number;
  hasPrivateKey: boolean;
  canRead: boolean;
  rootInstalled: boolean;
}
interface Outcome {
  decision: 'idle' | 'pending' | 'secure' | 'refused' | 'breach' | 'exposed';
  reason: Reason | null;
  detail: string;
}
interface Stats {
  handshakes: number;
  refused: number;
  records: number;
  bytesEncrypted: number;
  breaches: number;
}
interface TlsState {
  host: string;
  clock: number;
  trustStore: Root[];
  chain: Cert[];
  privateKeyHeld: boolean;
  handshake: Phase[];
  checks: Check[];
  crypto: Crypto;
  records: WireRecord[];
  attacker: Attacker;
  outcome: Outcome;
  stats: Stats;
}

const STATUS_COLORS: Record<StepStatus, string> = {
  pending: 'var(--border)',
  passed: 'var(--good)',
  failed: 'var(--bad)',
};

export default function TlsVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as TlsState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }
  const highlight = new Set(event?.highlight ?? []);

  return (
    <div style={wrapStyle}>
      <div style={policyRowStyle}>
        {state.host && (
          <span style={{ ...tagStyle, color: 'var(--accent)' }}>
            {tl(LABELS.requested, lang)}: {state.host}
          </span>
        )}
        <span style={{ ...chipStyle, ...(highlight.has('clock') ? highlightChipStyle : {}) }}>
          {tl(LABELS.day, lang)} {state.clock}
        </span>
        <span style={chipStyle}>{state.crypto.protocol}</span>
        <span style={state.crypto.forwardSecrecy ? chipStyle : warnChipStyle}>
          {tl(
            state.crypto.forwardSecrecy ? LABELS.forwardSecrecyOn : LABELS.forwardSecrecyOff,
            lang,
          )}
        </span>
      </div>

      <OutcomeBanner outcome={state.outcome} lang={lang} />

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.trustStoreTitle, lang)}</div>
        <BoxGroup boxes={rootBoxes(state.trustStore, highlight, lang)} />
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.chainTitle, lang)}</div>
        {state.chain.length === 0 ? (
          <div style={mutedStyle}>{tl(LABELS.chainEmpty, lang)}</div>
        ) : (
          <>
            <LinkedNodes nodes={chainNodes(state.chain, highlight, lang)} />
            <CertCard cert={state.chain[0]} held={state.privateKeyHeld} lang={lang} />
          </>
        )}
      </div>

      <div style={columnsStyle}>
        <div style={columnStyle}>
          <div style={sectionLabelStyle}>{tl(LABELS.handshakeTitle, lang)}</div>
          <div style={chainStyle}>
            {state.handshake.map((phase, i) => (
              <StepBox
                key={phase.id}
                name={tl(LABELS.phases[phase.id], lang)}
                status={phase.status}
                detail={phase.detail}
                last={i === state.handshake.length - 1}
                highlighted={highlight.has(`phase:${phase.id}`)}
              />
            ))}
          </div>
        </div>
        <div style={columnStyle}>
          <div style={sectionLabelStyle}>{tl(LABELS.checksTitle, lang)}</div>
          <div style={chainStyle}>
            {state.checks.map((check, i) => (
              <StepBox
                key={check.id}
                name={tl(LABELS.checks[check.id], lang)}
                status={check.status}
                detail={check.detail}
                last={i === state.checks.length - 1}
                highlighted={highlight.has(`check:${check.id}`)}
              />
            ))}
          </div>
        </div>
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.cryptoTitle, lang)}</div>
        <div
          style={{
            ...cardStyle,
            background: highlight.has('crypto') ? 'var(--viz-highlight)' : 'var(--viz-box)',
          }}
        >
          <Field label={tl(LABELS.authentication, lang)} value={state.crypto.authentication || '—'} />
          <Field label={tl(LABELS.keyExchange, lang)} value={state.crypto.keyExchange} />
          <Field label={tl(LABELS.cipher, lang)} value={state.crypto.cipher} />
          <Field
            label={tl(LABELS.sessionKey, lang)}
            value={state.crypto.sessionKey ?? tl(LABELS.noSessionKey, lang)}
            muted={!state.crypto.sessionKey}
          />
        </div>
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.recordsTitle, lang)}</div>
        {state.records.length === 0 ? (
          <div style={mutedStyle}>{tl(LABELS.recordsEmpty, lang)}</div>
        ) : (
          <div style={recordsStyle}>
            {state.records.map((rec) => (
              <RecordRow
                key={rec.seq}
                rec={rec}
                lang={lang}
                highlighted={highlight.has('records')}
              />
            ))}
          </div>
        )}
      </div>

      {(state.attacker.recorded > 0 ||
        state.attacker.hasPrivateKey ||
        state.attacker.rootInstalled) && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.attackerTitle, lang)}</div>
          <div style={policyRowStyle}>
            <span
              style={{
                ...chipStyle,
                ...(highlight.has('attacker') ? highlightChipStyle : {}),
              }}
            >
              {tl(LABELS.recorded, lang)}: {state.attacker.recorded}
            </span>
            {state.attacker.rootInstalled && (
              <span style={warnChipStyle}>{tl(LABELS.rootInstalled, lang)}</span>
            )}
            {state.attacker.hasPrivateKey && (
              <span style={warnChipStyle}>{tl(LABELS.hasKey, lang)}</span>
            )}
            {state.attacker.canRead && (
              <span style={warnChipStyle}>{tl(LABELS.canRead, lang)}</span>
            )}
          </div>
        </div>
      )}

      <div style={statsStyle}>
        <Stat label={tl(LABELS.stats.handshakes, lang)} value={state.stats.handshakes} />
        <Stat label={tl(LABELS.stats.refused, lang)} value={state.stats.refused} />
        <Stat label={tl(LABELS.stats.records, lang)} value={state.stats.records} />
        <Stat label={tl(LABELS.stats.bytesEncrypted, lang)} value={state.stats.bytesEncrypted} />
        <Stat
          label={tl(LABELS.stats.breaches, lang)}
          value={state.stats.breaches}
          color={state.stats.breaches > 0 ? 'var(--bad)' : undefined}
        />
      </div>
    </div>
  );
}

function rootBoxes(roots: Root[], highlight: Set<string>, lang: Lang): Box[] {
  return roots.map((root) => ({
    id: `root-${root.name}`,
    title: root.name,
    subtitle: tl(root.source === 'added' ? LABELS.added : LABELS.preinstalled, lang),
    highlighted: highlight.has('truststore'),
  }));
}

function chainNodes(chain: Cert[], highlight: Set<string>, lang: Lang): LinkedNode[] {
  return chain.map((cert) => ({
    id: `cert-${cert.role}-${cert.subject}`,
    title: cert.subject,
    subtitle: `${tl(LABELS.roles[cert.role], lang)}${cert.sent ? '' : ' · ' + tl(LABELS.notSent, lang)}`,
    highlighted: highlight.has('chain') || highlight.has(`cert:${cert.subject}`),
  }));
}

function CertCard({ cert, held, lang }: { cert: Cert; held: boolean; lang: Lang }) {
  return (
    <div style={{ ...cardStyle, marginTop: 8 }}>
      <Field label={tl(LABELS.issuedBy, lang)} value={cert.issuer} />
      <Field label="SAN" value={cert.names.length > 0 ? cert.names.join(', ') : '—'} />
      <Field
        label={tl(LABELS.validDays, lang)}
        value={`${cert.notBefore} … ${cert.notAfter}`}
      />
      <Field label="key" value={cert.keyType} />
      <div style={badgeRowStyle}>
        {cert.selfSigned && <span style={warnChipStyle}>{tl(LABELS.selfSigned, lang)}</span>}
        {cert.revoked && <span style={warnChipStyle}>{tl(LABELS.revoked, lang)}</span>}
        <span style={held ? goodChipStyle : warnChipStyle}>
          {tl(held ? LABELS.privateKeyHeld : LABELS.privateKeyMissing, lang)}
        </span>
      </div>
    </div>
  );
}

function RecordRow({
  rec,
  lang,
  highlighted,
}: {
  rec: WireRecord;
  lang: Lang;
  highlighted: boolean;
}) {
  return (
    <div
      style={{
        ...recordStyle,
        borderColor: rec.intact ? 'var(--border)' : 'var(--bad)',
        background: highlighted ? 'var(--viz-highlight)' : 'var(--viz-box)',
      }}
    >
      <div style={recordLineStyle}>
        <span style={recordLabelStyle}>{tl(LABELS.plaintext, lang)}</span>
        <span style={recordValueStyle}>{rec.plaintext}</span>
      </div>
      <div style={recordLineStyle}>
        <span style={recordLabelStyle}>{tl(LABELS.ciphertext, lang)}</span>
        <span
          style={{
            ...recordValueStyle,
            color: rec.exposed ? 'var(--bad)' : 'var(--good)',
          }}
        >
          {rec.ciphertext}
          {rec.tag && (
            <span style={tagStyleSmall}>
              {' '}
              · {tl(LABELS.tag, lang)} {rec.tag}
            </span>
          )}
        </span>
      </div>
      <div style={badgeRowStyle}>
        {!rec.intact && <span style={warnChipStyle}>{tl(LABELS.broken, lang)}</span>}
        {rec.exposed && <span style={warnChipStyle}>{tl(LABELS.readable, lang)}</span>}
      </div>
    </div>
  );
}

function StepBox({
  name,
  status,
  detail,
  last,
  highlighted,
}: {
  name: string;
  status: StepStatus;
  detail: string;
  last: boolean;
  highlighted: boolean;
}) {
  const color = STATUS_COLORS[status];
  return (
    <>
      <div
        style={{
          ...stepStyle,
          borderColor: color,
          opacity: status === 'pending' ? 0.45 : 1,
          background: highlighted ? 'var(--viz-highlight)' : 'var(--viz-box)',
          boxShadow: highlighted ? '0 0 0 2px rgba(255,204,102,0.35)' : undefined,
        }}
      >
        <div style={stepNameStyle}>{name}</div>
        <div style={{ ...stepMarkStyle, color }}>{mark(status)}</div>
        {detail && <div style={stepDetailStyle}>{detail}</div>}
      </div>
      {!last && <span style={arrowStyle}>→</span>}
    </>
  );
}

function mark(status: StepStatus) {
  if (status === 'passed') return '✓';
  if (status === 'failed') return '✗';
  return '·';
}

function OutcomeBanner({ outcome, lang }: { outcome: Outcome; lang: Lang }) {
  if (outcome.decision === 'idle') {
    return <div style={{ ...bannerStyle, opacity: 0.5 }}>{tl(LABELS.idle, lang)}</div>;
  }
  const verdict = verdictOf(outcome, lang);
  return (
    <div style={bannerStyle}>
      <span style={{ ...verdictStyle, color: verdict.color }}>{verdict.text}</span>
      {outcome.detail && <span style={detailStyle}>{outcome.detail}</span>}
      {outcome.reason && (
        <span style={reasonStyle}>{tl(LABELS.reasons[outcome.reason], lang)}</span>
      )}
    </div>
  );
}

function verdictOf(outcome: Outcome, lang: Lang) {
  if (outcome.decision === 'secure') {
    return { text: tl(LABELS.secure, lang), color: 'var(--good)' };
  }
  if (outcome.decision === 'refused') {
    return { text: tl(LABELS.refused, lang), color: 'var(--bad)' };
  }
  if (outcome.decision === 'breach') {
    return { text: tl(LABELS.breach, lang), color: 'var(--bad)' };
  }
  if (outcome.decision === 'exposed') {
    return { text: tl(LABELS.exposed, lang), color: 'var(--bad)' };
  }
  return { text: tl(LABELS.running, lang), color: 'var(--accent)' };
}

function Field({ label, value, muted }: { label: string; value: string; muted?: boolean }) {
  return (
    <div style={fieldStyle}>
      <span style={fieldLabelStyle}>{label}</span>
      <span style={{ ...fieldValueStyle, opacity: muted ? 0.5 : 1 }}>{value}</span>
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
const arrowStyle: CSSProperties = { alignSelf: 'center', opacity: 0.4, fontSize: 13 };
const tagStyle: CSSProperties = { fontSize: 11, fontWeight: 700 };
const tagStyleSmall: CSSProperties = { fontSize: 10, opacity: 0.6 };
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
const goodChipStyle: CSSProperties = {
  ...chipStyle,
  color: 'var(--good)',
  border: '1px solid var(--good)',
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
const verdictStyle: CSSProperties = { fontSize: 14, fontWeight: 700 };
const detailStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 12, opacity: 0.75 };
const reasonStyle: CSSProperties = { width: '100%', fontSize: 11, opacity: 0.75 };
const cardStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '8px 10px',
  display: 'flex',
  flexDirection: 'column',
  gap: 4,
  background: 'var(--viz-box)',
};
const fieldStyle: CSSProperties = { display: 'flex', gap: 8, fontSize: 12 };
const fieldLabelStyle: CSSProperties = { opacity: 0.6, minWidth: 130 };
const fieldValueStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 600 };
const badgeRowStyle: CSSProperties = { display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 2 };
const recordsStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const recordStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '6px 8px',
  display: 'flex',
  flexDirection: 'column',
  gap: 2,
};
const recordLineStyle: CSSProperties = { display: 'flex', gap: 8, fontSize: 12 };
const recordLabelStyle: CSSProperties = { opacity: 0.6, minWidth: 90 };
const recordValueStyle: CSSProperties = {
  fontFamily: 'monospace',
  wordBreak: 'break-all',
  flex: '1 1 auto',
};
const columnsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const columnStyle: CSSProperties = { flex: '1 1 280px', minWidth: 260 };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const mutedStyle: CSSProperties = { fontSize: 12, opacity: 0.5, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
