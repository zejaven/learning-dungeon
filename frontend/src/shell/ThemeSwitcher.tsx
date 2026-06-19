import { useTheme } from '@app/engine/themeStore';
import { ui, useLang } from '@app/i18n';

/** Dark/light theme toggle, shown in both screens' headers. */
export function ThemeSwitcher() {
  const theme = useTheme((s) => s.theme);
  const toggle = useTheme((s) => s.toggle);
  const lang = useLang((s) => s.lang);
  return (
    <button className="theme-switch" onClick={toggle} title={ui('toggleTheme', lang)}>
      {theme === 'dark' ? '☀️' : '🌙'}
    </button>
  );
}
