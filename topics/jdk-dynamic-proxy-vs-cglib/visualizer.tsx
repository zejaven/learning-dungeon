import type { CSSProperties, ReactNode } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize how Spring builds the proxy.',
    ru: 'Запустите код, чтобы увидеть, как Spring строит proxy.',
  },
  client: { en: 'client', ru: 'клиент' },
  proxy: { en: 'proxy', ru: 'proxy' },
  target: { en: 'target bean', ru: 'целевой bean' },
  strategy: { en: 'strategy', ru: 'стратегия' },
  reason: { en: 'reason', ru: 'причина' },
  notDecided: { en: 'not decided yet', ru: 'ещё не выбрана' },
  noProxy: { en: 'no proxy', ru: 'нет proxy' },
  interfaces: { en: 'interfaces', ru: 'interfaces' },
  methods: { en: 'methods', ru: 'методы' },
  noInterfaces: { en: 'none (CGLIB territory)', ru: 'нет (территория CGLIB)' },
  execution: { en: 'call dispatch', ru: 'путь вызова' },
  noSteps: { en: 'no call yet', ru: 'вызова ещё не было' },
  blocked: { en: 'BLOCKED', ru: 'ЗАБЛОКИРОВАНО' },
  finalTag: { en: 'final', ru: 'final' },
  proxyTargetClass: { en: 'proxyTargetClass', ru: 'proxyTargetClass' },
  // reason codes
  reasonHasInterface: { en: 'bean implements an interface', ru: 'bean реализует interface' },
  reasonNoInterface: { en: 'bean has no interface', ru: 'у bean нет interface' },
  reasonForceCglib: { en: 'proxyTargetClass=true forces CGLIB', ru: 'proxyTargetClass=true форсирует CGLIB' },
  // relations
  relImplements: { en: 'implements', ru: 'реализует' },
  relExtends: { en: 'extends', ru: 'наследует' },
  // actors
  actorClient: { en: 'client', ru: 'клиент' },
  actorProxy: { en: 'proxy', ru: 'proxy' },
  actorAdvice: { en: 'advice', ru: 'advice' },
  actorTarget: { en: 'target', ru: 'target' },
  // actions
  actIntercept: { en: 'intercept', ru: 'перехват' },
  actAdvice: { en: 'run advice chain', ru: 'цепочка advice' },
  actExecute: { en: 'execute method', ru: 'выполнить метод' },
  actReturn: { en: 'return', ru: 'возврат' },
  actUnadvised: { en: 'unadvised (final)', ru: 'без advice (final)' },
  actNoProxy: { en: 'no proxy to intercept', ru: 'нет proxy для перехвата' },
};

type Actor = 'client' | 'proxy' | 'advice' | 'target';

interface MethodInfo {
  name: string;
  isFinal: boolean;
}

interface Step {
  id: string;
  actor: Actor;
  action: string;
  method: string;
}

interface ProxyState {
  target: string;
  finalClass: boolean;
  proxyTargetClass: boolean;
  interfaces: string[];
  methods: MethodInfo[];
  strategy: '' | 'JDK' | 'CGLIB';
  reason: string;
  proxyName: string;
  relation: '' | 'implements' | 'extends';
  supertype: string;
  created: boolean;
  blocked: boolean;
  phase: string;
  activeMethod: string;
  log: Step[];
}

export default function ProxyFactoryVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as ProxyState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const strategyTone = state.strategy === 'JDK' ? 'accent' : state.strategy === 'CGLIB' ? 'good' : undefined;

  const proxyLabel = state.created
    ? state.proxyName
    : state.blocked
      ? tl(LABELS.blocked, lang)
      : tl(LABELS.noProxy, lang);

  return (
    <div style={wrapStyle}>
      <div style={topStyle}>
        <Box title={tl(LABELS.client, lang)} value={tl(LABELS.client, lang)} highlighted={highlight.has('client')} />
        <span style={arrowStyle}>→</span>
        <Box
          title={tl(LABELS.proxy, lang)}
          value={proxyLabel}
          highlighted={highlight.has('proxy')}
          tone={state.blocked ? 'bad' : 'accent'}
          subtitle={state.relation ? `${relationLabel(state.relation, lang)} ${state.supertype}` : undefined}
        />
        <span style={arrowStyle}>→</span>
        <Box
          title={tl(LABELS.target, lang)}
          value={`${state.target}${state.finalClass ? ' (final)' : ''}`}
          highlighted={highlight.has('target')}
        />
      </div>

      <div style={statusStyle}>
        <Stat
          label={tl(LABELS.strategy, lang)}
          value={state.strategy || tl(LABELS.notDecided, lang)}
          tone={strategyTone}
        />
        <Stat label={tl(LABELS.reason, lang)} value={reasonLabel(state.reason, lang)} />
        <Stat label={tl(LABELS.proxyTargetClass, lang)} value={String(state.proxyTargetClass)} />
      </div>

      <Section label={tl(LABELS.interfaces, lang)}>
        {state.interfaces.length === 0 ? (
          <span style={emptyStyle}>{tl(LABELS.noInterfaces, lang)}</span>
        ) : (
          <div style={pillWrapStyle}>
            {state.interfaces.map((name) => (
              <Pill
                key={name}
                text={name}
                tone="accent"
                highlighted={highlight.has(`interface:${name}`)}
              />
            ))}
          </div>
        )}
      </Section>

      <Section label={tl(LABELS.methods, lang)}>
        {state.methods.length === 0 ? (
          <span style={emptyStyle}>{tl(LABELS.noSteps, lang)}</span>
        ) : (
          <div style={pillWrapStyle}>
            {state.methods.map((m) => (
              <Pill
                key={m.name}
                text={`${m.name}()${m.isFinal ? ` · ${tl(LABELS.finalTag, lang)}` : ''}`}
                tone={m.isFinal ? 'bad' : undefined}
                highlighted={highlight.has(`method:${m.name}`)}
              />
            ))}
          </div>
        )}
      </Section>

      <Section label={tl(LABELS.execution, lang)}>
        {state.log.length === 0 ? (
          <span style={emptyStyle}>{tl(LABELS.noSteps, lang)}</span>
        ) : (
          <ArrayGrid cells={logCells(state.log, highlight, lang)} />
        )}
      </Section>
    </div>
  );
}

function logCells(log: Step[], highlight: Set<string>, lang: Lang): ArrayCell[] {
  return log.map((step, i) => ({
    key: step.id,
    label: `${i + 1}`,
    highlighted: highlight.has(step.actor) || highlight.has(`method:${step.method}`),
    content: (
      <div style={stepStyle}>
        <Pill text={actorLabel(step.actor, lang)} tone={step.actor === 'target' ? 'good' : 'accent'} />
        <span style={monoStyle}>{step.method}()</span>
        <span style={actionStyle}>{actionLabel(step.action, lang)}</span>
      </div>
    ),
  }));
}

function Section({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div style={sectionStyle}>
      <div style={sectionLabelStyle}>{label}</div>
      {children}
    </div>
  );
}

function Box({
  title,
  value,
  subtitle,
  highlighted,
  tone,
}: {
  title: string;
  value: string;
  subtitle?: string;
  highlighted: boolean;
  tone?: 'accent' | 'bad';
}) {
  return (
    <div style={{ ...boxStyle, ...(highlighted ? boxHighlightStyle : {}) }}>
      <div style={smallLabelStyle}>{title}</div>
      <div style={{ ...boxValueStyle, ...toneStyle(tone) }}>{value}</div>
      {subtitle && <div style={subtitleStyle}>{subtitle}</div>}
    </div>
  );
}

function Stat({ label, value, tone }: { label: string; value: string; tone?: 'accent' | 'good' | 'bad' }) {
  return (
    <div style={statStyle}>
      <div style={smallLabelStyle}>{label}</div>
      <div style={{ ...statValueStyle, ...toneStyle(tone) }}>{value}</div>
    </div>
  );
}

function Pill({
  text,
  tone,
  highlighted,
}: {
  text: string;
  tone?: 'good' | 'bad' | 'accent';
  highlighted?: boolean;
}) {
  return (
    <span style={{ ...pillStyle, ...toneStyle(tone), ...(highlighted ? pillHighlightStyle : {}) }}>{text}</span>
  );
}

function reasonLabel(reason: string, lang: Lang) {
  if (reason === 'has-interface') return tl(LABELS.reasonHasInterface, lang);
  if (reason === 'no-interface') return tl(LABELS.reasonNoInterface, lang);
  if (reason === 'force-cglib') return tl(LABELS.reasonForceCglib, lang);
  return tl(LABELS.notDecided, lang);
}

function relationLabel(relation: 'implements' | 'extends', lang: Lang) {
  return relation === 'implements' ? tl(LABELS.relImplements, lang) : tl(LABELS.relExtends, lang);
}

function actorLabel(actor: Actor, lang: Lang) {
  if (actor === 'client') return tl(LABELS.actorClient, lang);
  if (actor === 'proxy') return tl(LABELS.actorProxy, lang);
  if (actor === 'advice') return tl(LABELS.actorAdvice, lang);
  return tl(LABELS.actorTarget, lang);
}

function actionLabel(action: string, lang: Lang) {
  if (action === 'intercept') return tl(LABELS.actIntercept, lang);
  if (action === 'advice') return tl(LABELS.actAdvice, lang);
  if (action === 'execute') return tl(LABELS.actExecute, lang);
  if (action === 'return') return tl(LABELS.actReturn, lang);
  if (action === 'unadvised') return tl(LABELS.actUnadvised, lang);
  if (action === 'no-proxy') return tl(LABELS.actNoProxy, lang);
  return action;
}

function toneStyle(tone?: 'good' | 'bad' | 'accent'): CSSProperties {
  if (tone === 'good') return { color: 'var(--good)', borderColor: 'var(--good)' };
  if (tone === 'bad') return { color: 'var(--bad)', borderColor: 'var(--bad)' };
  if (tone === 'accent') return { color: 'var(--accent)', borderColor: 'var(--accent)' };
  return {};
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const topStyle: CSSProperties = { display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' };
const arrowStyle: CSSProperties = { fontSize: 18, opacity: 0.55 };
const boxStyle: CSSProperties = {
  padding: '6px 10px',
  borderRadius: 8,
  border: '1px solid var(--border)',
  background: 'var(--viz-box)',
  minWidth: 132,
};
const boxHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.24)',
};
const boxValueStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13, fontWeight: 700 };
const smallLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.62 };
const subtitleStyle: CSSProperties = { fontSize: 11, opacity: 0.7, fontFamily: 'monospace', marginTop: 2 };
const sectionStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const sectionLabelStyle: CSSProperties = { fontSize: 12, opacity: 0.65, fontWeight: 700 };
const statusStyle: CSSProperties = { display: 'flex', gap: 8, flexWrap: 'wrap' };
const statStyle: CSSProperties = {
  minWidth: 116,
  padding: '5px 8px',
  borderRadius: 6,
  border: '1px solid var(--border)',
  background: 'var(--viz-box)',
};
const statValueStyle: CSSProperties = { fontSize: 13, fontWeight: 700, fontFamily: 'monospace' };
const pillWrapStyle: CSSProperties = { display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' };
const pillStyle: CSSProperties = {
  display: 'inline-flex',
  border: '1px solid var(--border)',
  borderRadius: 999,
  padding: '2px 7px',
  fontSize: 12,
  fontFamily: 'monospace',
  background: 'var(--viz-box)',
};
const pillHighlightStyle: CSSProperties = {
  background: 'var(--viz-highlight)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.30)',
};
const stepStyle: CSSProperties = { display: 'flex', gap: 7, alignItems: 'center', flexWrap: 'wrap' };
const monoStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 12, fontWeight: 700 };
const actionStyle: CSSProperties = { fontSize: 12, opacity: 0.78 };
const emptyStyle: CSSProperties = { fontSize: 12, opacity: 0.45, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
