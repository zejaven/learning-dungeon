import type { CSSProperties } from 'react';

export interface TreeNode {
  id: string;
  title: string;
  subtitle?: string;
  /** Red-black tint: 'R' = red node, 'B' = black node. */
  color?: 'R' | 'B';
  highlighted?: boolean;
  left?: TreeNode | null;
  right?: TreeNode | null;
}

/**
 * A top-down binary tree. Each node renders a box with its children laid out in
 * a row beneath it, joined by short connector stems. Generic and data-driven —
 * used here to show a treeified HashMap bucket (a red-black tree), but it works
 * for any binary tree.
 */
export function TreeView({ root }: { root: TreeNode | null }) {
  if (!root) {
    return null;
  }
  return (
    <div style={rootWrapStyle}>
      <Subtree node={root} />
    </div>
  );
}

function Subtree({ node }: { node: TreeNode }) {
  const hasChildren = Boolean(node.left || node.right);
  const palette = node.color === 'R' ? redStyle : blackStyle;
  return (
    <div style={subtreeStyle}>
      <div
        style={{
          ...boxStyle,
          ...palette,
          ...(node.highlighted ? boxHighlightStyle : {}),
        }}
      >
        <div style={titleStyle}>{node.title}</div>
        {node.subtitle && <div style={subtitleStyle}>{node.subtitle}</div>}
      </div>
      {hasChildren && (
        <>
          <div style={stemStyle} />
          <div style={childrenRowStyle}>
            <ChildSlot node={node.left} />
            <ChildSlot node={node.right} />
          </div>
        </>
      )}
    </div>
  );
}

function ChildSlot({ node }: { node?: TreeNode | null }) {
  if (!node) {
    return <div style={emptySlotStyle} />;
  }
  return <Subtree node={node} />;
}

const rootWrapStyle: CSSProperties = { display: 'flex', justifyContent: 'center', padding: '4px 0' };
const subtreeStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  gap: 0,
};
const boxStyle: CSSProperties = {
  border: '1.5px solid var(--border)',
  borderRadius: 6,
  padding: '3px 7px',
  minWidth: 48,
  textAlign: 'center',
};
const blackStyle: CSSProperties = {
  background: '#2b2b33',
  color: '#f2f2f2',
  borderColor: '#55555f',
};
const redStyle: CSSProperties = {
  background: '#b23b3b',
  color: '#fff5f5',
  borderColor: '#d9534f',
};
const boxHighlightStyle: CSSProperties = {
  boxShadow: '0 0 0 2px rgba(255,204,102,0.7)',
  borderColor: 'var(--accent)',
};
const stemStyle: CSSProperties = { width: 1, height: 10, background: 'var(--border)' };
const childrenRowStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'flex-start',
  gap: 10,
  borderTop: '1px solid var(--border)',
  paddingTop: 8,
};
const emptySlotStyle: CSSProperties = { width: 48 };
const titleStyle: CSSProperties = { fontWeight: 600, fontSize: 12 };
const subtitleStyle: CSSProperties = { fontSize: 10, opacity: 0.75 };
