import { useEffect, useRef, useState } from 'react';
import { buildCatalog, compareEntries, stars, type CatalogEntry } from '@app/catalog';
import { useDomain } from '@app/engine/domainStore';
import { useStore } from '@app/engine/store';
import { tl, ui, useLang } from '@app/i18n';
import { groupByRoadmap } from '@app/roadmap';
import { TreeGroup } from '@app/shell/TreeGroup';

/**
 * Left-hand tree of interview questions. The Java domain follows its roadmap
 * (stages → modules → questions in learning order, see `roadmap.ts`); other
 * domains are categories sorted by difficulty. Generated topics are merged in
 * (linked to their question or added as new entries). Entries whose topic exists
 * show a 📘 (or ✅ when the topic is fully completed); the rest only offer theory
 * generation.
 *
 * The selected entry (driven by the URL) is scrolled into view, so after a
 * refresh the tree shows the current question without the user hunting for it.
 * Modules start collapsed, so the one holding the selection opens by itself: that
 * is derived rather than set from an effect, or the entry would not be rendered
 * yet when the scroll effect runs.
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
  const stages = groupByRoadmap(catalog, domainId);
  const existing = new Set(topics.map((t) => t.id));
  const completed = new Set(topics.filter((t) => t.completed).map((t) => t.id));
  const [opened, setOpened] = useState<Record<string, boolean>>({});

  const selectedStage = stages?.find((s) => s.modules.some((m) => m.entries.some((e) => e.id === selectedId)));
  const selectedModule = selectedStage?.modules.find((m) => m.entries.some((e) => e.id === selectedId));

  // Keep the module open once the selection moves on, so it does not fold away
  // from under the question the user just picked elsewhere.
  useEffect(() => {
    if (selectedStage && selectedModule) {
      setOpened((o) => ({ ...o, [selectedStage.id]: true, [selectedModule.id]: true }));
    }
  }, [selectedStage?.id, selectedModule?.id, selectedId]);

  // Bring the selected entry into view (e.g. after a reload, once topics load).
  const selectedRef = useRef<HTMLButtonElement | null>(null);
  useEffect(() => {
    selectedRef.current?.scrollIntoView({ block: 'nearest' });
  }, [selectedId, topics]);

  // Until topics arrive every roadmap entry would look unplaced.
  if (stages && topics.length === 0) return <div className="tree" />;

  const renderEntries = (entries: CatalogEntry[]) => (
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
  );

  const toggle = (id: string, open: boolean) => setOpened((o) => ({ ...o, [id]: !open }));

  if (stages) {
    return (
      <div className="tree">
        {stages.map((stage) => {
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
                const open = opened[m.id] ?? m.id === selectedModule?.id;
                return (
                  <TreeGroup
                    key={m.id}
                    variant="module"
                    name={tl(m.name, lang)}
                    count={m.entries.length}
                    open={open}
                    onToggle={() => toggle(m.id, open)}
                  >
                    {m.entries.length > 0 ? (
                      renderEntries(m.entries)
                    ) : (
                      <div className="tree-empty">{ui('treeModuleEmpty', lang)}</div>
                    )}
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
      {catalog.map((cat) => {
        const open = opened[cat.id] ?? true;
        const entries = [...cat.entries].sort(compareEntries);
        return (
          <TreeGroup
            key={cat.id}
            name={tl(cat.name, lang)}
            count={entries.length}
            open={open}
            onToggle={() => toggle(cat.id, open)}
          >
            {renderEntries(entries)}
          </TreeGroup>
        );
      })}
    </div>
  );
}
