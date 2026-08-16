import type { CSSProperties, ReactNode } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { type Lang, tl, useLang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize transaction propagation.',
    ru: 'Запустите код, чтобы визуализировать распространение транзакций.',
  },
  operation: { en: 'operation', ru: 'операция' },
  activeTx: { en: 'active transaction', ru: 'активная транзакция' },
  depth: { en: 'call depth', ru: 'глубина вызова' },
  callStack: { en: 'Method call stack', ru: 'Стек вызова методов' },
  physical: { en: 'Physical transactions', ru: 'Физические транзакции' },
  noFrames: { en: 'no @Transactional method entered yet', ru: 'ни один @Transactional-метод ещё не вызван' },
  noTx: { en: 'no physical transaction yet', ru: 'физической транзакции ещё нет' },
  rollbackOnly: { en: 'rollback-only', ru: 'rollback-only' },
  savepoints: { en: 'savepoints', ru: 'savepoints' },
  none: { en: 'none', ru: 'нет' },
};

const TX_STATUS_LABELS: Record<string, { en: string; ru: string }> = {
  ACTIVE: { en: 'active', ru: 'активна' },
  SUSPENDED: { en: 'suspended', ru: 'приостановлена' },
  COMMITTED: { en: 'committed', ru: 'закоммичена' },
  ROLLED_BACK: { en: 'rolled back', ru: 'откачена' },
};

const FRAME_STATUS_LABELS: Record<string, { en: string; ru: string }> = {
  ACTIVE: { en: 'active', ru: 'активен' },
  COMMITTED: { en: 'committed', ru: 'закоммичен' },
  ROLLED_BACK: { en: 'rolled back', ru: 'откачен' },
  RELEASED: { en: 'savepoint released', ru: 'savepoint освобождён' },
  RETURNED: { en: 'returned', ru: 'вернулся' },
  ERROR: { en: 'error', ru: 'ошибка' },
};

function roleText(frame: Frame, lang: Lang): string {
  switch (frame.roleKind) {
    case 'START':
      return tl({ en: `starts ${frame.physicalTx}`, ru: `открывает ${frame.physicalTx}` }, lang);
    case 'JOIN':
      return tl(
        { en: `joins ${frame.physicalTx}`, ru: `присоединяется к ${frame.physicalTx}` },
        lang,
      );
    case 'SAVEPOINT':
      return tl(
        {
          en: `savepoint ${frame.savepoint} in ${frame.physicalTx}`,
          ru: `savepoint ${frame.savepoint} в ${frame.physicalTx}`,
        },
        lang,
      );
    case 'NONE':
      return tl({ en: 'no transaction', ru: 'без транзакции' }, lang);
    case 'ERROR':
      return tl({ en: 'propagation error', ru: 'ошибка распространения' }, lang);
    default:
      return '';
  }
}

interface Frame {
  handle: string;
  method: string;
  propagation: string;
  physicalTx: string;
  roleKind: string;
  savepoint: string;
  depth: number;
  active: boolean;
  status: string;
}

interface PhysicalTx {
  id: string;
  status: string;
  rollbackOnly: boolean;
  savepoints: string[];
}

interface TxState {
  operation: string;
  activeTx: string;
  depth: number;
  frames: Frame[];
  physicalTransactions: PhysicalTx[];
}

export default function TransactionPropagationVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as TxState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  const txCells: ArrayCell[] = state.physicalTransactions.map((tx) => ({
    key: tx.id,
    label: tx.id,
    highlighted: highlight.has(`tx:${tx.id}`),
    content: <TxBox tx={tx} lang={lang} />,
  }));

  return (
    <div style={wrapStyle}>
      <div style={statsStyle}>
        <Stat label={tl(LABELS.operation, lang)} value={state.operation} mono />
        <Stat label={tl(LABELS.activeTx, lang)} value={state.activeTx} strong={state.activeTx !== 'none'} />
        <Stat label={tl(LABELS.depth, lang)} value={state.depth} />
      </div>

      <Panel title={tl(LABELS.callStack, lang)}>
        {state.frames.length === 0 ? (
          <span style={emptyStyle}>{tl(LABELS.noFrames, lang)}</span>
        ) : (
          <div style={frameListStyle}>
            {state.frames.map((frame) => (
              <FrameBox
                key={frame.handle}
                frame={frame}
                highlighted={highlight.has(`frame:${frame.handle}`)}
                lang={lang}
              />
            ))}
          </div>
        )}
      </Panel>

      <Panel title={tl(LABELS.physical, lang)}>
        {txCells.length === 0 ? (
          <span style={emptyStyle}>{tl(LABELS.noTx, lang)}</span>
        ) : (
          <ArrayGrid cells={txCells} />
        )}
      </Panel>
    </div>
  );
}

function FrameBox({ frame, highlighted, lang }: { frame: Frame; highlighted: boolean; lang: Lang }) {
  const style: CSSProperties = {
    ...frameBoxStyle,
    marginLeft: frame.depth * 20,
    opacity: frame.active ? 1 : 0.6,
    ...(frame.status === 'ERROR' || frame.status === 'ROLLED_BACK' ? errorFrameStyle : {}),
    ...(highlighted ? highlightedBoxStyle : {}),
  };
  const statusLabel = FRAME_STATUS_LABELS[frame.status] ?? { en: frame.status, ru: frame.status };
  return (
    <div style={style}>
      <div style={frameTitleStyle}>
        <code>{frame.method}()</code>
        <span style={propBadgeStyle}>{frame.propagation}</span>
      </div>
      <div style={frameRoleStyle}>{roleText(frame, lang)}</div>
      <div style={frameMetaStyle}>
        <span style={txRefStyle}>{frame.physicalTx}</span>
        <span style={statusStyle}>{tl(statusLabel, lang)}</span>
      </div>
    </div>
  );
}

function TxBox({ tx, lang }: { tx: PhysicalTx; lang: Lang }) {
  const statusLabel = TX_STATUS_LABELS[tx.status] ?? { en: tx.status, ru: tx.status };
  return (
    <div style={txContentStyle}>
      <span
        style={{
          ...txStatusBadgeStyle,
          ...(tx.status === 'COMMITTED' ? committedBadgeStyle : {}),
          ...(tx.status === 'ROLLED_BACK' ? rolledBackBadgeStyle : {}),
          ...(tx.status === 'SUSPENDED' ? suspendedBadgeStyle : {}),
        }}
      >
        {tl(statusLabel, lang)}
      </span>
      {tx.rollbackOnly && <span style={rollbackOnlyBadgeStyle}>{tl(LABELS.rollbackOnly, lang)}</span>}
      <span style={savepointsStyle}>
        {tl(LABELS.savepoints, lang)}: {tx.savepoints.length === 0 ? tl(LABELS.none, lang) : tx.savepoints.join(', ')}
      </span>
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
const frameListStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 5 };
const frameBoxStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '5px 8px',
  background: 'var(--viz-box)',
};
const errorFrameStyle: CSSProperties = {
  borderColor: '#e0603a',
  background: 'rgba(224,96,58,0.10)',
};
const highlightedBoxStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.35)',
};
const frameTitleStyle: CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'baseline',
  gap: 8,
  fontSize: 13,
  fontWeight: 700,
};
const propBadgeStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 10,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'rgba(91,141,239,0.16)',
  color: '#79a7ff',
};
const frameRoleStyle: CSSProperties = { fontSize: 12, marginTop: 2, opacity: 0.85 };
const frameMetaStyle: CSSProperties = {
  display: 'flex',
  gap: 8,
  alignItems: 'center',
  marginTop: 4,
  fontSize: 11,
};
const txRefStyle: CSSProperties = { fontFamily: 'monospace', opacity: 0.7 };
const statusStyle: CSSProperties = { opacity: 0.62, fontStyle: 'italic' };
const txContentStyle: CSSProperties = { display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' };
const txStatusBadgeStyle: CSSProperties = {
  fontSize: 11,
  padding: '1px 8px',
  borderRadius: 4,
  background: 'rgba(91,141,239,0.16)',
  color: '#79a7ff',
  fontWeight: 700,
};
const committedBadgeStyle: CSSProperties = { background: '#3aa76d', color: '#fff' };
const rolledBackBadgeStyle: CSSProperties = { background: 'rgba(224,96,58,0.85)', color: '#fff' };
const suspendedBadgeStyle: CSSProperties = { background: 'rgba(255,204,102,0.25)', color: 'var(--accent)' };
const rollbackOnlyBadgeStyle: CSSProperties = {
  fontSize: 10,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'rgba(224,96,58,0.15)',
  color: '#e0603a',
  fontFamily: 'monospace',
};
const savepointsStyle: CSSProperties = { fontSize: 11, opacity: 0.7, fontFamily: 'monospace' };
const emptyStyle: CSSProperties = { fontSize: 12, opacity: 0.45, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
