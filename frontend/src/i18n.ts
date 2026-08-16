import { create } from 'zustand';
import { DEFAULT_UI_LANG, FALLBACK_LANG, LANG_CODES, isUiLang, type Lang } from './languages';

export type { Lang };
/** Interface languages, in registry order. */
export const LANGS: Lang[] = LANG_CODES;

/**
 * A string in the languages it exists in. Keys are language codes, so a value
 * may carry one language, all of them, or any subset — `{ en, ru }` literals
 * stay valid.
 */
export type Localized = Partial<Record<Lang, string>>;

/**
 * Reads a LABEL: the active language, else any language the value has. Use for
 * titles, categories, summaries and anything that must render something rather
 * than nothing. For body text use {@link tlStrict}, which reports the gap.
 */
export function tl(value: Localized | string | null | undefined, lang: Lang): string {
  if (value == null) return '';
  if (typeof value === 'string') return value;
  if (value[lang]) return value[lang] as string;
  for (const code of LANG_CODES) {
    if (value[code]) return value[code] as string;
  }
  return '';
}

/**
 * Reads BODY content: only the active language, or null when it is missing, so
 * the caller can show the "not available in this language" state instead of
 * silently rendering another language.
 */
export function tlStrict(value: Localized | string | null | undefined, lang: Lang): string | null {
  if (value == null) return null;
  if (typeof value === 'string') return value;
  const text = value[lang];
  return text && text.trim() ? text : null;
}

/** Reads a per-language list leniently (accepted answers, word-bank tokens). */
export function tlList(
  value: Partial<Record<Lang, string[]>> | null | undefined,
  lang: Lang,
): string[] {
  if (value == null) return [];
  if (value[lang]?.length) return value[lang] as string[];
  for (const code of LANG_CODES) {
    if (value[code]?.length) return value[code] as string[];
  }
  return [];
}

/** The languages a value actually carries, in registry order. */
export function langsOf(
  value: Localized | Partial<Record<Lang, string[]>> | null | undefined,
): Lang[] {
  if (value == null) return [];
  return LANG_CODES.filter((code) => {
    const v = (value as Record<string, unknown>)[code];
    return Array.isArray(v) ? v.length > 0 : typeof v === 'string' && v.trim().length > 0;
  });
}

interface LangState {
  lang: Lang;
  setLang: (lang: Lang) => void;
}

const stored = typeof localStorage !== 'undefined' ? localStorage.getItem('lang') : null;

export const useLang = create<LangState>((set) => ({
  lang: isUiLang(stored) ? stored : DEFAULT_UI_LANG,
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
  stepPattern: { en: 'Step {0} / {1}', ru: 'Шаг {0} / {1}' },
  questionPattern: { en: 'Question {0} / {1}', ru: 'Вопрос {0} / {1}' },
  status_running: { en: 'running', ru: 'выполняется' },
  status_done: { en: 'done', ru: 'готово' },
  status_error: { en: 'error', ru: 'ошибка' },
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
  genLangsLabel: { en: 'Languages', ru: 'Языки' },
  allLanguages: { en: 'All', ru: 'Все' },
  addLanguage: { en: 'Generate this language', ru: 'Сгенерировать этот язык' },
  noContentInLang: {
    en: 'This topic has no text in {0} yet.',
    ru: 'У этой темы пока нет текста на языке {0}.',
  },
  showInLang: { en: 'Show in {0}', ru: 'Показать на {0}' },
  generateInLang: { en: '✨ Generate in {0}', ru: '✨ Сгенерировать на {0}' },
  generatingInLang: { en: 'Generating…', ru: 'Генерируется…' },
  errorBoundary: {
    en: 'Something went wrong while rendering this panel.',
    ru: 'Что-то пошло не так при отрисовке этой панели.',
  },
  errorRetry: { en: 'Try again', ru: 'Повторить' },
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
  // --- Settings / self-update ---
  settings: { en: 'Settings', ru: 'Настройки' },
  settingsTitle: { en: '⚙️ Settings', ru: '⚙️ Настройки' },
  settingsUpdate: { en: '⬇️ Update', ru: '⬇️ Обновить' },
  settingsUpdateDesc: {
    en: 'Pull the latest commits from GitHub, rebuild and restart.',
    ru: 'Подтянуть новые коммиты из GitHub, пересобрать и перезапустить.',
  },
  settingsRestart: { en: '↻ Restart', ru: '↻ Перезапустить' },
  settingsRestartDesc: {
    en: 'Rebuild from the current local files and restart.',
    ru: 'Пересобрать из текущих локальных файлов и перезапустить.',
  },
  settingsUpToDate: { en: 'Up to date', ru: 'Актуально' },
  settingsCommitsBehind: { en: 'new commit(s)', ru: 'нов. коммит(ов)' },
  settingsUnsupervised: {
    en: 'Update/restart is available only when the app is launched from its desktop icon.',
    ru: 'Обновление/перезапуск доступны только при запуске через ярлык на рабочем столе.',
  },
  settingsNoSource: {
    en: 'The source tree was not found next to the app, so it cannot rebuild here.',
    ru: 'Рядом с приложением нет исходников, пересборка на этой машине невозможна.',
  },
  settingsNoGit: {
    en: 'No git checkout with an upstream — pulling from GitHub is unavailable here.',
    ru: 'Нет git-репозитория с апстримом — обновление из GitHub недоступно.',
  },
  settingsRestarting: {
    en: 'Updating — the app is rebuilding and restarting. This can take a couple of minutes…',
    ru: 'Обновление — приложение пересобирается и перезапускается. Это может занять пару минут…',
  },
  settingsRestartTimeout: {
    en: "The app is taking longer than expected. It's usually still rebuilding — reload to check.",
    ru: 'Приложение отвечает дольше обычного. Обычно оно ещё пересобирается — обновите страницу для проверки.',
  },
  settingsReloadNow: { en: 'Reload now', ru: 'Обновить страницу' },
  bulkDialogTitleTheory: {
    en: 'Generate theory for all questions without it',
    ru: 'Сгенерировать теорию для всех вопросов без неё',
  },
  bulkDialogTitleAtoms: {
    en: 'Generate lessons for all topics without one',
    ru: 'Сгенерировать уроки для всех тем без них',
  },
  bulkDialogCount: { en: 'Items to generate:', ru: 'Будет сгенерировано:' },
  bulkEndTime: { en: 'Stop by', ru: 'Остановить к' },
  bulkEndTimeHint: {
    en: 'A time already past today means tomorrow (e.g. 10:00 tonight = tomorrow morning).',
    ru: 'Время, уже прошедшее сегодня, означает завтра (например, 10:00 ночью = завтра утром).',
  },
  bulkMaxPercent: { en: 'Max usage by that time', ru: 'Максимум расхода к этому времени' },
  bulkMaxPercentHint: {
    en: 'While the 5-hour limit window resets before the stop time, up to 100% may be used; '
      + 'after the last reset the loop keeps usage under this cap so you are not locked out.',
    ru: 'Пока 5-часовое окно лимитов сбрасывается до времени остановки, можно тратить до 100%; '
      + 'после последнего сброса цикл держит расход ниже этого порога, чтобы не заблокировать вас.',
  },
  bulkMaxWeeklyPercent: {
    en: 'Max usage of the weekly limit',
    ru: 'Максимум расхода недельного лимита',
  },
  bulkMaxWeeklyPercentHint: {
    en: 'The weekly window resets in days, so hitting this cap ends the run instead of '
      + 'pausing it — raise it and start again if you want to keep going.',
    ru: 'Недельное окно сбрасывается через несколько дней, поэтому при достижении этого '
      + 'порога цикл завершается, а не ждёт — поднимите порог и запустите заново, если нужно продолжить.',
  },
  bulkNoDeadline: {
    en: 'No stop time — run until the topics run out or I stop it',
    ru: 'Без времени остановки — работать, пока не закончатся темы или я не остановлю',
  },
  bulkNoDeadlineHint: {
    en: 'For daytime runs: the loop keeps every 5-hour window under the cap below, '
      + 'so the rest stays available for your own work. When the cap is hit it pauses '
      + 'until the window resets and then continues.',
    ru: 'Для запуска днём: цикл держит каждое 5-часовое окно ниже указанного порога, '
      + 'так что остальное остаётся для вашей работы. При достижении порога он ждёт '
      + 'сброса окна и продолжает.',
  },
  bulkMaxPercentWindow: {
    en: 'Max usage per 5-hour window',
    ru: 'Максимум расхода за 5-часовое окно',
  },
  bulkMaxPercentWindowHint: {
    en: 'The reading covers all Claude usage, including your own parallel work, '
      + 'so the loop backs off on its own while you are busy.',
    ru: 'Показатель учитывает весь расход Claude, включая вашу параллельную работу, '
      + 'так что цикл сам притормаживает, пока вы заняты.',
  },
  bulkStart: { en: 'Start', ru: 'Начать' },
  bulkStop: { en: 'Stop', ru: 'Остановить' },
  bulkStopping: { en: 'Stopping…', ru: 'Останавливается…' },
  bulkDismiss: { en: 'Hide', ru: 'Скрыть' },
  bulkUntil: { en: 'until', ru: 'до' },
  bulkPhaseGenerating: { en: 'Generating', ru: 'Генерация' },
  bulkPhaseWaitingPace: { en: 'Waiting (request spacing)', ru: 'Ожидание (пауза между запросами)' },
  bulkPhaseWaitingUsage: { en: 'Waiting for usage data', ru: 'Ожидание данных о лимитах' },
  bulkPhaseWaitingReset: { en: 'Paused until the limit resets', ru: 'Пауза до сброса лимита' },
  bulkPhaseFinished: { en: 'Done', ru: 'Готово' },
  bulkPhaseStopped: { en: 'Stopped', ru: 'Остановлено' },
  bulkPhaseEndReached: { en: 'Stop time reached', ru: 'Достигнуто время остановки' },
  bulkPhaseCapReached: { en: 'Usage cap reached', ru: 'Достигнут порог расхода' },
  bulkPhaseWeeklyCapReached: {
    en: 'Weekly usage cap reached',
    ru: 'Достигнут недельный порог расхода',
  },
  bulkWeekly: { en: 'week', ru: 'нед.' },
  bulkPhaseNoResetInfo: {
    en: 'Stopped: no limit-reset info',
    ru: 'Остановлено: нет данных о сбросе лимитов',
  },
};

/**
 * A chrome string. Falls back to another translated language rather than
 * rendering "undefined" when a UI language has no entry for the key.
 */
export function ui(key: keyof typeof UI | string, lang: Lang): string {
  const entry = UI[key];
  if (!entry) return key;
  return entry[lang] ?? entry[FALLBACK_LANG] ?? tl(entry, lang) ?? key;
}

/** Fills {0}, {1}, … in a UI pattern. */
function fmt(pattern: string, ...args: (string | number)[]): string {
  return pattern.replace(/\{(\d+)\}/g, (m, i) => String(args[Number(i)] ?? m));
}

/** A chrome string with {0}, {1}, … placeholders filled in. */
export function fmtUi(key: string, lang: Lang, ...args: (string | number)[]): string {
  return fmt(ui(key, lang), ...args);
}

export function stepLabel(lang: Lang, current: number, total: number): string {
  return fmt(ui('stepPattern', lang), current, total);
}

export function questionLabel(lang: Lang, current: number, total: number): string {
  return fmt(ui('questionPattern', lang), current, total);
}

export function statusLabel(lang: Lang, status: string): string {
  const key = `status_${status}`;
  return UI[key] ? ui(key, lang) : status;
}
