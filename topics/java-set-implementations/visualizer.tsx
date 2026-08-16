import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang, type Localized, type Lang } from '@app/i18n';

const LABELS = {
  operation: { en: 'operation', ru: 'операция' },
  probe: { en: 'value', ru: 'значение' },
  structure: { en: 'structure', ru: 'структура' },
  order: { en: 'order', ru: 'порядок' },
  uniqueness: { en: 'unique by', ru: 'уникальность по' },
  cost: { en: 'cost', ru: 'стоимость' },
  size: { en: 'size', ru: 'размер' },
  result: { en: 'result', ru: 'результат' },
  empty: { en: 'empty', ru: 'пусто' },
  runHint: {
    en: 'Run the code to compare Set implementations.',
    ru: 'Запустите код, чтобы сравнить реализации Set.',
  },
};

const OP_LABELS: Record<string, Localized> = {
  created: { en: 'created', ru: 'создано' },
  add: { en: 'add', ru: 'add' },
  contains: { en: 'contains', ru: 'contains' },
  remove: { en: 'remove', ru: 'remove' },
  iterate: { en: 'iterate', ru: 'итерация' },
};

const STATUS_LABELS: Record<string, Localized> = {
  ready: { en: 'ready', ru: 'готово' },
  added: { en: 'added', ru: 'добавлено' },
  duplicate: { en: 'duplicate', ru: 'дубликат' },
  found: { en: 'found', ru: 'найдено' },
  missing: { en: 'missing', ru: 'нет' },
  removed: { en: 'removed', ru: 'удалено' },
  rejected: { en: 'rejected', ru: 'отвергнуто' },
};

interface ValueItem {
  index: number;
  value: string;
}

interface ResultState {
  status: string;
  detail: Localized;
}

interface SetImplementation {
  id: string;
  title: string;
  structure: Localized;
  order: Localized;
  uniqueness: Localized;
  cost: Localized;
  size: number;
  values: ValueItem[];
  lastResult: ResultState;
}

interface SetComparisonState {
  name: string;
  operation: string;
  probe?: string | null;
  note: Localized;
  implementations: SetImplementation[];
}

export default function SetComparisonVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as SetComparisonState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const cells: ArrayCell[] = state.implementations.map((impl) => ({
    key: impl.id,
    label: shortLabel(impl.id, impl.title),
    highlighted: highlight.has(`impl:${impl.id}`),
    content: <ImplementationView impl={impl} highlight={highlight} lang={lang} />,
  }));

  return (
    <div style={wrapStyle}>
      <div style={summaryStyle}>
        <Stat label={tl(LABELS.operation, lang)} value={tl(OP_LABELS[state.operation] ?? { en: state.operation, ru: state.operation }, lang)} />
        {state.probe && <Stat label={tl(LABELS.probe, lang)} value={state.probe} />}
        <div style={noteStyle}>{tl(state.note, lang)}</div>
      </div>
      <ArrayGrid cells={cells} />
    </div>
  );
}

function ImplementationView({
  impl,
  highlight,
  lang,
}: {
  impl: SetImplementation;
  highlight: Set<string>;
  lang: Lang;
}) {
  const nodes: LinkedNode[] = impl.values.map((item) => ({
    id: `${impl.id}-${item.index}-${item.value}`,
    title: item.value,
    subtitle: `#${item.index}`,
    highlighted: highlight.has(`${impl.id}:value:${item.value}`) || highlight.has(`value:${item.value}`),
  }));

  return (
    <div style={implStyle}>
      <div style={implHeaderStyle}>
        <div style={titleStyle}>{impl.title}</div>
        <span style={statusStyle}>{tl(STATUS_LABELS[impl.lastResult.status] ?? { en: impl.lastResult.status, ru: impl.lastResult.status }, lang)}</span>
      </div>
      <div style={factsStyle}>
        <Fact label={tl(LABELS.structure, lang)} value={tl(impl.structure, lang)} />
        <Fact label={tl(LABELS.order, lang)} value={tl(impl.order, lang)} />
        <Fact label={tl(LABELS.uniqueness, lang)} value={tl(impl.uniqueness, lang)} />
        <Fact label={tl(LABELS.cost, lang)} value={tl(impl.cost, lang)} />
        <Fact label={tl(LABELS.size, lang)} value={String(impl.size)} />
      </div>
      <LinkedNodes nodes={nodes} />
      {impl.values.length === 0 && <span style={emptyStyle}>{tl(LABELS.empty, lang)}</span>}
      <div style={resultStyle}>
        <span style={resultLabelStyle}>{tl(LABELS.result, lang)}:</span>{' '}
        {tl(impl.lastResult.detail, lang)}
      </div>
    </div>
  );
}

function shortLabel(id: string, title: string): string {
  return id === 'linkedhashset' ? 'Linked' : title;
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <span style={factStyle}>
      <span style={factLabelStyle}>{label}</span> {value}
    </span>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={statValueStyle}>{value}</div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const summaryStyle: CSSProperties = { display: 'flex', gap: 16, alignItems: 'center', flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center', minWidth: 72 };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.62 };
const statValueStyle: CSSProperties = { fontSize: 15, fontWeight: 700, fontFamily: 'monospace' };
const noteStyle: CSSProperties = { fontSize: 13, opacity: 0.78, maxWidth: 760, lineHeight: 1.35 };
const implStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 7, minWidth: 0, padding: '3px 0' };
const implHeaderStyle: CSSProperties = { display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' };
const titleStyle: CSSProperties = { fontWeight: 800, fontSize: 14 };
const statusStyle: CSSProperties = {
  fontSize: 11,
  fontFamily: 'monospace',
  color: 'var(--accent)',
  border: '1px solid var(--border)',
  borderRadius: 4,
  padding: '1px 5px',
};
const factsStyle: CSSProperties = { display: 'flex', gap: 8, flexWrap: 'wrap', fontSize: 12 };
const factStyle: CSSProperties = { opacity: 0.82 };
const factLabelStyle: CSSProperties = { opacity: 0.58 };
const resultStyle: CSSProperties = { fontSize: 12, opacity: 0.78, lineHeight: 1.35 };
const resultLabelStyle: CSSProperties = { fontWeight: 700 };
const emptyStyle: CSSProperties = { fontSize: 12, opacity: 0.45, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
