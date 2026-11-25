---
name: intent
description: Turn a high-level requirement into a clear intent.md. Use when the user types /intent with a rough idea or requirement; asks follow-up questions to clarify goals, scope, users, constraints and success criteria, then writes intent.md to specs/<NNN>-<slug>/intent.md.
argument-hint: <high-level requirement>
---

# /intent

Take a high-level requirement from the user, clarify it through follow-up questions, and produce an `intent.md` file.

## Input

The requirement is in `$ARGUMENTS`. If it is empty, ask the user: "What do you want to build or change? A sentence or two is enough."

## Process

1. **Read the requirement.** Restate it in one sentence to confirm understanding. Briefly check the project (README, existing files, `intent.md` if present) so questions are informed and not redundant.

2. **Ask follow-up questions.** Use the AskUserQuestion tool (1-4 questions per round, with concrete options and a recommended default where sensible). Only ask what you cannot infer. Cover, as relevant:
   - **Goal / problem:** what problem does this solve, and why now?
   - **Users / stakeholders:** who uses it or is affected?
   - **Scope:** what is in, and what is explicitly out?
   - **Key behaviors / features:** the must-haves versus nice-to-haves.
   - **Constraints:** tech stack, deadlines, integrations, compliance, performance, budget.
   - **Success criteria:** how will we know it is done and working?
   - **Risks / unknowns:** assumptions, dependencies, open questions.

   Do one or two rounds, adapting later questions to earlier answers. Stop when you have enough to write a useful intent; do not interrogate.

3. **Pick the spec folder.** Each requirement gets its own folder under `specs/` at the project root: `specs/<NNN>-<slug>/`.
   - `<NNN>` is the next zero-padded 3-digit number (list existing `specs/` folders, take the highest number + 1; start at `001`).
   - `<slug>` is a short kebab-case name for the requirement (e.g. `003-user-login`).
   - Create `specs/` and the folder if they don't exist. If the user is clearly refining an existing requirement, reuse its folder instead of creating a new one.

4. **Write `intent.md`** at `specs/<NNN>-<slug>/intent.md` (overwrite only after confirming if one already exists) using the template below. Keep it concise and specific. Mark unresolved items under Open Questions rather than guessing.

5. **Report back.** Give the file path and a 2-3 line summary, and invite corrections.

## intent.md template

```markdown
# Intent: <short title>

## Summary
<One or two sentences: what we are building/changing and why.>

## Problem / Goal
<The problem being solved and the desired outcome.>

## Users & Stakeholders
- <who, and what they need>

## Scope
**In scope**
- ...

**Out of scope**
- ...

## Key Requirements
- [Must] ...
- [Should] ...
- [Could] ...

## Constraints
- <tech, time, integrations, compliance, performance>

## Success Criteria
- <measurable or observable outcomes>

## Assumptions & Risks
- ...

## Open Questions
- ...
```

## Rules

- Do not start implementing. This skill only clarifies intent and writes `specs/<NNN>-<slug>/intent.md`.
- Preserve the user's own wording for goals where possible.
- Omit template sections that have no content rather than filling them with filler.
