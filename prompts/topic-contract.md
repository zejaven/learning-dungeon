# Topic plugin contract

A topic is a folder `topics/<id>/` with this exact layout:

```
topics/<id>/
  topic.yaml          metadata + examples list + primitives + defaultExample
  explanation.md      prose: intuition, interview answer, traps
  examples/
    01-*.java         full `public class Playground { public static void main ... }`
    02-*.java
  visualizer.tsx      default-exported React component rendering event state
  trace-schema.json   JSON Schema for the `state` field + list of event types
  quiz.yaml           missions (event-checked) + bossFight questions
```

## topic.yaml

```yaml
id: <kebab-id>                  # must equal the folder name
title: <Human Title>
category: <e.g. Java Core / Collections>
type: <DATA_STRUCTURE | CONCURRENCY | ...>
summary: <one paragraph>
primitives: [ArrayGrid, LinkedNodes, EventLog]   # primitives the visualizer uses
defaultExample: <example-id>    # which example loads by default
examples:
  - id: <example-id>
    title: <button label>
    file: 01-basic.java         # relative to examples/
    explanation: <what it shows>
missionsFile: quiz.yaml
```

## Trace events (the core contract)

User code drives an instrumented `visual.*` model. Each model call emits a line
`@@TRACE@@{json}` to stdout via `visual.Trace.event(...)`. The backend strips
these from program output and returns them as `traceEvents`. The frontend replays
them step by step, passing each event's `state` to `visualizer.tsx`.

Event envelope (fixed):

```json
{
  "step": 3,
  "event": "HASHMAP_PUT",
  "description": "human-readable sentence shown in the EventLog",
  "highlight": ["bucket:0", "node:Aa"],
  "state": { "...": "topic-specific; must match trace-schema.json" }
}
```

`visual.Trace.event(String event, String description, List<String> highlight, Object state)`
serializes `state` from Maps/Lists/String/Number/Boolean. Build `state` as a
`LinkedHashMap` so key order is stable.

## visualizer.tsx

```tsx
import type { TraceEvent } from '../../frontend/src/engine/traceTypes';
import { ArrayGrid } from '../../frontend/src/primitives/ArrayGrid';
// ...compose primitives...

export default function Visualizer({ event }: { event: TraceEvent | null }) {
  const state = event?.state as MyState | undefined;
  // render `state` with primitives; use event.highlight to emphasize parts.
  return /* ... */;
}
```

The component is pure: given the current event, render the structure. It never
calls the backend and never runs Java.

## quiz.yaml

```yaml
missions:
  - id: <id>
    title: <short>
    goal: <what the learner should make happen>
    event: <TRACE_EVENT_TYPE that completes this mission>
bossFight:
  - <interview question>
```
