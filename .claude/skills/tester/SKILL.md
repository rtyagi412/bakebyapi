---
name: tester
description: Write and run tests for recent changes by running the tester agent on its own, outside the full build pipeline. Use when the user types /tester, optionally with a task id, file list or plan path.
argument-hint: [task id, files, or plan.md path; defaults to uncommitted/branch changes]
---

# /tester

Run the `tester` agent standalone against recent changes and report the results.

## Steps

1. **Scope.** From `$ARGUMENTS` take a task id, file list or plan path. If none, use the changes on the current branch: `git status --short` and `git diff main...HEAD --stat`. If there is nothing changed, stop and say so.
2. **Context.** If a plan or spec applies (named in arguments, or found under `specs/<NNN>-<slug>/` from the branch name), note the task ids and FR/AC numbers they cover.
3. **Run the agent.** Spawn the `tester` agent with the changed file list, task ids, FR/AC numbers, and a reminder of the CLAUDE.md "Tests" rules. Wait for its report.
4. **Report** to the user without softening:
   - Tests added (files and what each covers)
   - Exact command and passed/failed counts, plus failing output
   - Implementation bugs found, with reproduction
   - Gaps that could not be tested

## Rules

- Do not write code or tests yourself; delegate to the agent.
- Do not fix implementation bugs here; report them. The user can run `/build-feature` or the `implementer` to fix them.
- Do not commit or push.
- Never claim green without the agent's command and counts.
