import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to watch one key protect a message, a pair of keys replace it, and both end up working together.',
    ru: 'Запустите код, чтобы увидеть, как сообщение защищает один ключ, как его заменяет пара ключей и как в итоге работают оба.',
  },
  keysTitle: { en: 'keys in the lab', ru: 'ключи в лаборатории' },
  keysEmpty: { en: 'no keys generated yet', ru: 'ключи ещё не сгенерированы' },
  operationTitle: { en: 'this step', ru: 'этот шаг' },
  channelTitle: { en: 'on the wire', ru: 'на проводе' },
  channelEmpty: { en: 'nothing sent yet', ru: 'пока ничего не отправлено' },
  keyManagementTitle: { en: 'keys a group of this size needs', ru: 'сколько ключей нужно группе такого размера' },
  costTitle: { en: 'what it costs', ru: 'во что это обходится' },
  attackerTitle: { en: 'the party on the path', ru: 'тот, кто на пути' },
  comparisonTitle: { en: 'same payload, both families', ru: 'одна нагрузка, оба семейства' },
  kinds: {
    secret: { en: 'shared secret', ru: 'общий секрет' },
    public: { en: 'public half', ru: 'публичная половина' },
    private: { en: 'private half', ru: 'приватная половина' },
  },
  families: {
    symmetric: { en: 'symmetric', ru: 'симметричное' },
    asymmetric: { en: 'asymmetric', ru: 'асимметричное' },
    hybrid: { en: 'hybrid', ru: 'гибридное' },
    signature: { en: 'signature', ru: 'подпись' },
    'key-material': { en: 'key material', ru: 'ключевой материал' },
    none: { en: '—', ru: '—' },
  },
  operations: {
    generate: { en: 'generate', ru: 'сгенерировать' },
    publish: { en: 'publish', ru: 'опубликовать' },
    encrypt: { en: 'encrypt', ru: 'зашифровать' },
    decrypt: { en: 'decrypt', ru: 'расшифровать' },
    sign: { en: 'sign', ru: 'подписать' },
    verify: { en: 'verify', ru: 'проверить' },
    'wrap key': { en: 'wrap the key', ru: 'обернуть ключ' },
    'unwrap key': { en: 'unwrap the key', ru: 'развернуть ключ' },
    share: { en: 'send the key', ru: 'отправить ключ' },
    intercept: { en: 'intercept', ru: 'перехватить' },
    leak: { en: 'leak', ru: 'утечка' },
    compare: { en: 'compare', ru: 'сравнить' },
    'count keys': { en: 'count the keys', ru: 'посчитать ключи' },
    tamper: { en: 'alter in flight', ru: 'подменить на лету' },
    report: { en: 'summary', ru: 'итог' },
  },
  reasons: {
    'wrong-key': { en: 'the wrong key for this payload', ru: 'не тот ключ для этой нагрузки' },
    'payload-too-large': {
      en: 'larger than one asymmetric operation holds',
      ru: 'больше, чем помещается в одну асимметричную операцию',
    },
    'signature-mismatch': {
      en: 'the signature no longer matches the message',
      ru: 'подпись больше не соответствует сообщению',
    },
    tampered: { en: 'the message was changed in flight', ru: 'сообщение изменили на лету' },
    'key-in-the-open': {
      en: 'the shared key travelled over the same channel',
      ru: 'общий ключ прошёл по тому же каналу',
    },
    'attacker-has-the-key': {
      en: 'the key failed, not the cipher',
      ru: 'подвёл ключ, а не шифр',
    },
    'not-encrypted': {
      en: 'nothing here was ever encrypted',
      ru: 'здесь вообще ничего не шифровалось',
    },
    'public-key-cannot-decrypt': {
      en: 'the public half closes envelopes, it does not open them',
      ru: 'публичная половина закрывает конверты, а не открывает',
    },
    'no-key': { en: 'no key that opens this', ru: 'нет ключа, который это открывает' },
    'private-key-leaked': {
      en: 'the half that must never move has moved',
      ru: 'половина, которая не должна двигаться, сдвинулась',
    },
  },
  verdicts: {
    idle: { en: 'nothing has happened yet', ru: 'пока ничего не произошло' },
    pending: { en: 'in progress…', ru: 'выполняется…' },
    ok: { en: 'ok', ru: 'успех' },
    failed: { en: 'refused', ru: 'отказ' },
    blocked: { en: 'attacker blocked', ru: 'злоумышленник остановлен' },
    exposed: { en: 'readable by the wrong party', ru: 'читается не той стороной' },
  },
  input: { en: 'in', ru: 'вход' },
  key: { en: 'key', ru: 'ключ' },
  output: { en: 'out', ru: 'выход' },
  micros: { en: 'µs', ru: 'мкс' },
  published: { en: 'published', ru: 'опубликован' },
  compromised: { en: 'compromised', ru: 'скомпрометирован' },
  heldBy: { en: 'held by', ru: 'у кого' },
  readableBy: { en: 'readable by', ru: 'читают' },
  wrappedKey: { en: 'wrapped key', ru: 'обёрнутый ключ' },
  altered: { en: 'altered', ru: 'изменено' },
  everyone: { en: 'everyone', ru: 'все' },
  sender: { en: 'sender', ru: 'отправитель' },
  parties: { en: 'parties', ru: 'участников' },
  secretKeys: { en: 'shared secrets — n(n-1)/2', ru: 'общих секретов — n(n-1)/2' },
  keyPairs: { en: 'key pairs — n', ru: 'ключевых пар — n' },
  symmetricOps: { en: 'symmetric operations', ru: 'симметричных операций' },
  asymmetricOps: { en: 'asymmetric operations', ru: 'асимметричных операций' },
  bytes: { en: 'bytes', ru: 'байт' },
  blocks: { en: 'operations needed', ru: 'нужно операций' },
  onePass: { en: 'one pass', ru: 'один проход' },
  slower: { en: 'times slower', ru: 'раз медленнее' },
  holds: { en: 'holds', ru: 'владеет' },
  holdsNothing: { en: 'holds no useful key', ru: 'полезных ключей нет' },
  seen: { en: 'messages observed', ru: 'сообщений увидел' },
  stats: {
    encrypted: { en: 'encrypted', ru: 'зашифровано' },
    decrypted: { en: 'decrypted', ru: 'расшифровано' },
    failed: { en: 'refused', ru: 'отклонено' },
    signed: { en: 'signed', ru: 'подписано' },
    verified: { en: 'verified', ru: 'проверено' },
    exposed: { en: 'exposed', ru: 'раскрыто' },
  },
};

type KeyKind = keyof typeof LABELS.kinds;
type Family = keyof typeof LABELS.families;
type OperationName = keyof typeof LABELS.operations;
type Reason = keyof typeof LABELS.reasons;
type Verdict = keyof typeof LABELS.verdicts;

interface CryptoKey {
  id: string;
  kind: KeyKind;
  algorithm: string;
  bits: number;
  owner: string;
  holders: string[];
  published: boolean;
  compromised: boolean;
}
interface Operation {
  name: string;
  family: Family;
  algorithm: string;
  keyId: string;
  keyKind: string;
  input: string;
  output: string;
  micros: number;
  verdict: Verdict;
}
interface WireEntry {
  seq: number;
  family: Family;
  label: string;
  onTheWire: string;
  algorithm: string;
  keyId: string;
  wrappedKey: string | null;
  bytes: number;
  intact: boolean;
  readableBy: string[];
}
interface Attacker {
  holds: string[];
  seen: number;
}
interface KeyManagement {
  parties: number;
  secretKeys: number;
  keyPairs: number;
}
interface Comparison {
  label: string;
  bytes: number;
  symmetricMicros: number;
  asymmetricMicros: number;
  asymmetricBlocks: number;
  ratio: number;
}
interface Cost {
  symmetricOps: number;
  asymmetricOps: number;
  symmetricMicros: number;
  asymmetricMicros: number;
  comparison: Comparison;
}
interface Outcome {
  decision: Verdict;
  reason: Reason | null;
  detail: string;
}
interface Stats {
  encrypted: number;
  decrypted: number;
  failed: number;
  signed: number;
  verified: number;
  exposed: number;
}
interface CryptoState {
  keys: CryptoKey[];
  operation: Operation;
  channel: WireEntry[];
  attacker: Attacker;
  keyManagement: KeyManagement;
  cost: Cost;
  outcome: Outcome;
  stats: Stats;
}

const KIND_COLORS: Record<KeyKind, string> = {
  secret: 'var(--accent)',
  public: 'var(--good)',
  private: 'var(--bad)',
};

export default function CryptoVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as CryptoState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }
  const highlight = new Set(event?.highlight ?? []);
  const comparison = state.cost.comparison;

  return (
    <div style={wrapStyle}>
      <OutcomeBanner outcome={state.outcome} lang={lang} />

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.keysTitle, lang)}</div>
        {state.keys.length === 0 ? (
          <div style={mutedStyle}>{tl(LABELS.keysEmpty, lang)}</div>
        ) : (
          <div style={keyListStyle}>
            {state.keys.map((key) => (
              <KeyCard
                key={key.id}
                cryptoKey={key}
                lang={lang}
                highlighted={highlight.has('keys') || highlight.has(`key:${key.id}`)}
              />
            ))}
          </div>
        )}
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.operationTitle, lang)}</div>
        <div
          style={{
            ...cardStyle,
            background: highlight.has('operation') ? 'var(--viz-highlight)' : 'var(--viz-box)',
          }}
        >
          <div style={policyRowStyle}>
            <span style={{ ...tagStyle, color: 'var(--accent)' }}>
              {operationLabel(state.operation.name, lang)}
            </span>
            <span style={chipStyle}>{tl(LABELS.families[state.operation.family], lang)}</span>
            {state.operation.algorithm && state.operation.algorithm !== 'none' && (
              <span style={chipStyle}>{state.operation.algorithm}</span>
            )}
            {state.operation.micros > 0 && (
              <span style={chipStyle}>
                {state.operation.micros} {tl(LABELS.micros, lang)}
              </span>
            )}
          </div>
          <LinkedNodes nodes={pipelineNodes(state.operation, lang)} />
        </div>
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.channelTitle, lang)}</div>
        {state.channel.length === 0 ? (
          <div style={mutedStyle}>{tl(LABELS.channelEmpty, lang)}</div>
        ) : (
          <div style={wireStyle}>
            {state.channel.map((entry) => (
              <WireRow
                key={entry.seq}
                entry={entry}
                lang={lang}
                highlighted={highlight.has('channel')}
              />
            ))}
          </div>
        )}
      </div>

      {state.keyManagement.parties > 0 && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.keyManagementTitle, lang)}</div>
          <BoxGroup boxes={keyManagementBoxes(state.keyManagement, highlight, lang)} />
        </div>
      )}

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.costTitle, lang)}</div>
        <div
          style={{
            ...cardStyle,
            background: highlight.has('cost') ? 'var(--viz-highlight)' : 'var(--viz-box)',
          }}
        >
          <Field
            label={tl(LABELS.symmetricOps, lang)}
            value={`${state.cost.symmetricOps} · ${state.cost.symmetricMicros} ${tl(LABELS.micros, lang)}`}
          />
          <Field
            label={tl(LABELS.asymmetricOps, lang)}
            value={`${state.cost.asymmetricOps} · ${state.cost.asymmetricMicros} ${tl(LABELS.micros, lang)}`}
          />
          {comparison.bytes > 0 && (
            <>
              <div style={{ ...sectionLabelStyle, marginTop: 6, marginBottom: 2 }}>
                {tl(LABELS.comparisonTitle, lang)}
              </div>
              <Field
                label={comparison.label}
                value={`${comparison.bytes} ${tl(LABELS.bytes, lang)}`}
              />
              <Field
                label={tl(LABELS.families.symmetric, lang)}
                value={`${comparison.symmetricMicros} ${tl(LABELS.micros, lang)} · ${tl(LABELS.onePass, lang)}`}
              />
              <Field
                label={tl(LABELS.families.asymmetric, lang)}
                value={`${comparison.asymmetricMicros} ${tl(LABELS.micros, lang)} · ${comparison.asymmetricBlocks} ${tl(LABELS.blocks, lang)}`}
              />
              <div style={badgeRowStyle}>
                <span style={warnChipStyle}>
                  ×{comparison.ratio} {tl(LABELS.slower, lang)}
                </span>
              </div>
            </>
          )}
        </div>
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.attackerTitle, lang)}</div>
        <div style={policyRowStyle}>
          <span
            style={{ ...chipStyle, ...(highlight.has('attacker') ? highlightChipStyle : {}) }}
          >
            {tl(LABELS.seen, lang)}: {state.attacker.seen}
          </span>
          {state.attacker.holds.length === 0 ? (
            <span style={goodChipStyle}>{tl(LABELS.holdsNothing, lang)}</span>
          ) : (
            state.attacker.holds.map((id) => (
              <span key={id} style={warnChipStyle}>
                {tl(LABELS.holds, lang)} {id}
              </span>
            ))
          )}
        </div>
      </div>

      <div style={statsStyle}>
        <Stat label={tl(LABELS.stats.encrypted, lang)} value={state.stats.encrypted} />
        <Stat label={tl(LABELS.stats.decrypted, lang)} value={state.stats.decrypted} />
        <Stat label={tl(LABELS.stats.failed, lang)} value={state.stats.failed} />
        <Stat label={tl(LABELS.stats.signed, lang)} value={state.stats.signed} />
        <Stat label={tl(LABELS.stats.verified, lang)} value={state.stats.verified} />
        <Stat
          label={tl(LABELS.stats.exposed, lang)}
          value={state.stats.exposed}
          color={state.stats.exposed > 0 ? 'var(--bad)' : undefined}
        />
      </div>
    </div>
  );
}

function KeyCard({
  cryptoKey,
  lang,
  highlighted,
}: {
  cryptoKey: CryptoKey;
  lang: Lang;
  highlighted: boolean;
}) {
  const color = KIND_COLORS[cryptoKey.kind];
  return (
    <div
      style={{
        ...keyCardStyle,
        borderColor: color,
        background: highlighted ? 'var(--viz-highlight)' : 'var(--viz-box)',
        boxShadow: highlighted ? '0 0 0 2px rgba(255,204,102,0.35)' : undefined,
      }}
    >
      <div style={keyIdStyle}>{cryptoKey.id}</div>
      <div style={{ ...keyKindStyle, color }}>{tl(LABELS.kinds[cryptoKey.kind], lang)}</div>
      <div style={keyMetaStyle}>
        {cryptoKey.algorithm} · {cryptoKey.bits} bit
      </div>
      <div style={keyMetaStyle}>
        {tl(LABELS.heldBy, lang)}: {holderNames(cryptoKey.holders, lang)}
      </div>
      <div style={badgeRowStyle}>
        {cryptoKey.published && <span style={goodChipStyle}>{tl(LABELS.published, lang)}</span>}
        {cryptoKey.compromised && (
          <span style={warnChipStyle}>{tl(LABELS.compromised, lang)}</span>
        )}
      </div>
    </div>
  );
}

function WireRow({
  entry,
  lang,
  highlighted,
}: {
  entry: WireEntry;
  lang: Lang;
  highlighted: boolean;
}) {
  const leaked = entry.readableBy.includes('Mallory') || entry.readableBy.includes('everyone');
  return (
    <div
      style={{
        ...wireRowStyle,
        borderColor: entry.intact ? 'var(--border)' : 'var(--bad)',
        background: highlighted ? 'var(--viz-highlight)' : 'var(--viz-box)',
      }}
    >
      <div style={policyRowStyle}>
        <span style={tagStyle}>#{entry.seq}</span>
        <span style={chipStyle}>{tl(LABELS.families[entry.family], lang)}</span>
        <span style={chipStyle}>
          {entry.bytes} {tl(LABELS.bytes, lang)}
        </span>
      </div>
      <Field label={tl(LABELS.input, lang)} value={entry.label} />
      <Field
        label={tl(LABELS.output, lang)}
        value={entry.onTheWire}
        color={leaked ? 'var(--bad)' : 'var(--good)'}
      />
      {entry.wrappedKey && <Field label={tl(LABELS.wrappedKey, lang)} value={entry.wrappedKey} />}
      <div style={badgeRowStyle}>
        <span style={leaked ? warnChipStyle : goodChipStyle}>
          {tl(LABELS.readableBy, lang)}: {holderNames(entry.readableBy, lang)}
        </span>
        {!entry.intact && <span style={warnChipStyle}>{tl(LABELS.altered, lang)}</span>}
      </div>
    </div>
  );
}

function OutcomeBanner({ outcome, lang }: { outcome: Outcome; lang: Lang }) {
  const color = verdictColor(outcome.decision);
  return (
    <div style={bannerStyle}>
      <span style={{ ...verdictStyle, color }}>{tl(LABELS.verdicts[outcome.decision], lang)}</span>
      {outcome.detail && <span style={detailStyle}>{outcome.detail}</span>}
      {outcome.reason && (
        <span style={reasonStyle}>{tl(LABELS.reasons[outcome.reason], lang)}</span>
      )}
    </div>
  );
}

function pipelineNodes(operation: Operation, lang: Lang): LinkedNode[] {
  const nodes: LinkedNode[] = [];
  if (operation.input) {
    nodes.push({ id: 'in', title: operation.input, subtitle: tl(LABELS.input, lang) });
  }
  if (operation.keyId) {
    nodes.push({
      id: 'key',
      title: operation.keyId,
      subtitle: `${tl(LABELS.key, lang)} · ${keyKindLabel(operation.keyKind, lang)}`,
      highlighted: true,
    });
  }
  if (operation.output) {
    nodes.push({ id: 'out', title: operation.output, subtitle: tl(LABELS.output, lang) });
  }
  return nodes;
}

function keyManagementBoxes(
  management: KeyManagement,
  highlight: Set<string>,
  lang: Lang,
): Box[] {
  const on = highlight.has('keymgmt');
  return [
    {
      id: 'parties',
      title: String(management.parties),
      subtitle: tl(LABELS.parties, lang),
      highlighted: on,
    },
    {
      id: 'secret-keys',
      title: String(management.secretKeys),
      subtitle: tl(LABELS.secretKeys, lang),
      highlighted: on,
    },
    {
      id: 'key-pairs',
      title: String(management.keyPairs),
      subtitle: tl(LABELS.keyPairs, lang),
      highlighted: on,
    },
  ];
}

function holderNames(holders: string[], lang: Lang): string {
  if (holders.length === 0) return '—';
  return holders
    .map((holder) => {
      if (holder === 'everyone') return tl(LABELS.everyone, lang);
      if (holder === 'sender') return tl(LABELS.sender, lang);
      return holder;
    })
    .join(', ');
}

function operationLabel(name: string, lang: Lang): string {
  const entry = LABELS.operations[name as OperationName];
  return entry ? tl(entry, lang) : name || '—';
}

function keyKindLabel(kind: string, lang: Lang): string {
  const entry = LABELS.kinds[kind as KeyKind];
  return entry ? tl(entry, lang) : kind;
}

function verdictColor(verdict: Verdict): string {
  if (verdict === 'ok' || verdict === 'blocked') return 'var(--good)';
  if (verdict === 'failed') return 'var(--bad)';
  if (verdict === 'exposed') return 'var(--bad)';
  return 'var(--accent)';
}

function Field({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <div style={fieldStyle}>
      <span style={fieldLabelStyle}>{label}</span>
      <span style={{ ...fieldValueStyle, color: color ?? 'inherit' }}>{value}</span>
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
const keyListStyle: CSSProperties = { display: 'flex', gap: 8, flexWrap: 'wrap' };
const keyCardStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '6px 10px',
  minWidth: 150,
};
const keyIdStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700, fontSize: 13 };
const keyKindStyle: CSSProperties = { fontSize: 11, fontWeight: 600 };
const keyMetaStyle: CSSProperties = { fontSize: 10, opacity: 0.65 };
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
  gap: 6,
};
const fieldStyle: CSSProperties = { display: 'flex', gap: 8, fontSize: 12 };
const fieldLabelStyle: CSSProperties = { opacity: 0.6, minWidth: 110 };
const fieldValueStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontWeight: 600,
  wordBreak: 'break-all',
  flex: '1 1 auto',
};
const badgeRowStyle: CSSProperties = { display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 2 };
const wireStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const wireRowStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '6px 8px',
  display: 'flex',
  flexDirection: 'column',
  gap: 2,
};
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const mutedStyle: CSSProperties = { fontSize: 12, opacity: 0.5, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
