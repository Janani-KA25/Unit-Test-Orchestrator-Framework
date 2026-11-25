# Orchestrator Architecture: Design Decisions

This document explains the **why** behind the unit-test orchestrator framework. Read this after the README to understand the trade-offs and principles.

## 1. Why One Agent + Multiple Skills (Not Multiple Subagents)?

### Design Choice
- **One AI agent** that reads sequential **instruction files** (skills).
- **No subagents** – the supervisor doesn't spawn workers.

### Why Not Multiple Subagents?

| Aspect | Multiple Subagents | One Agent + Skills |
|--------|-------------------|-------------------|
| **Context** | Each subagent starts fresh; loses session context | Single agent carries full history |
| **Handoff cost** | High: serialize state, spawn process, deserialize | Low: read next skill file, continue |
| **Debugging** | Failures scattered across logs | Single conversation thread |
| **Cost** | Multiple API calls + overhead | One continuous session |
| **Consistency** | Subagents may diverge (same instruction → different output) | Single session ensures consistency |
| **State sharing** | Must be explicit (file I/O) | Implicit in memory |

**Result:** One agent + skills is cheaper, faster, and more reliable for sequential workflows.

### How the Supervisor Instructs

The supervisor skill is **not code**; it's **prose**:

```markdown
## Phase 2: Analyze the repository

You are now in Phase 2. Read and follow `.devin/skills/ut-analyze/SKILL.md`.

That skill will instruct you to:
1. List all production classes.
2. Run `mvn test` and `mvn jacoco:report`.
3. Parse the baseline coverage from `target/site/jacoco/`.
4. Write `01-repo-analysis.md`.

After you finish analyzing, return here. I will tell you the next phase.

[supervisor continues after the agent finishes ut-analyze]
```

The supervisor is a **master script** that orchestrates other scripts. It doesn't run them in parallel; it reads one after the other.

## 2. Explicit Approval Gates (Human Authority)

### Design Choice
- Plan **must be approved before** test code is generated.
- Approval is **recorded** (SHA-256 hash of plan + timestamp).
- Generation only proceeds if **`APPROVE: RECORDED`** is confirmed.

### Why This Matters

**Problem (without approvals):** AI generates 50 test cases silently → 90% are nonsense → human rejects all → wasted tokens and trust.

**Solution (with approvals):** AI generates a 10-case plan → human reviews (5 seconds) → approves or asks for changes (high-value feedback) → AI generates 10 tests with confidence.

### Approval Record

`UtGuard approve` writes:

```
approved_at: 2026-11-19T09:42:10Z
plan: unit-test-agent/plans/StructToCsvFn.plan.md
plan_sha256: 9f2c...e1
cases: 10
reply: yes, approve
```

If the plan is **edited after approval**, the hash no longer matches → `UtGuard check` fails → model must ask for re-approval. This prevents silent changes to test intent.

### Cost Benefit

| Cost | Benefit |
|------|---------|
| +1 approval prompt per class | Prevents 50+ auto-generated failures |
| +5 seconds per human decision | Confidence in generated tests |
| Human rejection forces re-plan (loop) | Feedback is cheap when plans are small |
| Final run summary requires re-review | But only 1–2 classes, not 50 |

## 3. Deterministic Checks (AI ≠ Verification)

### Design Choice
- Guard utility (Java) performs all **verification** independent of AI.
- Guard writes **only** to `audit.log` (never overwrites analysis/reports).
- Guard exit codes indicate specific failure modes (compile fail vs. test fail vs. coverage stale).

### What Guard Does (Deterministic)

| Check | Input | Output | Why not AI? |
|-------|-------|--------|-----------|
| `snapshot` | File list under `src/`, `pom.xml` | SHA-256 hashes | Reproducible; detects tamper |
| `approve` | Plan file + user reply text | Approval record + audit line | Credential detection; plan validation; exact timestamp |
| `check` | Plan file, new test file, baseline snapshot | List of violations (banned APIs, missing assertions, etc.) | Regex-based; deterministic; no false negatives |
| `results` | Two Surefire XML reports | Comparison (same tests? same outcomes?) | Exact parsing; flaky detection requires bit-exact comparison |
| `coverage` | `jacoco.xml` file | Line/branch percentage per class | XML DOM parsing; refuses stale data |
| `log` | Event name + detail | Append to `audit.log` | Only source of truth for what happened |

### Why AI Alone Is Insufficient

**Example:** AI reads Surefire XML and says "tests look stable". But it only skimmed the output; a timing-dependent test *actually* failed in run 1 and passed in run 2. The guard's deterministic XPath query `//testsuite/@failures` catches it; AI might miss it in prose.

**Example 2:** AI says "assertion is present" by grep. But it's in a comment: `// assertThat(...` or `String s = "assert this"`. The guard uses a regex with comment-stripping, ensuring it finds real assertions.

### Why Audit Log Is Guard-Only

If AI could write to `audit.log`, it could hide mistakes:

```
AI: "Coverage is 74%."
AI writes: COVERAGE_MEASURED 74% ✓
(But it misread; actually 64%)
AI: "Report complete."
(Audit looks clean)
Human: "Backlog still shows 30%?!"
(Now we're confused)
```

By restricting writes to the guard, the **audit trail is authoritative**. If AI claims "tests pass" but `audit.log` says `RESULTS: FLAKY`, we trust the audit.

## 4. Stateful Resume (Pause-and-Continue)

### Design Choice
- Every phase **saves state** to `state.json`.
- Resume reads `state.json` and determines where to restart.
- No data is lost between pauses.

### State Machine

```json
{
  "currentRun": "unit-test-agent/reports/20261126-091500",
  "phase": "PLAN_DRAFT",
  "currentClass": "com.example.ClassName",
  "runs": [],
  "lastBaseline": {
    "line": 52.0,
    "branch": 38.0,
    "date": "2026-11-26",
    "run": "20261126-091500"
  }
}
```

### Why This Matters

**Scenario 1 (without stateful resume):** AI generates a plan. User says "needs changes". AI generates a new plan from scratch. All context is lost; plan may contradict the first version.

**Scenario 2 (with stateful resume):** `state.json` shows `phase: PLAN_DRAFT`. AI reads the existing plan file, understands the user's feedback, and revises. Context is preserved.

**Scenario 3 (long run):** Class 1 completes. Human interrupts (goes to lunch). `/unit-test-agent resume` reads `state.json`, shows "Class 1 done, pick the next class" (no replay of class 1's work). Very efficient.

## 5. One Class at a Time (Bounded Scope)

### Design Choice
- Each round, **exactly one class** is selected by the human.
- Plan for one class is approved.
- Tests for one class are written and verified.
- Coverage for one class is measured.
- **Then ask "which class next?"**

### Why Not Batch?

**Without one-at-a-time:**
- AI picks 5 classes to test (arbitrary).
- Generates 200 test cases (unbounded).
- 50 fail; human can't pinpoint which class is the problem.
- Retry entire batch = expensive.

**With one-at-a-time:**
- Human picks "StructToCsvFn".
- AI plans 10 cases; human approves.
- 10 tests generated, 9 pass, 1 fails.
- Triage clearly shows "StructToCsvFn test failed".
- Fix and retry (1 class, not 5).

### Enforcement

**Instruction:** "One class per round. Never start a class the user didn't pick."

**Mechanical check:** `UtGuard check` fails if any new file is not named in the approved plan. This prevents sneaky multi-class work.

## 6. Failure Classification (Test Bug vs. Prod Bug vs. Plan Error)

### Design Choice
- Every failure is classified into one of four categories.
- Different **action** per category.
- Retry logic only applies to test bugs and plan errors.

### Categories

| Category | Meaning | Action | Counts Toward Retry Limit? |
|----------|---------|--------|---------------------------|
| **TEST_BUG** | Test code is wrong (bad mock, missing assertion, etc.) | Fix test code, rerun | Yes (up to 3) |
| **PLAN_ERROR** | Specification in plan is wrong (expected value incorrect) | Revise expectation, rerun | Yes (up to 3) |
| **SUSPECTED_PROD_BUG** | Test fails because production code doesn't match spec | Add `@Disabled("Suspected bug: ...")`, log ticket, don't retry | No (doesn't count) |
| **ENVIRONMENT** | Infrastructure is broken (JDK, Maven, Spanner unavailable) | Stop, tell user | No |

### Why This Separation?

If we treated everything as "test bug," we'd spend 3 retries fixing test expectations when the **production code is actually buggy**. By classifying first, we:

1. **Preserve test attempts** for real problems.
2. **Document suspected bugs** for the team to triage.
3. **Distinguish environment failures** (stop immediately).
4. **Avoid false negatives** (we don't weaken assertions to make tests pass).

**Example:**
- Javadoc says: "DATE columns formatted as YYYY-MM-DD".
- Test expects: `"2024-01-31"`.
- Code returns: `"2024/01/31"` (wrong).
- Result: `SUSPECTED_PROD_BUG` (not a test bug). Class stays at current coverage; a human fixes the code later.

## 7. Fail-Safe Audit Trail (Guard Writes Only)

### Design Choice
- **Only the guard** writes to `audit.log`.
- Every guard command appends a tab-separated line.
- Log is append-only, never overwritten.

### Sample Audit Log

```
timestamp                      event               result  detail
2026-11-19T09:42:10Z          GUARD_SNAPSHOT      ok      9 files hashed
2026-11-19T09:45:30Z          PLAN_APPROVED       ok      StructToCsvFn 10 cases
2026-11-19T09:50:15Z          GUARD_CHECK         fail    Missing assertion in TC-003
2026-11-19T09:51:00Z          CLASS_OUTCOME       blocked Guard check failed; fix test and re-approve plan
2026-11-19T10:05:30Z          TESTS_WRITTEN       ok      StructToCsvFnScalarTest.java 9 methods
2026-11-19T10:15:00Z          COVERAGE_MEASURED   ok      74.3% line, 64.3% branch
```

### Why Guard-Only Writes?

| Scenario | AI Writes Log | Guard Writes Log |
|----------|---------------|------------------|
| AI makes mistake in coverage numbers | "74%" written; human trusts it | "Coverage: not measured" (log says guard didn't finish) |
| Human reviews audit later | Finds false claims | Finds only truth (what guard verified) |
| Debugging a blocked class | "What went wrong?" → hard to tell | "Guard check failed on [line X]" → clear root cause |
| Audit trail integrity | AI could hide failures | Violations caught by guard are unambiguous |

## 8. File Permissions (Defense in Depth)

### Design Choice
- Supervisor declares `permissions` (deny/ask).
- These are enforced by the **Devin permission system**.
- Guard's snapshot + hashes provide a **second layer** of detection.

### Permission Config

```yaml
permissions:
  deny:
    - Write(src/main/**)        # Hard block: AI cannot edit production code
    - Write(pom.xml)            # Hard block: AI cannot change dependencies
    - Exec(git push)            # Hard block: prevent accidental commits
  ask:
    - Exec(java .../UtGuard.java approve)  # Always prompt before approving
    - Write(config.json)                    # Always prompt before changing limits
  allow:
    - Write(src/test/java/**)   # Auto-approve: test generation is expected
    - Write(unit-test-agent/**)            # Auto-approve: workflow state
```

### Why Multiple Layers?

**Layer 1 (Devin permissions):** Fast, blocks before execution. ← Primary defense

**Layer 2 (Guard snapshot):** Catches violations that bypass permissions (e.g., a shell command edits `pom.xml` directly). ← Secondary detection

**Layer 3 (AI instruction):** "Never edit src/main" (guardrail 1). ← Weakest but cheapest

## 9. Plans as Contracts (Reversibility)

### Design Choice
- Plan file is the **contract** between human and AI.
- Once approved, the plan is **immutable** (SHA-256 protected).
- Tests must implement the cases named in the plan.
- If plan changes, re-approval is required.

### Why Plans?

**Without plans:**
```
AI: "Here are 50 test cases. Ready?"
Human: "Wait, did you test the error path?"
AI: "Oh, I didn't see that in the requirements."
Human: "Now I have to debug your code to find what's wrong."
(Expensive)
```

**With plans:**
```
AI: "Here's a plan: 10 cases, 2 edge cases, 1 error case. Approval?"
Human: (reviews in 10 seconds) "Yes, approve."
AI: Writes 10 tests with exact names (TC-001 ... TC-010).
Human: If a test fails, we know it's one of the 10 planned cases, not 50 surprises.
(Cheap)
```

### Plan Contents (Template)

```markdown
# Test plan for ClassName

test_file: src/test/java/com/example/ClassNameTest.java
status: draft

## Methods and paths
| Method | Paths | Covered by |
|--------|-------|-----------|
| method1 | branch A, branch B, branch C | TC-001, EC-002, ES-001 |
| method2 | type X, type Y, type Z | TC-002, TC-003, TC-004 |

## Test cases
| ID | Method | Given | When | Then | Expected from |
|----|--------|-------|------|------|---------------|
| TC-001 | processElement | Struct ID=STRING "p-1", COUNT=INT64 42 | run in Pipeline | output exactly "p-1","42" | Javadoc |
| EC-001 | processElement | NULL column | run | empty field "" | Javadoc |
| ...
```

The guard checks:
- Plan has case IDs (TC-001, EC-001, ES-001).
- Test file exists under `src/test/java/`.
- Each ID in plan appears as a comment in the test file: `// TC-001`.

If the test file exists but doesn't name all IDs, the guard fails. Plan and code are kept in sync.

## 10. Coverage as a Stopping Point (Not a Reward)

### Design Choice
- Coverage is measured **after all tests pass and are stable**.
- Coverage is **never the reason to generate tests** (spec is).
- If coverage is below target and "valuable gaps remain," propose extra cases **once per round**.
- Accept or decline; no auto-generation.

### Why Not "Generate until 80% coverage"?

**Risk (without approval):**
- AI generates tests until coverage meter says 80%.
- Many tests are low-quality (just hitting lines, not testing behavior).
- Fake high coverage.

**Benefit (with gate):**
- Coverage is a **measurement**, not a **target**.
- If 74% and gaps are unreachable (e.g., exception path), that's fine → `done-below-target`.
- If 74% and gaps are valuable (e.g., STRING type handling), propose extra cases. Human decides: "Worth it?" Yes/No.

### Decision Matrix

```
Coverage | Target | Gap quality | Action |
---------|--------|-------------|--------|
74%      | 80%    | Unreachable | done-below-target, documented |
74%      | 80%    | Valuable    | Propose 5 extra cases; ask Yes/No |
85%      | 80%    | n/a         | done |
50%      | 80%    | Fatal       | blocked (< max_fix_attempts) |
```

## 11. Audit Log As History (Not Realtime)

### Design Choice
- Audit log is **append-only**.
- It is not a **liveStream** but a **final record**.
- Printed at the end of a run; not updated mid-run.

### Why Append-Only?

Prevents:
- Accidental overwrites.
- Loss of evidence (every event is kept).
- Circular reasoning ("it says success, so it must have succeeded").

**Once an event is logged, it is final:**

```
PLAN_APPROVED   ok     StructToCsvFn 10 cases
(This is now *truth*. If test generation fails, we know the plan was approved.)
```

## 12. Boundaries of AI vs. Determinism

| Activity | AI | Guard | Why |
|----------|----|----|---|
| Read code | AI | Guard regex (no parser) | Understand intent; detect syntax |
| Generate tests | AI | (not applicable) | Creativity |
| Grade tests (quality) | AI + human | (not applicable) | Judgment |
| Count tests that run | Guard | AI (read Maven output) | Deterministic |
| Count assertions | Guard regex | AI (read code) | Deterministic first |
| Grade failure (test vs. prod bug) | AI | Guard exit code | AI's judgment; guard's signal |
| Decide retry | AI + guardrail | (not applicable) | Follow rules |
| Measure coverage | Guard XML | AI (read report) | Deterministic first |
| Grade coverage (target OK?) | AI | Guard calculation | Deterministic first |

**Principle:** Use the guard for **anything that can be verified mechanically**. Use AI for **interpretation, creativity, and judgment**. Never use AI for **audit or verification**.

---

## Summary: The Design Ladder

```
Level 1: Specification (Human)
  "Build tests for StructToCsvFn to 80% coverage."
           ↓
Level 2: Plan (AI → Human Approval)
  "Here are 10 test cases with IDs. Approve?"
           ↓
Level 3: Code (AI)
  "Generating StructToCsvFnScalarTest.java with 10 methods tagged TC-001 ... TC-010."
           ↓
Level 4: Verification (Guard)
  "Checking: plan unchanged (hash OK), code implements all IDs, assertions present, no banned APIs."
           ↓
Level 5: Execution (Maven)
  "Running tests; 10 pass, 0 fail, 0 timeout."
           ↓
Level 6: Measurement (Guard)
  "Coverage: 74.3% line, 64.3% branch. Below target. Propose 5 extra cases?"
           ↓
Level 7: Decision (Human)
  "No, the gap is unreachable. Mark as done-below-target."
           ↓
Level 8: Report (AI)
  "Written to class-report-StructToCsvFn.md. Backlog updated. Next class?"
           ↓
Level 9: History (Guard)
  "Audit log: plan approved, guard passed, tests run, coverage measured, outcome recorded."
```

Each level **hands off** to the next. Each level has explicit inputs and outputs. Each level can fail independently, and failures are **caught and classified**. This is **robust orchestration**.
