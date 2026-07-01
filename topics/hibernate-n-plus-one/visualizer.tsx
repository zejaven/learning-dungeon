import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  queries: { en: 'SQL queries', ru: 'SQL-запросов' },
  strategy: { en: 'strategy', ru: 'стратегия' },
  batchSize: { en: 'batch size', ru: 'batch size' },
  parents: { en: 'Loaded parents', ru: 'Загруженные родители' },
  log: { en: 'Query log', ru: 'Журнал запросов' },
  loaded: { en: 'children loaded', ru: 'дети загружены' },
  proxy: { en: 'collection is a proxy', ru: 'коллекция — прокси' },
  runHint: {
    en: 'Run the code to count the SQL queries.',
    ru: 'Запустите код, чтобы посчитать SQL-запросы.',
  },
};

const KIND_LABELS: Record<string, { en: string; ru: string }> = {
  root: { en: 'root', ru: 'корневой' },
  nplus1: { en: '+1', ru: '+1' },
  joinfetch: { en: 'join fetch', ru: 'join fetch' },
  entitygraph: { en: 'entity graph', ru: 'entity graph' },
  batch: { en: 'batch', ru: 'batch' },
};

const KIND_COLORS: Record<string, string> = {
  root: '#5b8def',
  nplus1: '#e0603a',
  joinfetch: '#3aa76d',
  entitygraph: '#3aa76d',
  batch: '#c79a3a',
};

interface Parent {
  id: number;
  label: string;
  childCount: number;
  loaded: boolean;
}
interface Query {
  n: number;
  kind: string;
  sql: string;
}
interface NPlusOneState {
  scene: string;
  strategy: string;
  batchSize: number;
  parentTable: string;
  childTable: string;
  queryCount: number;
  parents: Parent[];
  queries: Query[];
}

export default function NPlusOneVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as NPlusOneState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const extraQueries = state.queryCount - 1;
  const countBad = state.strategy === 'LAZY' && state.batchSize === 0 && extraQueries > 0;

  const parentCells: ArrayCell[] = state.parents.map((p) => ({
    key: p.id,
    label: `#${p.id}`,
    highlighted: highlight.has(`parent:${p.id}`),
    content: (
      <div style={parentRowStyle}>
        <span style={{ fontFamily: 'monospace' }}>{p.label}</span>
        <span
          style={{
            ...pillStyle,
            background: p.loaded ? 'rgba(58,167,109,0.18)' : 'rgba(224,96,58,0.15)',
            color: p.loaded ? '#3aa76d' : '#e0603a',
          }}
        >
          {p.loaded ? tl(LABELS.loaded, lang) : tl(LABELS.proxy, lang)}
        </span>
      </div>
    ),
  }));

  return (
    <div style={wrapStyle}>
      <div style={statsStyle}>
        <div style={statStyle}>
          <div style={statLabelStyle}>{tl(LABELS.queries, lang)}</div>
          <div
            style={{
              ...statValueStyle,
              color: countBad ? 'var(--accent)' : 'var(--text)',
            }}
          >
            {state.queryCount}
          </div>
        </div>
        <div style={statStyle}>
          <div style={statLabelStyle}>{tl(LABELS.strategy, lang)}</div>
          <div style={statStrategyStyle}>{state.strategy}</div>
        </div>
        {state.batchSize > 0 && (
          <div style={statStyle}>
            <div style={statLabelStyle}>{tl(LABELS.batchSize, lang)}</div>
            <div style={statValueStyle}>{state.batchSize}</div>
          </div>
        )}
      </div>

      {state.parents.length > 0 && (
        <div>
          <div style={sectionTitleStyle}>{tl(LABELS.parents, lang)}</div>
          <ArrayGrid cells={parentCells} />
        </div>
      )}

      <div>
        <div style={sectionTitleStyle}>{tl(LABELS.log, lang)}</div>
        <ol style={logStyle}>
          {state.queries.map((q) => {
            const isLast = q.n === state.queryCount;
            return (
              <li
                key={q.n}
                style={{
                  ...logRowStyle,
                  outline: isLast ? '1px solid var(--accent)' : 'none',
                }}
              >
                <span
                  style={{ ...kindBadgeStyle, background: KIND_COLORS[q.kind] ?? '#777' }}
                >
                  {tl(KIND_LABELS[q.kind] ?? { en: q.kind, ru: q.kind }, lang)}
                </span>
                <code style={sqlStyle}>{q.sql}</code>
              </li>
            );
          })}
        </ol>
      </div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const statsStyle: CSSProperties = { display: 'flex', gap: 20 };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 22, fontWeight: 700, fontFamily: 'monospace' };
const statStrategyStyle: CSSProperties = { fontSize: 15, fontWeight: 700, fontFamily: 'monospace' };
const sectionTitleStyle: CSSProperties = {
  fontSize: 11,
  textTransform: 'uppercase',
  letterSpacing: 0.5,
  opacity: 0.55,
  marginBottom: 6,
};
const parentRowStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  gap: 8,
  width: '100%',
};
const pillStyle: CSSProperties = {
  fontSize: 11,
  padding: '1px 8px',
  borderRadius: 10,
};
const logStyle: CSSProperties = {
  listStyle: 'none',
  margin: 0,
  padding: 0,
  display: 'flex',
  flexDirection: 'column',
  gap: 4,
};
const logRowStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  padding: '4px 6px',
  borderRadius: 6,
  background: 'var(--viz-box)',
};
const kindBadgeStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 10,
  fontWeight: 700,
  color: '#fff',
  padding: '1px 6px',
  borderRadius: 4,
  flexShrink: 0,
  minWidth: 64,
  textAlign: 'center',
};
const sqlStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 12.5 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
