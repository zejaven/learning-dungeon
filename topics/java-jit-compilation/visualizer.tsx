import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang } from '@app/i18n';

type Localized = { en: string; ru: string };

const LABELS = {
  compileThreshold: { en: 'compile threshold', ru: 'порог компиляции' },
  totalCalls: { en: 'total calls', ru: 'всего вызовов' },
  activeMethod: { en: 'active method', ru: 'активный метод' },
  phase: { en: 'phase', ru: 'фаза' },
  pipeline: { en: 'JVM pipeline', ru: 'конвейер JVM' },
  methods: { en: 'methods', ru: 'методы' },
  calls: { en: 'calls', ru: 'вызовов' },
  active: { en: 'active now', ru: 'активно сейчас' },
  ready: { en: 'ready', ru: 'готово' },
  compiledReady: { en: 'compiled code ready', ru: 'compiled code готов' },
  inlined: { en: 'inlined', ru: 'встроено' },
  eliminated: { en: 'removed allocation', ru: 'убрана аллокация' },
  deopts: { en: 'deopt', ru: 'deopt' },
  none: { en: '-', ru: '-' },
  runHint: {
    en: 'Run the code to visualize JVM warmup and JIT compilation.',
    ru: 'Запустите код, чтобы увидеть прогрев JVM и JIT compilation.',
  },
  phaseValue: {
    ready: { en: 'ready', ru: 'готово' },
    interpreting: { en: 'interpreting', ru: 'интерпретация' },
    profiling: { en: 'profiling', ru: 'профилирование' },
    compiling: { en: 'compiling', ru: 'компиляция' },
    optimized: { en: 'optimized', ru: 'оптимизировано' },
    deoptimized: { en: 'deoptimized', ru: 'deoptimized' },
  } as Record<string, Localized>,
  stage: {
    bytecode: { en: 'bytecode', ru: 'bytecode' },
    interpreter: { en: 'interpreter', ru: 'интерпретатор' },
    profiler: { en: 'profiler', ru: 'profiler' },
    compiler: { en: 'JIT compiler', ru: 'JIT compiler' },
    'machine-code': { en: 'machine code', ru: 'машинный код' },
  } as Record<string, Localized>,
  mode: {
    cold: { en: 'cold', ru: 'cold' },
    interpreted: { en: 'interpreted', ru: 'интерпретируется' },
    hot: { en: 'hot', ru: 'hot' },
    compiled: { en: 'compiled', ru: 'compiled' },
  } as Record<string, Localized>,
};

interface JitStage {
  id: string;
  active: boolean;
  ready: boolean;
}

interface JitMethod {
  name: string;
  calls: number;
  mode: string;
  compiled: boolean;
  inlinedMethods: string[];
  eliminatedAllocations: string[];
  deoptimizations: number;
  assumptions: string[];
}

interface JitState {
  vmName: string;
  compileThreshold: number;
  totalCalls: number;
  phase: string;
  activeMethod: string | null;
  stages: JitStage[];
  methods: JitMethod[];
}

export default function JitVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as JitState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const phaseLabel = localized(LABELS.phaseValue, state.phase);
  const stageCells: ArrayCell[] = state.stages.map((stage) => ({
    key: stage.id,
    label: tl(localized(LABELS.stage, stage.id), lang),
    highlighted: stage.active || highlight.has(`stage:${stage.id}`),
    content: (
      <span style={stageStatusStyle}>
        {stage.active
          ? tl(LABELS.active, lang)
          : stage.ready
            ? tl(LABELS.compiledReady, lang)
            : tl(LABELS.ready, lang)}
      </span>
    ),
  }));

  const methodNodes: LinkedNode[] = state.methods.map((method) => ({
    id: method.name,
    title: method.name,
    subtitle: methodSubtitle(method, lang),
    highlighted: highlight.has(`method:${method.name}`),
  }));

  return (
    <div style={wrapStyle}>
      <div style={statsStyle}>
        <Stat label={state.vmName} value="" />
        <Stat label={tl(LABELS.compileThreshold, lang)} value={state.compileThreshold} />
        <Stat label={tl(LABELS.totalCalls, lang)} value={state.totalCalls} />
        <Stat label={tl(LABELS.activeMethod, lang)} value={state.activeMethod ?? tl(LABELS.none, lang)} />
        <Stat label={tl(LABELS.phase, lang)} value={tl(phaseLabel, lang)} />
      </div>

      <section style={sectionStyle}>
        <div style={sectionTitleStyle}>{tl(LABELS.pipeline, lang)}</div>
        <ArrayGrid cells={stageCells} />
      </section>

      <section style={sectionStyle}>
        <div style={sectionTitleStyle}>{tl(LABELS.methods, lang)}</div>
        <LinkedNodes nodes={methodNodes} />
      </section>
    </div>
  );
}

function methodSubtitle(method: JitMethod, lang: 'en' | 'ru') {
  const parts = [
    `${method.calls} ${tl(LABELS.calls, lang)}`,
    tl(localized(LABELS.mode, method.mode), lang),
  ];
  if (method.inlinedMethods.length > 0) {
    parts.push(`${tl(LABELS.inlined, lang)} ${method.inlinedMethods.join(', ')}`);
  }
  if (method.eliminatedAllocations.length > 0) {
    parts.push(`${tl(LABELS.eliminated, lang)} ${method.eliminatedAllocations.join(', ')}`);
  }
  if (method.deoptimizations > 0) {
    parts.push(`${method.deoptimizations} ${tl(LABELS.deopts, lang)}`);
  }
  return parts.join(' | ');
}

function localized(map: Record<string, Localized>, key: string): Localized {
  return map[key] ?? { en: key, ru: key };
}

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      {value !== '' && <div style={statValueStyle}>{value}</div>}
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const statsStyle: CSSProperties = { display: 'flex', gap: 16, alignItems: 'flex-end', flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'left', minWidth: 96 };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 16, fontWeight: 700, fontFamily: 'monospace' };
const sectionStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const sectionTitleStyle: CSSProperties = { fontSize: 12, fontWeight: 700, opacity: 0.75 };
const stageStatusStyle: CSSProperties = { fontSize: 12, opacity: 0.8 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
