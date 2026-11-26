---
name: pr-review
description: Review a GitHub pull request with the reviewer agent and report findings, optionally posting them as a PR review comment. Use when the user types /pr-review with a PR number or URL (defaults to the current branch's PR).
argument-hint: [PR number or URL] [--post]
---

# /pr-review

Review a pull request against the spec and CLAUDE.md conventions. Read-only unless the user passes `--post`.

## Steps

1. **Find the PR.** Use the number/URL in `$ARGUMENTS`; otherwise `gh pr view --json number,title,headRefName,baseRefName,url` for the current branch. If none exists, stop and say so.
2. **Gather.** `gh pr view <n> --json title,body,files,headRefName,statusCheckRollup` and `gh pr diff <n>`. Note CI status. Find the matching `specs/<NNN>-<slug>/` plan and spec from the branch name or PR body, and the task ids and FR/AC numbers.
3. **Get the code.** The reviewer reads whole files, so the PR head must be checked out. If the working tree is clean, `gh pr checkout <n>`; if not, stop and ask rather than switching branches over uncommitted work.
4. **Review.** Spawn the `reviewer` agent with the PR number, changed files, plan and spec paths, and task ids. Optionally also run the `security-review` skill for auth, input or config changes.
5. **Report** findings by severity (Blocker, Should fix, Nit) with `file_path:line_number` and a suggested fix, then the verdict (approve or changes requested), CI status, and whether the PR meets its spec's FR/AC items.
6. **Post (only with `--post`).** Post one summary with `gh pr review <n> --comment --body-file <file>` (use `--request-changes` only if the user asked). Without `--post`, offer it in one line and do nothing.

## Rules

- Never edit code, commit or push, and never merge or approve on the user's behalf.
- Be skeptical: confirm claims by reading the code or running existing tests, not from the PR description.
- Do not post anything to GitHub unless `--post` was given.
