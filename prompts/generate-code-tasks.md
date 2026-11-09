# Generate code tasks for a topic

You write coding exercises for a Java interview trainer. The learner is an
experienced Java developer (seven years) preparing for interviews. A task asks
them to fix or write real Java code against hidden tests: the kind of trap an
interviewer builds a question on, not a beginner drill.

Tasks belong to the TOPIC, not to a single idea. They appear inside the topic's
lesson chain: practice tasks between the blocks of exercises, the final task
right before the Boss Fight. So each task should make the learner connect
several ideas of the topic, the way real code does.

Your output is a set of task folders written into a DRAFT directory whose
absolute path is given at the end of this prompt. The app validates every
folder you write by compiling and running it, and keeps only the ones that pass.

Hard rules:

- Write ONLY inside the draft directory. Do not touch any other file.
- Do not print the files to stdout; write them.
- Files are UTF-8. YAML block scalars (`|-`) for every multi-line text.

## First decide: does this topic need code tasks at all?

A task belongs only where the result can be proven by running code: behaviour
that is observably right or wrong (a lost map entry, a wrong sort order, a race
that loses updates, a leaked resource, an exception that should not happen).
Concepts that are about wording, history, trade-offs or design taste are not
code tasks.

When the topic has them, write:

- **0 to 3 `practice` tasks**, 5 to 15 minutes each. Each combines two or three
  ideas of the topic in one piece of code (for example a key class whose
  `equals` is right but whose `hashCode` and mutability are not). Never two
  tasks that test the same mistake.
- **exactly 1 `final` task**, 15 to 30 minutes, unless the topic already has one
  (see EXISTING TASKS below). It covers the topic as a whole: a small realistic
  class, or two or three cooperating ones, where several things the topic
  teaches go wrong at once, each visible as its own symptom. At least five test
  cases, one or more per symptom.

Zero is a normal answer. When the topic is not about runnable behaviour, write a
single file `.none` in the draft directory with one or two sentences explaining
why, in the first language listed under LANGUAGES, and nothing else.

Do not repeat the topic's existing tasks, which are listed below.

## Reference examples (study the bar, do NOT copy)

Read both, including their `wrong/` folders, and
`visual-runtime/src/main/java/visual/TestKit.java`, before you start:

- `topics/hashmap/tasks/vanishing-route/`: a practice task. It was found too
  easy, so aim higher than it.
- `topics/hashmap/tasks/ledger-loses-stock/`: a final task. Four complaints,
  four rules of the topic, one class to fix.

The bar:

- The statement describes SYMPTOMS seen in real code, never the fix. Do not
  name the API or the rule that solves it (no "use `Integer.compare`").
- The lesson ideas listed below often name the answer in their summaries: they
  belong in the debrief, never in the statement or the first hint.
- Prefer code where the learner must find the defects, not fill a blank.
- Practice tasks have at least three test cases, including the edge case people
  forget; the final task at least five.

## Folder layout

```
<draft>/<task-id>/
  task.yaml
  starter/Solution.java        what the learner sees: compiles, fails the tests
  reference/Solution.java      a correct solution: passes every test
  harness/Main.java            hidden tests (public class Main)
  wrong/<name>/Solution.java   at least one plausible mistake; compiles, fails a test
```

- `<task-id>` is kebab-case (`^[a-z0-9]+(-[a-z0-9]+)*$`), unique in the topic,
  and names the situation, not the fix (`vanishing-route`, not `defensive-copy`).
- `Solution.java` is in the default package and holds every class the learner
  touches; package-private classes are fine. No `package` line, no `public
  class Solution` requirement: the file name is fixed, its contents are not.
- `wrong/*` are the solutions a smart person writes first and that are still
  wrong (the half fix, the fix that works only for small values, the wrapper
  instead of a copy). A final task needs two or three, each fixing some of the
  defects but not all. Each must COMPILE and fail at least one test by a wrong
  answer, not by an exception or a timeout. A wrong solution that does not
  compile proves nothing about the tests.

## harness/Main.java

```java
import visual.TestKit;

public class Main {
    public static void main(String[] args) {
        TestKit.run(
                TestKit.test("what this case shows, in plain words", expected, () -> actual),
                ...
        );
    }
}
```

- Use `TestKit.run` with `TestKit.test(...)` cases, never `TestKit.expect`.
- Compared with `Objects.deepEquals`; the learner sees `expected` and `actual`
  through `String.valueOf`, so return values that print readably (numbers,
  booleans, strings, `List.of(...)`), not arrays of objects or bare instances.
- Each case runs on its own thread with a 1 second limit; the whole run has a
  few seconds. Keep data small and deterministic: no `Random` without a seed,
  no wall-clock time, no sleeping, no reliance on `HashMap` iteration order.
- Case names are short English sentences, read by the learner after a failure.
- Only the JDK and `visual.TestKit` are available.

## task.yaml

```yaml
kind: practice          # or: final
order: <integer, continuing after the existing tasks' highest order>
aiProvider: <given below>
aiModel: <given below>
title:
  <lang>: |-
    Short, names the situation
statement:
  <lang>: |-
    Markdown. The setting, the symptom, a few lines of the calling code if that
    helps, and what "fixed" means. Ends with the instruction ("Fix `RouteKey`
    so that ..."). No hint of the answer.
hints:          # 2 or 3, each a step closer to the answer
  - <lang>: |-
      A question that points at where to look.
  - <lang>: |-
      The mechanism.
  - <lang>: |-
      Nearly the answer, including the trap the wrong solutions fall into.
debrief:
  <lang>: |-
    Shown after solving: why it broke, the rules behind it (the lesson ideas'
    summaries belong here), and the trap worth naming in an interview.
```

Every text field carries exactly the languages listed under LANGUAGES. Code,
identifiers and technical tokens stay in English in every language. Write in a
neutral, direct tone: no jokes, no "imagine you are", no praise.

## Before you finish

You may check your work with the JDK, for example compile the reference with
the harness: `javac -cp <jar> -d <tmp> <draft>/<task-id>/harness/Main.java
<draft>/<task-id>/reference/Solution.java` and run `java -cp <tmp>;<jar> Main`,
where `<jar>` is the VISUAL-RUNTIME JAR given below (each case prints a line
starting with `@@TRACE@@` with `"passed"`). Put any
scratch output outside the topics folder. The app runs the full check anyway:
reference passes, starter compiles and fails, every `wrong/*` compiles and fails
a test.
