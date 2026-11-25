# Run Summary Template

**When to create:** Phase 10 (Wrap-up), end of testing run  
**Location:** `unit-test-agent/reports/$RUN_DIR/run-summary.md`  
**Summarizes entire run across all classes tested**

---

```markdown
# Unit Test Agent - Run Summary

**Run ID:** [RUN_ID]  
**Started:** [DATE/TIME UTC]  
**Ended:** [DATE/TIME UTC]  
**Duration:** [HOURS]  
**Status:** ✓ COMPLETE

---

## Run Overview

This run tested **N classes**:

| Class | Status | Line Cov | Branch Cov | Test Count | Report |
|-------|--------|----------|------------|------------|--------|
| com.example.ClassName | done | X% | Y% | N | [report](report-link) |

---

## Coverage Progress

### Baseline (before this run)
- Line: X%
- Branch: X%

### After this run
- Line: X%
- Branch: X%

### Improvement
- **+X% line coverage**
- **+X% branch coverage**

---

## Classes This Run

(Detailed findings for each class)

---

## Suspected Bugs (Tickets)

(GitHub issues filed for production bugs found)

---

## Tests Created

| File | Methods | Status |
|------|---------|--------|
| src/test/java/com/example/ClassNameTest.java | N | ✓ PASS |

**Total:** N test methods, X passing, Y disabled, Z failing

---

## Approval Decisions

| Phase | Date | Decision | Approver |
|-------|------|----------|----------|
| Phase 2 | [DATE] | Select class | user |
| Phase 4 | [DATE] | Approve plan | user |
| Phase 9 | [DATE] | Continue/Stop | user |

---

## Artifacts

**Plans:** unit-test-agent/plans/ClassName.plan.md  
**Tests:** src/test/java/com/example/ClassNameTest.java  
**Reports:** unit-test-agent/reports/[RUN_ID]/07-coverage-*.md  
**Audit Trail:** unit-test-agent/audit.log

---

## Next Steps

1. Review this summary
2. Address suspected bugs (if any)
3. Plan next testing round for additional classes

```

---

## Workflow

1. **Phase 10 (Wrap-up):** Supervisor fills this template
2. **User reviews:** Checks coverage, bugs, recommendations
3. **Decision:** Continue or stop

---

## Validation Checklist

Before finalizing:
- [ ] All class reports linked
- [ ] Coverage numbers match reports
- [ ] All test files listed
- [ ] Approval decisions documented
- [ ] Artifacts exist
- [ ] Bugs (if any) have issue links
- [ ] Next steps clear
