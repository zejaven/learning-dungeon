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
  "mermaid": { "en": "...", "ru": "..." },  // OPTIONAL static Mermaid 11 diagram, use sparingly
  "feedback": {
    "correct":   { "en": "...", "ru": "..." },   // max 1-2 sentences of theory
    "incorrect": { "en": "...", "ru": "..." }    // max 1-2 sentences of theory
  }
}
```

Exercise types and their extra fields:

| type | extra fields |
|---|---|
| `multiple_choice` | `options`: 3-4 of `{ "id", "text": {en,ru}, "correct": bool, "feedback": {en,ru} }` — EXACTLY one `correct: true`; every wrong option carries its own `feedback` explaining the misconception |
| `true_false` | `answer`: boolean; the statement to judge goes in `prompt` |
| `fill_blank` | `text`: {en,ru} each containing exactly one `___`; `answers`: `{ "en": ["accepted", ...], "ru": ["...", ...] }` — short, unambiguous, usually a technical token identical in both languages; include common casing variants |
| `word_bank` | `tokens`: `{ "en": [...], "ru": [...] }` in the CORRECT order (the UI shuffles); `distractors`: `{ "en": [...], "ru": [...] }` (0-3 wrong tokens, may be empty lists) |
| `sort_steps` | `steps`: 3-6 of `{ "id", "text": {en,ru} }` in the CORRECT order (the UI shuffles) |
| `match_pairs` | `pairs`: 3-5 of `{ "id", "left": {en,ru}, "right": {en,ru} }` |
| `predict_output` | same as `multiple_choice` plus a REQUIRED `code` snippet the learner predicts the behaviour/output of |
| `spot_bug` | same as `multiple_choice` plus a REQUIRED `code` snippet containing a subtle defect |

## Atom rules

- 8-15 atoms. Each atom is ONE idea, stated in one sentence (`summary`).
- Order atoms from simple to complex; later atoms may build on earlier ones.
- `id`s are meaningful kebab-case, unique across the file. They key saved
  progress — choose them once and well.
- Per atom: 1-3 `discovery` exercises and 3-6 `practice` exercises. Target
  40-70 exercises total for the whole file — reinforcement needs volume.
- Vary exercise types; do not make everything `multiple_choice`.

## Discovery = prediction first

Discovery exercises assume the learner has NOT read any theory. Each one asks
the learner to PREDICT or DECIDE something ("what does this print?", "what
happens next?", "true or false?"), and the theory arrives only in `feedback` —
maximum 1-2 sentences per feedback string. Never lecture inside `prompt`; the
prompt sets up the situation and asks.

## Practice = standalone reinforcement

Practice exercises are re-asked later in a global mixed review across many
topics, shuffled and out of context. Every practice exercise must therefore be
fully self-contained: no "as we saw above", no references to other exercises,
and the `prompt` names its subject explicitly (e.g. "In HashMap, ..." instead
of "In this structure, ...").

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
- Mermaid diagrams: optional and rare; when used, provide valid Mermaid 11 per
  `prompts/mermaid-guide.md` with translated labels per language.
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
      "feedback": {
        "correct": { "en": "Same hash means same bucket, not same key: both entries live there as a chain.", "ru": "Одинаковый хэш означает один бакет, а не одинаковые ключи: обе записи живут там цепочкой." },
        "incorrect": { "en": "Equal hashes do not mean equal keys: both entries are stored in one bucket as a chain.", "ru": "Равные хэши не означают равные ключи: обе записи хранятся в одном бакете цепочкой." }
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
6. Per-type payloads are complete (exactly one correct option; exactly one
   `___` per fill_blank text; >= 3 sort steps; 3-5 match pairs; code present
   for predict_output / spot_bug).
