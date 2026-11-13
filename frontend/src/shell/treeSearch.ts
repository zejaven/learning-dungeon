import type { CatalogEntry } from '@app/catalog';
import type { TopicSummary } from '@app/engine/traceTypes';

/** Lower case, ё → е, so "ещё" and "еще" find each other. */
function normalize(text: string): string {
  return text.toLowerCase().replace(/ё/g, 'е');
}

/**
 * A matcher for the question tree's search box, or null for an empty query.
 * Every word of the query must occur in the entry's question (any language), its
 * topic title (any language) or its topic id, so "idor access" and "hashmap коллиз"
 * both narrow the tree rather than looking for one exact phrase.
 */
export function entryMatcher(query: string, topics: TopicSummary[]): ((e: CatalogEntry) => boolean) | null {
  const words = normalize(query).split(/\s+/).filter(Boolean);
  if (words.length === 0) return null;
  const titles = new Map(topics.map((t) => [t.id, Object.values(t.title).join(' ')]));
  return (e) => {
    const haystack = normalize(
      [...Object.values(e.question), e.topicId ?? '', e.topicId ? titles.get(e.topicId) ?? '' : ''].join(' '),
    );
    return words.every((w) => haystack.includes(w));
  };
}
