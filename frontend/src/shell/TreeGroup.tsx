import type { ReactNode } from 'react';

/**
 * A collapsible row of the left-hand trees: a category, or a roadmap stage or
 * module. The caller owns the open state, since the trees decide it differently.
 */
export function TreeGroup({
  name,
  count,
  open,
  onToggle,
  variant,
  children,
}: {
  name: string;
  count: number;
  open: boolean;
  onToggle: () => void;
  variant?: 'stage' | 'module';
  children: ReactNode;
}) {
  return (
    <div className={`tree-cat${variant ? ` tree-${variant}` : ''}`}>
      <button className="tree-cat-head" onClick={onToggle}>
        <span className="tree-caret">{open ? '▾' : '▸'}</span>
        <span className="tree-cat-name">{name}</span>
        <span className="tree-cat-count">{count}</span>
      </button>
      {open && children}
    </div>
  );
}
