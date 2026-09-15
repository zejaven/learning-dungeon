import { useState } from 'react';
import { buildAllCatalogs, compareEntries, stars, type CatalogCategory } from '@app/catalog';
import { topicCounts, useReview } from '@app/engine/reviewStore';
import { useStore } from '@app/engine/store';
import { tl, ui, uiAll, useLang } from '@app/i18n';

/**
 * Left-hand tree of the review screen: the same category grouping as the home
 * catalog, but restricted to the topics that currently have practice exercises
 * in the review pool.
 *
 * Each topic shows where its exercises stand on the repetition ladder — due now
 * / still waiting for their date / mastered — and carries a checkbox that takes
 * the whole topic in or out of review without touching its schedule. The ▶
 * button drills the topic ahead of schedule, which is the safe replacement for
 * the old ↺ "return this topic's answered exercises": it asks the questions
 * without moving a single repetition date.
 */
export function ReviewTree() {
  const lang = useLang((s) => s.lang);
  const topics = useStore((s) => s.topics);
  const manualQuestions = useStore((s) => s.manualQuestions);
  const poolTopics = useReview((s) => s.topics);
  const toggleTopic = useReview((s) => s.toggleTopic);
  const startAhead = useReview((s) => s.startAhead);
  const clock = useReview((s) => s.clock);
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});

  const poolById = new Map(poolTopics.map((t) => [t.topicId, t]));

  // Group pooled topics under their catalog categories (across every domain —
  // the pool itself is already scoped to the active one), dropping empty ones.
  const placed = new Set<string>();
  const cats: CatalogCategory[] = buildAllCatalogs(topics, manualQuestions)
    .map((cat) => ({
      ...cat,
      entries: cat.entries
        .filter((e) => {
          if (!e.topicId || !poolById.has(e.topicId)) return false;
          placed.add(e.topicId);
          return true;
        })
        .sort(compareEntries),
    }))
    .filter((cat) => cat.entries.length > 0);

  // Pooled topics not found in the catalog (e.g. a topic without a category)
  // still need a toggle, so collect them under a catch-all category.
  const orphans = poolTopics.filter((t) => !placed.has(t.topicId));
  if (orphans.length > 0) {
    cats.push({
      id: '__review_other__',
      name: uiAll('reviewOther'),
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
              <span className="tree-cat-name">{tl(cat.name, lang)}</span>
              <span className="tree-cat-count">{cat.entries.length}</span>
            </button>
            {open && (
              <div className="tree-entries">
                {cat.entries.map((e) => {
                  const pt = poolById.get(e.topicId!)!;
                  const counts = topicCounts(pt, clock);
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
                      <span className="review-tree-count" title={ui('reviewCounts', lang)}>
                        <b data-due={counts.due > 0 ? '1' : undefined}>{counts.due}</b>
                        {' · '}
                        {counts.waiting}
                        {' · '}
                        {counts.mastered}
                      </span>
                      <button
                        className="review-tree-restart"
                        title={ui('reviewAhead', lang)}
                        disabled={counts.waiting + counts.mastered === 0}
                        onClick={() => void startAhead(pt.topicId)}
                      >
                        ▶
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
