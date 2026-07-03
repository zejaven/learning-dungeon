import type { CSSProperties, ReactNode } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { type Lang, tl, useLang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize JPA entity states.',
    ru: 'Запустите код, чтобы визуализировать состояния сущности JPA.',
  },
  context: { en: 'Persistence Context', ru: 'Persistence Context' },
  open: { en: 'open', ru: 'открыт' },
  closed: { en: 'closed', ru: 'закрыт' },
  managedCount: { en: 'managed objects', ru: 'managed-объектов' },
  operation: { en: 'operation', ru: 'операция' },
  database: { en: 'Database rows', ru: 'Строки базы данных' },
  sqlLog: { en: 'SQL on flush', ru: 'SQL при flush' },
  emptyState: { en: 'no entity here', ru: 'здесь нет сущности' },
  noRows: { en: 'no rows yet', ru: 'строк пока нет' },
  noSql: { en: 'no SQL emitted yet', ru: 'SQL ещё не появился' },
  id: { en: 'id', ru: 'id' },
  changed: { en: 'changed', ru: 'изменена' },
  pending: { en: 'pending', ru: 'ожидает' },
  deleted: { en: 'deleted', ru: 'удалена' },
};

const STATE_ROWS = [
  {
    id: 'TRANSIENT',
    title: { en: 'transient', ru: 'transient' },
    subtitle: { en: 'plain new Java object', ru: 'обычный new Java-объект' },
  },
  {
    id: 'MANAGED',
    title: { en: 'managed', ru: 'managed' },
    subtitle: { en: 'tracked by Persistence Context', ru: 'отслеживается Persistence Context' },
  },
  {
    id: 'DETACHED',
    title: { en: 'detached', ru: 'detached' },
    subtitle: { en: 'Java object outside tracking', ru: 'Java-объект вне отслеживания' },
  },
  {
    id: 'REMOVED',
    title: { en: 'removed', ru: 'removed' },
    subtitle: { en: 'managed, scheduled for DELETE', ru: 'managed, ожидает DELETE' },
  },
];

interface JpaEntity {
  handle: string;
  label: string;
  id: string;
  state: string;
  changed: boolean;
  pendingAction: string;
}

interface DatabaseRow {
  id: number;
  label: string;
  deleted: boolean;
}

interface SqlLogItem {
  n: number;
  kind: string;
  sql: string;
}

interface JpaState {
  contextName: string;
  contextOpen: boolean;
  operation: string;
  managedCount: number;
  entities: JpaEntity[];
  databaseRows: DatabaseRow[];
  sqlLog: SqlLogItem[];
}

export default function JpaEntityStatesVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as JpaState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const cells: ArrayCell[] = STATE_ROWS.map((row) => {
    const entities = state.entities.filter((entity) => entity.state === row.id);
    return {
      key: row.id,
      label: tl(row.title, lang),
      highlighted: highlight.has(`state:${row.id}`),
      content: (
        <div style={stateContentStyle}>
          <div style={stateSubtitleStyle}>{tl(row.subtitle, lang)}</div>
          <div style={entityRowStyle}>
            {entities.length === 0 ? (
              <span style={emptyStyle}>{tl(LABELS.emptyState, lang)}</span>
            ) : (
              entities.map((entity) => (
                <EntityBox
                  key={entity.handle}
                  entity={entity}
                  highlighted={highlight.has(`entity:${entity.handle}`)}
                  lang={lang}
                />
              ))
            )}
          </div>
        </div>
      ),
    };
  });

  return (
    <div style={wrapStyle}>
      <div style={statsStyle}>
        <Stat
          label={tl(LABELS.context, lang)}
          value={`${state.contextName} · ${state.contextOpen ? tl(LABELS.open, lang) : tl(LABELS.closed, lang)}`}
          strong={state.contextOpen}
        />
        <Stat label={tl(LABELS.managedCount, lang)} value={state.managedCount} />
        <Stat label={tl(LABELS.operation, lang)} value={state.operation} mono />
      </div>

      <ArrayGrid cells={cells} />

      <div style={bottomGridStyle}>
        <Panel title={tl(LABELS.database, lang)}>
          {state.databaseRows.length === 0 ? (
            <span style={emptyStyle}>{tl(LABELS.noRows, lang)}</span>
          ) : (
            <div style={rowListStyle}>
              {state.databaseRows.map((row) => (
                <div
                  key={row.id}
                  style={{
                    ...dbRowStyle,
                    ...(highlight.has(`db:${row.id}`) ? highlightedBoxStyle : {}),
                    ...(row.deleted ? deletedRowStyle : {}),
                  }}
                >
                  <code>#{row.id}</code>
                  <span>{row.label}</span>
                  {row.deleted && <span style={deletedBadgeStyle}>{tl(LABELS.deleted, lang)}</span>}
                </div>
              ))}
            </div>
          )}
        </Panel>

        <Panel title={tl(LABELS.sqlLog, lang)}>
          {state.sqlLog.length === 0 ? (
            <span style={emptyStyle}>{tl(LABELS.noSql, lang)}</span>
          ) : (
            <ol style={sqlListStyle}>
              {state.sqlLog.map((item) => (
                <li key={item.n} style={sqlRowStyle}>
                  <span style={kindBadgeStyle}>{item.kind}</span>
                  <code style={sqlStyle}>{item.sql}</code>
                </li>
              ))}
            </ol>
          )}
        </Panel>
      </div>
    </div>
  );
}

function EntityBox({
  entity,
  highlighted,
  lang,
}: {
  entity: JpaEntity;
  highlighted: boolean;
  lang: Lang;
}) {
  return (
    <div style={{ ...entityBoxStyle, ...(highlighted ? highlightedBoxStyle : {}) }}>
      <div style={entityTitleStyle}>
        <code>{entity.handle}</code>
        <span style={idStyle}>
          {tl(LABELS.id, lang)} {entity.id}
        </span>
      </div>
      <div style={labelStyle}>{entity.label}</div>
      <div style={badgeRowStyle}>
        {entity.changed && <span style={softBadgeStyle}>{tl(LABELS.changed, lang)}</span>}
        {entity.pendingAction && (
          <span style={accentBadgeStyle}>
            {tl(LABELS.pending, lang)} {entity.pendingAction}
          </span>
        )}
      </div>
    </div>
  );
}

function Stat({
  label,
  value,
  mono,
  strong,
}: {
  label: string;
  value: string | number;
  mono?: boolean;
  strong?: boolean;
}) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div
        style={{
          ...statValueStyle,
          fontFamily: mono ? 'monospace' : 'inherit',
          color: strong ? 'var(--text)' : 'var(--muted)',
        }}
      >
        {value}
      </div>
    </div>
  );
}

function Panel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div style={panelStyle}>
      <div style={panelTitleStyle}>{title}</div>
      {children}
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { minWidth: 120 };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 15, fontWeight: 700 };
const stateContentStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 5,
  width: '100%',
};
const stateSubtitleStyle: CSSProperties = { fontSize: 11, opacity: 0.62 };
const entityRowStyle: CSSProperties = { display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 6 };
const entityBoxStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '5px 8px',
  minWidth: 150,
  background: 'var(--viz-box)',
};
const highlightedBoxStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.35)',
};
const entityTitleStyle: CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'baseline',
  gap: 8,
  fontSize: 13,
  fontWeight: 700,
};
const idStyle: CSSProperties = { fontSize: 11, opacity: 0.68, fontFamily: 'monospace' };
const labelStyle: CSSProperties = { fontSize: 12, marginTop: 2 };
const badgeRowStyle: CSSProperties = { display: 'flex', gap: 4, flexWrap: 'wrap', marginTop: 4 };
const softBadgeStyle: CSSProperties = {
  fontSize: 10,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'rgba(91,141,239,0.16)',
  color: '#79a7ff',
};
const accentBadgeStyle: CSSProperties = {
  fontSize: 10,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'rgba(255,204,102,0.18)',
  color: 'var(--accent)',
};
const bottomGridStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
  gap: 12,
};
const panelStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: 8,
  background: 'rgba(255,255,255,0.02)',
};
const panelTitleStyle: CSSProperties = {
  fontSize: 11,
  textTransform: 'uppercase',
  letterSpacing: 0,
  opacity: 0.58,
  marginBottom: 6,
};
const rowListStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 4 };
const dbRowStyle: CSSProperties = {
  display: 'flex',
  gap: 8,
  alignItems: 'center',
  padding: '4px 6px',
  borderRadius: 6,
  background: 'var(--viz-box)',
  border: '1px solid transparent',
  fontSize: 12,
};
const deletedRowStyle: CSSProperties = { opacity: 0.5, textDecoration: 'line-through' };
const deletedBadgeStyle: CSSProperties = {
  marginLeft: 'auto',
  fontSize: 10,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'rgba(224,96,58,0.15)',
  color: '#e0603a',
  textDecoration: 'none',
};
const sqlListStyle: CSSProperties = {
  listStyle: 'none',
  margin: 0,
  padding: 0,
  display: 'flex',
  flexDirection: 'column',
  gap: 4,
};
const sqlRowStyle: CSSProperties = {
  display: 'flex',
  gap: 7,
  alignItems: 'baseline',
  padding: '4px 6px',
  borderRadius: 6,
  background: 'var(--viz-box)',
};
const kindBadgeStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 10,
  minWidth: 42,
  textAlign: 'center',
  padding: '1px 5px',
  borderRadius: 4,
  background: '#3aa76d',
  color: '#fff',
};
const sqlStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 12, overflowWrap: 'anywhere' };
const emptyStyle: CSSProperties = { fontSize: 12, opacity: 0.45, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
