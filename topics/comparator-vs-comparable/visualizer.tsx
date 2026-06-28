import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  order: { en: 'order', ru: 'порядок' },
  source: { en: 'source', ru: 'источник' },
  operation: { en: 'operation', ru: 'операция' },
  result: { en: 'result', ru: 'результат' },
  comparison: { en: 'comparison', ru: 'сравнение' },
  left: { en: 'left', ru: 'левое' },
  right: { en: 'right', ru: 'правое' },
  integerCompare: { en: 'Integer.compare', ru: 'Integer.compare' },
  subtraction: { en: 'subtraction', ru: 'вычитание' },
  overflowRisk: { en: 'overflow risk', ru: 'риск overflow' },
  yes: { en: 'yes', ru: 'да' },
  no: { en: 'no', ru: 'нет' },
  empty: { en: 'empty', ru: 'пусто' },
  runHint: {
    en: 'Run the code to visualize comparison decisions.',
    ru: 'Запустите код, чтобы увидеть решения сравнения.',
  },
};

const OP_LABELS: Record<string, Localized> = {
  created: { en: 'created', ru: 'создано' },
  add: { en: 'added value', ru: 'добавлено значение' },
  compareTo: { en: 'compareTo()', ru: 'compareTo()' },
  comparator: { en: 'Comparator.compare()', ru: 'Comparator.compare()' },
  sorted: { en: 'sorted', ru: 'отсортировано' },
  'safe-compare': { en: 'safe int compare', ru: 'безопасное сравнение int' },
};

interface ValueItem {
  index: number;
  value: string;
  numericValue?: number;
}

interface LastComparison {
  source: string;
  left: string;
  right: string;
  result: number;
  sign: number;
  meaningEn: string;
  meaningRu: string;
}

interface SafeCompare {
  fieldName: string;
  leftLabel: string;
  leftValue: number;
  rightLabel: string;
  rightValue: number;
  subtractionResult: number;
  safeResult: number;
  overflowRisk: boolean;
}

interface OrderingState {
  name: string;
  order: string;
  orderEn?: string;
  orderRu?: string;
  source: string;
  operation: string;
  values: ValueItem[];
  lastComparison?: LastComparison;
  safeCompare?: SafeCompare;
}

export default function ComparatorVsComparableVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as OrderingState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const cells: ArrayCell[] =
    state.values.length === 0
      ? [
          {
            key: 'empty',
            label: '[]',
            content: <span style={emptyStyle}>{tl(LABELS.empty, lang)}</span>,
          },
        ]
      : state.values.map((item) => ({
          key: item.index,
          label: `[${item.index}]`,
          highlighted: highlight.has(`value:${item.value}`),
          content: (
            <span style={valueStyle}>
              {item.value}
              {item.numericValue !== undefined ? ` = ${item.numericValue}` : ''}
            </span>
          ),
        }));

  const compareBoxes: Box[] = state.lastComparison
    ? [
        {
          id: 'left',
          title: state.lastComparison.left,
          subtitle: tl(LABELS.left, lang),
          highlighted: highlight.has(`value:${state.lastComparison.left}`),
        },
        {
          id: 'right',
          title: state.lastComparison.right,
          subtitle: tl(LABELS.right, lang),
          highlighted: highlight.has(`value:${state.lastComparison.right}`),
        },
      ]
    : [];

  return (
    <div style={wrapStyle}>
      <div style={statsStyle}>
        <Stat label={tl(LABELS.source, lang)} value={state.source} />
        <Stat
          label={tl(LABELS.order, lang)}
          value={lang === 'ru' ? state.orderRu ?? state.order : state.orderEn ?? state.order}
        />
        <Stat label={tl(LABELS.operation, lang)} value={operationLabel(state.operation, lang)} highlight />
        {state.lastComparison && (
          <Stat
            label={tl(LABELS.result, lang)}
            value={`${state.lastComparison.result} (${localizedMeaning(state.lastComparison, lang)})`}
            highlight
          />
        )}
      </div>

      {state.lastComparison && (
        <div style={compareStyle}>
          <div style={sectionLabelStyle}>{tl(LABELS.comparison, lang)}</div>
          <BoxGroup boxes={compareBoxes} />
        </div>
      )}

      {state.safeCompare && (
        <div style={safeCompareStyle}>
          <Stat label={tl(LABELS.integerCompare, lang)} value={String(state.safeCompare.safeResult)} highlight />
          <Stat label={tl(LABELS.subtraction, lang)} value={String(state.safeCompare.subtractionResult)} />
          <Stat
            label={tl(LABELS.overflowRisk, lang)}
            value={state.safeCompare.overflowRisk ? tl(LABELS.yes, lang) : tl(LABELS.no, lang)}
            highlight={state.safeCompare.overflowRisk}
          />
        </div>
      )}

      <ArrayGrid cells={cells} />
    </div>
  );
}

function localizedMeaning(comparison: LastComparison, lang: 'en' | 'ru'): string {
  return lang === 'ru' ? comparison.meaningRu : comparison.meaningEn;
}

function operationLabel(kind: string, lang: 'en' | 'ru'): string {
  return tl(OP_LABELS[kind] ?? { en: kind, ru: kind }, lang);
}

function Stat({ label, value, highlight }: { label: string; value: string; highlight?: boolean }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={{ ...statValueStyle, color: highlight ? 'var(--accent)' : 'var(--text)' }}>{value}</div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const statsStyle: CSSProperties = { display: 'flex', gap: 16, alignItems: 'flex-start', flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 14, fontWeight: 700, fontFamily: 'monospace' };
const compareStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, textTransform: 'uppercase' };
const safeCompareStyle: CSSProperties = { display: 'flex', gap: 16, alignItems: 'flex-start', flexWrap: 'wrap' };
const valueStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700, fontSize: 14 };
const emptyStyle: CSSProperties = { opacity: 0.4, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
