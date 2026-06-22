# Employee API: Commands

> **Practice mode.** This is a *challenge* topic: implement the methods in
> `Solution.java`, press **Run tests**, and a hidden harness grades your code case
> by case. For the design behind it see
> [Employee API: Design](topic:employee-api-design); for the concepts read
> [Employee API: REST and Separation of Concerns](topic:employee-api-rest-cqrs).

## The task

Implement the in-memory logic the controllers would delegate to — four commands,
no Spring, no HTTP:

- `addEmployee(name, surname, passportNumber, passportDate, salary)` → returns a new
  unique `id` (starting at `1` and increasing).
- `changeSalary(id, newSalary)` → updates **only** the salary.
- `changePassport(id, passportNumber, passportDate)` → updates **only** the passport
  fields.
- `getCard(id)` → returns the response card as a string in exactly this format:

  ```
  name + " " + surname + " | " + passportNumber + " | " + passportDate + " | " + salary
  ```

  e.g. `"Ann Lee | AA111 | 2020-01-15 | 1000"`.

An operation on an id that was never added must **throw** (e.g.
`NoSuchElementException`).

## The point: independence of commands

The hidden tests aren't really checking that you can store a record — they check
that each command touches **only its own concern**:

- after `changeSalary`, the passport fields are unchanged;
- after `changePassport`, the salary is unchanged;
- two employees get distinct ids, and editing one never affects the other.

That independence is the code-level echo of the "different departments" note from
the task: each command is a small, single-purpose write.

## A clean approach

Keep one small holder per employee in a `Map` keyed by id, plus a counter for the
next id. Each command looks up its holder and changes only its own field:

```java
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public class Solution {
    private static final class Record {
        String name, surname, passportNumber, passportDate;
        long salary;
    }

    private final Map<Long, Record> byId = new HashMap<>();
    private long nextId = 0;

    public long addEmployee(String name, String surname,
                            String passportNumber, String passportDate, long salary) {
        Record r = new Record();
        r.name = name; r.surname = surname;
        r.passportNumber = passportNumber; r.passportDate = passportDate;
        r.salary = salary;
        long id = ++nextId;
        byId.put(id, r);
        return id;
    }

    private Record require(long id) {
        Record r = byId.get(id);
        if (r == null) throw new NoSuchElementException("no employee " + id);
        return r;
    }

    public void changeSalary(long id, long newSalary) {
        require(id).salary = newSalary;            // only the salary
    }

    public void changePassport(long id, String number, String date) {
        Record r = require(id);
        r.passportNumber = number;                 // only the passport
        r.passportDate = date;
    }

    public String getCard(long id) {
        Record r = require(id);
        return r.name + " " + r.surname + " | " + r.passportNumber
                + " | " + r.passportDate + " | " + r.salary;
    }
}
```

## 60-second interview answer

> I store each employee in a map keyed by a generated id, and expose each change as
> its own command. `changeSalary` writes only the salary field; `changePassport`
> writes only the passport fields — so the two concerns never interfere, which
> mirrors the two departments owning them. `getCard` reads the current state and
> maps it to the response shape, separate from how I store it. Unknown ids throw,
> so callers get a clear failure instead of a silent no-op.

## Common traps

- ❌ **A single `update(...)` that overwrites everything.** It re-couples salary and
  passport and breaks the independence tests.
- ❌ **Re-creating the record on each change.** If `changeSalary` rebuilds the holder
  it can wipe the passport fields. Mutate only the one field you own.
- ❌ **Returning the stored object instead of the formatted card.** The card format
  is part of the contract — build the string exactly as specified.
- ❌ **Silently ignoring unknown ids.** The tests expect a thrown exception, not a
  `null` or a no-op.
- ❌ **Getting the id sequence wrong.** Ids start at `1` and increase by one per
  `addEmployee`.
