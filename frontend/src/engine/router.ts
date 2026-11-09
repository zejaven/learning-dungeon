import { useEffect, useState } from 'react';

/**
 * Minimal hash-based router. The URL hash is the single source of truth for
 * navigation, so a refresh restores the selected question and screen, exiting
 * practice returns to that question, and (later) cross-links are just routes.
 *
 * Routes:
 *   #/                  landing: home with the roadmap in the detail panel (where the domain has one)
 *   #/roadmap/<module>  the same, with that module opened in the question tree
 *   #/tree              home, nothing selected (on a phone: the tree instead of the map)
 *   #/q/<id>            home, question <id> selected (lesson when one exists, else theory)
 *   #/q/<id>/theory     home, question <id> selected, reference theory forced open
 *   #/q/<id>/practice   workspace (practice) for that question's topic
 *   #/review            global review over answered practice exercises
 *
 * <id> is a catalog entry id (e.g. `java-collections-7`) or a topic id
 * (e.g. `inbox-pattern`); resolution to a catalog entry happens in catalog.ts.
 */

export type View = 'home' | 'workspace' | 'review';

export interface Route {
  view: View;
  id: string | null;
  /** 'theory' forces the reference explanation over the micro-actions lesson. */
  sub: 'theory' | null;
  /** A roadmap route (`#/`, `#/roadmap/...`); on a phone it shows the map rather than the tree. */
  map: boolean;
  /**
   * The roadmap module picked on the map, opened in the tree. Not kept in `id`: App selects a topic
   * from `id`, and a module is not a question.
   */
  moduleId: string | null;
}

export function parseHash(hash: string): Route {
  const parts = hash.replace(/^#/, '').split('/').filter(Boolean); // ['q','<id>','practice']
  const none = { id: null, sub: null, map: false, moduleId: null };
  if (parts[0] === 'review') {
    return { ...none, view: 'review' };
  }
  if (parts[0] === 'tree') {
    return { ...none, view: 'home' };
  }
  if (parts[0] === 'roadmap') {
    return { ...none, view: 'home', map: true, moduleId: parts[1] ? decodeURIComponent(parts[1]) : null };
  }
  if (parts[0] === 'q' && parts[1]) {
    const id = decodeURIComponent(parts[1]);
    if (parts[2] === 'practice') return { ...none, view: 'workspace', id };
    return { ...none, view: 'home', id, sub: parts[2] === 'theory' ? 'theory' : null };
  }
  return { ...none, view: 'home', map: true };
}

export function routeForQuestion(id: string): string {
  return `/q/${encodeURIComponent(id)}`;
}

export function routeForTheory(id: string): string {
  return `/q/${encodeURIComponent(id)}/theory`;
}

export function routeForPractice(id: string): string {
  return `/q/${encodeURIComponent(id)}/practice`;
}

export function routeForReview(): string {
  return '/review';
}

export function routeForRoadmap(moduleId?: string): string {
  return moduleId ? `/roadmap/${encodeURIComponent(moduleId)}` : '/';
}

export function routeForTree(): string {
  return '/tree';
}

/** Navigates by setting the hash; a no-op if already there (avoids history spam). */
export function navigate(path: string): void {
  const clean = path.startsWith('#') ? path.slice(1) : path;
  if (`#${clean}` === window.location.hash) return;
  window.location.hash = clean;
}

/** Subscribes a component to the current parsed route. */
export function useRoute(): Route {
  const [route, setRoute] = useState<Route>(() => parseHash(window.location.hash));
  useEffect(() => {
    const onChange = () => setRoute(parseHash(window.location.hash));
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return route;
}
