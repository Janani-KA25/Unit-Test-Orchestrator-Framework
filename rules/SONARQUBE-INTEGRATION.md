# SonarQube Quality Gates Integration

**Organization Standard:** Org SonarQube Instance  
**Quality Profile:** Sonar way (Java)  
**Coverage Gate:** ≥80% line, ≥75% branch (org baseline)

---

## Overview

This orchestrator framework is built to generate unit tests that **automatically comply** with your organization's SonarQube quality gates and the **Sonar way (Java)** profile.

All generated tests are validated by the **UtGuard** utility to ensure they pass org quality rules **before** approval.

---

## Mandatory SonarQube Rules (Enforced by Framework)

| Rule ID | Rule Name | What It Requires | Framework Action |
|---------|-----------|------------------|------------------|
| **java:S2699** | Mandatory Assertions | Every `@Test` has ≥1 assertion | ✓ UtGuard validates all tests have assertions |
| **java:S5785** | Appropriate Assertion Usage | Use fluent assertions (AssertJ) over generic boolean | ✓ Code generation templates prefer AssertJ |
| **java:S3577** | Test Class & Method Naming | Tests follow `*Test.java` class and `method_condition_result` naming | ✓ Naming conventions enforced, guard checks |
| **java:S5778** | Exception Testing | Use `assertThrows()` not try-catch blocks | ✓ Code generation templates use `assertThrows()` |
| **java:S6073** | No PowerMock or Static Mocking | Never use `mockStatic()`, `mockConstruction()` | ✓ Banned imports list; guard check detects |
| **java:S6068** | Mocking Best Practices | Mock interfaces; avoid @Spy on production classes | ✓ Code examples show proper Mockito patterns |
| **java:S2068** | No Hardcoded Secrets | Never include real credentials, API keys, PHI/PII | ✓ Guard regex scan for credential patterns |

---

## Coverage Gate (Org Baseline)

### Target Thresholds
- **Line Coverage:** ≥ 80%
- **Branch Coverage:** ≥ 75%

### How They're Measured
1. **JaCoCo Report:** Run `mvn clean test -P jacoco && mvn jacoco:report`
2. **Guard Measurement:** `java UtGuard coverage --class ClassName` extracts JaCoCo numbers
3. **Report:** UtGuard output recorded in coverage-ClassName.md
4. **Status Decision:**
   - **Goal Met:** Class marked `done`
   - **Below Target:** Class marked `done-below-target` with gap analysis
   - **With Bugs:** Class marked `done-with-suspected-bugs` with ticket links

### Configuration
Edit `templates/config.json`:
```json
{
  "line_coverage_target": 80,
  "branch_coverage_target": 75,
  "class_targets": {
    "com.example.expensive.Pipeline": [40, 30],
    "com.example.edge.EdgeCaseHandler": [90, 85]
  }
}
```

**Per-class overrides:** If a class is legitimately hard to test (integration-heavy, external dependencies), set lower targets. Document why in class-report.md.

---

## Quality Gate Validation Workflow

### Phase 7 (Verification): Guard Check

```bash
java UtGuard check --run $RUN_DIR --plan unit-test-agent/plans/ClassName.plan.md
```

**Checks performed:**
- ✓ Plan hash unchanged (approved, not edited)
- ✓ All test case IDs present in generated test file
- ✓ No banned imports (PowerMock, Testcontainers, Spring injection)
- ✓ No hardcoded secrets (regex scan for credential patterns)
- ✓ Every `@Test` method has an assertion
- ✓ Every `@Disabled` test has `Suspected bug:` reason
- ✓ Correct naming convention (`*Test.java`, `method_condition_result`)

**Exit codes:**
- `0` – All checks passed ✓
- `1` – Validation failed (fix and retry, up to 3 attempts)
- `2` – Error (environment issue)

### Phase 8 (Measurement): Coverage Check

```bash
java UtGuard coverage --class com.example.ClassName
```

**Checks performed:**
- ✓ JaCoCo report exists and is current
- ✓ LINE coverage measured
- ✓ BRANCH coverage measured
- ✓ Compare against targets (80% line, 75% branch)

**Exit codes:**
- `0` – Coverage measured ✓
- `1` – Below target (acceptable if gaps unreachable)
- `3` – Stale (JaCoCo older than test results; re-run build)

---

## Common Issues & Resolutions

| Issue | Root Cause | Resolution | SonarQube Rule |
|-------|-----------|-----------|----------------|
| "FAIL: All tests assert" | Test method has no assertion | Add at least one assertion (e.g., `assertThat(result).isNotNull()`) | java:S2699 |
| "FAIL: Credentials detected in test" | Hardcoded API key or password | Replace with synthetic test data, e.g., `"test-key"` or `"mock-password"` | java:S2068 |
| "FAIL: Banned import detected" | PowerMock or Testcontainers imported | Remove banned import; use Mockito instead | java:S6073 |
| "FAIL: Missing @Disabled reason" | `@Disabled` without `Suspected bug:` prefix | Add reason starting with `"Suspected bug: ..."` to `@Disabled` | Framework rule |
| "Coverage below 80%" | Not enough test cases or branches covered | Add more test cases (TC-X, EC-X, ES-X) or simplify code | Guardrail 7 |
| "Branch coverage low" | Conditional branches not tested | Add test cases for each if/else, switch case, loop condition | Guardrail 7 |

---

## SonarQube Export & Verification

### Export Org Quality Profile
If you want to review the full active quality profile:

1. Log into your org SonarQube instance
2. Navigate to **Quality Profiles** → **Java** → **Sonar way** (or search by org)
3. Click **Export** (top-right menu)
4. Save as JSON/XML
5. Share with the agent if any project-specific rules exist

### Scan Generated Tests (Optional)
To run a full SonarQube scan on generated tests after generation:

```bash
mvn clean test sonar:sonar \
  -Dsonar.host.url=https://your-sonarqube-instance.com \
  -Dsonar.login=YOUR_TOKEN
```

The orchestrator framework guarantees pre-scan compliance via UtGuard, so you should see:
- ✅ Zero violations for java:S2699, S5785, S5778, S6073, S2068, S3577
- ✅ Coverage meets targets (or is below-target but justified)

---

## Customization for Your Org

### If You Have Additional Rules
If your org SonarQube instance has **extra custom rules** beyond Sonar way (Java):

1. Document them in `rules/ut-test-conventions.md` under a new section: "Org Custom Rules"
2. Update the validation checks in `UtGuard.java` `check` command (regex patterns for new rules)
3. Add examples to `skills/ut-generate/SKILL.md` to guide code generation

**Example custom rule:**
```markdown
### Org Rule: No sleep in tests (Custom)
- Never use `Thread.sleep()` in test code
- Use fixed time (`LocalDate.of(...)`) or mock clocks instead
- Reason: Tests must be deterministic and fast
```

### If You Need Project-Specific Overrides
Edit `templates/config.json`:

```json
{
  "line_coverage_target": 80,
  "branch_coverage_target": 75,
  "class_targets": {
    "com.example.integration.DataImporter": [60, 50],
    "com.example.core.LogicEngine": [90, 85]
  }
}
```

---

## Quality Gate Compliance Checklist

Before declaring a testing round complete:

- [ ] All generated tests pass `java UtGuard check` ✓
- [ ] All tests pass compilation: `mvn test-compile` ✓
- [ ] All tests pass execution: `mvn test` ✓
- [ ] No flakiness detected (guard results: first run == second run) ✓
- [ ] Coverage measured: `java UtGuard coverage --class ClassName` ✓
- [ ] Coverage meets org target (≥80% line, ≥75% branch) **or** justified as below-target ✓
- [ ] Suspected bugs filed with GitHub tickets ✓
- [ ] Audit log complete: `cat unit-test-agent/audit.log` ✓
- [ ] Run summary generated and approved by user ✓

If all items are ✓, the round is **QUALITY GATE PASSED** and ready for production.

---

## Support & Escalation

**Question:** "My test fails SonarQube check but I don't know why"  
**Answer:** Run `java UtGuard check --run $RUN_DIR --plan your-plan.md` for detailed output. Cross-reference the error with the table above.

**Question:** "I need to lower coverage targets because class X is hard to test"  
**Answer:** Update `config.json` class_targets, document in class-report.md, and ask user to approve before class is marked done.

**Question:** "What if our SonarQube instance has different rules than Sonar way?"  
**Answer:** Export your org's quality profile, document the extra/different rules in `rules/ut-test-conventions.md`, and update UtGuard.java validation if needed. Ask your tech lead to review.

---

**Organization Quality Standard = Framework Default**

✅ All generated tests are org-compliant by design.  
✅ UtGuard enforces compliance before approval.  
✅ Coverage meets or exceeds org baseline (80% line, 75% branch).  
✅ Zero surprises when tests reach production SonarQube instance.
