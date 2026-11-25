# Unit-Test Conventions & Quality Gates

**Applies to:** All new test files created by the agent.

**Triggered:** This rule is loaded before every run and governs test generation.

**Quality Standard:** Org SonarQube instance - **Sonar way (Java)** profile + org baseline quality gates.

---

## SonarQube Quality Rules (Mandatory Compliance)

The following SonarQube rules are **enforced** by this framework. All generated tests must pass these checks:

| Rule ID | Rule Name | Requirement | Guard Check |
|---------|-----------|-------------|------------|
| **java:S2699** | Mandatory Assertions | Every `@Test` method must include ≥1 explicit assertion | ✓ Required |
| **java:S5785** | Appropriate Assertion Usage | Use modern fluent assertions (AssertJ) over generic boolean checks | ✓ Preferred |
| **java:S3577** | Test Class & Method Naming | Tests must follow `*Test` class naming and `method_condition_result` method naming | ✓ Enforced |
| **java:S5778** | Exception Testing | Use `assertThrows()` instead of try-catch blocks for exception tests | ✓ Enforced |
| **java:S6073** | Mocking Best Practices | No PowerMock, mockStatic(), or mockConstruction() | ✓ Banned imports |
| **java:S6068** | Mocking Best Practices | Mock interfaces; avoid @Spy on production classes | ✓ Recommended |
| **java:S2068** | No Hardcoded Secrets | Never include real credentials, API keys, or PHI/PII in test data | ✓ Regex scan |

**Coverage Gate:** Minimum **80% line coverage** and **75% branch coverage** on new test code (org baseline).

---

## Testing Stack (Allowed)

### Required
- **JUnit 5 (Jupiter)** – `org.junit.jupiter.api.*`
  - `@Test`, `@ParameterizedTest`, `@BeforeEach`, `@AfterEach`, `@Disabled`, `@DisplayName`
  - **Not** JUnit 4 (old API)

- **Assertions** – **AssertJ** (preferred) or **JUnit 5 built-in assertions**
  - `org.assertj.core.api.Assertions.assertThat(...)`  ← **Preferred for readability**
  - `org.junit.jupiter.api.Assertions.assertThat(...)`
  - `org.junit.jupiter.api.Assertions.assertThrows(...)`

- **Mocking** – **Mockito** (from `spring-boot-starter-test`)
  - `org.mockito.Mockito.*`
  - `@Mock`, `when(...).thenReturn(...)`, `verify(...)`
  - **Not** PowerMock, Testcontainers, MockMvc (unless domain-specific)

### Optional
- Domain-specific testing libraries (Beam `PAssert`, Spanner testing utils, etc.)
- Parameterized tests for data-driven cases
- `@TempDir` for temporary files (JUnit 5 built-in)

### Forbidden
- **Spring context injection** – `@SpringBootTest`, `@DataJpaTest`, `@WebMvcTest` (too heavy)
- **Spring test annotations** – `@Autowired`, `@Value` on test fields
- **TestContainers** or **Docker** (too slow for unit tests)
- **Testcontainers/Docker Compose** (integration test only)
- **Real network/file I/O** – No real Spanner, GCS, HTTP calls
- **Real time/threading** – No `Thread.sleep()`, real clock, or timing-dependent asserts
- **JUnit 4** – `org.junit.Test`, `@Before`, `@After`
- **Static mocking** – `mockStatic()`, `mockConstruction()` (PowerMock) ← **SonarQube java:S6073**
- **Private reflection** – `setAccessible(true)`, `getDeclaredField()`, `getDeclaredMethod()`
- **Test ordering** – `@TestMethodOrder`, `@Order` (tests must be independent)
- **Hardcoded secrets** – Real passwords, API keys, or PHI/PII ← **SonarQube java:S2068**

---

## Naming Conventions

### Test Class
- Name: `<ClassName>Test` or `<ClassName>Tests` ← **SonarQube java:S3577**
  - Examples: `StructToCsvFnTest.java`, `PipelineTests.java`
- Package: Same as production class
  - Production: `com.example.extract.StructToCsvFn`
  - Test: `com.example.extract.StructToCsvFnTest`
- If production class already has a test file, new tests go in a separate file with a suffix:
  - Existing: `StructToCsvFnTest.java` (arrays only)
  - New: `StructToCsvFnScalarTest.java` (scalars, edge cases)

### Test Method
- Format: `<method>_<condition>_<expectedResult>` ← **SonarQube java:S3577**
  - `processElement_stringAndInt_writesQuotedCsv`
  - `extractValue_nullColumn_returnsEmptyString`
  - `run_emptyTableList_returnsEarly`

- Use descriptive names, not cryptic abbreviations
  - ✅ Good: `processElement_boolColumnTrue_writesTrueLiteral`
  - ❌ Bad: `testBool1`, `test_bool_x`

### Test Case IDs (Plan Traceability)
Every test method must have a comment on the line before `@Test`:

```java
// TC-001  (positive test case)
@Test
void processElement_stringAndInt_writesQuotedCsv() { ... }

// EC-002  (edge case)
@Test
void extractValue_null_returnsEmpty() { ... }

// ES-001  (error/exception case)
@Test
void extractValue_unsupportedType_logsAndReturnsEmpty() { ... }

// TC-003, TC-004  (parameterized test; list all IDs it implements)
@ParameterizedTest
@ValueSource(ints = { 1, 2, 3 })
void processElement_multipleColumns_writesCsvLine(int count) { ... }
```

---

## Code Style

### Formatting
- **Indent:** 2 spaces (not tabs)
- **Line length:** 100 characters (soft limit)
- **Imports:** Organized (java.*, javax.*, com.*, then static)
- **Blank lines:** One between methods; two between inner classes

### Structure
1. Package and imports
2. Class declaration with Javadoc (one-liner OK for simple tests)
3. Fields and static helpers
4. `@BeforeEach` / `@AfterEach` (test setup/teardown)
5. Test methods (grouped by class method being tested)

### Example

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

class StructToCsvFnScalarTest {

  // TC-001
  @Test
  void processElement_stringAndInt_writesQuotedCsv() {
    Pipeline p = Pipeline.create();
    Struct row = Struct.newBuilder()
        .set("ID").to("p-1")
        .set("COUNT").to(42L)
        .build();

    PCollection<String> result = p
        .apply(Create.of(row))
        .apply(ParDo.of(new StructToCsvFn()));

    PAssert.that(result).containsInAnyOrder("\"p-1\",\"42\"");

    p.run().waitUntilFinish();
  }

  // EC-001
  @Test
  void processElement_nullColumn_writesEmptyField() {
    // Setup
    Pipeline p = Pipeline.create();
    Struct row = Struct.newBuilder()
        .set("ID").to("p-1")
        .set("NOTE").to((String) null)
        .build();

    // Act
    PCollection<String> result = p
        .apply(Create.of(row))
        .apply(ParDo.of(new StructToCsvFn()));

    // Assert
    PAssert.that(result).containsInAnyOrder("\"p-1\",\"\"");

    p.run().waitUntilFinish();
  }
}
```

---

## Assertion Rules (SonarQube Compliance)

### Every Test Must Assert (java:S2699 - Mandatory Assertions)
- **REQUIRED:** Every `@Test` method must contain **at least one assertion, verification, or `PAssert`**
- Assertions must be **precise and meaningful**, not just checking for null:
  - ✅ `assertThat(actual).isEqualTo("expected value")`
  - ✅ `assertThat(list).containsExactly(elem1, elem2)`
  - ✅ `assertThrows(IllegalArgumentException.class, () -> code())`
  - ✅ `PAssert.that(collection).containsInAnyOrder(...)`
  - ❌ `assertThat(value).isNotNull()` (too weak; says nothing about correctness)
  - ❌ Empty test body (SonarQube failure: java:S2699)

### Appropriate Assertion Usage (java:S5785 - Fluent Assertions Preferred)
- **PREFERRED:** Use AssertJ fluent style for readability and maintainability
  - ✅ `assertThat(result).isEqualTo(expected).isNotEmpty()`
  - ❌ `assertTrue(result.equals(expected) && !result.isEmpty())`
  - ❌ Generic boolean assertions that hide intent

### Exception Testing (java:S5778 - Use assertThrows)
- **REQUIRED:** Use `assertThrows()` for exception tests, **never** try-catch blocks
  - ✅ `assertThrows(IllegalArgumentException.class, () -> method())`
  - ❌ Catching exception inside test method:
    ```java
    try {
        method();
        fail("should throw");
    } catch (IllegalArgumentException e) {
        // assertion here
    }
    ```

### No Empty Catch Blocks
- ❌ `catch (Exception e) { }` – forbidden
- ✅ `catch (Exception e) { fail("should not throw: " + e); }`
- ✅ `assertThrows(IllegalArgumentException.class, () -> code())`

### No Assumption-Based Tests
- ❌ `assumeTrue(systemProperty)` to conditionally skip tests
- ✅ Use `@Disabled("reason")` if a test cannot run

### Disabled Tests
- Only use `@Disabled` for **suspected bugs** (guardrail 8):
  ```java
  @Disabled("Suspected bug: Javadoc says quote only when necessary, code quotes all")
  @Test
  void processElement_csvQuoting_followsRfc4180() { ... }
  ```
- Reason must start with `Suspected bug:` (guard checks this).
- Never use `@Disabled` to comment out a failing test without reason.

### No Hardcoded Secrets (java:S2068 - Security Rule)
- **FORBIDDEN:** Never include real credentials, API keys, passwords, or sensitive data:
  - ❌ Real passwords, OAuth tokens, JWT secrets
  - ❌ Real API keys, database credentials, service account JSON
  - ❌ PHI/PII (phone numbers, SSN, real email addresses)
  
- **ALLOWED:** Use synthetic test data only:
  - ✅ Hardcoded synthetic strings: `"test-password"`, `"mock-api-key"`, `"user@example.com"`
  - ✅ Mocked credentials objects built from synthetic data
  - ✅ Test fixtures from files in `src/test/resources/`

- Guard Check: UtGuard `check` command scans for patterns like `password=`, `api_key:`, `secret:` in test code and fails if found.

---

## Mocking Best Practices (java:S6073, java:S6068)

### Never Use PowerMock or Static Mocking (java:S6073)
- ❌ `mockStatic(Class.class)` – forbidden
- ❌ `mockConstruction(Class.class)` – forbidden
- ❌ PowerMock library – forbidden
- ✅ Mock interfaces with Mockito
- ✅ Inject dependencies to make classes testable

### Mock Interfaces, Not Classes (java:S6068)
- ❌ `@Spy` on production classes – avoid
- ✅ Mock interfaces: `Mockito.mock(InterfaceClass.class)`
- ✅ Inject via constructor or setter
- ✅ Use when/thenReturn for predictable behavior

---

## Collaboration with Production Code

### Real vs. Mock Decisions

| Collaborator | Real or Mock? | Why |
|--------------|---------------|-----|
| `Struct` (Spanner) | Real | Built via `Struct.newBuilder()`, serializable, no external calls |
| `Pipeline` (Beam) | Real | Direct runner is fast, local, deterministic |
| `PAssert` (Beam) | Real | Part of test harness, designed for unit tests |
| External API (HTTP/REST) | Mock | Would need network access; too slow |
| Spanner client / DatabaseClient | Mock | Would need real connection; too slow |
| File I/O | `@TempDir` | Use temporary directory, never `/tmp` or hardcoded paths |
| Random / UUID generation | Synthetic | Fixed values or seeded randomness |
| System clock / time | Synthetic | Fixed time (e.g., `LocalDate.of(2024, 1, 1)`) |

### Mockito Usage
```java
// Mock an interface
Collaborator mock = Mockito.mock(Collaborator.class);
when(mock.method()).thenReturn(value);

// Verify calls
verify(mock).method();
verify(mock, times(2)).method();

// Do NOT use: mockStatic, mockConstruction, PowerMock
// Do NOT use: @Spy on production classes
```

---

## Common Pitfalls

| Pitfall | Why Bad | How to Fix |
|---------|---------|-----------|
| Test depends on method execution order | Breaks with parallel runs or shuffled order | Make each test independent; setUp/tearDown per test |
| Shared mutable static state | Tests interfere with each other | Use instance fields, reset in `@BeforeEach` |
| Hardcoded file paths | Breaks on different machines/OS | Use `@TempDir File` or `Files.createTempFile()` |
| Real time dependencies | Tests become flaky on slow machines | Use fixed time values, mock clocks |
| Exception swallowing | Silent failures | Always `fail()` or use `assertThrows()` |
| Copy-paste from production | Defeats test purpose | Reimplement logic independently in test |
| Test name doesn't match what it does | Confusing for maintenance | Rename to match actual behavior tested |

---

## Examples (Domain-Specific)

### Beam/Spanner Testing Pattern
```java
class StructToCsvFnTest {
  @Test
  void processElement_realBeamPipeline() {
    Pipeline p = Pipeline.create();
    
    PCollection<Struct> input = p.apply(Create.of(
        Struct.newBuilder().set("col1").to("value").build()
    ));
    
    PCollection<String> output = input.apply(ParDo.of(new MyFn()));
    
    PAssert.that(output).containsInAnyOrder("expected");
    
    p.run().waitUntilFinish();  // Block until done
  }
}
```

### JUnit 5 Parameterized Tests
```java
class MathTest {
  @ParameterizedTest
  @ValueSource(ints = { 1, 2, 3, 4, 5 })
  void isOdd_evenAndOdd_detectsCorrectly(int value) {
    // This method is called 5 times with each value
    // All 5 invocations are counted as one test method
    // List all IDs in the comment:
    // TC-001, TC-002, TC-003, TC-004, TC-005
  }
}
```

---

## Checklist Before Submitting Tests

- [ ] All files are under `src/test/java/`
- [ ] All new test classes extend nothing (no base class required)
- [ ] All test methods have `@Test` and are non-static
- [ ] Every test method has a case ID comment (`// TC-001`, `// EC-002`, etc.)
- [ ] Every test method asserts something (not `assertTrue(true)`)
- [ ] No `@Disabled` without `Suspected bug:` reason
- [ ] No banned imports (JUnit 4, PowerMock, TestContainers, Spring injection)
- [ ] No real network/file calls, no hardcoded paths
- [ ] No static mocking, no private reflection
- [ ] No test ordering (`@TestMethodOrder`, `@Order`)
- [ ] 2-space indent, 100-char line limit
- [ ] Class follows `<ClassName>Test` naming
- [ ] Methods follow `method_condition_result` naming
- [ ] All case IDs from the plan are implemented (or `@Disabled` with reason)
- [ ] No extra test files not in the plan

---

**You must follow all conventions above. Violation = guard failure = guardrail 7 triggered.**
