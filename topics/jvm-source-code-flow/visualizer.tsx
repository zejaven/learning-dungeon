import type { CSSProperties, ReactNode } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize the JVM pipeline.',
    ru: 'Запустите код, чтобы увидеть JVM pipeline.',
  },
  className: { en: 'class', ru: 'класс' },
  currentStage: { en: 'current stage', ru: 'текущий этап' },
  activeMethod: { en: 'active method', ru: 'активный метод' },
  hotCalls: { en: 'hot calls', ru: 'hot вызовы' },
  none: { en: '-', ru: '-' },
  stages: { en: 'Pipeline', ru: 'Pipeline' },
  artifacts: { en: 'Artifacts', ru: 'Artifacts' },
  checks: { en: 'Verifier checks', ru: 'Проверки verifier' },
  memory: { en: 'Runtime memory', ru: 'Runtime память' },
  output: { en: 'Program output', ru: 'Вывод программы' },
  noChecks: { en: 'not verified yet', ru: 'еще не проверено' },
  noOutput: { en: 'no output yet', ru: 'вывода пока нет' },
  stage: {
    source: { en: '.java source', ru: '.java source' },
    javac: { en: 'javac compile', ru: 'javac compile' },
    bytecode: { en: '.class bytecode', ru: '.class bytecode' },
    classloader: { en: 'ClassLoader', ru: 'ClassLoader' },
    verifier: { en: 'Verifier', ru: 'Verifier' },
    runtime: { en: 'runtime', ru: 'runtime' },
    interpreter: { en: 'Interpreter', ru: 'Interpreter' },
    jit: { en: 'JIT / Code Cache', ru: 'JIT / Code Cache' },
  } as Record<string, { en: string; ru: string }>,
  status: {
    waiting: { en: 'waiting', ru: 'ожидает' },
    active: { en: 'active', ru: 'активен' },
    done: { en: 'done', ru: 'готово' },
  } as Record<string, { en: string; ru: string }>,
  artifactKind: {
    source: { en: 'source', ru: 'source' },
    bytecode: { en: 'bytecode', ru: 'bytecode' },
    metadata: { en: 'metadata', ru: 'metadata' },
    native: { en: 'native code', ru: 'native code' },
  } as Record<string, { en: string; ru: string }>,
  check: {
    'magic-number': { en: 'magic number', ru: 'magic number' },
    'bytecode-version': { en: 'bytecode version', ru: 'bytecode version' },
    'type-safety': { en: 'type safety', ru: 'type safety' },
    'stack-map-frames': { en: 'stack map frames', ru: 'stack map frames' },
  } as Record<string, { en: string; ru: string }>,
};

interface Stage {
  id: string;
  status: string;
}

interface Artifact {
  id: string;
  label: string;
  kind: string;
}

interface MemoryArea {
  area: string;
  items: string[];
}

interface JvmPipelineState {
  className: string;
  sourceFile: string;
  bytecodeFile: string;
  currentStage: string;
  activeMethod: string;
  hotMethod: string;
  hotCalls: number;
  jitThreshold: number;
  nativeCompiled: boolean;
  stages: Stage[];
  artifacts: Artifact[];
  checks: string[];
  memory: MemoryArea[];
  output: string[];
}

export default function JvmPipelineVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as JvmPipelineState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const currentStageLabel = LABELS.stage[state.currentStage] ?? LABELS.stage.runtime;
  const activeMethod = state.activeMethod ? `${state.activeMethod}()` : tl(LABELS.none, lang);

  const stageCells: ArrayCell[] = state.stages.map((stage) => {
    const label = LABELS.stage[stage.id] ?? { en: stage.id, ru: stage.id };
    const status = LABELS.status[stage.status] ?? { en: stage.status, ru: stage.status };
    const stageHighlighted =
      highlight.has(`stage:${stage.id}`) ||
      (stage.id === 'runtime' && highlight.has('stage:interpreter'));
    return {
      key: stage.id,
      label: tl(status, lang),
      highlighted: stageHighlighted,
      content: (
        <LinkedNodes
          nodes={[
            {
              id: stage.id,
              title: tl(label, lang),
              subtitle: tl(status, lang),
              highlighted: stageHighlighted,
            },
          ]}
        />
      ),
    };
  });

  const artifactNodes: LinkedNode[] = state.artifacts.map((artifact) => ({
    id: artifact.id,
    title: artifact.label,
    subtitle: tl(LABELS.artifactKind[artifact.kind] ?? { en: artifact.kind, ru: artifact.kind }, lang),
    highlighted: highlight.has(`artifact:${artifact.id}`),
  }));

  const memoryCells: ArrayCell[] = state.memory.map((area) => ({
    key: area.area,
    label: area.area,
    highlighted: highlight.has(`memory:${area.area}`),
    content: (
      <LinkedNodes
        nodes={area.items.map((item) => ({
          id: `${area.area}-${item}`,
          title: item,
          highlighted: highlight.has(`object:${item}`),
        }))}
      />
    ),
  }));

  return (
    <div style={wrapStyle}>
      <div style={statsStyle}>
        <Stat label={tl(LABELS.className, lang)} value={state.className} />
        <Stat label={tl(LABELS.currentStage, lang)} value={tl(currentStageLabel, lang)} />
        <Stat label={tl(LABELS.activeMethod, lang)} value={activeMethod} />
        <Stat label={tl(LABELS.hotCalls, lang)} value={`${state.hotCalls}/${state.jitThreshold}`} />
      </div>

      <Section title={tl(LABELS.stages, lang)}>
        <ArrayGrid cells={stageCells} />
      </Section>

      <Section title={tl(LABELS.artifacts, lang)}>
        <LinkedNodes nodes={artifactNodes} />
      </Section>

      <div style={twoColumnStyle}>
        <Section title={tl(LABELS.checks, lang)}>
          {state.checks.length ? (
            <div style={pillRowStyle}>
              {state.checks.map((check) => (
                <span key={check} style={pillStyle}>
                  {tl(LABELS.check[check] ?? { en: check, ru: check }, lang)}
                </span>
              ))}
            </div>
          ) : (
            <div style={emptyStyle}>{tl(LABELS.noChecks, lang)}</div>
          )}
        </Section>

        <Section title={tl(LABELS.output, lang)}>
          {state.output.length ? (
            <pre style={outputStyle}>{state.output.join('\n')}</pre>
          ) : (
            <div style={emptyStyle}>{tl(LABELS.noOutput, lang)}</div>
          )}
        </Section>
      </div>

      <Section title={tl(LABELS.memory, lang)}>
        <ArrayGrid cells={memoryCells} />
      </Section>
    </div>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section style={sectionStyle}>
      <div style={sectionTitleStyle}>{title}</div>
      {children}
    </section>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={statValueStyle}>{value}</div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { minWidth: 120 };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 15, fontWeight: 700, fontFamily: 'monospace' };
const sectionStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const sectionTitleStyle: CSSProperties = { fontSize: 12, fontWeight: 700, opacity: 0.72 };
const twoColumnStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
  gap: 14,
};
const pillRowStyle: CSSProperties = { display: 'flex', flexWrap: 'wrap', gap: 6 };
const pillStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '3px 7px',
  background: 'var(--viz-box)',
  fontSize: 12,
};
const outputStyle: CSSProperties = {
  margin: 0,
  padding: 8,
  borderRadius: 6,
  background: 'var(--viz-box)',
  fontSize: 12,
  whiteSpace: 'pre-wrap',
};
const emptyStyle: CSSProperties = { fontSize: 12, opacity: 0.45, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
