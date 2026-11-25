---
name: spec
description: Turn an intent.md into a detailed spec.md. Use when the user types /spec with the path to an intent.md; reads the intent, asks clarifying questions about anything the intent leaves ambiguous (behavior, interfaces, data, edge cases, tech decisions), then writes spec.md next to the intent file.
argument-hint: <path to intent.md>
---

# /spec

Take the path to an `intent.md`, read it, clarify any gaps through follow-up questions, and produce a `spec.md` describing *how* the intent will be satisfied.

`intent.md` says **what and why**. `spec.md` says **what exactly**: concrete behavior, interfaces, data, and acceptance criteria precise enough to implement and test against.

## Input

The path to `intent.md` is in `$ARGUMENTS`.

- If empty, look for `specs/*/intent.md`. If exactly one exists, confirm it with the user and use it; if several exist, ask which one (AskUserQuestion); if none, ask the user for the path.
- Accept a path to the file or to its folder (e.g. `specs/003-user-login` means `specs/003-user-login/intent.md`).
- If the file does not exist, say so and suggest running `/intent` first. Do not invent an intent.

## Process

1. **Read the intent.** Read `intent.md` fully. Also skim the project (README, existing code, other `specs/*/spec.md`) so the spec fits existing stack, naming and conventions and your questions are not redundant.

2. **Restate** the intent in one sentence to confirm understanding.

3. **Ask clarifying questions before writing.** Use AskUserQuestion (1-4 questions per round, concrete options, recommended default first). Only ask what the intent and codebase do not already answer, and carry over any items in the intent's *Open Questions*. Cover, as relevant:
   - **Behavior:** exact flows, inputs/outputs, states, and error handling.
   - **Interfaces:** endpoints, CLI commands, UI screens, events, file formats.
   - **Data:** entities, fields, validation, storage, migrations.
   - **Edge cases & failure modes:** empty/invalid input, limits, concurrency, partial failure.
   - **Technical decisions:** libraries, architecture, patterns, where code lives.
   - **Non-functional:** performance, security, accessibility, observability.
   - **Testing:** how each success criterion will be verified.

   Do one or two rounds, adapting to earlier answers. If the intent is already specific enough, skip questions and say so. Do not interrogate.

4. **Write `spec.md`** in the same folder as `intent.md` (overwrite only after confirming if one already exists), using the template below. Be specific and testable. Trace each requirement back to the intent. Record anything still unresolved under Open Questions rather than guessing.

5. **Report back.** Give the file path, a 2-3 line summary, any open questions, and invite corrections.

## spec.md template

```markdown
# Spec: <title from intent>

> Source: [intent.md](./intent.md)

## Overview
<What is being built and how it satisfies the intent, in a short paragraph.>

## Functional Requirements
<Numbered, testable statements. Reference the intent priority where useful.>
1. [Must] The system shall ...
2. [Should] ...

## Behavior
<Flows and rules: step-by-step main flow, states/transitions, alternate flows, error handling.>

## Interfaces
<Endpoints / commands / screens / events with inputs, outputs, and error cases. Use tables or code blocks for schemas.>

## Data Model
<Entities, fields, types, constraints, relationships, storage.>

## Technical Design
<Architecture, components, key decisions and why, files/modules affected, dependencies.>

## Non-Functional Requirements
- Performance: ...
- Security: ...
- Accessibility / Compatibility: ...
- Observability: ...

## Edge Cases & Error Handling
- <case> -> <expected behavior>

## Acceptance Criteria
<One or more per success criterion in the intent; written so each can be checked pass/fail.>
- [ ] Given ... when ... then ...

## Testing Plan
- <unit / integration / manual checks and what each covers>

## Out of Scope
- <carried over from intent, plus anything excluded while specifying>

## Open Questions
- ...
```

## Rules

- Do not start implementing. This skill only clarifies and writes `spec.md`.
- Never contradict or silently expand the intent. If the spec needs to go beyond it, flag it and ask the user, or note it under Open Questions.
- Match the project's existing stack and conventions; do not introduce new technology without asking.
- Omit template sections that have no content rather than filling them with filler.
- Write `spec.md` beside the `intent.md` it came from, never elsewhere.
