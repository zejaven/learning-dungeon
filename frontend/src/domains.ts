import type { Localized } from './i18n';

/**
 * Subject areas ("domains") the app can teach. Each topic declares its domain
 * via `domainId` in topic.yaml (absent = 'java' for backward compatibility);
 * the home screen shows one domain at a time. The static question CATALOG in
 * catalog.ts belongs to the 'java' domain; other domains build their category
 * tree purely from their topics' categoryId/categoryName.
 */
export interface Domain {
  id: string;
  /** App title shown in the header while this domain is active. */
  title: Localized;
  icon: string;
}

export const DOMAINS: Domain[] = [
  {
    id: 'java',
    title: { en: 'Java Interview Dungeon', ru: 'Java Interview Dungeon' },
    icon: '🗡️',
  },
  {
    id: 'ndm',
    title: { en: 'Network Design & Management', ru: 'Проектирование и управление сетями' },
    icon: '🌐',
  },
  {
    id: 'qa',
    title: { en: 'QA Interview Prep', ru: 'Подготовка к собеседованию QA' },
    icon: '🐛',
  },
];

export const DEFAULT_DOMAIN = 'java';

export function domainById(id: string): Domain {
  return DOMAINS.find((d) => d.id === id) ?? DOMAINS[0];
}

/** Domain of a topic; legacy topics without the field belong to 'java'. */
export function domainOf(topic: { domainId?: string }): string {
  return topic.domainId || DEFAULT_DOMAIN;
}
