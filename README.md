# Unit-Test Orchestrator Framework

A production-ready supervisor-based orchestration system for AI-driven unit test generation. This framework is designed to work with a **single AI agent** and **multiple sequential skills**, not multiple subagents.

## Architecture Overview

```
User Command (/unit-test-agent [Class|resume])
         ↓
Supervisor Skill (orchestration logic)
         ↓
Sequential Step Skills (ut-analyze → ut-plan → ut-generate → ut-verify → ut-report)
         ↓
Guard Utility (deterministic checks, approvals, audit log)
         ↓
Build System (Maven, JUnit, JaCoCo)
         ↓
Persistence (state.json, backlog.md, plans, reports, audit.log)
```

### Key Design Principles

1. **One Agent, Many Skills** – The same AI agent reads sequential instruction files (skills), never creates subagents.
2. **Explicit Approval Gates** – Plan approval before code generation; no silent code creation.
3. **Deterministic Checks** – Guard utility validates (snapshot hashes, approval records, test quality) independent of AI.
4. **Stateful Resume** – Runs can be paused and resumed from `state.json` without losing context.
5. **One Class at a Time** – Prevents runaway generation; human chooses scope each round.
6. **Fail-Safe Audit Trail** – Guard writes `audit.log` only; all events are recorded.

## Folder Structure

```
unit-test-orchestrator/
├── README.md                           (this file)
├── ARCHITECTURE.md                     (detailed design decisions)
├── supervisor/
│   └── SKILL.md                        (main orchestration skill)
├── skills/
│   ├── ut-analyze/
│   │   └── SKILL.md                    (Phase 1: repository analysis + baseline)
│   ├── ut-plan/
│   │   └── SKILL.md                    (Phase 2: test plan + approval gate)
│   ├── ut-generate/
│   │   └── SKILL.md                    (Phase 3: test code generation)
│   ├── ut-verify/
│   │   └── SKILL.md                    (Phase 4: guard checks + Maven test execution)
│   ├── maven-test-runner/
│   │   └── SKILL.md                    (Maven command reference)
│   ├── test-failure-triage/
│   │   └── SKILL.md                    (Phase 5: failure classification + retry logic)
│   ├── ut-report/
│   │   └── SKILL.md                    (Phase 6: coverage measurement + reporting)
│   ├── ut-status/
│   │   └── SKILL.md                    (read-only: current workflow state)
│   └── ut-guard/
│       ├── SKILL.md                    (reference: how to call guard commands)
│       └── UtGuard.java                (executable: snapshot, approve, check, results, coverage, log)
├── rules/
│   ├── ut-guardrails.md                (mandatory safety limits)
│   └── ut-test-conventions.md          (code style and banned patterns)
├── templates/
│   ├── config.json                     (targets, limits, overrides)
│   ├── state.json                      (runtime: current run, phase, class)
│   ├── backlog.md                      (class list with status and coverage)
│   ├── test-plan.md                    (approved test contract)
│   ├── class-report.md                 (outcome, coverage, findings)
│   └── run-summary.md                  (whole-run summary and next steps)
└── examples/
    └── single-class-flow.md            (walkthrough: one complete class → tests → coverage)
```

## Quick Start

### 1. For a New Project

Copy this framework into your repository:

```bash
cp -r unit-test-orchestrator <your-repo>/orchestrator/
```

Customize:

- **`rules/ut-guardrails.md`** – Add your project's forbidden edits, safety limits.
- **`rules/ut-test-conventions.md`** – Specify your testing stack (JUnit 5, Mockito, etc.) and naming conventions.
- **`templates/config.json`** – Set line/branch targets and class overrides.

### 2. To Use (AI Agent)

The user triggers the workflow with one of these commands:

```
/unit-test-agent                       # New run, ask which class
/unit-test-agent <ClassName>           # New run, start with this class
/unit-test-agent resume                # Continue previous run from state.json
/ut-status                             # Read-only: workflow status
```

The supervisor skill reads the command and orchestrates the phases.

### 3. To Customize for Your Domain

- **Update `rules/ut-test-conventions.md`** with your testing stack (JUnit 5, Mockito, AssertJ, etc.), naming conventions, and code style.
- **Add domain-specific examples** to your documentation (e.g., Spring Boot testing patterns, Hibernate examples, gRPC setup).
- **Edit `UtGuard.java`** (optional) to add project-specific validation checks.
- **Adjust `templates/config.json`** for your coverage targets and per-class overrides.

## Component Descriptions

### Supervisor Skill (`supervisor/SKILL.md`)

**Triggers:** User slash command (`/unit-test-agent`).  
**Responsibility:** Decide new-run vs. resume; orchestrate phases; stop at approval and class selection.  
**Outputs:** Coordinates all other skills; updates `state.json` and `backlog.md`.  
**Safety:** Declares permissions (write/exec denies) and asks for approval-sensitive commands.

### Step Skills

Each step skill is an instruction file the AI agent reads and follows. The supervisor tells the agent when to open each one.

| Skill | Phase | Input | Output | Decision Points |
|-------|-------|-------|--------|-----------------|
| `ut-analyze` | 1 | Repo `src/`, `pom.xml` | `01-repo-analysis.md` + baseline | Environment blockers? |
| `ut-plan` | 2 | Class source, existing tests | `plans/FQCN.plan.md`, backlog row | >40 cases? Split? |
| `ut-generate` | 3 | Approved plan | New test file(s) | Plan not approved? |
| `ut-verify` | 4 | Test file, source | Guard result, Maven compile | Guard or compile fail? |
| `test-failure-triage` | 5 | Failing test output | Failure label + action | Fix? Disable? Block? |
| `ut-report` | 6 | Measured coverage | Class report + backlog update | On target? Add cases? |
| `ut-status` | ∞ | state.json, backlog.md | Workflow status text | (read-only) |

### Guard Utility (`UtGuard.java`)

Executable Java (JDK 11+). Deterministic operations independent of AI.

**Commands:**

```bash
java UtGuard.java snapshot --run RUN_DIR                           # Phase 0: snapshot pom.xml and src/
java UtGuard.java approve --run RUN_DIR --plan FILE --reply "text" # Phase 2: validate plan, record approval
java UtGuard.java check --run RUN_DIR --plan FILE                  # Phase 4: verify plan unchanged, no banned APIs, assertions present
java UtGuard.java results --run RUN_DIR --class TestFQCN --label first|second  # Phase 5: compare test outcomes
java UtGuard.java coverage [--class NAME]                          # Phase 6: measure and report JaCoCo coverage
java UtGuard.java log --event E [--run RUN_DIR]                    # Any phase: audit event
```

**Exit codes:** 0 (success), 1 (validation failed), 2 (usage/environment error), 3 (stale data).

### Rules

**`ut-guardrails.md`** – Loaded first; lists hard limits (don't modify `src/main`, don't weaken assertions, etc.).  
**`ut-test-conventions.md`** – Testing stack and naming rules (JUnit 5, AssertJ, Mockito allowed; Spring context not allowed).

### Templates

Pre-filled skeleton files the AI fills in:

- **`backlog.md`** – Class table with statuses and coverage numbers.
- **`test-plan.md`** – Test cases, collaborators, testability notes.
- **`class-report.md`** – Outcome (done / done-below-target / blocked), coverage, findings.
- **`run-summary.md`** – Whole-run summary, suspected bugs, recommendations.

## Permissions (Devin CLI)

The supervisor's front-matter declares:

```yaml
permissions:
  deny:
    - Write(src/main/**)
    - Write(pom.xml)
    - Write(Jenkinsfile)
    - Exec(git push|git commit)
    - Exec(mvn sonar|mvn deploy|mvn install)
  ask:
    - Exec(java .../ut-guard/UtGuard.java approve)
    - Write(config.json)
  allow:
    - Write(src/test/java/**)
    - Write(unit-test-agent/**)
```

These are enforced by the Devin permission system. The guard's deterministic checks catch violations after the fact.

## Workflow States

| Phase | state.json `phase` | User Sees | Supervisor Next |
|-------|-------------------|-----------|-----------------|
| Phase 0 | `NEW_RUN` | Snapshot message | Phase 1 |
| Phase 1 | `ANALYZE` | Analysis complete, baseline numbers | Phase 2 |
| Phase 2 | `PICK_CLASS` | Class list, asks "Which class?" | **STOP** – waits for user |
| Phase 3 | `PLAN_DRAFT` | Plan summary, asks "Approve?" | **STOP** – waits for user |
| Phase 4 | `PLAN_APPROVED` | Generating tests… | Phase 5 |
| Phase 5 | `VERIFY` | Running tests, checking for failures | Phase 6 (or loop on triage) |
| Phase 6 | `MEASURE` | Measuring coverage | Phase 7 |
| Phase 7 | `REPORT` | Report complete, asks "Next class or stop?" | **STOP** – waits for user |
| `DONE` | (final) | Run complete, audit trail saved | (workflow ended) |

## Error Handling and Retry Logic

The `test-failure-triage` skill classifies failures:

| Failure Type | Label | Action | Retry Counted? |
|--------------|-------|--------|----------------|
| Compile error | `TEST_BUG` | Fix test code | Yes (up to max=3) |
| Mock setup issue | `TEST_BUG` | Fix test setup | Yes |
| Assertion fails + written spec | `SUSPECTED_PROD_BUG` | Add `@Disabled("Suspected bug: ...")`, log ticket | No (doesn't count) |
| Assertion fails + no spec | `PLAN_ERROR` | Revise expectation, list under "corrected" | Yes |
| Test timeout | `TEST_BUG` | Stub dependency or fix loop | Yes |
| Flaky (order-dependent) | `TEST_BUG` | Remove shared state | Yes |
| Guard failed | `TEST_BUG` | Fix new file, rerun guard | Yes |
| Environment | `ENVIRONMENT` | Stop, show user | No |

**Limit:** max 3 counted fix attempts per class. If still failing: class → `blocked`, coverage not measured, ask user to keep or remove test file.

## Coverage and Targets

After tests pass and are stable:

```bash
java UtGuard.java coverage --class StructToCsvFn
```

Reports:

```
LINE: 74.3% (26/35), target 80%
BRANCH: 64.3% (18/28), target 75%
RESULT: BELOW_TARGET
```

**Decision:**
- Meets target → `done`
- Below target, valuable gaps → propose extra test cases (once per round)
  - Accept → revised plan + new approval + repeat
  - Decline → `done-below-target` (with reasons for each uncovered line)
- Unreachable lines → `done-below-target` (noted as such)

## Resume and Pauses

The workflow is designed to pause at every human decision point:

- **After Phase 2:** Awaiting class selection
- **After Phase 3:** Awaiting plan approval
- **After Phase 7:** Asking for next class or stop

Resuming with `/unit-test-agent resume`:

1. Supervisor reads `state.json` and determines current phase.
2. If mid-class (phase `VERIFY`, `MEASURE`, `REPORT`): continue from that point.
3. If awaiting decision (phase `PICK_CLASS` or `PLAN_DRAFT`): re-show options.
4. If done: offer to start a new run or review the last run.

All work is saved in `unit-test-agent/reports/<timestamp>/` per run. No data is lost.

## Audit Trail

Every guard command appends a line to `unit-test-agent/audit.log`:

```
2026-11-19T09:42:10+05:30	GUARD_SNAPSHOT	ok	9 files hashed
2026-11-19T09:45:30+05:30	PLAN_APPROVED	ok	StructToCsvFn 10 cases
2026-11-19T09:50:15+05:30	GUARD_CHECK	ok	no issues
2026-11-19T09:52:00+05:30	TESTS_WRITTEN	ok	StructToCsvFnScalarTest.java 9 methods
2026-11-19T10:05:30+05:30	FIX_ATTEMPT	1 of 3	TC-006 expectation corrected
2026-11-19T10:15:00+05:30	COVERAGE_MEASURED	ok	74.3% line, 64.3% branch
2026-11-19T10:16:00+05:30	CLASS_OUTCOME	done-below-target	Lines 123-124 unreachable
```

Only the guard writes to `audit.log`. It is append-only, never edited.

## Extending the Framework

### For a Different Testing Domain (not Beam)

1. Copy the framework.
2. Add testing patterns to your project documentation (Spring Boot, Hibernate, gRPC, etc.).
3. Update `rules/ut-test-conventions.md` with your testing stack and conventions.
4. If your language/framework has different conventions, adjust templates and guard checks.

### Adding Extra Validation

Edit `UtGuard.java`:

- Add a command like `static analysis --run RUN_DIR` for security patterns.
- Or add a check in the existing `check` command.
- Recompile: `javac UtGuard.java`.
- Update `ut-guard/SKILL.md` to document the new command.

### Customizing Approval Flow

The supervisor skill determines STOP points. Edit `supervisor/SKILL.md`:

- Add extra approval questions if needed.
- Remove a phase if not applicable to your domain.
- Adjust the state machine in the "Phases" section.

## Testing the Framework

Before using in production:

1. **Dry run on a sample project:** Use a small repo with 1–2 classes.
2. **Validate guard utility:** Run `UtGuard.java` manually with sample JSON/XML files.
3. **Review permission config:** Ensure deny/ask rules match your safety policy.
4. **Test resume:** Start a run, pause, resume, verify state is consistent.

See `examples/single-class-flow.md` for a walkthrough of one complete class → tests → coverage cycle.

## Troubleshooting

### "Phase is not established"

The state machine has gaps. Check `supervisor/SKILL.md` for missing phase transitions or `state.json` for an unexpected phase value.

### "Guard failed: plan not approved"

The plan file exists but no approval record was saved. Likely the user clicked "approve" but `UtGuard approve` was not run. Have the user re-approve explicitly.

### "Coverage report is STALE"

A test run was modified after `jacoco:report` ran. This is usually a Maven ordering issue. Delete `target/site/jacoco/` and re-run:

```bash
mvn jacoco:report
```

### "Resume does not work"

Check `state.json` exists and is valid JSON. If corrupted, restore from the last successful `01-repo-analysis.md` report or start a new run.

## References

- **`ARCHITECTURE.md`** – Design decisions and rationales.
- **`examples/single-class-flow.md`** – Walkthrough of one complete cycle.
- **`supervisor/SKILL.md`** – Full orchestration logic.
- **Original reference:** `../UNIT_TEST_AGENT_WALKTHROUGH.md` (the rops-common-schema-extract-spanner implementation).

## License and Attribution

This framework is based on the architecture described in `../UNIT_TEST_AGENT_WALKTHROUGH.md` for the `rops-common-schema-extract-spanner` project. Use freely in your projects; credit the original design.
