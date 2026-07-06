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

Starts a fresh backend on `http://localhost:8080` and frontend on
`http://localhost:5173`, after building `visual-runtime`.

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
- User-facing topic content is bilingual: English and Russian.
- Files contain UTF-8 Cyrillic content. Preserve UTF-8 and avoid bulk rewrites
  caused only by console encoding/mojibake.
- Use stable, deterministic data for examples, tests, SQL seeds, and trace states.
- Keep changes scoped. Ignore unrelated dirty worktree changes.

## Backend Notes

- Main package: `com.interviewlearning`.
- Controllers live mostly under `backend/src/main/java/com/interviewlearning/api`.
- Topic loading is in `topics/TopicRepository`. It rereads `topics/` from disk on
  requests so new folders appear without a backend restart.
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
- `ReviewScreen` (`#/review`) reuses `ExerciseCard` outside the lesson context.
- Router (`frontend/src/engine/router.ts`) routes: `#/q/<id>` (lesson if one
  exists, else theory), `#/q/<id>/theory` (forces the reference explanation),
  `#/q/<id>/practice` (trace/structural/sql/challenge workspace — the "Go to
  practice" button is hidden for `trace` topics once a lesson exists, but the
  route still works), `#/review` (global review).

## Topic Authoring

Before adding or significantly editing a topic, read:

- `prompts/topic-contract.md`
- `prompts/add-topic.md`
- `prompts/mermaid-guide.md` when editing explanations with diagrams
- `prompts/generate-learning-atoms.md` when authoring or editing a topic's
  `learning-atoms.json`

The topic `id` in `topic.yaml` must match the folder name. New topics must include
`categoryId`, `difficulty`, and usually `assistantExample`.

Known modes:

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
(`correct`/`incorrect`, each 1-2 sentences); exercises are neutral in tone
(ignore any generation-style analogies baked into the source explanation) and
must be answerable standalone, since practice exercises are later reused out of
context in the global review pool. `LessonAtomsContractTest` enforces the exact
shape per exercise type — trust it over hand-written JSON.

For all topics:

- Every visible string must exist in both English and Russian.
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
- SQL topic: `topics/sql-many-to-many/`
- Challenge topic: `topics/algo-max-pair-product/`
- `learning-atoms.json` (micro-actions lesson): `topics/hashmap/learning-atoms.json`

## Claude and Generation Notes

The backend uses the Claude Code CLI for generation and assistant flows. Relevant
settings are under `app.claude` and `app.usage` in
`backend/src/main/resources/application.yml`.

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
