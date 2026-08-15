import { create } from 'zustand';

/**
 * Content languages for AI generation (topics, lessons, theory versions),
 * chosen in the settings gear and persisted like the language/domain choice.
 * At least one language is always selected: toggling the last one off is a
 * no-op, so the persisted state can never become empty.
 */
interface GenLangState {
  en: boolean;
  ru: boolean;
  toggle: (lang: 'en' | 'ru') => void;
}

function load(): { en: boolean; ru: boolean } {
  try {
    const raw = localStorage.getItem('genLangs');
    if (raw) {
      const parsed = JSON.parse(raw) as { en?: boolean; ru?: boolean };
      const en = parsed.en !== false;
      const ru = parsed.ru !== false;
      if (en || ru) return { en, ru };
    }
  } catch {
    /* fall through to the default */
  }
  return { en: true, ru: true };
}

export const useGenLangs = create<GenLangState>((set, get) => ({
  ...load(),
  toggle: (lang) => {
    const next = { en: get().en, ru: get().ru, [lang]: !get()[lang] };
    if (!next.en && !next.ru) return; // never allow zero languages
    try {
      localStorage.setItem('genLangs', JSON.stringify({ en: next.en, ru: next.ru }));
    } catch {
      /* ignore */
    }
    set(next);
  },
}));

/** The selected languages as the list generation requests send to the backend. */
export function genLanguages(): string[] {
  const s = useGenLangs.getState();
  const result: string[] = [];
  if (s.en) result.push('en');
  if (s.ru) result.push('ru');
  return result;
}
