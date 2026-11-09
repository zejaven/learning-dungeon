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
  groupId,
  children,
}: {
  name: string;
  count: number;
  open: boolean;
  onToggle: () => void;
  variant?: 'stage' | 'module';
  /** Lets the tree scroll a group into view, e.g. a module picked on the map. */
  groupId?: string;
  children: ReactNode;
}) {
  return (
    <div className={`tree-cat${variant ? ` tree-${variant}` : ''}`} data-group-id={groupId}>
      <button className="tree-cat-head" onClick={onToggle}>
        <span className="tree-caret">{open ? '▾' : '▸'}</span>
        <span className="tree-cat-name">{name}</span>
        <span className="tree-cat-count">{count}</span>
      </button>
      {open && children}
    </div>
  );
}
