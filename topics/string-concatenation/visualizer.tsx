import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  strategy: { en: 'Strategy', ru: 'Стратегия' },
  concat: { en: '+ in a loop', ru: '+ в цикле' },
  builder: { en: 'StringBuilder', ru: 'StringBuilder' },
  live: { en: 'Live objects', ru: 'Живые объекты' },
  length: { en: 'length', ru: 'длина' },
  capacity: { en: 'capacity', ru: 'ёмкость' },
  allocations: { en: 'objects allocated', ru: 'объектов выделено' },
  copies: { en: 'chars copied', ru: 'символов скопировано' },
  garbage: { en: 'garbage', ru: 'мусор' },
  garbageObjects: { en: 'objects', ru: 'объектов' },
  garbageChars: { en: 'chars', ru: 'символов' },
  iteration: { en: 'pieces added', ru: 'кусков добавлено' },
  runHint: {
    en: 'Run the code to see how each strategy copies characters and produces garbage.',
    ru: 'Запустите код, чтобы увидеть, как каждая стратегия копирует символы и создаёт мусор.',
  },
};

interface LiveObject {
  id: string;
  kind: 'String' | 'char[]';
  value: string;
  len: number;
  capacity?: number | null;
}
interface SceneState {
  strategy: 'CONCAT' | 'BUILDER';
  iteration: number;
  piece?: string;
  length: number;
  capacity?: number | null;
  allocations: number;
  copies: number;
  garbageObjects: number;
  garbageChars: number;
  live: LiveObject[];
}

export default function StringConcatVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as SceneState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const isBuilder = state.strategy === 'BUILDER';

  const boxes: Box[] = state.live.map((o) => {
    const cap = o.kind === 'char[]' && o.capacity != null ? o.capacity : null;
    return {
      id: o.id,
      title: o.value.length === 0 ? '""' : `"${o.value}"`,
      subtitle:
        cap != null
          ? `char[] #${o.id} · ${o.len}/${cap}`
          : `${o.kind} #${o.id} · ${o.len}`,
      highlighted: highlight.has(`obj:${o.id}`),
    };
  });

  return (
    <div style={wrapStyle}>
      <div style={pillRowStyle}>
        <span style={labelStyle}>{tl(LABELS.strategy, lang)}:</span>
        <span style={{ ...pillStyle, ...(isBuilder ? builderPillStyle : concatPillStyle) }}>
          {tl(isBuilder ? LABELS.builder : LABELS.concat, lang)}
        </span>
      </div>

      <div style={metricsStyle}>
        <Metric label={tl(LABELS.iteration, lang)} value={state.iteration} />
        <Metric label={tl(LABELS.length, lang)} value={state.length} />
        {isBuilder && state.capacity != null && (
          <Metric
            label={tl(LABELS.capacity, lang)}
            value={state.capacity}
            highlighted={highlight.has('metric:capacity')}
          />
        )}
        <Metric label={tl(LABELS.allocations, lang)} value={state.allocations} />
        <Metric
          label={tl(LABELS.copies, lang)}
          value={state.copies}
          highlighted={highlight.has('metric:copies')}
        />
        <Metric
          label={`${tl(LABELS.garbage, lang)} (${tl(LABELS.garbageObjects, lang)})`}
          value={state.garbageObjects}
          danger
          highlighted={highlight.has('metric:garbage')}
        />
        <Metric
          label={`${tl(LABELS.garbage, lang)} (${tl(LABELS.garbageChars, lang)})`}
          value={state.garbageChars}
          danger
          highlighted={highlight.has('metric:garbage')}
        />
      </div>

      <div style={sectionLabelStyle}>{tl(LABELS.live, lang)}</div>
      <BoxGroup boxes={boxes} />
    </div>
  );
}

function Metric({
  label,
  value,
  danger,
  highlighted,
}: {
  label: string;
  value: number;
  danger?: boolean;
  highlighted?: boolean;
}) {
  return (
    <div
      style={{
        ...metricStyle,
        ...(highlighted ? metricHighlightStyle : {}),
        ...(danger && value > 0 ? metricDangerStyle : {}),
      }}
    >
      <div style={metricValueStyle}>{value}</div>
      <div style={metricLabelStyle}>{label}</div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 10 };
const pillRowStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 8 };
const labelStyle: CSSProperties = { fontSize: 12, opacity: 0.7 };
const pillStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 12,
  fontWeight: 700,
  padding: '2px 10px',
  borderRadius: 999,
  border: '1px solid var(--border)',
};
const concatPillStyle: CSSProperties = { background: 'rgba(220,80,80,0.18)' };
const builderPillStyle: CSSProperties = { background: 'rgba(80,180,120,0.18)' };
const metricsStyle: CSSProperties = { display: 'flex', flexWrap: 'wrap', gap: 8 };
const metricStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '6px 10px',
  background: 'var(--viz-box)',
  minWidth: 70,
  textAlign: 'center',
};
const metricHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.35)',
};
const metricDangerStyle: CSSProperties = {
  borderColor: 'rgba(220,80,80,0.6)',
  background: 'rgba(220,80,80,0.12)',
};
const metricValueStyle: CSSProperties = { fontWeight: 700, fontSize: 18, fontFamily: 'monospace' };
const metricLabelStyle: CSSProperties = { fontSize: 10, opacity: 0.65, textTransform: 'uppercase' };
const sectionLabelStyle: CSSProperties = {
  fontSize: 11,
  textTransform: 'uppercase',
  letterSpacing: 0.5,
  opacity: 0.55,
};
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
