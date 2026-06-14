# Outbox Pattern

> **Teaching model.** The runnable code uses `VisualOutbox`, a learning model that
> reproduces the idea an interviewer asks about — a service that must change its
> database **and** publish an event, doing both reliably through an **outbox
> table** — and emits trace events the panel on the right replays. The concrete
> scenario is an **order service**: placing an order saves it to the business
> table and must publish an `OrderPlaced` event. The *behaviour you reason about*
> is the real pattern.

## The problem it solves

A service often has to do two things for one logical change: **update its own
database** and **tell the outside world** by publishing a message/event to a
broker (Kafka, RabbitMQ, SQS, …). The database and the broker are **two separate
systems**, and you cannot wrap them in a single transaction.

So the naive code does a **dual write** — two independent steps:

```
1) commit the order to the DB
2) publish the OrderPlaced event to the broker
```

Whatever order you pick, a crash between the steps breaks you:

- **Commit, then crash before publish** → the order exists but no one is told
  (silent event loss).
- **Publish, then the DB transaction rolls back** → the world is told about an
  order that never happened (phantom event).

That is the **dual-write problem**: two systems, no shared transaction, an
inconsistency window in between.

## The mental model

Put an **outbox table** in the **same database** as your business data. For each
change, in **one local transaction**:

1. Write the business change (the order).
2. Insert an **event row** into the outbox table (status `PENDING`).
3. Commit — both rows commit together, or neither does.

Then a separate **relay** moves events out of the database to the broker:

```
[ write order | insert outbox row ]  one local TX      relay (poller / CDC)
            business DB ───────────────────────────▶  broker
              orders        outbox: {e1 PENDING…}        OrderPlaced…
```

Because the business write and the outbox row share **one ACID transaction**,
there is **no window** where the change happened without the event being staged
(or vice versa). "Publish exactly when the DB commits" becomes guaranteed.

## The relay: getting events out

The transaction only **stages** the event; it never talks to the broker. A
separate process publishes staged rows:

- **Polling publisher** — periodically `SELECT … WHERE status = 'PENDING'`,
  publish each, mark it `PUBLISHED` (or delete it).
- **Change-data-capture (CDC)** — tail the database transaction log (e.g.
  Debezium) and publish outbox inserts. No polling, lower latency.

Either way the relay is **decoupled** from the business transaction, so a slow or
temporarily-down broker never blocks or fails your commits.

## The relay is at-least-once

The relay publishes, then marks the row published — two steps again. If it
crashes **after sending but before marking**, the row stays `PENDING` and the
next poll **sends the event again**. So the broker can see **duplicates**: the
Outbox guarantees *at-least-once* delivery, not exactly-once. Consumers must
**dedup** — which is exactly the **Inbox pattern** (idempotent consumer).

## Outbox vs Inbox

Two halves of reliable messaging, often used together:

- **Outbox** (producer side) — atomically stage the event with the business
  change, then relay it. Solves "publish exactly when the DB commits."
- **Inbox** (consumer side) — dedup incoming messages so at-least-once delivery
  is processed effectively-once. Solves "apply each message's effect once."

## Keeping the table from growing forever

Published rows pile up, so you either **delete on publish** or sweep old
`PUBLISHED` rows on a retention job. With CDC you often delete immediately after
the insert is captured. Either way, don't let the outbox grow without bound.

## Interview answer (60 seconds)

> When a service must update its database and publish an event, those are two
> systems with no shared transaction, so doing them as separate steps — a dual
> write — risks losing the event or publishing a phantom one if it crashes in
> between. The Outbox pattern fixes this: in one local transaction you write the
> business change and insert an event row into an outbox table in the same
> database, so they commit atomically. A separate relay — a poller or
> change-data-capture — then reads unpublished rows and publishes them to the
> broker, marking them published. The relay is at-least-once, so it can resend on
> a crash; consumers dedup with the Inbox pattern. It's the producer-side
> counterpart of the Inbox pattern.

## Common misconceptions

- ❌ "Just write the DB and publish to the broker in one transaction." — You
  can't. They are different systems; there is no shared ACID transaction across
  a database and a broker (distributed transactions / XA are heavyweight,
  poorly supported, and usually avoided).
- ❌ "The Outbox gives exactly-once delivery." — No. The relay is at-least-once
  and can resend after a crash. You get reliable, eventually-published events,
  plus possible duplicates that consumers must dedup.
- ❌ "The business transaction publishes to the broker." — It must not. The
  transaction only **stages** the event; a separate relay publishes it, so the
  broker being slow or down can't block your commits.
- ❌ "Stage the event in a separate transaction from the business change." — Then
  you're back to a dual write. The whole point is one atomic local transaction.
- ❌ "Outbox and Inbox are the same thing." — Outbox is producer-side reliable
  publishing; Inbox is consumer-side idempotent consumption.
- ❌ "You can let the outbox table grow forever." — Published rows must be deleted
  or swept, or the table grows unbounded.
