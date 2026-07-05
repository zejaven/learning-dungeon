# План: режим «Learn by micro-actions» (Java Interview Dungeon)

> После утверждения первым шагом скопировать этот план в репозиторий как `plans/learn-by-micro-actions.md` (пользователь просил отдельный .md-файл).

## Контекст

Чтение длинной сгенерированной теории не работает из-за СДВГ: чтение прерывается, перечитывается, фокус теряется. Идея (из обсуждения в `chatgpt-discussion.md`): заменить «чтение темы» на «прохождение темы» — теория атомизируется в knowledge atoms, каждый атом подаётся через микродействие (вопрос → действие → мгновенный фидбек → 1-2 предложения теории). Существующая теория остаётся как справочник. Поток: **Discovery Run → Practice Loop → Boss Fight**, всё в одном экране со страничной навигацией в стиле Duolingo, плюс глобальный режим повторения по завершённым темам.

### Зафиксированные решения пользователя

- Стили генерации (Real world и т.д.) — **только для теории** (справочника). Упражнения генерируются нейтрально.
- В глобальный пул повторения попадают **только Practice Loop** упражнения (не Discovery, не Boss Fight).
- Boss Fight внутри урока **переиспользует существующие вопросы из `quiz.yaml`** и весь текущий механизм оценки (SCORE n/10, порог 6, `boss_fight_answer`).
- Кнопка «Go to practice →» скрывается **только для trace-топиков** (миссии остаются доступны по прямой ссылке `#/q/<id>/practice`); у structural/sql/challenge кнопка остаётся.
- Урок показывается **по умолчанию** вместо теории; теория открывается кнопкой «Справочник» (там же version bar и стили).
- Ответы и правильность сохраняются в PostgreSQL. Ошибки допустимы в Discovery/Practice; Boss Fight гейтится по оценке.
- Кнопка «Generate lesson» генерирует `learning-atoms.json` из уже существующей теории через Claude Code CLI.

## 1. Артефакт контента: `topics/<id>/learning-atoms.json`

UTF-8 JSON рядом с `topic.yaml`. Читается на каждый запрос (как всё в `TopicRepository`), но отдельным загрузчиком. Бэкенд считает `atomsHash = sha256(байты файла)` — токен инвалидации прогресса при регенерации.

```jsonc
{
  "schemaVersion": 1,
  "topicId": "hashmap",
  "sourceVersion": 1,          // версия теории-источника
  "aiProvider": "claude",
  "aiModel": "claude-opus-4-8",
  "atoms": [
    {
      "id": "bucket-index",              // стабильный kebab id, уникален в файле
      "title":   { "en": "...", "ru": "..." },
      "summary": { "en": "...", "ru": "..." },   // вывод атома в 1-2 предложения
      "discovery": [ /* Exercise[] — prediction-first, теория приходит в фидбеке */ ],
      "practice":  [ /* Exercise[] — закрепление; попадают в review pool */ ]
    }
  ]
}
```

**Exercise** — discriminated union по `type`. Общие поля: `id`, `type`, `prompt {en,ru}`, опционально `code` (не локализуется) + `codeLang`, опционально `mermaid {en,ru}` (статичный), `feedback { correct: {en,ru}, incorrect: {en,ru} }` (максимум 1-2 предложения теории).

| `type` | Специфичные поля |
|---|---|
| `multiple_choice` | `options: [{id, text{en,ru}, correct, feedback?{en,ru}}]` — ровно один `correct: true`; фидбек опции = объяснение misconception |
| `true_false` | `answer: boolean` (утверждение в `prompt`) |
| `fill_blank` | `text{en,ru}` с одним `___`; `answers {en: string[], ru: string[]}` (сравнение trim + case-insensitive) |
| `word_bank` | `tokens {en,ru}` в правильном порядке + `distractors {en,ru}`; UI перемешивает |
| `sort_steps` | `steps: [{id, text{en,ru}}]` в правильном порядке (≥3); UI перемешивает |
| `match_pairs` | `pairs: [{id, left{en,ru}, right{en,ru}}]` (3–5) |
| `predict_output` | как MC + обязательный `code` (отдельный type для промпта/статистики) |
| `spot_bug` | как MC + обязательный `code` |

Вся проверка **детерминированная, на клиенте** (`grading.ts`) — без AI-вызовов в Discovery/Practice/Review. Boss Fight в этом файле нет — берётся из `quiz.yaml`.

Drag-на-визуализатор интеракций в v1 нет (future work).

## 2. Юниты (кружочки): деривация, не хранение

Чистая функция от `(atoms, bossFight)`, реализуется зеркально: `lesson/LessonUnits.java` (бэкенд, для recompute завершения) и `engine/lessonUnits.ts` (фронтенд, для рендера); закрепляется тестом.

1. **Discovery-юниты**: по одному на атом (в порядке файла), id `d:<atomId>`, упражнения = `discovery` атома.
2. **Practice-юниты**: все practice-упражнения выравниваются **round-robin по атомам** (1-е каждого атома, потом 2-е, …) — микс тем как в Duolingo; чанки по 5 (последний чанк < 3 → слить с предыдущим); id `p1`, `p2`, …
3. **Boss-юниты**: по одному на вопрос `bossFight`, id `b:<questionId>`.

Гейтинг: юниты открываются строго последовательно; будущие — серые/заблокированные; навигация только по пройденным и текущему. Внутри юнита — кнопка Continue после ответа. Boss-юнит проходится при score ≥ 6.

## 3. Backend

Новый пакет `com.interviewlearning.lesson`:

- **`lesson/LessonDtos.java`** — records схемы §1 (`LearningAtoms`, `Atom`, `Exercise` с nullable type-полями, Jackson-friendly), `AtomsResponse(atomsHash, atoms)`, `LessonState(atomsHash, completedUnits, lessonCompleted)`, `ExerciseAnswerRequest(exerciseId, atomId, unitId, context, answerJson, correct)`, `UnitCompleteRequest(unitId, atomsHash)`, review-DTO.
- **`lesson/LearningAtomsRepository.java`** — читает JSON через `RepoPaths` + `ObjectMapper`; lenient (лог + empty при ошибке парсинга, как `TopicRepository.loadYaml`); `exists(topicId)`.
- **`lesson/LessonUnits.java`** — `derive(atoms, boss): List<UnitRef>` по §2.
- **`lesson/LessonProgressRepository.java`** — JDBC в стиле `ProgressRepository`: `recordAnswer`, `completedUnits(topicId, atomsHash)`, `completeUnit` (ON CONFLICT DO NOTHING), `recomputeLessonCompletion(...)` (`@Transactional`): урок завершён ⇔ все не-boss юниты в `lesson_unit_progress` для текущего hash И все boss-вопросы passed (по живым строкам `boss_fight_answer`). При переходе в completed — upsert `lesson_progress` + заполнение `review_pool` всеми practice-упражнениями (явные строки — требование пользователя).

### Эндпоинты — `api/LessonController.java`

- `GET /api/topics/{id}/atoms` → `{atomsHash, atoms}` или 404.
- `GET /api/lesson/{topicId}/state` → `LessonState` (hash mismatch ⇒ прогресс считается пустым — автоинвалидация при регенерации).
- `POST /api/lesson/{topicId}/answer` — лог ответа; при `context='review'` обновляет статистику `review_pool`. Правильность считает клиент — доверяем (та же модель, что у trace-миссий; локальный инструмент).
- `POST /api/lesson/{topicId}/unit-complete` — 409 при несовпадении `atomsHash` («урок перегенерирован, обновите»); вставка юнита; recompute завершения; возвращает `{lessonCompleted}`. После прохождения boss-вопроса фронт тоже вызывает unit-complete (recompute в одном месте).

### Review — `api/ReviewController.java`

- `GET /api/review/summary` → `{poolSize, topicCount}` для бейджа на главной; попутно lazy-prune строк, чьи `exercise_id` больше не резолвятся в текущем файле топика.
- `POST /api/review/session/start` — вернуть активную сессию или создать: все строки пула → полные Exercise (inline, чтобы ReviewScreen не грузил топики), shuffle, `state_json = {items, queue: [0..n-1], position: 0}`.
- `GET /api/review/session/active` — для resume после перезагрузки.
- `POST /api/review/session/{id}/answer` — лог в `lesson_exercise_answer` (`context='review'`), обновление статистики пула; wrong ⇒ **индекс item добавляется в конец queue** (перерешивание до правильного ответа); queue исчерпан ⇒ `finished_at`.
- `POST /api/review/session/{id}/abandon`.

### DDL в `DbInitializer.init()` (тот же идемпотентный стиль)

```sql
CREATE TABLE IF NOT EXISTS lesson_exercise_answer (
    id BIGSERIAL PRIMARY KEY, topic_id TEXT NOT NULL, exercise_id TEXT NOT NULL,
    atom_id TEXT, unit_id TEXT, context TEXT NOT NULL DEFAULT 'lesson', -- lesson | review
    atoms_hash TEXT NOT NULL, answer_json TEXT NOT NULL, correct BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX IF NOT EXISTS ix_lesson_answer ON lesson_exercise_answer (topic_id, exercise_id, created_at);

CREATE TABLE IF NOT EXISTS lesson_unit_progress (
    id BIGSERIAL PRIMARY KEY, topic_id TEXT NOT NULL, unit_id TEXT NOT NULL,
    atoms_hash TEXT NOT NULL, completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (topic_id, unit_id, atoms_hash));

CREATE TABLE IF NOT EXISTS lesson_progress (
    topic_id TEXT PRIMARY KEY, atoms_hash TEXT NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE, completed_at TIMESTAMPTZ);

CREATE TABLE IF NOT EXISTS review_pool (
    id BIGSERIAL PRIMARY KEY, topic_id TEXT NOT NULL, exercise_id TEXT NOT NULL,
    atom_id TEXT, atoms_hash TEXT NOT NULL, added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_reviewed_at TIMESTAMPTZ, last_correct BOOLEAN,
    correct_count INT NOT NULL DEFAULT 0, wrong_count INT NOT NULL DEFAULT 0,
    UNIQUE (topic_id, exercise_id));

CREATE TABLE IF NOT EXISTS review_session (
    id BIGSERIAL PRIMARY KEY, started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ, state_json TEXT NOT NULL);
CREATE UNIQUE INDEX IF NOT EXISTS ux_review_session_active
    ON review_session ((1)) WHERE finished_at IS NULL;
```

`topic_progress`/`recomputeCompletion` не трогаем: «топик завершён» (все boss passed) и «урок завершён» (все юниты) сосуществуют; завершение урока влечёт завершение топика, т.к. boss-юниты гейтятся теми же строками.

### Генерация атомов

- `ai/AiTask.java`: добавить `GENERATE_ATOMS(true, true)` (пишет файлы; strong model).
- `generation/GenerationService.startOrGet` — добавить параметр `AiTask` (сейчас хардкод `GENERATE_TOPIC`); поправить единственного вызывающего `TopicGenController`.
- `POST /api/topics/{id}/atoms/generate` `{provider, versionNo}` → промпт + `startOrGet("atoms:" + id, provider, prompt, GENERATE_ATOMS)`. SSE attach/reattach — существующие `GET /api/topics/generate/{taskId}/events` и `/generate/active` без изменений (key-agnostic).
- Промпт: `prompts/generate-learning-atoms.md` + заголовок топика + **en и ru текст выбранной версии теории** (v1 — с диска, ≥2 — из `theory_version`) + boss-вопросы из `quiz.yaml` (только контекст: атомы готовят к ним, не дублируют) + абсолютный путь вывода. AI **пишет файл напрямую** (как в `VersionController` — обход искажения кириллицы в stdout на Windows).
- `TopicDtos.TopicDetail`/`TopicSummary`: поле `hasAtoms` (по `Files.exists(...)` в `TopicRepository`).

### Контракт-тест

`backend/src/test/java/com/interviewlearning/lesson/LessonAtomsContractTest.java` — dynamic-test на каждый топик с `learning-atoms.json` (по образцу `TopicContractTest`): валидный JSON; `schemaVersion == 1`; `topicId` = имя папки; 8–15 атомов; уникальные kebab-id; все `Localized` непустые в обоих языках; по-типовые проверки (ровно один correct у MC; ровно один `___` в fill_blank; sort_steps ≥ 3; match_pairs 3–5; `code` у predict_output/spot_bug); у атома ≥1 discovery и ≥2 practice. Плюс unit-тест `LessonUnits.derive` на фикстуре (закрепляет FE/BE-зеркало, `PRACTICE_CHUNK = 5`).

## 4. Промпт `prompts/generate-learning-atoms.md` (контур)

1. Роль: конвертировать существующее двуязычное объяснение в файл `learning-atoms.json`; писать ОДИН валидный JSON по указанному пути, ничего в stdout.
2. Полная схема §1 + один полный пример атома; «валидный JSON, UTF-8, без комментариев/fences».
3. Атомы: 8–15, каждый — ОДНА идея, порядок от простого к сложному; осмысленные kebab-id.
4. Объём: на атом 1–3 discovery + 3–6 practice (цель 40–70 упражнений на топик — пользователь хочет много закрепления); разнообразие типов; каждое practice-упражнение самодостаточно (без «как мы видели выше»).
5. **Discovery = prediction-first**: учащийся НЕ читал теорию; упражнение просит предсказать; теория подаётся только через `feedback`, максимум 1–2 предложения. Не читать лекции в `prompt`.
6. Неправильные варианты MC = **реальные misconceptions**, каждый со своим `feedback`.
7. Двуязычие: все `Localized` на естественных en и ru; код/идентификаторы на английском; mermaid (редко, опционально) на каждый язык с переведёнными подписями, Mermaid 11 по `prompts/mermaid-guide.md`.
8. **Нейтральный стиль**: игнорировать тематические аналогии из объяснения (у 148 топиков в текст вплетён стиль «Real world») — извлекать только технические факты; никакого сюжетного оформления в упражнениях.
9. Grounding: только факты из переданного объяснения; ничего не выдумывать.

## 5. Frontend

### Роутер (`frontend/src/engine/router.ts`)

- `View`: `'home' | 'workspace' | 'review'`; `Route` получает `sub: 'theory' | null`.
- `#/review` → ReviewScreen; `#/q/<id>/theory` → home с принудительным справочником (refresh-safe); `#/q/<id>/practice` без изменений.
- `routeForReview()`, `routeForTheory(id)`; в `App.tsx` — ветка ReviewScreen.

### Типы / API / сторы

- `engine/lessonTypes.ts` (discriminated union Exercise, LessonUnit, LessonState, review-DTO), `engine/lessonUnits.ts` (зеркало LessonUnits.java), `engine/grading.ts` (чистая `grade(exercise, answer)` — общая для урока и review).
- `engine/api.ts`: `fetchAtoms` (404 → null), `fetchLessonState`, `saveExerciseAnswer`, `completeUnit`, `startAtomsGeneration`, `fetchReviewSummary`, `startReviewSession`, `fetchActiveReviewSession`, `answerReview`, `abandonReviewSession`.
- `engine/lessonStore.ts` (новый zustand-стор): `atoms`, `atomsHash`, `units`, `completedUnits`, `currentUnitId`, `exerciseIndex`, `phase: 'answering'|'feedback'`, `lessonCompleted`; действия `loadLesson`, `submitAnswer` (grade → фидбек → fire-and-forget save), `continueNext` (следующее упражнение или completeUnit + следующий кружочек; при `lessonCompleted` — Celebration), `goToUnit` (только пройденные/текущий). Генерация атомов — через существующий `useGeneration` с ключом `atoms:<topicId>`; по `done` — refetch атомов.
- `engine/reviewStore.ts`: сессия, текущий item, фидбек-фаза; `start/resume/submit/next/abandon`.
- `traceTypes.ts`: `hasAtoms` в TopicDetail/TopicSummary. `i18n.ts`: новые UI-ключи (lesson, reference, backToLesson, generateLesson, regenerateLesson, check, continue, correct, incorrect, unitLocked, review, reviewEmpty, reviewFinished, …) en+ru.

### Компоненты — `frontend/src/shell/lesson/`

- **`LessonPanel.tsx`** — в правой панели HomeScreen: шапка (фаза + кнопка «Справочник» → `routeForTheory` + «Перегенерировать урок» с confirm), тело текущего юнита, `UnitTrack` внизу.
- **`UnitTrack.tsx`** — горизонтальный ряд кружочков: пройден = залит + кликабелен; текущий = кольцо; заблокирован = серый, клик игнорируется; boss = ⚔️; `overflow-x: auto`.
- **`ExerciseCard.tsx`** — диспетчер: `prompt` через `Markdown`, `code` через `<pre>` (Monaco не нужен), `MermaidBlock`, type-специфичный ввод, кнопка Check → фидбек-баннер (зелёный/красный + текст) → Continue. Ошибки всегда пропускают дальше.
- **`exercises/`**: `MultipleChoice`, `TrueFalse`, `FillBlank`, `WordBank` (тап-чипы), `SortSteps` (кнопки вверх/вниз, без DnD-библиотек в v1), `MatchPairs`. `predict_output`/`spot_bug` → MultipleChoice.
- **`BossFightUnit.tsx`** — inline boss-вопрос. **Рефакторинг, не дублирование**: извлечь из `BossFightDialog.tsx` общий `frontend/src/shell/BossQuestionForm.tsx` (evaluate-SSE, `parseScore`/`stripScoreLine`, `saveBossAnswer`, textarea + verdict + score badge) с пропсами `{topicId, question, stored, onGraded}`. Диалог сохраняет модальную обёртку и prev/next; юнит рендерит одну форму; при pass (≥6) вызывает `completeUnit('b:<qid>')` и открывает Continue. Ранее пройденные boss-вопросы — кружочек сразу зелёный.

### `HomeScreen.tsx` (ветка theoryReady)

- `topic.hasAtoms && route.sub !== 'theory'` → `<LessonPanel/>` (дефолт). Ask AI остаётся в шапке.
- `route.sub === 'theory'` → существующий блок теории без изменений (version bar, StyleSelector, New version, Markdown) + кнопка «← К уроку» (если hasAtoms).
- `!topic.hasAtoms` → теория + кнопка **«✨ Generate lesson»** (передаёт активный `activeVersionNo`; во время работы `GenerationView` с ключом `atoms:<id>`; по done урок появляется).
- Кнопка «Go to practice →» скрывается **только при `topic.mode === 'trace'`** (structural/sql/challenge — остаётся). Кнопка «⚔️ Boss Fight» (диалог) — только для топиков без атомов.
- Шапка: кнопка «🔁 Review» с бейджем `poolSize` → `#/review`.

### `screens/ReviewScreen.tsx`

Полноэкранный (как WorkspaceScreen): назад-на-главную, прогресс `answered / total` (remaining растёт при requeue), один `ExerciseCard` за раз из `reviewStore`. Неправильный ответ → фидбек → Continue → item в хвост очереди (персистится на сервере). Пустой пул → подсказка «пройди урок целиком, чтобы открыть повторение». Финиш → мини-celebration + «Начать заново».

## 6. Регенерация / инвалидация

- Регенерация атомов (кнопка в LessonPanel с предупреждением) ⇒ новый `atomsHash` ⇒ state возвращает 0 пройденных юнитов — прогресс сбрасывается по построению; старые строки остаются историей.
- Boss-прогресс **не сбрасывается** (стабильные id из `quiz.yaml`, `boss_fight_answer` не трогаем).
- Устаревшие строки `review_pool` чистятся лениво в summary/session-start; повторное завершение перегенерированного урока перезаполняет пул.

## 7. Пример: `topics/hashmap/` («Как работает HashMap под капотом?»)

Входы: `explanation.en.md`/`ru.md` (mental model → коллизии → load factor/resize → мутабельные ключи → потокобезопасность), `quiz.yaml` с 5 boss-id: `bucket-index`, `collision-java8`, `mutable-key`, `resize`, `thread-safety` (проверено).

Фрагмент `learning-atoms.json` (2 из ~9 атомов; в реальном файле у каждого 3–6 practice):

```json
{
  "schemaVersion": 1, "topicId": "hashmap", "sourceVersion": 1,
  "aiProvider": "claude", "aiModel": "claude-opus-4-8",
  "atoms": [
    {
      "id": "bucket-index",
      "title": { "en": "From key to bucket index", "ru": "От ключа к индексу бакета" },
      "summary": { "en": "index = spread(hashCode) & (capacity - 1) — a fast mod because capacity is a power of two.",
                   "ru": "index = spread(hashCode) & (capacity - 1) — быстрый mod, потому что ёмкость — степень двойки." },
      "discovery": [
        { "id": "bi-d1-predict-index", "type": "predict_output",
          "prompt": { "en": "A key's spread hash is 2112 and the map has 16 buckets. Predict the bucket index.",
                      "ru": "Распределённый хэш ключа равен 2112, в мапе 16 бакетов. Предскажи индекс бакета." },
          "code": "int index = 2112 & (16 - 1);\nSystem.out.println(index);", "codeLang": "java",
          "options": [
            { "id": "a", "text": { "en": "0", "ru": "0" }, "correct": true },
            { "id": "b", "text": { "en": "2112", "ru": "2112" }, "correct": false,
              "feedback": { "en": "The raw hash is masked down to the bucket range first.",
                            "ru": "Сырой хэш сначала маскируется до диапазона бакетов." } },
            { "id": "c", "text": { "en": "12", "ru": "12" }, "correct": false,
              "feedback": { "en": "Decimal trap: the mask uses capacity 16, and 2112 & 15 = 0.",
                            "ru": "Десятичная ловушка: маска использует ёмкость 16, и 2112 & 15 = 0." } }
          ],
          "feedback": {
            "correct":   { "en": "2112 & 15 = 0: the low bits pick the bucket — that is why capacity is a power of two.",
                           "ru": "2112 & 15 = 0: бакет выбирают младшие биты — поэтому ёмкость всегда степень двойки." },
            "incorrect": { "en": "HashMap computes hash & (capacity - 1); with capacity 16 only the low 4 bits remain, so 2112 → 0.",
                           "ru": "HashMap вычисляет hash & (capacity - 1); при ёмкости 16 остаются младшие 4 бита, поэтому 2112 → 0." } } }
      ],
      "practice": [
        { "id": "bi-p1-order-steps", "type": "sort_steps",
          "prompt": { "en": "Put the steps of put(key, value) in order.", "ru": "Расставь шаги put(key, value) по порядку." },
          "steps": [
            { "id": "s1", "text": { "en": "Call key.hashCode()", "ru": "Вызвать key.hashCode()" } },
            { "id": "s2", "text": { "en": "Spread it: h ^ (h >>> 16)", "ru": "Распределить: h ^ (h >>> 16)" } },
            { "id": "s3", "text": { "en": "Mask: hash & (capacity - 1)", "ru": "Маска: hash & (capacity - 1)" } },
            { "id": "s4", "text": { "en": "Store the entry in that bucket", "ru": "Положить запись в этот бакет" } } ],
          "feedback": {
            "correct":   { "en": "hashCode → spread → mask → store; get() repeats the same route.",
                           "ru": "hashCode → spread → mask → store; get() повторяет тот же маршрут." },
            "incorrect": { "en": "The spread happens before masking so high bits also influence the bucket choice.",
                           "ru": "Spread выполняется до маски, чтобы старшие биты тоже влияли на выбор бакета." } } },
        { "id": "bi-p2-fill-mask", "type": "fill_blank",
          "prompt": { "en": "Type the missing operator.", "ru": "Впиши пропущенный оператор." },
          "text": { "en": "index = hash ___ (capacity - 1)", "ru": "index = hash ___ (capacity - 1)" },
          "answers": { "en": ["&"], "ru": ["&"] },
          "feedback": {
            "correct":   { "en": "Bitwise AND with capacity-1 is a fast modulo for power-of-two capacities.",
                           "ru": "Побитовое И с capacity-1 — быстрый остаток от деления для степеней двойки." },
            "incorrect": { "en": "It is &, not %: for a power-of-two capacity the AND mask equals the modulo but is cheaper.",
                           "ru": "Это &, а не %: при ёмкости-степени двойки маска И равна остатку, но дешевле." } } }
      ]
    },
    {
      "id": "collisions",
      "title": { "en": "Collisions and Java 8 treeify", "ru": "Коллизии и treeify в Java 8" },
      "summary": { "en": "Colliding keys chain in one bucket; Java 8 turns chains longer than 8 into red-black trees.",
                   "ru": "Ключи с коллизией образуют цепочку в одном бакете; в Java 8 цепочки длиннее 8 становятся красно-чёрными деревьями." },
      "discovery": [
        { "id": "col-d1-aa-bb", "type": "multiple_choice",
          "prompt": { "en": "\"Aa\" and \"BB\" both have hashCode() == 2112. What happens when you put both into one map?",
                      "ru": "У \"Aa\" и \"BB\" hashCode() == 2112. Что произойдёт, если положить оба ключа в одну мапу?" },
          "options": [
            { "id": "a", "text": { "en": "Both land in one bucket and form a chain", "ru": "Оба попадут в один бакет и образуют цепочку" }, "correct": true },
            { "id": "b", "text": { "en": "The second put overwrites the first entry", "ru": "Второй put перезапишет первую запись" }, "correct": false,
              "feedback": { "en": "Overwrite happens only when equals() says the keys are the same — \"Aa\".equals(\"BB\") is false.",
                            "ru": "Перезапись происходит только когда equals() считает ключи одинаковыми — \"Aa\".equals(\"BB\") == false." } },
            { "id": "c", "text": { "en": "put throws an exception", "ru": "put бросит исключение" }, "correct": false,
              "feedback": { "en": "Collisions are a normal case handled by chaining.", "ru": "Коллизии — нормальный случай, он решается цепочками." } }
          ],
          "feedback": {
            "correct":   { "en": "Same hash → same bucket → chain; equals() distinguishes keys inside it.",
                           "ru": "Одинаковый хэш → один бакет → цепочка; внутри неё ключи различает equals()." },
            "incorrect": { "en": "Equal hashes do not mean equal keys: both entries live in one bucket as a chain.",
                           "ru": "Равные хэши не означают равные ключи: обе записи живут в одном бакете цепочкой." } } }
      ],
      "practice": [
        { "id": "col-p1-treeify-tf", "type": "true_false",
          "prompt": { "en": "In Java 8+, a bucket chain longer than 8 entries (capacity ≥ 64) becomes a red-black tree.",
                      "ru": "В Java 8+ цепочка бакета длиннее 8 записей (при ёмкости ≥ 64) становится красно-чёрным деревом." },
          "answer": true,
          "feedback": {
            "correct":   { "en": "Treeify keeps worst-case lookup at O(log n) instead of O(n).",
                           "ru": "Treeify держит худший случай поиска на O(log n) вместо O(n)." },
            "incorrect": { "en": "This is true — the Java 8 defence against long chains and hash-flooding.",
                           "ru": "Это правда — так Java 8 защищается от длинных цепочек и hash-flooding." } } },
        { "id": "col-p2-word-bank", "type": "word_bank",
          "prompt": { "en": "Assemble the collision rule.", "ru": "Собери правило коллизии." },
          "tokens": { "en": ["same", "bucket", "→", "walk", "the", "chain", "comparing", "with", "equals()"],
                      "ru": ["один", "бакет", "→", "идём", "по", "цепочке", "сравнивая", "через", "equals()"] },
          "distractors": { "en": ["hashCode()", "=="], "ru": ["hashCode()", "=="] },
          "feedback": {
            "correct":   { "en": "hashCode() finds the bucket; equals() finds the key inside it.",
                           "ru": "hashCode() находит бакет; equals() находит ключ внутри него." },
            "incorrect": { "en": "Inside a bucket the map compares keys with equals(), never == or hashCode().",
                           "ru": "Внутри бакета мапа сравнивает ключи через equals(), а не == или hashCode()." } } }
      ]
    }
  ]
}
```

Раскладка юнитов (при 9 атомах, 2 discovery + 4 practice на атом = 18 discovery + 36 practice):

```
Кружочки: (D)(D)(D)(D)(D)(D)(D)(D)(D)  (P)(P)(P)(P)(P)(P)(P)(P)  (⚔)(⚔)(⚔)(⚔)(⚔)
Id:       d:bucket-index … d:thread-safety   p1…p8 (36/5, round-robin микс)   b:bucket-index … b:thread-safety
```

- Свежий урок: текущий юнит `d:bucket-index`, всё правее — серое.
- `p1` содержит первые practice-упражнения атомов 1–5 (round-robin) — микс тем.
- `b:collision-java8` рендерит `BossQuestionForm` с вопросом из `quiz.yaml`; оценка через `POST /api/assistant/evaluate` (SCORE ≥ 6), сохранение через нетронутый `POST /api/progress/hashmap/boss-fight`. Уже пройденный ранее вопрос — кружочек сразу зелёный.
- Прохождение `b:thread-safety`: unit-complete → сервер видит все юниты → `lesson_progress.completed = true` → 36 practice-id вставляются в `review_pool` → Celebration; бейдж Review на главной +36.

## 8. Порядок реализации и верификация

Бэкенд-шаги завершаются `.\gradlew.bat :backend:test`; фронтенд-шаги — `npm run build` в `frontend/`.

0. Скопировать этот план в репозиторий (`plans/learn-by-micro-actions.md`).
1. **Схема + загрузчик**: `LessonDtos`, `LearningAtomsRepository`, `LessonUnits`; `hasAtoms`; `GET /api/topics/{id}/atoms`; `LessonAtomsContractTest` + unit-тест derive. → тесты.
2. **БД + прогресс**: DDL в `DbInitializer`, `LessonProgressRepository`, эндпоинты state/answer/unit-complete. → тесты.
3. **Генерация**: `AiTask.GENERATE_ATOMS`, параметр в `GenerationService.startOrGet`, `POST .../atoms/generate`, `prompts/generate-learning-atoms.md`. → тесты.
4. **Фронтенд-ядро**: роутер, `lessonTypes.ts`, `lessonUnits.ts`, `grading.ts`, api-функции, `lessonStore.ts`, i18n-ключи. → build.
5. **UI урока**: `LessonPanel`, `UnitTrack`, `ExerciseCard` + 6 компонентов упражнений, извлечение `BossQuestionForm`, `BossFightUnit`, правки `HomeScreen` (урок по умолчанию, справочник, Generate lesson, скрытие practice-кнопки у trace), CSS. → build.
6. **Review**: `ReviewController` + методы репозитория → тесты; `reviewStore.ts`, `ReviewScreen.tsx`, кнопка Review в шапке. → build.
7. **E2E вручную** через `.\dev.ps1` на `topics/hashmap`: сгенерировать урок, пройти discovery → practice → boss, завершить, открыть `#/review`, ответить неверно (проверить requeue), перезагрузить посреди сессии (проверить resume). Требует локального PostgreSQL и Claude CLI — если недоступны, явно сказать, что осталось непроверенным.
8. Закоммитить сгенерированный `topics/hashmap/learning-atoms.json` как эталон под контроль `LessonAtomsContractTest`.

## 9. Риски / открытые вопросы

- **Надёжность генерации**: большой строгий JSON напрямую в `topics/<id>/` может быть обрезан/невалиден. Митигируется lenient-парсингом (404 → кнопка генерации снова видна) и идемпотентным ключом `atoms:<id>`; при необходимости — пост-валидация в контроллере (v1: retry вручную).
- **FE/BE-зеркало деривации юнитов** — дублирование логики; закреплено тестом и константой `PRACTICE_CHUNK = 5`. Чистая альтернатива на будущее: сервер возвращает юниты в `GET /atoms`.
- **Клиентская оценка правильности** доверяется сервером — как у trace-миссий; для локального однопользовательского инструмента приемлемо.
- **Асимметрия при регенерации**: boss-кружочки могут быть уже зелёными при сброшенных discovery/practice — намеренно (стабильные id), UI должен рендерить их пройденными.
- **Рост пула**: ~30–40 упражнений на завершённый топик; v1-сессия — весь пул целиком; лимит сессии (например, 20 случайных) — лёгкая ручка v1.1 (`review_pool` уже хранит счётчики и `last_reviewed_at` — задел под SM-2 spaced repetition).
- **Вне v1**: drag-на-визуализатор, spaced-repetition-расписание, кнопка «объясни подробнее» у упражнения.
