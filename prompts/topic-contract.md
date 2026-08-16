# Topic plugin contract

A topic is a folder `topics/<id>/` with this exact layout:

```
topics/<id>/
  topic.yaml          metadata + examples list + primitives + defaultExample
  explanation.en.md   English prose: intuition, interview answer, traps
  explanation.ru.md   Russian translation of the same explanation
  examples/
    01-*.java         full `public class Playground { public static void main ... }`
    02-*.java
  visualizer.tsx      default-exported React component rendering event state
  trace-schema.json   JSON Schema for the `state` field + list of event types
  quiz.yaml           missions (event-checked) + bossFight questions
```

## Content languages

A topic carries every user-visible string in each language it declares. The
languages are listed in `languages:` in topic.yaml; when the key is absent the
topic carries `[en, ru]`, which is what every legacy topic does.

- Translatable YAML fields use a `{ <lang>: … }` map with one key per declared
  language — or a plain string, which means the same text serves every language
  (this is how a single-language topic is written).
- There is one `explanation.<lang>.md` per declared language.
- quiz.yaml bossFight entries keep their stable `id` and carry one key per
  declared language.
- Code, identifiers and technical terms (Java, HashMap, hashCode, resize, …)
  stay untranslated, and Java source including comments stays English — only the
  trace *descriptions* are per-language.

**The generation prompt always ends with a LANGUAGES directive naming the exact
languages to write. It is authoritative** and overrides any bilingual wording in
the examples below, which show a topic declaring `[en, ru]`.

## topic.yaml

```yaml
id: <kebab-id>                  # must equal the folder name
title:
  en: <Human Title>
  ru: <Заголовок>
category:
  en: <e.g. Java Core / Collections>
  ru: <напр. Java Core / Коллекции>
type: <DATA_STRUCTURE | CONCURRENCY | ...>
mode: <trace | structural | theory | sql | challenge>   # OPTIONAL: default `trace`.
                                # `structural` = design-pattern class-graph engine;
                                # `theory` = explanation + Boss Fight only;
                                # `sql` = seeded DB + query editor;
                                # `challenge` = implement a method, graded by tests (see below)
summary:
  en: <one paragraph>
  ru: <один абзац>
categoryId: <catalog category id>   # which home-tree category this topic belongs to
categoryName: <Human Category Name> # ONLY when categoryId is a brand-new category
difficulty: <1 | 2 | 3>             # 1 = Junior, 2 = Middle, 3 = Senior
catalogId: <catalog question id>    # OPTIONAL: only when generated from a tree question
style: <Style name>                 # OPTIONAL: the explanation style this was generated in
                                    # (set only when the request supplies one; default omit)
aiProvider: <claude | codex | kimi> # AI provider used for generation; copy from request metadata
aiModel: <model name>               # OPTIONAL: model used for generation, when supplied
primitives: [ArrayGrid, LinkedNodes, EventLog]   # primitives the visualizer uses
defaultExample: <example-id>    # which example loads by default
examples:
  - id: <example-id>
    title:
      en: <button label>
      ru: <подпись кнопки>
    file: 01-basic.java         # relative to examples/
    explanation:
      en: <what it shows>
      ru: <что показывает>
assistantExample:                # one example question, shown as the Ask AI placeholder
  en: "e.g. <a natural interview question about this topic>"
  ru: "например: <тот же вопрос по-русски>"
missionsFile: quiz.yaml
```

(A plain scalar instead of an `{en, ru}` map is accepted and used for both
languages, but new topics should provide both.)

## Catalog placement (categoryId, difficulty, catalogId)

`categoryId` places the topic in the home-screen question tree, and `difficulty`
(1 = Junior, 2 = Middle, 3 = Senior) sets its star rating. `categoryId` MUST be
one of these exact ids:

```
java-core, java-collections, concurrency, memory-gc, oop-design, exceptions,
streams, algorithms, databases, spring, hibernate, design-patterns,
microservices, rest, networking, security, devops, performance, kotlin, messaging, other
```

Pick the single best fit from that list. If **none** of them genuinely fits the
topic, do not force it into `other` — instead invent a **new category**: set
`categoryId` to a new kebab-case id (not already in the list) and add a
human-readable `categoryName` (an English label, in the same style as the
existing category names). `categoryName` is required only for such a new category;
omit it when `categoryId` is one of the known ids.

`catalogId` is set only when a topic is generated from an existing tree question
(it links the topic back to that question) — omit it otherwise. The generation
request supplies the exact values to use for any of these it has already decided;
otherwise choose them yourself.

The generation request also supplies `aiProvider` and usually `aiModel`; copy
those exact values into `topic.yaml`. Existing topics generated before provider
selection use `aiProvider: claude`.

## Structural topics (mode: structural)

Design-pattern topics that are about **class relationships** (Strategy, Observer,
Factory, Decorator, Adapter, …) use a different engine. The learner builds real
classes in a multi-file editor and presses **Analyze**, which compiles the project
and parses it into a class graph (nodes + `extends`/`implements`/`association`
edges) rendered as a Mermaid class diagram; missions are checked against that
graph. There is no "Run" and no trace events.

Set `mode: structural`. The folder layout changes:

```
topics/<id>/
  topic.yaml          mode: structural   (no primitives/examples/defaultExample)
  explanation.en.md   prose + a Mermaid classDiagram of the target shape
  explanation.ru.md
  starter/            seed .java files the learner opens (MUST compile)
    PaymentStrategy.java
    Checkout.java
  quiz.yaml           structure missions + bossFight
```

**Omit entirely:** `examples/`, `visualizer.tsx`, `trace-schema.json`,
`primitives`, `defaultExample`, and any `visual.*` model.

**starter/** seeds the editor: give the interface / abstract base and a stub
context, but leave the classes and fields the missions ask for **unbuilt** — every
starter file must compile as-is, and must NOT already satisfy the missions.

**Structure missions** use `type: structure` + a `requires` list of predicates; a
mission passes when EVERY predicate holds against the analyzed graph:

```yaml
missions:
  - id: strategy-interface
    type: structure
    title: { en: Define a Strategy interface, ru: Определи интерфейс Strategy }
    goal:  { en: An interface with two implementations, ru: Интерфейс с двумя реализациями }
    requires:
      - { kind: interfaceWithImpls, minImplementations: 2 }
  - id: context-holds-strategy
    type: structure
    title: { en: Context holds a Strategy, ru: Контекст хранит Strategy }
    goal:  { en: A class has a field of the interface type, ru: У класса есть поле типа интерфейса }
    requires:
      - { kind: composition, targetKind: interface }
```

Predicate kinds:

- `interfaceWithImpls { minImplementations, name? }` — an interface (optionally
  named) with at least N implementing classes.
- `composition { targetKind, ownerKind?, name? }` — some class has a field whose
  type is a node of `targetKind` (`interface` | `class` | `abstractClass` | `enum`).
- `edge { relation: extends | implements | association, from?, to? }` — such an edge exists.
- `nodeExists { nodeKind, name? }` — a declared type of that kind (optionally named).

Only types **declared in the project** are nodes (`java.*` references are ignored);
generic/array field types are unwrapped, so `List<Strategy>` counts as a
`composition` to `Strategy`. `bossFight` is unchanged. Validate with
`./gradlew :backend:test`. Mirror `topics/strategy/`.

## Theory topics (mode: theory)

For purely conceptual / overview questions with nothing to run or build. The
learner reads the explanation and does a Boss Fight (no editor, no missions, no
diagram to build). Set `mode: theory`. The folder is just:

```
topics/<id>/
  topic.yaml          mode: theory
  explanation.en.md   prose (+ Mermaid diagrams and cross-links where useful)
  explanation.ru.md
  quiz.yaml           bossFight only — NO `missions:` block
```

**Omit entirely:** `examples/`, `visualizer.tsx`, `trace-schema.json`, `starter/`,
`primitives`, `defaultExample`, and `missions`. Lean on **cross-links**
(`[Strategy](topic:strategy)`) so an overview topic points at the focused topics
that detail each part. Mirror `topics/design-patterns-overview/`.

## SQL topics (mode: sql)

A query playground: the learner writes SQL against a seeded in-memory H2 database
(PostgreSQL mode), runs it, and sees the result table; missions compare the result
to a reference query. Set `mode: sql`. Folder layout:

```
topics/<id>/
  topic.yaml          mode: sql
  explanation.en.md   prose (+ an erDiagram of the schema where useful)
  explanation.ru.md
  starter/
    schema.sql        DDL + seed INSERTs (deterministic, small)
    query.sql         the editor's opening query (a SELECT stub; task in comments)
  quiz.yaml           sql missions + bossFight
```

**Omit:** `examples/`, `visualizer.tsx`, `trace-schema.json`, `primitives`,
`defaultExample`. SQL missions:

```yaml
missions:
  - id: courses-over-10
    type: sql
    title: { en: ..., ru: ... }
    goal:  { en: Return the course id and name (in that order)..., ru: ... }
    requires:
      - kind: sqlResult
        expectedSql: >
          SELECT c.id, c.name FROM courses c JOIN enrollments e ON e.course_id = c.id
          GROUP BY c.id, c.name HAVING COUNT(*) > 10
        # ordered: true   # only when the task is about ORDER BY
```

The learner's result is compared to `expectedSql` (run on the same seed): columns
by position, rows as a multiset unless `ordered`. So **state the expected column
order in `goal`**. Use only SQL that H2's PostgreSQL mode supports; questions about
real `EXPLAIN`/`Seq Scan` plans belong in `mode: theory`. Mirror
`topics/sql-many-to-many/`.

## Challenge topics (mode: challenge)

A coding kata: the learner implements a method in `Solution.java` and runs it
against a hidden test harness; the result is a list of pass/fail cases. Set
`mode: challenge`. Folder layout:

```
topics/<id>/
  topic.yaml          mode: challenge
  explanation.en.md   prose (approach, edge cases, complexity)
  explanation.ru.md
  starter/
    Solution.java     public class Solution { <signature> { /* TODO */ } } — must compile
  harness/
    Main.java         public class Main { public static void main(...) { ...TestKit.expect... } }
  quiz.yaml           challenge mission(s) + bossFight
```

**Omit:** `examples/`, `visualizer.tsx`, `trace-schema.json`, `primitives`,
`defaultExample`. The harness (hidden from the learner) reports each case with
`visual.TestKit.expect(String name, Object expected, Object actual)` — it
deep-compares (handles arrays) and emits a `TEST` event; the backend reads those.
Call the solution via `new Solution().<method>(...)`. Challenge missions:

```yaml
missions:
  - id: all-tests
    type: challenge
    title: { en: Pass all tests, ru: Пройди все тесты }
    goal:  { en: Implement <method> so every test passes., ru: ... }
    requires:
      - kind: tests        # passes when every harness case is green
```

Mirror `topics/algo-max-pair-product/`.

## Explanation files (explanation.en.md / explanation.ru.md)

Prose teaching the concept: intuition, a 60-second interview answer, production
relevance, and the common traps. The Russian file is a faithful translation of
the English one.

**Explanation style (optional).** The generation request may include an
`EXPLANATION STYLE` block — an analogy theme (sports, video games, cooking, …).
When present, weave a short themed analogy into each technical point of the
explanation prose, in BOTH languages, to aid memorisation. Accuracy comes first:
the analogy supplements, never replaces, correctness; keep it concise; and do NOT
style code, Mermaid diagrams, identifiers, missions or boss-fight questions. With
no style block, write plain (default) prose.

**Cross-link to other topics.** When the explanation mentions a concept that
already has its own topic, link to it with a `topic:<id>` (or `catalog:<id>`)
markdown link — the app turns it into in-app navigation to that question:

```
… deduplicate redelivered messages with the [Inbox pattern](topic:inbox-pattern).
```

Use only ids that actually exist (the generation request lists the available
topics under "EXISTING TOPICS YOU MAY CROSS-LINK TO"); never invent an id, and
never link the new topic to itself. This is what lets a broad/overview question
point at the focused topics that detail each part of the answer.

**Include 1–3 Mermaid diagrams** where a picture genuinely helps understanding —
structure, interaction, lifecycle or relationships are far clearer drawn than
described. Do not add diagrams just to have them. Embed each as a fenced block:

````
```mermaid
flowchart LR
  A[hash(key)] --> B["index = hash & (n-1)"] --> C[(bucket)]
```
````

Pick the diagram type that fits the idea (see `prompts/mermaid-guide.md` for
ready-to-copy examples per topic type):

- `flowchart` / `graph` — algorithms, decision flow, memory layout, data structures
- `sequenceDiagram` — interactions over time (threads, request flow, transactions)
- `stateDiagram-v2` — lifecycles and state machines (Thread states, bean lifecycle)
- `classDiagram` — type relationships, inheritance, design patterns
- `erDiagram` — table/entity relationships (SQL, persistence)

Diagram rules:

- Use **valid** Mermaid 11 syntax; keep each diagram focused (roughly ≤ 12 nodes).
- **Translate diagram labels too** (same bilingual rule as the prose): the diagram
  in `explanation.ru.md` has Russian labels, the one in `explanation.en.md` has
  English labels. Keep **technical terms untranslated** exactly as in code —
  type/identifier names, keywords and tokens like `HashMap`, `hashCode`, `resize`,
  `capacity`, `threshold`, `RUNNABLE`, `Eden`, `Old`, `PENDING`, `O(n)`, code
  expressions (`h ^ (h >>> 16)`) and SQL — but translate ordinary words
  (`bucket empty?` → `бакет пуст?`, `store entry` → `сохранить запись`, edge labels
  `yes`/`no` → `да`/`нет`). Node **ids** stay ASCII; only the label text and edge
  labels are translated. So the two diagram blocks differ in their wording but share
  the same structure and the same technical tokens.
- Wrap any label containing spaces or punctuation in quotes: `B["index = hash & (n-1)"]`.
  Cyrillic in labels and edge labels is fine (`-->|да|`, `{"бакет пуст?"}`).
- Stay within the common node/edge syntax of the chosen diagram type; avoid exotic
  features (themes, `click`, raw HTML, `%%{init}%%` blocks) — the app themes and
  sanitizes diagrams itself.

## Trace events (the core contract)

User code drives an instrumented `visual.*` model. Each model call emits a line
`@@TRACE@@{json}` to stdout via `visual.Trace.event(...)`. The backend strips
these from program output and returns them as `traceEvents`. The frontend replays
them step by step, passing each event's `state` to `visualizer.tsx`.

`visual.Trace.event(String event, String descEn, String descRu, List<String> highlight, Object state)`

Event envelope (fixed):

```json
{
  "step": 3,
  "event": "HASHMAP_PUT",
  "description": { "en": "shown in the EventLog", "ru": "показывается в журнале" },
  "highlight": ["bucket:0", "node:Aa"],
  "state": { "...": "topic-specific; must match trace-schema.json" }
}
```

`event` is a technical code and is NOT translated. `state` is serialized from
Maps/Lists/String/Number/Boolean; build it as a `LinkedHashMap` for stable order.

## visualizer.tsx

```tsx
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid } from '@app/primitives/ArrayGrid';
import { tl, useLang } from '@app/i18n';

const LABELS = { capacity: { en: 'capacity', ru: 'ёмкость' } /* ... */ };

export default function Visualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);     // read the active language
  const state = event?.state as MyState | undefined;
  // render `state` with primitives; localize any labels via tl(LABELS.x, lang);
  // use event.highlight to emphasize parts.
  return /* ... */;
}
```

The component is pure given the current event + language: it never calls the
backend and never runs Java. Localize every visible label through `tl(..., lang)`.

## quiz.yaml

```yaml
missions:
  - id: <id>
    title:
      en: <short>
      ru: <коротко>
    goal:
      en: <what the learner should make happen>
      ru: <чего нужно добиться>
    event: <TRACE_EVENT_TYPE that completes this mission>   # not translated
bossFight:
  - id: <stable-kebab-id>          # stable across edits; saved answers key off it
    en: <interview question>
    ru: <вопрос с собеседования>
```

Each `bossFight` question needs a stable `id` (a short kebab slug, unique within
the topic). Learner answers are stored against this id, so never reuse or
repurpose an id for a different question — add a new one instead.

## YAML quoting (avoid syntax errors)

A YAML syntax error in `topic.yaml` / `quiz.yaml` makes the whole file unreadable
(the topic loses its missions and boss fight). **Single-quote any value that
contains quotes, a colon-space, `#`, or starts with punctuation** — e.g.
`en: '"Change" a string'`, not `en: "Change" a string`. Inside single quotes,
double a literal apostrophe (`'it''s'`). Russian guillemets `«…»` need no quoting.

## Validation (always run after writing a topic)

`./gradlew :backend:test --tests "*TopicContractTest"` validates every topic with
the same parser the app uses: it fails on a YAML syntax error and on missing or
malformed structure (bilingual fields, examples + their files, `missions`, a
non-empty `bossFight` of `{ id, en, ru }`). A new topic must pass it.
