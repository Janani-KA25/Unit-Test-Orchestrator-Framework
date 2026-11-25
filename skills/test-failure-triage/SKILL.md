---
trigger: manual
---

# Phase 5: Test Failure Triage (`test-failure-triage`)

**Triggered by:** Supervisor (Phase 9) when verification fails.  
**Responsibility:** Classify failures into 4 categories and decide remediation.  
**Output:** Failure classification report + actions.  
**Key principle:** Different failures require different actions.

---

## Overview: 4 Failure Categories

When a test fails, ask: **Why did this test fail?**

| Category | Cause | Action | Counts as Attempt? |
|----------|-------|--------|-------------------|
| **TEST_BUG** | Test code is wrong | Fix test; rerun (up to 3×) | YES |
| **PLAN_ERROR** | Plan/expectation wrong | Revise plan expectation; re-generate test | YES |
| **SUSPECTED_PROD_BUG** | Production code is wrong | Disable test; log ticket; don't retry | NO |
| **ENVIRONMENT** | Infrastructure/setup broken | Stop; ask user to fix | NO |

---

## Section 1: TEST_BUG (Test Code is Wrong)

**You wrote the test incorrectly. Fix it and rerun.**

### Signs of TEST_BUG:
- Test logic doesn't match plan's Given/When/Then
- Test uses wrong data (wrong column name, wrong type)
- Assertion is too strict or too loose
- Test violates testing conventions
- Guard check caught an issue (banned API, missing assertion, etc.)
- Flaky test (passes sometimes, fails sometimes) due to bad setup

### Example TEST_BUGs:

```
TEST_BUG #1: Assertion too strict
  Test: assertThat(result).isEqualTo("value")
  Plan: "Returns the value or empty string if null"
  Actual: null input returns "" (empty string)
  Fix: Change assertion to assertThat(result).isEmpty()

TEST_BUG #2: Wrong data type
  Test: Struct.newBuilder().set("count").to("42") // string
  Plan: "count is an INT64"
  Actual: IllegalArgumentException (type mismatch)
  Fix: .to(42L) instead of .to("42")

TEST_BUG #3: Missing assertion
  Test: Struct row = ...; PAssert.that(result)...
  Guard: "Assertion not checked; test may never fail"
  Actual: Test never verifies result
  Fix: Add PAssert or assertThat() call

TEST_BUG #4: Flaky test (test order dependent)
  Test 1st run: PASS
  Test 2nd run: FAIL (static state from run 1)
  Fix: Clear static state; use @BeforeEach; avoid shared mutable objects
```

### Remediation steps:

1. **Identify the bug:**
   - Read test failure message
   - Compare test code to plan's expected value
   - Check guard check output
   - Run test manually to see exact error

2. **Fix the test:**
   - Edit test method in `src/test/java/com/example/MyClassTest.java`
   - Re-verify against plan's given/when/then
   - Ensure assertion matches expected value from plan

3. **Recompile and rerun:**
   ```bash
   mvn -B -ntp test-compile -DskipTests
   mvn -B -ntp test -Dtest=MyClassTest -Dmaven.test.failure.ignore=true
   ```

4. **Increment attempt counter:**
   This is fix attempt #1, #2, or #3 (max 3).

5. **Rerun guard check:**
   ```bash
   java UtGuard check --run unit-test-agent/reports/$RUN_DIR --plan unit-test-agent/plans/FQCN.plan.md
   ```

6. **Rerun flakiness check (if was flaky):**
   ```bash
   mvn -B -ntp test -Dtest=MyClassTest -Dmaven.test.failure.ignore=true
   mvn -B -ntp test -Dtest=MyClassTest -Dmaven.test.failure.ignore=true -Drunorder=random
   java UtGuard results --run unit-test-agent/reports/$RUN_DIR --class com.example.MyClassTest --label first
   java UtGuard results --run unit-test-agent/reports/$RUN_DIR --class com.example.MyClassTest --label second
   ```

**When to escalate:** If 3 fix attempts fail, the class is too hard to test right now. Mark as `skip-too-hard` in backlog and pick a different class.

---

## Section 2: PLAN_ERROR (Plan Expectation Wrong)

**The plan expected the wrong behavior. Revise and re-generate.**

### Signs of PLAN_ERROR:
- Test fails, but Javadoc/spec actually says something different
- Existing production tests show different behavior
- Code doesn't match Javadoc (genuine disagreement)
- Expected value in plan doesn't match Javadoc

### Example PLAN_ERRORs:

```
PLAN_ERROR #1: Javadoc vs plan mismatch
  Javadoc: "Returns list of non-null values"
  Plan expected: List.of("A", "B") (skips nulls)
  Test runs; actual: List.of("A", null, "B") (keeps nulls)
  Root cause: Javadoc wasn't read carefully; code is correct
  Fix: Revise plan's expected value; re-generate test

PLAN_ERROR #2: Existing test shows different behavior
  Existing test (StructToCsvFnTest.java):
    public void testNullValue() { ... returns empty string ... }
  New plan expected: throw exception
  Test fails; actual: returns "" (empty string)
  Root cause: Plan didn't review existing tests
  Fix: Check existing tests; revise plan expected value

PLAN_ERROR #3: Spec actually undefined (code defines it)
  Javadoc: "Handles all CSV formats"
  Code: Only handles comma-separated, fails on tab-separated
  Plan expected: Handles tabs
  Test fails; actual: Can't handle tabs
  Root cause: Plan assumed from Javadoc, but code is the spec
  Fix: Revise plan to match what code actually does (or mark as bug if spec disagrees)
```

### Remediation steps:

1. **Understand the disagreement:**
   - Read Javadoc, code, and existing tests again
   - Find the **written source** for expected behavior
   - Determine what code actually does vs what spec says

2. **Decide:**
   - Is this a **suspected bug** (code doesn't match spec)? → Go to Section 3
   - Is the **spec wrong** (plan misread Javadoc)? → Revise plan

3. **If revising plan:**
   - Edit `unit-test-agent/plans/FQCN.plan.md`
   - Update the test case's "Then" column to match actual behavior
   - Update "Expected value comes from" to cite correct source
   - Set status back to `draft`
   - Request user re-approval (Supervisor Phase 4)

4. **After re-approval:**
   - Return to Phase 6 (ut-generate) to fix test code
   - Recompile and rerun
   - Retry verification (Phase 7)

**When to escalate:** If you can't determine the written spec (Javadoc, existing test, README), ask user: "What should this method do?" and update plan based on answer.

---

## Section 3: SUSPECTED_PROD_BUG (Production Code is Wrong)

**The code doesn't match its specification. Record the bug; don't retry.**

### Signs of SUSPECTED_PROD_BUG:
- Test asserts the Javadoc spec (correct)
- Code fails that assertion (incorrect)
- The failure is consistent (not flaky)
- No test-code issue

### Example SUSPECTED_PROD_BUGs:

```
SUSPECTED_PROD_BUG #1: Code returns wrong value
  Javadoc: "Returns the absolute value of x"
  Test: assertThat(Math.abs(-5)).isEqualTo(5)
  Result: FAIL (code returns -5, not 5)
  Decision: Code is broken; don't retry test

SUSPECTED_PROD_BUG #2: Code ignores documented parameter
  Javadoc: "If strict=true, throws on invalid input"
  Code: Never checks strict parameter; always lenient
  Test: Expects exception when strict=true
  Result: FAIL (exception not thrown)
  Decision: Code is broken; don't retry

SUSPECTED_PROD_BUG #3: Code crashes on documented input
  Javadoc: "Handles null input gracefully"
  Code: Calls list.get(0) without null check
  Test: Passes null
  Result: FAIL (NullPointerException)
  Decision: Code is broken; don't retry
```

### Remediation steps:

1. **Confirm it's a bug (not test-code issue):**
   - Verify test code matches plan's Given/When/Then ✓
   - Guard check passed (no test-code issues) ✓
   - Test is not flaky (consistent failure) ✓
   - Javadoc clearly states expected behavior ✓

2. **Disable the test:**
   - Add `@Disabled("Suspected bug: ")` to the test method
   - Reason must start with "Suspected bug: " (guard checks this)
   - Example:
     ```java
     @Disabled("Suspected bug: Javadoc says absolute value, code returns original sign")
     @Test
     void abs_negativeInput_returnsPositive() { ... }
     ```

3. **Log the ticket:**
   - Create issue in GitHub/JIRA with:
     - Title: "BUG: ClassName.methodName() behavior doesn't match Javadoc"
     - Description: What Javadoc says vs what code does
     - Link to test: `src/test/java/com/example/MyClassTest.java::methodName`
   - Record ticket link in plan under "Suspected bugs"

4. **Update status:**
   - In backlog, set class status to `done-with-suspected-bugs`
   - In plan, add ticket link
   - Don't increment fix attempt counter (this is not a test-code bug)

5. **Decide next step:**
   - If other tests in this class passed → continue with them
   - If multiple suspected bugs block testing → mark class as `skip-prod-bugs` and pick different class

**When to escalate:** If >50% of test cases hit suspected bugs, the class is too buggy to test productively. Pick a different class and return to this one after bugs are fixed.

---

## Section 4: ENVIRONMENT (Infrastructure Broken)

**Build system, dependencies, or environment is broken. Stop and ask user to fix.**

### Signs of ENVIRONMENT failure:
- Maven can't find a dependency
- Spanner emulator is down
- File system permission denied
- Java version is wrong
- Network is down (for integration tests)

### Example ENVIRONMENT failures:

```
ENVIRONMENT #1: Maven can't compile
  Error: [ERROR] Failed to execute goal ... cannot find symbol [class Struct]
  Root cause: Spanner client library not in pom.xml
  Action: Stop; ask user to add com.google.cloud:cloud-spanner to pom.xml

ENVIRONMENT #2: Spanner emulator not running
  Error: com.google.cloud.spanner.SpannerException: ... Connection refused
  Root cause: Emulator process stopped or not configured
  Action: Stop; ask user to start emulator before tests

ENVIRONMENT #3: Java wrong version
  Error: Error: This project requires Java 21, but you have 25
  Root cause: JAVA_HOME pointing to wrong JDK
  Action: Stop; ask user to run with correct JDK (use jdk-21.cmd wrapper)

ENVIRONMENT #4: Permission denied
  Error: java.nio.file.AccessDeniedException: target/test-reports/
  Root cause: target/ directory read-only or owned by different user
  Action: Stop; ask user to `rm -rf target/` and retry
```

### Remediation steps:

1. **Identify the environment issue:**
   - Read error message carefully
   - Is it a dependency? Class not found? Emulator?
   - Is it Java version? Permissions? Network?

2. **Stop immediately:**
   - Don't retry
   - Don't mark as attempt
   - Log to audit trail: `java UtGuard log --event ENVIRONMENT_BLOCKER --run ... --detail "..."`

3. **Report to user:**
   ```
   ENVIRONMENT BLOCKER
   
   The build system cannot proceed because:
   [Error message and cause]
   
   Action required (not AI):
   [Instructions for user to fix]
   
   Once fixed, re-run: /unit-test-agent resume
   ```

4. **Wait for user:**
   - User fixes the environment
   - User runs `/unit-test-agent resume`
   - Supervisor resumes from state.json (Phase 11)

**When to escalate:** If the same environment issue persists after user fix attempt, escalate to ops team.

---

## Section 5: Triage Decision Tree

**When a test fails, follow this flowchart:**

```
Test failed
    ↓
Is it a flaky test?
(Passes sometimes, fails sometimes)
    ├─ YES → LIKELY TEST_BUG (bad setup, test order dependent)
    └─ NO ↓
Is guard check passing?
(no banned imports, assertions present, etc.)
    ├─ NO → TEST_BUG (guard found the issue)
    └─ YES ↓
Does test code match plan's Given/When/Then?
    ├─ NO → TEST_BUG (test logic wrong)
    └─ YES ↓
Does code fail its own Javadoc?
    ├─ YES → SUSPECTED_PROD_BUG (disable, log ticket)
    └─ NO ↓
Can you identify written source for expected value?
    ├─ NO (no Javadoc, no existing test, no README)
        → PLAN_ERROR (plan assumption was wrong)
    └─ YES ↓
Is the written source (Javadoc) correct?
    ├─ NO → PLAN_ERROR (plan misread spec)
    └─ YES → SUSPECTED_PROD_BUG (code doesn't match spec)
↓
Can you run Maven and Java?
    ├─ NO → ENVIRONMENT (fix and retry)
    └─ YES → Proceed to remediation
```

---

## Section 6: Report and Resume

### Step 6a: Create triage report

Write `$RUN_DIR/06-triage-<Class>.md`:

```markdown
# Triage Report for StructToCsvFn

Generated: 2026-11-26, 10:45 UTC
Run: unit-test-agent/reports/20261126-100000/

## Test Results
- Tests run: 6
- Passed: 5
- Failed: 1
- Flaky: 0

## Failed Test
Method: processElement_nullColumn_writesEmptyField
Error: AssertionError: expected "" but got "NULL"

## Triage Classification
**Type: SUSPECTED_PROD_BUG**

Reason:
- Javadoc says: "Null columns are written as empty string"
- Plan expected: ""
- Test code is correct (matches plan Given/When/Then)
- Guard check passed
- Error is consistent (not flaky)
- Code returns "NULL" instead of ""

This indicates a bug in production code (StructToCsvFn.extractValue()).

## Action Taken
1. Test method marked with @Disabled("Suspected bug: ...")
2. GitHub issue created: example/example-common#1234
3. Test count: 5 passed, 1 disabled

## Status
✗ FAILED with 1 suspected bug
Recommend: Fix production code and re-test, or skip this class
```

### Step 6b: Decide next step

**If TEST_BUG:**
```
Fixing test code (attempt 1 of 3).
Recompiling and re-running...
```
Return to Phase 7 (ut-verify).

**If PLAN_ERROR:**
```
Plan expected value was incorrect.
Revising plan and re-generating tests.
```
Return to Phase 2 (ut-plan) to edit plan; request user re-approval.

**If SUSPECTED_PROD_BUG:**
```
Test disabled; bug ticket created.
Moving to Phase 8 (Coverage measurement).
```
Return to Phase 8 (even though one test is disabled).

**If ENVIRONMENT:**
```
Build system blocker detected.
Stopping now.

[Error details and fix instructions]

Please fix and run: /unit-test-agent resume
```
Pause; await user fix.

---

## Troubleshooting

| Situation | Action |
|-----------|--------|
| "I don't know if it's a bug or bad test" | Follow decision tree above |
| "Multiple tests failing" | Triage each separately |
| "Same failure every time (not flaky)" | Likely TEST_BUG or SUSPECTED_PROD_BUG |
| "Failure is random (flaky)" | Likely TEST_BUG (bad setup or test order dependent) |
| "Can't compile at all" | ENVIRONMENT (dependency or config issue) |
| "Javadoc says X, code does Y" | SUSPECTED_PROD_BUG (disable test, log ticket) |

---

**You are done with Phase 5 (Triage).**

Depending on classification, return to appropriate phase (7 to retry, 8 to continue, or stop for user to fix).
