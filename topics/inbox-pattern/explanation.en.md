# Inbox Pattern

> **Teaching model.** The runnable code uses `VisualInbox`, a learning model that
> reproduces the idea an interviewer asks about — an idempotent consumer that
> deduplicates redelivered messages against an **inbox table** — and emits trace
> events the panel on the right replays. The concrete scenario is a **payment
> consumer**: each message carries an effect (an `amount` to charge). The
> *behaviour you reason about* is the real pattern.

## The problem it solves

Message brokers (Kafka, RabbitMQ, SQS, …) almost never give you **exactly-once**
delivery. The practical guarantee is **at-least-once**: if a consumer crashes
after doing the work but *before* acknowledging the message, or the ack is lost,
or a consumer group rebalances, the broker will **redeliver the same message**.

A naive consumer applies the side effect every time it sees the message. Under
at-least-once that means **double charges, duplicate orders, double emails**. The
Inbox pattern makes the consumer **idempotent** so a redelivered message is a
no-op.

## The mental model

Keep an **inbox table** in the *same database* as your business data. Each
incoming message has a stable **unique message id**. For every message, in **one
local transaction**:

1. **Dedup check** — is this id already in the inbox table?
2. If **yes** → it's an at-least-once redelivery. Skip it; apply no effect.
3. If **no** → insert the id into the inbox **and** do the business work, then
   commit. A `UNIQUE` constraint on the id is the real guard.

```
broker (at-least-once) ──▶ [ dedup by id | do work ]  one local TX
                                  │
                            inbox table: { m1, m2, m3, ... }
```

Because the dedup record and the business write live in the **same ACID
transaction**, they commit or roll back together. That is what upgrades
at-least-once delivery into **effectively-once processing**.

## Why one transaction is non-negotiable

If you recorded the id in one transaction and did the work in another, a crash
between them breaks you both ways:

- Work commits, id record doesn't → the next redelivery does the work **again**.
- Id record commits, work doesn't → the message is marked done but the effect
  **never happened** (silent loss).

Same DB, same transaction, atomic. (If the side effect is in an external system
you can't enlist, you need a different tactic — make the downstream call itself
idempotent with the message id as its key.)

## The idempotency key is the message id, not the payload

Dedup by the **stable unique id** the producer stamps on the message. Two
genuinely different events can carry an identical body (a customer really did buy
the same item twice) — dedup by payload would silently drop the second real
purchase. Conversely the same logical message must keep the **same id** across
redeliveries, so the producer (not the consumer) must assign it.

## Inbox vs Outbox

They are two halves of reliable messaging and often appear together:

- **Outbox** (producer side) — write the business change and an "to-send" event
  into the same DB transaction, then a relay publishes the event. Solves
  "publish exactly when the DB commits."
- **Inbox** (consumer side) — dedup incoming messages so at-least-once delivery
  is processed effectively-once. Solves "handle each message's effect once."

## Keeping the table from growing forever

The inbox grows with every distinct message, so you purge old ids on a
**retention policy**. The trap: the retention window must be **longer than the
broker's maximum redelivery delay**. Purge too early and a late duplicate of a
forgotten id looks brand new — and gets processed a second time.

## Interview answer (60 seconds)

> Brokers give at-least-once delivery, so the same message can arrive more than
> once and a naive consumer would double-apply the effect. The Inbox pattern
> makes the consumer idempotent: you keep an inbox table of processed message
> ids, and for each message, in one local transaction, you check whether the id
> is already there — if so you skip it, otherwise you record the id and do the
> business work together and commit. Because the dedup record and the work share
> one transaction, at-least-once becomes effectively-once. The key is the
> producer's stable message id, not the payload, and you purge old ids on a
> retention window longer than the broker's redelivery delay. It's the
> consumer-side counterpart of the Outbox pattern.

## Common misconceptions

- ❌ "It gives true exactly-once delivery." — No. Delivery is still
  at-least-once; the inbox gives effectively-once *processing*. End-to-end
  exactly-once delivery is impossible across an unreliable network (the
  two-generals problem).
- ❌ "Dedup by the message body." — Use the stable unique message id. Identical
  payloads can be distinct legitimate events.
- ❌ "Record the id and do the work in separate transactions." — A crash between
  them either reprocesses or silently loses the effect. They must be atomic.
- ❌ "You can keep the inbox table forever, so cleanup doesn't matter." — It grows
  unboundedly; but purge too aggressively and late duplicates reprocess. The
  window must outlast max redelivery delay.
- ❌ "If the work is naturally idempotent you still need an inbox." — Not always;
  if applying the effect twice is genuinely harmless (e.g. an idempotent `SET`),
  you may not need dedup at all. The inbox matters when the effect is *not*
  idempotent.
- ❌ "Inbox and Outbox are the same thing." — Outbox is producer-side reliable
  publishing; Inbox is consumer-side idempotent consumption.
