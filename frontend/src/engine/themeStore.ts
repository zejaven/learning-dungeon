import { create } from 'zustand';

export type Theme = 'dark' | 'light';

function load(): Theme {
  try {
    const t = localStorage.getItem('theme');
    return t === 'light' || t === 'dark' ? t : 'dark';
  } catch {
    return 'dark';
  }
}

/** Reflects the theme onto <html data-theme> so the CSS variables switch. */
function apply(theme: Theme): void {
  try {
    document.documentElement.dataset.theme = theme;
  } catch {
    /* SSR / no DOM */
  }
}

interface ThemeState {
  theme: Theme;
  toggle: () => void;
}

export const useTheme = create<ThemeState>((set, get) => {
  const initial = load();
  apply(initial);
  return {
    theme: initial,
    toggle: () => {
      const next: Theme = get().theme === 'dark' ? 'light' : 'dark';
      apply(next);
      try {
        localStorage.setItem('theme', next);
      } catch {
        /* ignore */
      }
      set({ theme: next });
    },
  };
});
