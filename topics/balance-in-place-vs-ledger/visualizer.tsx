import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  storage: { en: 'storage', ru: 'хранение' },
  inPlace: { en: 'UPDATE in place', ru: 'UPDATE на месте' },
  ledger: { en: 'append-only entries', ru: 'только добавление записей' },
  ledgerCached: { en: 'entries + cached balance', ru: 'записи + кэш баланса' },
  balance: { en: 'balance', ru: 'баланс' },
  storedCol: { en: 'balance column', ru: 'колонка баланса' },
  computedCol: { en: 'SUM(entries)', ru: 'SUM(записи)' },
  drift: { en: 'drift', ru: 'расхождение' },
  lastRead: { en: 'last read', ru: 'последнее чтение' },
  rows: { en: 'row(s)', ru: 'строк' },
  srcNone: { en: 'not read yet', ru: 'ещё не читали' },
  srcColumn: { en: 'the balance column', ru: 'колонка баланса' },
  srcCached: { en: 'the cached column', ru: 'кэширующая колонка' },
  srcFold: { en: 'a fold over every entry', ru: 'свёртка по всем записям' },
  srcSnapshot: { en: 'snapshot + later entries', ru: 'снимок + записи после него' },
  entries: { en: 'entries (append-only)', ru: 'записи (только добавление)' },
  noHistory: {
    en: 'no history recorded — the balance is its own only evidence',
    ru: 'история не ведётся — баланс сам себе единственное доказательство',
  },
  snapshot: { en: 'snapshot', ru: 'снимок' },
  inFlight: { en: 'in flight at the same moment', ru: 'одновременно в обработке' },
  operations: { en: 'operations', ru: 'операций' },
  corrections: { en: 'corrections', ru: 'корректировок' },
  lostUpdates: { en: 'lost updates', ru: 'потерянных обновлений' },
  rewrites: { en: 'silent rewrites', ru: 'тихих перезаписей' },
  mutations: { en: 'deleted entries', ru: 'удалённых записей' },
  violations: { en: 'broken invariants', ru: 'нарушенных инвариантов' },
  phaseRead: { en: 'read the balance', ru: 'прочитала баланс' },
  phaseWrote: { en: 'wrote', ru: 'записала' },
  phaseAppended: { en: 'appended', ru: 'добавила запись' },
  phaseDiscarded: { en: 'overwritten', ru: 'перезаписана' },
  saw: { en: 'saw', ru: 'видела' },
  kindReversal: { en: 'reversal', ru: 'сторно' },
  kindCorrection: { en: 'correction', ru: 'поправка' },
  runHint: {
    en: 'Run the code to visualize how the balance is stored.',
    ru: 'Запустите код, чтобы увидеть, как хранится баланс.',
  },
};

type Storage = 'in-place' | 'ledger';
type ReadSource = 'none' | 'column' | 'cached-column' | 'fold' | 'snapshot-fold';
type Phase = 'read' | 'wrote' | 'appended' | 'discarded';
type Kind = 'operation' | 'reversal' | 'correction';

interface Entry {
  seq: number;
  amount: number;
  reason: string;
  kind: Kind;
  running: number;
}
interface Flight {
  id: string;
  action: string;
  sawBalance: number;
  phase: Phase;
}
interface LedgerState {
  storage: Storage;
  cachedBalance: boolean;
  balance: number;
  stored?: number;
  computed?: number;
  drift?: number;
  entries: Entry[];
  snapshot?: { atSeq: number; balance: number };
  read: { rows: number; source: ReadSource };
  inFlight: Flight[];
  operations: number;
  corrections: number;
  lostUpdates: number;
  rewrites: number;
  mutations: number;
  violations: number;
}

export default function BalanceLedgerVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as LedgerState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const drift = state.drift ?? 0;

  const entryBoxes: Box[] = state.entries.map((entry) => ({
    id: `entry-${entry.seq}`,
    title: `#${entry.seq} ${signed(entry.amount)}${kindMark(entry.kind)}`,
    subtitle: `${entry.reason} · = ${entry.running}`,
    highlighted: highlight.has(`entry:${entry.seq}`),
  }));

  const flightBoxes: Box[] = state.inFlight.map((flight) => ({
    id: flight.id,
    title: `${flight.id} ${flight.action}`,
    subtitle: `${tl(LABELS.saw, lang)} ${flight.sawBalance} · ${tl(phaseLabel(flight.phase), lang)}`,
    highlighted: highlight.has(`flight:${flight.id}`),
  }));

  return (
    <div style={wrapStyle}>
      <div style={headerStyle}>
        <span style={pillStyle}>
          {tl(LABELS.storage, lang)}: {tl(storageLabel(state), lang)}
        </span>
        <span style={pillStyle}>
          {tl(LABELS.lastRead, lang)}: {tl(sourceLabel(state.read.source), lang)}
          {state.read.source !== 'none' && ` · ${state.read.rows} ${tl(LABELS.rows, lang)}`}
        </span>
        {state.snapshot && (
          <span style={{ ...pillStyle, ...(highlight.has('snapshot') ? accentPillStyle : {}) }}>
            {tl(LABELS.snapshot, lang)}: #{state.snapshot.atSeq} = {state.snapshot.balance}
          </span>
        )}
      </div>

      <div style={{ ...bannerStyle, ...(highlight.has('balance') ? bannerActiveStyle : {}) }}>
        <span style={bannerLabelStyle}>{tl(LABELS.balance, lang)}</span>
        <span style={{ ...bannerValueStyle, color: drift !== 0 ? 'var(--bad)' : 'var(--good)' }}>
          {state.balance}
        </span>
        {state.stored !== undefined && state.computed !== undefined && (
          <span style={compareStyle}>
            <span style={highlight.has('stored') ? warnTextStyle : undefined}>
              {tl(LABELS.storedCol, lang)} {state.stored}
            </span>
            <span style={{ opacity: 0.5 }}>vs</span>
            <span>
              {tl(LABELS.computedCol, lang)} {state.computed}
            </span>
            {drift !== 0 && (
              <span style={warnTextStyle}>
                {tl(LABELS.drift, lang)} {signed(drift)}
              </span>
            )}
          </span>
        )}
      </div>

      {flightBoxes.length > 0 && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.inFlight, lang)}</div>
          <BoxGroup boxes={flightBoxes} />
        </div>
      )}

      <div style={statsStyle}>
        <Stat label={tl(LABELS.operations, lang)} value={state.operations} />
        <Stat
          label={tl(LABELS.corrections, lang)}
          value={state.corrections}
          color={state.corrections > 0 ? 'var(--accent-2)' : undefined}
        />
        <Stat
          label={tl(LABELS.lostUpdates, lang)}
          value={state.lostUpdates}
          color={state.lostUpdates > 0 ? 'var(--bad)' : undefined}
        />
        <Stat
          label={tl(LABELS.rewrites, lang)}
          value={state.rewrites}
          color={state.rewrites > 0 ? 'var(--bad)' : undefined}
        />
        <Stat
          label={tl(LABELS.mutations, lang)}
          value={state.mutations}
          color={state.mutations > 0 ? 'var(--bad)' : undefined}
        />
        <Stat
          label={tl(LABELS.violations, lang)}
          value={state.violations}
          color={state.violations > 0 ? 'var(--bad)' : undefined}
        />
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.entries, lang)}</div>
        {state.storage === 'in-place' ? (
          <div style={emptyHistoryStyle}>{tl(LABELS.noHistory, lang)}</div>
        ) : (
          <BoxGroup boxes={entryBoxes} />
        )}
      </div>
    </div>
  );
}

function signed(amount: number) {
  return amount >= 0 ? `+${amount}` : String(amount);
}

function kindMark(kind: Kind) {
  if (kind === 'reversal') return ' ↩';
  if (kind === 'correction') return ' ✎';
  return '';
}

function storageLabel(state: LedgerState) {
  if (state.storage === 'in-place') return LABELS.inPlace;
  return state.cachedBalance ? LABELS.ledgerCached : LABELS.ledger;
}

function sourceLabel(source: ReadSource) {
  if (source === 'column') return LABELS.srcColumn;
  if (source === 'cached-column') return LABELS.srcCached;
  if (source === 'fold') return LABELS.srcFold;
  if (source === 'snapshot-fold') return LABELS.srcSnapshot;
  return LABELS.srcNone;
}

function phaseLabel(phase: Phase) {
  if (phase === 'wrote') return LABELS.phaseWrote;
  if (phase === 'appended') return LABELS.phaseAppended;
  if (phase === 'discarded') return LABELS.phaseDiscarded;
  return LABELS.phaseRead;
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
const accentPillStyle: CSSProperties = { borderColor: 'var(--accent)', background: 'var(--viz-highlight)' };
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
const compareStyle: CSSProperties = {
  marginLeft: 'auto',
  display: 'flex',
  gap: 10,
  fontSize: 12,
  fontFamily: 'monospace',
  flexWrap: 'wrap',
};
const warnTextStyle: CSSProperties = { color: 'var(--bad)', fontWeight: 700 };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const emptyHistoryStyle: CSSProperties = {
  fontSize: 12,
  opacity: 0.55,
  fontStyle: 'italic',
  padding: '6px 0',
};
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
