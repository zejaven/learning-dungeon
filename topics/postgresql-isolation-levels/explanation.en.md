# PostgreSQL Transaction Isolation Levels

Isolation is the "I" in [ACID](topic:acid-principles): it defines how much one transaction can notice another transaction while both are running. In PostgreSQL, this is built on MVCC, where readers usually see a snapshot instead of blocking writers. Think of a shared kitchen order board: each cook may read either the current board or a photo of the board, depending on the isolation level.

PostgreSQL accepts the four SQL standard names:

- `READ UNCOMMITTED`
- `READ COMMITTED`
- `REPEATABLE READ`
- `SERIALIZABLE`

The important interview detail is that PostgreSQL has only three distinct behaviors here: `READ UNCOMMITTED` behaves like `READ COMMITTED`. Like a post office that refuses to show half-written address labels, PostgreSQL does not expose dirty reads even when you ask for the weakest level.

```mermaid
flowchart LR
  RU["READ UNCOMMITTED"] -- "same behavior in PostgreSQL" --> RC["READ COMMITTED"]
  RC -- "new snapshot per statement" --> RR["REPEATABLE READ"]
  RR -- "SSI checks and possible retry" --> S["SERIALIZABLE"]
```

## The Levels

| Level | PostgreSQL behavior | Interview memory hook |
| --- | --- | --- |
| `READ UNCOMMITTED` | Accepted, but treated as `READ COMMITTED`; dirty reads are still not possible. | Asking to peek into an unfinished parcel still gets you only sealed parcels. |
| `READ COMMITTED` | Default level. Each statement sees rows committed before that statement starts. | Every time you check the kitchen board, you get a fresh view. |
| `REPEATABLE READ` | One transaction-level snapshot is used after the first statement, so later reads see the same database snapshot. PostgreSQL also prevents phantom reads at this level. | You take one photo of the board and keep cooking from that photo. |
| `SERIALIZABLE` | Strongest level. PostgreSQL uses Serializable Snapshot Isolation and can abort one transaction if the concurrent result would not match any serial order. | A traffic controller lets cars move together, but sends one car around again if the crossing order becomes unsafe. |

## READ COMMITTED

`READ COMMITTED` is PostgreSQL's default. Each SQL statement gets its own snapshot of committed data. If transaction A runs `SELECT`, transaction B commits an update, and transaction A runs the same `SELECT` again, the second result can be different. This allows non-repeatable reads and phantoms.

Analogy: a cashier looks at the shelf before each customer request. If a colleague restocks between two looks, the cashier sees the new shelf on the second look.

```mermaid
sequenceDiagram
  participant T1 as Transaction A
  participant DB as PostgreSQL
  participant T2 as Transaction B
  T1->>DB: SELECT balance
  DB-->>T1: snapshot for statement 1
  T2->>DB: UPDATE balance; COMMIT
  T1->>DB: SELECT balance again
  DB-->>T1: new snapshot sees committed update
  Note over T1,DB: READ COMMITTED can return different results
```

For `UPDATE`, `DELETE`, `SELECT FOR UPDATE`, and similar row-locking statements, PostgreSQL may wait for a concurrent updater and then re-check the `WHERE` condition against the newest committed row. Analogy: if two clerks try to edit the same paper form, the second clerk waits, then checks the finished form before deciding whether it still matches the request.

## REPEATABLE READ

`REPEATABLE READ` gives the transaction a stable snapshot. After the first statement, ordinary reads keep seeing the same committed state for the whole transaction, even if other transactions commit changes. PostgreSQL's implementation prevents dirty reads, non-repeatable reads, and phantom reads at this level.

Analogy: a cook takes a dated photo of the pantry and plans the whole recipe from that photo. New groceries may arrive, but this cook's recipe does not suddenly change.

The trap is that PostgreSQL `REPEATABLE READ` is not the same as full serializability. It is snapshot isolation. Some cross-row business rules can still suffer write skew: two transactions read the same stable snapshot, update different rows, and together break an invariant. Analogy: two supervisors each see two people on duty, then each sends a different person home; nobody noticed that the final schedule has too few people.

## SERIALIZABLE

`SERIALIZABLE` is the strongest level. PostgreSQL still uses snapshots for performance, but it also tracks read/write dependency patterns. If concurrent transactions would produce a result that cannot be explained as one transaction running completely before the other, PostgreSQL aborts one with a serialization failure, usually SQLSTATE `40001`.

Analogy: cars can enter an intersection concurrently while the traffic controller watches the paths. If the final movement would be unsafe, one car is sent back to try again.

Because aborts are part of the contract, application code must retry the whole transaction, not just the last statement. This matters in services that use frameworks such as Spring transactions: retry has to wrap the transaction boundary, not sit inside a transaction that is already doomed. Analogy: if a postal form is rejected because the queue changed, you fill and submit the whole form again, not only the final signature line.

## 60-Second Interview Answer

PostgreSQL lets you set `READ UNCOMMITTED`, `READ COMMITTED`, `REPEATABLE READ`, and `SERIALIZABLE`. The default is `READ COMMITTED`: every statement sees a fresh snapshot of data committed before that statement starts, so non-repeatable reads and phantoms are possible. `READ UNCOMMITTED` is accepted but behaves like `READ COMMITTED`, because PostgreSQL MVCC does not expose dirty reads. `REPEATABLE READ` uses a stable transaction snapshot and in PostgreSQL prevents dirty reads, non-repeatable reads, and phantom reads, but it is still snapshot isolation, so write skew can remain. `SERIALIZABLE` adds Serializable Snapshot Isolation checks and may abort a transaction with a serialization failure; correct code must retry the entire transaction.

## Production Relevance

Most OLTP code works well with the default `READ COMMITTED`, especially when invariants are protected by constraints, unique indexes, and explicit row locks. Analogy: for normal shop work, checking the current shelf before each operation is enough.

Use `REPEATABLE READ` for reports, exports, or calculations that need a stable view of many rows. Analogy: a stocktaking team should use one printed inventory sheet, not a sheet that changes while they count.

Use `SERIALIZABLE` when the application needs the database to reject unsafe concurrent schedules, especially for business rules that span multiple rows. Analogy: a traffic controller is useful when several roads share one dangerous crossing.

Long transactions at strict isolation levels can increase conflicts and keep old row versions alive longer. Analogy: holding an old pantry photo for hours forces everyone to keep remembering what the pantry looked like back then.

## Common Misconceptions

- "PostgreSQL `READ UNCOMMITTED` allows dirty reads." It does not; it behaves like `READ COMMITTED`. The kitchen never serves food from an unfinished ticket.
- "`REPEATABLE READ` means serializable." Not in PostgreSQL; it is stronger than the SQL minimum and prevents phantoms, but write skew can still happen. Two clerks can follow their own copies of the schedule and still create a bad final roster.
- "`SERIALIZABLE` just locks everything." PostgreSQL uses SSI, not a simple global lock. The intersection stays busy, but unsafe traffic patterns may be rejected.
- "A serialization failure is a database bug." It is expected behavior. The application should retry the whole transaction, like resubmitting a form after the queue order changed.
- "Higher isolation is always better." Higher isolation can mean more retries, more waiting, and longer retention of old row versions. A stricter kitchen process is safer, but it slows down service if every small order needs a supervisor.
