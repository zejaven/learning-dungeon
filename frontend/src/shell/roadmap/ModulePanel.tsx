import { useEffect } from 'react';
import type { CatalogCategory } from '@app/catalog';
import { navigate, routeForQuestion } from '@app/engine/router';
import { fmtUi, tl, ui, useLang } from '@app/i18n';
import { TreeEntries } from '@app/shell/TreeEntries';

/** The questions of one roadmap module, open beside the map. */
export function ModulePanel({
  module,
  done,
  total,
  onClose,
}: {
  module: CatalogCategory;
  done: number;
  total: number;
  onClose: () => void;
}) {
  const lang = useLang((s) => s.lang);

  useEffect(() => {
    const onKey = (ev: KeyboardEvent) => {
      if (ev.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <aside className="panel roadmap-panel">
      <div className="panel-title tree-panel-title">
        <span>{tl(module.name, lang)}</span>
        <button className="roadmap-panel-close" title={ui('mapClose', lang)} onClick={onClose}>
          ✕
        </button>
      </div>
      <div className="panel-body tree-body">
        <p className="roadmap-panel-progress">{fmtUi('mapModuleProgress', lang, done, total)}</p>
        <TreeEntries entries={module.entries} onSelect={(e) => navigate(routeForQuestion(e.id))} />
      </div>
    </aside>
  );
}
