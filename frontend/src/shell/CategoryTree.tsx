import { useEffect, useRef, useState } from 'react';
import { buildCatalog, compareEntries, type CatalogEntry } from '@app/catalog';
import { useDomain } from '@app/engine/domainStore';
import { useStore } from '@app/engine/store';
import { tl, ui, useLang } from '@app/i18n';
import { groupByRoadmap } from '@app/roadmap';
import { TreeEntries } from '@app/shell/TreeEntries';
import { TreeGroup } from '@app/shell/TreeGroup';
import { entryMatcher } from '@app/shell/treeSearch';

/**
 * Left-hand tree of interview questions. The Java domain follows its roadmap
 * (stages → modules → questions in learning order, see `roadmap.ts`); other
 * domains are categories sorted by difficulty. Generated topics are merged in
 * (linked to their question or added as new entries). Entries without a topic
 * only offer theory generation.
 *
 * The selected entry (driven by the URL) is scrolled into view, so after a
 * refresh the tree shows the current question without the user hunting for it.
 * Modules start collapsed, so the one holding the selection opens by itself: that
 * is derived rather than set from an effect, or the entry would not be rendered
 * yet when the scroll effect runs. A module picked on the roadmap map
 * (`revealModuleId`) opens the same way and is scrolled to the top.
 *
 * The search box above the tree keeps only the matching questions (see
 * `treeSearch.ts`), drops the groups left empty and shows the rest open, without
 * touching what the user had folded: clearing the query brings that back.
 */
export function CategoryTree({
  selectedId,
  revealModuleId = null,
  onSelect,
}: {
  selectedId: string | null;
  revealModuleId?: string | null;
  onSelect: (entry: CatalogEntry) => void;
}) {
  const topics = useStore((s) => s.topics);
  const manualQuestions = useStore((s) => s.manualQuestions);
  const lang = useLang((s) => s.lang);
  const domainId = useDomain((s) => s.domainId);
  const catalog = buildCatalog(topics, manualQuestions, domainId);
  const stages = groupByRoadmap(catalog, domainId);
  const [opened, setOpened] = useState<Record<string, boolean>>({});
  const [query, setQuery] = useState('');
  const matches = entryMatcher(query, topics);

  const selectedStage = stages?.find((s) => s.modules.some((m) => m.entries.some((e) => e.id === selectedId)));
  const selectedModule = selectedStage?.modules.find((m) => m.entries.some((e) => e.id === selectedId));

  // Keep the module open once the selection moves on, so it does not fold away
  // from under the question the user just picked elsewhere.
  useEffect(() => {
    if (selectedStage && selectedModule) {
      setOpened((o) => ({ ...o, [selectedStage.id]: true, [selectedModule.id]: true }));
    }
  }, [selectedStage?.id, selectedModule?.id, selectedId]);

  // A module picked on the map: open it (and its stage) and bring its head to the top.
  const treeRef = useRef<HTMLDivElement | null>(null);
  const revealStage = stages?.find((s) => s.modules.some((m) => m.id === revealModuleId));
  useEffect(() => {
    if (!revealModuleId || !revealStage) return;
    setOpened((o) => ({ ...o, [revealStage.id]: true, [revealModuleId]: true }));
    // After the open module has rendered, or its questions would push it back down.
    const frame = requestAnimationFrame(() => {
      const group = treeRef.current?.querySelector(`[data-group-id="${CSS.escape(revealModuleId)}"]`);
      group?.scrollIntoView({ block: 'start', behavior: 'smooth' });
    });
    return () => cancelAnimationFrame(frame);
  }, [revealModuleId, revealStage?.id, topics.length]);

  // Bring the selected entry into view (e.g. after a reload, once topics load).
  const selectedRef = useRef<HTMLButtonElement | null>(null);
  useEffect(() => {
    selectedRef.current?.scrollIntoView({ block: 'nearest' });
  }, [selectedId, topics]);

  // Until topics arrive every roadmap entry would look unplaced.
  if (stages && topics.length === 0) return <div className="tree" />;

  const searchBox = (
    <div className="tree-search">
      <input
        type="search"
        value={query}
        placeholder={ui('treeSearchPlaceholder', lang)}
        aria-label={ui('treeSearchPlaceholder', lang)}
        onChange={(ev) => setQuery(ev.target.value)}
        onKeyDown={(ev) => {
          if (ev.key === 'Escape') setQuery('');
        }}
      />
    </div>
  );
  const nothingFound = <div className="tree-empty">{ui('treeSearchNothing', lang)}</div>;

  const renderEntries = (entries: CatalogEntry[]) => (
    <TreeEntries entries={entries} selectedId={selectedId} selectedRef={selectedRef} onSelect={onSelect} />
  );

  const toggle = (id: string, open: boolean) => setOpened((o) => ({ ...o, [id]: !open }));

  if (stages) {
    const shown = matches
      ? stages
          .map((stage) => ({
            ...stage,
            modules: stage.modules
              .map((m) => ({ ...m, entries: m.entries.filter(matches) }))
              .filter((m) => m.entries.length > 0),
          }))
          .filter((stage) => stage.modules.length > 0)
      : stages;
    return (
      <div className="tree" ref={treeRef}>
        {searchBox}
        {shown.length === 0 && nothingFound}
        {shown.map((stage) => {
          const stageOpen = matches ? true : (opened[stage.id] ?? true);
          return (
            <TreeGroup
              key={stage.id}
              variant="stage"
              name={tl(stage.name, lang)}
              count={stage.modules.reduce((sum, m) => sum + m.entries.length, 0)}
              open={stageOpen}
              onToggle={() => !matches && toggle(stage.id, stageOpen)}
            >
              {stage.modules.map((m) => {
                const open = matches
                  ? true
                  : (opened[m.id] ?? (m.id === selectedModule?.id || m.id === revealModuleId));
                return (
                  <TreeGroup
                    key={m.id}
                    groupId={m.id}
                    variant="module"
                    name={tl(m.name, lang)}
                    count={m.entries.length}
                    open={open}
                    onToggle={() => !matches && toggle(m.id, open)}
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

  const shownCategories = catalog
    .map((cat) => ({ ...cat, entries: [...cat.entries].filter((e) => !matches || matches(e)).sort(compareEntries) }))
    .filter((cat) => !matches || cat.entries.length > 0);
  return (
    <div className="tree">
      {searchBox}
      {shownCategories.length === 0 && matches && nothingFound}
      {shownCategories.map((cat) => {
        const open = matches ? true : (opened[cat.id] ?? true);
        const entries = cat.entries;
        return (
          <TreeGroup
            key={cat.id}
            name={tl(cat.name, lang)}
            count={entries.length}
            open={open}
            onToggle={() => !matches && toggle(cat.id, open)}
          >
            {renderEntries(entries)}
          </TreeGroup>
        );
      })}
    </div>
  );
}
