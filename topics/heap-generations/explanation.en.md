# JVM Heap Generations: Eden, Survivor (S0/S1) and Old

## The intuition

Watch a real program and you notice something: the vast majority of objects die
almost immediately. A temporary `StringBuilder`, the boxed `Integer` in a loop,
the DTO you map and throw away — born and dead within milliseconds. A few objects,
though, live for the entire run: caches, connection pools, the Spring context.

This is the **generational hypothesis**: *most objects die young, and the ones
that don't tend to live a very long time.* The JVM heap is shaped around it.

Instead of one big pile that the collector must scan in full every time, the heap
is split into a **Young generation** and an **Old (Tenured) generation**:

- **Eden** — where new objects are born. Allocation is just bumping a pointer, so
  it is almost free.
- **Two Survivor spaces, S0 and S1** — small staging areas where objects that
  survived a collection wait and "age".
- **Old generation** — where long-lived objects end up.

A **minor GC** collects only the Young generation. Because almost everything in
Eden is already dead, it has very little to copy and runs in milliseconds. The
Old generation is collected only by a much rarer, more expensive **major (full)
GC**. Splitting the heap is what lets the common case (clearing out short-lived
garbage) be cheap.

## How an object moves through it

1. **Born in Eden.** New objects allocate here with a bump pointer.
2. **Minor GC.** When Eden fills up, a minor GC runs. It is a *copying* collector:
   it copies the **live** objects out and simply abandons the rest — dead objects
   cost nothing to reclaim because they are never touched. Each survivor's **age**
   is incremented.
3. **Bounce between Survivors.** Live young objects are copied into the *empty*
   Survivor space (S0, then S1, then S0…). One survivor is always kept empty so
   the other (plus Eden) can be evacuated into it. **That is why there are two.**
4. **Promotion (tenuring).** When an object's age reaches the **tenuring
   threshold**, it is promoted to the Old generation — it has proven it is
   long-lived, so stop copying it around on every minor GC.
5. **Major GC.** Eventually the Old generation fills and a major/full GC scans it.
   This is the expensive one you try to avoid.

## Why two Survivor spaces (the key trick)

A copying collector needs a *destination* that is empty. With two survivor spaces
you always have one empty space to copy the live objects into; afterwards the
roles swap. This gives **automatic compaction** (survivors are packed together,
no fragmentation) at the cost of keeping one survivor space empty at all times.
One survivor space would not work — you'd have nowhere to copy to.

## 60-second interview answer

> The heap is generational because most objects die young. New objects are
> allocated cheaply in **Eden**. When Eden fills, a **minor GC** runs: it's a
> copying collector, so it only copies the live objects — dead ones are free to
> drop — and ages the survivors, bouncing them between the two **Survivor
> spaces** (one is always empty so the other can be evacuated into it, which also
> compacts them). An object that survives enough minor GCs reaches the **tenuring
> threshold** and is **promoted to the Old generation**, which holds long-lived
> objects and is collected only by a rare, expensive **major/full GC**. The
> whole split exists so the frequent collection of short-lived garbage stays
> cheap.

## Production relevance

- **Minor GCs are cheap, major GCs hurt.** Latency-sensitive services tune the
  heap so most garbage dies in the young gen and full GCs are rare.
- **Sizing the young gen matters.** Too small → frequent minor GCs and premature
  promotion; too large → longer (but rarer) minor pauses.
- **Premature promotion pollutes Old.** If objects get promoted before they die,
  the Old generation fills with garbage and you pay for more full GCs.
- Modern collectors (**G1, ZGC, Shenandoah**) still use these generational ideas;
  G1 has logical Eden/Survivor/Old regions rather than fixed contiguous spaces.

## Common traps & misconceptions

- **"Eden, Gen 1, Gen 2" is loose terminology.** The real names are Eden + two
  Survivor spaces (= the **Young** generation) and the **Old**/Tenured
  generation. "Gen 1 / Gen 2" usually means *young vs old* (or *survivor vs old*),
  not three independent generations. The **PermGen** was removed in Java 8 and
  replaced by **Metaspace**, which is *not* part of the heap at all.
- **A minor GC never touches the Old generation.** Dropping the last reference to
  an Old-gen object does **not** free it on the next minor GC — only a major/full
  GC reclaims Old.
- **"GC is slow because it scans everything."** A copying minor GC scans only the
  *live* objects; dead objects are never visited, so a heap full of garbage is
  actually cheap to collect.
- **The tenuring threshold is adaptive.** It is not a fixed constant; the JVM can
  lower it dynamically when survivor spaces are under pressure.
- **More survivor spaces would not help.** Two is exactly what a copying collector
  needs: a "from" space and an empty "to" space.

> This visualizer is a simplified model: you mark garbage explicitly with
> `drop(...)` instead of a real reachability graph, sizes are tiny, and there is
> no compaction detail or `OutOfMemoryError`. The *mechanics* — born in Eden,
> copy-and-age through the Survivors, promote to Old, collect Old only on a full
> GC — match the real JVM.
