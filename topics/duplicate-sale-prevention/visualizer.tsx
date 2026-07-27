import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  guard: { en: 'guard', ru: 'защита' },
  dedupIndex: { en: 'dedup index (server)', ru: 'индекс дедупликации (сервер)' },
  salesTable: { en: 'sales table', ru: 'таблица продаж' },
  inFlight: { en: 'in flight at the same moment', ru: 'одновременно в обработке' },
  day: { en: 'day', ru: 'день' },
  retention: { en: 'retention', ru: 'хранение' },
  days: { en: 'd', ru: 'дн' },
  forever: { en: 'unbounded', ru: 'без ограничения' },
  registered: { en: 'registered', ru: 'зарегистрировано' },
  blocked: { en: 'blocked', ru: 'заблокировано' },
  duplicates: { en: 'duplicate rows', ru: 'строк-дублей' },
  lost: { en: 'lost sales', ru: 'потеряно продаж' },
  total: { en: 'books say', ru: 'по учёту' },
  noRequest: { en: 'no request being processed', ru: 'запросов в обработке нет' },
  guardNone: { en: 'no guard', ru: 'нет защиты' },
  guardCheck: { en: 'SELECT, then INSERT', ru: 'SELECT, потом INSERT' },
  guardUnique: { en: 'UNIQUE index', ru: 'UNIQUE-индекс' },
  guardUpsert: { en: 'ON CONFLICT DO NOTHING', ru: 'ON CONFLICT DO NOTHING' },
  byKey: { en: 'dedup by sale key', ru: 'дедупликация по ключу продажи' },
  byHash: { en: 'dedup by payload hash', ru: 'дедупликация по хэшу тела' },
  outPending: { en: 'processing…', ru: 'обработка…' },
  outRegistered: { en: 'registered ✓', ru: 'зарегистрировано ✓' },
  outDuplicated: { en: 'duplicate row written', ru: 'записана строка-дубль' },
  outBlocked: { en: 'blocked, response replayed', ru: 'заблокировано, ответ повторён' },
  outConflict: { en: '409 Conflict', ru: '409 Conflict' },
  outLost: { en: 'sale lost', ru: 'продажа потеряна' },
  outCrashed: { en: 'crashed mid-write', ru: 'падение посреди записи' },
  runHint: {
    en: 'Run the code to visualize how duplicates are prevented.',
    ru: 'Запустите код, чтобы увидеть, как предотвращаются дубли.',
  },
};

type Guard = 'none' | 'check-then-insert' | 'unique-index' | 'upsert';
type Outcome = 'pending' | 'registered' | 'duplicated' | 'blocked' | 'conflict' | 'lost' | 'crashed';

interface IndexEntry {
  dedupKey: string;
  sourceKey: string;
  response: string;
  day: number;
}
interface SaleRow {
  row: number;
  key: string;
  item: string;
  amount: number;
}
interface Flight {
  id: string;
  key: string;
  phase: string;
}
interface Request {
  key: string;
  item: string;
  amount: number;
  outcome: Outcome;
}
interface DedupState {
  guard: Guard;
  dedupBy: 'idempotency-key' | 'content-hash';
  day: number;
  retentionDays: number;
  request?: Request;
  inFlight: Flight[];
  dedupIndex: IndexEntry[];
  sales: SaleRow[];
  total: number;
  registered: number;
  blocked: number;
  duplicates: number;
  lost: number;
}

export default function DuplicateSaleVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as DedupState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  // A row whose key already appeared above it is the duplicate one.
  const seen = new Set<string>();
  const saleBoxes: Box[] = state.sales.map((row) => {
    const repeat = seen.has(row.key);
    seen.add(row.key);
    return {
      id: `row-${row.row}`,
      title: `#${row.row} ${row.key}`,
      subtitle: `${row.item} · ${row.amount}${repeat ? ' · ⚠' : ''}`,
      highlighted: highlight.has(`row:${row.row}`),
    };
  });

  const indexBoxes: Box[] = state.dedupIndex.map((entry, i) => ({
    id: `entry-${i}-${entry.dedupKey}`,
    title: entry.dedupKey,
    subtitle: `${entry.response} · ${tl(LABELS.day, lang)} ${entry.day}`,
    highlighted: highlight.has(`entry:${entry.dedupKey}`),
  }));

  const flightBoxes: Box[] = state.inFlight.map((flight) => ({
    id: flight.id,
    title: flight.id,
    subtitle: `${flight.key} · ${flight.phase}`,
    highlighted: highlight.has(`flight:${flight.id}`),
  }));

  return (
    <div style={wrapStyle}>
      <div style={headerStyle}>
        <span style={pillStyle}>
          {tl(LABELS.guard, lang)}: {tl(guardLabel(state.guard), lang)}
        </span>
        <span style={state.dedupBy === 'content-hash' ? warnPillStyle : pillStyle}>
          {tl(state.dedupBy === 'content-hash' ? LABELS.byHash : LABELS.byKey, lang)}
        </span>
        <span style={pillStyle}>
          {tl(LABELS.day, lang)} {state.day} · {tl(LABELS.retention, lang)}{' '}
          {state.retentionDays > 0
            ? `${state.retentionDays}${tl(LABELS.days, lang)}`
            : tl(LABELS.forever, lang)}
        </span>
      </div>

      <RequestBanner request={state.request} lang={lang} />

      {flightBoxes.length > 0 && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.inFlight, lang)}</div>
          <BoxGroup boxes={flightBoxes} />
        </div>
      )}

      <div style={statsStyle}>
        <Stat label={tl(LABELS.registered, lang)} value={state.registered} color="var(--good)" />
        <Stat label={tl(LABELS.blocked, lang)} value={state.blocked} color="var(--accent-2)" />
        <Stat
          label={tl(LABELS.duplicates, lang)}
          value={state.duplicates}
          color={state.duplicates > 0 ? 'var(--bad)' : undefined}
        />
        <Stat
          label={tl(LABELS.lost, lang)}
          value={state.lost}
          color={state.lost > 0 ? 'var(--bad)' : undefined}
        />
        <Stat
          label={tl(LABELS.total, lang)}
          value={state.total}
          color={state.duplicates > 0 || state.lost > 0 ? 'var(--bad)' : 'var(--good)'}
        />
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.dedupIndex, lang)}</div>
        <BoxGroup boxes={indexBoxes} />
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.salesTable, lang)}</div>
        <BoxGroup boxes={saleBoxes} />
      </div>
    </div>
  );
}

function guardLabel(guard: Guard) {
  if (guard === 'check-then-insert') return LABELS.guardCheck;
  if (guard === 'unique-index') return LABELS.guardUnique;
  if (guard === 'upsert') return LABELS.guardUpsert;
  return LABELS.guardNone;
}

const OUTCOMES: Record<Outcome, { label: { en: string; ru: string }; color: string }> = {
  pending: { label: LABELS.outPending, color: 'var(--accent)' },
  registered: { label: LABELS.outRegistered, color: 'var(--good)' },
  duplicated: { label: LABELS.outDuplicated, color: 'var(--bad)' },
  blocked: { label: LABELS.outBlocked, color: 'var(--accent-2)' },
  conflict: { label: LABELS.outConflict, color: 'var(--accent-2)' },
  lost: { label: LABELS.outLost, color: 'var(--bad)' },
  crashed: { label: LABELS.outCrashed, color: 'var(--bad)' },
};

function RequestBanner({ request, lang }: { request: Request | undefined; lang: Lang }) {
  if (!request) {
    return <div style={{ ...bannerStyle, opacity: 0.5 }}>{tl(LABELS.noRequest, lang)}</div>;
  }
  const outcome = OUTCOMES[request.outcome] ?? OUTCOMES.pending;
  return (
    <div style={bannerStyle}>
      <span style={tagStyle}>POST /sales</span>
      <span style={keyStyle}>{request.key}</span>
      <span style={bodyStyle}>
        {request.item} · {request.amount}
      </span>
      <span style={{ ...outcomeStyle, color: outcome.color }}>{tl(outcome.label, lang)}</span>
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
const headerStyle: CSSProperties = { display: 'flex', gap: 8, flexWrap: 'wrap' };
const pillStyle: CSSProperties = {
  fontSize: 11,
  padding: '2px 8px',
  borderRadius: 999,
  border: '1px solid var(--border)',
  fontFamily: 'monospace',
};
const warnPillStyle: CSSProperties = { ...pillStyle, color: 'var(--bad)', borderColor: 'var(--bad)' };
const bannerStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 10,
  flexWrap: 'wrap',
  padding: '8px 12px',
  borderRadius: 8,
  background: 'var(--viz-box)',
  border: '1px solid var(--viz-active)',
};
const tagStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const keyStyle: CSSProperties = { fontWeight: 700, fontSize: 15, fontFamily: 'monospace' };
const bodyStyle: CSSProperties = { fontSize: 12, opacity: 0.7, fontFamily: 'monospace' };
const outcomeStyle: CSSProperties = { marginLeft: 'auto', fontSize: 13, fontWeight: 600 };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
