---
name: build-feature
description: Build one task or phase from a plan.md by running the implementer, tester and reviewer agents in order, with fix loops. Use when the user types /build-feature with a plan path and a task or phase (e.g. specs/001-catalog/plan.md 1.2).
argument-hint: <path to plan.md> <task or phase, e.g. 1.2 or phase 2>
---

# /build-feature

Run the implement, test, review pipeline for one task or phase of a `plan.md`. The agents do not call each other, so this skill is the orchestrator.

## Input

`$ARGUMENTS` = path to `plan.md`, then a task id (`1.2`) or a phase (`phase 2`). If the task is missing, read the plan and ask which one to build, suggesting the first not-yet-done task whose dependencies are met.

## Steps

1. **Read** the plan and its sibling `spec.md`. Find the task(s), their dependencies, "done when" checks and the FR/AC numbers they trace to. Refuse to start if a dependency task is not done; say which one.
2. **Implement.** Spawn the `implementer` agent with: the plan and spec paths, the exact task ids, the done-when checks, and a reminder to follow the CLAUDE.md "Coding conventions". Wait for its report.
3. **Test.** Spawn the `tester` agent with the implementer's file list, the task ids, and the relevant FR/AC numbers. Wait for its report. It must run after the implementer finishes, never at the same time (they share the source tree and Maven's `target/`).
4. **Fix loop** (max 2 rounds). If the tester reports implementation bugs, send them back to a new `implementer` run with the failing test output, then re-run `tester`. If tests themselves were wrong, the tester fixes them. If still failing after 2 rounds, stop and report to the user.
5. **Review.** Spawn the `reviewer` agent with the changed files, the plan and spec paths, and the task ids. If it returns Blockers, run one more implement, test, review round for those only. "Should fix" and "Nit" items are listed for the user, not auto-fixed.
6. **Update the plan.** When tester passes and reviewer has no Blockers, mark the task done in `plan.md` (add a `Status: done` line or tick the checkbox, matching the plan's existing format). Do not mark it done otherwise.

## Rules

- Run the agents one at a time, in order. Do not run implementer and tester in parallel.
- Do not write code or tests yourself; delegate to the agents.
- Never claim a task is done unless the tester's run was green and its command and counts are in the report.

## Final report to the user

- Task(s) built and final status (done / blocked)
- Files changed
- Exact test command and pass/fail counts
- Reviewer verdict and any remaining Should-fix and Nit items
- Anything left for the user to decide
