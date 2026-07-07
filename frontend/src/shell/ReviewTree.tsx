import { useState } from 'react';
import { buildCatalog, stars, type CatalogCategory } from '@app/catalog';
import { useReview } from '@app/engine/reviewStore';
import { useStore } from '@app/engine/store';
import { tl, ui, useLang } from '@app/i18n';

/**
 * Left-hand tree of the review screen: the same category grouping as the home
 * catalog, but restricted to the topics that currently have practice exercises
 * in the review pool. Each topic shows how many exercises are still in the list
 * (pending / total) and carries a checkbox — unchecking it removes the topic's
 * pending exercises from the review list, re-checking restores the same ones.
 * The ↺ button returns the topic's already-answered exercises to the list.
 */
export function ReviewTree() {
  const lang = useLang((s) => s.lang);
  const topics = useStore((s) => s.topics);
  const manualQuestions = useStore((s) => s.manualQuestions);
  const poolTopics = useReview((s) => s.topics);
  const toggleTopic = useReview((s) => s.toggleTopic);
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});

  const restartTopic = useReview((s) => s.restartTopic);
  const poolById = new Map(poolTopics.map((t) => [t.topicId, t]));

  // Group pooled topics under their catalog categories, dropping empty ones.
  const placed = new Set<string>();
  const cats: CatalogCategory[] = buildCatalog(topics, manualQuestions)
    .map((cat) => ({
      ...cat,
      entries: cat.entries
        .filter((e) => {
          if (!e.topicId || !poolById.has(e.topicId)) return false;
          placed.add(e.topicId);
          return true;
        })
        .sort((a, b) => a.difficulty - b.difficulty),
    }))
    .filter((cat) => cat.entries.length > 0);

  // Pooled topics not found in the catalog (e.g. a topic without a category)
  // still need a toggle, so collect them under a catch-all category.
  const orphans = poolTopics.filter((t) => !placed.has(t.topicId));
  if (orphans.length > 0) {
    cats.push({
      id: '__review_other__',
      name: ui('reviewOther', lang),
      entries: orphans.map((t) => ({ id: `topic-${t.topicId}`, question: t.title, difficulty: 2, topicId: t.topicId })),
    });
  }

  if (poolTopics.length === 0) return null;

  return (
    <div className="tree">
      {cats.map((cat) => {
        const open = !collapsed[cat.id];
        return (
          <div key={cat.id} className="tree-cat">
            <button
              className="tree-cat-head"
              onClick={() => setCollapsed((c) => ({ ...c, [cat.id]: !c[cat.id] }))}
            >
              <span className="tree-caret">{open ? '▾' : '▸'}</span>
              <span className="tree-cat-name">{cat.name}</span>
              <span className="tree-cat-count">{cat.entries.length}</span>
            </button>
            {open && (
              <div className="tree-entries">
                {cat.entries.map((e) => {
                  const pt = poolById.get(e.topicId!)!;
                  return (
                    <div key={e.id} className="tree-entry review-tree-entry">
                      <label className="review-tree-label" title={tl(e.question, lang)}>
                        <input
                          type="checkbox"
                          className="review-tree-check"
                          checked={pt.enabled}
                          onChange={(ev) => void toggleTopic(pt.topicId, ev.target.checked)}
                        />
                        <span className="tree-stars" data-d={e.difficulty}>
                          {stars(e.difficulty)}
                        </span>
                        <span className="tree-q">{tl(e.question, lang)}</span>
                      </label>
                      <span className="review-tree-count" title={ui('reviewTopicCount', lang)}>
                        {pt.pending}/{pt.total}
                      </span>
                      <button
                        className="review-tree-restart"
                        title={ui('reviewRestartTopic', lang)}
                        disabled={pt.pending >= pt.total}
                        onClick={() => void restartTopic(pt.topicId)}
                      >
                        ↺
                      </button>
                    </div>
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
