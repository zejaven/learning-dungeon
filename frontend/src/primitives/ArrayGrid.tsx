import type { CSSProperties, ReactNode } from 'react';

export interface ArrayCell {
  key: string | number;
  /** Gutter label, e.g. a bucket index. */
  label?: string;
  highlighted?: boolean;
  content?: ReactNode;
}

/**
 * A vertical array of indexed cells, each with a gutter label and a content
 * slot. Used for HashMap buckets, array slots, memory blocks, etc.
 */
export function ArrayGrid({ cells }: { cells: ArrayCell[] }) {
  return (
    <div style={gridStyle}>
      {cells.map((cell) => (
        <div
          key={cell.key}
          style={{ ...rowStyle, ...(cell.highlighted ? rowHighlightStyle : {}) }}
        >
          <div style={gutterStyle}>{cell.label}</div>
          <div style={contentStyle}>{cell.content}</div>
        </div>
      ))}
    </div>
  );
}

const gridStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 4 };
const rowStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  padding: '3px 4px',
  borderRadius: 6,
  borderLeft: '3px solid transparent',
};
const rowHighlightStyle: CSSProperties = {
  background: 'rgba(255,204,102,0.10)',
  borderLeft: '3px solid var(--accent)',
};
const gutterStyle: CSSProperties = {
  width: 64,
  flexShrink: 0,
  fontSize: 12,
  fontFamily: 'monospace',
  opacity: 0.7,
  textAlign: 'right',
};
const contentStyle: CSSProperties = { flex: 1, minHeight: 28, display: 'flex', alignItems: 'center' };
