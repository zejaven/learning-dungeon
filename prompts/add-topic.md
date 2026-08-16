# Add a new Java interview learning topic

You are working **inside an existing interactive Java interview learning app**.
The architecture is fixed. Your job is to add ONE new topic as a self-contained
plugin folder under `topics/<id>/`, reusing the existing engine, runner and
visual primitives. **Do not modify the shell, the engine, the runner, or the
backend** unless a new visual primitive or instrumented model is genuinely
required (see below).

Read `prompts/topic-contract.md` for the exact folder schema and the trace-event
contract. Mirror the existing `topics/hashmap/` topic as your reference example.

**The topic must carry every user-visible string in each language the trailing
LANGUAGES directive of this prompt lists** (see topic-contract.md § Content
languages). That directive is always present and authoritative; the list below
describes the fields it applies to, using the usual English + Russian pair as
its example: `title`, `category`, `summary`, each example's
`title` and `explanation`, every mission `title`/`goal`, each `bossFight`
question's `en`/`ru` (each with a stable `id`),
the `assistantExample` (the Ask AI placeholder question), two explanation files
(`explanation.en.md` + `explanation.ru.md`), and the trace event descriptions
(`Trace.event(event, descEn, descRu, highlight, state)`). Keep
code, identifiers and technical terms (Java, HashMap, hashCode, …) untranslated,
and keep Java source/comments in English. Localize visualizer labels via
`tl(..., lang)` with `useLang` from `@app/i18n`.

## Steps

1. **Classify the topic.** Pick one `type`:
   `DATA_STRUCTURE | CONCURRENCY | JVM_MEMORY | SPRING | TRANSACTION | SQL |
   HTTP | DESIGN_PATTERN | TESTING | OTHER`. Also set the topic's catalog
   placement in `topic.yaml`: `categoryId` (one of the allowed ids in
   topic-contract.md) and `difficulty` (1 = Junior, 2 = Middle, 3 = Senior). If
   no existing category fits, create a NEW one: a new kebab-case `categoryId`
   plus a human-readable `categoryName` (do not dump it into `other`).
   The generation request may already specify `categoryId`, `difficulty` and a
   `catalogId` — if so, use those exact values; otherwise determine `categoryId`
   and `difficulty` yourself (and omit `catalogId`). The generation request also
   includes `aiProvider` and usually `aiModel`; copy both into `topic.yaml`
   exactly as supplied.

   Then **choose the mode**. Most topics are `mode: trace` (the default — a runnable
   model + visualizer + trace events; steps 2–8 below). A **design pattern about
   class relationships** (GoF: Strategy, Observer, Factory, Decorator, Adapter, …)
   should instead be `mode: structural` — see *Structural topics* below; for those,
   skip steps 2, 3, 5, 6 and the event-missions in step 8. A **purely conceptual or
   overview question** with nothing to run or build (e.g. "what is OWASP?", "what
   design patterns exist?", "HTTP vs HTTPS", "why microservices?") should be
   `mode: theory` — just an explanation + a Boss Fight; see *Theory topics* below.
   A **SQL question** (joins, grouping, NULL semantics, subqueries, …) should be
   `mode: sql` — a seeded in-memory database + a query editor; see *SQL topics* below.
   A **coding/algorithm task** (implement a method, graded by test cases) should be
   `mode: challenge` — an editable `Solution.java` + a hidden test harness; see
   *Challenge topics* below.
2. **Identify the mental model to visualize.** The primitives that exist today are
   `ArrayGrid`, `LinkedNodes` and `EventLog` (under `frontend/src/primitives/`).
   If none fit your topic, add a new generic, data-driven primitive there following
   the same style, and use it from your visualizer.
3. **Decide how the code produces trace events.** Prefer an existing instrumented
   model in `visual-runtime` (e.g. `visual.VisualHashMap`). If the topic needs a
   new model, add it under `visual-runtime/src/main/java/visual/` and have it call
   `visual.Trace.event(type, descEn, descRu, highlight, state)` with a bilingual
   description. Keep `visual-runtime` dependency-free. **When you add a model, also
   add a `Visual<Name>Test`** under `visual-runtime/src/test/java/visual/` asserting
   its key trace events (mirror `VisualHashMapTest`).
4. **Create the topic folder** `topics/<id>/` with all required files (see schema).
5. **Write 4–8 small examples**, each a full `public class Playground` with a
   `main`, each teaching exactly one idea, importing the `visual.*` model.
6. **Write `visualizer.tsx`** — a default-exported React component rendering the
   event `state` using existing primitives. It must NOT know about Java execution;
   it only renders the `state` of the current step.
7. **Write `explanation.en.md` and `explanation.ru.md`**: intuitive explanation, a
   60-second interview answer, production relevance, and common misconceptions — the
   Russian file is a faithful translation of the English one. **Include 1–3 Mermaid
   diagrams** where they aid understanding (structure / interaction / lifecycle /
   relationships): pick the diagram type that fits the concept. **Translate the
   diagram labels too** — Russian labels in `explanation.ru.md`, English in
   `explanation.en.md` — keeping technical terms/identifiers/code untranslated.
   **Cross-link** related concepts that already have a topic via
   `[label](topic:<id>)`, using only the ids listed in the generation request. See
   the explanation-files section of `topic-contract.md` and `prompts/mermaid-guide.md`.
8. **Write `quiz.yaml`** with missions whose `event` matches a trace event type the
   examples can produce, plus a `bossFight` question list (each with a stable `id`).
9. **Validate** — run BOTH and fix every failure before finishing:
   - `./gradlew :visual-runtime:test` — the learning-model unit tests (always run;
     this includes the `Visual<Name>Test` you add for a new model).
   - `./gradlew :backend:test` — runs `TopicContractTest` (strictly parses
     `topic.yaml` / `quiz.yaml`, so a YAML syntax error fails; checks bilingual
     fields, examples + their files, `missions`, and a non-empty `bossFight` of
     `{ id, en, ru }`) AND `TopicExamplesTest` (compiles and runs every example
     through the real runner, asserting each runs cleanly and that every mission's
     `event` is actually emitted by some example — i.e. every mission is
     completable).

## Structural topics (design patterns)

A structural topic teaches a pattern by its **class relationships**, not runtime
behaviour. Set `mode: structural` in topic.yaml and follow these deltas (full
schema in `topic-contract.md` → "Structural topics"); mirror `topics/strategy/`:

- **No** `visual.*` model, `examples/`, `visualizer.tsx`, `trace-schema.json` or
  `primitives` (skip steps 2, 3, 5, 6). The practice screen has no "Run" — it
  compiles the project and analyzes it into a class diagram via "Analyze".
- Add a **`starter/`** folder of seed `.java` files the learner opens — give them
  the interface / abstract base and a stub context, but leave the pieces the
  missions ask for unbuilt. **Every starter file must compile.**
- `quiz.yaml` missions use `type: structure` + a `requires` list of predicates
  checked against the analyzed class graph (`interfaceWithImpls` / `composition` /
  `edge` / `nodeExists`). Make every mission reachable by the intended solution.
- Still write `explanation.en.md` / `explanation.ru.md` (step 7) with a Mermaid
  `classDiagram` of the target shape, and a `bossFight` list (step 8).
- Validate with `./gradlew :backend:test` (`TopicContractTest` is mode-aware;
  `TopicExamplesTest` skips structural topics). `:visual-runtime:test` is only
  needed if you touched a model (structural topics don't).

## Theory topics (mode: theory)

A theory topic is a **read-and-discuss** topic for purely conceptual questions:
there is no editor, no missions and no diagram to build — just the explanation and
a Boss Fight (the learner's spoken answer, graded by the AI). Set `mode: theory`
and follow these deltas (mirror `topics/design-patterns-overview/`):

- **Only** `topic.yaml`, `explanation.en.md` / `explanation.ru.md`, and `quiz.yaml`
  with a `bossFight` list. **Omit** `examples/`, `visualizer.tsx`,
  `trace-schema.json`, `starter/`, `primitives`, `defaultExample`, and the
  `missions:` block entirely (skip steps 2, 3, 5, 6, and the missions in step 8).
- Write a strong explanation (step 7) with Mermaid diagrams where they help and
  **cross-links** to any focused topics that detail a part of the answer
  (`[Strategy](topic:strategy)`) — this is what makes an overview question useful.
- Validate with `./gradlew :backend:test` (the contract test is mode-aware; the
  examples test skips theory topics).

## SQL topics (mode: sql)

A SQL topic is a **query playground**: the learner writes SQL against a seeded
in-memory H2 database (PostgreSQL mode) and sees the result table. Missions check
the result matches a reference query. Set `mode: sql` and follow these deltas
(mirror `topics/sql-many-to-many/`):

- **Only** `topic.yaml`, `explanation.en.md` / `explanation.ru.md`, `quiz.yaml`, and
  a `starter/` with **`schema.sql`** (DDL + seed `INSERT`s — keep deterministic and
  small) and **`query.sql`** (the editor's opening query, e.g. a `SELECT` stub with
  the task in comments). **Omit** examples/visualizer/trace-schema/primitives and any
  `visual.*` model (skip steps 2, 3, 5, 6).
- Missions use `type: sql` + `requires: [{ kind: sqlResult, expectedSql: "<correct query>", ordered?: false }]`.
  The result of the learner's query is compared to `expectedSql` run on the same
  seed — columns by position, rows as a multiset (set `ordered: true` only when the
  task is about `ORDER BY`). State the expected column order in the mission `goal`.
- Stay within standard SQL that H2's PostgreSQL mode supports (JOIN, GROUP BY,
  HAVING, subqueries, CTEs, NULL logic). Do NOT make a SQL topic about real
  `EXPLAIN` / `Seq Scan` plans — those are `mode: theory`.
- Validate with `./gradlew :backend:test` (the contract test is mode-aware; the
  examples test skips SQL topics).

## Challenge topics (mode: challenge)

A challenge topic is a **coding kata**: the learner implements a method and presses
**Run tests**; a hidden harness runs it against cases and shows pass/fail. Set
`mode: challenge` and follow these deltas (mirror `topics/algo-max-pair-product/`):

- **Only** `topic.yaml`, `explanation.en.md` / `explanation.ru.md`, `quiz.yaml`, a
  `starter/Solution.java` (the editable stub — `public class Solution` with the
  method signature + a TODO body that compiles), and a **`harness/Main.java`**
  (hidden test driver — `public class Main` with `main`). **Omit** examples/
  visualizer/trace-schema/primitives (skip steps 2, 3, 5, 6).
- The harness reports each case with **`visual.TestKit.expect(name, expected, actual)`**
  (on the sandbox classpath) — it deep-compares and emits a `TEST` event. Call the
  learner's code via `new Solution().<method>(...)`. Cover normal + edge cases.
- Missions use `type: challenge` + `requires: [{ kind: tests }]` — passes when every
  test case is green. Usually one mission ("pass all tests").
- Validate with `./gradlew :visual-runtime:test :backend:test` (the contract test is
  mode-aware; the examples test skips challenge topics).

## Hard constraints

- Keep examples short and deterministic; one idea each.
- Reuse primitives. Only add a new primitive under `frontend/src/primitives/` if
  none fit, and keep it generic and data-driven.
- Trace `state` must follow the topic's own `trace-schema.json`.
- The topic must work fully offline (no external services).
- Include the common interview traps and misconceptions for the topic.
- Do not invent a new layout, editor, runner, or build system.
