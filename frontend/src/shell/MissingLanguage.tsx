import { langName, type Lang } from '@app/languages';
import { fmtUi, ui, useLang } from '@app/i18n';

interface Props {
  /** Languages the content does have, offered as "show in …" buttons. */
  available: Lang[];
  /** Omit to offer no generation (lessons and boss questions cannot translate in place). */
  onGenerate?: () => void;
  generating?: boolean;
}

/**
 * Shown instead of body content the topic does not carry in the current
 * language. The app deliberately does not fall back to another language here:
 * silently showing Russian under an English interface reads as a bug, and hides
 * the fact that the translation can be generated.
 */
export function MissingLanguage({ available, onGenerate, generating }: Props) {
  const lang = useLang((s) => s.lang);
  const setLang = useLang((s) => s.setLang);
  return (
    <div className="missing-lang">
      <p>{fmtUi('noContentInLang', lang, langName(lang))}</p>
      <div className="missing-lang-actions">
        {available
          .filter((code) => code !== lang)
          .map((code) => (
            <button key={code} onClick={() => setLang(code)}>
              {fmtUi('showInLang', lang, langName(code))}
            </button>
          ))}
        {onGenerate && (
          <button className="accent" onClick={onGenerate} disabled={generating}>
            {generating
              ? ui('generatingInLang', lang)
              : fmtUi('generateInLang', lang, langName(lang))}
          </button>
        )}
      </div>
    </div>
  );
}
