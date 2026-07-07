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
  askAboutSelection: { en: 'Ask AI about the selected text', ru: 'Спросить ИИ про выделенный текст' },
  addTopic: { en: '＋ Add topic', ru: '＋ Добавить тему' },
  toggleTheme: { en: 'Toggle light/dark theme', ru: 'Переключить светлую/тёмную тему' },
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
  askNew: { en: '＋ New question', ru: '＋ Новый вопрос' },
  newQuestion: { en: 'New question', ru: 'Новый вопрос' },
  deleteQa: { en: '🗑 Delete', ru: '🗑 Удалить' },
  noAskHistory: { en: 'No questions yet — ask one above.', ru: 'Пока нет вопросов — задайте выше.' },
  addTopicTitle: { en: 'Add a new topic', ru: 'Добавить новую тему' },
  addTopicDesc: {
    en: 'Paste an interview question. The selected AI will generate a full bilingual topic plugin (explanation, examples, visualizer, missions) under topics/.',
    ru: 'Вставьте вопрос с собеседования. Выбранная нейросеть сгенерирует полноценную двуязычную тему (объяснение, примеры, визуализатор, миссии) в папке topics/.',
  },
  addTopicPlaceholder: {
    en: 'e.g. What is the difference between ArrayList and LinkedList?',
    ru: 'например: В чём разница между ArrayList и LinkedList?',
  },
  generate: { en: 'Generate topic', ru: 'Сгенерировать тему' },
  generating: { en: 'Generating…', ru: 'Генерация…' },
  reloadNew: { en: 'Reload to open new topic', ru: 'Перезагрузить, чтобы открыть тему' },
  genFinished: { en: '— topic generation finished —', ru: '— генерация темы завершена —' },
  bossFight: { en: '⚔️ Boss Fight', ru: '⚔️ Битва с боссом' },
  bossFightTitle: { en: 'Boss Fight — ', ru: 'Битва с боссом — ' },
  bossFightAnswerPlaceholder: {
    en: 'Type your answer as if you were in the interview…',
    ru: 'Напишите ответ так, будто вы на собеседовании…',
  },
  submitAnswer: { en: 'Submit answer (Ctrl+Enter)', ru: 'Отправить ответ (Ctrl+Enter)' },
  evaluating: { en: 'Evaluating…', ru: 'Оцениваю…' },
  reEvaluate: { en: 'Try again', ru: 'Ответить заново' },
  examinerVerdict: { en: 'Examiner verdict', ru: 'Вердикт экзаменатора' },
  score: { en: 'Score', ru: 'Оценка' },
  notScored: { en: 'not answered yet', ru: 'ещё нет ответа' },
  passHint: {
    en: 'Score 6 or higher to unlock the next question.',
    ru: 'Наберите 6 или выше, чтобы открыть следующий вопрос.',
  },
  passed: { en: 'Passed', ru: 'Зачтено' },
  needMore: { en: 'Needs work — think again', ru: 'Недостаточно — подумайте ещё' },
  evaluateFailed: {
    en: "Couldn't grade your answer — the examiner returned no score. Please try again.",
    ru: 'Не удалось оценить ответ — экзаменатор не вернул оценку. Попробуйте ещё раз.',
  },
  topicCompleted: { en: '✅ Completed', ru: '✅ Пройдено' },
  catalogTitle: { en: 'Questions', ru: 'Вопросы' },
  theory: { en: 'Theory', ru: 'Теория' },
  files: { en: 'Files', ru: 'Файлы' },
  selectQuestion: {
    en: 'Pick a question from the tree on the left.',
    ru: 'Выберите вопрос в дереве слева.',
  },
  noTheoryYet: {
    en: 'No theory for this question yet.',
    ru: 'Для этого вопроса пока нет теории.',
  },
  generateTheory: { en: '✨ Generate theory', ru: '✨ Сгенерировать теорию' },
  goToPractice: { en: 'Go to practice →', ru: 'Перейти к практике →' },
  backToCatalog: { en: '← Catalog', ru: '← Каталог' },
  openingTheory: { en: 'Opening…', ru: 'Открываю…' },
  congratsTitle: { en: '🎉 Topic completed!', ru: '🎉 Тема пройдена!' },
  congratsBody: {
    en: 'You passed every boss-fight question for this topic. Well done!',
    ru: 'Вы зачли все вопросы Битвы с боссом по этой теме. Отличная работа!',
  },
  celebrateClose: { en: 'Nice!', ru: 'Класс!' },
  usageSession: { en: 'Session', ru: 'Сессия' },
  usageWeekly: { en: 'Week', ru: 'Неделя' },
  usageResets: { en: 'resets in', ru: 'сброс через' },
  aiProvider: { en: 'AI provider', ru: 'Нейросеть' },
  aiProviderShort: { en: 'AI', ru: 'ИИ' },
  missing: { en: 'missing', ru: 'нет' },
  installCli: { en: 'Install CLI', ru: 'Скачать CLI' },
  analyze: { en: '🔎 Analyze', ru: '🔎 Проанализировать' },
  analyzing: { en: 'Analyzing…', ru: 'Анализ…' },
  classDiagram: { en: 'Class diagram', ru: 'Диаграмма классов' },
  analyzeHint: {
    en: 'Create your classes, then click Analyze to see the class diagram.',
    ru: 'Создайте классы, затем нажмите «Проанализировать», чтобы увидеть диаграмму классов.',
  },
  noActiveFile: { en: 'Select or create a file on the left.', ru: 'Выберите или создайте файл слева.' },
  newFile: { en: 'New file', ru: 'Новый файл' },
  newFileName: { en: 'NewClass.java', ru: 'NewClass.java' },
  noFiles: { en: 'No files yet — create one above.', ru: 'Файлов пока нет — создайте выше.' },
  rename: { en: 'Rename', ru: 'Переименовать' },
  deleteFile: { en: 'Delete', ru: 'Удалить' },
  runQuery: { en: '▶ Run query', ru: '▶ Выполнить запрос' },
  schema: { en: 'Schema', ru: 'Схема' },
  runQueryHint: {
    en: 'Write a query and click Run to see the result.',
    ru: 'Напишите запрос и нажмите «Выполнить», чтобы увидеть результат.',
  },
  noResult: { en: 'Query ran — no rows.', ru: 'Запрос выполнен — строк нет.' },
  rows: { en: 'row(s)', ru: 'строк(и)' },
  runTests: { en: '▶ Run tests', ru: '▶ Запустить тесты' },
  testsHint: {
    en: 'Implement the method and click Run tests.',
    ru: 'Реализуйте метод и нажмите «Запустить тесты».',
  },
  expected: { en: 'expected', ru: 'ожидалось' },
  actual: { en: 'got', ru: 'получено' },
  style: { en: 'Style', ru: 'Стиль' },
  styleCustom: { en: '✏️ Custom…', ru: '✏️ Свой…' },
  stylePlaceholder: {
    en: 'describe a theme, e.g. cooking, Star Wars, chess…',
    ru: 'опиши тему, напр. кулинария, Star Wars, шахматы…',
  },
  styleName: { en: 'Save as…', ru: 'Название…' },
  save: { en: 'Save', ru: 'Сохранить' },
  version: { en: 'Version', ru: 'Версия' },
  generateVersion: { en: '✨ New version', ru: '✨ Новая версия' },
  addQuestion: { en: 'Add question', ru: 'Добавить вопрос' },
  addQuestionTitle: { en: 'Add a question', ru: 'Добавить вопрос' },
  addQuestionDesc: {
    en: 'Type an interview question. The AI picks its category (or creates a new one) and difficulty.',
    ru: 'Введите вопрос с собеседования. ИИ выберет категорию (или создаст новую) и сложность.',
  },
  addQuestionPlaceholder: {
    en: 'e.g. What is the difference between a process and a thread?',
    ru: 'например: В чём разница между процессом и потоком?',
  },
  addQuestionConfirm: { en: 'Add', ru: 'Добавить' },
  classifying: { en: 'Classifying…', ru: 'Классификация…' },
  // --- "Learn by micro-actions" lesson mode ---
  lesson: { en: 'Lesson', ru: 'Урок' },
  reference: { en: '📖 Reference', ru: '📖 Справочник' },
  backToLesson: { en: '← Back to lesson', ru: '← К уроку' },
  generateLesson: { en: '✨ Generate lesson', ru: '✨ Сгенерировать урок' },
  regenerateLesson: { en: '↻ Regenerate lesson', ru: '↻ Перегенерировать урок' },
  regenerateLessonWarning: {
    en: 'Regenerating replaces all exercises and resets lesson progress (Boss Fight progress is kept). Continue?',
    ru: 'Перегенерация заменит все упражнения и сбросит прогресс урока (прогресс Битвы с боссом сохранится). Продолжить?',
  },
  regenerateTitle: { en: 'Regenerate lesson', ru: 'Перегенерировать урок' },
  regenerateFullMode: {
    en: 'Full regeneration (otherwise: augment the existing lesson)',
    ru: 'Полная перегенерация (иначе — дополнить текущий урок)',
  },
  regenerateFullWarning: {
    en: 'Full regeneration replaces all exercises and resets lesson progress (Boss Fight progress is kept).',
    ru: 'Полная перегенерация заменит все упражнения и сбросит прогресс урока (прогресс Битвы с боссом сохранится).',
  },
  regenerateAugmentHint: {
    en: 'Augment keeps the current lesson and adds new atoms/exercises to it.',
    ru: 'Дополнение сохраняет текущий урок и добавляет к нему новые атомы и упражнения.',
  },
  regenerateCommentAugmentLabel: { en: 'What to add (required)', ru: 'Чем дополнить (обязательно)' },
  regenerateCommentFullLabel: {
    en: 'What must be in the atoms (optional)',
    ru: 'Что точно должно быть в атомах (необязательно)',
  },
  regenerateCommentFullHint: {
    en: 'This is an extra requirement — things the lesson must cover — not the basis for the whole lesson.',
    ru: 'Это дополнительное требование — что урок обязан покрыть, — а не основа всего урока.',
  },
  regenerateCommentAugmentPlaceholder: {
    en: 'e.g. add a section on collision handling and load factor',
    ru: 'напр. добавить раздел про коллизии и коэффициент загрузки',
  },
  regenerateCommentFullPlaceholder: {
    en: 'e.g. must include a comparison with TreeMap',
    ru: 'напр. обязательно включить сравнение с TreeMap',
  },
  regenerateConfirm: { en: 'Regenerate', ru: 'Перегенерировать' },
  generatingLesson: { en: 'Generating lesson…', ru: 'Генерация урока…' },
  lessonGenHint: {
    en: 'Turn this theory into a micro-actions lesson: short exercises instead of a wall of text.',
    ru: 'Превратите эту теорию в урок из микродействий: короткие упражнения вместо стены текста.',
  },
  check: { en: 'Check', ru: 'Проверить' },
  continueBtn: { en: 'Continue', ru: 'Продолжить' },
  backBtn: { en: '← Back', ru: '← Назад' },
  correct: { en: 'Correct!', ru: 'Верно!' },
  incorrect: { en: 'Not quite', ru: 'Не совсем' },
  unitLocked: { en: 'Complete the previous steps first', ru: 'Сначала пройдите предыдущие шаги' },
  discoveryPhase: { en: 'Discovery', ru: 'Открытие' },
  practicePhase: { en: 'Practice', ru: 'Практика' },
  capstonePhase: { en: 'Synthesis', ru: 'Синтез' },
  bossPhase: { en: 'Boss Fight', ru: 'Битва с боссом' },
  mistakesTitle: { en: 'Fix your mistakes', ru: 'Работа над ошибками' },
  mistakesLeft: { en: 'left', ru: 'осталось' },
  noMistakes: { en: '✅ No mistakes — nicely done!', ru: '✅ Ошибок нет — отлично!' },
  lessonCompleted: { en: '🏆 Lesson completed!', ru: '🏆 Урок пройден!' },
  typeAnswerPlaceholder: { en: 'Type your answer…', ru: 'Введите ответ…' },
  wordBankHint: { en: 'Tap the words in the right order.', ru: 'Нажимайте слова в правильном порядке.' },
  sortStepsHint: { en: 'Drag the steps into order.', ru: 'Перетащите шаги, чтобы расставить их по порядку.' },
  matchPairsHint: { en: 'Match each item on the left with one on the right.', ru: 'Соотнесите элементы слева с элементами справа.' },
  trueLabel: { en: 'True', ru: 'Верно' },
  falseLabel: { en: 'False', ru: 'Неверно' },
  yourAnswer: { en: 'Your answer', ru: 'Ваш ответ' },
  // --- Global review ---
  review: { en: '🔁 Review', ru: '🔁 Повторение' },
  reviewTitle: { en: 'Review', ru: 'Повторение' },
  reviewEmpty: {
    en: 'Nothing to review yet — fully complete a lesson (including its Boss Fight) to add its practice exercises here.',
    ru: 'Пока нечего повторять — полностью пройдите урок (включая Битву с боссом), чтобы его упражнения появились здесь.',
  },
  reviewFinished: { en: '🎉 Review finished — everything answered correctly!', ru: '🎉 Повторение завершено — всё решено верно!' },
  reviewStartAgain: { en: 'Start again', ru: 'Начать заново' },
  reviewProgress: { en: 'answered', ru: 'отвечено' },
  reviewRemaining: { en: 'left', ru: 'осталось' },
  reviewTopicsTitle: { en: 'Topics in review', ru: 'Топики для повторения' },
  reviewOther: { en: 'Other', ru: 'Другое' },
  reviewTopicCount: { en: 'in the list / total', ru: 'в списке / всего' },
  reviewRestartAll: { en: 'Start over — return every answered exercise', ru: 'Начать заново — вернуть все отвеченные вопросы' },
  reviewRestartTopic: { en: "Return this topic's answered exercises", ru: 'Вернуть отвеченные вопросы этой темы' },
  reviewAllOff: { en: 'All topics are off — enable one on the left to review.', ru: 'Все темы выключены — включите тему слева, чтобы повторять.' },
  backHome: { en: '← Home', ru: '← На главную' },
};

export function ui(key: keyof typeof UI | string, lang: Lang): string {
  const entry = UI[key];
  return entry ? entry[lang] : key;
}

export function stepLabel(lang: Lang, current: number, total: number): string {
  return lang === 'ru' ? `Шаг ${current} / ${total}` : `Step ${current} / ${total}`;
}

export function questionLabel(lang: Lang, current: number, total: number): string {
  return lang === 'ru' ? `Вопрос ${current} / ${total}` : `Question ${current} / ${total}`;
}

export function statusLabel(lang: Lang, status: string): string {
  const map: Record<string, Localized> = {
    running: { en: 'running', ru: 'выполняется' },
    done: { en: 'done', ru: 'готово' },
    error: { en: 'error', ru: 'ошибка' },
  };
  return map[status] ? map[status][lang] : status;
}
