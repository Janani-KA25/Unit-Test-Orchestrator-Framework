---
trigger: manual
---

# Phase 4: Test Verification (`ut-verify`)

**Triggered by:** Supervisor (Phase 7) after generation.  
**Responsibility:** Run guard checks, compile, run tests, detect flakiness, report results.  
**Output:** `05-verification-<Class>.md` and `guard/check` output.  
**Safety:** Guard validates independently; human interprets results.

---

## Section 1: Pre-Verification Setup

### Step 1a: Confirm generated test file

Verify the test file from Phase 3 (ut-generate) exists:

```bash
ls -la src/test/java/com/example/YourClassTest.java
```

Must exist and be readable.

### Step 1b: Run guard snapshot

Take a snapshot of the generated code:

```bash
java UtGuard snapshot --run unit-test-agent/reports/$RUN_DIR
```

This computes SHA-256 hashes of all files. Output: `guard/snapshot-after-generate.txt`

---

## Section 2: Guard Check

### Step 2a: Run guard check command

Validate the test file against the approved plan:

```bash
java UtGuard check --run unit-test-agent/reports/$RUN_DIR --plan unit-test-agent/plans/FQCN.plan.md
```

**What guard checks:**
1. Plan hash matches approval record (hasn't changed)
2. All case IDs from plan are present in test file (TC-001, EC-001, etc.)
3. No banned imports (PowerMock, Testcontainers, Spring injection)
4. No credential patterns in test code (password=, api_key:, secret:)
5. All `@Test` methods have assertions
6. All `@Disabled` methods have `Suspected bug:` reason
7. Test file name matches plan's `test_file:`

**Expected output:**
```
CHECK PASSED
Plan hash matches approval: YES
Case IDs in test: 6/6 (TC-001, TC-002, TC-003, EC-001, EC-002, ES-001)
Banned imports: none
Credentials: none detected
Assertions: 6/6 methods have assertions
Disabled methods: 0 (all with reasons)
Compile: src/test/java/com/example/MyClassTest.java ready
```

**If guard check FAILS:**
- Goto Phase 5 (Triage, TEST_BUG case)
- Fix the issue (missing case ID, banned import, missing assertion, etc.)
- Rerun guard check
- Max 3 total fix attempts (this counts as attempt #1)

### Step 2b: Interpret guard output

| Guard Output | Meaning | Action |
|--------------|---------|--------|
| `Plan hash matches: NO` | Plan was edited after approval | Re-approve plan (doesn't count as fix attempt) |
| `Case IDs missing: ...` | Test methods weren't generated | Generate missing methods, increment attempt count |
| `Banned imports: Spring, PowerMock` | Test uses wrong stack | Remove imports, increment attempt count |
| `Credentials detected: password=...` | Synthetic data leaked | Remove, increment attempt count |
| `Missing assertion: method M` | Test doesn't assert | Add assertion, increment attempt count |
| `Disabled without reason: method M` | @Disabled doesn't explain | Add reason or remove @Disabled |

---

## Section 3: Compile Test Code

### Step 3a: Maven compile-only

Verify test code compiles without running:

```bash
mvn -B -ntp clean test-compile -DskipTests
```

**Expected:** `BUILD SUCCESS`

**If ERROR:** Fix compilation errors (increment attempt count). Note the error location and class.

### Step 3b: Check for common issues

Look for:

- **Package mismatch:** Test package doesn't match production class package
- **Missing imports:** Reference to undefined class (Struct, Pipeline, etc.)
- **Wrong annotation:** `@Before` instead of `@BeforeEach`, etc.

---

## Section 4: Run Tests (First Pass)

### Step 4a: Single-class test run

Run **only** the generated test file:

```bash
mvn -B -ntp test -Dtest=StructToCsvFnTest -Dmaven.test.failure.ignore=true
```

**Expected output locations:**
- Tests run: `target/surefire-reports/`
- Coverage: `target/site/jacoco/` (if JaCoCo configured)

**What we're looking for:**
- How many tests PASS vs FAIL vs SKIP?
- Any flaky failures (non-deterministic)?

### Step 4b: Check Surefire output

```bash
ls target/surefire-reports/TEST-com.example.MyClassTest.xml
cat target/surefire-reports/TEST-com.example.MyClassTest.xml | head -50
```

Expected XML structure:
```xml
<testsuite tests="6" failures="0" errors="0" skipped="0">
  <testcase classname="..." name="processElement_stringAndInt_writesQuotedCsv" time="0.123">
    <!-- PASS: no child element -->
  </testcase>
  <testcase classname="..." name="...">
    <failure type="AssertionError">...</failure>
  </testcase>
  ...
</testsuite>
```

---

## Section 5: Detect Flakiness

### Step 5a: Shuffle and re-run

**Flaky tests fail non-deterministically.** Run the same tests again with order shuffled:

```bash
mvn -B -ntp test -Dtest=StructToCsvFnTest -Dmaven.test.failure.ignore=true
```

(Surefire shuffles order by default; if not, add `-Drunorder=random`.)

Compare result #2 against result #1 (Section 4):

- **Same result:** Test is not flaky (reliable)
- **Different result:** Test is flaky (might PASS once, FAIL next time)

### Step 5b: Run guard results command

```bash
java UtGuard results --run unit-test-agent/reports/$RUN_DIR --class com.example.StructToCsvFnTest --label first
```

After first run, then:

```bash
java UtGuard results --run unit-test-agent/reports/$RUN_DIR --class com.example.StructToCsvFnTest --label second
```

Guard compares XML from first vs second run:

**Output:**
```
RESULTS COMPARISON
Run 1 (first): 6 passed, 0 failed, 0 skipped
Run 2 (second): 6 passed, 0 failed, 0 skipped
Flakiness: none detected

Overall: RELIABLE
```

**If flaky detected:**
```
RESULTS COMPARISON
Run 1 (first): 5 passed, 1 failed (testMethod_condition)
Run 2 (second): 6 passed, 0 failed
Flakiness: DETECTED

Flaky test: testMethod_condition
  Suspect: external call, timing issue, random seed, test order dependent
```

**Action if flaky:** Goto Phase 5 (Triage, TEST_BUG case). Fix and retry (counts as attempt).

---

## Section 6: Style and Convention Check

### Step 6a: Code style (automated)

Verify test file follows conventions from `rules/ut-test-conventions.md`:

```
Checklist:
- [ ] 2-space indentation
- [ ] No tabs
- [ ] Lines <100 chars (mostly)
- [ ] Package: same as production
- [ ] Imports: organized, no star imports
- [ ] Naming: method_condition_result
- [ ] No commented-out code
```

### Step 6b: Semantic check

Verify test cases match plan:

**For each case ID in plan:**
- [ ] Method exists with that ID in comment
- [ ] Given/When/Then logic matches plan
- [ ] Assertion matches expected value from plan
- [ ] No surprises or extra assertions

---

## Section 7: Verification Summary

### Step 7a: Create verification report

Write `$RUN_DIR/05-verification-<Class>.md`:

```markdown
# Verification Report for StructToCsvFn

Generated: 2026-11-26, 10:30 UTC
Run: unit-test-agent/reports/20261126-100000/

## Guard check
- Plan hash: ✓ matches approval
- Case IDs: ✓ 6/6 present
- Banned imports: ✓ none
- Credentials: ✓ none detected
- Assertions: ✓ 6/6 methods
- Disabled methods: ✓ all with reasons
Result: **PASSED**

## Compilation
```
mvn test-compile -DskipTests
BUILD SUCCESS
```
Result: **OK**

## First run
```
mvn test -Dtest=StructToCsvFnTest
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
```
Result: **ALL PASS**

## Flakiness check
```
mvn test -Dtest=StructToCsvFnTest (second run)
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
```
Comparison: Run 1 == Run 2
Result: **NO FLAKINESS DETECTED**

## Style check
- Indentation: 2-space ✓
- Naming: method_condition_result ✓
- No commented code ✓
Result: **OK**

## Semantic check
- All 6 case IDs match plan ✓
- Given/When/Then logic matches ✓
- Assertions match expected values ✓
Result: **OK**

## Conclusion
✓ **ALL CHECKS PASSED**
Proceeding to coverage measurement.
```

### Step 7b: Decide next phase

**If all checks passed:**
```
Verification complete: PASSED
Moving to Phase 8 (Coverage measurement).
```

**If some checks failed:**
```
Verification found issues:
- Flaky test: extractArray_emptyInput
- Guard check: missing assertion in method M

This counts as fix attempt #1 of 3.

Moving to Phase 5 (Failure triage).
```

---

## Troubleshooting

| Issue | Root Cause | Action |
|-------|-----------|--------|
| Guard check fails: plan hash mismatch | Plan edited after approval | Re-approve; doesn't count as attempt |
| Compilation error | Syntax error in test code | Fix; increment attempt counter |
| Test fails | Logic error in test or production code | Triage; increment attempt counter |
| Flaky test | External call, timing, test order dependent | Isolate; increment attempt counter |
| Guard timeout | Test runs >15 min | Optimize test; increment attempt counter |
| Surefire XML missing | Maven didn't run tests | Rerun Maven with correct flags |

---

**You are done with Phase 4 (Verification).**

Report results. If all passed, proceed to Phase 8 (Coverage). If failed, go to Phase 5 (Triage).
