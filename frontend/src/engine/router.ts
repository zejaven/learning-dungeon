import { useEffect, useState } from 'react';

/**
 * Minimal hash-based router. The URL hash is the single source of truth for
 * navigation, so a refresh restores the selected question and screen, exiting
 * practice returns to that question, and (later) cross-links are just routes.
 *
 * Routes:
 *   #/                  home, nothing selected
 *   #/q/<id>            home, question <id> selected (theory)
 *   #/q/<id>/practice   workspace (practice) for that question's topic
 *
 * <id> is a catalog entry id (e.g. `java-collections-7`) or a topic id
 * (e.g. `inbox-pattern`); resolution to a catalog entry happens in catalog.ts.
 */

export type View = 'home' | 'workspace';

export interface Route {
  view: View;
  id: string | null;
}

export function parseHash(hash: string): Route {
  const parts = hash.replace(/^#/, '').split('/').filter(Boolean); // ['q','<id>','practice']
  if (parts[0] === 'q' && parts[1]) {
    const id = decodeURIComponent(parts[1]);
    return { view: parts[2] === 'practice' ? 'workspace' : 'home', id };
  }
  return { view: 'home', id: null };
}

export function routeForQuestion(id: string): string {
  return `/q/${encodeURIComponent(id)}`;
}

export function routeForPractice(id: string): string {
  return `/q/${encodeURIComponent(id)}/practice`;
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
