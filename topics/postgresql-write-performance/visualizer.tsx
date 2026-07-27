import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to see what one write to PostgreSQL costs.',
    ru: 'Запустите код, чтобы увидеть, во что обходится одна запись в PostgreSQL.',
  },
  payloadInline: { en: 'payload in a column', ru: 'нагрузка в колонке' },
  payloadOffloaded: { en: 'payload in object storage', ru: 'нагрузка в объектном хранилище' },
  row: { en: 'row', ru: 'строка' },
  indexes: { en: 'secondary indexes', ru: 'вторичных индексов' },
  commitFlush: { en: 'commit waits for fsync', ru: 'коммит ждёт fsync' },
  commitAsync: { en: 'synchronous_commit = off', ru: 'synchronous_commit = off' },
  pool: { en: 'pool', ru: 'пул' },
  compressible: { en: 'compressible', ru: 'сжимаемая' },
  precompressed: { en: 'already compressed', ru: 'уже сжатая' },
  external: { en: 'SET STORAGE EXTERNAL', ru: 'SET STORAGE EXTERNAL' },
  oneWrite: { en: 'one write', ru: 'одна запись' },
  stored: { en: 'to the table', ru: 'в таблицу' },
  wal: { en: 'to the WAL', ru: 'в WAL' },
  chunks: { en: 'TOAST chunks', ru: 'кусков TOAST' },
  entries: { en: 'index entries', ru: 'записей в индексы' },
  lastWrite: { en: 'this write call', ru: 'этот вызов записи' },
  strategyRowByRow: { en: 'one INSERT per row', ru: 'по одному INSERT на строку' },
  strategyBatch: { en: 'batched INSERT', ru: 'батч INSERT' },
  strategyCopy: { en: 'COPY FROM STDIN', ru: 'COPY FROM STDIN' },
  rows: { en: 'rows', ru: 'строк' },
  roundTrips: { en: 'round trips', ru: 'round trips' },
  commits: { en: 'commits', ru: 'коммитов' },
  flushes: { en: 'fsyncs', ru: 'fsync' },
  total: { en: 'total', ru: 'всего' },
  perRow: { en: 'per row', ru: 'на строку' },
  callers: { en: 'callers at once', ru: 'вызывающих одновременно' },
  writing: { en: 'writing', ru: 'пишут' },
  queued: { en: 'queued', ru: 'в очереди' },
  capacity: { en: 'absorbed', ru: 'принимается' },
  perSec: { en: '/s', ru: '/с' },
  poolCeiling: { en: 'connections allow', ru: 'соединения позволяют' },
  diskCeiling: { en: 'disk allows', ru: 'диск позволяет' },
  boundByPool: { en: 'limited by the connections', ru: 'ограничивают соединения' },
  boundByDisk: { en: 'limited by the disk', ru: 'ограничивает диск' },
  drain: { en: 'burst clears in', ru: 'всплеск рассасывается за' },
  totals: { en: 'everything written so far', ru: 'записано с начала' },
  partNetwork: { en: 'network', ru: 'сеть' },
  partParse: { en: 'parse', ru: 'разбор' },
  partCompress: { en: 'compress', ru: 'сжатие' },
  partTable: { en: 'table', ru: 'таблица' },
  partToast: { en: 'TOAST', ru: 'TOAST' },
  partWal: { en: 'WAL', ru: 'WAL' },
  partIndex: { en: 'indexes', ru: 'индексы' },
  partCommit: { en: 'commit', ru: 'коммит' },
};

type PartName =
  | 'network'
  | 'parse'
  | 'compress'
  | 'table'
  | 'toast'
  | 'wal'
  | 'index'
  | 'commit';

interface Part {
  name: PartName;
  us: number;
}
interface WriteState {
  table: string;
  config: {
    payloadBytes: number;
    payloadPlacement: 'inline' | 'object-storage';
    rowBytes: number;
    compressible: boolean;
    columnCompression: boolean;
    secondaryIndexes: number;
    commitMode: 'flush' | 'async';
    poolSize: number;
  };
  row: {
    storedBytes: number;
    walBytes: number;
    toastChunks: number;
    indexEntries: number;
    requestUs: number;
    parts: Part[];
  };
  lastWrite: {
    strategy: 'row-by-row' | 'batch' | 'copy';
    rows: number;
    batches: number;
    roundTrips: number;
    commits: number;
    flushes: number;
    walBytes: number;
    us: number;
    usPerRow: number;
  } | null;
  pool: {
    size: number;
    clients: number;
    inUse: number;
    queued: number;
    drainUs: number;
    requestUs: number;
    capacityPerSec: number;
    poolCeilingPerSec: number;
    diskCeilingPerSec: number;
    boundBy: 'pool' | 'disk';
  } | null;
  totals: {
    rows: number;
    roundTrips: number;
    commits: number;
    flushes: number;
    walBytes: number;
    toastChunks: number;
    indexEntries: number;
    us: number;
  };
}

const PART_COLOR: Record<PartName, string> = {
  network: '#6c8ebf',
  parse: '#8d7bb5',
  compress: '#c98b3f',
  table: '#4f9d7a',
  toast: '#b5643f',
  wal: '#b04a4a',
  index: '#a0894a',
  commit: '#5f7f9d',
};

/** Highlight tokens the model emits that should light up a given part. */
const PART_TOKENS: Record<PartName, string[]> = {
  network: ['row'],
  parse: ['row'],
  compress: ['payload'],
  table: ['payload'],
  toast: ['toast', 'payload'],
  wal: ['wal'],
  index: ['index'],
  commit: ['commit'],
};

export default function WritePathVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as WriteState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const { config, row, lastWrite, pool, totals } = state;
  const offloaded = config.payloadPlacement === 'object-storage';
  const totalParts = row.parts.reduce((sum, part) => sum + part.us, 0) || 1;

  const lastWriteBoxes: Box[] = lastWrite
    ? [
        { id: 'lw-rows', title: String(lastWrite.rows), subtitle: tl(LABELS.rows, lang) },
        {
          id: 'lw-trips',
          title: String(lastWrite.roundTrips),
          subtitle: tl(LABELS.roundTrips, lang),
        },
        {
          id: 'lw-commits',
          title: `${lastWrite.commits} / ${lastWrite.flushes}`,
          subtitle: `${tl(LABELS.commits, lang)} / ${tl(LABELS.flushes, lang)}`,
          highlighted: highlight.has('commit'),
        },
        { id: 'lw-wal', title: fmtBytes(lastWrite.walBytes), subtitle: tl(LABELS.wal, lang) },
        { id: 'lw-total', title: fmtDur(lastWrite.us), subtitle: tl(LABELS.total, lang) },
        { id: 'lw-per-row', title: fmtDur(lastWrite.usPerRow), subtitle: tl(LABELS.perRow, lang) },
      ]
    : [];

  return (
    <div style={wrapStyle}>
      <div style={headerStyle}>
        <span style={pillStyle}>{state.table}</span>
        <span style={{ ...pillStyle, ...(highlight.has('payload') ? accentPillStyle : {}) }}>
          {tl(offloaded ? LABELS.payloadOffloaded : LABELS.payloadInline, lang)}:{' '}
          {fmtBytes(config.payloadBytes)}
        </span>
        <span style={pillStyle}>
          {tl(LABELS.row, lang)}: {fmtBytes(config.rowBytes)}
        </span>
        {!offloaded && (
          <span style={pillStyle}>
            {tl(config.compressible ? LABELS.compressible : LABELS.precompressed, lang)}
          </span>
        )}
        {!config.columnCompression && <span style={pillStyle}>{tl(LABELS.external, lang)}</span>}
        <span style={{ ...pillStyle, ...(highlight.has('index') ? accentPillStyle : {}) }}>
          {config.secondaryIndexes} {tl(LABELS.indexes, lang)}
        </span>
        <span style={{ ...pillStyle, ...(highlight.has('commit') ? accentPillStyle : {}) }}>
          {tl(config.commitMode === 'flush' ? LABELS.commitFlush : LABELS.commitAsync, lang)}
        </span>
        <span style={{ ...pillStyle, ...(highlight.has('pool') ? accentPillStyle : {}) }}>
          {tl(LABELS.pool, lang)}: {config.poolSize}
        </span>
      </div>

      <div style={bannerStyle}>
        <span style={bannerLabelStyle}>{tl(LABELS.oneWrite, lang)}</span>
        <span style={bannerValueStyle}>{fmtDur(row.requestUs)}</span>
        <span style={factsStyle}>
          <span>
            {fmtBytes(row.storedBytes)} {tl(LABELS.stored, lang)}
          </span>
          <span style={highlight.has('wal') ? strongStyle : undefined}>
            {fmtBytes(row.walBytes)} {tl(LABELS.wal, lang)}
          </span>
          <span style={highlight.has('toast') ? strongStyle : undefined}>
            {row.toastChunks} {tl(LABELS.chunks, lang)}
          </span>
          <span style={highlight.has('index') ? strongStyle : undefined}>
            {row.indexEntries} {tl(LABELS.entries, lang)}
          </span>
        </span>
      </div>

      <div>
        <div style={barStyle}>
          {row.parts.map((part) => (
            <div
              key={part.name}
              style={{
                ...segmentStyle,
                width: `${(part.us / totalParts) * 100}%`,
                background: PART_COLOR[part.name],
                opacity: isLit(part.name, highlight) ? 1 : 0.55,
              }}
              title={partLabel(part.name, lang)}
            />
          ))}
        </div>
        <div style={legendStyle}>
          {row.parts.map((part) => (
            <span key={part.name} style={legendItemStyle}>
              <span style={{ ...swatchStyle, background: PART_COLOR[part.name] }} />
              {partLabel(part.name, lang)} {fmtDur(part.us)} ·{' '}
              {((part.us / totalParts) * 100).toFixed(1)}%
            </span>
          ))}
        </div>
      </div>

      {lastWrite && (
        <div>
          <div style={sectionLabelStyle}>
            {tl(LABELS.lastWrite, lang)} — {tl(strategyLabel(lastWrite.strategy), lang)}
          </div>
          <BoxGroup boxes={lastWriteBoxes} />
        </div>
      )}

      {pool && (
        <div
          style={{
            ...poolStyle,
            ...(highlight.has('pool') ? bannerActiveStyle : {}),
          }}
        >
          <div style={poolRowStyle}>
            <Stat label={tl(LABELS.callers, lang)} value={String(pool.clients)} />
            <Stat label={tl(LABELS.writing, lang)} value={String(pool.inUse)} />
            <Stat
              label={tl(LABELS.queued, lang)}
              value={String(pool.queued)}
              color={pool.queued > 0 ? 'var(--bad)' : undefined}
            />
            <Stat
              label={tl(LABELS.capacity, lang)}
              value={`${pool.capacityPerSec}${tl(LABELS.perSec, lang)}`}
            />
            <Stat label={tl(LABELS.drain, lang)} value={fmtDur(pool.drainUs)} />
          </div>
          <div style={ceilingStyle}>
            <span style={pool.boundBy === 'pool' ? strongStyle : dimStyle}>
              {tl(LABELS.poolCeiling, lang)} {pool.poolCeilingPerSec}
              {tl(LABELS.perSec, lang)}
            </span>
            <span style={pool.boundBy === 'disk' ? strongStyle : dimStyle}>
              {tl(LABELS.diskCeiling, lang)} {pool.diskCeilingPerSec}
              {tl(LABELS.perSec, lang)}
            </span>
            <span style={verdictStyle}>
              {tl(pool.boundBy === 'disk' ? LABELS.boundByDisk : LABELS.boundByPool, lang)}
            </span>
          </div>
        </div>
      )}

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.totals, lang)}</div>
        <div style={poolRowStyle}>
          <Stat label={tl(LABELS.rows, lang)} value={String(totals.rows)} />
          <Stat label={tl(LABELS.roundTrips, lang)} value={String(totals.roundTrips)} />
          <Stat
            label={`${tl(LABELS.commits, lang)} / ${tl(LABELS.flushes, lang)}`}
            value={`${totals.commits} / ${totals.flushes}`}
          />
          <Stat label={tl(LABELS.wal, lang)} value={fmtBytes(totals.walBytes)} />
          <Stat label={tl(LABELS.chunks, lang)} value={String(totals.toastChunks)} />
          <Stat label={tl(LABELS.total, lang)} value={fmtDur(totals.us)} />
        </div>
      </div>
    </div>
  );
}

function isLit(name: PartName, highlight: Set<string>) {
  if (highlight.size === 0) return true;
  return PART_TOKENS[name].some((token) => highlight.has(token));
}

function partLabel(name: PartName, lang: Lang) {
  const map = {
    network: LABELS.partNetwork,
    parse: LABELS.partParse,
    compress: LABELS.partCompress,
    table: LABELS.partTable,
    toast: LABELS.partToast,
    wal: LABELS.partWal,
    index: LABELS.partIndex,
    commit: LABELS.partCommit,
  };
  return tl(map[name], lang);
}

function strategyLabel(strategy: 'row-by-row' | 'batch' | 'copy') {
  if (strategy === 'batch') return LABELS.strategyBatch;
  if (strategy === 'copy') return LABELS.strategyCopy;
  return LABELS.strategyRowByRow;
}

/** Microseconds -> the unit that reads best, without touching the locale. */
function fmtDur(us: number) {
  if (us >= 1_000_000) return `${(us / 1_000_000).toFixed(1)} s`;
  if (us >= 1000) return `${(us / 1000).toFixed(us >= 100_000 ? 0 : 1)} ms`;
  return `${us} us`;
}

function fmtBytes(value: number) {
  if (value >= 1024 * 1024) return `${(value / (1024 * 1024)).toFixed(1)} MB`;
  if (value >= 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${value} B`;
}

function Stat({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={{ ...statValueStyle, color: color ?? 'inherit' }}>{value}</div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const headerStyle: CSSProperties = { display: 'flex', gap: 6, flexWrap: 'wrap' };
const pillStyle: CSSProperties = {
  fontSize: 11,
  padding: '2px 8px',
  borderRadius: 999,
  border: '1px solid var(--border)',
  fontFamily: 'monospace',
};
const accentPillStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
};
const bannerStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 12,
  flexWrap: 'wrap',
  padding: '8px 12px',
  borderRadius: 8,
  background: 'var(--viz-box)',
  border: '1px solid var(--border)',
};
const bannerActiveStyle: CSSProperties = { borderColor: 'var(--viz-active)' };
const bannerLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const bannerValueStyle: CSSProperties = { fontSize: 26, fontWeight: 700, fontFamily: 'monospace' };
const factsStyle: CSSProperties = {
  marginLeft: 'auto',
  display: 'flex',
  gap: 12,
  fontSize: 12,
  fontFamily: 'monospace',
  flexWrap: 'wrap',
};
const strongStyle: CSSProperties = { color: 'var(--bad)', fontWeight: 700 };
const dimStyle: CSSProperties = { opacity: 0.5 };
const barStyle: CSSProperties = {
  display: 'flex',
  height: 18,
  borderRadius: 4,
  overflow: 'hidden',
  border: '1px solid var(--border)',
};
const segmentStyle: CSSProperties = { height: '100%', minWidth: 1 };
const legendStyle: CSSProperties = {
  display: 'flex',
  gap: 12,
  flexWrap: 'wrap',
  marginTop: 6,
  fontSize: 11,
  fontFamily: 'monospace',
};
const legendItemStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 4 };
const swatchStyle: CSSProperties = { width: 9, height: 9, borderRadius: 2, display: 'inline-block' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const poolStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 8,
  padding: '8px 12px',
  borderRadius: 8,
  background: 'var(--viz-box)',
  border: '1px solid var(--border)',
};
const poolRowStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const ceilingStyle: CSSProperties = {
  display: 'flex',
  gap: 14,
  flexWrap: 'wrap',
  fontSize: 11,
  fontFamily: 'monospace',
};
const verdictStyle: CSSProperties = { marginLeft: 'auto', opacity: 0.8 };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 17, fontWeight: 700, fontFamily: 'monospace' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
