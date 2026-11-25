---
trigger: manual
reference: true
---

# Status Reporting (`ut-status`)

**Purpose:** Read-only view of current workflow state without triggering new work.

**When to use:** User runs `/ut-status` to check progress mid-workflow.

---

## Responsibility

Display current state from `state.json` without modifying it.

**Never:** Edit files, run Maven, run guard, or change state.

**Always:** Read-only operations only.

---

## Status Report Contents

### 1. Current Run Info

```
╔══════════════════════════════════════╗
║ Unit Test Agent - Status Report      ║
╚══════════════════════════════════════╝

Run ID: 20261126-100000
Started: 2026-11-26 10:00 UTC
Elapsed: 45 minutes
Phase: 7 (Verify) → 8 (Coverage measurement)
```

### 2. Current Class

```
Current Class: com.example.extract.StructToCsvFn
  Status: draft-plan → generating
  Plan: unit-test-agent/plans/StructToCsvFn.plan.md
  Test file: src/test/java/com/example/extract/StructToCsvFnScalarTest.java
  Test cases: 6 (5 implemented, 1 skipped)
```

### 3. Run History

```
Classes processed this run:
  1. ✓ TableNameExtractor (done: 92% line, 88% branch)
  2. → StructToCsvFn (in progress)
  3. - ParsePlanner (queued)
  4. - ValidationRules (queued)
```

### 4. Baseline & Progress

```
Baseline Coverage (from 02-baseline-coverage.md):
  Line: 50%, Branch: 40%

Current Coverage (from 07-coverage-*.md):
  Run 1 (TableNameExtractor): 92% line, 88% branch (+42% line improvement)
  Run 2 (StructToCsvFn):       85% line, 77% branch (+35% line improvement)
  
Total change: +77 coverage lines vs baseline
```

### 5. Backlog Summary

```
Backlog Status (from backlog.md):
  Total classes: 23
  Done:                  2
  Draft-plan/Generating: 1
  Queued:               20
  
  Coverage targets:
    Line: 80% (project-wide)
    Branch: 75% (project-wide)
  
  Suspect bugs filed: 1
    - GitHub example/example-common#1234 (StructToCsvFn)
```

### 6. Approval Gates Completed

```
Approvals recorded:
  Phase 2: Selected TableNameExtractor → ✓ approved
  Phase 4: Approved plan for TableNameExtractor → ✓ recorded
  Phase 9: Continue to StructToCsvFn? → ✓ user chose yes
```

### 7. Issues and Blockers

```
Issues:
  None currently.

Blockers:
  None currently.
  
Warnings:
  - StructToCsvFn has 1 test disabled (suspected bug filed)
  - Suggested next: Review GitHub issue example/example-common#1234
```

### 8. Quick Commands

```
Resume workflow:
  /unit-test-agent resume

Check details of run 2:
  cat unit-test-agent/reports/20261126-100000/07-coverage-StructToCsvFn.md

Review plan for next class:
  cat unit-test-agent/backlog.md | grep ParsePlanner

Stop workflow and wrap up:
  (Supervisor will handle when user says stop at Phase 9)
```

---

## Implementation

Print status from state.json fields:

```
{
  "currentRun": "20261126-100000",
  "phase": "coverage",
  "currentClass": "com.example.extract.StructToCsvFn",
  "runs": [
    {
      "run": "20261126-100000",
      "class": "com.example.extract.TableNameExtractor",
      "status": "done",
      "lineCoverage": 92.0,
      "branchCoverage": 88.0,
      "testCount": 8,
      "approvalTime": "2026-11-26T10:05:00Z"
    },
    {
      "run": "20261126-100000",
      "class": "com.example.extract.StructToCsvFn",
      "status": "coverage",
      "lineCoverage": 85.0,
      "branchCoverage": 77.0,
      "testCount": 6,
      "approvalTime": "2026-11-26T10:15:00Z"
    }
  ],
  "lastBaseline": {
    "line": 50.0,
    "branch": 40.0,
    "date": "2026-11-25T15:30:00Z",
    "run": "20261125-baseline"
  }
}
```

**Display fields:**
- currentRun, phase, currentClass
- For each run: class, status, coverage, test count, approval time
- lastBaseline for reference
- Calculate elapsed time (now - runs[0].approvalTime)
- Count done/in-progress/queued from backlog.md

---

## Read-Only Scope

✓ **Allowed:**
- Read state.json
- Read backlog.md
- Read any *.md files in reports/
- Read any *.md files in plans/
- Display without modification
- Calculate elapsed time, counts, percentages

✗ **Forbidden:**
- Edit state.json
- Edit backlog.md
- Edit any plans/ files
- Run Maven
- Run guard commands
- Create new files
- Delete files

---

## Trigger Points

**When user runs:**
```
/ut-status
```

Supervisor calls ut-status skill, which:
1. Reads state.json
2. Reads backlog.md
3. Reads latest *.md files from reports/
4. Formats and prints status
5. Returns to waiting (no phase change)

---

**Reference only. No execution required.**
