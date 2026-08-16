import { create } from 'zustand';
import { LANG_CODES, orderLangs, type Lang } from '@app/languages';

/**
 * Content languages the AI should write when generating topics, lessons and
 * theory versions. It is a generation parameter (shown next to the style
 * selector), but the choice is global and persisted, so it carries across
 * topics until it is changed again.
 *
 * At least one language is always selected: unchecking the last one is a no-op,
 * so the persisted state can never become empty.
 */
interface GenLangState {
  /** Selected languages, in registry order; never empty. */
  selected: Lang[];
  toggle: (lang: Lang) => void;
  set: (langs: Lang[]) => void;
}

const KEY = 'genLangs';

function load(): Lang[] {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      // Current format: an array of codes.
      if (Array.isArray(parsed)) {
        const langs = orderLangs(parsed.filter((c): c is string => typeof c === 'string'));
        if (langs.length) return langs;
      } else if (parsed && typeof parsed === 'object') {
        // Legacy format: { en: boolean, ru: boolean }, where a missing key meant on.
        const flags = parsed as Record<string, boolean | undefined>;
        const langs = LANG_CODES.filter((c) => flags[c] !== false);
        if (langs.length) return langs;
      }
    }
  } catch {
    /* fall through to the default */
  }
  return LANG_CODES;
}

function persist(langs: Lang[]): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(langs));
  } catch {
    /* ignore */
  }
}

export const useGenLangs = create<GenLangState>((set, get) => ({
  selected: load(),
  toggle: (lang) => {
    const current = get().selected;
    const next = current.includes(lang)
      ? current.filter((c) => c !== lang)
      : orderLangs([...current, lang]);
    if (!next.length) return; // never allow zero languages
    persist(next);
    set({ selected: next });
  },
  set: (langs) => {
    const next = orderLangs(langs);
    if (!next.length) return;
    persist(next);
    set({ selected: next });
  },
}));

/** The selected languages, as generation requests send them to the backend. */
export function genLanguages(): string[] {
  return useGenLangs.getState().selected;
}
