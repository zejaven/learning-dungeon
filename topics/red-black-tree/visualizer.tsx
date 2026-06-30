import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { TreeView, type TreeNode } from '@app/primitives/TreeView';
import { tl, useLang, type Lang, type Localized } from '@app/i18n';

const LABELS = {
  size: { en: 'size', ru: 'размер' },
  height: { en: 'height', ru: 'высота' },
  blackHeight: { en: 'black-height', ru: 'black-height' },
  lastOp: { en: 'last operation', ru: 'последняя операция' },
  result: { en: 'result', ru: 'результат' },
  path: { en: 'path', ru: 'путь' },
  sortedOrder: { en: 'in-order keys', ru: 'ключи in-order' },
  red: { en: 'red', ru: 'красный' },
  black: { en: 'black', ru: 'чёрный' },
  empty: { en: 'empty', ru: 'пусто' },
  runHint: {
    en: 'Run the code to visualize the red-black tree.',
    ru: 'Запустите код, чтобы визуализировать красно-чёрное дерево.',
  },
};

const OP_LABELS: Record<string, Localized> = {
  created: { en: 'created', ru: 'создано' },
  'insert-root': { en: 'insert root', ru: 'вставка корня' },
  insert: { en: 'insert red leaf', ru: 'вставка красного листа' },
  recolor: { en: 'recolor', ru: 'перекраска' },
  'rotate-left': { en: 'left rotation', ru: 'левый поворот' },
  'rotate-right': { en: 'right rotation', ru: 'правый поворот' },
  'root-black': { en: 'root forced black', ru: 'корень сделан чёрным' },
  search: { en: 'search', ru: 'поиск' },
  duplicate: { en: 'duplicate ignored', ru: 'дубликат проигнорирован' },
};

const RESULT_LABELS: Record<string, Localized> = {
  inserted: { en: 'inserted', ru: 'вставлен' },
  'inserted red leaf': { en: 'inserted red leaf', ru: 'вставлен красный лист' },
  'red uncle': { en: 'red uncle', ru: 'красный дядя' },
  'root forced black': { en: 'root forced black', ru: 'корень сделан чёрным' },
  'rotated left': { en: 'rotated left', ru: 'левый поворот' },
  'rotated right': { en: 'rotated right', ru: 'правый поворот' },
  found: { en: 'found', ru: 'найден' },
  missing: { en: 'missing', ru: 'не найден' },
  'already present': { en: 'already present', ru: 'уже есть' },
};

interface RbtNode {
  id: string;
  key: number;
  color: 'R' | 'B';
  left?: RbtNode | null;
  right?: RbtNode | null;
}

interface OrderedKey {
  index: number;
  key: number;
  color: 'R' | 'B';
}

interface LastOp {
  kind: string;
  key?: number | null;
  result?: string | null;
  path: number[];
}

interface RbtState {
  name: string;
  size: number;
  height: number;
  blackHeight: number;
  root: RbtNode | null;
  inOrder: OrderedKey[];
  lastOp: LastOp;
}

export default function RedBlackTreeVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as RbtState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const cells: ArrayCell[] =
    state.inOrder.length === 0
      ? [
          {
            key: 'empty',
            label: '[]',
            content: <span style={emptyStyle}>{tl(LABELS.empty, lang)}</span>,
          },
        ]
      : state.inOrder.map((item) => ({
          key: item.index,
          label: `[${item.index}]`,
          highlighted: highlight.has(`node:${item.key}`) || highlight.has(`path:${item.key}`),
          content: (
            <span style={valueStyle}>
              {item.key} <span style={colorTextStyle}>{colorLabel(item.color, lang)}</span>
            </span>
          ),
        }));

  return (
    <div style={wrapStyle}>
      <div style={statsStyle}>
        <Stat label={tl(LABELS.size, lang)} value={state.size} />
        <Stat label={tl(LABELS.height, lang)} value={state.height} />
        <Stat label={tl(LABELS.blackHeight, lang)} value={state.blackHeight} />
        <Stat label={tl(LABELS.lastOp, lang)} value={operationLabel(state.lastOp.kind, lang)} highlight />
        {state.lastOp.result && <Stat label={tl(LABELS.result, lang)} value={resultLabel(state.lastOp.result, lang)} />}
      </div>

      {state.lastOp.path.length > 0 && (
        <div style={pathStyle}>
          <span>{tl(LABELS.path, lang)} </span>
          <code>{state.lastOp.path.join(' -> ')}</code>
        </div>
      )}

      <div style={treePanelStyle}>
        <TreeView root={toTree(state.root, highlight, lang)} />
      </div>

      <div style={sectionTitleStyle}>{tl(LABELS.sortedOrder, lang)}</div>
      <ArrayGrid cells={cells} />
    </div>
  );
}

function toTree(node: RbtNode | null, highlight: Set<string>, lang: Lang): TreeNode | null {
  if (!node) return null;
  return {
    id: node.id,
    title: String(node.key),
    subtitle: colorLabel(node.color, lang),
    color: node.color,
    highlighted: highlight.has(`node:${node.key}`) || highlight.has(`path:${node.key}`),
    left: toTree(node.left ?? null, highlight, lang),
    right: toTree(node.right ?? null, highlight, lang),
  };
}

function colorLabel(color: 'R' | 'B', lang: Lang): string {
  return color === 'R' ? tl(LABELS.red, lang) : tl(LABELS.black, lang);
}

function operationLabel(kind: string, lang: Lang): string {
  return tl(OP_LABELS[kind] ?? { en: kind, ru: kind }, lang);
}

function resultLabel(result: string, lang: Lang): string {
  return tl(RESULT_LABELS[result] ?? { en: result, ru: result }, lang);
}

function Stat({
  label,
  value,
  highlight,
}: {
  label: string;
  value: number | string;
  highlight?: boolean;
}) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={{ ...statValueStyle, color: highlight ? 'var(--accent)' : 'var(--text)' }}>
        {value}
      </div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const statsStyle: CSSProperties = { display: 'flex', gap: 16, alignItems: 'flex-start', flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 15, fontWeight: 700, fontFamily: 'monospace' };
const pathStyle: CSSProperties = { fontSize: 13, opacity: 0.8 };
const treePanelStyle: CSSProperties = { overflowX: 'auto', padding: '4px 0 8px' };
const sectionTitleStyle: CSSProperties = { fontSize: 12, opacity: 0.7, fontWeight: 700 };
const valueStyle: CSSProperties = { display: 'inline-flex', gap: 8, alignItems: 'baseline', fontFamily: 'monospace' };
const colorTextStyle: CSSProperties = { fontSize: 11, opacity: 0.7 };
const emptyStyle: CSSProperties = { opacity: 0.4, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
