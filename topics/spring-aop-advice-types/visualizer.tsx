import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang, type Lang, type Localized } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run an example to visualize advice timing.',
    ru: 'Запустите пример, чтобы визуализировать порядок advice.',
  },
  proxy: { en: 'proxy', ru: 'proxy' },
  target: { en: 'target bean', ru: 'целевой bean' },
  adviceChain: { en: 'registered advice', ru: 'зарегистрированные advice' },
  timing: { en: 'advice timing', ru: 'когда выполняется advice' },
  execution: { en: 'execution order', ru: 'порядок выполнения' },
  method: { en: 'method', ru: 'метод' },
  phase: { en: 'phase', ru: 'фаза' },
  pointcut: { en: 'pointcut', ru: 'pointcut' },
  matched: { en: 'matched', ru: 'подошел' },
  missed: { en: 'not matched', ru: 'не подошел' },
  noActiveMethod: { en: 'no active method', ru: 'нет активного метода' },
  noAdvice: { en: 'no advice registered', ru: 'нет зарегистрированных advice' },
  noSteps: { en: 'no executed steps', ru: 'нет выполненных шагов' },
  registered: { en: 'registered', ru: 'зарегистрирован' },
  notRegistered: { en: 'not registered', ru: 'не зарегистрирован' },
  ran: { en: 'ran', ru: 'выполнился' },
  before: { en: '@Before', ru: '@Before' },
  after: { en: '@After', ru: '@After' },
  afterReturning: { en: '@AfterReturning', ru: '@AfterReturning' },
  afterThrowing: { en: '@AfterThrowing', ru: '@AfterThrowing' },
  around: { en: '@Around', ru: '@Around' },
  proxyRole: { en: 'proxy', ru: 'proxy' },
  adviceRole: { en: 'advice', ru: 'advice' },
  targetRole: { en: 'target', ru: 'target' },
  actionIntercept: { en: 'intercept', ru: 'перехват' },
  actionBefore: { en: '@Before', ru: '@Before' },
  actionAfter: { en: '@After', ru: '@After' },
  actionAroundBefore: { en: '@Around before proceed()', ru: '@Around до proceed()' },
  actionAroundAfter: { en: '@Around after proceed()', ru: '@Around после proceed()' },
  actionAroundException: { en: '@Around sees exception', ru: '@Around видит exception' },
  actionAfterReturning: { en: '@AfterReturning', ru: '@AfterReturning' },
  actionAfterThrowing: { en: '@AfterThrowing', ru: '@AfterThrowing' },
  actionException: { en: 'throws exception', ru: 'бросает exception' },
};

type AdviceKind = 'before' | 'after' | 'afterReturning' | 'afterThrowing' | 'around';
type Role = 'proxy' | 'advice' | 'target';

const ADVICE_ORDER: AdviceKind[] = ['around', 'before', 'afterReturning', 'afterThrowing', 'after'];

const ADVICE_WHEN: Record<AdviceKind, Localized> = {
  around: {
    en: 'wraps proceed(); can run before and after the target',
    ru: 'оборачивает proceed(); может работать до и после target',
  },
  before: {
    en: 'runs before the target method starts',
    ru: 'выполняется до старта target method',
  },
  afterReturning: {
    en: 'runs only after a successful return',
    ru: 'выполняется только после успешного return',
  },
  afterThrowing: {
    en: 'runs only when an exception escapes',
    ru: 'выполняется только когда exception выходит наружу',
  },
  after: {
    en: 'runs after completion, success or exception',
    ru: 'выполняется после завершения: success или exception',
  },
};

const PHASES: Record<string, Localized> = {
  idle: { en: 'idle', ru: 'ожидание' },
  setup: { en: 'setup', ru: 'настройка' },
  proxy: { en: 'proxy call', ru: 'вызов proxy' },
  pointcut: { en: 'pointcut check', ru: 'проверка pointcut' },
  around: { en: '@Around', ru: '@Around' },
  before: { en: '@Before', ru: '@Before' },
  target: { en: 'target method', ru: 'target method' },
  after: { en: '@After', ru: '@After' },
  afterReturning: { en: '@AfterReturning', ru: '@AfterReturning' },
  exception: { en: 'exception', ru: 'exception' },
  afterThrowing: { en: '@AfterThrowing', ru: '@AfterThrowing' },
  done: { en: 'done', ru: 'готово' },
};

interface Advice {
  name: string;
  kind: AdviceKind;
  pointcut: string;
  applied: boolean;
}

interface Frame {
  name: string;
  role: Role;
}

interface Step {
  id: string;
  role: Role;
  name: string;
  method: string;
  action: string;
}

interface AopAdviceState {
  target: string;
  proxy: string;
  activeMethod: string;
  callMode: 'none' | 'proxy' | 'direct';
  phase: string;
  pointcutMatched: boolean;
  advices: Advice[];
  stack: Frame[];
  executed: Step[];
}

export default function AopAdviceVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as AopAdviceState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const advices = state.advices ?? [];
  const chain: LinkedNode[] = advices.map((advice) => ({
    id: advice.name,
    title: advice.name,
    subtitle: `${kindLabel(advice.kind, lang)} / ${advice.pointcut}`,
    highlighted: advice.applied || highlight.has(`advice:${advice.name}`),
  }));

  return (
    <div style={wrapStyle}>
      <div style={topStyle}>
        <Box label={tl(LABELS.proxy, lang)} value={state.proxy} highlighted={highlight.has('proxy')} />
        <span style={arrowStyle}>-&gt;</span>
        <div style={chainStyle}>
          <div style={sectionLabelStyle}>{tl(LABELS.adviceChain, lang)}</div>
          {chain.length > 0 ? <LinkedNodes nodes={chain} /> : <span style={emptyStyle}>{tl(LABELS.noAdvice, lang)}</span>}
        </div>
        <span style={arrowStyle}>-&gt;</span>
        <Box label={tl(LABELS.target, lang)} value={state.target} highlighted={highlight.has('target')} />
      </div>

      <div style={statusStyle}>
        <Stat label={tl(LABELS.method, lang)} value={state.activeMethod || tl(LABELS.noActiveMethod, lang)} />
        <Stat label={tl(LABELS.phase, lang)} value={phaseLabel(state.phase, lang)} />
        <Stat
          label={tl(LABELS.pointcut, lang)}
          value={state.pointcutMatched ? tl(LABELS.matched, lang) : tl(LABELS.missed, lang)}
          tone={state.pointcutMatched ? 'good' : 'bad'}
        />
      </div>

      <section style={sectionStyle}>
        <div style={sectionLabelStyle}>{tl(LABELS.timing, lang)}</div>
        <ArrayGrid cells={timingCells(advices, highlight, lang)} />
      </section>

      <section style={sectionStyle}>
        <div style={sectionLabelStyle}>{tl(LABELS.execution, lang)}</div>
        {state.executed.length === 0 && state.stack.length === 0 ? (
          <span style={emptyStyle}>{tl(LABELS.noSteps, lang)}</span>
        ) : (
          <ArrayGrid cells={executionCells(state.executed, state.stack, highlight, lang)} />
        )}
      </section>
    </div>
  );
}

function timingCells(advices: Advice[], highlight: Set<string>, lang: Lang): ArrayCell[] {
  return ADVICE_ORDER.map((kind) => {
    const advice = advices.find((item) => item.kind === kind);
    const status = advice?.applied
      ? tl(LABELS.ran, lang)
      : advice
        ? tl(LABELS.registered, lang)
        : tl(LABELS.notRegistered, lang);
    return {
      key: kind,
      label: kindLabel(kind, lang),
      highlighted: Boolean(advice && (advice.applied || highlight.has(`advice:${advice.name}`))),
      content: (
        <div style={timingContentStyle}>
          <span style={timingTextStyle}>{tl(ADVICE_WHEN[kind], lang)}</span>
          <Pill text={status} tone={advice?.applied ? 'good' : advice ? 'accent' : undefined} />
        </div>
      ),
    };
  });
}

function executionCells(
  steps: Step[],
  stack: Frame[],
  highlight: Set<string>,
  lang: Lang,
): ArrayCell[] {
  const stackCells = stack.map((frame, i) => ({
    key: `stack-${i}`,
    label: `${i + 1}`,
    highlighted: highlight.has(frame.role) || highlight.has(`advice:${frame.name}`),
    content: (
      <div style={stepStyle}>
        <Pill text={roleLabel(frame.role, lang)} tone={frame.role === 'target' ? 'good' : 'accent'} />
        <span style={monoStyle}>{frame.name}</span>
      </div>
    ),
  }));

  const stepCells = steps.map((step, i) => ({
    key: step.id,
    label: `${stack.length + i + 1}`,
    highlighted: highlight.has(step.role) || highlight.has(`advice:${step.name}`),
    content: (
      <div style={stepStyle}>
        <Pill text={roleLabel(step.role, lang)} tone={step.role === 'target' ? 'good' : 'accent'} />
        <span style={monoStyle}>{step.name}</span>
        <span style={actionStyle}>{actionLabel(step.action, lang)}</span>
      </div>
    ),
  }));

  return [...stackCells, ...stepCells];
}

function Box({ label, value, highlighted }: { label: string; value: string; highlighted: boolean }) {
  return (
    <div style={{ ...boxStyle, ...(highlighted ? boxHighlightStyle : {}) }}>
      <div style={smallLabelStyle}>{label}</div>
      <div style={boxValueStyle}>{value}</div>
    </div>
  );
}

function Stat({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone?: 'good' | 'bad';
}) {
  return (
    <div style={statStyle}>
      <div style={smallLabelStyle}>{label}</div>
      <div style={{ ...statValueStyle, ...toneStyle(tone) }}>{value}</div>
    </div>
  );
}

function Pill({ text, tone }: { text: string; tone?: 'good' | 'bad' | 'accent' }) {
  return <span style={{ ...pillStyle, ...toneStyle(tone) }}>{text}</span>;
}

function kindLabel(kind: AdviceKind, lang: Lang) {
  if (kind === 'before') return tl(LABELS.before, lang);
  if (kind === 'after') return tl(LABELS.after, lang);
  if (kind === 'afterReturning') return tl(LABELS.afterReturning, lang);
  if (kind === 'afterThrowing') return tl(LABELS.afterThrowing, lang);
  return tl(LABELS.around, lang);
}

function roleLabel(role: Role, lang: Lang) {
  if (role === 'proxy') return tl(LABELS.proxyRole, lang);
  if (role === 'advice') return tl(LABELS.adviceRole, lang);
  return tl(LABELS.targetRole, lang);
}

function phaseLabel(phase: string, lang: Lang) {
  return PHASES[phase] ? tl(PHASES[phase], lang) : phase;
}

function actionLabel(action: string, lang: Lang) {
  if (action === 'intercept') return tl(LABELS.actionIntercept, lang);
  if (action === 'before') return tl(LABELS.actionBefore, lang);
  if (action === 'after') return tl(LABELS.actionAfter, lang);
  if (action === 'around.before') return tl(LABELS.actionAroundBefore, lang);
  if (action === 'around.after') return tl(LABELS.actionAroundAfter, lang);
  if (action === 'around.exception') return tl(LABELS.actionAroundException, lang);
  if (action === 'afterReturning') return tl(LABELS.actionAfterReturning, lang);
  if (action === 'afterThrowing') return tl(LABELS.actionAfterThrowing, lang);
  if (action === 'exception') return tl(LABELS.actionException, lang);
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
const arrowStyle: CSSProperties = { fontSize: 13, opacity: 0.55, fontFamily: 'monospace' };
const chainStyle: CSSProperties = {
  minWidth: 240,
  flex: '1 1 280px',
  padding: '6px 8px',
  border: '1px solid var(--border)',
  borderRadius: 8,
  background: 'var(--viz-box)',
};
const boxStyle: CSSProperties = {
  padding: '6px 10px',
  borderRadius: 8,
  border: '1px solid var(--border)',
  background: 'var(--viz-box)',
  minWidth: 136,
};
const boxHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.24)',
};
const boxValueStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13, fontWeight: 700 };
const smallLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.62 };
const sectionStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const sectionLabelStyle: CSSProperties = { fontSize: 12, opacity: 0.65, fontWeight: 700 };
const statusStyle: CSSProperties = { display: 'flex', gap: 8, flexWrap: 'wrap' };
const statStyle: CSSProperties = {
  minWidth: 132,
  padding: '5px 8px',
  borderRadius: 6,
  border: '1px solid var(--border)',
  background: 'var(--viz-box)',
};
const statValueStyle: CSSProperties = { fontSize: 13, fontWeight: 700, fontFamily: 'monospace' };
const timingContentStyle: CSSProperties = { display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' };
const timingTextStyle: CSSProperties = { fontSize: 12 };
const stepStyle: CSSProperties = { display: 'flex', gap: 7, alignItems: 'center', flexWrap: 'wrap' };
const monoStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 12, fontWeight: 700 };
const actionStyle: CSSProperties = { fontSize: 12, opacity: 0.78 };
const pillStyle: CSSProperties = {
  display: 'inline-flex',
  border: '1px solid var(--border)',
  borderRadius: 999,
  padding: '2px 7px',
  fontSize: 12,
  fontFamily: 'monospace',
  background: 'var(--viz-box)',
};
const emptyStyle: CSSProperties = { fontSize: 12, opacity: 0.45, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
