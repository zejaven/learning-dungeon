# ArrayList vs LinkedList

> **Teaching model.** The runnable code uses `VisualArrayList` and
> `VisualLinkedList`, learning models that reproduce the ideas an interviewer
> asks about — backing array, grow-and-copy, shifts, a doubly-linked chain, and
> walking by hops — and emit trace events the panel on the right replays. They
> are **not** the JDK classes (e.g. the array starts at capacity 4 instead of 10
> so a grow is visible after a few adds). The *behaviour you reason about* is the
> same.

## The mental model

Both `ArrayList` and `LinkedList` implement `List`, so their **API is
identical**. Their **internals are opposites**, and that is the whole interview.

- **`ArrayList`** is a **resizable array**. Elements live in contiguous slots
  `[0], [1], [2]…`. Because it is an array, the address of slot `i` is just
  `base + i`, so reaching any element is **O(1)**.
- **`LinkedList`** is a **doubly-linked chain of nodes**. Each node holds a
  value plus a `prev` and `next` pointer. There is no array to index into, so to
  reach element `i` you must **walk** the chain one node at a time — **O(n)**.

## Cost, operation by operation

| Operation              | ArrayList            | LinkedList                |
| ---------------------- | -------------------- | ------------------------- |
| `get(i)` / `set(i)`    | **O(1)** (index)     | **O(n)** (walk)           |
| `add(e)` (at end)      | **O(1)** amortised   | **O(1)**                  |
| `addFirst` / `addLast` | O(n) at front, O(1) end | **O(1)** both ends     |
| `add(i, e)` (middle)   | O(n) (shift right)   | O(n) walk + **O(1)** link |
| `remove(i)` (middle)   | O(n) (shift left)    | O(n) walk + **O(1)** link |

Two phrases explain the table:

- **ArrayList shifts.** Inserting or removing anywhere but the end means moving
  every following element one slot over. Insert at the front = shift everything.
- **ArrayList grows.** When the backing array is full, it allocates a new array
  (×1.5 in the JDK) and copies all elements across. That copy is why append is
  only *amortised* O(1): most adds are O(1), but the occasional grow is O(n),
  averaging out to O(1) per add.
- **LinkedList walks but never shifts.** Inserting in the middle only changes two
  pointers — but you first pay O(n) to *find* the node. `get(i)` walks from the
  **nearer end** (head or tail), so `get(size-1)` is cheap, `get(size/2)` is the
  worst case.

## So which one?

In practice, **`ArrayList` is the default and almost always the right choice.**
Even for queue-like add/remove at the ends, `ArrayDeque` beats `LinkedList`.
`LinkedList` wins only when you frequently insert/remove **at a position you
already hold** (e.g. via an `Iterator` / `ListIterator`), so you skip the walk.
Its per-node object overhead and poor cache locality usually make it slower in
real benchmarks even where Big-O suggests it should win.

## Interview answer (60 seconds)

> `ArrayList` is backed by a resizable array; `LinkedList` is a doubly-linked
> list. Because `ArrayList` is an array, `get(i)` is O(1), and appending is
> amortised O(1) — when it fills up it copies into an array 1.5× larger.
> Inserting or removing in the middle is O(n) because it shifts elements.
> `LinkedList` has O(1) add/remove at the ends and at a known node, but `get(i)`
> is O(n) because it walks the chain. In practice I default to `ArrayList`; its
> cache locality and lack of per-node overhead usually beat `LinkedList`, and for
> a deque I'd reach for `ArrayDeque`.

## Common misconceptions

- ❌ "LinkedList is faster for inserts." — Only if you already hold the node.
  Inserting *at an index* still costs an O(n) walk to find the position.
- ❌ "ArrayList.add is O(1)." — It is *amortised* O(1); a full array triggers an
  O(n) grow-and-copy.
- ❌ "LinkedList saves memory." — The opposite: every element carries an extra
  node object with two pointers.
- ❌ "Random access is fine on a LinkedList." — `get(i)` in a loop turns an O(n)
  algorithm into O(n²); iterate with the iterator instead.
- ❌ "They behave differently to callers." — No; both implement `List` with the
  same API. Only performance differs.
