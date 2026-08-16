import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize loop and Stream work.',
    ru: 'Запустите код, чтобы визуализировать работу цикла и Stream.',
  },
  source: { en: 'source', ru: 'источник' },
  pipeline: { en: 'pipeline', ru: 'pipeline' },
  counters: { en: 'counters', ru: 'счётчики' },
  chunks: { en: 'chunks', ru: 'части' },
  result: { en: 'result', ru: 'результат' },
  value: { en: 'value', ru: 'значение' },
  sum: { en: 'sum', ru: 'сумма' },
};

const STAGES: Record<string, { en: string; ru: string }> = {
  source: { en: 'source', ru: 'источник' },
  loop: { en: 'loop body', ru: 'тело цикла' },
  filter: { en: 'filter', ru: 'filter' },
  map: { en: 'map', ru: 'map' },
  reduce: { en: 'reduce', ru: 'reduce' },
  terminal: { en: 'terminal op', ru: 'terminal op' },
  boxing: { en: 'boxing / unboxing', ru: 'boxing / unboxing' },
  primitive: { en: 'IntStream', ru: 'IntStream' },
  split: { en: 'split', ru: 'разделение' },
  merge: { en: 'merge', ru: 'объединение' },
};

const STAT_LABELS: Record<string, { en: string; ru: string }> = {
  loopIterations: { en: 'loop iterations', ru: 'итерации цикла' },
  stageCalls: { en: 'stage calls', ru: 'вызовы стадий' },
  boxedConversions: { en: 'boxed conversions', ru: 'boxed conversions' },
  primitiveSteps: { en: 'primitive steps', ru: 'primitive steps' },
  splits: { en: 'splits', ru: 'разделения' },
  merges: { en: 'merges', ru: 'объединения' },
};

const STATUSES: Record<string, { en: string; ru: string }> = {
  idle: { en: 'idle', ru: 'ожидает' },
  ready: { en: 'ready', ru: 'готово' },
  waiting: { en: 'waiting', ru: 'ожидает' },
  active: { en: 'active', ru: 'активно' },
  current: { en: 'current', ru: 'текущий' },
  accepted: { en: 'accepted', ru: 'принят' },
  rejected: { en: 'rejected', ru: 'отброшен' },
  mapped: { en: 'mapped', ru: 'mapped' },
  done: { en: 'done', ru: 'готово' },
  boxed: { en: 'boxed', ru: 'boxed' },
  primitive: { en: 'primitive', ru: 'primitive' },
  split: { en: 'split', ru: 'разделено' },
  merged: { en: 'merged', ru: 'объединено' },
};

interface SourceItem {
  index: number;
  value: number;
  status: string;
}

interface Stage {
  id: string;
  label: string;
  status: string;
  value: string;
}

interface Chunk {
  id: string;
  values: number[];
  sum: number;
  status: string;
}

interface Counters {
  loopIterations: number;
  stageCalls: number;
  boxedConversions: number;
  primitiveSteps: number;
  splits: number;
  merges: number;
}

interface StreamState {
  mode: string;
  source: SourceItem[];
  stages: Stage[];
  chunks: Chunk[];
  counters: Counters;
  result: string;
}

export default function StreamsVsLoopsVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as StreamState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const cells: ArrayCell[] = state.source.map((item) => ({
    key: item.index,
    label: `[${item.index}]`,
    highlighted: highlight.has(`item:${item.index}`),
    content: (
      <div style={itemStyle}>
        <span style={itemValueStyle}>{item.value}</span>
        <span style={statusStyle}>{statusLabel(item.status, lang)}</span>
      </div>
    ),
  }));

  return (
    <div style={wrapStyle}>
      <SectionTitle text={tl(LABELS.source, lang)} />
      <ArrayGrid cells={cells} />

      {state.stages.length > 0 && (
        <>
          <SectionTitle text={tl(LABELS.pipeline, lang)} />
          <div style={stageRowStyle}>
            {state.stages.map((stage) => (
              <div
                key={stage.id}
                style={{
                  ...stageStyle,
                  ...(highlight.has(`stage:${stage.id}`) ? activeStageStyle : {}),
                }}
              >
                <div style={stageNameStyle}>{stageLabel(stage.id, lang)}</div>
                <div style={statusStyle}>{statusLabel(stage.status, lang)}</div>
                {stage.value && <div style={stageValueStyle}>{stage.value}</div>}
              </div>
            ))}
          </div>
        </>
      )}

      {state.chunks.length > 0 && (
        <>
          <SectionTitle text={tl(LABELS.chunks, lang)} />
          <div style={chunkRowStyle}>
            {state.chunks.map((chunk) => (
              <div key={chunk.id} style={chunkStyle}>
                <div style={stageNameStyle}>{chunk.id}</div>
                <div style={chunkValuesStyle}>[{chunk.values.join(', ')}]</div>
                <div style={statusStyle}>
                  {tl(LABELS.sum, lang)} {chunk.sum} / {statusLabel(chunk.status, lang)}
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      <SectionTitle text={tl(LABELS.counters, lang)} />
      <div style={statsStyle}>
        {Object.entries(state.counters).map(([key, value]) => (
          <Stat
            key={key}
            label={statLabel(key, lang)}
            value={value}
            highlight={highlight.has(`counter:${key}`)}
          />
        ))}
        {state.result && (
          <Stat label={tl(LABELS.result, lang)} value={state.result} highlight />
        )}
      </div>
    </div>
  );
}

function SectionTitle({ text }: { text: string }) {
  return <div style={sectionTitleStyle}>{text}</div>;
}

function Stat({ label, value, highlight }: { label: string; value: number | string; highlight?: boolean }) {
  return (
    <div style={{ ...statStyle, ...(highlight ? activeStatStyle : {}) }}>
      <div style={statLabelStyle}>{label}</div>
      <div style={statValueStyle}>{value}</div>
    </div>
  );
}

function stageLabel(id: string, lang: Lang) {
  return tl(STAGES[id] ?? { en: id, ru: id }, lang);
}

function statLabel(id: string, lang: Lang) {
  return tl(STAT_LABELS[id] ?? { en: id, ru: id }, lang);
}

function statusLabel(status: string, lang: Lang) {
  return tl(STATUSES[status] ?? { en: status, ru: status }, lang);
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 10 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
const sectionTitleStyle: CSSProperties = {
  fontSize: 11,
  textTransform: 'uppercase',
  color: 'var(--muted)',
  fontWeight: 700,
};
const itemStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 10 };
const itemValueStyle: CSSProperties = {
  minWidth: 36,
  fontFamily: 'monospace',
  fontSize: 16,
  fontWeight: 700,
};
const statusStyle: CSSProperties = { fontSize: 12, color: 'var(--muted)' };
const stageRowStyle: CSSProperties = { display: 'flex', gap: 8, flexWrap: 'wrap' };
const stageStyle: CSSProperties = {
  minWidth: 116,
  padding: '8px 10px',
  borderRadius: 6,
  background: 'var(--viz-box)',
  border: '1px solid transparent',
};
const activeStageStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-active)',
};
const stageNameStyle: CSSProperties = { fontSize: 13, fontWeight: 700 };
const stageValueStyle: CSSProperties = {
  marginTop: 4,
  fontFamily: 'monospace',
  fontSize: 15,
};
const chunkRowStyle: CSSProperties = { display: 'flex', gap: 8, flexWrap: 'wrap' };
const chunkStyle: CSSProperties = {
  minWidth: 116,
  padding: '8px 10px',
  borderRadius: 6,
  background: 'var(--viz-box)',
};
const chunkValuesStyle: CSSProperties = {
  marginTop: 4,
  fontFamily: 'monospace',
  fontSize: 13,
};
const statsStyle: CSSProperties = { display: 'flex', flexWrap: 'wrap', gap: 8 };
const statStyle: CSSProperties = {
  minWidth: 112,
  padding: '7px 9px',
  borderRadius: 6,
  background: 'var(--viz-box)',
};
const activeStatStyle: CSSProperties = { boxShadow: 'inset 2px 0 0 var(--accent)' };
const statLabelStyle: CSSProperties = { fontSize: 11, color: 'var(--muted)' };
const statValueStyle: CSSProperties = {
  marginTop: 2,
  fontSize: 17,
  fontWeight: 700,
  fontFamily: 'monospace',
};
