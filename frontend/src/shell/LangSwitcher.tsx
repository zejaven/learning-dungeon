import { UI_LANGS, langChip, langName, type Lang } from '@app/languages';
import { useLang } from '@app/i18n';

/** Interface-language dropdown, shown in both screens' headers. */
export function LangSwitcher() {
  const lang = useLang((s) => s.lang);
  const setLang = useLang((s) => s.setLang);
  return (
    <select value={lang} onChange={(e) => setLang(e.target.value as Lang)}>
      {UI_LANGS.map((l) => (
        <option key={l} value={l} title={langName(l)}>
          {langChip(l)}
        </option>
      ))}
    </select>
  );
}
