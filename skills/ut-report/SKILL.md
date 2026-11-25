---
trigger: manual
---

# Phase 6: Coverage Reporting (`ut-report`)

**Triggered by:** Supervisor (Phase 8) after verification.  
**Responsibility:** Measure coverage, generate reports, update backlog.  
**Output:** `07-coverage-<Class>.md`, updated backlog, test recommendations.

---

## Section 1: Coverage Measurement with Guard

### Step 1a: Run guard coverage command

```bash
java UtGuard coverage --class com.example.StructToCsvFn
```

Guard reads JaCoCo report and outputs:

```
COVERAGE REPORT for com.example.StructToCsvFn

Line coverage: 85/100 (85%)
Branch coverage: 23/30 (77%)

Target line: 80% ✓ PASSED
Target branch: 75% ✓ PASSED

Methods covered:
  - processElement: 100% (3/3 branches)
  - extractValue: 83% (10/12 branches)
  - constructor: 100% (1/1 branches)

Uncovered lines:
  - 42-45 (exception handler for CSVWriter)
  - 78 (unreachable in practice)
```

**Expected:** Actual coverage >= target coverage (80% line, 75% branch from config.json).

**If below target:** Decide whether to:
1. Add more test cases (plan extension)
2. Accept as `done-below-target` (unreachable lines or test-hostile design)

### Step 1b: Understand guard's exit code

| Exit Code | Meaning | Action |
|-----------|---------|--------|
| **0** | Coverage passed targets | Proceed to reporting |
| **1** | Coverage below target | Decide: extend tests or accept |
| **3** | Stale report (old) | Rerun tests: `mvn clean test -Pjacoco` |

---

## Section 2: Analyze Coverage Gaps

### Step 2a: Identify unreachable code

From guard output or JaCoCo HTML report (`target/site/jacoco/index.html`):

**Unreachable lines = OK to leave untested:**
- Exception handlers that never fire (e.g., "should never happen")
- Defensive code (null checks, range checks for impossible cases)
- Dead code (unreachable due to `if (false)` or similar)
- Generated code

**Example:**
```java
// Line 45: unreachable in practice
} catch (Exception e) {
  logger.error("CSVWriter error", e);  // ← unreachable: Struct.newBuilder() can't throw
  throw new RuntimeException(e);
}
```

**Record this in coverage report.**

### Step 2b: Identify testable but uncovered code

Lines that **could** be tested but weren't:

**Example:**
```java
// Line 60: not tested yet
public String getMetadata() {
  return metadata;  // ← Simple getter, easy to test
}
```

**Record these as "gaps"; propose new test cases.**

### Step 2c: Calculate "true coverage" (excluding unreachable)

```
Reported coverage: 85% line, 77% branch
Unreachable lines: 2 (lines 42-45, exception)
True coverage: (85 - 2) / (100 - 2) = 83/98 = 85% (same, unreachable lines already excluded by JaCoCo)
```

JaCoCo already excludes lines it can prove unreachable. Use guard's "Uncovered lines" to verify.

---

## Section 3: Coverage Report Template

Create `$RUN_DIR/07-coverage-<Class>.md`:

```markdown
# Coverage Report for StructToCsvFn

Generated: 2026-11-26, 11:00 UTC
Run: unit-test-agent/reports/20261126-100000/

## Coverage Summary
| Metric | Value | Target | Status |
|--------|-------|--------|--------|
| Line | 85% | 80% | ✓ PASS |
| Branch | 77% | 75% | ✓ PASS |

## Methods Coverage Detail
| Method | Type | Line Cov | Branch Cov | Status |
|--------|------|----------|-----------|--------|
| processElement | public | 100% | 100% | ✓ |
| extractValue | private | 83% | 72% | ⚠ |
| constructor | public | 100% | 100% | ✓ |

## Uncovered Lines (Analysis)

### Unreachable (OK to skip)
- **Line 42–45:** Exception handler for CSVWriter error
  - Reason: Struct.newBuilder() doesn't throw in practice
  - Verdict: Unreachable; not testing

### Testable but Uncovered (Gap)
- **Line 78:** `metadata = extractMetadata(input)`
  - Reason: No test case for extractMetadata() method
  - Verdict: Could add test; not critical for this class

## Coverage Assessment
✓ **GOAL MET**
- Line coverage (85%) exceeds target (80%)
- Branch coverage (77%) exceeds target (75%)
- Unreachable code properly excluded
- Key paths covered: processElement (100%), constructor (100%)

## Test Case Summary
- Tests run: 6
- Tests passed: 5
- Tests disabled: 1 (suspected bug)
- Coverage added: ~40 lines vs baseline (from 02-baseline-coverage.md)

## Backlog Update
Class StructToCsvFn moved from `draft-plan` to `done`.

## Recommendations
1. ✓ **Continue to Phase 10 (wrap-up)**
2. Future round: Consider testing extractMetadata() separately or as part of full integration test
3. No action needed on suspected bug (ticket filed separately)

## Coverage Trend
| Run | Date | Line | Branch | Status |
|-----|------|------|--------|--------|
| 1 (baseline) | 2026-11-25 | 50% | 40% | Before tests |
| 2 (StructToCsvFn) | 2026-11-26 | 85% | 77% | After tests ✓ |
```

---

## Section 4: Update Backlog

Edit `unit-test-agent/backlog.md` for the class:

**Before:**
```
| com.example.extract.StructToCsvFn | draft-plan | 50.0 | 40.0 | 35 | [plan](../plans/StructToCsvFn.plan.md) | |
```

**After:**
```
| com.example.extract.StructToCsvFn | done | 85.0 | 77.0 | 6 | [plan](../plans/StructToCsvFn.plan.md) | [report](../reports/20261126-100000/07-coverage-StructToCsvFn.md) |
```

**Status legend:**
- `done` = all test cases implemented, coverage targets met, no failures
- `done-below-target` = coverage below target but gaps are unreachable
- `done-with-suspected-bugs` = coverage met, but some tests are disabled for bugs
- `skip-too-hard` = >3 fix attempts failed
- `skip-prod-bugs` = too many production bugs block testing

---

## Section 5: Coverage Decision Tree

```
Coverage measurement complete
    ↓
Coverage >= Target?
    ├─ YES ✓
    │   ├─ Unreachable gaps?
    │   │   ├─ YES → Record in report; mark done
    │   │   └─ NO → Mark done
    │   └─ DONE: Proceed to Phase 10
    │
    └─ NO ✗
        ├─ Gaps are testable?
        │   ├─ YES → Propose new test cases
        │   │   ├─ User approves → Plan extension round (back to Phase 2)
        │   │   └─ User declines → Mark done-below-target
        │   │
        │   └─ NO (unreachable) → Mark done-below-target
        │
        └─ Update backlog status
```

---

## Section 6: Coverage Extensions (Optional)

If coverage is below target and gaps are testable:

### Step 6a: Propose test case extension

```
Current coverage: 75% line, 68% branch
Target: 80% line, 75% branch

Gaps identified:
1. extractMetadata() method: not tested
   - 2 test cases would add 10 lines, 5 branches
   - Estimated gain: +3% line, +2% branch

2. error handling in validateInput(): not tested
   - 1 test case (exception path) would add 5 lines
   - Estimated gain: +2% line

Proposal:
Plan extension: Add 3 test cases for methods 1–2 above
Estimated new coverage: 80% line, 75% branch ✓

Would you like to approve this extension?
```

### Step 6b: If user approves

- Create new plan in `unit-test-agent/plans/FQCN-extension.plan.md`
- Request approval (Phase 4)
- Generate and test new cases (Phases 6–7)
- Measure coverage again
- Update backlog

---

## Section 7: Finish and Recommend

### Step 7a: Print coverage summary

```
Coverage Report Complete
========================

Class: StructToCsvFn
Line coverage: 85% (target 80%) ✓ PASS
Branch coverage: 77% (target 75%) ✓ PASS

Tests: 6 implemented, 5 passed, 1 disabled (suspected bug)
Gap: extractMetadata() not tested (future round OK)

Status: DONE

Recommend: Proceed to Phase 10 (wrap-up)
```

### Step 7b: Return to supervisor

```
Phase 8 (Coverage reporting) complete.
Moving to Phase 10 (Wrap-up).
```

---

## Troubleshooting

| Issue | Cause | Action |
|-------|-------|--------|
| Guard says "stale report" | JaCoCo report is old | Rerun `mvn clean test -Pjacoco` |
| Coverage tool not found | JaCoCo not in pom.xml | Configure JaCoCo plugin; rerun Maven |
| Coverage dropped | No tests implemented for class | Check if tests actually compiled and ran |
| Coverage exactly at target | Random chance; rerun to verify | May be coincidence; check individual methods |
| Below target, can't add tests | Design is test-hostile | Mark `done-below-target` or `skip-too-hard` |

---

**You are done with Phase 6 (Coverage Reporting).**

Backlog updated. Return to Supervisor for Phase 10 (Wrap-up).
