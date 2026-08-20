# AGENTS.md

Instructions for AI coding agents working in this repository.

## Project Snapshot

This is a local interactive Java interview-prep app ("Java Interview Dungeon").
It turns interview questions into self-contained learning topics with theory,
practice, missions, visualizations, SQL exercises, coding challenges, a
Duolingo-style micro-actions lesson mode, and Claude Code assistance.

The architecture is intentionally split:

- `backend/`: Spring Boot API on Java 21. It serves topics, runs Java snippets in
  a child JVM, analyzes structural topics, runs SQL exercises in disposable H2
  databases, tracks progress in PostgreSQL, serves and generates micro-action
  lessons, and calls the Claude Code CLI.
- `frontend/`: React 18 + TypeScript + Vite app. It uses Zustand stores, Monaco,
  Mermaid, and topic visualizers imported dynamically from `topics/*`.
- `visual-runtime/`: dependency-free Java library placed on the classpath of
  learner code. It emits `@@TRACE@@{json}` events for the frontend to replay.
- `topics/<id>/`: content plugins. A topic folder defines metadata, explanations,
  starter/example code, missions, boss-fight questions, an optional
  `learning-atoms.json` micro-lesson, and sometimes a visualizer.
- `prompts/`: strict contracts for generated topics. Treat these as source of
  truth when adding or repairing topics.

This is a local personal tool. The Java runner has a timeout and heap cap, but it
is not a production-grade sandbox.

### Domains (subject areas)

The app is multi-domain: a "domain" is a subject area above categories, and the
home screen shows one domain at a time. Current domains: `java` (the original
interview prep, with the static question catalog), `ndm` (the IT8516
"Network Design and Management" university course, imported as theory topics),
and `qa` (QA interview prep, theory topics parsed from the user's question
base; ids prefixed `qa-`).

- A topic declares its domain via `domainId` in `topic.yaml`. An absent
  `domainId` means `java`, so all legacy topics are unaffected.
- The domain list lives in `frontend/src/domains.ts` (id, bilingual title,
  icon); the active domain is persisted by
  `frontend/src/engine/domainStore.ts` (localStorage, like the language).
- The header renders domain-switcher pills; the app title is the active
  domain's title. A deep link to a topic of another domain (`#/q/ndm-...`)
  auto-switches the domain.
- Only the `java` domain has the static `CATALOG`, manual questions, and the
  "Add question" button; other domains build their category tree purely from
  their topics' `categoryId`/`categoryName`.
- "Add topic" works in every domain: the request carries `domainId`, and
  `TopicPromptBuilder.appendDomain` adds a block that overrides the Java-shaped
  `prompts/add-topic.md` — it sets `domainId`, requires the `<domain>-` id
  prefix, forces `mode: theory`, suppresses the Java category/mode heuristics,
  and lists the categories that domain already uses. Cross-link context is
  filtered to the same domain, and the generation task is keyed
  `add-topic:<domainId>` so two domains cannot attach to each other's run.
- Progress tables still key by `topic_id` only — topic ids are a single global
  namespace, so new domains use an id prefix (e.g. `ndm-`) by convention.
- The global review pool is filtered to the active domain on the frontend; the
  backend review endpoints stay domain-agnostic.
- Non-programming domains use `mode: theory` topics (explanation + Boss Fight);
  their explanations may embed images from `topics/<id>/images/`, served by
  `GET /api/topics/{id}/assets/**`.

### Learn by micro-actions

A topic's default learning path is not its long-form explanation but a
Duolingo-style lesson generated from it: `topics/<id>/learning-atoms.json` holds
8-15 "knowledge atoms," each with prediction-first `discovery` exercises and
reinforcement `practice` exercises (multiple choice, true/false, fill-blank,
word-bank, sort-steps, match-pairs). The lesson UI derives a sequence of units
(discovery → practice → boss) rendered as a horizontal, sequentially-unlocked
circle track; Boss Fight units reuse the topic's existing `bossFight` questions
and AI-grading flow. Progress is scoped by a hash of `learning-atoms.json`, so
regenerating the file resets discovery/practice progress but not Boss Fight
progress (keyed by stable question ids). Practice exercises of a fully
completed lesson join a global, cross-topic review pool (`#/review`) with
wrong-answer requeueing. The full design, DB schema, and a worked example live
in `plans/learn-by-micro-actions.md`. The long-form explanation still exists as
a "📖 Reference" view; it is not the primary path once a lesson exists.

## Prerequisites

- JDK 21. The backend uses the JDK compiler API, so a JRE is not enough.
- Node 18+ and npm.
- Claude Code CLI on `PATH` for Add Topic, Ask AI, style regeneration, and usage
  meter features.
- Local PostgreSQL for progress persistence when running the full app. Copy
  `config/secret.example.yml` to `config/secret.yml` for local credentials.

Never commit `config/secret.yml`, `.dev-pids`, logs, build outputs, `node_modules`,
or `frontend/dist`.

## Common Commands

Use the Windows wrapper in this workspace:

```powershell
.\dev.ps1
```

Starts a fresh backend on `http://localhost:18080` and frontend on
`http://localhost:15173`, after building `visual-runtime`. These non-default
ports keep 8080/5173 free for the user's other local projects.

Manual startup:

```powershell
.\gradlew.bat :visual-runtime:jar
.\gradlew.bat :backend:bootRun
```

```powershell
Set-Location frontend
npm install
npm run dev
```

Packaged (tray) run — a single JVM on `http://localhost:18080` serving the API and
the built frontend (bundled into the jar under `static/`), with no Vite. Build
once, then launch from the tray:

```powershell
launcher\build-app.ps1
```

This is the mode the in-app settings gear self-updates: the tray supervisor
(`launcher/tray.ps1`) launches the jar with `-Dapp.launcher=tray`, and on a
flagged exit hands off to `launcher/update.ps1` to rebuild and relaunch (see
Backend Notes — the `system` package). `launch.vbs` runs `launcher/tray.ps1`
straight from disk, so edits to `tray.ps1`/`update.ps1` apply on the next launch
with no rebuild; changes to backend or frontend sources need a fresh
`build-app.ps1` because they are baked into the jar.

Validation commands:

```powershell
.\gradlew.bat :visual-runtime:test
.\gradlew.bat :backend:test
```

Focused topic checks:

```powershell
.\gradlew.bat :backend:test --tests "*TopicContractTest"
.\gradlew.bat :backend:test --tests "*TopicExamplesTest"
.\gradlew.bat :backend:test --tests "*LessonAtomsContractTest"
.\gradlew.bat :backend:test --tests "*LessonUnitsTest"
```

Frontend build check:

```powershell
Set-Location frontend
npm run build
```

There is currently no frontend lint/test script beyond `npm run build`.

## Verification Expectations

- Backend/API/topic changes: run `.\gradlew.bat :backend:test`.
- `visual-runtime/` changes: run `.\gradlew.bat :visual-runtime:test` and usually
  `.\gradlew.bat :backend:test`, because topic examples use the runtime jar.
- Frontend changes: run `npm run build` from `frontend/`.
- New or edited trace topics: run both `:visual-runtime:test` and `:backend:test`
  unless you only changed prose.
- New structural, theory, SQL, or challenge topics: at minimum run
  `.\gradlew.bat :backend:test`.
- New or edited `learning-atoms.json`: run `.\gradlew.bat :backend:test`
  (`LessonAtomsContractTest` validates the file strictly).

If a command cannot be run, say exactly why and what remains unverified.

## Codebase Rules

- Prefer existing patterns and module boundaries. Do not redesign the shell,
  runner, topic system, or build setup unless the task explicitly requires it.
- Keep Java source, Java comments, identifiers, and technical tokens in English.
- The set of content languages lives in one registry per side —
  `backend/.../lang/ContentLanguages.java` and `frontend/src/languages.ts` —
  and adding a language is a one-line change there. Nothing else may hardcode a
  language pair: read text with `Localized.label()` / `tl()` for short labels and
  `Localized.get()` / `tlStrict()` for body content, which reports a missing
  translation instead of silently substituting another language.
- A topic carries content in the languages it declares in `languages:`, which is
  required in every topic.yaml — see Topic Authoring. UI chrome strings
  (`i18n.ts`) are translated for the languages flagged `ui: true`.
- Files contain UTF-8 Cyrillic content. Preserve UTF-8 and avoid bulk rewrites
  caused only by console encoding/mojibake.
- Use stable, deterministic data for examples, tests, SQL seeds, and trace states.
- Keep changes scoped. Ignore unrelated dirty worktree changes.

## Backend Notes

- Main package: `com.interviewlearning`.
- `server.address: ${app.bind-address:127.0.0.1}` in `application.yml` keeps the
  run endpoint off the LAN by default, and it binds the IPv4 loopback ONLY.
  Anything that talks to the backend from outside a browser (launcher probes,
  the Vite `/api` proxy, scripts) must address it as `127.0.0.1`: on Windows
  `localhost` resolves to `::1` first, and connecting there stalls for ~2s
  rather than failing over. Binding elsewhere is only safe together with the
  `remote` package below.
- `server.forward-headers-strategy: framework` is what makes the app usable
  behind Tailscale Serve. That proxy terminates TLS, so the browser sends
  `Origin: https://<host>` while the app sees plain http; without the forwarded
  headers Spring calls that cross-origin and answers every POST with 403
  "Invalid CORS request". Reads keep working (browsers send no Origin on
  same-origin GETs), which is what made it look like the phone was fine while
  silently recording nothing. Do not narrow this without testing a write from
  the phone.
- Controllers live mostly under `backend/src/main/java/com/interviewlearning/api`.
- Topic loading is in `topics/TopicRepository`. It rereads `topics/` from disk on
  requests so new folders appear without a backend restart. It reads `domainId`
  from `topic.yaml` (default `java`) into `TopicSummary`/`TopicDetail`; when
  adding a record component, also update the positional copy in
  `api/TopicController.list()` and the mirrored TS types in
  `frontend/src/engine/traceTypes.ts` (TS fails silently).
- `api/AssistantController` picks its prompt wording per domain (`DomainVoice`
  map: mentor role, grader intro, example technical terms). A new domain that
  should not sound like a Java interviewer needs an entry there.
- `api/TopicAssetController` serves `GET /api/topics/{id}/assets/{*path}` from
  the topic folder for explanation images: image-extension allowlist plus
  path-traversal checks (unit-tested in `TopicAssetControllerTest`). Keep the
  allowlist tight — it is what stops quiz.yaml/harness/ from leaking.
- The runtime loader is lenient, but `TopicContractTest` is strict and should be
  trusted when fixing YAML or topic shape.
- `JavaCodeRunner` compiles code with the JDK compiler API, runs it in a child JVM,
  and extracts `@@TRACE@@` lines through `TraceCollector`.
- Structural topics compile multi-file Java projects and use JavaParser in
  `StructureAnalyzer` to produce class graphs.
- SQL topics run against fresh in-memory H2 databases in PostgreSQL mode. Mission
  results compare user query output to `expectedSql`.
- Challenge topics compile learner `Solution.java` with hidden `harness/` files
  and report cases via `visual.TestKit.expect`.
- The `lesson` package (`backend/src/main/java/com/interviewlearning/lesson/`)
  is the "Learn by micro-actions" domain:
  - `LearningAtomsRepository` reads `topics/<id>/learning-atoms.json` per
    request (same no-restart philosophy as `TopicRepository`) and returns it
    with a sha-256 hash of the file bytes (`atomsHash`) that scopes progress.
  - `LessonUnits.derive(...)` turns atoms + boss-fight questions into the
    ordered unit sequence (discovery per atom, practice chunked round-robin
    across atoms in groups of `PRACTICE_CHUNK` = 5, one boss unit per
    question). This algorithm is mirrored in
    `frontend/src/engine/lessonUnits.ts` — a change to one requires the same
    change to the other, and `LessonUnitsTest` pins the exact output.
  - `LessonProgressRepository` and `ReviewRepository` persist answers, unit
    completion, lesson completion, and the global review pool/session in
    PostgreSQL (tables added in `progress/DbInitializer`: `lesson_exercise_answer`,
    `lesson_unit_progress`, `lesson_progress`, `review_pool`, `review_session`).
  - `api/LessonController` serves atoms/state/answer/unit-complete;
    `api/ReviewController` serves the review pool/session; `api/LessonGenController`
    starts atoms generation via the existing detached-SSE `GenerationService`
    (task key `atoms:<topicId>`, `AiTask.GENERATE_ATOMS`), reusing
    `GET /api/topics/generate/{taskId}/events` for streaming.
  - Grading of discovery/practice exercises is deterministic and happens on the
    frontend; these endpoints only persist results (same trust model as trace
    mission completion). Boss Fight units reuse the existing AI-graded
    `POST /api/assistant/evaluate` flow and `boss_fight_answer` persistence
    unchanged — Boss Fight progress is not scoped by `atomsHash`.
- The `system` package (`backend/src/main/java/com/interviewlearning/system/`)
  is the settings-gear self-update/restart domain:
  - `SystemService` detects deployment capabilities (`supervised` — set only
    when the tray launcher passes `-Dapp.launcher=tray`; `canRebuild` — the
    source tree is present next to the app; `canPull` — a git checkout with an
    upstream), periodically shells out to `git fetch` + `git rev-list --count
    HEAD..@{u}` to cache how many commits the upstream is ahead, and holds a
    random per-process `bootId`.
  - "Update"/"Restart" cannot be done in-process (a running JVM holds its own
    jar open on Windows), so `requestUpdate` writes a `launcher/update.flag`
    sentinel and exits the JVM; the tray supervisor (`launcher/tray.ps1`) sees
    the flagged exit and hands off to `launcher/update.ps1`, which optionally
    `git pull`s, runs `launcher/build-app.ps1`, and relaunches. The frontend
    polls `GET /api/system/status` and reloads once `bootId` changes.
  - `update.ps1` must never wait on anything a build leaves behind. A Gradle
    daemon outlives the build by hours and inherits its handles, so both
    `& child *>> $log` (waits for end-of-stream) and `Start-Process -Wait`
    (waits for the process AND its descendants) hang forever, one line short of
    restarting the app — which is exactly how the Restart button broke. Run each
    step through `Invoke-Step`, which redirects with cmd and waits on that one
    process. For the same reason the relaunch happens BEFORE any failure dialog:
    a modal box in a process with no visible window is invisible to the user and
    would keep the app down until someone dismisses it.
  - `api/SystemController` serves `GET /api/system/status` and
    `POST /api/system/update {pull}` (the latter 409s unless `supervised` and
    the requested capability is present). No PostgreSQL state is involved.
  - `KeepAwakeService` stops Windows from sleeping while AI runs are active
    (refcounted around `AiCliService.runProcess`): it holds a child PowerShell
    process asserting `SetThreadExecutionState(ES_CONTINUOUS |
    ES_SYSTEM_REQUIRED)` and releases it ~90s after the last run ends. Display
    sleep stays allowed; non-Windows is a no-op. Every AI code path must keep
    funneling through `runProcess` for this to hold.
- The `remote` package (`backend/src/main/java/com/interviewlearning/remote/`)
  is the only thing standing between this app and a remote shell, because
  `/api/run`, `/api/sql`, `/api/challenge` and `/api/analyze` execute what the
  caller sends and the generation endpoints drive an AI CLI that writes files:
  - `RemoteAccessFilter` refuses every non-loopback request unless
    `app.remote.mode` is set (`direct` = the server itself is bound to the LAN;
    `proxied` = a local proxy such as Tailscale Serve or the Vite dev server
    forwards to 127.0.0.1). The token arrives once as `?token=...` and then
    lives in an HttpOnly cookie, so the SPA's same-origin fetches carry it with
    no frontend changes. `X-Forwarded-For` is trusted in `proxied` mode ONLY —
    a directly exposed server would let anyone forge it.
  - Even an authenticated remote client cannot reach the code-execution paths
    unless `app.remote.allow-code-execution` is on. Adding a new endpoint that
    compiles, executes or shells out means adding it to
    `CODE_EXECUTION_PATHS`.
  - Settings live in `config/secret.yml` (`remote.*`, `app.bind-address`),
    written by `launcher/remote.ps1` into a managed block. The policy is a pure
    function (`RemoteAccessFilter.decide`) pinned by `RemoteAccessFilterTest` —
    change the tests deliberately, never to make a new case pass.

## visual-runtime Rules

`visual-runtime` must stay dependency-free. Its jar is placed on the classpath of
learner-written code, so do not add third-party runtime dependencies there.

When adding or changing a `visual.Visual*` model:

- Emit trace events through `Trace.event(event, descEn, descRu, highlight, state)`.
- Use `LinkedHashMap` or deterministic list ordering for trace state.
- Keep event type strings technical and untranslated.
- Make descriptions bilingual.
- Add or update a matching test under `visual-runtime/src/test/java/visual/`.

## Frontend Notes

- Vite alias: import app code from topic visualizers with `@app/...`.
- Topic visualizers are discovered by
  `import.meta.glob('../../../topics/*/visualizer.tsx')` in
  `frontend/src/engine/topicRegistry.ts`.
- The Vite dev server allows importing files outside `frontend/` so topic
  visualizers can live under `topics/`.
- Global app state is primarily in `frontend/src/engine/store.ts`.
- Domains: `buildCatalog(topics, manualQuestions, domainId)` in
  `frontend/src/catalog.ts` builds one domain's tree (`java` seeds the static
  `CATALOG`; other domains start empty and invent categories from topic
  metadata). Use `buildAllCatalogs(...)` for domain-agnostic lookups (route
  resolution in `App.tsx`/`HomeScreen`, review grouping). The review pool is
  scoped to the active domain in `reviewStore.ts` (`activeDomainFilter`).
- `shell/Markdown.tsx` takes an optional `assetBase` prop that resolves
  relative image paths (`images/x.png`) against
  `/api/topics/<id>/assets`; the theory panel passes it, dialogs don't.
- Localized labels should use `tl(...)`, `ui(...)`, and `useLang` from `@app/i18n`.
- Visualizers are pure React components of the current trace event and language.
  They must not call the backend or run Java.
- The lesson mode lives in `frontend/src/engine/lessonStore.ts` (current lesson,
  unit/exercise navigation, saved answers keyed by exercise id so revisiting a
  unit restores what was answered) and `frontend/src/engine/reviewStore.ts`
  (global review session). Types are in `lessonTypes.ts`; grading is
  deterministic and shared between the lesson and review via `grading.ts`; unit
  derivation is mirrored from the backend in `lessonUnits.ts` (see Backend
  Notes — keep both in sync).
- Lesson UI components live under `frontend/src/shell/lesson/`: `LessonPanel`
  (default view of a topic's right panel once `TopicDetail.hasAtoms` is true),
  `UnitTrack` (the circle row; locked/current/done, and done-with-a-mistake
  renders red with `✗` via `unitHasMistake`), `ExerciseCard` (dispatches to
  `exercises/*` per exercise type), and `BossFightUnit`. The Boss Fight
  grading form itself is shared with the standalone dialog via
  `frontend/src/shell/BossQuestionForm.tsx` — do not duplicate that logic.
- The completion fireworks (`shell/Celebration.tsx`) are raised only by
  `celebrateTopic()` in `store.ts`, which fires once per topic per session:
  finishing a topic is reported twice, by the boss answer response and by the
  lesson recompute that follows it, so the trigger must be idempotent rather
  than a flag flip. A topic that has a lesson celebrates from `finishLesson`
  in `lessonStore.ts` when the LESSON completes; one without a lesson from
  `BossQuestionForm` when the last boss question passes. Both are gated on
  "it was not already finished when it was opened" — which is why `loadLesson`
  takes the boss half of lesson completion from the server instead of deriving
  it from boss results that are still being fetched.
- `ReviewScreen` (`#/review`) reuses `ExerciseCard` outside the lesson context.
- The settings gear (`frontend/src/shell/SettingsButton.tsx`, in every screen's
  header) opens `SettingsDialog` (Update = git pull + rebuild + restart; Restart
  = rebuild from local files + restart). `frontend/src/engine/systemStore.ts`
  polls `GET /api/system/status` for the commits-behind badge and drives the
  update: it POSTs, then polls for a changed `bootId` and reloads. `UpdatingOverlay`
  covers the screen while the backend rebuilds (see Backend Notes — the `system`
  package). Buttons disable themselves per the status capabilities.
- Router (`frontend/src/engine/router.ts`) routes: `#/q/<id>` (lesson if one
  exists, else theory), `#/q/<id>/theory` (forces the reference explanation),
  `#/q/<id>/practice` (trace/structural/sql/challenge workspace — the "Go to
  practice" button is hidden for `trace` topics once a lesson exists, but the
  route still works), `#/review` (global review).
- Colour tokens carry a ROLE, not just a hue, and the light theme is where
  getting that wrong shows: `--accent` paints a surface (a filled button, a
  progress bar) so it must stay light, `--accent-ink` writes or draws on one so
  it must stay dark, and `--mark-*` are the vivid variants for pure decoration
  (difficulty stars), which only need 3:1 rather than the 4.5:1 of text. Using a
  single accent for all three is what made the light theme muddy. Never hardcode
  a palette value in a rule — a literal `rgba(86, 211, 100, …)` is a dark-theme
  green that silently follows the user into the light theme.
- Mobile/touch profile: the same components in one column, driven entirely by
  the media queries at the end of `frontend/src/styles.css` — there is no
  second UI and no JS width check. Rules:
  - A screen whose desktop shape is "list + detail" (home, review) puts
    `has-detail` on its `.home-main` container; under 860px that hides
    `.home-tree-panel` or `.home-detail-panel` so exactly one is on screen.
    The home screen derives it from the selected question, the review screen
    from a `☰` toggle.
  - Elements that belong to only one profile use `.mobile-only` /
    `.desktop-only` instead of conditional rendering.
  - The workspace `.main` stacks into one scrolling column; Monaco needs an
    explicit `.editor-wrap` height there because it fills its container.
  - Any new interaction must work without a mouse: HTML5 drag-and-drop never
    fires on touch, which is why `SortSteps` also has arrow buttons. Inputs
    stay >= 16px on phones or iOS zooms the page on focus, and full-viewport
    boxes use `dvh` plus `env(safe-area-inset-*)`.
- PWA / offline (`frontend/src/engine/offline/`, `offlineStore.ts`, and the
  `VitePWA` block in `vite.config.ts`). The app installs to a phone home screen
  and keeps working with the PC switched off. Rules that are easy to break:
  - The service worker only exists in a **secure context**: `https://` (what
    Tailscale Serve provides) or `localhost`. Over plain `http://<lan-ip>` there
    is no worker and no offline — the settings dialog says so instead of
    offering buttons that would do nothing. `npm run dev` generates no worker
    either; use `npm run preview` (port 4173, same `/api` proxy) to exercise it.
  - `runtimeCaching` matchers are inlined into the generated worker **as source
    text**: a matcher that references anything from `vite.config.ts` throws at
    match time and silently stops serving from the cache. Keep every matcher
    self-contained.
  - Cache names are a contract between `vite.config.ts` (what the worker reads)
    and `engine/offline/cache.ts` (what "Download this domain" writes). Renaming
    one without the other leaves a full cache nothing reads.
  - `?token=...` is in `navigateFallbackDenylist`: that navigation must reach
    `RemoteAccessFilter` to get the cookie, and a precached shell would 401 on
    every call instead.
  - Only pure-persistence writes may go through `engine/offline/outbox.ts`
    (lesson/review answers — grading is deterministic and already done on the
    client). Anything whose response the UI needs — AI grading, generation,
    running code — stays online-only and must fail loudly instead of queueing.
    `lessonStore.loadLesson` replays the queue over the server state, so an
    answer given offline survives a cold start.
  - The outbox writes to IndexedDB BEFORE it touches the network, every request
    carries a timeout, and 401/403 are retryable rather than dropped. Each of
    those three rules exists because breaking it silently destroyed a full
    lesson of progress: an unreachable host does not refuse a connection, it
    swallows the request, so a queue that only fills on `fetch` rejection stays
    empty while the answers live in a tab that is about to be reloaded; and a
    permission error is about who is asking, not about what was sent.
  - Online-ness means "the backend answered", not `navigator.onLine`: the normal
    case is a phone happily on Wi-Fi while the PC is asleep. `offlineStore`
    combines both, fed by outbox results and the `/api/system/status` poll.

## Topic Authoring

Before adding or significantly editing a topic, read:

- `prompts/topic-contract.md`
- `prompts/add-topic.md`
- `prompts/mermaid-guide.md` when editing explanations with diagrams
- `prompts/generate-learning-atoms.md` when authoring or editing a topic's
  `learning-atoms.json`

The topic `id` in `topic.yaml` must match the folder name. New topics must include
`categoryId`, `difficulty`, and usually `assistantExample`. Topics outside the
Java domain also set `domainId` (kebab-case; enforced by `TopicContractTest`)
and prefix their id with the domain (e.g. `ndm-scalability`) so ids stay
globally unique. An optional `order:` (e.g. the lecture number; ndm topics use
1-16) sorts entries within a category before difficulty and puts invented
categories in first-lecture order; topics without it (all Java topics) keep
sorting by difficulty. Explanations may embed images: put files under
`topics/<id>/images/` and reference them with relative links
(`![...](images/x.png)`) — `TopicContractTest` fails on links to missing files.

Known modes (the required explanation files are one per declared language —
both `explanation.en.md` and `explanation.ru.md` unless `languages:` narrows it):

- `trace` (default): runnable `Playground` examples plus trace visualizer.
  Required: `topic.yaml`, `explanation.en.md`, `explanation.ru.md`,
  `examples/*.java`, `visualizer.tsx`, `trace-schema.json`, `quiz.yaml`.
- `structural`: design-pattern/class-relationship practice. Required:
  `topic.yaml`, explanations, `starter/*.java`, `quiz.yaml`. Omit examples,
  visualizer, trace schema, and visual-runtime models.
- `theory`: explanation plus Boss Fight only. Required: `topic.yaml`,
  explanations, `quiz.yaml` with `bossFight`. Omit missions and editor/runtime
  files.
- `sql`: seeded SQL query playground. Required: `topic.yaml`, explanations,
  `starter/schema.sql`, `starter/query.sql`, `quiz.yaml`.
- `challenge`: coding kata. Required: `topic.yaml`, explanations,
  `starter/Solution.java`, `harness/Main.java`, `quiz.yaml`.

Any mode may additionally have `learning-atoms.json`, generated from the
topic's existing explanation (normally via the app's "✨ Generate lesson"
button, which drives `AiTask.GENERATE_ATOMS` and
`prompts/generate-learning-atoms.md`; hand-authoring is fine too). It is
optional — its presence is what makes `TopicDetail.hasAtoms` true and switches
the topic's default view from theory to the micro-actions lesson. Rules when
authoring one by hand: 8-15 atoms, each with >= 1 `discovery` and >= 2
`practice` exercise; every id (atoms and exercises) is kebab-case and unique
across the whole file; every exercise has bilingual `prompt` and `feedback`
(short one-line verdicts); every discovery exercise has a `reveal` teaching card
(Markdown, may embed a fenced ```mermaid``` diagram) that defines new terms,
gives the "why", and shows a worked example/diagram — this is where the concept
is actually taught, so terms must be introduced in a `reveal` before any later
exercise tests them; each atom's `practice` ramps recognition → arrange →
produce (fill_blank supports multiple `blanks`); the LAST atom is a
`"capstone": true` synthesis atom whose practice the engine renders as a final
block (unit kind `capstone`, ids `c1`..) right before the Boss Fight; exercises
are neutral in tone
(ignore any generation-style analogies baked into the source explanation) and
must be answerable standalone, since practice exercises are later reused out of
context in the global review pool. `LessonAtomsContractTest` enforces the exact
shape per exercise type — trust it over hand-written JSON.

For all topics:

- Every visible string must exist in every language the topic declares.
  `languages:` in topic.yaml is required and is a non-empty list of registered
  codes; most topics carry `[en, ru]`. A single-language topic writes translatable
  YAML fields as plain strings, has only `explanation.<lang>.md`, and fills only
  that language's key in bossFight entries and `learning-atoms.json`. Loaders no
  longer mirror one language into another: short labels fall back at read time,
  while theory, lessons and boss questions show a "no text in this language"
  state instead. The language picker sits next to the style selector in every
  generation row (topic, new theory version, lesson, Add topic); the choice is
  global and persisted, and at least one language always stays selected. For an
  existing topic it is narrowed to what the topic has
  (`ContentLanguages.effective`), except when regenerating a version, which may
  add a language the topic lacks.
- A theory version stores its text per language (`theory_version_text`); the
  version bar shows a chip per language it has and a `+XX` button that
  translates it into a missing one. For version 1 that writes
  `explanation.<lang>.md` and adds the code to topic.yaml through
  `TopicYamlEditor`, which edits that single line and leaves the rest of the
  file byte-identical (never a snakeyaml re-dump — it would destroy comments and
  block scalars).
- `bossFight` questions need stable unique ids. Do not reuse an old id for a new
  question.
- YAML values containing `: `, `#`, quotes, or leading punctuation should be
  single-quoted.
- Java examples should be small, deterministic, and teach one idea each.
- Mermaid diagrams must use valid Mermaid 11 syntax and translated labels in each
  language file.
- Cross-links use `[label](topic:<id>)` or `[label](catalog:<id>)` and must point
  to real ids only.

## Existing Topic References

Use these as templates:

- Trace topic: `topics/hashmap/`
- Structural topic: `topics/strategy/`
- Theory topic: `topics/design-patterns-overview/`
- Non-Java (domain) theory topic with images: `topics/ndm-scalability/`
- Single-language topic (`languages: [ru]`): `topics/qa-test-estimation-task/`
- SQL topic: `topics/sql-many-to-many/`
- Challenge topic: `topics/algo-max-pair-product/`
- `learning-atoms.json` (micro-actions lesson): `topics/hashmap/learning-atoms.json`
  — the canonical quality reference for lesson generation (the generator prompt
  points the model at it). Keep it exemplary: if you change how lessons should
  look, update this file to match so it stays the gold standard.

## Claude and Generation Notes

The backend uses the Claude Code CLI for generation and assistant flows. Relevant
settings are under `app.ai.*` (legacy `app.claude.*` still works as a fallback)
and `app.usage` in `backend/src/main/resources/application.yml`.

Claude models are configured as tier aliases (`sonnet`, `opus`), not pinned ids,
so a new model release needs no config change. `AiCliService` passes the
configured value to `--model` verbatim and separately caches the concrete id the
CLI reports (`system`/`init` and `assistant` stream events) under that alias;
`modelFor(...)` returns the cached id when known, which is what lands in
`aiModel` of generated `topic.yaml` / `learning-atoms.json` / theory versions.
Do not re-pin these to exact model ids.

Topic generation is intentionally constrained by `prompts/add-topic.md`. Do not
loosen the contract unless you also update tests and the app logic that relies on
the topic shape.

Lesson (`learning-atoms.json`) generation is likewise constrained by
`prompts/generate-learning-atoms.md` and follows the same detached-task pattern
as topic generation and theory-version regeneration
(`generation/GenerationService`, `api/LessonGenController`): the AI writes the
JSON file directly rather than streaming it to stdout, to avoid the Windows
Cyrillic stdout-mangling issue that theory-version regeneration also works
around.
