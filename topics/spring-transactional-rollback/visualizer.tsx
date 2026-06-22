import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize Spring transaction rollback rules.',
    ru: 'Запустите код, чтобы увидеть правила отката Spring transactions.',
  },
  phase: { en: 'phase', ru: 'фаза' },
  method: { en: 'method', ru: 'метод' },
  rollbackOnly: { en: 'rollback-only', ru: 'rollback-only' },
  yes: { en: 'yes', ru: 'да' },
  no: { en: 'no', ru: 'нет' },
  tx: { en: 'transaction / persistence context', ru: 'transaction / persistence context' },
  rules: { en: 'rollback rules', ru: 'rollback rules' },
  db: { en: 'committed database', ru: 'зафиксированная база' },
  noRows: { en: 'no rows', ru: 'нет строк' },
  noRules: { en: 'default rules only', ru: 'только правила по умолчанию' },
  rollbackFor: { en: 'rollbackFor', ru: 'rollbackFor' },
  noRollbackFor: { en: 'noRollbackFor', ru: 'noRollbackFor' },
  exception: { en: 'exception', ru: 'exception' },
  source: { en: 'source', ru: 'источник' },
  decision: { en: 'decision', ru: 'решение' },
  kind: { en: 'kind', ru: 'тип' },
  matchedRule: { en: 'matched rule', ru: 'совпавшее правило' },
};

type Phase = 'idle' | 'active' | 'committed' | 'rolled_back' | 'external_failed';

interface Row {
  id: string;
  value: string;
}

interface Rules {
  rollbackFor: string[];
  noRollbackFor: string[];
}

interface TxException {
  name: string;
  kind: string;
  source: string;
  decision: string;
  matchedRule: string;
}

interface TxState {
  name: string;
  phase: Phase;
  currentMethod?: string;
  rollbackOnly: boolean;
  staged: Row[];
  database: Row[];
  rules: Rules;
  exception?: TxException;
}

export default function TransactionalRollbackVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as TxState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const staged = state.staged ?? [];
  const database = state.database ?? [];
  const rules = state.rules ?? { rollbackFor: [], noRollbackFor: [] };

  const cells: ArrayCell[] = [
    {
      key: 'tx',
      label: tl(LABELS.tx, lang),
      highlighted: highlight.has('tx') || highlight.has('rollbackOnly'),
      content: (
        <div style={rowWrapStyle}>
          {staged.length === 0 ? (
            <span style={emptyStyle}>{tl(LABELS.noRows, lang)}</span>
          ) : (
            staged.map((row) => (
              <Pill
                key={row.id}
                text={`${row.id}: ${row.value}`}
                highlighted={highlight.has(`staged:${row.id}`)}
                tone="accent"
              />
            ))
          )}
        </div>
      ),
    },
    {
      key: 'rules',
      label: tl(LABELS.rules, lang),
      highlighted: false,
      content: <RulesView rules={rules} highlight={highlight} lang={lang} />,
    },
    {
      key: 'db',
      label: tl(LABELS.db, lang),
      highlighted: false,
      content: (
        <div style={rowWrapStyle}>
          {database.length === 0 ? (
            <span style={emptyStyle}>{tl(LABELS.noRows, lang)}</span>
          ) : (
            database.map((row) => (
              <Pill
                key={row.id}
                text={`${row.id}: ${row.value}`}
                highlighted={highlight.has(`db:${row.id}`)}
                tone="good"
              />
            ))
          )}
        </div>
      ),
    },
  ];

  return (
    <div style={wrapStyle}>
      <div style={topBarStyle}>
        <Info label={tl(LABELS.phase, lang)} value={state.phase} tone={phaseTone(state.phase)} />
        {state.currentMethod ? <Info label={tl(LABELS.method, lang)} value={state.currentMethod} /> : null}
        <Info
          label={tl(LABELS.rollbackOnly, lang)}
          value={state.rollbackOnly ? tl(LABELS.yes, lang) : tl(LABELS.no, lang)}
          tone={state.rollbackOnly ? 'bad' : undefined}
        />
      </div>

      {state.exception ? (
        <ExceptionBanner exceptionInfo={state.exception} highlighted={highlight.has('exception')} lang={lang} />
      ) : null}

      <ArrayGrid cells={cells} />
    </div>
  );
}

function RulesView({ rules, highlight, lang }: { rules: Rules; highlight: Set<string>; lang: Lang }) {
  const chips = [
    ...rules.rollbackFor.map((rule) => ({
      id: `rollbackFor:${rule}`,
      label: `${tl(LABELS.rollbackFor, lang)}: ${rule}`,
      tone: 'bad' as const,
      highlighted: highlight.has(`rule:rollbackFor:${rule}`),
    })),
    ...rules.noRollbackFor.map((rule) => ({
      id: `noRollbackFor:${rule}`,
      label: `${tl(LABELS.noRollbackFor, lang)}: ${rule}`,
      tone: 'good' as const,
      highlighted: highlight.has(`rule:noRollbackFor:${rule}`),
    })),
  ];
  if (chips.length === 0) {
    return <span style={emptyStyle}>{tl(LABELS.noRules, lang)}</span>;
  }
  return (
    <div style={rowWrapStyle}>
      {chips.map((chip) => (
        <Pill key={chip.id} text={chip.label} tone={chip.tone} highlighted={chip.highlighted} />
      ))}
    </div>
  );
}

function ExceptionBanner({
  exceptionInfo,
  highlighted,
  lang,
}: {
  exceptionInfo: TxException;
  highlighted: boolean;
  lang: Lang;
}) {
  return (
    <div style={{ ...bannerStyle, ...(highlighted ? bannerHighlightStyle : {}) }}>
      <span style={tagStyle}>{tl(LABELS.exception, lang)}</span>
      <strong>{exceptionInfo.name}</strong>
      <span>{tl(LABELS.kind, lang)}: {exceptionInfo.kind}</span>
      <span>{tl(LABELS.source, lang)}: {exceptionInfo.source}</span>
      <span>{tl(LABELS.decision, lang)}: {exceptionInfo.decision}</span>
      <span>{tl(LABELS.matchedRule, lang)}: {exceptionInfo.matchedRule}</span>
    </div>
  );
}

function Info({ label, value, tone }: { label: string; value: string; tone?: 'good' | 'bad' | 'accent' }) {
  return (
    <div style={infoStyle}>
      <div style={infoLabelStyle}>{label}</div>
      <div style={{ ...infoValueStyle, ...toneStyle(tone) }}>{value}</div>
    </div>
  );
}

function Pill({
  text,
  highlighted,
  tone,
}: {
  text: string;
  highlighted: boolean;
  tone: 'good' | 'bad' | 'accent';
}) {
  return (
    <span style={{ ...pillStyle, ...toneStyle(tone), ...(highlighted ? pillHighlightStyle : {}) }}>
      {text}
    </span>
  );
}

function phaseTone(phase: Phase): 'good' | 'bad' | 'accent' | undefined {
  if (phase === 'committed') return 'good';
  if (phase === 'rolled_back' || phase === 'external_failed') return 'bad';
  if (phase === 'active') return 'accent';
  return undefined;
}

function toneStyle(tone?: 'good' | 'bad' | 'accent'): CSSProperties {
  if (tone === 'good') return { color: 'var(--good)', borderColor: 'var(--good)' };
  if (tone === 'bad') return { color: 'var(--bad)', borderColor: 'var(--bad)' };
  if (tone === 'accent') return { color: 'var(--accent)', borderColor: 'var(--accent)' };
  return {};
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const topBarStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const infoStyle: CSSProperties = { minWidth: 92 };
const infoLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const infoValueStyle: CSSProperties = { fontSize: 15, fontWeight: 700, fontFamily: 'monospace' };
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
const pillHighlightStyle: CSSProperties = {
  boxShadow: '0 0 0 2px rgba(255,204,102,0.28)',
  borderColor: 'var(--accent)',
};
const bannerStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 10,
  flexWrap: 'wrap',
  padding: '8px 12px',
  borderRadius: 8,
  background: 'var(--viz-box)',
  border: '1px solid var(--viz-active)',
  fontSize: 12,
};
const bannerHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.22)',
};
const tagStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const emptyStyle: CSSProperties = { fontSize: 12, opacity: 0.45, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
