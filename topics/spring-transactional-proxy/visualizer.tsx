import type { CSSProperties, ReactNode } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize the @Transactional proxy.',
    ru: 'Запустите код, чтобы увидеть @Transactional proxy.',
  },
  proxy: { en: 'proxy', ru: 'proxy' },
  bean: { en: 'target bean', ru: 'целевой бин' },
  tx: { en: 'transaction', ru: 'транзакция' },
  active: { en: 'active', ru: 'активна' },
  none: { en: 'none', ru: 'нет' },
  owner: { en: 'opened by', ru: 'открыл' },
  callStack: { en: 'call stack', ru: 'стек вызовов' },
  emptyStack: { en: 'no active call', ru: 'нет активного вызова' },
  staged: { en: 'staged (in transaction)', ru: 'подготовлено (в транзакции)' },
  db: { en: 'committed database', ru: 'зафиксированная база' },
  noRows: { en: 'no rows', ru: 'нет строк' },
  viaProxy: { en: 'via proxy', ru: 'через proxy' },
  viaSelf: { en: 'this.* (bypasses proxy)', ru: 'this.* (минуя proxy)' },
  viaDirect: { en: 'raw object (no proxy)', ru: 'голый объект (без proxy)' },
  transactional: { en: '@Transactional', ru: '@Transactional' },
  inTx: { en: 'in transaction', ru: 'в транзакции' },
  noTx: { en: 'no transaction', ru: 'без транзакции' },
};

type Via = 'proxy' | 'self' | 'direct';

interface Frame {
  method: string;
  via: Via;
  transactional: boolean;
  inTx: boolean;
}

interface Row {
  id: string;
  value: string;
}

interface TxState {
  bean: string;
  proxy: string;
  txActive: boolean;
  txOwner: string;
  stack: Frame[];
  staged: Row[];
  database: Row[];
}

export default function TransactionalProxyVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as TxState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const stack = state.stack ?? [];
  const staged = state.staged ?? [];
  const database = state.database ?? [];

  return (
    <div style={wrapStyle}>
      <div style={topBarStyle}>
        <Box
          title={tl(LABELS.proxy, lang)}
          value={state.proxy}
          highlighted={highlight.has('proxy')}
          tone="accent"
        />
        <span style={arrowStyle}>→</span>
        <Box
          title={tl(LABELS.bean, lang)}
          value={state.bean}
          highlighted={highlight.has('bean')}
        />
        <TxBadge
          active={state.txActive}
          owner={state.txOwner}
          highlighted={highlight.has('tx')}
          lang={lang}
        />
      </div>

      <Section label={tl(LABELS.callStack, lang)}>
        {stack.length === 0 ? (
          <span style={emptyStyle}>{tl(LABELS.emptyStack, lang)}</span>
        ) : (
          <div style={stackStyle}>
            {stack.map((frame, i) => (
              <FrameView
                key={`${frame.method}-${i}`}
                frame={frame}
                depth={i}
                highlighted={highlight.has(`call:${frame.method}`)}
                lang={lang}
              />
            ))}
          </div>
        )}
      </Section>

      <ArrayGrid
        cells={[
          rowCell('staged', tl(LABELS.staged, lang), staged, 'accent', highlight, 'staged', lang),
          rowCell('db', tl(LABELS.db, lang), database, 'good', highlight, 'db', lang),
        ]}
      />
    </div>
  );
}

function rowCell(
  key: string,
  label: string,
  rows: Row[],
  tone: 'good' | 'accent',
  highlight: Set<string>,
  prefix: string,
  lang: Lang,
): ArrayCell {
  return {
    key,
    label,
    highlighted: highlight.has(key),
    content: (
      <div style={rowWrapStyle}>
        {rows.length === 0 ? (
          <span style={emptyStyle}>{tl(LABELS.noRows, lang)}</span>
        ) : (
          rows.map((row) => (
            <Pill
              key={row.id}
              text={`${row.id}: ${row.value}`}
              tone={tone}
              highlighted={highlight.has(`${prefix}:${row.id}`)}
            />
          ))
        )}
      </div>
    ),
  };
}

function Section({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div style={sectionStyle}>
      <div style={sectionLabelStyle}>{label}</div>
      {children}
    </div>
  );
}

function FrameView({
  frame,
  depth,
  highlighted,
  lang,
}: {
  frame: Frame;
  depth: number;
  highlighted: boolean;
  lang: Lang;
}) {
  const viaLabel =
    frame.via === 'proxy'
      ? tl(LABELS.viaProxy, lang)
      : frame.via === 'self'
        ? tl(LABELS.viaSelf, lang)
        : tl(LABELS.viaDirect, lang);
  const viaTone: 'good' | 'bad' | 'accent' = frame.via === 'proxy' ? 'accent' : 'bad';
  return (
    <div
      style={{
        ...frameStyle,
        marginLeft: depth * 18,
        ...(highlighted ? frameHighlightStyle : {}),
      }}
    >
      <span style={frameMethodStyle}>{frame.method}()</span>
      <Pill text={viaLabel} tone={viaTone} highlighted={false} small />
      {frame.transactional ? (
        <Pill text={tl(LABELS.transactional, lang)} tone="accent" highlighted={false} small />
      ) : null}
      <Pill
        text={frame.inTx ? tl(LABELS.inTx, lang) : tl(LABELS.noTx, lang)}
        tone={frame.inTx ? 'good' : 'bad'}
        highlighted={false}
        small
      />
    </div>
  );
}

function TxBadge({
  active,
  owner,
  highlighted,
  lang,
}: {
  active: boolean;
  owner: string;
  highlighted: boolean;
  lang: Lang;
}) {
  return (
    <div
      style={{
        ...txBadgeStyle,
        ...(active ? txActiveStyle : {}),
        ...(highlighted ? txHighlightStyle : {}),
      }}
    >
      <div style={infoLabelStyle}>{tl(LABELS.tx, lang)}</div>
      <div style={{ ...infoValueStyle, color: active ? 'var(--good)' : undefined }}>
        {active ? tl(LABELS.active, lang) : tl(LABELS.none, lang)}
      </div>
      {active && owner ? (
        <div style={txOwnerStyle}>
          {tl(LABELS.owner, lang)}: {owner}()
        </div>
      ) : null}
    </div>
  );
}

function Box({
  title,
  value,
  highlighted,
  tone,
}: {
  title: string;
  value: string;
  highlighted: boolean;
  tone?: 'accent';
}) {
  return (
    <div style={{ ...boxStyle, ...(highlighted ? boxHighlightStyle : {}) }}>
      <div style={infoLabelStyle}>{title}</div>
      <div style={{ ...boxValueStyle, ...(tone === 'accent' ? { color: 'var(--accent)' } : {}) }}>
        {value}
      </div>
    </div>
  );
}

function Pill({
  text,
  tone,
  highlighted,
  small,
}: {
  text: string;
  tone: 'good' | 'bad' | 'accent';
  highlighted: boolean;
  small?: boolean;
}) {
  return (
    <span
      style={{
        ...pillStyle,
        ...(small ? pillSmallStyle : {}),
        ...toneStyle(tone),
        ...(highlighted ? pillHighlightStyle : {}),
      }}
    >
      {text}
    </span>
  );
}

function toneStyle(tone?: 'good' | 'bad' | 'accent'): CSSProperties {
  if (tone === 'good') return { color: 'var(--good)', borderColor: 'var(--good)' };
  if (tone === 'bad') return { color: 'var(--bad)', borderColor: 'var(--bad)' };
  if (tone === 'accent') return { color: 'var(--accent)', borderColor: 'var(--accent)' };
  return {};
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const topBarStyle: CSSProperties = { display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center' };
const arrowStyle: CSSProperties = { fontSize: 20, opacity: 0.6 };
const boxStyle: CSSProperties = {
  padding: '6px 10px',
  borderRadius: 8,
  border: '1px solid var(--border)',
  background: 'var(--viz-box)',
  minWidth: 120,
};
const boxHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.24)',
};
const boxValueStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13, fontWeight: 700 };
const infoLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const infoValueStyle: CSSProperties = { fontSize: 15, fontWeight: 700, fontFamily: 'monospace' };
const txBadgeStyle: CSSProperties = {
  padding: '6px 10px',
  borderRadius: 8,
  border: '1px solid var(--border)',
  background: 'var(--viz-box)',
  minWidth: 120,
};
const txActiveStyle: CSSProperties = { borderColor: 'var(--good)' };
const txHighlightStyle: CSSProperties = { boxShadow: '0 0 0 2px rgba(255,204,102,0.24)' };
const txOwnerStyle: CSSProperties = { fontSize: 11, opacity: 0.7, fontFamily: 'monospace' };
const sectionStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const sectionLabelStyle: CSSProperties = { fontSize: 12, opacity: 0.6, fontWeight: 600 };
const stackStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 4 };
const frameStyle: CSSProperties = {
  display: 'flex',
  gap: 6,
  alignItems: 'center',
  flexWrap: 'wrap',
  padding: '4px 8px',
  borderRadius: 6,
  background: 'var(--viz-box)',
  border: '1px solid var(--border)',
};
const frameHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.22)',
};
const frameMethodStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13, fontWeight: 700 };
const rowWrapStyle: CSSProperties = { display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' };
const pillStyle: CSSProperties = {
  display: 'inline-flex',
  border: '1px solid var(--border)',
  borderRadius: 999,
  padding: '3px 8px',
  fontSize: 12,
  fontFamily: 'monospace',
  background: 'var(--viz-box)',
};
const pillSmallStyle: CSSProperties = { fontSize: 11, padding: '1px 7px' };
const pillHighlightStyle: CSSProperties = {
  boxShadow: '0 0 0 2px rgba(255,204,102,0.28)',
  borderColor: 'var(--accent)',
};
const emptyStyle: CSSProperties = { fontSize: 12, opacity: 0.45, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
