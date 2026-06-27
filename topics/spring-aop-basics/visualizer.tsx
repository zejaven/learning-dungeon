import type { CSSProperties, ReactNode } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize Spring AOP.',
    ru: 'Запустите код, чтобы визуализировать Spring AOP.',
  },
  proxy: { en: 'proxy', ru: 'proxy' },
  target: { en: 'target bean', ru: 'целевой bean' },
  adviceChain: { en: 'advice chain', ru: 'цепочка advice' },
  businessMethods: { en: 'business methods', ru: 'бизнес-методы' },
  execution: { en: 'current execution', ru: 'текущее выполнение' },
  activeMethod: { en: 'method', ru: 'метод' },
  phase: { en: 'phase', ru: 'фаза' },
  pointcut: { en: 'pointcut', ru: 'pointcut' },
  matched: { en: 'matched', ru: 'подошёл' },
  missed: { en: 'not matched', ru: 'не подошёл' },
  noActiveMethod: { en: 'no active method', ru: 'нет активного метода' },
  noAdvice: { en: 'no advice', ru: 'нет advice' },
  noConcerns: { en: 'no copied concern', ru: 'нет копии логики' },
  noSteps: { en: 'no executed steps', ru: 'нет выполненных шагов' },
  proxyCall: { en: 'through proxy', ru: 'через proxy' },
  directCall: { en: 'direct target call', ru: 'прямой вызов target' },
  none: { en: 'none', ru: 'нет' },
  before: { en: 'before', ru: 'before' },
  afterReturning: { en: 'after returning', ru: 'after returning' },
  afterThrowing: { en: 'after throwing', ru: 'after throwing' },
  around: { en: 'around', ru: 'around' },
  proxyRole: { en: 'proxy', ru: 'proxy' },
  adviceRole: { en: 'advice', ru: 'advice' },
  targetRole: { en: 'target', ru: 'target' },
  actionIntercept: { en: 'intercept', ru: 'перехват' },
  actionDirect: { en: 'direct call', ru: 'прямой вызов' },
  actionBefore: { en: 'before advice', ru: 'before advice' },
  actionAroundBefore: { en: 'around before proceed()', ru: 'around до proceed()' },
  actionAroundAfter: { en: 'around after proceed()', ru: 'around после proceed()' },
  actionAroundException: { en: 'around sees exception', ru: 'around видит exception' },
  actionAfterReturning: { en: 'after returning', ru: 'after returning' },
  actionAfterThrowing: { en: 'after throwing', ru: 'after throwing' },
  actionException: { en: 'throws exception', ru: 'бросает exception' },
};

type AdviceKind = 'before' | 'afterReturning' | 'afterThrowing' | 'around';
type Role = 'proxy' | 'advice' | 'target';

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

interface BusinessMethod {
  name: string;
  concerns: string[];
}

interface Step {
  id: string;
  role: Role;
  name: string;
  method: string;
  action: string;
}

interface AopState {
  target: string;
  proxy: string;
  activeMethod: string;
  callMode: 'none' | 'proxy' | 'direct';
  phase: string;
  pointcutMatched: boolean;
  advices: Advice[];
  stack: Frame[];
  businessMethods: BusinessMethod[];
  executed: Step[];
}

export default function AopVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as AopState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const adviceNodes: LinkedNode[] = (state.advices ?? []).map((advice) => ({
    id: advice.name,
    title: advice.name,
    subtitle: `${kindLabel(advice.kind, lang)} / ${advice.pointcut}`,
    highlighted: advice.applied || highlight.has(`advice:${advice.name}`),
  }));

  return (
    <div style={wrapStyle}>
      <div style={topStyle}>
        <Box
          title={tl(LABELS.proxy, lang)}
          value={state.proxy}
          highlighted={highlight.has('proxy')}
          tone="accent"
        />
        <span style={arrowStyle}>→</span>
        <div style={advicePanelStyle}>
          <div style={sectionLabelStyle}>{tl(LABELS.adviceChain, lang)}</div>
          {adviceNodes.length > 0 ? (
            <LinkedNodes nodes={adviceNodes} />
          ) : (
            <span style={emptyStyle}>{tl(LABELS.noAdvice, lang)}</span>
          )}
        </div>
        <span style={arrowStyle}>→</span>
        <Box
          title={tl(LABELS.target, lang)}
          value={state.target}
          highlighted={highlight.has('target')}
        />
      </div>

      <div style={statusStyle}>
        <Stat label={tl(LABELS.activeMethod, lang)} value={state.activeMethod || tl(LABELS.noActiveMethod, lang)} />
        <Stat label={tl(LABELS.phase, lang)} value={phaseLabel(state.phase, lang)} />
        <Stat label={tl(LABELS.proxy, lang)} value={modeLabel(state.callMode, lang)} />
        <Stat
          label={tl(LABELS.pointcut, lang)}
          value={state.pointcutMatched ? tl(LABELS.matched, lang) : tl(LABELS.missed, lang)}
          tone={state.pointcutMatched ? 'good' : 'bad'}
        />
      </div>

      <Section label={tl(LABELS.businessMethods, lang)}>
        {state.businessMethods.length === 0 ? (
          <span style={emptyStyle}>{tl(LABELS.noConcerns, lang)}</span>
        ) : (
          <ArrayGrid cells={businessCells(state.businessMethods, highlight, lang)} />
        )}
      </Section>

      <Section label={tl(LABELS.execution, lang)}>
        {state.executed.length === 0 && state.stack.length === 0 ? (
          <span style={emptyStyle}>{tl(LABELS.noSteps, lang)}</span>
        ) : (
          <ArrayGrid cells={executionCells(state.executed, state.stack, highlight, lang)} />
        )}
      </Section>
    </div>
  );
}

function businessCells(methods: BusinessMethod[], highlight: Set<string>, lang: Lang): ArrayCell[] {
  return methods.map((method) => ({
    key: method.name,
    label: `${method.name}()`,
    highlighted: highlight.has(`method:${method.name}`),
    content:
      method.concerns.length === 0 ? (
        <span style={emptyStyle}>{tl(LABELS.noConcerns, lang)}</span>
      ) : (
        <div style={pillWrapStyle}>
          {method.concerns.map((concern, i) => (
            <Pill key={`${method.name}-${concern}-${i}`} text={concern} tone="bad" />
          ))}
        </div>
      ),
  }));
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
  highlighted,
  tone,
}: {
  title: string;
  value: string;
  highlighted: boolean;
  tone?: 'accent';
}) {
  return (
    <div style={{ ...boxStyle, ...(highlighted ? boxHighlightStyle : {}) }}>
      <div style={smallLabelStyle}>{title}</div>
      <div style={{ ...boxValueStyle, ...(tone === 'accent' ? { color: 'var(--accent)' } : {}) }}>
        {value}
      </div>
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

function Pill({ text, tone }: { text: string; tone: 'good' | 'bad' | 'accent' }) {
  return <span style={{ ...pillStyle, ...toneStyle(tone) }}>{text}</span>;
}

function kindLabel(kind: AdviceKind, lang: Lang) {
  if (kind === 'before') return tl(LABELS.before, lang);
  if (kind === 'afterReturning') return tl(LABELS.afterReturning, lang);
  if (kind === 'afterThrowing') return tl(LABELS.afterThrowing, lang);
  return tl(LABELS.around, lang);
}

function roleLabel(role: Role, lang: Lang) {
  if (role === 'proxy') return tl(LABELS.proxyRole, lang);
  if (role === 'advice') return tl(LABELS.adviceRole, lang);
  return tl(LABELS.targetRole, lang);
}

function modeLabel(mode: AopState['callMode'], lang: Lang) {
  if (mode === 'proxy') return tl(LABELS.proxyCall, lang);
  if (mode === 'direct') return tl(LABELS.directCall, lang);
  return tl(LABELS.none, lang);
}

function phaseLabel(phase: string, lang: Lang) {
  if (phase === 'afterReturning') return tl(LABELS.afterReturning, lang);
  if (phase === 'afterThrowing') return tl(LABELS.afterThrowing, lang);
  return phase || tl(LABELS.none, lang);
}

function actionLabel(action: string, lang: Lang) {
  if (action === 'intercept') return tl(LABELS.actionIntercept, lang);
  if (action === 'direct') return tl(LABELS.actionDirect, lang);
  if (action === 'before') return tl(LABELS.actionBefore, lang);
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
const arrowStyle: CSSProperties = { fontSize: 18, opacity: 0.55 };
const advicePanelStyle: CSSProperties = {
  minWidth: 210,
  flex: '1 1 260px',
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
  minWidth: 132,
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
const stepStyle: CSSProperties = { display: 'flex', gap: 7, alignItems: 'center', flexWrap: 'wrap' };
const monoStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 12, fontWeight: 700 };
const actionStyle: CSSProperties = { fontSize: 12, opacity: 0.78 };
const emptyStyle: CSSProperties = { fontSize: 12, opacity: 0.45, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
