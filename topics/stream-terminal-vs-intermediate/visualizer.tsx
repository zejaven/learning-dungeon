import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  source: { en: 'source', ru: 'источник' },
  pipeline: { en: 'pipeline', ru: 'конвейер' },
  output: { en: 'output', ru: 'результат' },
  result: { en: 'result', ru: 'итог' },
  intermediate: { en: 'intermediate', ru: 'промежуточная' },
  terminal: { en: 'terminal', ru: 'терминальная' },
  current: { en: 'current element', ru: 'текущий элемент' },
  consumed: { en: 'stream consumed', ru: 'стрим потреблён' },
  shortCircuit: { en: 'short-circuited', ru: 'замкнут' },
  lazyHint: {
    en: 'No terminal yet — the pipeline is built but lazy (nothing has run).',
    ru: 'Терминала ещё нет — конвейер построен, но ленив (ничего не выполнялось).',
  },
  runHint: {
    en: 'Run the code to visualize the stream pipeline.',
    ru: 'Запустите код, чтобы увидеть конвейер стрима.',
  },
};

const STATUS_COLOR: Record<string, string> = {
  pending: 'var(--viz-box)',
  active: 'var(--viz-active)',
  used: 'rgba(102,204,153,0.22)',
  skipped: 'rgba(255,99,99,0.18)',
};

interface SourceCell {
  value: string;
  status: string;
}
interface StageCell {
  label: string;
  kind: 'intermediate' | 'terminal';
  op: string;
  active: boolean;
}
interface StreamState {
  name: string;
  phase: string;
  consumed?: boolean;
  shortCircuited?: boolean;
  source: SourceCell[];
  stages: StageCell[];
  current?: { value: string; stageIndex: number } | null;
  output: string[];
  result?: string | null;
}

export default function StreamVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as StreamState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }
  const highlight = new Set(event?.highlight ?? []);

  const cells: ArrayCell[] = state.stages.map((stage, i) => ({
    key: i,
    label: stage.kind === 'terminal' ? tl(LABELS.terminal, lang) : tl(LABELS.intermediate, lang),
    highlighted: stage.active || highlight.has(`stage:${i}`),
    content: (
      <div style={stageRowStyle}>
        <span style={{ ...badgeStyle, ...(stage.kind === 'terminal' ? terminalBadge : intermediateBadge) }}>
          {stage.op}
        </span>
        <code style={stageLabelStyle}>{stage.label}</code>
      </div>
    ),
  }));

  const hasTerminal = state.stages.some((s) => s.kind === 'terminal');

  return (
    <div style={wrapStyle}>
      <div style={sectionStyle}>
        <div style={captionStyle}>{tl(LABELS.source, lang)}</div>
        <div style={chipRowStyle}>
          {state.source.map((cell, i) => (
            <span
              key={i}
              style={{
                ...chipStyle,
                background: STATUS_COLOR[cell.status] ?? 'var(--viz-box)',
                ...(highlight.has(`source:${i}`) ? chipActiveStyle : {}),
              }}
            >
              {cell.value}
            </span>
          ))}
        </div>
      </div>

      {state.current && (
        <div style={currentStyle}>
          {tl(LABELS.current, lang)}: <code style={currentValueStyle}>{state.current.value}</code>
        </div>
      )}

      <div style={sectionStyle}>
        <div style={captionStyle}>{tl(LABELS.pipeline, lang)}</div>
        <ArrayGrid cells={cells} />
        {!hasTerminal && <div style={lazyStyle}>{tl(LABELS.lazyHint, lang)}</div>}
      </div>

      <div style={sectionStyle}>
        <div style={captionStyle}>{tl(LABELS.output, lang)}</div>
        <div style={chipRowStyle}>
          {state.output.length === 0 ? (
            <span style={emptyStyle}>—</span>
          ) : (
            state.output.map((value, i) => (
              <span key={i} style={{ ...chipStyle, background: STATUS_COLOR.used }}>
                {value}
              </span>
            ))
          )}
        </div>
      </div>

      <div style={footRowStyle}>
        {state.result != null && (
          <span style={resultStyle}>
            {tl(LABELS.result, lang)}: <code>{state.result}</code>
          </span>
        )}
        {state.shortCircuited && <span style={flagStyle}>⚡ {tl(LABELS.shortCircuit, lang)}</span>}
        {state.consumed && <span style={flagStyle}>✓ {tl(LABELS.consumed, lang)}</span>}
      </div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const sectionStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 4 };
const captionStyle: CSSProperties = { fontSize: 11, opacity: 0.6, textTransform: 'uppercase', letterSpacing: 0.5 };
const chipRowStyle: CSSProperties = { display: 'flex', flexWrap: 'wrap', gap: 6 };
const chipStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '3px 9px',
  fontFamily: 'monospace',
  fontSize: 13,
};
const chipActiveStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.35)',
};
const stageRowStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 8 };
const badgeStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 11,
  padding: '1px 7px',
  borderRadius: 4,
  fontWeight: 700,
};
const intermediateBadge: CSSProperties = { background: 'var(--viz-badge)' };
const terminalBadge: CSSProperties = { background: 'rgba(102,204,153,0.30)' };
const stageLabelStyle: CSSProperties = { fontSize: 13 };
const currentStyle: CSSProperties = { fontSize: 13 };
const currentValueStyle: CSSProperties = { fontWeight: 700 };
const lazyStyle: CSSProperties = { fontSize: 12, opacity: 0.7, fontStyle: 'italic', marginTop: 2 };
const footRowStyle: CSSProperties = { display: 'flex', gap: 14, flexWrap: 'wrap', alignItems: 'center' };
const resultStyle: CSSProperties = { fontSize: 14, fontWeight: 600 };
const flagStyle: CSSProperties = { fontSize: 13, color: 'var(--accent)', fontWeight: 600 };
const emptyStyle: CSSProperties = { fontSize: 13, opacity: 0.4 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
