---
name: plan
description: Turn a spec.md into an implementation plan.md. Use when the user types /plan with the path to a spec.md; reads the spec, asks clarifying questions only about sequencing or approach decisions the spec leaves open, then writes plan.md next to the spec file.
argument-hint: <path to spec.md>
---

# /plan

Take the path to a `spec.md`, read it, and produce a `plan.md` describing *how and in what order* the spec will be built.

`spec.md` says **what exactly**. `plan.md` says **how and in what order**: ordered, small, verifiable tasks that together satisfy every requirement and acceptance criterion in the spec.

## Input

The path to `spec.md` is in `$ARGUMENTS`.

- If empty, look for `specs/*/spec.md`. If exactly one exists, confirm it with the user and use it; if several exist, ask which one (AskUserQuestion); if none, ask the user for the path.
- Accept a path to the file or to its folder (e.g. `specs/003-user-login` means `specs/003-user-login/spec.md`).
- If the file does not exist, say so and suggest running `/spec` first. Do not invent a spec.

## Process

1. **Read the spec.** Read `spec.md` fully, and `intent.md` beside it if present. Then inspect the codebase (existing files, structure, tests, conventions) so tasks reference real files and fit the existing stack. Note what already exists versus what must be created.

2. **Restate** the goal in one sentence and list the spec's requirement and acceptance-criteria numbers you must cover.

3. **Ask only if needed.** Use AskUserQuestion (1-4 questions, concrete options, recommended default first) for decisions that change the plan and that the spec and codebase do not settle, such as ordering preferences, rollout or migration strategy, or carried-over Open Questions that block planning. If the spec is clear enough, skip questions and say so. Do not re-litigate decisions the spec already made.

4. **Break the work into tasks.** Group tasks into phases ordered by dependency (foundation, core behavior, edge cases, polish). Each task should be:
   - small enough to finish and verify on its own;
   - specific about files or modules touched;
   - linked to the spec requirement(s) it satisfies;
   - paired with a concrete check that proves it is done (a test, command, or observable behavior).

   Mark tasks that can run in parallel, and note dependencies explicitly.

5. **Check coverage.** Every Functional Requirement and Acceptance Criterion in the spec must map to at least one task. Add a traceability table. Anything not covered goes under Open Questions, not silently dropped.

6. **Write `plan.md`** in the same folder as `spec.md` (confirm before overwriting an existing one), using the template below.

7. **Report back.** Give the file path, the phase count and task count, any risks or open questions, and invite corrections.

## plan.md template

```markdown
# Plan: <title from spec>

> Source: [spec.md](./spec.md)

## Summary
<One short paragraph: the approach and the order of attack.>

## Assumptions & Prerequisites
- <tools, access, existing code, or decisions this plan relies on>

## Phase 1: <name>
**Goal:** <what is true when this phase is done>

- [ ] **1.1 <task title>**
  - Do: <what to change; files/modules>
  - Covers: <spec requirement / acceptance criteria numbers>
  - Depends on: <task ids or none>
  - Done when: <specific check: test, command, observable result>
- [ ] **1.2 ...**

## Phase 2: <name>
...

## Traceability
| Spec item | Task(s) |
|-----------|---------|
| FR 1      | 1.1, 2.3 |
| AC 1      | 2.4 |

## Risks & Mitigations
- <risk> -> <how the plan reduces it>

## Verification
<How the whole feature is validated end to end once all phases are done.>

## Open Questions
- ...
```

## Rules

- Do not start implementing. This skill only reads, asks, and writes `plan.md`.
- Never contradict or silently expand the spec. If the plan needs something the spec lacks, flag it and ask the user, or note it under Open Questions.
- Ground tasks in the real codebase; do not reference files or tools that do not exist unless the task creates them.
- Match the project's existing stack and conventions; do not introduce new technology without asking.
- Keep tasks ordered so the project stays buildable and testable after each one where possible.
- Omit template sections that have no content rather than filling them with filler.
- Write `plan.md` beside the `spec.md` it came from, never elsewhere.
