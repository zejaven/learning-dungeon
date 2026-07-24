import { useEffect, useRef, useState } from 'react';
import { buildCatalog, compareEntries, stars, type CatalogEntry } from '@app/catalog';
import { useDomain } from '@app/engine/domainStore';
import { useStore } from '@app/engine/store';
import { tl, useLang } from '@app/i18n';

/**
 * Left-hand tree of interview questions, grouped by category and sorted by
 * difficulty. Generated topics are merged in (linked to their question or added
 * as new entries). Entries whose topic exists show a 📘 (or ✅ when the topic is
 * fully completed); the rest only offer theory generation.
 *
 * The selected entry (driven by the URL) is scrolled into view, so after a
 * refresh the tree shows the current question without the user hunting for it.
 */
export function CategoryTree({
  selectedId,
  onSelect,
}: {
  selectedId: string | null;
  onSelect: (entry: CatalogEntry) => void;
}) {
  const topics = useStore((s) => s.topics);
  const manualQuestions = useStore((s) => s.manualQuestions);
  const lang = useLang((s) => s.lang);
  const domainId = useDomain((s) => s.domainId);
  const catalog = buildCatalog(topics, manualQuestions, domainId);
  const existing = new Set(topics.map((t) => t.id));
  const completed = new Set(topics.filter((t) => t.completed).map((t) => t.id));
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});

  // Bring the selected entry into view (e.g. after a reload, once topics load).
  const selectedRef = useRef<HTMLButtonElement | null>(null);
  useEffect(() => {
    selectedRef.current?.scrollIntoView({ block: 'nearest' });
  }, [selectedId, topics]);

  return (
    <div className="tree">
      {catalog.map((cat) => {
        const open = !collapsed[cat.id];
        const entries = [...cat.entries].sort(compareEntries);
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
                  const isSelected = selectedId === e.id;
                  return (
                    <button
                      key={e.id}
                      ref={isSelected ? selectedRef : undefined}
                      className={`tree-entry${isSelected ? ' selected' : ''}`}
                      onClick={() => onSelect(e)}
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
