import { create } from 'zustand';

export type Lang = 'en' | 'ru';
export const LANGS: Lang[] = ['en', 'ru'];

/** A string available in both supported languages (matches the backend DTO). */
export interface Localized {
  en: string;
  ru: string;
}

/** Picks the active language out of a Localized value (or passes a plain string). */
export function tl(value: Localized | string | null | undefined, lang: Lang): string {
  if (value == null) return '';
  if (typeof value === 'string') return value;
  return value[lang] || value.en || value.ru || '';
}

interface LangState {
  lang: Lang;
  setLang: (lang: Lang) => void;
}

const stored = (typeof localStorage !== 'undefined' && localStorage.getItem('lang')) as Lang | null;

export const useLang = create<LangState>((set) => ({
  lang: stored === 'en' || stored === 'ru' ? stored : 'ru',
  setLang: (lang) => {
    try {
      localStorage.setItem('lang', lang);
    } catch {
      /* ignore */
    }
    set({ lang });
  },
}));

/** Fixed UI chrome strings. The app name is intentionally not translated. */
const UI: Record<string, Localized> = {
  askAI: { en: '💬 Ask AI', ru: '💬 Спросить ИИ' },
  askAbout: { en: 'Ask AI about ', ru: 'Спросить ИИ про ' },
  addTopic: { en: '＋ Add topic', ru: '＋ Добавить тему' },
  noTopics: { en: 'No topics yet', ru: 'Пока нет тем' },
  run: { en: '▶ Run', ru: '▶ Запустить' },
  running: { en: 'running…', ru: 'выполняется…' },
  reset: { en: '↺ Reset', ru: '↺ Сброс' },
  explanation: { en: 'Explanation', ru: 'Объяснение' },
  visualization: { en: 'Visualization', ru: 'Визуализация' },
  missions: { en: 'Missions', ru: 'Миссии' },
  loading: { en: 'Loading…', ru: 'Загрузка…' },
  prev: { en: '◀ Prev', ru: '◀ Назад' },
  next: { en: 'Next ▶', ru: 'Вперёд ▶' },
  noSteps: { en: 'No steps yet', ru: 'Пока нет шагов' },
  eventLogTitle: { en: 'Event log — click a step', ru: 'Журнал событий — кликните шаг' },
  eventLogEmpty: {
    en: 'Run the code to see what happens, step by step.',
    ru: 'Запустите код, чтобы увидеть, что происходит, шаг за шагом.',
  },
  noVisualizer: {
    en: 'No visualizer for this topic; showing the event log only.',
    ru: 'Для этой темы нет визуализатора; показан только журнал событий.',
  },
  empty: { en: 'empty', ru: 'пусто' },
  close: { en: 'Close', ru: 'Закрыть' },
  assistantPlaceholder: {
    en: 'e.g. Why does a mutable key break get()?',
    ru: 'например: Почему изменяемый ключ ломает get()?',
  },
  ask: { en: 'Ask (Ctrl+Enter)', ru: 'Спросить (Ctrl+Enter)' },
  thinking: { en: 'Thinking…', ru: 'Думаю…' },
  addTopicTitle: { en: 'Add a new topic', ru: 'Добавить новую тему' },
  addTopicDesc: {
    en: 'Paste an interview question. Claude Code will generate a full bilingual topic plugin (explanation, examples, visualizer, missions) under topics/.',
    ru: 'Вставьте вопрос с собеседования. Claude Code сгенерирует полноценную двуязычную тему (объяснение, примеры, визуализатор, миссии) в папке topics/.',
  },
  addTopicPlaceholder: {
    en: 'e.g. What is the difference between ArrayList and LinkedList?',
    ru: 'например: В чём разница между ArrayList и LinkedList?',
  },
  generate: { en: 'Generate topic', ru: 'Сгенерировать тему' },
  generating: { en: 'Generating…', ru: 'Генерация…' },
  reloadNew: { en: 'Reload to open new topic', ru: 'Перезагрузить, чтобы открыть тему' },
  genFinished: { en: '— topic generation finished —', ru: '— генерация темы завершена —' },
};

export function ui(key: keyof typeof UI | string, lang: Lang): string {
  const entry = UI[key];
  return entry ? entry[lang] : key;
}

export function stepLabel(lang: Lang, current: number, total: number): string {
  return lang === 'ru' ? `Шаг ${current} / ${total}` : `Step ${current} / ${total}`;
}

export function statusLabel(lang: Lang, status: string): string {
  const map: Record<string, Localized> = {
    running: { en: 'running', ru: 'выполняется' },
    done: { en: 'done', ru: 'готово' },
    error: { en: 'error', ru: 'ошибка' },
  };
  return map[status] ? map[status][lang] : status;
}
