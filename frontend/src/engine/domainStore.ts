import { create } from 'zustand';
import { DEFAULT_DOMAIN, DOMAINS } from '@app/domains';

/** The active subject area, persisted like the language choice. */
interface DomainState {
  domainId: string;
  setDomain: (domainId: string) => void;
}

const stored = typeof localStorage !== 'undefined' ? localStorage.getItem('domain') : null;

export const useDomain = create<DomainState>((set) => ({
  domainId: stored && DOMAINS.some((d) => d.id === stored) ? stored : DEFAULT_DOMAIN,
  setDomain: (domainId) => {
    try {
      localStorage.setItem('domain', domainId);
    } catch {
      /* ignore */
    }
    set({ domainId });
  },
}));
