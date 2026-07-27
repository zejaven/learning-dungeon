import type { CSSProperties, ReactNode } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid } from '@app/primitives/ArrayGrid';
import { BoxGroup } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to see where the registry lives, which thread writes into it, and which value each connection walks away with.',
    ru: 'Запустите код, чтобы увидеть, где живёт реестр, какой поток в него пишет и с каким значением уходит каждое соединение.',
  },
  stores: {
    INSTANCE_FIELD: { en: 'a field per connection', ru: 'поле на соединение' },
    PLAIN_MAP: { en: 'a static HashMap', ru: 'статический HashMap' },
    SYNCHRONIZED_MAP: { en: 'a synchronizedMap', ru: 'synchronizedMap' },
    CONCURRENT_MAP: { en: 'a ConcurrentHashMap', ru: 'ConcurrentHashMap' },
  },
  sources: {
    PLAIN_COUNTER: { en: 'nextId++', ru: 'nextId++' },
    REGISTRY_SIZE: { en: 'size() + 1', ru: 'size() + 1' },
    SYNCHRONIZED_COUNTER: { en: 'synchronized counter', ru: 'счётчик под synchronized' },
    ATOMIC_COUNTER: { en: 'AtomicLong', ru: 'AtomicLong' },
    RANDOM_UUID: { en: 'UUID.randomUUID()', ru: 'UUID.randomUUID()' },
    DB_SEQUENCE: { en: 'database sequence', ru: 'последовательность в БД' },
  },
  phases: {
    OPEN: { en: 'connected', ru: 'подключён' },
    JOINING: { en: 'registering', ru: 'регистрируется' },
    REGISTERED: { en: 'in the registry', ru: 'в реестре' },
    CLOSED: { en: 'closed', ru: 'закрыт' },
  },
  writesSerialized: { en: 'writes serialized per session', ru: 'записи в сессию сериализованы' },
  writesFree: { en: 'any thread may write to any session', ru: 'в любую сессию пишет любой поток' },
  nodes: { en: 'processes', ru: 'процессы' },
  counter: { en: 'counter', ru: 'счётчик' },
  entries: { en: 'entries', ru: 'записей' },
  connections: { en: 'connections and their threads', ru: 'соединения и их потоки' },
  noConnections: { en: 'nobody has connected yet', ru: 'пока никто не подключился' },
  registry: { en: 'session registry', ru: 'реестр сессий' },
  noRegistry: { en: 'nothing has been registered yet', ru: 'пока ничего не зарегистрировано' },
  issued: { en: 'values handed out', ru: 'выданные значения' },
  noIssued: { en: 'no value has been handed out yet', ru: 'пока не выдано ни одного значения' },
  wants: { en: 'wants', ru: 'хочет' },
  duplicate: { en: 'duplicate', ru: 'дубликат' },
  lost: { en: 'lost', ru: 'потеряна' },
  removed: { en: 'removed', ru: 'удалена' },
  leaked: { en: 'socket gone, entry left', ru: 'сокет исчез, запись осталась' },
  statOpened: { en: 'opened', ru: 'открыто' },
  statRegistered: { en: 'registered', ru: 'зарегистрировано' },
  statIssued: { en: 'values', ru: 'значений' },
  statDistinct: { en: 'distinct', ru: 'различных' },
  statDuplicates: { en: 'duplicates', ru: 'дубликатов' },
  statLost: { en: 'entries lost', ru: 'записей потеряно' },
  statLeaked: { en: 'entries leaked', ru: 'записей утекло' },
  statDelivered: { en: 'delivered', ru: 'доставлено' },
  statMissed: { en: 'missed', ru: 'пропущено' },
  statStale: { en: 'dead writes', ru: 'записей в мёртвое' },
  statWriteErrors: { en: 'write conflicts', ru: 'конфликтов записи' },
  comparison: {
    en: 'thread-safe and globally unique are two different guarantees',
    ru: 'потокобезопасно и глобально уникально — две разные гарантии',
  },
  sourceNames: {
    PLAIN_COUNTER: { en: 'long nextId++', ru: 'long nextId++' },
    REGISTRY_SIZE: { en: 'sessions.size() + 1', ru: 'sessions.size() + 1' },
    SYNCHRONIZED_COUNTER: { en: 'synchronized { n++ }', ru: 'synchronized { n++ }' },
    ATOMIC_COUNTER: { en: 'AtomicLong', ru: 'AtomicLong' },
    RANDOM_UUID: { en: 'UUID.randomUUID()', ru: 'UUID.randomUUID()' },
    DB_SEQUENCE: { en: 'DB sequence', ru: 'последовательность в БД' },
    SNOWFLAKE_ID: { en: 'Snowflake id', ru: 'Snowflake id' },
  },
  colSource: { en: 'where the value comes from', ru: 'откуда берётся значение' },
  colJvm: { en: 'unique in one JVM', ru: 'уникально в одной JVM' },
  colCluster: { en: 'unique across nodes', ru: 'уникально между узлами' },
  colOrdered: { en: 'ordered', ru: 'упорядочено' },
  colCoordination: { en: 'what must agree', ru: 'что должно договориться' },
  coordination: {
    NONE: { en: 'nothing', ru: 'ничего' },
    JVM_LOCK: { en: 'a lock in this JVM', ru: 'блокировка в этой JVM' },
    JVM_CAS: { en: 'a CAS in this JVM', ru: 'CAS в этой JVM' },
    DATABASE: { en: 'the database', ru: 'база данных' },
    UNIQUE_NODE_ID: { en: 'a unique node id', ru: 'уникальный номер узла' },
  },
  yes: { en: 'yes', ru: 'да' },
  no: { en: 'no', ru: 'нет' },
};

type StoreCode = keyof typeof LABELS.stores;
type SourceCode = keyof typeof LABELS.sources;
type PhaseCode = keyof typeof LABELS.phases;
type ComparisonSource = keyof typeof LABELS.sourceNames;
type CoordinationCode = keyof typeof LABELS.coordination;

interface NodeRow {
  id: string;
  counter: number;
  entries: number;
}
interface Connection {
  id: string;
  node: string;
  thread: string;
  holder: string;
  phase: PhaseCode;
  candidate: string | null;
  value: string | null;
  duplicate: boolean;
}
interface RegistryEntry {
  node: string;
  holder: string;
  client: string;
  value: string;
  live: boolean;
  stale: boolean;
  duplicate: boolean;
}
interface IssuedValue {
  seq: number;
  client: string;
  node: string;
  value: string;
  unique: boolean;
}
interface Stats {
  opened: number;
  registered: number;
  issued: number;
  distinct: number;
  duplicates: number;
  lost: number;
  leaked: number;
  delivered: number;
  missed: number;
  stale: number;
  writeErrors: number;
}
interface ComparisonRow {
  source: ComparisonSource;
  uniqueInJvm: boolean;
  uniqueInCluster: boolean;
  ordered: boolean;
  coordination: CoordinationCode;
}
interface SharedState {
  store: StoreCode;
  valueSource: SourceCode;
  serializedWrites: boolean;
  nodes: NodeRow[];
  connections: Connection[];
  registry: RegistryEntry[];
  issued: IssuedValue[];
  stats: Stats;
  comparison?: ComparisonRow[];
}

export default function SharedStateVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as SharedState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const comparison = state.comparison ?? [];
  if (comparison.length > 0) {
    return <ComparisonTable rows={comparison} lang={lang} />;
  }

  const holders: string[] = [];
  for (const entry of state.registry) {
    if (!holders.includes(entry.holder)) holders.push(entry.holder);
  }

  return (
    <div style={wrapStyle}>
      <Header state={state} lang={lang} />

      {state.nodes.length > 0 && (
        <Pane title={tl(LABELS.nodes, lang)} highlighted={highlight.has('nodes')}>
          <BoxGroup
            boxes={state.nodes.map((node) => ({
              id: node.id,
              title: node.id,
              subtitle: `${tl(LABELS.counter, lang)} ${node.counter} · ${node.entries} ${tl(LABELS.entries, lang)}`,
              highlighted: highlight.has(`node:${node.id}`),
            }))}
          />
        </Pane>
      )}

      <Pane title={tl(LABELS.connections, lang)} highlighted={highlight.has('connections')}>
        {state.connections.length === 0 ? (
          <div style={mutedStyle}>{tl(LABELS.noConnections, lang)}</div>
        ) : (
          <BoxGroup boxes={state.connections.map((conn) => connectionBox(conn, lang))} />
        )}
      </Pane>

      <div style={sidesStyle}>
        <Pane
          title={tl(LABELS.registry, lang)}
          highlighted={highlight.has('registry') || highlight.has('broadcast')}
        >
          {state.registry.length === 0 ? (
            <div style={mutedStyle}>{tl(LABELS.noRegistry, lang)}</div>
          ) : (
            <div style={holdersStyle}>
              {holders.map((holder) => (
                <div key={holder}>
                  <div style={holderNameStyle}>{holder}</div>
                  <ArrayGrid
                    cells={state.registry
                      .filter((entry) => entry.holder === holder)
                      .map((entry) => ({
                        key: `${entry.holder}/${entry.client}`,
                        label: entry.client,
                        highlighted: entry.duplicate,
                        content: <EntryContent entry={entry} lang={lang} />,
                      }))}
                  />
                </div>
              ))}
            </div>
          )}
        </Pane>

        <Pane
          title={tl(LABELS.issued, lang)}
          highlighted={highlight.has('values') || highlight.has('duplicate')}
        >
          {state.issued.length === 0 ? (
            <div style={mutedStyle}>{tl(LABELS.noIssued, lang)}</div>
          ) : (
            <ArrayGrid
              cells={state.issued.map((row) => ({
                key: row.seq,
                label: `#${row.seq}`,
                highlighted: !row.unique,
                content: (
                  <span style={valueRowStyle}>
                    <span style={{ ...valueStyle, color: row.unique ? undefined : 'var(--bad)' }}>
                      {row.value}
                    </span>
                    <span style={valueMetaStyle}>{`${row.client} · ${row.node}`}</span>
                    {!row.unique && (
                      <span style={badStyle}>{tl(LABELS.duplicate, lang)}</span>
                    )}
                  </span>
                ),
              }))}
            />
          )}
        </Pane>
      </div>

      <div style={statsStyle}>
        <Stat label={tl(LABELS.statOpened, lang)} value={state.stats.opened} />
        <Stat label={tl(LABELS.statRegistered, lang)} value={state.stats.registered} />
        <Stat label={tl(LABELS.statIssued, lang)} value={state.stats.issued} />
        <Stat
          label={tl(LABELS.statDistinct, lang)}
          value={state.stats.distinct}
          color={
            state.stats.distinct < state.stats.issued ? 'var(--bad)' : undefined
          }
        />
        <Stat
          label={tl(LABELS.statDuplicates, lang)}
          value={state.stats.duplicates}
          color={state.stats.duplicates > 0 ? 'var(--bad)' : undefined}
        />
        <Stat
          label={tl(LABELS.statLost, lang)}
          value={state.stats.lost}
          color={state.stats.lost > 0 ? 'var(--bad)' : undefined}
        />
        <Stat
          label={tl(LABELS.statLeaked, lang)}
          value={state.stats.leaked}
          color={state.stats.leaked > 0 ? 'var(--accent)' : undefined}
        />
        <Stat label={tl(LABELS.statDelivered, lang)} value={state.stats.delivered} />
        <Stat
          label={tl(LABELS.statMissed, lang)}
          value={state.stats.missed}
          color={state.stats.missed > 0 ? 'var(--bad)' : undefined}
        />
        <Stat
          label={tl(LABELS.statStale, lang)}
          value={state.stats.stale}
          color={state.stats.stale > 0 ? 'var(--accent)' : undefined}
        />
        <Stat
          label={tl(LABELS.statWriteErrors, lang)}
          value={state.stats.writeErrors}
          color={state.stats.writeErrors > 0 ? 'var(--bad)' : undefined}
        />
      </div>
    </div>
  );
}

function connectionBox(conn: Connection, lang: Lang) {
  const parts = [`${conn.node} · ${conn.thread}`, tl(LABELS.phases[conn.phase], lang)];
  if (conn.value) {
    parts.push(`= ${conn.value}`);
  } else if (conn.candidate) {
    parts.push(`${tl(LABELS.wants, lang)} ${conn.candidate}`);
  }
  if (conn.duplicate) {
    parts.push(tl(LABELS.duplicate, lang));
  }
  return {
    id: conn.id,
    title: conn.id,
    subtitle: parts.join(' · '),
    highlighted: conn.phase === 'JOINING',
    dim: conn.phase === 'CLOSED',
  };
}

function EntryContent({ entry, lang }: { entry: RegistryEntry; lang: Lang }) {
  const flag = !entry.live
    ? { text: tl(entry.stale ? LABELS.leaked : LABELS.removed, lang), color: 'var(--bad)' }
    : entry.stale
      ? { text: tl(LABELS.leaked, lang), color: 'var(--accent)' }
      : entry.duplicate
        ? { text: tl(LABELS.duplicate, lang), color: 'var(--bad)' }
        : null;
  return (
    <span style={{ ...valueRowStyle, opacity: entry.live ? 1 : 0.45 }}>
      <span style={valueStyle}>{entry.value}</span>
      {flag && <span style={{ ...flagStyle, color: flag.color }}>{flag.text}</span>}
    </span>
  );
}

function Header({ state, lang }: { state: SharedState; lang: Lang }) {
  return (
    <div style={headerStyle}>
      <span style={badgeStyle}>{tl(LABELS.stores[state.store], lang)}</span>
      <span style={badgeStyle}>{tl(LABELS.sources[state.valueSource], lang)}</span>
      <span style={pillStyle}>{`${state.nodes.length} × JVM`}</span>
      <span
        style={{ ...pillStyle, color: state.serializedWrites ? 'var(--good)' : 'var(--bad)' }}
      >
        {tl(state.serializedWrites ? LABELS.writesSerialized : LABELS.writesFree, lang)}
      </span>
    </div>
  );
}

function ComparisonTable({ rows, lang }: { rows: ComparisonRow[]; lang: Lang }) {
  const yesNo = (value: boolean) => tl(value ? LABELS.yes : LABELS.no, lang);
  return (
    <div style={wrapStyle}>
      <div style={sectionLabelStyle}>{tl(LABELS.comparison, lang)}</div>
      <div style={tableStyle}>
        <div style={tableHeadStyle}>
          <span style={cellTextStyle}>{tl(LABELS.colSource, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colJvm, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colCluster, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colOrdered, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colCoordination, lang)}</span>
        </div>
        {rows.map((row) => (
          <div key={row.source} style={tableRowStyle}>
            <span style={{ ...cellTextStyle, fontWeight: 700 }}>
              {tl(LABELS.sourceNames[row.source], lang)}
            </span>
            <span
              style={{ ...cellTextStyle, color: row.uniqueInJvm ? 'var(--good)' : 'var(--bad)' }}
            >
              {yesNo(row.uniqueInJvm)}
            </span>
            <span
              style={{ ...cellTextStyle, color: row.uniqueInCluster ? 'var(--good)' : 'var(--bad)' }}
            >
              {yesNo(row.uniqueInCluster)}
            </span>
            <span style={cellTextStyle}>{yesNo(row.ordered)}</span>
            <span style={cellTextStyle}>{tl(LABELS.coordination[row.coordination], lang)}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function Pane({
  title,
  highlighted,
  children,
}: {
  title: string;
  highlighted: boolean;
  children: ReactNode;
}) {
  return (
    <div style={{ ...paneStyle, ...(highlighted ? paneHighlightStyle : {}) }}>
      <div style={sectionLabelStyle}>{title}</div>
      {children}
    </div>
  );
}

function Stat({ label, value, color }: { label: string; value: number; color?: string }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={{ ...statValueStyle, color: color ?? 'inherit' }}>{value}</div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const headerStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' };
const badgeStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 12,
  fontWeight: 700,
  padding: '2px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const pillStyle: CSSProperties = {
  fontSize: 11,
  opacity: 0.75,
  border: '1px solid var(--border)',
  borderRadius: 10,
  padding: '1px 7px',
};
const sidesStyle: CSSProperties = { display: 'flex', gap: 10, alignItems: 'stretch', flexWrap: 'wrap' };
const paneStyle: CSSProperties = {
  flex: '1 1 260px',
  minWidth: 240,
  display: 'flex',
  flexDirection: 'column',
  gap: 6,
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '8px 10px',
  background: 'var(--viz-box)',
};
const paneHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
};
const holdersStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 8 };
const holderNameStyle: CSSProperties = {
  fontSize: 11,
  fontFamily: 'monospace',
  fontWeight: 700,
  opacity: 0.75,
  marginBottom: 2,
};
const valueRowStyle: CSSProperties = { display: 'flex', gap: 8, alignItems: 'baseline', flexWrap: 'wrap' };
const valueStyle: CSSProperties = { fontSize: 12, fontFamily: 'monospace', fontWeight: 700 };
const valueMetaStyle: CSSProperties = { fontSize: 10, opacity: 0.6, fontFamily: 'monospace' };
const flagStyle: CSSProperties = { fontSize: 10, fontWeight: 700 };
const badStyle: CSSProperties = { fontSize: 10, fontWeight: 700, color: 'var(--bad)' };
const tableStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 2 };
const tableHeadStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1.8fr repeat(4, 1fr)',
  fontSize: 10,
  opacity: 0.6,
};
const tableRowStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1.8fr repeat(4, 1fr)',
  fontSize: 11,
  fontFamily: 'monospace',
  padding: '4px 0',
  borderTop: '1px solid var(--border)',
  alignItems: 'center',
};
const cellTextStyle: CSSProperties = { paddingRight: 6, overflowWrap: 'anywhere' };
const statsStyle: CSSProperties = { display: 'flex', gap: 16, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 2 };
const mutedStyle: CSSProperties = { fontSize: 12, opacity: 0.5, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
