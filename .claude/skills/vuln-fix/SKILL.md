---
name: vuln-fix
description: Find Maven dependencies with CRITICAL vulnerabilities, bump them to fixed versions, and rebuild and test the project. Runs standalone. Use when the user types /vuln-fix, optionally with a severity override (e.g. high).
argument-hint: [severity: critical (default) | high]
---

# /vuln-fix

Standalone: find critical-severity vulnerable dependencies in `pom.xml`, update them to fixed versions, and prove the project still builds and passes tests. Does not need a plan or spec.

## Steps

1. **Branch.** `git status` must be clean (if not, stop and ask). `git fetch origin`; `git switch main && git pull --ff-only`; `git switch -c fix/vuln-deps-<YYYYMMDD>` (switch to it if it exists). Never work on `main`.
2. **Find critical vulnerabilities.** Severity is `critical` unless `$ARGUMENTS` says otherwise. Try in order and say which source was used:
   - **GitHub Dependabot alerts:** `gh api "repos/{owner}/{repo}/dependabot/alerts?state=open&severity=critical" --paginate`. Take the package, vulnerable range, `first_patched_version` and advisory id/CVE.
   - **OWASP Dependency-Check** if Dependabot is unavailable (no access or not enabled): `./mvnw org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=9 -Dformat=JSON` (PowerShell: `.\mvnw.cmd`). Without an `NVD_API_KEY` env var the first run is slow; do not hard-code a key. Read `target/dependency-check-report.json` for CVSS >= 9 findings.
   - If neither works, stop and tell the user what is missing. Do not guess.
     If there are no critical findings, report that and stop. No changes, no commit.
3. **Plan each fix.** For every finding decide where the version comes from by reading `pom.xml`:
   - **Direct dependency with a literal or property version:** bump the property or version to the first patched version (or the newest patch in the same minor line when that is enough).
   - **Managed by the Spring Boot parent BOM:** prefer overriding the property named in the BOM (e.g. `<jackson-bom.version>`) over adding a hard-coded version. If a newer Spring Boot release fixes it within the same minor line, say so and prefer that.
   - **Transitive only:** add a `dependencyManagement` pin to the patched version, with an XML comment naming the CVE so it can be removed later.
     Avoid major-version jumps; if the only fix is a major bump, list it for the user instead of applying it. No new dependencies.
4. **Apply** the edits to `pom.xml` (one commit-worthy change per finding, no unrelated edits). Do not touch source code unless the build breaks from an API change; then make the smallest fix and note it.
5. **Verify.** Run the full test suite, not a subset: `./mvnw clean verify` (PowerShell: `.\mvnw.cmd clean verify`) compiles, runs all unit and integration tests, and packages. Never use `-Dtest`, `-DskipTests`, `-Dmaven.test.skip` or `-fae` to narrow or hide failures. Tests use Testcontainers, so Docker must be running; if it is not, stop and tell the user rather than skipping those tests. Record total tests run, failures, errors and skipped from the Surefire/Failsafe summary. Run this once on the baseline before editing too (step 4), so existing failures are known. Then re-run the step 2 scan and confirm the fixed findings are gone. Compare against the baseline: only count a failure as caused by the update if it passes on a clean `main`. Max 2 attempts per finding: if an upgrade breaks the build or tests and cannot be fixed trivially, revert that bump and list it as unresolved.
6. **Commit, push, PR.** Only if the build and tests are green and at least one finding was fixed.
   - Stage `pom.xml` (and any minimal source fix) by name; never `git add -A`, never `target/`.
   - Commit message `deps: fix critical vulnerabilities (<CVE ids>)` with the attribution line from the session's system reminder.
   - `git push -u origin <branch>`, no force-push. `gh pr create --base main` unless one exists. Body: a table of dependency, old version, new version, CVE/advisory, and source of the finding; the build command and test pass/fail counts; unresolved items; the PR attribution line from the system reminder.
   - Do not merge.

## Rules

- Do not downgrade or remove dependencies to dodge a finding.
- Do not suppress findings (no `suppressions.xml` entries) unless the user asks.
- Never claim a fix is verified without the build command and test counts, and a re-scan result.
- Never push `main`, never force-push, never merge.

## Final report to the user

- Scan source and severity searched
- Findings: fixed, unresolved (with reason), and any needing a major bump
- Version changes (old to new)
- Exact build command, test pass/fail counts, and re-scan result
- Branch, commit hash and PR URL (or why none was made)
