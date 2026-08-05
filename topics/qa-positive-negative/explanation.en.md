# Positive and Negative Testing

When test types are classified **by the expected result**, they fall into two
groups: positive and negative testing. This is one of the most frequent
interview questions for a QA engineer position — and one of the most practical:
every test case you write at work will belong to one of these two types. The
interviewer checks not so much the definitions as the understanding: can you,
for the same element of the system, come up with both the "correct" scenario
and the "how do I break it" scenarios.

## Positive testing — the "pass test"

**Positive testing** is a set of checks whose goal is to obtain a positive
result: to make sure the system is functional and behaves correctly on valid,
expected input data. This approach is also known as the **"pass test"** (happy
path): we walk through the scenario exactly as the user and the requirements
intended it.

Examples of positive checks:

- the "Email" field accepts a valid address `user@example.com` and the form is
  submitted successfully;
- the "Age" field accepts the value `25` from the allowed range;
- login with a valid username/password pair lets the user into the system.

> **The 60-second interview answer.**
> Positive testing verifies that the system works as designed on valid data —
> it is also called the "pass test". Negative testing covers scenarios where an
> action cannot be performed: how the system reacts to errors and incorrect
> requests, and whether it shows an error where it should. Both types are a
> classification by expected result, and in a real test suite they come in
> pairs.

## Negative testing

**Negative testing** is the verification of scenarios where an action **cannot
be performed** by the system. Here we analyse how the system reacts to errors
and incorrect requests: does it display an error when it should — and avoid
displaying one when it should not.

Examples of negative checks:

- the "Email" field with `user@` or `just text` — the system shows a clear
  error message and does not submit the form;
- the "Age" field with `-5`, `999` or letters — the input is rejected;
- login with a wrong password — access is denied, the system does not crash;
- an API request without a mandatory parameter — a meaningful error code is
  returned, not a stack trace or a blank page.

An important nuance: the goal of a negative test is not to "break the system"
at any cost, but to verify that it **handles failure correctly**: a clear
message, no data loss, no crashes.

## Pairs of checks for a single input field

A classic interview exercise is to list the checks for one element. For an
"Age" field (allowed range 18–65) the pair looks like this:

| Positive checks | Negative checks |
|---|---|
| entering `18` — accepted | entering `17` — rejected with a message |
| entering `65` — accepted | entering `66` — rejected with a message |
| entering `30` — accepted | entering letters `thirty` — rejected |
| | empty field — rejected (if mandatory) |

Note: boundary values (17/18, 65/66) already belong to the territory of
[test design](topic:qa-test-design), but in the "positive/negative" dimension,
values inside the range are positive, values outside are negative.

## Why positive testing alone is not enough

Positive scenarios confirm that the system **can** work correctly. Negative
scenarios confirm that it **does not break** and does nothing dangerous when
something goes wrong. Most critical production defects are related precisely to
unexpected input: empty values, overly long strings, special characters,
repeated form submissions. That is why a good interview answer is: "for every
positive scenario I try to come up with several negative ones."

> **Typical follow-up questions.**
> - Give an example of a negative test for a registration form?
> - Should a test suite contain more positive or more negative checks?
> - Is negative testing the same as security testing? (No: a security attack
>   like SQL injection is a special case of malformed input, but negative
>   testing has a broader goal — any correct reaction to an error.)

> **Traps.**
> - Don't confuse negative testing with "bad" or "harmful" testing — it is a
>   normal and mandatory part of the checks.
> - "The system didn't crash" is not yet a successful negative test: success
>   means the system showed a correct, understandable error.
> - Positive/negative is a classification **by expected result**, not by level
>   (unit/integration/system) and not by execution (manual/automated). For
>   those classifications, see the topic on
>   [types of testing](topic:qa-test-types).
