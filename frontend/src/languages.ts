/**
 * The languages the app knows about. This is the single place to change when a
 * language is added: everything else (the UI switcher, the generation-language
 * picker, content fallbacks, version chips) derives from this list.
 *
 * `ui: true` means the chrome strings in i18n.ts are translated into it, so it
 * may be offered in the interface switcher. A language may carry content
 * without that flag — topics can be generated in it before the UI follows.
 */
export interface ContentLanguage {
  code: string;
  /** Native name, shown in the language picker. */
  name: string;
  /** Short label for chips and the switcher. */
  chip: string;
  /** Offer as an interface language (i18n.ts has its chrome strings). */
  ui: boolean;
}

export const LANGUAGES = [
  { code: 'en', name: 'English', chip: 'EN', ui: true },
  { code: 'ru', name: 'Русский', chip: 'RU', ui: true },
] as const satisfies readonly ContentLanguage[];

/** Language code. Widens automatically when LANGUAGES grows. */
export type Lang = (typeof LANGUAGES)[number]['code'];

export const LANG_CODES: Lang[] = LANGUAGES.map((l) => l.code);
export const UI_LANGS: Lang[] = LANGUAGES.filter((l) => l.ui).map((l) => l.code);

/** Interface language before the user picks one. */
export const DEFAULT_UI_LANG: Lang = 'ru';
/** Anchor for label/chrome fallback when the wanted language is missing. */
export const FALLBACK_LANG: Lang = 'en';
/**
 * What a topic without an explicit `languages:` carries. Deliberately NOT
 * "every registered language": legacy topics are bilingual, and a newly
 * registered language must not retroactively be claimed by 272 of them.
 */
export const DEFAULT_TOPIC_LANGS: Lang[] = ['en', 'ru'];

export function isLang(code: unknown): code is Lang {
  return typeof code === 'string' && LANG_CODES.includes(code as Lang);
}

export function isUiLang(code: unknown): code is Lang {
  return typeof code === 'string' && UI_LANGS.includes(code as Lang);
}

export function langName(code: string): string {
  return LANGUAGES.find((l) => l.code === code)?.name ?? code;
}

export function langChip(code: string): string {
  return LANGUAGES.find((l) => l.code === code)?.chip ?? code.toUpperCase();
}

/** Keeps a set of codes in registry order and drops unknown ones. */
export function orderLangs(codes: readonly string[]): Lang[] {
  return LANG_CODES.filter((c) => codes.includes(c));
}
