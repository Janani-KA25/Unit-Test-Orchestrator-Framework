# Test Backlog Template

**Repository:** [Your Project Name]  
**Last updated:** [DATE]  
**Maintained by:** Supervisor skill  

---

## Classes to Test

| Class | Status | Line Cov | Branch Cov | Test Count | Plan | Report | Notes |
|-------|--------|----------|------------|------------|------|--------|-------|
| (to be filled by supervisor) | queued | - | - | 0 | | | |

---

## Status Legend

| Status | Meaning | Action |
|--------|---------|--------|
| **queued** | Waiting for selection (Phase 2) | Pick next |
| **draft-plan** | Plan created, awaiting approval (Phase 4) | User approves/rejects |
| **generating** | Tests being written (Phase 6) | In progress |
| **verifying** | Tests running (Phase 7) | In progress |
| **coverage** | Coverage measurement (Phase 8) | In progress |
| **done** | Coverage met, all tests passed | Reference for next rounds |
| **done-below-target** | Coverage below target, gaps unreachable | Acceptable; note in report |
| **done-with-suspected-bugs** | Coverage met, some tests disabled | Bug tickets filed |
| **skip-too-hard** | >3 fix attempts failed | Revisit later |
| **skip-prod-bugs** | Too many bugs block testing | Wait for production fixes |

---

## Coverage Summary

| Metric | Baseline | Current | Target | Delta |
|--------|----------|---------|--------|-------|
| **Line** | - | - | 80% | - |
| **Branch** | - | - | 75% | - |

**Baseline date:** [DATE]  
**Current date:** [DATE]  
**Progress:** 0/N classes done

---

## Suspected Bugs (Tickets)

(None yet)

---

## Notes for Next Rounds

(To be filled as testing progresses)

---

## Template Usage

**To add a new class:**

```markdown
| com.example.NewClass | queued | XX.X | XX.X | 0 | | | Brief description |
```

**To update status:**

```markdown
| com.example.ClassName | done | 85.0 | 77.0 | 6 | [plan](../plans/ClassName.plan.md) | [report](../reports/RUNID/07-coverage-ClassName.md) | Notes |
```

**To add suspected bug:**

```markdown
| [repo/owner/issues/NNN](link) | ClassName | test_method_name | OPEN |
```

---

**Maintained by supervisor skill at each phase.**
