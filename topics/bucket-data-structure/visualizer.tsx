import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { TreeView, type TreeNode } from '@app/primitives/TreeView';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  structure: { en: 'structure', ru: 'структура' },
  list: { en: 'linked list', ru: 'связный список' },
  tree: { en: 'red-black tree', ru: 'красно-чёрное дерево' },
  count: { en: 'count', ru: 'кол-во' },
  capacity: { en: 'capacity', ru: 'ёмкость' },
  treeifyAt: { en: 'treeify at', ru: 'treeify при' },
  untreeifyAt: { en: 'untreeify at', ru: 'untreeify при' },
  hash: { en: 'hash', ru: 'хэш' },
  bucket: { en: 'bucket', ru: 'бакет' },
  runHint: {
    en: 'Run the code to visualize the bucket.',
    ru: 'Запустите код, чтобы визуализировать бакет.',
  },
};

interface Entry {
  key: string;
  value: string;
  hash: number;
}
interface TreeJson extends Entry {
  color?: 'R' | 'B';
  left?: TreeJson | null;
  right?: TreeJson | null;
}
interface BucketState {
  index: number;
  capacity: number;
  structure: 'list' | 'tree';
  count: number;
  treeifyThreshold: number;
  untreeifyThreshold: number;
  minTreeifyCapacity: number;
  list: Entry[];
  tree: TreeJson | null;
}

export default function BucketVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as BucketState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const isTree = state.structure === 'tree';

  const content = isTree ? (
    <TreeView root={toTree(state.tree, highlight, lang)} />
  ) : (
    <LinkedNodes nodes={toList(state.list, highlight, lang)} />
  );

  const cells: ArrayCell[] = [
    {
      key: state.index,
      label: `[${state.index}]`,
      highlighted: highlight.has(`bucket:${state.index}`),
      content,
    },
  ];

  return (
    <div style={wrapStyle}>
      <div style={statsStyle}>
        <Stat
          label={tl(LABELS.structure, lang)}
          value={isTree ? tl(LABELS.tree, lang) : tl(LABELS.list, lang)}
          highlight={isTree}
        />
        <Stat label={tl(LABELS.count, lang)} value={state.count} />
        <Stat label={tl(LABELS.capacity, lang)} value={state.capacity} />
        <Stat label={tl(LABELS.treeifyAt, lang)} value={state.treeifyThreshold} />
        <Stat label={tl(LABELS.untreeifyAt, lang)} value={state.untreeifyThreshold} />
      </div>
      <ArrayGrid cells={cells} />
    </div>
  );
}

function toList(entries: Entry[], highlight: Set<string>, lang: string): LinkedNode[] {
  return entries.map((n) => ({
    id: n.key,
    title: `${n.key} → ${n.value}`,
    subtitle: `${tl(LABELS.hash, lang)} ${n.hash}`,
    highlighted: highlight.has(`node:${n.key}`),
  }));
}

function toTree(node: TreeJson | null, highlight: Set<string>, lang: string): TreeNode | null {
  if (!node) return null;
  return {
    id: node.key,
    title: `${node.key} → ${node.value}`,
    subtitle: `${tl(LABELS.hash, lang)} ${node.hash}`,
    color: node.color,
    highlighted: highlight.has(`node:${node.key}`),
    left: toTree(node.left ?? null, highlight, lang),
    right: toTree(node.right ?? null, highlight, lang),
  };
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
const statsStyle: CSSProperties = { display: 'flex', gap: 16, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 16, fontWeight: 700, fontFamily: 'monospace' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
