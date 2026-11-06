import { useState } from 'react';
import { buildAllCatalogs, compareEntries, stars, type CatalogCategory, type CatalogEntry } from '@app/catalog';
import { useDomain } from '@app/engine/domainStore';
import { topicCounts, useReview } from '@app/engine/reviewStore';
import { useStore } from '@app/engine/store';
import { tl, ui, uiAll, useLang } from '@app/i18n';
import { groupByRoadmap, pruneEmpty } from '@app/roadmap';
import { TreeGroup } from '@app/shell/TreeGroup';

/**
 * Left-hand tree of the review screen: the same grouping as the home catalog
 * (roadmap stages and modules where the domain has them, categories otherwise),
 * but restricted to the topics that currently have practice exercises in the
 * review pool.
 *
 * Each topic shows where its exercises stand on the repetition ladder — due now
 * / still waiting for their date / mastered — and carries a checkbox that takes
 * the whole topic in or out of review without touching its schedule. The ▶
 * button drills the topic ahead of schedule, which is the safe replacement for
 * the old ↺ "return this topic's answered exercises": it asks the questions
 * without moving a single repetition date.
 *
 * A module with something due starts open, or its counts would be hidden.
 */
export function ReviewTree() {
  const lang = useLang((s) => s.lang);
  const domainId = useDomain((s) => s.domainId);
  const topics = useStore((s) => s.topics);
  const manualQuestions = useStore((s) => s.manualQuestions);
  const poolTopics = useReview((s) => s.topics);
  const toggleTopic = useReview((s) => s.toggleTopic);
  const startAhead = useReview((s) => s.startAhead);
  const clock = useReview((s) => s.clock);
  const [opened, setOpened] = useState<Record<string, boolean>>({});

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

  const stages = groupByRoadmap(cats, domainId);

  const renderEntries = (entries: CatalogEntry[]) => (
    <div className="tree-entries">
      {entries.map((e) => {
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
              <span className="tree-q">
                {e.optional && (
                  <span className="tree-optional" title={ui('treeOptional', lang)}>
                    ◇{' '}
                  </span>
                )}
                {tl(e.question, lang)}
              </span>
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
  );

  const toggle = (id: string, open: boolean) => setOpened((o) => ({ ...o, [id]: !open }));

  if (stages) {
    return (
      <div className="tree">
        {pruneEmpty(stages).map((stage) => {
          const stageOpen = opened[stage.id] ?? true;
          return (
            <TreeGroup
              key={stage.id}
              variant="stage"
              name={tl(stage.name, lang)}
              count={stage.modules.reduce((sum, m) => sum + m.entries.length, 0)}
              open={stageOpen}
              onToggle={() => toggle(stage.id, stageOpen)}
            >
              {stage.modules.map((m) => {
                const hasDue = m.entries.some((e) => topicCounts(poolById.get(e.topicId!)!, clock).due > 0);
                const open = opened[m.id] ?? hasDue;
                return (
                  <TreeGroup
                    key={m.id}
                    variant="module"
                    name={tl(m.name, lang)}
                    count={m.entries.length}
                    open={open}
                    onToggle={() => toggle(m.id, open)}
                  >
                    {renderEntries(m.entries)}
                  </TreeGroup>
                );
              })}
            </TreeGroup>
          );
        })}
      </div>
    );
  }

  return (
    <div className="tree">
      {cats.map((cat) => {
        const open = opened[cat.id] ?? true;
        return (
          <TreeGroup
            key={cat.id}
            name={tl(cat.name, lang)}
            count={cat.entries.length}
            open={open}
            onToggle={() => toggle(cat.id, open)}
          >
            {renderEntries(cat.entries)}
          </TreeGroup>
        );
      })}
    </div>
  );
}
