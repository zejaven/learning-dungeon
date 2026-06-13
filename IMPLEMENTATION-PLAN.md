# План реализации: Java Interview Learning Engine

## Context

`interview-preparation` сейчас — почти пустой репозиторий: лежат вопросы с собеседования
(`src/main/resources/interview-questions/eRagaInfosystems-questions.md`), транскрипция и обсуждение
с ChatGPT (`chatgpt-discussion.md`). Цель — превратить подготовку к Java-собеседованию в
интерактивную обучающую игру: каждый вопрос становится «темой» с объяснением, редактируемым кодом,
запуском в песочнице и **визуальным интерактивным представлением**, которое реагирует на то, что
происходит в коде.

Главная архитектурная идея (согласована с пользователем): **не визуализировать произвольную Java
автоматически**, а сделать (1) универсальную оболочку, (2) набор переиспользуемых визуальных
примитивов, (3) управляемые учебные модели (instrumented wrappers), которые порождают **trace-события**.
Код не управляет UI напрямую — он порождает поток событий, а UI их проигрывает.

Решения пользователя:
- **Локальный персональный инструмент** (запуск только на ПК пользователя) — можно свободно вызывать
  Claude Code CLI и компилировать Java локально.
- Кнопка **«Добавить тему» вызывает Claude Code на backend** (`claude -p`), а в браузере открывается
  окно, куда **в реальном времени стримится вывод Claude** + индикатор статуса готовности темы.
- Песочница — **отдельный JVM-процесс с лимитами** (таймаут + `-Xmx`).
- MVP — **одна тема (HashMap) end-to-end**, чтобы доказать правильность архитектуры.

> Примечание: первым шагом реализации этот план будет сохранён в корень проекта как
> `IMPLEMENTATION-PLAN.md` (пользователь просил отдельный .md-файл-артефакт в самом приложении).

---

## Стек

- **Frontend:** React + TypeScript + Vite, **Monaco Editor**, Zustand (state), кастомный SVG/canvas для
  примитивов (React Flow подключим позже для графовых тем; для HashMap хватит SVG).
- **Backend:** Java 21 + Spring Boot (Gradle). Эндпоинты `/api/run`, `/api/topics`,
  `/api/topics/generate` (SSE), `/api/assistant/ask` (SSE).
- **visual-runtime:** отдельная Java-библиотека с учебными обёртками (`VisualHashMap` и т.д.) — она
  попадает в classpath при компиляции/запуске пользовательского кода и печатает trace-события.
- **topics/** — папка тем в корне репозитория; и backend (данные), и frontend (визуализатор) читают
  одну и ту же папку.

---

## Целевая структура репозитория

```
interview-preparation/
  backend/                          Spring Boot (Gradle)
    src/main/java/.../learning/
      LearningApplication.java
      api/RunController.java         POST /api/run  -> {output, traceEvents}
      api/TopicController.java       GET  /api/topics, GET /api/topics/{id}
      api/TopicGenController.java    POST /api/topics/generate (SSE stream Claude)
      api/AssistantController.java   POST /api/assistant/ask  (SSE stream Claude)
      runner/JavaCodeRunner.java     javac (JavaCompiler API) + дочерний java-процесс с лимитами
      runner/TraceCollector.java     парсит @@TRACE@@-строки из stdout
      claude/ClaudeCodeService.java  spawn `claude -p ... --output-format stream-json --verbose`
      topics/TopicRepository.java    читает topics/ (yaml, md, examples)
    build.gradle
  visual-runtime/                   Java-библиотека учебных моделей
    src/main/java/visual/
      Trace.java                     emit(event): печатает @@TRACE@@<json>
      VisualHashMap.java             учебная модель HashMap -> trace-события
    build.gradle
  frontend/                         React + TS + Vite
    index.html  vite.config.ts
    src/
      App.tsx
      shell/                         фиксированная оболочка (одинакова для всех тем)
        Layout.tsx ExplanationPanel.tsx EditorPanel.tsx VisualizationCanvas.tsx
        ExamplesBar.tsx MissionPanel.tsx AssistantDialog.tsx TopicSwitcher.tsx
        AddTopicDialog.tsx           окно со стримом вывода Claude + статус
        PlaybackControls.tsx         Run / Step / Prev / Reset / Explain step
      engine/
        topicRegistry.ts             import.meta.glob('../../topics/*/...') — авто-обнаружение тем
        TopicPlugin.ts               интерфейс topic plugin
        traceTypes.ts                типы trace-событий (общий контракт)
        runClient.ts                 fetch /api/run
        sseClient.ts                 чтение SSE для генерации темы и ассистента
      primitives/                    переиспользуемые визуальные примитивы
        ArrayGrid.tsx LinkedNodes.tsx EventLog.tsx         (MVP)
        TreeNodes.tsx Timeline.tsx ThreadLanes.tsx
        MemoryBlocks.tsx ObjectGraph.tsx TableView.tsx StateMachine.tsx   (заглушки/позже)
      state/store.ts                 Zustand: текущая тема, код, trace, шаг, миссии
  topics/
    hashmap/
      topic.yaml                     id, title, category, type, defaultCode-ref, primitives, missions
      explanation.md
      examples/
        01-basic-put.java 02-collision.java 03-resize.java 04-mutable-key.java ...
      visualizer.tsx                 рисует state из trace через примитивы
      trace-schema.json              JSON Schema для state HashMap-событий
      quiz.yaml                      вопросы/миссии/boss-fight
  prompts/
    add-topic.md                     СТРОГИЙ универсальный промпт-контракт для Claude Code
    topic-contract.md                справка по TopicPlugin / схеме папки темы
  src/main/resources/interview-questions/   (существующее) — пул вопросов для «Добавить тему»
  IMPLEMENTATION-PLAN.md             этот план, сохранённый как артефакт
  scripts/ или Makefile / npm-скрипты для одновременного запуска backend+frontend
```

---

## Ключевой контракт: trace-события

Общий «конверт» события (одинаков для всех тем); `state` — топик-специфичный, валидируется
`trace-schema.json` темы:

```json
{
  "step": 3,
  "event": "HASHMAP_PUT",
  "description": "Inserted key 'Aa' into bucket 0",
  "highlight": ["bucket:0", "node:Aa"],
  "state": {
    "capacity": 16, "loadFactor": 0.75, "threshold": 12,
    "buckets": [ { "index": 0, "nodes": [ {"key":"Aa","value":1,"hash":2112} ] } ]
  }
}
```

Поток: `visual-runtime` печатает каждое событие как `@@TRACE@@{...json...}` в stdout →
`TraceCollector` отделяет trace от обычного вывода программы → `/api/run` возвращает
`{ output, traceEvents[] }` → frontend проигрывает события пошагово через визуализатор темы.

`TopicPlugin` (frontend) — это просто: `{ meta, defaultCode, examples[], Visualizer, missions[] }`,
собираемый из файлов папки темы. Визуализатор получает на вход `state` текущего шага и рендерит его
примитивами; он **не знает** про Java-выполнение.

---

## Поток запуска кода (`/api/run`)

1. Frontend шлёт `{ topicId, code }` (один файл `Playground.java`).
2. `JavaCodeRunner` пишет код во временную папку, компилирует через `JavaCompiler` API с classpath,
   включающим `visual-runtime.jar`.
3. Запуск `java Playground` **в отдельном процессе** с `-Xmx128m`, таймаутом (напр. 5 c), без сети
   (для локального инструмента достаточно таймаута + лимита памяти + дочернего процесса; в плане
   отдельно отмечено, что это персональный инструмент и пользователь исполняет собственный код).
4. `TraceCollector` парсит `@@TRACE@@`-строки → `traceEvents`; остальное → `output`.
5. Ответ `{ output, traceEvents }`.

Кнопка **Reset** возвращает редактор к `defaultCode` темы. Кнопки **Examples** подставляют код из
`examples/*.java`.

---

## Интеграция Claude Code («Добавить тему») со стримингом

- `AddTopicDialog` (frontend): textarea для вопроса → POST `/api/topics/generate` → открывает **SSE**.
- `ClaudeCodeService` (backend) запускает подпроцесс:
  `claude -p "<add-topic.md + вопрос>" --output-format stream-json --verbose`
  (рабочая директория = корень репо, чтобы Claude создал `topics/<id>/`).
- Backend построчно читает stdout (stream-json), парсит события (assistant text, tool_use, result) и
  ретранслирует их через SSE в браузер. Диалог показывает текст по мере написания + **индикатор
  статуса**: `running → generating files → validating → done/error` (выводим из событий и из появления
  обязательных файлов в `topics/<id>/`).
- После завершения frontend перечитывает `topicRegistry` (Vite HMR подхватит новую папку) и добавляет
  тему в `TopicSwitcher`.

`prompts/add-topic.md` — строгий контракт (на основе финального промпта из обсуждения): сначала выбрать
тип темы (DATA_STRUCTURE / CONCURRENCY / JVM_MEMORY / SPRING / TRANSACTION / SQL / HTTP / DESIGN_PATTERN
/ OTHER), затем заполнить папку темы по схеме, **не менять оболочку и runner**, переиспользовать
примитивы, добавить тесты валидации темы.

---

## AI-помощник по теме (`/api/assistant/ask`)

`AssistantDialog`: вопрос пользователя + контекст текущей темы (`explanation.md`, текущий код) →
POST `/api/assistant/ask` → тот же SSE-стриминг через `ClaudeCodeService` (`claude -p`, без записи
файлов) → ответ печатается в окне по мере генерации.

---

## Визуальные примитивы (MVP)

Реализуем generic-компоненты, управляемые данными (не топик-специфичные):
- **ArrayGrid** — массив бакетов 0..N с подсветкой индексов.
- **LinkedNodes** — цепочка узлов в бакете (key→value, hash), показывает коллизии.
- **EventLog** — лента шагов с `description` и кнопкой «Why did this happen?».

Остальные примитивы (TreeNodes, Timeline, ThreadLanes, MemoryBlocks, ObjectGraph, TableView,
StateMachine, RequestFlow) — заглушки с интерфейсом, реализуются по мере добавления соответствующих
типов тем.

---

## Тема HashMap (эталонная)

- `VisualHashMap` (visual-runtime): учебная модель — capacity=16, loadFactor=0.75, реальный расчёт
  bucket index по `hash & (capacity-1)`, цепочки при коллизиях, resize при превышении threshold;
  каждое `put/get/resize/collision` печатает trace-событие. В `explanation.md` честно помечено, что
  это **учебная модель**, повторяющая ключевые идеи `HashMap`.
- `defaultCode`: `Playground` с `VisualHashMap<String,Integer> map = new VisualHashMap<>("map")` и
  несколькими `put/get`.
- `examples/`: basic put/get, коллизия, resize, mutable key, одинаковый hashCode/разный equals,
  нарушенный контракт equals/hashCode, HashMap vs LinkedHashMap, не thread-safe.
- `visualizer.tsx`: рендерит `state` через ArrayGrid + LinkedNodes + EventLog, подсвечивает `highlight`.
- `quiz.yaml` (миссии): «создай коллизию», «вызови resize», «сделай так, чтобы get() вернул null после
  put() (mutable key)», «объясни как на интервью». Миссия засчитывается, когда в `traceEvents`
  появляется нужное событие (напр. `HASHMAP_COLLISION`, `HASHMAP_RESIZE`).

---

## Цикл обучения и геймификация (поверх оболочки)

- **Step-through playback**: Run даёт весь trace; Step/Prev перематывают по шагам; «Explain this step»
  показывает `description`; EventLog синхронизирован с визуализацией.
- **Миссии с целями** (см. выше) — проверяются по trace, а не по тексту.
- Boss-fight/интервью-ответ и оценка — через AI-помощник (после MVP-визуализации; в quiz.yaml
  заложены вопросы).

---

## Этапы реализации

1. **Scaffolding:** монорепо; Gradle-модули `backend` + `visual-runtime`; Vite-проект `frontend`;
   скрипт одновременного запуска; сохранить этот план как `IMPLEMENTATION-PLAN.md`.
2. **Trace + runner:** контракт событий (`traceTypes.ts` + `trace-schema.json`), `Trace.java`,
   `JavaCodeRunner` + `TraceCollector`, эндпоинт `/api/run`. Проверка отдельным JVM-процессом с лимитами.
3. **visual-runtime `VisualHashMap`:** учебная модель, печатающая события.
4. **Frontend shell:** фиксированная раскладка с Monaco, панелями объяснения/примеров/визуализации,
   `TopicSwitcher`, `topicRegistry` через `import.meta.glob`.
5. **Тема HashMap:** `topics/hashmap/*` целиком; примитивы ArrayGrid/LinkedNodes/EventLog;
   visualizer.tsx; примеры; миссии; step-through.
6. **AI-помощник:** `/api/assistant/ask` + `AssistantDialog` со стримингом.
7. **«Добавить тему» через Claude Code:** `prompts/add-topic.md` (контракт),
   `ClaudeCodeService` + `/api/topics/generate` (SSE), `AddTopicDialog` со стримом и статусом.

---

## Verification (как проверить end-to-end)

- **Backend unit:** `JavaCodeRunner` компилирует и запускает `Playground`, возвращает `output`;
  `TraceCollector` корректно отделяет `@@TRACE@@`-события от обычного stdout (тест на коллизию и resize).
- **visual-runtime:** тест `VisualHashMap`: 2 ключа в один бакет → событие `HASHMAP_COLLISION`;
  превышение threshold → `HASHMAP_RESIZE`.
- **Frontend:** `npm run dev` + backend запущены; открыть тему HashMap; нажать Run → визуализация
  показывает бакеты/узлы; Step перематывает шаги; Reset возвращает defaultCode; кнопка примера
  «Collision» → видно 2 узла в одном бакете; миссия «создай коллизию» засчитывается.
- **AI-помощник:** задать вопрос по HashMap → ответ стримится в окно.
- **Добавить тему:** вставить вопрос (напр. про `ArrayList` из
  `eRagaInfosystems-questions.md`) → в окне стримится вывод Claude → появляется `topics/arraylist/` →
  тема доступна в `TopicSwitcher` и открывается в оболочке без правок ядра.

---

## Открытые вопросы на потом (вне MVP)

- Source transformation (`new HashMap<>()` → `VisualHashMap`) перед компиляцией — после MVP.
- Несколько файлов/вкладок и file-tree — версии 2–4.
- Дополнительные типы тем и примитивы (concurrency, Spring lifecycle, transactions, SQL).
- Возможный деплой в облако (тогда песочница → контейнеры, кнопка Claude Code → опциональна).
