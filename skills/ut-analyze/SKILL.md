---
trigger: manual
---

# Phase 1: Repository Analysis and Baseline (`ut-analyze`)

**Triggered by:** Supervisor (Phase 1) after setup.  
**Responsibility:** Understand the codebase and measure baseline test coverage.  
**Outputs:** `01-repo-analysis.md`, `02-baseline-coverage.md`, `backlog.md`.

---

## Section 1: Static Analysis

You will analyze the production source code without running tests yet. This builds a map of what's testable.

### Step 1a: List production classes

Read all files under `src/main/java/`. For each class:

1. **Class name** (fully qualified).
2. **Type:** Categorize as one of:
   - `interface` or `abstract class` (not directly testable) → exclude from backlog
   - `enum` (mostly getters, constants) → might be testable, decide per-class
   - Concrete class with executable code → include

3. **Methods:** List all public and package-private methods (names only).
4. **Dependencies:** Note significant collaborators (Spanner, Pipeline, Beam, external APIs).

### Step 1b: Identify existing tests

Read `src/test/java/`. For each test class:

1. Find which production class(es) it covers.
2. List test methods (names only).
3. Note if it's using real objects, mocks, or parameterized tests.

### Step 1c: Check for testability blockers

For each production class, note:

- If it has a `public static void main()`: **production code may call `System.exit()`, which is not testable in a test suite.**
- If key methods build real external clients (Spanner, Dataflow, GCS): **hard to unit-test without production infrastructure.**
- If methods are private and have no public entry point: **test indirectly through public methods only.**

### Step 1d: Identify conventions

From existing tests, note:

- Naming pattern: `<Class>Test` or `<Class>Tests`?
- Assertion library: JUnit 5 (Jupiter), AssertJ, Mockito, Hamcrest?
- Test method naming: `testXxx`, `xxx_condition_result`, or other?
- Real vs. mock dependencies: how are boundaries handled?

### Step 1e: Create `01-repo-analysis.md`

Write a summary document:

```markdown
# Repository Analysis

## Build system
- Build tool: Maven 3.9.x
- Java release: 21 (from pom.xml, verify with `java -version`)
- JDK installed locally: (output of `java -version`)
- Key plugins: Surefire (tests), JaCoCo (coverage), Checkstyle, Spotless

## Production classes (count, types)
| Class | Type | Public methods | Test file | Existing tests |
|-------|------|-----------------|-----------|-----------------|
| com.example.MyClass | concrete | method1, method2 | MyClassTest.java | 3 methods |
| com.example.Utils | concrete | staticHelper | none | (no existing tests) |
| ...

## Testability issues
- Class A: `main()` calls `System.exit()` → not unit-testable
- Class B: Opens real Spanner client in `run()` → needs mock/stub
- Class C: Private helpers only reachable via public method X → test indirectly

## Existing test conventions
- Naming: `<Class>Test`
- Stack: JUnit 5, AssertJ, Mockito (from spring-boot-starter-test)
- Dependencies: Real Beam Pipeline on direct runner; mocked Spanner client

## Per-class coverage notes
(To be filled in by baseline run below)
```

---

## Section 2: Baseline Coverage

You will now run the existing test suite and measure coverage **before the agent adds any tests**. This is the "before" picture.

### Step 2a: Verify Maven

Run:

```bash
mvn -v
```

If this fails (Maven not found, corrupted install), stop and tell the supervisor: "Maven is not available. User must install it."

### Step 2b: Run baseline tests

```bash
mvn -B -ntp "-Djacoco.append=false" "-Dsurefire.timeout=900" "-Dmaven.test.failure.ignore=true" test jacoco:report
```

**Flags explained:**
- `-B`: Batch mode (no color, no interactive prompts).
- `-ntp`: No transfer progress (faster output).
- `-Djacoco.append=false`: Start fresh JaCoCo data (don't accumulate from other runs).
- `-Dsurefire.timeout=900`: Timeout hung tests after 900 seconds.
- `-Dmaven.test.failure.ignore=true`: Don't stop if tests fail; still generate coverage report.
- `test`: Run all tests.
- `jacoco:report`: Generate JaCoCo report.

**Expected output:**
```
[INFO] Running com.example.MyClassTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- jacoco:0.8.x:report ---
[INFO] BUILD SUCCESS
```

### Step 2c: Check for environment blockers

**If Maven output contains:**

- `Could not resolve dependencies` → **ENVIRONMENT blocker.** User must fix VPN/artifactory access. Tell the supervisor: mark baseline as "not available", continue with static analysis.
- `COMPILATION ERROR` → **ENVIRONMENT blocker** (JDK version mismatch, missing dependency). Tell supervisor, continue static.
- `org.jacoco.report.internal.ReportGenerator.start` ... `WARN report has no executable lines` → **ENVIRONMENT blocker** (Surefire not configured for JaCoCo). Tell supervisor, continue static.
- Heap space error, out of memory → **ENVIRONMENT blocker.** User may need to increase heap: `export MAVEN_OPTS="-Xmx1g"`.

**If you see ENVIRONMENT blockers:**
- Do NOT continue with numbers. Mark them as "not available".
- Tell supervisor to record blocker verbatim.
- Continue static analysis (you can still read source and list classes).

### Step 2d: Parse coverage numbers

If `target/site/jacoco/jacoco.xml` exists and is recent:

```bash
java .devin/skills/ut-guard/UtGuard.java coverage
```

This command:
- Reads `target/site/jacoco/jacoco.xml` (DOM parse, no assumptions).
- Extracts `<line>` and `<branch>` counters for each class.
- Reports: class name, line %, branch %, missed lines.

**Output sample:**
```
| Class | Line % | Branch % | Missed lines |
|-------|--------|----------|--------------|
| com.example.MyClass | 45.0% | 30.0% | 32 |
| com.example.Utils | 100.0% | 100.0% | 0 |
| (repo total) | 55.3% | 35.2% | 120 |
```

**If the report is STALE (newer Surefire XML than JaCoCo report):**
- Guard exits with code 3.
- Do NOT use the numbers.
- Tell supervisor: baseline not available.

**If JaCoCo data is 0% everywhere:**
- Check if Surefire is configured to use JaCoCo agent.
- This is usually a parent POM config issue.
- Mark as ENVIRONMENT blocker.

### Step 2e: Identify pre-existing failures

From the Surefire output, note any test that failed **before the agent runs**:

```
[ERROR] com.example.MyClassTest.testXxx FAILURE
```

**Record these in backlog.md** under "Tests that were already failing". The agent will never touch them.

### Step 2f: Write `02-baseline-coverage.md`

```markdown
# Baseline Coverage Report

**When:** 2026-11-19, 10:00 UTC  
**Run:** unit-test-agent/reports/20261119-100000/

## Status
- Maven version: 3.9.12
- Java version: 21.0.1
- Tests run: 13
- Tests passed: 11
- Tests failed: 2 (pre-existing, listed below)
- BUILD: SUCCESS

## Baseline coverage (repo-wide)
| Metric | Value |
|--------|-------|
| Line coverage | 52.3% (190/363) |
| Branch coverage | 38.1% (57/150) |

## Per-class coverage
| Class | Line % | Branch % | Missed lines |
|-------|--------|----------|--------------|
| com.example.MyClass | 45.0% | 30.0% | 32 |
| com.example.Utils | 100.0% | 100.0% | 0 |
| ...

## Pre-existing test failures
- com.example.IntegrationTest.testRealSpannerConnection: "Cannot reach Spanner emulator"
  (This test was already failing; agent will not modify it.)

## Notes
- JaCoCo configuration from parent POM profile `coverage`.
- Spanner tests require emulator; not running in this environment.
```

---

## Section 3: Create / Update Backlog

The backlog is the **master list of which classes need tests**, their current status, and what's left to do.

### Step 3a: Build the class table

For each **production class** with executable code:

1. Get the name, current line %, branch %, missed lines (from baseline).
2. Determine status:
   - If you see `src/test/java/.../ClassTest.java` → `has_existing_tests`
   - If the class has 0% coverage and no existing tests → `todo` (highest priority)
   - If you see a plan file → `draft-plan` or `planned`
   - If a report exists → `done` or `done-below-target` or `blocked`

3. Create backlog.md:

```markdown
# Test Backlog

**Last full scan:** 2026-11-19, run unit-test-agent/reports/20261119-100000  
**Baseline:** line 52.3% / branch 38.1% (13 tests run, all pass except pre-existing 2)  
**Targets:** line 80% / branch 75% (global defaults, may override per class)

## Status legend
| Status | Meaning |
|--------|---------|
| `todo` | Not started |
| `draft-plan` | Plan written, awaiting approval |
| `planned` | Plan approved, tests not written |
| `done` | Tests written, guard passed, coverage meets target |
| `done-below-target` | Tests written, guard passed, coverage below target but documented |
| `blocked` | Tests written but failing after max attempts, or guard check failed |
| `skipped` | User chose not to test this class now |

## Classes
| Class | Status | Line % | Branch % | Missed | Plan | Report |
|-------|--------|--------|----------|--------|------|--------|
| com.example.MyClass | todo | 45.0 | 30.0 | 32 | | |
| com.example.Utils | todo | 100.0 | 100.0 | 0 | | |
| ...

## Suspected bugs
(None detected in analysis. Will be populated as tests are written.)

## Tests that were already failing
| Test | Failure reason |
|------|-----------------|
| com.example.IntegrationTest.testRealSpannerConnection | Cannot reach Spanner emulator |
```

### Step 3b: Order by priority

Sort classes by most missed lines first (highest need for coverage). Within same coverage, sort by business criticality (from your domain knowledge or existing test count).

### Step 3c: Preserve existing state

If `backlog.md` already exists from a previous run:

- Keep the existing status values (don't overwrite `done`/`blocked`/etc.).
- Update coverage numbers from new baseline.
- Keep existing plan/report links.
- Add new classes if the codebase changed.

---

## Section 4: Finish

### Step 4a: Save artifacts

Write `$RUN_DIR/01-repo-analysis.md` and `$RUN_DIR/02-baseline-coverage.md` and `unit-test-agent/backlog.md`.

Verify they are readable and complete.

### Step 4b: Return to Supervisor

Log to supervisor:

```
Analysis complete.
Baseline: line 52.3% / branch 38.1% (13 tests, 2 pre-existing failures)
Classes found: 5 (4 todo, 0 draft-plan, 0 done)
Backlog ready.
```

The supervisor will now show the backlog and ask the user to pick a class.

---

## Troubleshooting

| Issue | Action |
|-------|--------|
| Maven not found | Stop, tell user to install Maven. |
| Dependencies won't resolve | Mark as ENVIRONMENT blocker; continue static analysis. |
| JaCoCo report empty / 0% | Mark as ENVIRONMENT blocker; parent POM may need configuration. |
| Existing tests fail in baseline | List under "Tests that were already failing"; don't touch them. |
| New class added to repo since last run | Add to backlog with 0% coverage and status `todo`. |
| Backlog file corrupted | Regenerate from scratch. |

---

**You are done with Phase 1.** Return to the supervisor.
