import type { CSSProperties } from 'react';
import { ui, useLang } from '@app/i18n';

export interface Box {
  id: string;
  title: string;
  subtitle?: string;
  highlighted?: boolean;
  /** Render the box as faded/struck-through, e.g. for garbage. */
  dim?: boolean;
}

/**
 * A wrapping set of unconnected box tiles. Unlike LinkedNodes there are no
 * arrows between boxes — use this for a collection of independent items such as
 * objects sitting in a memory region, cells in a pool, etc.
 */
export function BoxGroup({ boxes }: { boxes: Box[] }) {
  const lang = useLang((s) => s.lang);
  if (boxes.length === 0) {
    return <span style={emptyStyle}>{ui('empty', lang)}</span>;
  }
  return (
    <div style={rowStyle}>
      {boxes.map((box) => (
        <div
          key={box.id}
          style={{
            ...boxStyle,
            ...(box.highlighted ? boxHighlightStyle : {}),
            ...(box.dim ? boxDimStyle : {}),
          }}
        >
          <div style={{ ...titleStyle, textDecoration: box.dim ? 'line-through' : 'none' }}>
            {box.title}
          </div>
          {box.subtitle && <div style={subtitleStyle}>{box.subtitle}</div>}
        </div>
      ))}
    </div>
  );
}

const rowStyle: CSSProperties = { display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 6 };
const boxStyle: CSSProperties = {
  border: '1px solid #5b6b7a',
  borderRadius: 6,
  padding: '4px 8px',
  background: '#1e2a36',
  minWidth: 48,
  textAlign: 'center',
};
const boxHighlightStyle: CSSProperties = {
  borderColor: '#ffcc66',
  background: '#3a3320',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.35)',
};
const boxDimStyle: CSSProperties = { opacity: 0.4, borderStyle: 'dashed' };
const titleStyle: CSSProperties = { fontWeight: 600, fontSize: 13 };
const subtitleStyle: CSSProperties = { fontSize: 11, opacity: 0.7 };
const emptyStyle: CSSProperties = { fontSize: 12, opacity: 0.4, fontStyle: 'italic' };
