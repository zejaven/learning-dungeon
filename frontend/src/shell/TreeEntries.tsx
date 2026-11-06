import type { Ref } from 'react';
import { stars, type CatalogEntry } from '@app/catalog';
import { useStore } from '@app/engine/store';
import { tl, ui, useLang } from '@app/i18n';

/**
 * A list of question rows: stars, the question and a 📘 (or ✅ when the topic is
 * fully completed). Shared by the question tree and the roadmap's module panel.
 */
export function TreeEntries({
  entries,
  selectedId,
  selectedRef,
  onSelect,
}: {
  entries: CatalogEntry[];
  selectedId?: string | null;
  selectedRef?: Ref<HTMLButtonElement>;
  onSelect: (entry: CatalogEntry) => void;
}) {
  const topics = useStore((s) => s.topics);
  const lang = useLang((s) => s.lang);
  const existing = new Set(topics.map((t) => t.id));
  const completed = new Set(topics.filter((t) => t.completed).map((t) => t.id));

  return (
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
            <span className="tree-q">
              {e.optional && (
                <span className="tree-optional" title={ui('treeOptional', lang)}>
                  ◇{' '}
                </span>
              )}
              {tl(e.question, lang)}
            </span>
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
  );
}
