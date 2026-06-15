import { LANGS, useLang } from '@app/i18n';

/** EN/RU language dropdown, shown in both screens' headers. */
export function LangSwitcher() {
  const lang = useLang((s) => s.lang);
  const setLang = useLang((s) => s.setLang);
  return (
    <select value={lang} onChange={(e) => setLang(e.target.value as (typeof LANGS)[number])}>
      {LANGS.map((l) => (
        <option key={l} value={l}>
          {l.toUpperCase()}
        </option>
      ))}
    </select>
  );
}
