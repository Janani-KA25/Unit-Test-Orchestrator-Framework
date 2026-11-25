# Class Report Template

**When to create:** Phase 8 (ut-report) for each tested class  
**Location:** `unit-test-agent/reports/$RUN_DIR/07-coverage-ClassName.md`  
**Copy this template and fill in the measurement results**

---

```markdown
# Coverage Report for ClassName

Generated: [DATE, TIME UTC]
Run: unit-test-agent/reports/[RUN_ID]/

## Coverage Summary
| Metric | Value | Target | Status |
|--------|-------|--------|--------|
| Line | XX% | YY% | ✓ PASS / ✗ FAIL |
| Branch | XX% | YY% | ✓ PASS / ✗ FAIL |

## Methods Coverage Detail
| Method | Type | Line Cov | Branch Cov | Status |
|--------|------|----------|-----------|--------|
| method1 | public | X% | Y% | ✓/⚠ |
| method2 | private | X% | Y% | ✓/⚠ |

## Uncovered Lines (Analysis)

### Unreachable (OK to skip)
- **Line XX–YY:** Description
  - Reason: Why is this unreachable?
  - Verdict: Unreachable; not testing

### Testable but Uncovered (Gap)
- **Line XX:** Description
  - Reason: Why wasn't this tested?
  - Verdict: Could add test; priority (high/medium/low)

## Coverage Assessment
✓ **GOAL MET** / ✗ **GOAL NOT MET**

Summary of what was achieved:
- Line coverage (XX%) meets/exceeds/falls below target (YY%)
- Branch coverage (XX%) meets/exceeds/falls below target (YY%)
- Unreachable code properly excluded
- Key paths covered: (list methods)

## Test Case Summary
- Tests run: N
- Tests passed: N
- Tests failed: N
- Tests disabled: N (suspected bugs)
- Coverage added: ~NN lines vs baseline

## Backlog Update
Class ClassName moved to: done / done-below-target / done-with-suspected-bugs

## Recommendations
1. ✓ **Continue to Phase 10 (wrap-up)** / ⚠ **Consider extension** / ✗ **Review needed**
2. Future round: (what could be improved)
3. Next steps: (action required, if any)
```

**Example recommendations:**
1. ✓ **Continue to Phase 10 (wrap-up)**
2. Future round: Consider testing extractMetadata() separately or as part of full integration test
3. No action needed on suspected bug (ticket filed separately)

## Coverage Trend
| Run | Date | Line | Branch | Status |
|-----|------|------|--------|--------|
| Baseline | 2026-11-25 | 50% | 40% | Before tests |
| This run | 2026-11-26 | 85% | 77% | After tests ✓ |

(optional: include multiple runs if doing multiple rounds)

## Notes
- (Any special observations, e.g., "One test disabled for suspected bug")
- (Any dependencies or assumptions)
- (Any recommendations for future testing)

**Example notes:**
- One test disabled: processElement_nullColumn_writesEmptyField (Javadoc says empty string, code returns "NULL")
- Beam DirectRunner used (deterministic, no flakiness)
- Spanner Struct built in-process (no external calls)

---

```

---

## Workflow

1. **Phase 8 (ut-report):** AI runs guard coverage, fills template, updates backlog
2. **Phase 10 (Wrap-up):** Supervisor summaries all reports across run
3. **Phase 11 (Resume):** User can review report and decide next class

---

## Validation Checklist

Before finalizing, verify:
- [ ] Coverage numbers match guard output
- [ ] Uncovered lines are categorized (unreachable vs gap)
- [ ] Assessment is clear (goal met or not)
- [ ] Test case summary matches actual test count
- [ ] Backlog status is updated
- [ ] Recommendations are specific and actionable
- [ ] Notes explain any unusual findings
- [ ] Coverage trend is documented
- [ ] All linked reports/plans exist
- [ ] Generated timestamp is current
