import type { CSSProperties, ReactNode } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize @Transactional self-invocation.',
    ru: 'Запустите код, чтобы визуализировать self-invocation с @Transactional.',
  },
  proxy: { en: 'proxy', ru: 'proxy' },
  bean: { en: 'target bean', ru: 'целевой bean' },
  activeTx: { en: 'active transaction', ru: 'активная транзакция' },
  noTx: { en: 'none', ru: 'нет' },
  methods: { en: 'registered methods', ru: 'зарегистрированные методы' },
  callStack: { en: 'call stack', ru: 'стек вызовов' },
  transactions: { en: 'transactions', ru: 'транзакции' },
  emptyStack: { en: 'no active call', ru: 'нет активного вызова' },
  emptyTx: { en: 'no transaction opened', ru: 'транзакций не было' },
  transactional: { en: '@Transactional', ru: '@Transactional' },
  plain: { en: 'plain', ru: 'без @Transactional' },
  viaProxy: { en: 'via proxy', ru: 'через proxy' },
  viaThis: { en: 'this.method()', ru: 'this.method()' },
  viaInjected: { en: 'via self/proxy', ru: 'через self/proxy' },
  ownsTx: { en: 'owns', ru: 'владеет' },
  bypassed: { en: 'proxy skipped', ru: 'proxy пропущен' },
  inTx: { en: 'in', ru: 'в' },
  withoutTx: { en: 'no transaction', ru: 'без транзакции' },
  statusActive: { en: 'active', ru: 'активна' },
  statusCommitted: { en: 'committed', ru: 'закоммичена' },
  statusRolledBack: { en: 'rolled back', ru: 'откачена' },
  owner: { en: 'opened by', ru: 'открыл' },
};

type Via = 'proxy' | 'this' | 'injected';

interface RegisteredMethod {
  method: string;
  transactional: boolean;
  propagation: string;
}

interface Frame {
  method: string;
  via: Via;
  declaredTx: boolean;
  propagation: string;
  ownsTx: boolean;
  bypassed: boolean;
  txId: string | null;
  lastAction: string;
}

interface Transaction {
  id: string;
  owner: string;
  propagation: string;
  status: 'active' | 'committed' | 'rolled-back';
}

interface SelfInvocationState {
  bean: string;
  proxy: string;
  activeTx: string | null;
  registered: RegisteredMethod[];
  stack: Frame[];
  transactions: Transaction[];
}

export default function SelfInvocationVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as SelfInvocationState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  return (
    <div style={wrapStyle}>
      <div style={topStyle}>
        <Box title={tl(LABELS.proxy, lang)} value={state.proxy} highlighted={highlight.has('proxy')} tone="accent" />
        <span style={arrowStyle}>→</span>
        <Box title={tl(LABELS.bean, lang)} value={state.bean} highlighted={highlight.has('bean')} />
        <Stat
          label={tl(LABELS.activeTx, lang)}
          value={state.activeTx ?? tl(LABELS.noTx, lang)}
          tone={state.activeTx ? 'good' : 'bad'}
        />
      </div>

      <Section label={tl(LABELS.methods, lang)}>
        <ArrayGrid cells={methodCells(state.registered, highlight, lang)} />
      </Section>

      <Section label={tl(LABELS.callStack, lang)}>
        {state.stack.length === 0 ? (
          <span style={emptyStyle}>{tl(LABELS.emptyStack, lang)}</span>
        ) : (
          <ArrayGrid cells={stackCells(state.stack, highlight, lang)} />
        )}
      </Section>

      <Section label={tl(LABELS.transactions, lang)}>
        {state.transactions.length === 0 ? (
          <span style={emptyStyle}>{tl(LABELS.emptyTx, lang)}</span>
        ) : (
          <ArrayGrid cells={txCells(state.transactions, highlight, lang)} />
        )}
      </Section>
    </div>
  );
}

function methodCells(methods: RegisteredMethod[], highlight: Set<string>, lang: Lang): ArrayCell[] {
  return methods.map((m) => ({
    key: m.method,
    label: `${m.method}()`,
    highlighted: highlight.has(`method:${m.method}`),
    content: (
      <div style={rowContentStyle}>
        {m.transactional ? (
          <Pill text={`${tl(LABELS.transactional, lang)} (${m.propagation})`} tone="accent" />
        ) : (
          <Pill text={tl(LABELS.plain, lang)} tone="muted" />
        )}
      </div>
    ),
  }));
}

function stackCells(stack: Frame[], highlight: Set<string>, lang: Lang): ArrayCell[] {
  return stack.map((frame, i) => ({
    key: `${frame.method}-${i}`,
    label: `${i + 1}`,
    highlighted: highlight.has(`method:${frame.method}`),
    content: (
      <div style={rowContentStyle}>
        <span style={monoStyle}>{frame.method}()</span>
        <Pill text={viaLabel(frame.via, lang)} tone={frame.via === 'this' ? 'bad' : 'accent'} />
        {frame.bypassed && <Pill text={tl(LABELS.bypassed, lang)} tone="bad" />}
        {frame.ownsTx && <Pill text={tl(LABELS.ownsTx, lang)} tone="good" />}
        {frame.txId ? (
          <span style={txRefStyle}>
            {tl(LABELS.inTx, lang)} {frame.txId}
          </span>
        ) : (
          <span style={{ ...txRefStyle, color: 'var(--bad)' }}>{tl(LABELS.withoutTx, lang)}</span>
        )}
      </div>
    ),
  }));
}

function txCells(transactions: Transaction[], highlight: Set<string>, lang: Lang): ArrayCell[] {
  return transactions.map((t) => ({
    key: t.id,
    label: t.id,
    highlighted: highlight.has(`tx:${t.id}`),
    content: (
      <div style={rowContentStyle}>
        <Pill text={statusLabel(t.status, lang)} tone={statusTone(t.status)} />
        <span style={txRefStyle}>
          {tl(LABELS.owner, lang)} {t.owner}() · {t.propagation}
        </span>
      </div>
    ),
  }));
}

function Section({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div style={sectionStyle}>
      <div style={sectionLabelStyle}>{label}</div>
      {children}
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
      <div style={smallLabelStyle}>{title}</div>
      <div style={{ ...boxValueStyle, ...(tone === 'accent' ? { color: 'var(--accent)' } : {}) }}>{value}</div>
    </div>
  );
}

function Stat({ label, value, tone }: { label: string; value: string; tone?: 'good' | 'bad' }) {
  return (
    <div style={statStyle}>
      <div style={smallLabelStyle}>{label}</div>
      <div style={{ ...statValueStyle, ...toneStyle(tone) }}>{value}</div>
    </div>
  );
}

function Pill({ text, tone }: { text: string; tone: 'good' | 'bad' | 'accent' | 'muted' }) {
  return <span style={{ ...pillStyle, ...toneStyle(tone) }}>{text}</span>;
}

function viaLabel(via: Via, lang: Lang) {
  if (via === 'this') return tl(LABELS.viaThis, lang);
  if (via === 'injected') return tl(LABELS.viaInjected, lang);
  return tl(LABELS.viaProxy, lang);
}

function statusLabel(status: Transaction['status'], lang: Lang) {
  if (status === 'committed') return tl(LABELS.statusCommitted, lang);
  if (status === 'rolled-back') return tl(LABELS.statusRolledBack, lang);
  return tl(LABELS.statusActive, lang);
}

function statusTone(status: Transaction['status']): 'good' | 'bad' | 'accent' {
  if (status === 'committed') return 'good';
  if (status === 'rolled-back') return 'bad';
  return 'accent';
}

function toneStyle(tone?: 'good' | 'bad' | 'accent' | 'muted'): CSSProperties {
  if (tone === 'good') return { color: 'var(--good)', borderColor: 'var(--good)' };
  if (tone === 'bad') return { color: 'var(--bad)', borderColor: 'var(--bad)' };
  if (tone === 'accent') return { color: 'var(--accent)', borderColor: 'var(--accent)' };
  if (tone === 'muted') return { opacity: 0.6 };
  return {};
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const topStyle: CSSProperties = { display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' };
const arrowStyle: CSSProperties = { fontSize: 18, opacity: 0.55 };
const boxStyle: CSSProperties = {
  padding: '6px 10px',
  borderRadius: 8,
  border: '1px solid var(--border)',
  background: 'var(--viz-box)',
  minWidth: 132,
};
const boxHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.24)',
};
const boxValueStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13, fontWeight: 700 };
const smallLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.62 };
const sectionStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const sectionLabelStyle: CSSProperties = { fontSize: 12, opacity: 0.65, fontWeight: 700 };
const statStyle: CSSProperties = {
  minWidth: 130,
  padding: '5px 8px',
  borderRadius: 6,
  border: '1px solid var(--border)',
  background: 'var(--viz-box)',
};
const statValueStyle: CSSProperties = { fontSize: 13, fontWeight: 700, fontFamily: 'monospace' };
const rowContentStyle: CSSProperties = { display: 'flex', gap: 7, alignItems: 'center', flexWrap: 'wrap' };
const pillStyle: CSSProperties = {
  display: 'inline-flex',
  border: '1px solid var(--border)',
  borderRadius: 999,
  padding: '2px 7px',
  fontSize: 12,
  fontFamily: 'monospace',
  background: 'var(--viz-box)',
};
const monoStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 12, fontWeight: 700 };
const txRefStyle: CSSProperties = { fontSize: 12, opacity: 0.78, fontFamily: 'monospace' };
const emptyStyle: CSSProperties = { fontSize: 12, opacity: 0.45, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
