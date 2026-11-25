---
trigger: manual
---

# Phase 3: Test Code Generation (`ut-generate`)

**Triggered by:** Supervisor (Phase 6) after `APPROVE: RECORDED`.  
**Responsibility:** Write test code for all cases in the approved plan.  
**Output:** New test file(s) under `src/test/java/`; `04-generated-<Class>.md` summary.  
**Safety:** Only create files named in plan; never edit existing files.

---

## Section 1: Pre-Generation Checklist

Before writing a single line of test code:

1. **Verify approval was recorded:**
   ```
   Approval file should exist: unit-test-agent/reports/$RUN_DIR/approval-*.txt
   ```
   If not, ask the supervisor to re-approve.

2. **Read the approved plan:**
   ```
   unit-test-agent/plans/FQCN.plan.md
   ```
   Verify `status: draft` or approved marker.

3. **Identify test file location:**
   ```
   From plan: test_file: src/test/java/com/example/StructToCsvFnTest.java
   ```
   This file must NOT already exist (guard refuses existing files).

4. **Identify test methods to write:**
   ```
   From plan test case table: TC-001, TC-002, ... EC-001, ... ES-001, ...
   Each ID → one `@Test` method (or `@ParameterizedTest` if grouped)
   ```

5. **Review testing conventions:**
   ```
   Read: .devin/rules/ut-test-conventions.md
   - Testing stack: JUnit 5, AssertJ, Mockito (from spring-boot-starter-test)
   - Naming: <Class>Test.java, method_condition_result
   - Every test must have an assertion
   - Case IDs in comments: // TC-001
   ```

---

## Section 2: Write the Test File

### Step 2a: File structure and imports

```java
package com.example.extract;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.spanner.Struct;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class StructToCsvFnTest {
  // Test methods here
}
```

**Rules:**
- Package: same as production class
- Class name: from plan's `test_file:` line
- Imports: only from allowed stack (JUnit 5, AssertJ, Mockito, domain libraries)
- **Never:** Spring injection, PowerMock, Testcontainers, JUnit 4

### Step 2b: Helper methods and fixtures

If multiple tests share setup, use a helper method (not `@BeforeEach` unless stateless):

```java
class StructToCsvFnTest {
  
  private static Struct buildRow(String id, long count) {
    return Struct.newBuilder()
        .set("ID").to(id)
        .set("COUNT").to(count)
        .build();
  }
  
  private static PCollection<String> runInPipeline(Struct row) {
    Pipeline p = Pipeline.create();
    return p.apply(Create.of(row))
        .apply(ParDo.of(new StructToCsvFn()));
  }
}
```

**Rules:**
- Use `@BeforeEach` only for stateless setup
- Use `@TempDir` for temporary files
- Use synthetic data (no real patient IDs, credentials, etc.)
- Avoid shared mutable state (each test independent)

### Step 2c: Write test methods

For each test case ID in the plan:

```java
// TC-001  (comment with ID)
@Test
void processElement_stringAndInt_writesQuotedCsv() {
  // Arrange (given)
  Struct row = buildRow("p-1", 42L);
  
  // Act (when)
  PCollection<String> result = runInPipeline(row);
  
  // Assert (then)
  PAssert.that(result).containsInAnyOrder("\"p-1\",\"42\"");
  
  p.run().waitUntilFinish();
}

// EC-001  (edge case)
@Test
void processElement_nullColumn_writesEmptyField() {
  Struct row = Struct.newBuilder()
      .set("ID").to("p-1")
      .set("NOTE").to((String) null)
      .build();
  
  PCollection<String> result = runInPipeline(row);
  
  PAssert.that(result).containsInAnyOrder("\"p-1\",\"\"");
  
  p.run().waitUntilFinish();
}

// ES-001  (error case)
@Test
void extractValue_unsupportedType_logsAndReturnsEmpty() {
  // Setup: a column with unsupported type
  // Act: call extractValue
  // Assert: returns empty string, doesn't throw
  assertThat(result).isEmpty();
}
```

**Rules for each test method:**
1. **ID in comment:** `// TC-001` on the line before `@Test`
2. **Naming:** `methodName_condition_expectedResult`
3. **Annotation:** `@Test` for single test, `@ParameterizedTest` for data-driven
4. **Structure:** Arrange → Act → Assert (clear separation)
5. **Assertion:** Every test must `assert*()` or `PAssert` or `verify()`
6. **No side effects:** Each test independent, no shared state

### Step 2d: Parameterized tests

If multiple similar cases, use `@ParameterizedTest`:

```java
// TC-002, TC-003, TC-004  (all BOOL/INT/STRING types)
@ParameterizedTest
@ValueSource(strings = { "true", "42", "hello" })
void extractValue_scalarTypes_returnStringValue(String expected) {
  // Setup: create appropriate column type
  // Act: call extractValue
  // Assert: equals expected
  assertThat(actual).isEqualTo(expected);
}
```

**Rule:** List all case IDs in a comment above `@ParameterizedTest` (not `@Test`).

### Step 2e: Skipped/disabled tests

If a test case in the plan **cannot be implemented** (truly unreachable, e.g., exception path that never fires):

```java
// TC-999 not implemented: extractArray() exception unreachable
// (ObjectMapper cannot throw on a List<String> in practice)
```

Do **not** create a `@Test` method. Add a comment explaining why. The plan notes this.

**Important:** Never use `@Disabled` to comment out a failing test without reason. Only use `@Disabled` for **suspected bugs**:

```java
@Disabled("Suspected bug: Javadoc says quote only when necessary, code quotes all")
@Test
void processElement_csvQuoting_followsRfc4180() { ... }
```

Reason **must** start with `Suspected bug:` (guard checks this).

---

## Section 3: Verify and Compile

### Step 3a: Check the test file

Verify before writing:

- [ ] All case IDs from plan are present (TC-001 ... TC-N)
- [ ] Each `@Test` has an ID comment: `// TC-001`
- [ ] Every test method has an assertion
- [ ] No `@Disabled` without `Suspected bug:` reason
- [ ] 2-space indent, no tabs
- [ ] Class name matches plan's `test_file:`
- [ ] Package matches production class
- [ ] No imports from banned list (Spring, PowerMock, Testcontainers)

### Step 3b: Compile

```bash
mvn -B -ntp test -Dmaven.test.failure.ignore=true
```

**Expected:** `BUILD SUCCESS` (even if tests fail initially; compilation matters).

**If COMPILATION ERROR:** Goto Phase 5 (Triage, case 1). Fix and rerun.

### Step 3c: Write generation summary

Create `$RUN_DIR/04-generated-<Class>.md`:

```markdown
# Generated Tests for StructToCsvFn

Generated: 2026-11-26, 10:15 UTC
Run: unit-test-agent/reports/20261126-100000/

## Files created
- src/test/java/com/example/extract/StructToCsvFnScalarTest.java

## Methods created
| ID | Method | Status |
|----|--------|--------|
| TC-001 | processElement_stringAndInt_writesQuotedCsv | implemented |
| TC-002 | extractValue_boolTrue_returnsTrue | implemented |
| TC-003 | extractValue_intValue_returnsStringInt | implemented |
| EC-001 | processElement_nullColumn_writesEmptyField | implemented |
| EC-002 | processElement_stringWithNewline_replacesWithSpace | implemented |
| ES-001 | extractValue_unsupportedType_logsAndReturnsEmpty | implemented |
| TC-999 | extractArray exception path | skipped (unreachable in practice) |

## Compilation
Maven output: BUILD SUCCESS
Tests found: 6 methods

## Conventions check (before guard)
- Naming: ✓ method_condition_result
- IDs present: ✓ all case IDs from plan
- Assertions: ✓ every test asserts
- Imports: ✓ JUnit 5, AssertJ, Mockito only
- Banned APIs: ✓ none detected
```

---

## Section 4: Domain-Specific Patterns

### For Beam/Spanner tests:

Use real `Struct` and `Pipeline` on direct runner:

```java
@Test
void processElement_realBeamPipeline() {
  Pipeline p = Pipeline.create();
  
  Struct row = Struct.newBuilder()
      .set("col1").to("value")
      .build();
  
  PCollection<String> result = p
      .apply(Create.of(row))
      .apply(ParDo.of(new MyFn()));
  
  PAssert.that(result).containsInAnyOrder("expected");
  
  p.run().waitUntilFinish();
}
```

### For Spring/REST tests (if applicable):

Mock the HTTP client:

```java
@Test
void callApi_validRequest_returns200() {
  HttpClient mock = Mockito.mock(HttpClient.class);
  when(mock.post(any(), any())).thenReturn(response);
  
  MyClass obj = new MyClass(mock);
  Result result = obj.callApi();
  
  assertThat(result).isEqualTo(expected);
  verify(mock).post(url, payload);
}
```

### For database tests (if applicable):

Use `@TempDir` or mock the client:

```java
@Test
void loadData_invalidPath_throwsFileNotFoundException(@TempDir File tmpDir) {
  File missing = new File(tmpDir, "nonexistent.csv");
  
  assertThrows(FileNotFoundException.class, () -> {
    loader.load(missing);
  });
}
```

---

## Section 5: Finish

### Step 5a: Verify all files

- [ ] Test file exists under `src/test/java/`
- [ ] File compiles (Maven BUILD SUCCESS)
- [ ] All case IDs from plan are implemented
- [ ] Each method has an assertion
- [ ] No existing test files were edited
- [ ] No `src/main/` files were edited
- [ ] Generation summary created

### Step 5b: Return to Supervisor

Log to supervisor:

```
Tests generated for StructToCsvFn.

File: src/test/java/com/example/extract/StructToCsvFnScalarTest.java
Methods: 6 implemented, 0 skipped
Compilation: OK
Expected coverage: 85% line, 78% branch

Proceeding to verification.
```

Go to **Supervisor Phase 7 (Verify)**.

---

## Troubleshooting

| Issue | Action |
|-------|--------|
| Test file already exists | Guard refuses; use different name (plan must be re-approved) |
| Compilation error | Fix test code; rerun `mvn test` |
| Import not found | Check testing stack (JUnit 5, AssertJ, Mockito from spring-boot-starter-test) |
| Assertion syntax wrong | Review AssertJ Javadoc; use `assertThat(...).isEqualTo(...)` |
| Test method name too long | Shorten; must be under 100 chars |
| Case ID missing or wrong | Add `// TC-001` comment; must match plan exactly |
| Parameterized test list all IDs | Add comment: `// TC-001, TC-002, TC-003` |

---

**You are done with Phase 3 (Generation).**

Compilation successful. Return to supervisor for Phase 7 (Verify).
