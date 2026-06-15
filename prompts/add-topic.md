# Add a new Java interview learning topic

You are working **inside an existing interactive Java interview learning app**.
The architecture is fixed. Your job is to add ONE new topic as a self-contained
plugin folder under `topics/<id>/`, reusing the existing engine, runner and
visual primitives. **Do not modify the shell, the engine, the runner, or the
backend** unless a new visual primitive or instrumented model is genuinely
required (see below).

Read `prompts/topic-contract.md` for the exact folder schema and the trace-event
contract. Mirror the existing `topics/hashmap/` topic as your reference example.

**The topic must be fully bilingual (English + Russian).** Every user-visible
string exists in both languages: `title`, `category`, `summary`, each example's
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
   and `difficulty` yourself (and omit `catalogId`).
2. **Identify the mental model to visualize.** The primitives that exist today are
   `ArrayGrid`, `LinkedNodes` and `EventLog` (under `frontend/src/primitives/`).
   If none fit your topic, add a new generic, data-driven primitive there following
   the same style, and use it from your visualizer.
3. **Decide how the code produces trace events.** Prefer an existing instrumented
   model in `visual-runtime` (e.g. `visual.VisualHashMap`). If the topic needs a
   new model, add it under `visual-runtime/src/main/java/visual/` and have it call
   `visual.Trace.event(type, descEn, descRu, highlight, state)` with a bilingual
   description. Keep `visual-runtime` dependency-free.
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
   relationships): pick the diagram type that fits the concept, keep labels as
   English technical terms so the same diagram block is identical in both files. See
   the explanation-files section of `topic-contract.md` and `prompts/mermaid-guide.md`.
8. **Write `quiz.yaml`** with missions whose `event` matches a trace event type the
   examples can produce, plus a `bossFight` question list (each with a stable `id`).
9. **Validate**: run `./gradlew :visual-runtime:test` if you added/changed a model,
   and make sure each example compiles. Confirm the topic folder matches the schema.

## Hard constraints

- Keep examples short and deterministic; one idea each.
- Reuse primitives. Only add a new primitive under `frontend/src/primitives/` if
  none fit, and keep it generic and data-driven.
- Trace `state` must follow the topic's own `trace-schema.json`.
- The topic must work fully offline (no external services).
- Include the common interview traps and misconceptions for the topic.
- Do not invent a new layout, editor, runner, or build system.
