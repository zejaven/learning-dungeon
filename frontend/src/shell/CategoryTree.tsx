import { useState } from 'react';
import { buildCatalog, stars, type CatalogEntry } from '@app/catalog';
import { useStore } from '@app/engine/store';
import { tl, useLang } from '@app/i18n';

/**
 * Left-hand tree of interview questions, grouped by category and sorted by
 * difficulty. Generated topics are merged in (linked to their question or added
 * as new entries). Entries whose topic exists show a 📘 (or ✅ when the topic is
 * fully completed); the rest only offer theory generation.
 */
export function CategoryTree({
  selectedId,
  onSelect,
}: {
  selectedId: string | null;
  onSelect: (entry: CatalogEntry, categoryId: string) => void;
}) {
  const topics = useStore((s) => s.topics);
  const lang = useLang((s) => s.lang);
  const catalog = buildCatalog(topics);
  const existing = new Set(topics.map((t) => t.id));
  const completed = new Set(topics.filter((t) => t.completed).map((t) => t.id));
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});

  return (
    <div className="tree">
      {catalog.map((cat) => {
        const open = !collapsed[cat.id];
        const entries = [...cat.entries].sort((a, b) => a.difficulty - b.difficulty);
        return (
          <div key={cat.id} className="tree-cat">
            <button
              className="tree-cat-head"
              onClick={() => setCollapsed((c) => ({ ...c, [cat.id]: !c[cat.id] }))}
            >
              <span className="tree-caret">{open ? '▾' : '▸'}</span>
              <span className="tree-cat-name">{cat.name}</span>
              <span className="tree-cat-count">{entries.length}</span>
            </button>
            {open && (
              <div className="tree-entries">
                {entries.map((e) => {
                  const hasTheory = !!e.topicId && existing.has(e.topicId);
                  const isDone = !!e.topicId && completed.has(e.topicId);
                  return (
                    <button
                      key={e.id}
                      className={`tree-entry${selectedId === e.id ? ' selected' : ''}`}
                      onClick={() => onSelect(e, cat.id)}
                      title={tl(e.question, lang)}
                    >
                      <span className="tree-stars" data-d={e.difficulty}>
                        {stars(e.difficulty)}
                      </span>
                      <span className="tree-q">{tl(e.question, lang)}</span>
                      {isDone ? (
                        <span className="tree-flag">✅</span>
                      ) : hasTheory ? (
                        <span className="tree-flag" title="theory available">
                          📘
                        </span>
                      ) : null}
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
