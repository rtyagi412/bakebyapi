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
   1b. **Branch.** Every new phase gets its own branch, created from an up-to-date `main`. Name it `feat/<NNN-slug>-phase-<n>` (e.g. `feat/001-catalog-phase-2`); for a single task use `feat/<NNN-slug>-task-<id>`. Steps: `git status` must be clean (if not, stop and ask); `git fetch origin`; `git switch main && git pull --ff-only`; `git switch -c <branch>`. If the branch already exists, switch to it instead of recreating. Never build on `main` directly.
2. **Implement.** Spawn the `implementer` agent with: the plan and spec paths, the exact task ids, the done-when checks, and a reminder to follow the CLAUDE.md "Coding conventions". Wait for its report.
3. **Test.** Spawn the `tester` agent with the implementer's file list, the task ids, and the relevant FR/AC numbers. Wait for its report. It must run after the implementer finishes, never at the same time (they share the source tree and Maven's `target/`).
4. **Fix loop** (max 2 rounds). If the tester reports implementation bugs, send them back to a new `implementer` run with the failing test output, then re-run `tester`. If tests themselves were wrong, the tester fixes them. If still failing after 2 rounds, stop and report to the user.
5. **Review.** Spawn the `reviewer` agent with the changed files, the plan and spec paths, and the task ids. If it returns Blockers, run one more implement, test, review round for those only. "Should fix" and "Nit" items are listed for the user, not auto-fixed.
6. **Update the plan.** When tester passes and reviewer has no Blockers, mark the task done in `plan.md` (add a `Status: done` line or tick the checkbox, matching the plan's existing format). Do not mark it done otherwise.
7. **Commit, push, PR.** Only when step 6 marked the task done (tester green, no reviewer Blockers); otherwise skip this step and report why.
   - Confirm `git branch --show-current` is the `feat/...` branch from step 1b, never `main`.
   - Stage specific files only (never `git add -A`; never `.env`, secrets, `target/`). Commit message in repo style: `<feature>: <task ids> <summary>` (e.g. `catalog: 2.1 category CRUD`), ending with the attribution line from the session's system reminder.
   - `git push -u origin <branch>`. Never push `main`, never force-push.
   - If `gh pr list --head <branch>` shows none, run `gh pr create --base main --head <branch>`; if one exists, the push already updated it. Title matches the commit. Body: tasks and FR/AC covered, test command and counts, reviewer verdict, remaining Should-fix / Nit items, ending with the PR attribution line from the system reminder.
   - Do not merge.

## Rules

- Run the agents one at a time, in order. Do not run implementer and tester in parallel.
- Do not write code or tests yourself; delegate to the agents.
- Commit, push and open the PR only in step 7, on the feature branch. Never push `main`, never force-push, never merge.
- Never claim a task is done unless the tester's run was green and its command and counts are in the report.

## Final report to the user

- Branch name
- Task(s) built and final status (done / blocked)
- Files changed
- Exact test command and pass/fail counts
- Reviewer verdict and any remaining Should-fix and Nit items
- Anything left for the user to decide
- Commit hash and PR URL (or why step 7 was skipped)
- Next step: the user reviews and merges the PR
