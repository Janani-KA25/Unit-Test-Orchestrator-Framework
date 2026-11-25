# Test Plan Template

**When to create:** Phase 2 (ut-plan) for each new class  
**Location:** `unit-test-agent/plans/FQCN.plan.md`  
**Copy this template and fill in for each class**

---

```markdown
# Test plan for ClassName

class: com.example.ClassName
class_type: concrete|interface|abstract|enum
test_file: src/test/java/com/example/ClassNameTest.java
status: draft

## What the class does
(1–3 sentences. Copied from Javadoc or README.)

## Where the expected behaviour comes from
| Source | What it specifies |
|--------|-------------------|
| Javadoc on method X | Returns... |
| Existing test <Name> | Shows that... |
| README section Y | Describes... |
| (code intent only) | No written spec; test describes current code |

## Methods and paths
| Method | Paths (key branches) | Covered by |
|--------|----------------------|-----------|
| method1 | branch A, branch B | TC-001, EC-001 |
| method2 | branch X, branch Y | TC-002, EC-002 |

## Test cases
| ID | Type | Method | Given | When | Then (exact assertions) | Expected value comes from |
|----|------|--------|-------|------|--------------------------|---------------------------|
| TC-001 | positive | methodName | setup data | action | assertion | Javadoc/Code/Spec |
| EC-001 | edge | methodName | edge case | action | assertion | Javadoc/Code |
| ES-001 | error | methodName | error input | action | assertion | @annotation/Javadoc |

## Collaborators
| Dependency | Real or mock | Why |
|------------|--------------|-----|
| Dependency1 | Real/Mock | Reason |
| Dependency2 | Real/Mock | Reason |

## Spec and code disagree
(List any discrepancies. If none, write "none noticed".)

## Existing tests (not duplicated, never edited)
(List test methods already in <ClassName>Test.java. If none, write "none".)

## Testability notes
- Lines unreachable from unit tests: ... (why?)
- Private methods only reachable via: ...
- Known issues: ...

## Expected coverage (estimate only)
Line: XX% (target YY%)
Branch: XX% (target YY%)

## Change log
| Date | Change |
|------|--------|
| [DATE] | Plan created |

---

```

---

## Workflow

1. **Phase 2 (ut-plan):** AI fills this template, prints summary
2. **Phase 4 (Approval):** User approves or asks for changes
3. **Phase 6 (ut-generate):** AI reads plan, generates test code with case IDs
4. **Phase 7 (ut-verify):** Guard validates test methods match case IDs
5. **Phase 8 (ut-report):** Coverage updated; plan links to report

**Never edit a plan after Phase 4 approval** (would break hash validation).

---

## Validation Checklist

Before requesting approval, verify:
- [ ] All case IDs are unique (TC-001, EC-001, ES-001)
- [ ] Each case has: ID, type, method, given, when, then, expected from
- [ ] Expected values are grounded in written source
- [ ] Testability notes explain unreachable lines
- [ ] "Spec and code disagree" section filled
- [ ] Collaborators table is complete
- [ ] Estimated coverage is reasonable
- [ ] Change log has entries
- [ ] Class type is specified
- [ ] Test file name matches planned location
