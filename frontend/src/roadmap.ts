import { compareEntries, type CatalogCategory, type CatalogEntry } from './catalog';
import { uiAll, type Localized } from './i18n';
import data from './roadmap.json';

/**
 * The Java domain's learning path: stages of modules, each an ordered list of
 * topic ids (`roadmap.json`). It is a grouping layered over the catalog, not a
 * replacement for categories: `categoryId` still drives generation prompts,
 * question classification and manual questions, so {@link groupByRoadmap} only
 * rearranges what `buildCatalog` returns.
 *
 * Entries are matched by topic id because entry ids are not stable across
 * machines (`manual-N` depends on the database). What the file does not list — a
 * freshly generated topic, a manual question without a topic — lands in a
 * trailing "not placed" stage, so nothing can fall out of the tree.
 */

const ROADMAP_DOMAIN = 'java';

export interface RoadmapStage {
  id: string;
  name: Localized;
  modules: CatalogCategory[];
}

const moduleByTopic = new Map<string, Localized>(
  data.stages.flatMap((s) => s.modules.flatMap((m) => m.topics.map((t) => [t, m.name] as const))),
);

/** The name of the module a topic is listed in, if any. */
export function moduleOfTopic(topicId: string): Localized | undefined {
  return moduleByTopic.get(topicId);
}

/** Arranges a catalog into stages and modules; `null` for a domain without a roadmap. */
export function groupByRoadmap(categories: CatalogCategory[], domainId: string): RoadmapStage[] | null {
  if (domainId !== ROADMAP_DOMAIN) return null;

  const byTopic = new Map<string, CatalogEntry>();
  const unplaced: CatalogEntry[] = [];
  for (const entry of categories.flatMap((c) => c.entries)) {
    if (entry.topicId && moduleByTopic.has(entry.topicId)) byTopic.set(entry.topicId, entry);
    else unplaced.push(entry);
  }

  const stages: RoadmapStage[] = data.stages.map((s) => ({
    id: s.id,
    name: s.name,
    modules: s.modules.map((m) => ({
      id: m.id,
      name: m.name,
      entries: m.topics.flatMap((t) => byTopic.get(t) ?? []),
    })),
  }));
  if (unplaced.length > 0) {
    stages.push({
      id: '__unplaced__',
      name: uiAll('treeUnplaced'),
      modules: [{ id: '__unplaced_module__', name: uiAll('reviewOther'), entries: unplaced.sort(compareEntries) }],
    });
  }
  return stages;
}

/** Drops modules without entries, and the stages that leaves empty. */
export function pruneEmpty(stages: RoadmapStage[]): RoadmapStage[] {
  return stages
    .map((s) => ({ ...s, modules: s.modules.filter((m) => m.entries.length > 0) }))
    .filter((s) => s.modules.length > 0);
}

/** Every entry in the order the tree renders it, with the category it belongs to. */
export function entriesInTreeOrder(
  catalog: CatalogCategory[],
  domainId: string,
): { categoryId: string; entry: CatalogEntry }[] {
  const stages = groupByRoadmap(catalog, domainId);
  if (!stages) {
    return catalog.flatMap((c) => [...c.entries].sort(compareEntries).map((entry) => ({ categoryId: c.id, entry })));
  }
  const categoryOf = new Map(catalog.flatMap((c) => c.entries.map((e) => [e.id, c.id] as const)));
  return stages
    .flatMap((s) => s.modules)
    .flatMap((m) => m.entries.map((entry) => ({ categoryId: categoryOf.get(entry.id) ?? m.id, entry })));
}
