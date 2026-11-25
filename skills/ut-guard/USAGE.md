---
trigger: manual
reference: true
---

# UtGuard Utility - Complete Implementation

**Version:** 1.0  
**Status:** ✅ Production Ready  
**Language:** Java 11+  
**Dependencies:** None (JDK only)

---

## Overview

**UtGuard** is a deterministic validation utility that runs independently of AI, ensuring test integrity through:

- File fingerprinting (SHA-256)
- Plan approval recording  
- Test file validation (case IDs, banned APIs, assertions)
- Flaky test detection (compare 2 runs)
- Coverage measurement (JaCoCo parsing)
- Immutable audit trail

---

## Building

### Compile

```bash
# From repository root
javac -d target/classes skills/ut-guard/UtGuard.java
```

### Run

```bash
# From repository root
java -cp target/classes UtGuard <command> [options]
```

---

## Commands

### 1. snapshot `--run DIR`

**Purpose:** Fingerprint all source files (pom.xml, src/**) using SHA-256.

**Usage:**
```bash
java UtGuard snapshot --run unit-test-agent/reports/20261126-100000
```

**What it does:**
- Traverses `pom.xml`, `src/main/java`, `src/main/resources`, `src/test/java`
- Computes SHA-256 hash for each file
- Writes to `$RUN_DIR/guard-snapshot.txt`
- Appends to `unit-test-agent/audit.log`

**Output:**
```
SNAPSHOT: OK (127 files)
```

**Exit codes:**
- `0` – Success
- `2` – Error (directory creation failed, etc.)

---

### 2. approve `--run DIR --plan FILE --reply TEXT`

**Purpose:** Validate and record plan approval before test generation.

**Usage:**
```bash
java UtGuard approve --run unit-test-agent/reports/20261126-100000 \
  --plan unit-test-agent/plans/StructToCsvFn.plan.md \
  --reply "APPROVED"
```

**What it does:**
- Reads plan file (Markdown)
- Validates:
  - Status is `draft` (not already approved)
  - Total test cases ≤ 40 (from config)
  - No credential patterns (password=, api_key:, secret:)
- Computes plan SHA-256 hash
- Writes approval record: `$RUN_DIR/approval-ClassName.txt`
  - Contains: timestamp, plan file path, SHA-256, case count, user reply
- Appends to `unit-test-agent/audit.log`

**Output:**
```
APPROVE: RECORDED
  Plan SHA-256: a1b2c3d4e5f6...
  Test cases: 10
```

**Exit codes:**
- `0` – Plan approved, recorded
- `1` – Plan rejected (violations found)
- `2` – Error (file not found, etc.)

**Validation rules:**
- Plan must exist
- Plan status must be `draft`
- Test case count ≤ 40 per plan
- No credential patterns in plan text

---

### 3. check `--run DIR --plan FILE`

**Purpose:** Validate generated test file against approved plan.

**Usage:**
```bash
java UtGuard check --run unit-test-agent/reports/20261126-100000 \
  --plan unit-test-agent/plans/StructToCsvFn.plan.md
```

**What it does:**
- Reads approval record and compares plan SHA-256 (ensures plan unchanged)
- Extracts test file location from plan
- Checks all case IDs (TC-001, EC-001, ES-001) present in test file (via grep)
- Validates test code:
  - No banned imports (PowerMock, Testcontainers, Spring injection)
  - No credential patterns in test code
  - Every `@Test` method has an assertion (`assertThat`, `PAssert`, `verify`)
  - Every `@Disabled` method has reason starting with `Suspected bug:`
- Verifies test compilation (checks for compile errors)

**Output (success):**
```
Plan hash: ✓ matches approval
Case IDs: ✓ all 10 present
Banned imports: ✓ none
Assertions: ✓ all tests assert
@Disabled: ✓ all with 'Suspected bug:' reason

GUARD CHECK: PASS
All validations passed.
```

**Output (failure):**
```
GUARD CHECK: FAIL (2 failures)
  ✗ Case IDs: missing [TC-005, EC-003]
  ✗ Assertion: test method testNull_emptyField missing assertion
```

**Exit codes:**
- `0` – All checks passed
- `1` – Validation failed (see output for details)
- `2` – Error (file not found, etc.)

**Validation rules:**
- Plan hash must match approval record (unchanged)
- All case IDs from plan must be present in test file
- No banned imports
- No credentials
- Every `@Test` asserts
- Every `@Disabled` has `Suspected bug:` reason

---

### 4. results `--run DIR --class TESTFQCN --label first|second`

**Purpose:** Record test outcomes and detect flaky tests.

**Usage (first run):**
```bash
mvn test -Dtest=StructToCsvFnTest
java UtGuard results --run unit-test-agent/reports/20261126-100000 \
  --class com.example.extract.StructToCsvFnTest \
  --label first
```

**Usage (second run to detect flakiness):**
```bash
mvn test -Dtest=StructToCsvFnTest -Drunorder=random
java UtGuard results --run unit-test-agent/reports/20261126-100000 \
  --class com.example.extract.StructToCsvFnTest \
  --label second
```

**What it does:**
- Parses `target/surefire-reports/TEST-com.example.extract.StructToCsvFnTest.xml`
- Extracts test outcomes (PASS/FAIL for each method)
- Writes to `$RUN_DIR/results-$CLASS-$label.txt`
- On "second" label: compares with first run to detect flakiness
  - Flaky if: same test passes in run 1 but fails in run 2 (or vice versa)
  - Also detects if failure list changed

**Output (first run):**
```
RESULTS: RECORDED (first run)
  Tests: 6 passed: 6 failed: 0
```

**Output (second run, stable):**
```
FLAKY_CHECK: STABLE
  Run 1 == Run 2: no flakiness detected
```

**Output (second run, flaky):**
```
FLAKY_CHECK: DETECTED
  Now passing: [testNull_emptyField]
  Now failing: [testArray_duplicates]
```

**Exit codes:**
- `0` – Success (first run) or stable (second run)
- `1` – Flakiness detected (differences between runs)
- `2` – Error (file not found, etc.)

---

### 5. coverage `[--class NAME]`

**Purpose:** Measure code coverage from JaCoCo report.

**Usage (repo-wide):**
```bash
mvn clean test -Pjacoco && mvn jacoco:report
java UtGuard coverage
```

**Usage (per-class):**
```bash
java UtGuard coverage --class com.example.extract.StructToCsvFn
```

**What it does:**
- Parses `target/site/jacoco/jacoco.xml` (DOM/regex parsing)
- Checks if report is stale (older than surefire-reports)
- Extracts LINE and BRANCH coverage counters
- If `--class` given: reports per-class only
- Otherwise: reports repo-wide totals

**Output (repo-wide):**
```
COVERAGE REPORT (repo-wide)
  LINE: 190/363 (52.3%)
  BRANCH: 57/150 (38.1%)
RESULT: REPORTED
```

**Output (per-class):**
```
COVERAGE REPORT for com.example.extract.StructToCsvFn
  LINE: 85/100 (85.0%)
  BRANCH: 23/30 (76.7%)
RESULT: REPORTED
```

**Output (stale):**
```
COVERAGE: STALE (JaCoCo older than test results)
Run: mvn jacoco:report
```

**Exit codes:**
- `0` – Coverage measured and reported
- `1` – Below target (future: will check against config)
- `2` – Error (file not found, etc.)
- `3` – Stale (JaCoCo report older than tests)

---

### 6. log `--event E [--run DIR] --detail TEXT`

**Purpose:** Append audit event to immutable log.

**Usage:**
```bash
java UtGuard log --event GUARD_SNAPSHOT --run unit-test-agent/reports/20261126-100000 \
  --detail "127 files hashed"
```

**What it does:**
- Creates `unit-test-agent/audit.log` if missing
- Appends tab-separated line:
  ```
  TIMESTAMP TAB EVENT TAB RESULT TAB RUN_DIR TAB DETAIL
  ```
  Example:
  ```
  2026-11-26T10:15:00.123Z	GUARD_SNAPSHOT	ok	unit-test-agent/reports/20261126-100000	127 files hashed
  ```
- File is **append-only** (guard-only writes, permission deny prevents AI edits)

**Output:**
```
Event logged: GUARD_SNAPSHOT
```

**Exit codes:**
- `0` – Event logged
- `2` – Error (directory creation failed, etc.)

---

## Global Options

### `--config FILE`

(Future) Read configuration from JSON file. Default: `unit-test-agent/config.json`

```bash
java UtGuard check --run ... --plan ... --config custom-config.json
```

---

## Exit Codes

| Code | Meaning | Action |
|------|---------|--------|
| **0** | Success | Continue to next phase |
| **1** | Validation failed | Triage (fix and retry, up to 3 attempts) |
| **2** | Error/usage | Stop (ask user to fix environment) |
| **3** | Stale data | Stop (re-run build/report command) |

---

## Audit Log Format

Located at: `unit-test-agent/audit.log`

**Append-only, tab-separated format:**

```
2026-11-26T10:00:00.001Z	GUARD_SNAPSHOT	ok	unit-test-agent/reports/20261126-100000	127 files hashed
2026-11-26T10:05:00.234Z	PLAN_APPROVED	ok	unit-test-agent/reports/20261126-100000	Plan StructToCsvFn approved with 10 cases
2026-11-26T10:15:00.567Z	GUARD_CHECK	ok	unit-test-agent/reports/20261126-100000	No violations
2026-11-26T10:20:00.890Z	TESTS_RUN	ok	unit-test-agent/reports/20261126-100000	6 tests, 6 passed
2026-11-26T10:25:00.123Z	TESTS_STABLE	ok	unit-test-agent/reports/20261126-100000	No flakiness detected
2026-11-26T10:30:00.456Z	COVERAGE_MEASURED	ok	unit-test-agent/reports/20261126-100000	Line: 85.0%, Branch: 77.0%
```

**Columns:**
1. Timestamp (ISO 8601)
2. Event type (GUARD_*, PLAN_*, TESTS_*, COVERAGE_*, etc.)
3. Result (ok, fail, warn)
4. Run directory
5. Detail (free text)

---

## Integration with Supervisor

**Supervisor calls UtGuard at key phases:**

| Phase | Command | Purpose |
|-------|---------|---------|
| **0** | `snapshot --run $RUN_DIR` | Fingerprint baseline |
| **5** | `approve --run $RUN_DIR --plan X --reply "APPROVED"` | Record approval |
| **7** | `check --run $RUN_DIR --plan X` | Validate generated tests |
| **7** | `results --run $RUN_DIR --class X --label first` | First run |
| **7** | `results --run $RUN_DIR --class X --label second` | Flaky check |
| **8** | `coverage --class X` | Measure coverage |
| **Any** | `log --event E --run $RUN_DIR --detail TEXT` | Audit trail |

---

## Troubleshooting

| Issue | Root Cause | Solution |
|-------|-----------|----------|
| "Command not found" | UtGuard.java not compiled | Run `javac -d target/classes skills/ut-guard/UtGuard.java` |
| "File not found: pom.xml" | Running from wrong directory | Change to repository root |
| "Plan hash mismatch" | Plan edited after approval | Re-approve plan |
| "Case IDs missing: [TC-005]" | Test method not generated | Generate missing test method |
| "Stale (JaCoCo older...)" | Build not run after test | Run `mvn jacoco:report` |
| "No Surefire XML" | Tests didn't run or compile failed | Run `mvn test` first |
| "Class not found in coverage" | Class name mismatch or no coverage | Check class FQCN; ensure test ran |

---

## Example: Full Test Cycle

```bash
# Phase 0: Setup
java UtGuard snapshot --run unit-test-agent/reports/20261126-100000

# Phase 5: Plan Approval
java UtGuard approve --run unit-test-agent/reports/20261126-100000 \
  --plan unit-test-agent/plans/StructToCsvFn.plan.md \
  --reply "APPROVED"

# Phase 7: Verification
# (AI generates test code)

# Validate test file
java UtGuard check --run unit-test-agent/reports/20261126-100000 \
  --plan unit-test-agent/plans/StructToCsvFn.plan.md

# Build and test
mvn clean test -Dtest=com.example.extract.StructToCsvFnTest

# Record first run
java UtGuard results --run unit-test-agent/reports/20261126-100000 \
  --class com.example.extract.StructToCsvFnTest \
  --label first

# Re-run with shuffle to detect flakiness
mvn test -Dtest=com.example.extract.StructToCsvFnTest -Drunorder=random

# Record second run and compare
java UtGuard results --run unit-test-agent/reports/20261126-100000 \
  --class com.example.extract.StructToCsvFnTest \
  --label second

# Phase 8: Coverage
# (Build with JaCoCo)
mvn jacoco:report

# Measure coverage
java UtGuard coverage --class com.example.extract.StructToCsvFn

# Review audit trail
cat unit-test-agent/audit.log
```

---

## Security & Integrity

**UtGuard Guarantees:**

1. **Guard-only writes:** Only UtGuard writes to audit.log (AI forbidden via permission deny)
2. **Plan hash protection:** SHA-256 hash detects plan changes after approval
3. **Case ID validation:** Every test method must match plan
4. **Assertion enforcement:** Every test must assert (no silent passes)
5. **Ban list:** Forbidden APIs detected via regex
6. **Flaky detection:** 2-run comparison catches non-deterministic tests

**UtGuard Cannot Do (by design):**

- Create or edit source files (supervisor does that)
- Change audit.log (append-only via CLI)
- Approve plans (supervisor asks user)
- Fix compilation errors (supervisor asks AI)

---

## Implementation Notes

**Language:** Java (JDK 11+, no external dependencies)  
**Techniques:**
- SHA-256 hashing for file fingerprinting
- Regex matching for code validation (banned APIs, assertions, case IDs)
- Surefire XML parsing (regex-based for speed, no DOM library)
- JaCoCo XML parsing (regex-based)

**Assumptions:**
- Maven build produces target/surefire-reports/TEST-*.xml
- JaCoCo report at target/site/jacoco/jacoco.xml
- Plans use Markdown table format for case IDs
- Test methods use `// TC-001` comments for ID mapping

---

**UtGuard is production-ready. All 6 commands fully implemented.** ✅
