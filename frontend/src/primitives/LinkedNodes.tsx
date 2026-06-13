import type { CSSProperties } from 'react';
import { ui, useLang } from '@app/i18n';

export interface LinkedNode {
  id: string;
  title: string;
  subtitle?: string;
  highlighted?: boolean;
}

/**
 * A horizontal chain of node boxes joined by arrows. Used to show a bucket's
 * collision chain, a linked list, etc.
 */
export function LinkedNodes({ nodes }: { nodes: LinkedNode[] }) {
  const lang = useLang((s) => s.lang);
  if (nodes.length === 0) {
    return <span style={emptyStyle}>{ui('empty', lang)}</span>;
  }
  return (
    <div style={rowStyle}>
      {nodes.map((node, i) => (
        <div key={node.id} style={chainItemStyle}>
          <div style={{ ...boxStyle, ...(node.highlighted ? boxHighlightStyle : {}) }}>
            <div style={titleStyle}>{node.title}</div>
            {node.subtitle && <div style={subtitleStyle}>{node.subtitle}</div>}
          </div>
          {i < nodes.length - 1 && <span style={arrowStyle}>→</span>}
        </div>
      ))}
    </div>
  );
}

const rowStyle: CSSProperties = { display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 2 };
const chainItemStyle: CSSProperties = { display: 'flex', alignItems: 'center' };
const boxStyle: CSSProperties = {
  border: '1px solid #5b6b7a',
  borderRadius: 6,
  padding: '4px 8px',
  background: '#1e2a36',
  minWidth: 54,
  textAlign: 'center',
};
const boxHighlightStyle: CSSProperties = {
  borderColor: '#ffcc66',
  background: '#3a3320',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.35)',
};
const titleStyle: CSSProperties = { fontWeight: 600, fontSize: 13 };
const subtitleStyle: CSSProperties = { fontSize: 11, opacity: 0.7 };
const arrowStyle: CSSProperties = { margin: '0 4px', opacity: 0.6 };
const emptyStyle: CSSProperties = { fontSize: 12, opacity: 0.4, fontStyle: 'italic' };
