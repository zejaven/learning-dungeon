# Generate a "Learn by micro-actions" lesson (learning-atoms.json)

You convert an EXISTING bilingual theory explanation into a micro-action lesson
file. The learner will not read the theory as the main path; they will pass
through the topic as a sequence of tiny interactive exercises. Your output is
ONE file: `learning-atoms.json` in the topic folder. The exact absolute output
path is given at the end of this prompt.

Hard rules:

- Write ONLY that one file. Do not touch any other file.
- The file must be valid JSON, UTF-8, no comments, no trailing commas, and no
  markdown fences around the content.
- Do not print the JSON to stdout; write it to the file.

## Reference example (study the quality bar — do NOT copy)

Before you start, open and read `topics/hashmap/learning-atoms.json`. It is the
canonical, contract-validated reference for the QUALITY BAR and how the pieces
fit together: the depth of the `reveal` teaching cards, term-before-test
sequencing across atoms, the recognition→arrange→produce escalation, the final
`capstone` synthesis atom, diagram style, and the neutral tone.

Study HOW it is built, but do NOT copy its content or shape:

- Never reuse HashMap wording, analogies, atom breakdown, or examples in an
  unrelated topic.
- Do not force HashMap's data-structure framing onto your topic. Diagrams,
  bit-math and formulas belong only where YOUR topic warrants them — a purely
  conceptual topic may have few or none.

The rules in THIS prompt are the contract (the source of truth); the reference
file is only an illustration of the bar. Where they seem to differ, follow the
rules here.

## Output schema

```jsonc
{
  "schemaVersion": 1,
  "topicId": "<topic id — given below>",
  "sourceVersion": 1,            // theory version number — given below
  "aiProvider": "<given below>",
  "aiModel": "<given below>",
  "atoms": [
    {
      "id": "kebab-case-atom-id",
      "title":   { "en": "...", "ru": "..." },
      "summary": { "en": "...", "ru": "..." },
      "capstone": false,           // OPTIONAL; true only on the final synthesis atom (see "Capstone")
      "discovery": [ /* Exercise[], 1-3 items */ ],
      "practice":  [ /* Exercise[], 3-6 items */ ]
    }
  ]
}
```

Every Exercise has these common fields:

```jsonc
{
  "id": "kebab-case-exercise-id",     // unique across the WHOLE file
  "type": "multiple_choice",          // one of the types below
  "prompt": { "en": "...", "ru": "..." },
  "code": "int x = 1;\n...",          // OPTIONAL code snippet; code is never localized
  "codeLang": "java",                 // OPTIONAL: java | sql | text (required when code is set)
  "mermaid": { "en": "...", "ru": "..." },  // OPTIONAL static Mermaid 11 diagram shown ABOVE the input (context only; never for a prediction, it would spoil the answer)
  "reveal": { "en": "...", "ru": "..." },   // the teaching card shown AFTER answering (see "Reveal" below)
  "feedback": {
    "correct":   { "en": "...", "ru": "..." },   // ONE short line: the verdict, not the lesson
    "incorrect": { "en": "...", "ru": "..." }    // ONE short line: the verdict, not the lesson
  }
}
```

Exercise types and their extra fields:

| type | extra fields |
|---|---|
| `multiple_choice` | `options`: 3-4 of `{ "id", "text": {en,ru}, "correct": bool, "feedback": {en,ru} }` — EXACTLY one `correct: true`; every wrong option carries its own `feedback` explaining the misconception |
| `true_false` | `answer`: boolean; the statement to judge goes in `prompt` |
| `fill_blank` | `text`: {en,ru} with ONE OR MORE `___`; `blanks`: an array with one `{ "en": [...], "ru": [...] }` per `___`, in order — short accepted answers (usually a technical token identical in both languages; include casing variants). Use SEVERAL blanks to make the learner type each part of a formula/step. (Legacy single-blank `answers` is still accepted but prefer `blanks`.) |
| `word_bank` | `tokens`: `{ "en": [...], "ru": [...] }` in the CORRECT order (the UI shuffles); `distractors`: `{ "en": [...], "ru": [...] }` (0-3 wrong tokens, may be empty lists) |
| `sort_steps` | `steps`: 3-6 of `{ "id", "text": {en,ru} }` in the CORRECT order (the UI shuffles) |
| `match_pairs` | `pairs`: 3-5 of `{ "id", "left": {en,ru}, "right": {en,ru} }` |
| `predict_output` | same as `multiple_choice` plus a REQUIRED `code` snippet the learner predicts the behaviour/output of |
| `spot_bug` | same as `multiple_choice` plus a REQUIRED `code` snippet containing a subtle defect |

## Atom rules

- 8-15 atoms, the LAST of which is the capstone (`"capstone": true`, see below).
  Each non-capstone atom is ONE idea, stated in one sentence (`summary`).
- Order atoms from simple to complex; later atoms may build on earlier ones.
- `id`s are meaningful kebab-case, unique across the file. They key saved
  progress — choose them once and well.
- Per atom: 1-3 `discovery` exercises and 3-6 `practice` exercises. Target
  40-70 exercises total for the whole file — reinforcement needs volume.
- Vary exercise types; do not make everything `multiple_choice`.

## Discovery = prediction first, then TEACH

Discovery exercises assume the learner has NOT read any theory. Each one asks
the learner to PREDICT or DECIDE something ("what does this print?", "what
happens next?", "true or false?"). Never lecture inside `prompt`; the prompt
sets up the situation and asks.

The teaching happens AFTER the answer, in the `reveal` card — not in `feedback`.
Keep `feedback.correct` / `feedback.incorrect` to a single short verdict line
(and each wrong MC option's `feedback` to one line naming the misconception).
The real explanation lives in `reveal`.

## Reveal = the teaching card (this is where understanding is built)

`reveal` is the most important field. It is Markdown, shown after the learner
answers, and is where the concept is actually taught. This is what makes the
lesson prepare someone for an interview instead of just quizzing them.

EVERY discovery exercise MUST have a `reveal`. Add one to a practice exercise
too when it is the trickiest drill of its atom. A good `reveal`:

- **Defines any new term before it is relied on.** A learner meeting "bucket",
  "spread", "treeify", "red-black tree", "load factor", "ConcurrentHashMap",
  etc. for the first time must be told what it means. Never test a term in a
  later exercise that no earlier `reveal` has introduced.
- **Gives the WHY, not just the WHAT.** Why does this step exist, why this
  value, why this behaviour. ("Why rehash? The index depends on capacity, so
  when capacity doubles most entries move.")
- **Shows a worked example when there is a computation** (e.g. the actual bit
  math of `hash & (capacity - 1)`, laid out in a plain ``` code block).
- **Includes a small diagram when it aids understanding**, embedded as a fenced
  ```mermaid block inside the Markdown (valid Mermaid 11 per
  `prompts/mermaid-guide.md`, labels translated per language). Do NOT use
  `<br/>` in labels — diagrams render in strict mode and it would show
  literally. Keep diagrams small (a handful of nodes).

Length: a `reveal` is allowed to be a short paragraph or two plus one diagram —
much longer than `feedback`. But keep it to ONE idea (the atom's idea), shown
after an action. It is the ADHD-friendly middle ground: not a wall of text, but
enough to actually understand. Err toward explaining more here, not less.

Across the whole file, sequence terms so each is introduced in a discovery
`reveal` BEFORE any practice exercise drills it (discovery units always run
before practice units).

## Practice = standalone reinforcement

Practice exercises are re-asked later in a global mixed review across many
topics, shuffled and out of context. Every practice exercise must therefore be
fully self-contained: no "as we saw above", no references to other exercises,
and the `prompt` names its subject explicitly (e.g. "In HashMap, ..." instead
of "In this structure, ...").

## Difficulty comes from TWO axes

Do not make everything hard the same way. Difficulty has two independent axes;
use both.

**Axis 1 — retrieval format** (per atom, one fact): recognize -> arrange ->
produce. Re-ask each atom's core fact in progressively harder FORMATS so recall
strengthens — but NEVER repeat the identical exercise (spaced repetition of the
same question is the global Review's job).

1. **Recognition** (discovery + first practice): `multiple_choice`,
   `true_false`, `predict_output`, `spot_bug` — pick the right answer.
2. **Arrange** (middle practice): `word_bank`, `sort_steps`, `match_pairs` —
   assemble/order the answer from given parts.
3. **Produce** (last practice): `fill_blank` — type the answer from memory.

Put recognition FIRST and `fill_blank` LAST in each atom's `practice`; the engine
flattens practice round-robin, so the whole practice phase ramps recognition ->
recall. The LAST practice exercise of an atom should be a produce format
(`fill_blank`/`word_bank`) — BUT only when the fact is nameable/computable (a
term, method name, formula, number). A purely CONCEPTUAL judgement or
misconception ("does a collision overwrite?", "is it thread-safe?") has no
natural typed answer — keep it as `true_false`/`multiple_choice` rather than
forcing an artificial blank. For formulas and step sequences, use a MULTI-BLANK
`fill_blank` (several `___` + a `blanks` array) so the learner types each part.

**Axis 2 — cognition** (across atoms): recall -> apply -> SYNTHESIZE. A fact
recalled in isolation is easy; applying it in a scenario is harder; combining
several atoms is hardest and closest to the Boss Fight. Deliver this axis with a
capstone atom (below).

## The capstone atom (final synthesis)

End the file with exactly ONE atom marked `"capstone": true`. It does not teach a
new idea — it makes the learner INTEGRATE the earlier atoms, which is what the
Boss Fight demands. The engine renders its practice as a dedicated final block
right before the Boss Fight (the difficulty peak), not mixed into the round-robin.

The capstone atom has:

- One `discovery` exercise: a cold, multi-step synthesis prediction
  (`predict_output` of a scenario touching several atoms), with a `reveal` that
  shows the whole pipeline (a diagram tying the atoms together).
- 3-6 `practice` exercises, all synthesis, escalating recognition -> arrange ->
  produce: e.g. `predict_output`/`spot_bug` over multi-step code that combines
  two or three atoms; a `sort_steps` of the COMPLETE operation (including its
  edge cases); a `match_pairs` linking each mechanism to its role across the
  whole flow; a final MULTI-BLANK `fill_blank` of the key formula(s)/answer.

Every capstone exercise must still be self-contained (it goes into the global
review pool too). Do NOT restate the boss questions verbatim.

## Content rules

- Misconception-driven distractors: wrong options must be REAL, tempting
  misconceptions, each with its own `feedback` explaining why it is wrong.
- Bilingual: every localized field in natural English AND natural Russian.
  Code, identifiers and technical tokens stay in English in both languages.
- NEUTRAL style: the source explanation may weave themed analogies (factories,
  sports, games, production war stories) into its prose. IGNORE the analogies —
  extract only the technical facts. Exercises must be short neutral stimuli
  with zero narrative decoration.
- Grounding: use ONLY facts present in the supplied explanation. Do not invent
  APIs, numbers, version behaviours or terminology that the explanation does
  not contain.
- Mermaid diagrams: use them where they clarify a structure or flow (typically
  inside a discovery `reveal`). Valid Mermaid 11 per `prompts/mermaid-guide.md`,
  labels translated per language, no `<br/>` in labels, kept small. Reuse the
  shapes already in the source explanation's diagrams when it has them.
- The boss-fight questions of this topic are appended below FOR CONTEXT ONLY:
  the atoms should collectively prepare the learner to answer them, but do not
  restate them verbatim and do not include them in the file.

## Example atom (shape reference)

```json
{
  "id": "collisions",
  "title": { "en": "Collisions", "ru": "Коллизии" },
  "summary": {
    "en": "Different keys can land in the same bucket; equals() distinguishes them inside it.",
    "ru": "Разные ключи могут попасть в один бакет; внутри него их различает equals()."
  },
  "discovery": [
    {
      "id": "collisions-d1-two-keys",
      "type": "multiple_choice",
      "prompt": {
        "en": "\"Aa\" and \"BB\" have the same hashCode(). What happens when you put both into one HashMap?",
        "ru": "У \"Aa\" и \"BB\" одинаковый hashCode(). Что произойдёт, если положить оба ключа в один HashMap?"
      },
      "options": [
        { "id": "a", "text": { "en": "Both are stored in one bucket as a chain", "ru": "Оба сохранятся в одном бакете цепочкой" }, "correct": true },
        { "id": "b", "text": { "en": "The second put overwrites the first entry", "ru": "Второй put перезапишет первую запись" }, "correct": false,
          "feedback": { "en": "Overwrite happens only when equals() says the keys are equal.", "ru": "Перезапись происходит, только когда equals() считает ключи равными." } },
        { "id": "c", "text": { "en": "put throws an exception", "ru": "put бросит исключение" }, "correct": false,
          "feedback": { "en": "A collision is a normal, expected case.", "ru": "Коллизия — нормальный ожидаемый случай." } }
      ],
      "reveal": {
        "en": "A **collision** is when two different keys land in the same bucket. HashMap does not lose either one: it stores them in that bucket as a small linked chain (**separate chaining**).\n\n```mermaid\nflowchart LR\n  subgraph table[\"bucket array\"]\n    b0[\"bucket 0\"]\n    b5[\"bucket 5: Aa=1 -> BB=2\"]\n    b7[\"bucket 7\"]\n  end\n```\n\nTo find a key later, HashMap goes to the bucket and walks the chain, comparing each stored key with `equals()`. Same hash means same bucket — it does NOT mean the keys are equal.",
        "ru": "**Коллизия** — это когда два разных ключа попадают в один бакет. HashMap не теряет ни один: он хранит их в этом бакете небольшой связной цепочкой (**separate chaining**).\n\n```mermaid\nflowchart LR\n  subgraph table[\"массив бакетов\"]\n    b0[\"бакет 0\"]\n    b5[\"бакет 5: Aa=1 -> BB=2\"]\n    b7[\"бакет 7\"]\n  end\n```\n\nЧтобы позже найти ключ, HashMap идёт в бакет и проходит цепочку, сравнивая каждый ключ через `equals()`. Одинаковый хэш означает общий бакет — но НЕ означает, что ключи равны."
      },
      "feedback": {
        "correct": { "en": "Right — same hash, same bucket, both kept.", "ru": "Верно — одинаковый хэш, один бакет, оба сохранены." },
        "incorrect": { "en": "Not quite — see below.", "ru": "Не совсем — смотри ниже." }
      }
    }
  ],
  "practice": [
    {
      "id": "collisions-p1-fill",
      "type": "fill_blank",
      "prompt": { "en": "Type the missing method name.", "ru": "Впиши имя пропущенного метода." },
      "text": { "en": "Inside one bucket, HashMap tells keys apart using ___().", "ru": "Внутри одного бакета HashMap различает ключи с помощью ___()." },
      "answers": { "en": ["equals"], "ru": ["equals"] },
      "feedback": {
        "correct": { "en": "hashCode() picks the bucket; equals() picks the key inside it.", "ru": "hashCode() выбирает бакет; equals() — ключ внутри него." },
        "incorrect": { "en": "It is equals(): hashCode() only chooses the bucket.", "ru": "Это equals(): hashCode() лишь выбирает бакет." }
      }
    }
  ]
}
```

(A real atom has 3-6 practice exercises; this example is truncated.)

## Validation

After writing the file, re-read it and verify:

1. It parses as JSON.
2. `schemaVersion` is 1 and `topicId` matches the topic id given below.
3. 8-15 atoms; every atom has >= 1 discovery and >= 2 practice exercises.
4. All ids are kebab-case and unique across the file.
5. Every localized field has non-blank `en` AND `ru`.
6. Per-type payloads are complete (exactly one correct option; a fill_blank's
   number of `___` matches its `blanks` count; >= 3 sort steps; 3-5 match pairs;
   code present for predict_output / spot_bug).
7. Each atom's `practice` ramps recognition → arrange → produce, ending in a
   produce format where the fact is nameable (conceptual facts stay MC/true_false).
8. EVERY discovery exercise has a `reveal` that teaches its atom's idea (why +
   worked example / diagram where useful); `feedback` strings are short verdicts.
9. Every term used in the file is introduced in some earlier discovery `reveal`
   before it is tested.
10. Exactly one atom, the LAST, has `"capstone": true` with a cold-synthesis
    discovery and integrative practice exercises.
