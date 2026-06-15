# Mermaid diagram guide for topic explanations

Use these as **ready-to-copy templates** when adding diagrams to
`explanation.en.md` / `explanation.ru.md`. Pick the type that fits the concept,
then adapt the labels. Keep labels as **English technical terms** so the same
diagram block is byte-identical in both language files (only the surrounding
prose is translated).

General rules:

- Target **Mermaid 11** syntax. Validate it mentally — a broken diagram falls back
  to raw text in the app.
- Keep each diagram focused: roughly **≤ 12 nodes**. Split a big idea into two.
- Quote any label with spaces or punctuation: `B["index = hash & (n-1)"]`.
- Do **not** use `%%{init}%%`, custom themes, `click` handlers or raw HTML — the app
  applies its own dark theme and sanitizes the SVG.

---

## flowchart / graph — data structures, algorithms, memory layout

Best for: how something is structured, or how a decision/algorithm flows.

```mermaid
flowchart LR
  K["key"] --> H["hash(key)"]
  H --> I["index = hash & (n-1)"]
  I --> B0["bucket 0"]
  I --> B1["bucket 1"]
  B1 --> N1["Node A"] --> N2["Node B (collision)"]
```

Memory layout example:

```mermaid
graph TD
  subgraph JVM
    Heap["Heap (objects)"]
    Stack["Stack (frames, locals)"]
    Meta["Metaspace (class metadata)"]
  end
  Heap --> Young["Young Gen"]
  Heap --> Old["Old Gen"]
```

---

## sequenceDiagram — interactions over time

Best for: threads communicating, request/response flow, transaction steps.

```mermaid
sequenceDiagram
  participant T1 as Thread 1
  participant L as Lock
  participant T2 as Thread 2
  T1->>L: acquire()
  Note over T1: holds lock
  T2->>L: acquire() (blocks)
  T1->>L: release()
  L-->>T2: granted
```

---

## stateDiagram-v2 — lifecycles and state machines

Best for: Thread states, bean lifecycle, connection/transaction states.

```mermaid
stateDiagram-v2
  [*] --> NEW
  NEW --> RUNNABLE: start()
  RUNNABLE --> BLOCKED: wait for monitor
  RUNNABLE --> WAITING: wait()
  WAITING --> RUNNABLE: notify()
  RUNNABLE --> TERMINATED: run() returns
  TERMINATED --> [*]
```

---

## classDiagram — type relationships, inheritance, design patterns

Best for: OOP hierarchies, interface/implementation, pattern roles.

```mermaid
classDiagram
  class Collection
  class List
  class ArrayList
  class LinkedList
  Collection <|-- List
  List <|.. ArrayList
  List <|.. LinkedList
  ArrayList : Object[] elementData
  LinkedList : Node first
  LinkedList : Node last
```

---

## erDiagram — table / entity relationships

Best for: SQL schema, JPA entity relationships.

```mermaid
erDiagram
  CUSTOMER ||--o{ ORDER : places
  ORDER ||--|{ ORDER_ITEM : contains
  PRODUCT ||--o{ ORDER_ITEM : "appears in"
  CUSTOMER {
    bigint id PK
    string email
  }
  ORDER {
    bigint id PK
    bigint customer_id FK
  }
```
