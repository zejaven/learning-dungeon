# Code Review — Learning Dungeon / Java Interview Dungeon

Дата: 2026-08-05. Проверены: backend (все пакеты), frontend (все src), gradle, dev.ps1, launcher/*.ps1, конфиги, все 254 темы в topics/ (скриптом), prompts/, plans/.

> Статус: пункты из раздела «Сделать сегодня» (quick wins 1–4, 6, 13 + destroyForcibly/synchronizedList) **исправлены в тот же день**; backend-тесты и production-сборка фронта зелёные. По ходу исправлений добавлены `frontend/tsconfig.build.json` и `frontend/src/vite-env.d.ts`.

## 0. Если данных не хватает

Данных хватает: прочитаны все ключевые файлы backend, frontend, скриптов, gradle, а все 254 темы в `topics/` проверены скриптом на целостность. Не делалось (помечено Confidence там, где важно):

- `config/secret.yml` не читал намеренно (секреты) — проверял только то, как он подключается.
- End-to-end запуски (build-app.ps1, update.ps1, tray) не выполнялись — выводы по скриптам статические.
- Не воспроизводил mojibake в `startup.log` и поведение на путях с кириллицей — Confidence Medium.

## 1. Краткий вердикт

Проект в хорошем состоянии: видно, что типовые ловушки (дедлок на pipe, кодировки Windows, path traversal в assets, SQL-параметризация, CORS, секреты в гите) уже осознанно закрыты — это не часто встречается. Пользоваться можно и дальше. Но есть четыре реальные проблемы, которые чинятся за минуты: backend слушает `0.0.0.0` при наличии endpoint'а выполнения произвольного кода; неограниченное накопление вывода дочерней JVM может уронить backend по OOM; ручная сборка JSON в SSE-статусах ломается на Windows-путях; на фронте нет ни одного Error Boundary, и один кривой trace-event кладёт всё приложение до перезагрузки. Чинить в первую очередь именно их — все четыре фикса суммарно на вечер.

## 2. Топ-15 quick wins

| # | Проблема | Где | Почему плохо | Минимальный фикс | Severity | Effort | Confidence |
|---|----------|-----|--------------|------------------|----------|--------|------------|
| 1 | Backend биндится на 0.0.0.0 + есть `POST /api/run` с произвольным кодом | `application.yml:1` | RCE с любой машины в LAN (CORS не спасает от curl) | `server.address: 127.0.0.1` | High | S | High |
| 2 | Вывод дочерней JVM копится в память без лимита | `JavaCodeRunner.java:262-268` | `while(true) println` за 5 с таймаута качает сотни МБ в heap → OOM backend'а | cap на N строк + флаг `truncated` | High | S | High |
| 3 | JSON в SSE-статусе собирается конкатенацией, экранируются только кавычки | `GenerationTask.java:65-66`, `AiCliService.java:690` | Windows-путь `C:\new\...` в сообщении об ошибке ломает JSON → фронт теряет статус | `mapper.writeValueAsString(Map.of(...))` | High | S | High |
| 4 | Нет ни одного Error Boundary | `main.tsx`, `VisualizationCanvas.tsx:24` | Один кривой trace-event = белый экран всего приложения | class ErrorBoundary (~15 строк) вокруг корня и визуализатора | High | S | High |
| 5 | Чтение stdout AI-CLI без общего дедлайна | `AiCliService.java:371-386` | Зависший CLI вешает HTTP-поток Tomcat навсегда | `waitFor(overallTimeout)` + `destroyForcibly` | High | M | High |
| 6 | Сборка фронта не гоняет typecheck | `frontend/package.json:7` | TS-ошибки не ломают build, уезжают в прод-jar | `"build": "tsc --noEmit && vite build"` | Medium | S | High |
| 7 | Процесс CLI не убивается при обрыве SSE-клиента | `AiCliService.java:744-749` | CLI с bypassPermissions доживает до конца, жгя токены | `finally { if (process.isAlive()) process.destroyForcibly(); }` | Medium | S | High |
| 8 | Гонки stale state: результат `run`/`runSql`/`analyze` применяется к уже другой теме | `engine/store.ts:355,465,503,533` | Чужие события/галочки миссий в текущей теме | после await: `if (get().topic?.id !== id) return;` | Medium | S | High |
| 9 | `selectTopic` не защищён от stale-ответа `fetchTopic` | `engine/store.ts:271-306` | URL — тема B, контент — тема A | монотонный счётчик запроса | Medium | S | High |
| 10 | `topicsError` пишется, но нигде не показывается | `store.ts:31`, `HomeScreen.tsx:336` | При недоступном backend выглядит как «все мои темы удалились» | вывести `topicsError`/`runError` в HomeScreen | Medium | S | High |
| 11 | Нет `statement.setQueryTimeout` в SQL-песочнице | `SqlService.java:87-100` | Тяжёлый запрос вешает HTTP-поток надолго | `st.setQueryTimeout(5)` | Medium | S | High |
| 12 | Кэш резолва CLI бессрочный, включая negative result | `AiCliService.java:604-616` | Установил CLI после старта — «unavailable» до рестарта | кэшировать только успех или TTL 60 с | Medium | S | High |
| 13 | `bootJar` молча собирает jar без UI без `frontend/dist` | `backend/build.gradle:55-59` | Валидный jar, который отдаёт 404 на `/` | `doFirst { if (!file('.../dist/index.html').exists()) throw ... }` | Medium | S | High |
| 14 | SSE-стримы не отменяются (signal поддержан, но не передаётся) | `AssistantDialog.tsx:112`, `BossQuestionForm.tsx:97` | Размонтирование посреди стрима жжёт токены до конца | AbortController + abort в cleanup | Medium | S | High |
| 15 | Monaco без `path` — общая модель и undo-стек между файлами | `EditorPanel.tsx:16-29` | Undo протекает между файлами structural-режима | `path={activePath ?? 'main'}` | Low | S | Medium |

Не вошли в топ-15, но тоже дешёвые: `destroyForcibly` в catch `InterruptedException` раннера, `synchronizedList` для `lines`, `compute()` в `GenerationService.startOrGet`, path-проверка в `TopicRepository` для `examples`/`missionsFile`, добавить `difficulty`/`categoryId` в 3 темы, убрать NUL-байт из `explanation.en.md`.

## 3. Детальные находки

### [P0] Backend доступен из локальной сети, endpoint выполняет произвольный код
- Где: `backend/src/main/resources/application.yml:1-2`, `api/RunController.java:23-26`.
- Что не так: задан только `server.port`, `server.address` нет → биндинг на `0.0.0.0`.
- Почему это проблема: любой хост в LAN (общий Wi-Fi, офис) может `POST /api/run` с произвольным Java-кодом — исполнение с правами пользователя. CORS (`WebConfig.java`) ограничивает только браузеры, curl он не останавливает. Заодно открыты `POST /api/system/update` и `POST /api/bulk/start` (тратит деньги на AI).
- Как воспроизвести: с другой машины в сети `curl -X POST http://<ip>:18080/api/run -d '{"code":"..."}'`.
- Фикс: одна строка в `application.yml`:
  ```yaml
  server:
    address: 127.0.0.1
    port: 18080
  ```
- Что не нужно делать: не нужна авторизация/API-токены — после бинда на localhost атаковать некому.
- Severity: High (для desktop; Windows Firewall может частично смягчить) / Effort: S / Confidence: High.

### [P0] Неограниченное накопление вывода дочерней JVM → OOM backend'а
- Где: `runner/JavaCodeRunner.java:262-268`, `runner/TraceCollector.java:22-23`.
- Что не так: drainer-поток есть (дедлока нет, это хорошо), но все строки складываются в `ArrayList` без лимита. Проверено лично по коду.
- Почему: `while(true) System.out.println(...)` за 5 секунд таймаута накачает сотни МБ в heap backend'а; пара таких запусков — OOM/зависание Tomcat.
- Фикс: кап по строкам с флагом обрезки:
  ```java
  private static final int MAX_LINES = 10_000;
  // в reader:
  if (lines.size() < MAX_LINES) lines.add(line); else truncated = true;
  ```
  и `truncated` в `RunResult`, чтобы UI показал «вывод обрезан».
- Что не нужно делать: не нужен стриминг вывода на фронт.
- Severity: High / Effort: S / Confidence: High.

### [P1] Ручная сборка JSON в SSE-статусе ломается на Windows-путях
- Где: `generation/GenerationTask.java:65-66`, `ai/AiCliService.java:690`. Проверено лично.
- Что не так: экранируются только кавычки (`message.replace("\"","'")`). Сообщение об ошибке включает путь к CLI (`AiCliService.java:352-353`): `C:\new\claude` → `\n` станет переводом строки, `C:\users\...` → невалидный `\u`-escape → `JSON.parse` на фронте падает, статус прогона теряется именно тогда, когда он нужнее всего (при ошибке).
- Как воспроизвести: указать `app.ai.claude.command: C:\utils\claude` (несуществующий) → запустить генерацию → битый status event.
- Фикс: сериализовать через ObjectMapper:
  ```java
  String json = mapper.writeValueAsString(Map.of("status", newStatus, "message", message));
  ```
  (ObjectMapper сделать статическим полем). Заодно закрывает NPE при `message == null`.
- Severity: High / Effort: S / Confidence: High.

### [P1] Нет Error Boundary — один кривой trace-event кладёт всё приложение
- Где: `frontend/src/main.tsx:6-10`, `shell/VisualizationCanvas.tsx:24`, визуализаторы `topics/*/visualizer.tsx`.
- Что не так: визуализаторы делают `event?.state as XState` и дереальны поля без проверки формы. Есть проверка `if (!state)`, но `state.slots === null` или неполный JSON — и рендер бросает. В React 18 необработанная ошибка рендера размонтирует всё дерево: белый экран до перезагрузки. Grep по `ErrorBoundary|componentDidCatch` — 0 совпадений.
- Фикс: один class-компонент ErrorBoundary (15 строк) в двух местах: корень `main.tsx` и обёртка вокруг `<Visualizer>` — тогда кривой event ломает только панель визуализации, а не приложение.
- Severity: High / Effort: S / Confidence: High.

### [P1] Чтение stdout AI-CLI без общего дедлайна — вечное зависание
- Где: `ai/AiCliService.java:371-386`; синхронные вызовы из HTTP-потока: `QuestionController.java:92`, `VersionController.java:96`.
- Что не так: `br.readLine()` блокируется до EOF; `RESULT_TIMEOUT_MINUTES` начинает отсчёт только после закрытия stdout. CLI, зависший с открытым stdout (сетевой сталл, ожидание ввода), вешает поток бесконечно — клиент не получает ни ответа, ни ошибки. В detached-режиме ещё и удерживается `keepAwake` (сон заблокирован).
- Как воспроизвести: подменить `app.ai.claude.command` на скрипт, который молчит и не завершается → `POST /api/questions` висит вечно.
- Фикс: читать stdout в отдельном потоке, основной — `if (!process.waitFor(OVERALL, MINUTES)) { process.destroyForcibly(); sink.status("error", ...); }`.
- Severity: High / Effort: M / Confidence: High.

### [P1] Процесс CLI не убивается при обрыве SSE-клиента
- Где: `ai/AiCliService.java:744-749`.
- Что не так: при дисконнекте `EmitterSink.ai()` бросает `RuntimeException`, которая вылетает мимо catch'ей (ловятся только IOException/InterruptedException) — `process.destroy()` не вызывается. CLI с `--permission-mode bypassPermissions` доживает до конца, тратя токены, handle не reaped.
- Фикс: в `runProcessInner` добавить `finally { if (process.isAlive()) process.destroyForcibly(); process.waitFor(5, SECONDS); }`.
- Severity: Medium / Effort: S / Confidence: High.

### [P2] Гонки stale state на фронте
- Где: `engine/store.ts:271-306` (selectTopic), `:355,465,503,533` (run/analyze/runSql/runTests).
- Что не так: защита от stale-ответа есть только для `fetchProgress` (`store.ts:312`), остальные экшены применяют результат, даже если пользователь уже ушёл в другую тему: чужие trace-события, чужие галочки миссий (только в памяти сессии, на сервер уходит правильный topicId).
- Фикс: та же идиома, что уже есть в файле — после await `if (get().topic?.id !== topic.id) return;`, плюс монотонный счётчик в `selectTopic`.
- Severity: Medium / Effort: S / Confidence: High.

### [P2] Path traversal из содержимого topic.yaml
- Где: `topics/TopicRepository.java:152` (examples), `:167,201` (missionsFile).
- Что не так: `topicDir.resolve("examples").resolve(file)` без проверки containment — `file: "../../../config/secret.yml"` в yaml вернёт содержимое в `GET /api/topics/{id}`. Источник yaml — AI с bypassPermissions, промпт включает произвольный текст пользователя (prompt injection). Контрастирует с образцовой защитой в `TopicAssetController.resolveAsset:73-95`.
- Фикс: после resolve — `if (!p.normalize().startsWith(examplesDir)) continue;` и аналог для `missionsFile`. Туда же: валидировать `id` топика в `getTopic` по паттерну `[a-z0-9-]+`.
- Severity: Medium / Effort: S / Confidence: High (баг), Medium (эксплуатируемость).

### [P2] Компиляция javac in-process без таймаута
- Где: `runner/JavaCodeRunner.java:204-237`.
- Что не так: `compiler.getTask(...).call()` синхронно в HTTP-потоке; `timeout-seconds` покрывает только выполнение. Патологический код (тяжёлая рекурсия типов) висит минутами. Ловится только IOException, нет `-proc:none`.
- Фикс: компиляцию в `ExecutorService` + `Future.get(timeout)`; `catch (Throwable)`; добавить `"-proc:none"` в options.
- Severity: Medium / Effort: M / Confidence: High (что таймаута нет), Medium (что реально повиснет).

### [P2] H2-песочница: нет query timeout, доступны опасные функции
- Где: `sql/SqlService.java:87-100`.
- Что не так: (а) нет `setQueryTimeout` — декартов продукт вешает HTTP-поток; (б) H2 даёт `CREATE ALIAS ... AS '<java code>'`, `FILE_READ`/`FILE_WRITE`, `RUNSCRIPT` — чтение файлов (включая `config/secret.yml`) от имени backend'а. Изоляция от основной БД при этом хорошая (отдельная in-memory БД, `DB_CLOSE_DELAY=0`).
- Фикс: `st.setQueryTimeout(5)`; для (б) — либо принять риск (своя машина), либо простой фильтр по `CREATE ALIAS|RUNSCRIPT|SCRIPT|FILE_READ|FILE_WRITE`.
- Severity: Medium (а) / Low-Medium (б) / Effort: S / Confidence: High.

### [P2] GenerationService: гонка в startOrGet + вечный рост карт
- Где: `generation/GenerationService.java:32-40`, `GenerationTask.java:31`.
- Что не так: get-then-put не атомарен — два одновременных запроса стартуют два CLI-процесса (двойной расход токенов). `byKey`/`byId`/`history` никогда не чистятся — медленный рост памяти при неделях аптайма.
- Фикс: `byKey.compute(key, (k, old) -> old != null && !old.isTerminal() ? old : createAndStart(...))`; терминальные таски вытеснять при повторном старте ключа.
- Severity: Medium / Effort: M / Confidence: High.

### [P2] update.ps1: `$LASTEXITCODE` после вызова build-app.ps1 ненадёжен
- Где: `launcher/update.ps1:41-45`.
- Что не так: `$LASTEXITCODE` выставляют только нативные команды; `throw` в PowerShell-скрипте его не меняет. Сейчас работает «случайно» (перед каждым `throw` падает gradlew/npm). Любой будущий `throw` от командлета → ложный «success»: пользователю скажут, что обновление прошло, а запустится старая сборка.
- Фикс: в build-app.ps1 обернуть тело в `try/catch { ...; exit 1 }`, либо в update.ps1 проверять `$?`.
- Severity: Medium / Effort: S / Confidence: High.

### [P2] Сборка фронта без typecheck
- Где: `frontend/package.json:7-9`.
- Что не так: `"build": "vite build"` — esbuild выкидывает типы, TS-ошибки не ломают сборку и уезжают в прод-jar.
- Фикс: `"build": "tsc --noEmit && vite build"`.
- Severity: Medium / Effort: S / Confidence: High.

### [P3] Мелочи (по одной строке каждая)
- **Гонка на `lines` при таймауте `reader.join()`** — `JavaCodeRunner.java:280-287`: несинхронизированный ArrayList читается, пока reader ещё может писать → `Collections.synchronizedList(...)` + копия. S/High.
- **Нет `destroyForcibly` в catch InterruptedException** — `JavaCodeRunner.java:297-300`: дочерний JVM переживает прерывание. S/High.
- **Temp-директории `ilrun-*`/`ilproj-*` копятся** при kill/OOM — очистка только в happy-path finally; фикс — startup-sweep `%TEMP%/il*`. S/High.
- **Postgres недоступен = приложение падает на старте** (`DbInitializer.java:26`) — пользователь трея увидит только стектрейс; фикс — try/catch с понятным сообщением. S/High.
- **Нет глобального `@RestControllerAdvice`** — RuntimeException даёт дефолтный пустой 500 (stack trace не утекает — дефолты Boot 3 это закрывают). S/High.
- **NPE при `e.getMessage() == null`** — `AiCliService.java:194`, `GenerationTask.java:66`. S/Medium.
- **Негативный кэш резолва CLI навсегда** — `AiCliService.java:604-616` (см. топ-15). S/High.
- **Временные файлы в корне репо** — `ai-prompt-*.txt` (`AiCliService.java:302`), `classify-*`, `regen-*` — засоряют `git status`; фикс — `Files.createTempDirectory` в системном temp. S/High.
- **Гонка MAX+1 версии теории** — `TheoryVersionRepository.java:45-58` → PK violation, второй AI-прогон уже оплачен; фикс — `INSERT ... SELECT COALESCE(MAX(version_no),1)+1` одним statement. S/High.
- **tray.ps1: «Open log» открывает startup.log, а не app.log** (`tray.ps1:131-134`, расходится с README) + startup.log не ротируется. S/High.
- **tray.ps1: занятый порт 18080 чужим приложением** → трей молча открывает браузер на него; фикс — проверить `GET /api/system/status` на «наш» bootId. S/High.
- **Сырой NUL-байт** в `topics/java-data-types/explanation.en.md:5641` — файл считается бинарным; заменить на `'\0'`. S/High.
- **3 темы без `difficulty`/`categoryId`** (`hashmap`, `arraylist-vs-linkedlist`, `heap-generations`) — AGENTS.md требует, тест не проверяет, `TopicRepository.java:66` молча ставит 0; фикс — дописать поля + проверка в TopicContractTest. S/High.
- **`update.flag` не в .gitignore**. S/High.
- **Monaco `path`**, **WordBank лишнее поле `keys` в ответе**, **SortSteps shuffle может выдать решённое упражнение**, **`deleteQuestion` мёртвый код**, **hardcoded "theory available" в CategoryTree.tsx:74** — все S/High.
- **Лог генерации: O(n²) рост** — `generationStore.ts:75-79` `[...t.log, line]` без лимита; фикс — cap хвоста. S/High.
- **localStorage без версионирования, квота глотается молча** — при переполнении пользователь теряет код без намёка; фикс — разовое предупреждение. S/High.
- **JDK из `tools/` нигде не используется** — скрипты смотрят только JAVA_HOME/PATH; фикс — fallback в dev.ps1/build-app.ps1. S/Medium.
- **Кириллица в startup.log может быть mojibake** (ANSI в PS5.1). S/Medium.

## 4. Простые патчи

`backend/src/main/resources/application.yml`:
```diff
 server:
+  address: 127.0.0.1
   port: 18080
```

`GenerationTask.java:65-66` (и аналогично `AiCliService.statusJson`):
```diff
-String json = "{\"status\":\"" + newStatus + "\",\"message\":\""
-        + message.replace("\"", "'") + "\"}";
+String json;
+try {
+    json = MAPPER.writeValueAsString(Map.of("status", newStatus,
+            "message", String.valueOf(message)));
+} catch (JsonProcessingException e) {
+    json = "{\"status\":\"" + newStatus + "\"}";
+}
```

`JavaCodeRunner.java` — кап вывода и зачистка процесса:
```diff
-List<String> lines = new ArrayList<>();
+List<String> lines = Collections.synchronizedList(new ArrayList<>());
 ...
-while ((line = br.readLine()) != null) {
-    lines.add(line);
-}
+while ((line = br.readLine()) != null) {
+    if (lines.size() < MAX_LINES) lines.add(line);
+}
 ...
 } catch (InterruptedException e) {
     Thread.currentThread().interrupt();
+    process.destroyForcibly();
     return RunResult.failure("Run interrupted.");
 }
```

`frontend/package.json`:
```diff
-"build": "vite build"
+"build": "tsc --noEmit && vite build"
```

Error Boundary (новый файл `frontend/src/shell/ErrorBoundary.tsx`):
```tsx
import { Component, type ReactNode } from 'react';

export class ErrorBoundary extends Component<
  { children: ReactNode; fallback?: ReactNode },
  { error: Error | null }
> {
  state = { error: null as Error | null };
  static getDerivedStateFromError(error: Error) { return { error }; }
  render() {
    if (this.state.error) {
      return this.props.fallback ?? (
        <div style={{ padding: 16 }}>
          Something went wrong. <button onClick={() => this.setState({ error: null })}>Retry</button>
        </div>
      );
    }
    return this.props.children;
  }
}
```
Использовать в `main.tsx` вокруг `<App/>` и в `VisualizationCanvas.tsx:24` вокруг `<Visualizer event={currentEvent}/>` (с `key={currentEvent}`-сбросом при смене события).

## 5. Большие проблемы

**«Песочница» выполнения кода — не песочница.**
Пользовательский код в дочерней JVM может: порождать процессы (внуки переживают `destroyForcibly`, `JavaCodeRunner.java:279`), читать любые файлы (включая `config/secret.yml` — пароль БД печатается в output), писать на диск, заполнять `%TEMP%`. SecurityManager в Java 21 удалён, альтернативы «из коробки» нет.
- Почему нельзя совсем игнорировать: учебный инструмент предполагает эксперименты с кодом, и случайный fork-bomb в примере студента — вопрос времени.
- Минимальный временный фикс (S, сделать сейчас): короткое предупреждение в UI рядом с кнопкой Run + документация в README, что код выполняется с полными правами.
- Нормальное решение (L, отложить): запуск дочерней JVM через Windows Job Object с `KILL_ON_JOB_CLOSE` (убивает всё дерево) — это JNA/FFM-интеграция, полдня-день работы.
- Делать сейчас не нужно: для однопользовательского инструмента риск «пользователь вредит сам себе».

**AI-генерация: нет валидации результата перед статусом "done".**
Битый `learning-atoms.json` ловится lenient-лоадером с ретраем, ручные изменения не затираются — но таск помечается "done", даже если файл невалиден (осознанный trade-off, помечен в javadoc). Минимальный шаг (S): в finally таска проверять наличие/парсабельность файла и менять статус на "error" с понятным сообщением. Делать можно в любой момент, не блокирует.

## 6. Что выглядит нормально

- **JavaCodeRunner**: stdout/stderr слиты и дренятся отдельным потоком (дедлока нет), `destroyForcibly` при таймауте есть, дочерней JVM явно выставлены UTF-8 кодировки (классическая Windows-проблема решена), `-Xmx128m`, path traversal в `runProject`/`compileFiles` корректно отсечён.
- **Command injection отсутствует**: все внешние вызовы — ProcessBuilder со списком аргументов без shell; промпт передаётся через stdin/файл, не через argv; keep-awake — статический скрипт через `-EncodedCommand`.
- **Секреты**: `secret.yml` в .gitignore, optional-импорт, пароль не логируется, stack trace в ответы API не попадают (дефолты Boot 3), токен Claude идёт только на api.anthropic.com.
- **SQL**: все запросы параметризованы, конкатенации нет (проверено грепом); H2-изоляция с уникальными именами БД корректна.
- **TopicAssetController.resolveAsset** — образцовая анти-traversal защита.
- **Целостность контента**: все 254 темы проверены скриптом — ни одной битой ссылки на файлы, `id` == имя папки везде, EN/RU explanation непустые, все `topic:`/`catalog:` cross-link'и живые, 233 learning-atoms валидны.
- **Frontend**: XSS-вектора в react-markdown нет (rehype-raw отсутствует, urlTransform санитизирует), Mermaid с `securityLevel:'strict'` и fallback на сырой код, SSE-парсер корректен, loading/error-состояния в основном есть, useEffect cleanup'ы на месте, i18n без пропущенных ключей.
- **Gradle/сборка**: версии согласованы (wrapper 8.10.2 ↔ Boot 3.3.4 ↔ toolchain 21), раскладка `frontend/dist` в `static/` совпадает со static-locations.
- **dev.ps1** идемпотентен с защитой от reuse PID; **tray.ps1** корректно дренирует pipe JVM, делает tree-kill, честно диагностирует падения; **update.ps1** при провале возвращается на прежнюю сборку.
- **prompts/ и plans/** — чистый Markdown, никакого исполняемого кода.

## 7. Итоговый план действий

**Сделать сегодня (все фиксы S, суммарно ~2 часа):**
1. `server.address: 127.0.0.1` в application.yml.
2. Кап на вывод в JavaCodeRunner + `truncated` в RunResult.
3. JSON через ObjectMapper в GenerationTask/AiCliService.
4. Error Boundary на корень и визуализатор.
5. `tsc --noEmit` в build фронта.
6. `destroyForcibly` в InterruptedException раннера + synchronizedList.
7. bootJar: fail-fast без `frontend/dist`.

**Сделать на этой неделе:**
1. Общий дедлайн на AI-CLI процессы (waitFor + destroyForcibly) и kill при обрыве SSE.
2. Stale-guards в store (run/runSql/analyze/selectTopic) + показ `topicsError` в UI.
3. `setQueryTimeout` в SqlService + фильтр опасных H2-функций.
4. Path-проверка в TopicRepository для examples/missionsFile.
5. update.ps1: надёжная передача кода ошибки из build-app.ps1.
6. Компиляция javac с таймаутом + `-proc:none`.
7. Мелочи из P3 (темы без difficulty, NUL-байт, кэш CLI, temp-файлы, tray-логи).

**Можно отложить:**
1. Job Object для дерева дочерних процессов (L).
2. Очистка завершённых GenerationTask и кап лога генерации (актуально при неделях аптайма).
3. Startup-sweep `%TEMP%/il*`.
4. Версионирование localStorage — при следующей смене формата.
5. Валидация результата генерации перед статусом "done".

**Не делать вообще, чтобы не переусложнить:**
1. Авторизацию/API-токены — после бинда на localhost не нужны.
2. Стриминг вывода кода на фронт вместо буфера с капом.
3. Полноценную sandbox-изоляцию (контейнеры/VM) для учебного runner'а.
4. Миграции через Flyway/Liquibase — DbInitializer со своими идемпотентными DDL справляется.
5. Замену zustand/Vite/Gradle/H2 — текущий стек адекватен задаче.
