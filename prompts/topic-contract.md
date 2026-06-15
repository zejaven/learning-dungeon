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

## Bilingual rule

Everything shown in the UI must exist in **both English and Russian**. Keep code,
identifiers and technical terms (Java, HashMap, hashCode, resize, …) untranslated.
Translatable YAML fields use a `{ en, ru }` map; explanation is two files.
Java source code (including comments) stays in English — only the trace
*descriptions* are bilingual.

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
summary:
  en: <one paragraph>
  ru: <один абзац>
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
