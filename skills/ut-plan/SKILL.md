---
trigger: manual
---

# Phase 2: Test Planning (`ut-plan`)

**Triggered by:** Supervisor (Phase 3) after class selection.  
**Responsibility:** Analyze the selected class and create a test plan with explicit test cases.  
**Output:** `plans/FQCN.plan.md` with status `draft`.  
**Gate:** Plan approval before generation (Supervisor Phase 4).

---

## Section 1: Class Analysis

You are now planning tests for a single class. Your goal is to understand what's testable and define test cases before writing code.

### Step 1a: Understand the class

Read the entire production class:

1. **Class type:** interface, abstract, concrete, enum?
   - Interfaces → usually not testable as themselves
   - Abstract → test via concrete subclasses
   - Concrete → directly testable
   - Enum → usually testable (constants, methods)

2. **Public methods:** What can a user of this class call?
3. **Package-private methods:** What can tests in the same package call?
4. **Private helpers:** Only testable indirectly (through public/package methods).
5. **Static methods:** Independently testable.
6. **Constructors:** What do they require (required options)? What do they set up?

### Step 1b: Identify collaborators

For each method, what does it depend on?

- **Real objects:** Spanner `Struct`, Beam `Pipeline`, etc. → test with real
- **External clients:** Database, HTTP API, file system → mock or stub
- **Static calls:** Domain-specific helpers, `Math.`, `String.format()` → usually OK
- **Configuration/options:** Passed in? Set at construction? → understand defaults

### Step 1c: Check existing tests

If the class already has tests:

1. What do they cover (positive cases, edge cases, error cases)?
2. What do they mock/stub?
3. What's the naming pattern?
4. What assertion library do they use?

**Gaps:** What cases are **not** covered? Those are candidates for new tests.

### Step 1d: Read Javadoc and comments

- Javadoc on methods = **spec**. Expected values come from here first.
- Comments in code = implementation notes (data, not spec).
- README or design docs = clarify intent.

### Step 1e: Identify testability issues

Can you unit-test this class independently? Or does it:

- Build a real Spanner client → hard to test without infrastructure
- Call `System.exit()` → not testable in JUnit
- Use real file I/O → needs `@TempDir` or mocking
- Depend on static mutable state → flaky, test-order dependent
- Have private methods only → can only test via public entry points

**Record these** in the plan's "Testability notes" section.

---

## Section 2: Test Case Design

### Step 2a: Identify paths through the code

For each **public or package-private method**, trace all execution paths:

```
Method: processElement(Struct row)
  if (row == null) → early return or throw?
  for each column:
    if (column.isNull()) → handle null
    switch (column.type()):
      case STRING → extractString()
      case INT → extractInt()
      case ... → other types
      default → warn + empty string
  if (exception) → catch or throw?
  return result or side effect?
```

For each path, ask:

- **Positive test:** Happy path, expected inputs, typical output?
- **Edge case:** Boundary (empty list, null, 0, max value), unusual but valid input?
- **Error case:** Invalid input, exception, unsupported type?

### Step 2b: Create test case IDs

Assign an ID to each test case you plan:

- **TC-001, TC-002, ...** – Positive/typical test cases
- **EC-001, EC-002, ...** – Edge cases (boundary, unusual but valid)
- **ES-001, ES-002, ...** – Error/exception cases

Limit to **max 40 cases per plan** (from config). If more:

```
"This class has >40 valuable test cases. 

Propose 1: Test methods parseTableNames() and parseNormalizedColumnSet() (parsing) → ~15 cases
Propose 2: Test method parseEntityColumnMap() (entity mapping) → ~20 cases
Propose 3: Test methods constructor() and run() (high-level) → ~10 cases

Which should we do in this round?"
```

Wait for user answer, then proceed with the chosen subset.

### Step 2c: Fill the test case table

For each test case ID, record:

| Column | Example | Purpose |
|--------|---------|---------|
| **ID** | TC-001 | Ties test case to test method |
| **Type** | positive, edge, error | Classifier |
| **Method** | parseTableNames | Which production method |
| **Given** | `"A,,B, ,A"` (input) | Setup |
| **When** | parse | Action |
| **Then** | equals `List.of("A","B")` | Expected output (exact assertion) |
| **Expected from** | Javadoc ("comma-separated"), code | Where value comes from |

**Key rule:** "Expected from" must cite a **written source** (Javadoc, existing test, README). If no written spec exists, say so and note "describes what the code does today" (you're testing current behavior, not an ideal spec).

### Step 2d: If spec and code disagree

If Javadoc says one thing and code does another:

- List the disagreement under "Spec and code disagree"
- Keep the **spec's expected value** in the test case
- Plan will be: test asserts the spec; if code fails the test, that's a suspected bug (Phase 5 triage)
- Example:
  ```
  Javadoc: "Returns the absolute value of x"
  Code: returns x (sometimes negative)
  Test expects: abs(-5) == 5
  Code fails → suspected bug
  ```

---

## Section 3: Plan Document Template

Write `unit-test-agent/plans/FQCN.plan.md`:

```markdown
# Test plan for ClassName

class: com.example.MyClass
class_type: concrete|beam-dofn|beam-options|...
test_file: src/test/java/com/example/MyClassTest.java
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
| parseTableNames | empty tokens, duplicates, case kept, only commas | TC-001, EC-001, EC-002 |
| extractValue | each type (INT, STRING, ...) | TC-002, TC-003, ... |
| ...

## Test cases
| ID | Type | Method | Given | When | Then (exact assertions) | Expected value comes from |
|----|------|--------|-------|------|--------------------------|---------------------------|
| TC-001 | positive | parseTableNames | `"A,,B, ,A"` | parse | equals `List.of("A","B")` | Javadoc ("comma-separated") |
| EC-001 | edge | parseTableNames | `"a,A"` | parse | equals `List.of("a","A")` | Javadoc ("case-sensitive") |
| ES-001 | error | constructor | no --requiredOption | construct | throws IllegalArgumentException | @Validation.Required |
| ...

## Collaborators
| Dependency | Real or mock | Why |
|------------|--------------|-----|
| Spanner Struct | Real | Built via Struct.newBuilder(), no external call |
| Beam Pipeline | Real | Direct runner, local, deterministic |
| External HTTP API | Mock | Would need network, too slow |

## Spec and code disagree
(List any discrepancies. If none, write "none noticed".)

## Existing tests (not duplicated, never edited)
(List test methods already in <ClassName>Test.java. These will not be touched.)

## Testability notes
- Lines unreachable from unit tests: ... (why? e.g., "requires real Spanner")
- Private methods only reachable via: ... (e.g., "via public method X")
- Known issues: ... (e.g., "CSVWriter exception path unreachable in practice")

## Expected coverage (estimate only)
Line: XX% (target YY%)  
Branch: XX% (target YY%)  
May end as `done-below-target` if gaps are unreachable.

## Change log
| Date | Change |
|------|--------|
| 2026-11-26 | Plan created |
```

---

## Section 4: Plan Summary

**Before asking for approval**, print a **summary** of the plan (not the full document):

```
Class: StructToCsvFn (beam-dofn)
Test file: src/test/java/com/example/extract/StructToCsvFnScalarTest.java
Test cases: 10 total
  - 5 positive (TC-001 to TC-005)
  - 3 edge (EC-001 to EC-003)
  - 2 error (ES-001 to ES-002)

Mocked: none
Real collaborators: Struct (Spanner), Pipeline (Beam)

Existing tests cover: Arrays only (from StructToCsvFnTest.java)
New plan covers: Scalar types, null, unsupported types

Spec vs. code disagreements: none noticed

Testability notes:
  - extractArray() only reachable via processElement() (private helper)
  - CSVWriter exception path unreachable in practice

Estimated coverage:
  - Line: 85% (target 80%) ✓
  - Branch: 78% (target 75%) ✓

Ready to approve this plan?
```

---

## Section 5: Handle Special Cases

### Case: Class has no testable logic

If the class is an interface, pure utility with only static calls, or generated code:

```
This class has no executable/testable logic:
- It is an interface (ClassName)
- Methods are all getters/setters (no business logic)
- Code is generated (see GeneratedBy annotation)

Recommend testing a different class. Would you like to pick another?
```

Skip this class (back to Phase 2 class selection).

### Case: Existing plan from previous round

If `plans/FQCN.plan.md` already exists:

```
Found existing plan for StructToCsvFn (from previous run).

Status: draft
Created: 2026-11-19

Would you like to:
1. Continue from this plan (add change log line, re-approve)
2. Create a new plan (replace it)
3. Skip this class
```

### Case: >40 test cases

If you identify >40 valuable cases:

```
This class deserves many test cases. I found ~60.

Propose a split:

Round 1 (this round): parseTableNames(), parseNormalizedColumnSet()
  - Parsing methods, ~15 cases
  - 2–3 hours to generate, verify, measure

Round 2 (next round): parseEntityColumnMap()
  - Complex mapping logic, ~20 cases
  - 2–3 hours

Round 3 (later): constructor(), run()
  - High-level orchestration, ~10 cases
  - 1–2 hours

Which should I plan for now?
```

Wait for user to pick one subset. Plan only that subset.

---

## Section 6: Finish

### Step 6a: Save the plan

Write to `unit-test-agent/plans/FQCN.plan.md` and set `status: draft`.

Verify:
- All case IDs are unique (TC-001, EC-001, ES-001, not duplicates)
- Each case has: ID, type, method, given, when, then, expected from
- Expected values are grounded (Javadoc, code, existing test, README)
- Testability notes are honest (what's unreachable and why)
- Estimated coverage is reasonable

### Step 6b: Update backlog

Set the class status to `draft-plan` in `unit-test-agent/backlog.md`:

```
| com.example.StructToCsvFn | draft-plan | 30.0 | 21.4 | 35 | [plan](../plans/StructToCsvFn.plan.md) | |
```

### Step 6c: Print summary and ask for approval

Show the summary (Section 4).

Go to **Supervisor Phase 4 (Approval Question)**.

---

## Troubleshooting

| Issue | Action |
|-------|--------|
| "Class has no executable code" | Skip it; back to class selection |
| ">40 test cases found" | Propose split; ask user which methods this round |
| "Existing tests cover the class fully" | Note gaps anyway (new patterns, error cases) and propose extra tests |
| "No written spec (Javadoc), only code" | Say so in plan; test current behavior as "described what code does today" |
| "Code looks buggy" | List under "Spec and code disagree"; test against spec; mark as suspected bug if it fails |
| "Method has private helper only" | Testable indirectly via public method; note in "Testability notes" |

---

**You are done with Phase 2 (Planning).** 

Print the plan summary and ask for approval. Await user answer (Phase 4, STOP #2).
