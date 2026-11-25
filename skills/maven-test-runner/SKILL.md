---
trigger: manual
reference: true
---

# Maven Test Commands Reference (`maven-test-runner`)

**Purpose:** Catalog of allowed Maven commands for building, testing, and measuring coverage.

**When to use:** Copy/paste commands from this reference in ut-analyze, ut-verify, ut-report phases.

---

## Allowed Maven Flags

**Safe flags (always use):**
```
-B              Batch mode (no interactive prompts)
-ntp            No transfer progress (faster output)
-DskipTests     Skip tests (compile only)
-Dtest=CLASS    Run specific test class
-Dmaven.test.failure.ignore=true   Continue even if tests fail
```

**Coverage flags (only for ut-report phase):**
```
-Pjacoco        Enable JaCoCo profile (if configured in pom.xml)
```

**Never use:**
```
-X              Debug (too verbose)
-e              Errors (verbose; use sparingly)
-U              Force update (slow)
--fail-at-end   Changes timing (may hide early failures)
-T 1C           Parallel builds (flaky test detection needs serial)
```

---

## Compilation Commands

### Compile production code only
```bash
mvn -B -ntp clean compile
```
Expected: `BUILD SUCCESS`, no tests run.

### Compile test code only
```bash
mvn -B -ntp clean test-compile -DskipTests
```
Expected: `BUILD SUCCESS`, test code compiled, no tests run.

### Full build (compile both)
```bash
mvn -B -ntp clean test-compile
```
Expected: `BUILD SUCCESS`, both production and test code compiled.

---

## Test Execution Commands

### Run all tests
```bash
mvn -B -ntp clean test
```
Output: All test results to `target/surefire-reports/`

### Run single test class
```bash
mvn -B -ntp test -Dtest=com.example.StructToCsvFnTest
```
Output: Only that class's tests to `target/surefire-reports/TEST-com.example.StructToCsvFnTest.xml`

### Run test and ignore failures (don't stop)
```bash
mvn -B -ntp test -Dmaven.test.failure.ignore=true
```
Output: All test results, even if some fail; build continues to end.

### Run with coverage (JaCoCo)
```bash
mvn -B -ntp clean test -Pjacoco
```
Output: Tests run, coverage data written to `target/site/jacoco/`

### Run with timeout per test
```bash
mvn -B -ntp test -DforkCount=1 -DreuseForks=false -Dsurefire.timeout=900
```
(900 seconds = 15 minutes per test; adjust as needed)

---

## Verification Commands

### Check if Maven is installed and configured
```bash
mvn -v
```
Expected output:
```
Apache Maven X.X.X
Java version: 21.x (should be 21+)
```

### Verify test jar was created
```bash
ls -la target/test-classes/com/example/YourClassTest.class
```
Expected: file exists and is recent.

### List all test classes found
```bash
mvn -B -ntp test -DfailIfNoTests=false 2>&1 | grep "Tests run:"
```
Expected: summary line showing test count.

---

## Coverage Report Commands

### Generate JaCoCo report
```bash
mvn -B -ntp jacoco:report
```
Output: HTML report at `target/site/jacoco/index.html`, XML at `target/site/jacoco/jacoco.xml`

### Parse JaCoCo XML (for guard)
```bash
java UtGuard coverage --class com.example.StructToCsvFn
```
Reads `target/site/jacoco/jacoco.xml` and reports line/branch coverage.

---

## Combining Commands

### Full workflow: clean, compile, test, coverage
```bash
mvn -B -ntp clean test -Pjacoco && mvn -B -ntp jacoco:report
```

### Quick check: compile only, then verify tests found
```bash
mvn -B -ntp test-compile && mvn -B -ntp test -DfailIfNoTests=false
```

---

## Example Phase Workflows

### Phase 1 (ut-analyze) baseline:
```bash
# 1. Check Maven setup
mvn -v

# 2. Compile and find test classes
mvn -B -ntp clean test-compile

# 3. Run baseline with coverage
mvn -B -ntp clean test -Pjacoco -Dmaven.test.failure.ignore=true

# 4. Generate coverage report
mvn -B -ntp jacoco:report

# 5. Parse coverage (guard does this)
java UtGuard coverage --class com.example.extract.StructToCsvFn
```

### Phase 7 (ut-verify) first run:
```bash
# 1. Compile test code
mvn -B -ntp clean test-compile

# 2. Run single test class (may fail initially)
mvn -B -ntp test -Dtest=StructToCsvFnTest -Dmaven.test.failure.ignore=true

# 3. Check output
ls target/surefire-reports/TEST-*.xml
```

### Phase 7 (ut-verify) flakiness check:
```bash
# Run same test again (order shuffled)
mvn -B -ntp test -Dtest=StructToCsvFnTest -Dmaven.test.failure.ignore=true -Drunorder=random

# Guard compares results
java UtGuard results --run unit-test-agent/reports/$RUN_DIR --class com.example.MyClassTest --label first
java UtGuard results --run unit-test-agent/reports/$RUN_DIR --class com.example.MyClassTest --label second
```

### Phase 8 (ut-report) final coverage:
```bash
# Full build with coverage
mvn -B -ntp clean test -Pjacoco -Dmaven.test.failure.ignore=true

# Generate report
mvn -B -ntp jacoco:report

# Parse final numbers
java UtGuard coverage --class com.example.StructToCsvFn
```

---

## Troubleshooting

| Command | Expected | Got | Action |
|---------|----------|-----|--------|
| `mvn -v` | Java 21+ | Java 25 | Use jdk-21.cmd wrapper (see AGENTS.md) |
| `mvn test` | BUILD SUCCESS | Compilation error | Fix test code; check imports |
| `mvn test` | 6 tests run | 0 tests run | Verify @Test annotations, class name ends in Test |
| `mvn test` | XML in target/surefire-reports/ | Directory missing | Ensure maven-surefire-plugin configured |
| `mvn jacoco:report` | Report generated | File not found | Ensure maven-jacoco-plugin configured; run `mvn test` first |
| `java UtGuard coverage` | Percentage numbers | File not found | Ensure JaCoCo report was generated; check path |

---

**Reference document only. No execution required.**
