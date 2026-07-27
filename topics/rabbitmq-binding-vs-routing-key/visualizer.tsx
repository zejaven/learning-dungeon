import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  routingKey: { en: 'routing key — set by the producer, on the message', ru: 'routing key — задаёт продюсер, живёт на сообщении' },
  bindings: { en: 'binding keys — set by the queue side, on the binding', ru: 'binding key — задаёт сторона очереди, живёт на привязке' },
  queues: { en: 'queues (routed copies)', ru: 'очереди (доставленные копии)' },
  rule: { en: 'rule', ru: 'правило' },
  noBindings: { en: 'no binding yet — nothing is reachable', ru: 'привязок пока нет — попасть некуда' },
  noQueues: { en: 'no queue declared yet', ru: 'очередей пока нет' },
  idle: { en: 'no message being routed', ru: 'сейчас ничто не маршрутизируется' },
  noKey: { en: '(none)', ru: '(нет)' },
  emptyKey: { en: '(empty)', ru: '(пусто)' },
  implicit: { en: 'created by the broker', ru: 'создана брокером' },
  literal: {
    en: "'*' and '#' are literal characters in a routing key",
    ru: "в routing key '*' и '#' — обычные символы",
  },
  published: { en: 'published', ru: 'опубликовано' },
  copies: { en: 'copies stored', ru: 'сохранено копий' },
  unroutable: { en: 'unroutable', ru: 'недоставляемо' },
  runHint: {
    en: 'Run the code to watch the exchange compare the two keys.',
    ru: 'Запустите код, чтобы увидеть, как exchange сравнивает два ключа.',
  },
};

const RULES: Record<string, { en: string; ru: string }> = {
  direct: { en: 'the binding key must equal the routing key', ru: 'binding key должен быть равен routing key' },
  topic: {
    en: "the binding key is a pattern: '*' = one word, '#' = zero or more",
    ru: "binding key — шаблон: '*' — одно слово, '#' — ноль или больше",
  },
  fanout: { en: 'neither key is read', ru: 'ни один из ключей не читается' },
};

const STAGES: Record<string, { label: { en: string; ru: string }; color: string }> = {
  published: { label: { en: 'published', ru: 'опубликовано' }, color: 'var(--accent)' },
  matching: { label: { en: 'comparing keys…', ru: 'сравниваем ключи…' }, color: 'var(--accent)' },
  routed: { label: { en: 'routed ✓', ru: 'смаршрутизировано ✓' }, color: 'var(--good)' },
  unroutable: { label: { en: 'unroutable — dropped ✗', ru: 'недоставляемо — выброшено ✗' }, color: 'var(--bad)' },
};

const VERDICTS: Record<string, { label: { en: string; ru: string }; color: string }> = {
  pending: { label: { en: '·', ru: '·' }, color: 'var(--text)' },
  match: { label: { en: '✓ match', ru: '✓ совпало' }, color: 'var(--good)' },
  'no-match': { label: { en: '✗ no match', ru: '✗ не совпало' }, color: 'var(--bad)' },
  duplicate: { label: { en: '↺ already matched', ru: '↺ уже совпало' }, color: 'var(--accent-2)' },
};

interface Binding {
  queue: string;
  key: string;
  verdict: string;
  implicit: boolean;
}
interface Copy {
  id: string;
  routingKey: string;
}
interface Queue {
  name: string;
  messages: Copy[];
}
interface Message {
  id: string;
  routingKey: string;
  stage: string;
  literalWildcard: boolean;
}
interface RouterState {
  exchange: { name: string; type: string; isDefault: boolean };
  bindings: Binding[];
  queues: Queue[];
  message?: Message;
  stats: { published: number; copies: number; unroutable: number };
}

export default function RoutingVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as RouterState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const rule = RULES[state.exchange.type];

  return (
    <div style={wrapStyle}>
      <div
        style={{
          ...exchangeStyle,
          ...(highlight.has('exchange') ? highlightStyle : {}),
        }}
      >
        <span style={nameStyle}>{state.exchange.name}</span>
        <span style={badgeStyle}>{state.exchange.type}</span>
        <span style={metaStyle}>
          {tl(LABELS.rule, lang)}: {rule ? tl(rule, lang) : state.exchange.type}
        </span>
      </div>

      <MessageBanner message={state.message} highlighted={highlight.has('message')} lang={lang} />

      <section>
        <div style={sectionLabelStyle}>{tl(LABELS.bindings, lang)}</div>
        {state.bindings.length === 0 ? (
          <div style={emptyStyle}>{tl(LABELS.noBindings, lang)}</div>
        ) : (
          state.bindings.map((b, i) => {
            const verdict = VERDICTS[b.verdict] ?? VERDICTS.pending;
            return (
              <div
                key={`${b.queue}|${b.key}|${i}`}
                style={{ ...rowStyle, ...(highlight.has(`binding:${i}`) ? highlightStyle : {}) }}
              >
                <span style={keyStyle}>{b.key === '' ? tl(LABELS.noKey, lang) : b.key}</span>
                <span style={arrowStyle}>→</span>
                <span style={nameStyle}>{b.queue}</span>
                {b.implicit && <span style={metaStyle}>{tl(LABELS.implicit, lang)}</span>}
                <span style={{ ...verdictStyle, color: verdict.color }}>{tl(verdict.label, lang)}</span>
              </div>
            );
          })
        )}
      </section>

      <section>
        <div style={sectionLabelStyle}>{tl(LABELS.queues, lang)}</div>
        {state.queues.length === 0 ? (
          <div style={emptyStyle}>{tl(LABELS.noQueues, lang)}</div>
        ) : (
          state.queues.map((q) => (
            <div
              key={q.name}
              style={{ ...blockStyle, ...(highlight.has(`queue:${q.name}`) ? highlightStyle : {}) }}
            >
              <div style={nameStyle}>{q.name}</div>
              <BoxGroup boxes={q.messages.map((m) => toBox(m, lang))} />
            </div>
          ))
        )}
      </section>

      <div style={statsStyle}>
        <Stat label={tl(LABELS.published, lang)} value={state.stats.published} color="var(--accent)" />
        <Stat label={tl(LABELS.copies, lang)} value={state.stats.copies} color="var(--good)" />
        <Stat label={tl(LABELS.unroutable, lang)} value={state.stats.unroutable} color="var(--bad)" />
      </div>
    </div>
  );
}

function toBox(copy: Copy, lang: Lang): Box {
  return {
    id: `${copy.id}|${copy.routingKey}`,
    title: copy.id,
    subtitle: copy.routingKey === '' ? tl(LABELS.emptyKey, lang) : copy.routingKey,
  };
}

function MessageBanner({
  message,
  highlighted,
  lang,
}: {
  message: Message | undefined;
  highlighted: boolean;
  lang: Lang;
}) {
  if (!message) {
    return <div style={{ ...bannerStyle, opacity: 0.5 }}>{tl(LABELS.idle, lang)}</div>;
  }
  const stage = STAGES[message.stage];
  return (
    <div style={{ ...bannerStyle, ...(highlighted ? highlightStyle : {}) }}>
      <span style={nameStyle}>{message.id}</span>
      <span style={metaStyle}>{tl(LABELS.routingKey, lang)}</span>
      <span style={keyStyle}>
        {message.routingKey === '' ? tl(LABELS.emptyKey, lang) : message.routingKey}
      </span>
      {message.literalWildcard && <span style={warnStyle}>{tl(LABELS.literal, lang)}</span>}
      <span style={{ ...verdictStyle, color: stage ? stage.color : 'var(--accent)' }}>
        {stage ? tl(stage.label, lang) : message.stage}
      </span>
    </div>
  );
}

function Stat({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={{ ...statValueStyle, color }}>{value}</div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const exchangeStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 10,
  flexWrap: 'wrap',
  padding: '6px 10px',
  borderRadius: 8,
  border: '1px solid transparent',
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
const highlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
};
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const rowStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 10,
  flexWrap: 'wrap',
  padding: '3px 8px',
  borderRadius: 6,
  border: '1px solid transparent',
};
const blockStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 4,
  padding: '6px 8px',
  borderRadius: 8,
  border: '1px solid transparent',
  marginBottom: 6,
};
const nameStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700, fontSize: 13 };
const keyStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 13,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const badgeStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const arrowStyle: CSSProperties = { opacity: 0.5, fontSize: 13 };
const metaStyle: CSSProperties = { fontSize: 12, opacity: 0.65 };
const warnStyle: CSSProperties = { fontSize: 12, color: 'var(--bad)' };
const verdictStyle: CSSProperties = { marginLeft: 'auto', fontSize: 13, fontWeight: 600 };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const emptyStyle: CSSProperties = { fontSize: 12, opacity: 0.4, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
