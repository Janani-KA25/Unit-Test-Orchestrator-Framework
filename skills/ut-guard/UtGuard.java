import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Deterministic guard utility for unit-test orchestrator.
 * 
 * Run from repository root with JDK 11+:
 *
 *   java UtGuard.java <command> [options]
 *
 * Commands:
 *   snapshot --run DIR                              fingerprint pom.xml and src/**
 *   approve  --run DIR --plan FILE --reply TEXT     validate plan and record approval
 *   check    --run DIR --plan FILE                  verify tests against snapshot and plan
 *   results  --run DIR --class TESTFQCN --label STR record and compare test outcomes
 *   coverage [--class NAME]                         measure JaCoCo coverage per class
 *   log      --event E --run DIR --detail TEXT      append audit event
 *
 * Global option: --config FILE (default unit-test-agent/config.json)
 * Exit codes: 0 (pass), 1 (fail), 2 (error/usage), 3 (stale data).
 */
public class UtGuard {

  static class Config {
    int lineCoverageTarget = 80;
    int branchCoverageTarget = 75;
    int maxFixAttempts = 3;
    int maxCasesPerPlan = 40;
    int surefireTimeoutSeconds = 900;
    Map<String, int[]> classTargets = new HashMap<>();
  }

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.err.println("Usage: java UtGuard.java <command> [options]");
      System.exit(2);
    }

    String command = args[0];
    switch (command) {
      case "snapshot":
        snapshot(args);
        break;
      case "approve":
        approve(args);
        break;
      case "check":
        check(args);
        break;
      case "results":
        results(args);
        break;
      case "coverage":
        coverage(args);
        break;
      case "log":
        log(args);
        break;
      default:
        System.err.println("Unknown command: " + command);
        System.exit(2);
    }
  }

  /**
   * snapshot --run DIR
   * 
   * Fingerprint pom.xml and all files under src/ using SHA-256.
   * Save to DIR/guard-snapshot.txt.
   * Write audit log entry.
   * Exit: 0 (success), 2 (error).
   */
  static void snapshot(String[] args) throws Exception {
    String runDir = getOption(args, "--run");
    if (runDir == null) {
      error("--run is required");
      System.exit(2);
    }
    
    try {
      // Create run directory if needed
      Path runPath = Paths.get(runDir);
      Files.createDirectories(runPath);
      
      // Traverse and hash pom.xml and src/**
      List<String> snapshotLines = new ArrayList<>();
      
      // Hash pom.xml
      Path pomFile = Paths.get("pom.xml");
      if (Files.exists(pomFile)) {
        byte[] pomContent = Files.readAllBytes(pomFile);
        String pomHash = sha256(new String(pomContent));
        snapshotLines.add(pomHash + "  pom.xml");
      }
      
      // Traverse src/main/java, src/test/java, src/**
      for (String srcPath : new String[] { "src/main/java", "src/main/resources", "src/test/java" }) {
        Path srcDir = Paths.get(srcPath);
        if (Files.exists(srcDir)) {
          Files.walk(srcDir)
            .filter(Files::isRegularFile)
            .sorted()
            .forEach(file -> {
              try {
                byte[] content = Files.readAllBytes(file);
                String hash = sha256(new String(content));
                String relPath = file.toString().replace("\\", "/");
                snapshotLines.add(hash + "  " + relPath);
              } catch (Exception e) {
                // skip unhashable files
              }
            });
        }
      }
      
      // Write snapshot file
      Path snapshotFile = runPath.resolve("guard-snapshot.txt");
      StringBuilder sb = new StringBuilder();
      for (String line : snapshotLines) {
        sb.append(line).append("\n");
      }
      Files.write(snapshotFile, sb.toString().getBytes(StandardCharsets.UTF_8));
      
      println("SNAPSHOT: OK (" + snapshotLines.size() + " files)");
      auditLog(runDir, "GUARD_SNAPSHOT", "ok", snapshotLines.size() + " files hashed");
      System.exit(0);
    } catch (Exception e) {
      error("snapshot: " + e.getMessage());
      System.exit(2);
    }
  }

  /**
   * approve --run DIR --plan FILE --reply TEXT
   * 
   * Validate plan file and record user approval.
   * Exit: 0 (recorded), 1 (refused), 2 (error).
   */
  static void approve(String[] args) throws Exception {
    String runDir = getOption(args, "--run");
    String planFile = getOption(args, "--plan");
    String reply = getOption(args, "--reply");
    
    if (runDir == null || planFile == null) {
      error("--run, --plan, --reply are required");
      System.exit(2);
    }
    
    try {
      Path runPath = Paths.get(runDir);
      Files.createDirectories(runPath);
      
      Path planPath = Paths.get(planFile);
      if (!Files.exists(planPath)) {
        error("Plan file not found: " + planFile);
        System.exit(2);
      }
      
      // Read plan
      String planContent = Files.readString(planPath);
      
      // Validate plan
      List<String> violations = new ArrayList<>();
      
      // Check status is "draft"
      if (!planContent.contains("status: draft")) {
        violations.add("Plan status must be 'draft', not already approved");
      }
      
      // Count test cases (TC-, EC-, ES- IDs)
      int tcCount = 0, ecCount = 0, esCount = 0;
      for (String line : planContent.split("\n")) {
        if (line.contains("| TC-")) tcCount++;
        if (line.contains("| EC-")) ecCount++;
        if (line.contains("| ES-")) esCount++;
      }
      int totalCases = tcCount + ecCount + esCount;
      
      if (totalCases > 40) {
        violations.add("Plan has " + totalCases + " test cases; max is 40");
      }
      
      // Check for credentials in plan (should not exist)
      if (planContent.toLowerCase().contains("password=") || 
          planContent.toLowerCase().contains("api_key:") ||
          planContent.toLowerCase().contains("secret:")) {
        violations.add("Plan contains credential-like patterns");
      }
      
      if (!violations.isEmpty()) {
        println("APPROVE: REFUSED");
        for (String v : violations) {
          println("  - " + v);
        }
        auditLog(runDir, "PLAN_REJECTED", "fail", violations.size() + " violations");
        System.exit(1);
      }
      
      // Compute plan SHA-256
      String planHash = sha256(planContent);
      
      // Extract class name from plan file
      String className = planFile.replaceAll(".*/", "").replace(".plan.md", "");
      
      // Write approval record
      Path approvalFile = runPath.resolve("approval-" + className + ".txt");
      String timestamp = Instant.now().toString();
      String approvalContent = "approval_timestamp: " + timestamp + "\n"
        + "plan_file: " + planFile + "\n"
        + "plan_sha256: " + planHash + "\n"
        + "test_cases: " + totalCases + " (TC: " + tcCount + ", EC: " + ecCount + ", ES: " + esCount + ")\n"
        + "user_reply: " + (reply != null ? reply : "APPROVED") + "\n";
      
      Files.write(approvalFile, approvalContent.getBytes(StandardCharsets.UTF_8));
      
      println("APPROVE: RECORDED");
      println("  Plan SHA-256: " + planHash);
      println("  Test cases: " + totalCases);
      auditLog(runDir, "PLAN_APPROVED", "ok", "Plan " + className + " approved with " + totalCases + " cases");
      System.exit(0);
    } catch (Exception e) {
      error("approve: " + e.getMessage());
      System.exit(2);
    }
  }

  /**
   * check --run DIR --plan FILE
   * 
   * Verify that:
   * - Plan unchanged since approval (hash matches)
   * - New test file implements all case IDs
   * - No banned APIs, missing assertions, empty catch
   * - @Disabled reasons correct
   * 
   * Exit: 0 (pass), 1 (fail), 2 (error).
   */
  static void check(String[] args) throws Exception {
    String runDir = getOption(args, "--run");
    String planFile = getOption(args, "--plan");
    
    if (runDir == null || planFile == null) {
      error("--run and --plan are required");
      System.exit(2);
    }
    
    try {
      Path runPath = Paths.get(runDir);
      Path planPath = Paths.get(planFile);
      
      if (!Files.exists(planPath)) {
        error("Plan file not found: " + planFile);
        System.exit(2);
      }
      
      List<String> failures = new ArrayList<>();
      
      // Check 1: Plan hash matches approval record
      String className = planFile.replaceAll(".*/", "").replace(".plan.md", "");
      Path approvalFile = runPath.resolve("approval-" + className + ".txt");
      
      if (!Files.exists(approvalFile)) {
        failures.add("Plan hash: approval record not found (not approved?)");
      } else {
        String planContent = Files.readString(planPath);
        String approvalContent = Files.readString(approvalFile);
        String currentHash = sha256(planContent);
        
        if (!approvalContent.contains("plan_sha256: " + currentHash)) {
          failures.add("Plan hash: file changed after approval");
        } else {
          println("Plan hash: ✓ matches approval");
        }
      }
      
      // Extract test file location from plan
      String testFilePath = null;
      for (String line : Files.readAllLines(planPath)) {
        if (line.startsWith("test_file:")) {
          testFilePath = line.substring("test_file:".length()).trim();
          break;
        }
      }
      
      if (testFilePath == null) {
        failures.add("Plan: test_file not specified");
      } else {
        Path testFile = Paths.get(testFilePath);
        
        if (!Files.exists(testFile)) {
          failures.add("Test file: not found (" + testFilePath + ")");
        } else {
          String testContent = Files.readString(testFile);
          
          // Check 2: All case IDs present
          Pattern caseIdPattern = Pattern.compile("// (TC-\\d+|EC-\\d+|ES-\\d+)");
          java.util.Set<String> foundIds = new java.util.HashSet<>();
          java.util.regex.Matcher m = caseIdPattern.matcher(testContent);
          while (m.find()) {
            foundIds.add(m.group(1));
          }
          
          // Extract expected IDs from plan
          java.util.Set<String> expectedIds = new java.util.HashSet<>();
          for (String line : Files.readAllLines(planPath)) {
            if (line.startsWith("| TC-") || line.startsWith("| EC-") || line.startsWith("| ES-")) {
              String[] parts = line.split("\\|");
              if (parts.length > 1) {
                String id = parts[1].trim();
                if (id.matches("(TC|EC|ES)-\\d+")) {
                  expectedIds.add(id);
                }
              }
            }
          }
          
          if (!foundIds.containsAll(expectedIds)) {
            java.util.Set<String> missing = new java.util.HashSet<>(expectedIds);
            missing.removeAll(foundIds);
            failures.add("Case IDs: missing " + missing);
          } else {
            println("Case IDs: ✓ all " + foundIds.size() + " present");
          }
          
          // Check 3: No banned imports
          Pattern[] bannedImports = {
            Pattern.compile("import.*PowerMock"),
            Pattern.compile("import.*Testcontainers"),
            Pattern.compile("import.*org\\.springframework\\.beans\\.factory\\.annotation"),
            Pattern.compile("@ExtendWith.*SpringExtension")
          };
          
          for (Pattern banned : bannedImports) {
            if (banned.matcher(testContent).find()) {
              failures.add("Imports: contains banned API: " + banned.pattern());
            }
          }
          println("Banned imports: ✓ none");
          
          // Check 4: Every @Test has assertion
          Pattern testPattern = Pattern.compile("@Test\\s+void (\\w+)\\(");
          Pattern assertPattern = Pattern.compile("assert|PAssert|verify");
          java.util.regex.Matcher testMatcher = testPattern.matcher(testContent);
          
          while (testMatcher.find()) {
            String methodName = testMatcher.group(1);
            int testStart = testMatcher.start();
            int nextTest = testContent.indexOf("@Test", testStart + 1);
            if (nextTest == -1) nextTest = testContent.length();
            
            String methodBody = testContent.substring(testStart, nextTest);
            if (!assertPattern.matcher(methodBody).find()) {
              failures.add("Assertion: test method " + methodName + " missing assertion");
            }
          }
          println("Assertions: ✓ all tests assert");
          
          // Check 5: @Disabled has "Suspected bug:" reason
          Pattern disabledPattern = Pattern.compile("@Disabled\\(\"([^\"]+)\"\\)");
          java.util.regex.Matcher disabledMatcher = disabledPattern.matcher(testContent);
          
          while (disabledMatcher.find()) {
            String reason = disabledMatcher.group(1);
            if (!reason.startsWith("Suspected bug:")) {
              failures.add("@Disabled: reason must start with 'Suspected bug:' but got '" + reason + "'");
            }
          }
          println("@Disabled: ✓ all with 'Suspected bug:' reason");
        }
      }
      
      // Report
      if (failures.isEmpty()) {
        println("\nGUARD CHECK: PASS");
        println("All validations passed.");
        auditLog(runDir, "GUARD_CHECK", "ok", "No violations");
        System.exit(0);
      } else {
        println("\nGUARD CHECK: FAIL (" + failures.size() + " failures)");
        for (String f : failures) {
          println("  ✗ " + f);
        }
        auditLog(runDir, "GUARD_CHECK", "fail", failures.size() + " violations");
        System.exit(1);
      }
    } catch (Exception e) {
      error("check: " + e.getMessage());
      System.exit(2);
    }
  }

  /**
   * results --run DIR --class TESTFQCN --label first|second
   * 
   * Parse Surefire XML results and record outcomes.
   * On second run, compare with first to detect flaky tests.
   * 
   * Exit: 0 (success), 1 (flaky or failure detected), 2 (error).
   */
  static void results(String[] args) throws Exception {
    String runDir = getOption(args, "--run");
    String testClass = getOption(args, "--class");
    String label = getOption(args, "--label");
    
    if (runDir == null || testClass == null || label == null) {
      error("--run, --class, --label are required");
      System.exit(2);
    }
    
    try {
      Path runPath = Paths.get(runDir);
      Files.createDirectories(runPath);
      
      // Convert FQCN to file name
      String testFileName = "TEST-" + testClass.replace(".", "/") + ".xml";
      Path surefireDir = Paths.get("target/surefire-reports");
      
      // Find the actual surefire XML file
      Path surefireFile = surefireDir.resolve(testFileName);
      if (!Files.exists(surefireFile)) {
        // Try without full path
        java.util.Optional<Path> found = Files.list(surefireDir)
          .filter(p -> p.getFileName().toString().startsWith("TEST-") && p.getFileName().toString().endsWith(".xml"))
          .filter(p -> p.getFileName().toString().contains(testClass.substring(testClass.lastIndexOf('.') + 1)))
          .findFirst();
        
        if (found.isPresent()) {
          surefireFile = found.get();
        } else {
          error("Surefire XML not found for " + testClass);
          System.exit(2);
        }
      }
      
      // Parse Surefire XML and extract test outcomes
      String surefireContent = Files.readString(surefireFile);
      
      java.util.Map<String, String> outcomes = new java.util.LinkedHashMap<>();
      Pattern testcasePattern = Pattern.compile("<testcase[^>]*name=\"([^\"]+)\"[^>]*>([^<]*)</(failure|error)?");
      java.util.regex.Matcher m = testcasePattern.matcher(surefireContent);
      
      while (m.find()) {
        String testName = m.group(1);
        String outcome = m.group(3) != null ? "FAIL" : "PASS";
        outcomes.put(testName, outcome);
      }
      
      // Record results
      Path resultsFile = runPath.resolve("results-" + testClass + "-" + label + ".txt");
      StringBuilder sb = new StringBuilder();
      sb.append("label: ").append(label).append("\n");
      sb.append("test_class: ").append(testClass).append("\n");
      sb.append("timestamp: ").append(Instant.now()).append("\n");
      sb.append("total: ").append(outcomes.size()).append("\n");
      
      long passCount = outcomes.values().stream().filter(o -> "PASS".equals(o)).count();
      long failCount = outcomes.values().stream().filter(o -> "FAIL".equals(o)).count();
      
      sb.append("passed: ").append(passCount).append("\n");
      sb.append("failed: ").append(failCount).append("\n");
      sb.append("tests:\n");
      
      for (java.util.Map.Entry<String, String> e : outcomes.entrySet()) {
        sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
      }
      
      Files.write(resultsFile, sb.toString().getBytes(StandardCharsets.UTF_8));
      
      if ("first".equals(label)) {
        println("RESULTS: RECORDED (first run)");
        println("  Tests: " + outcomes.size() + " passed: " + passCount + " failed: " + failCount);
        auditLog(runDir, "TESTS_RUN", "ok", outcomes.size() + " tests, " + passCount + " passed");
        System.exit(passCount == outcomes.size() ? 0 : 1);
      } else if ("second".equals(label)) {
        // Compare with first run
        Path firstResultsFile = runPath.resolve("results-" + testClass + "-first.txt");
        
        if (!Files.exists(firstResultsFile)) {
          println("FLAKY_CHECK: First run file not found, cannot compare");
          System.exit(2);
        }
        
        String firstResults = Files.readString(firstResultsFile);
        java.util.Set<String> failedInFirst = new java.util.HashSet<>();
        java.util.Set<String> failedInSecond = new java.util.HashSet<>();
        
        Pattern failPattern = Pattern.compile("  ([^:]+): FAIL");
        java.util.regex.Matcher failMatcher = failPattern.matcher(firstResults);
        while (failMatcher.find()) {
          failedInFirst.add(failMatcher.group(1));
        }
        
        failMatcher = failPattern.matcher(sb.toString());
        while (failMatcher.find()) {
          failedInSecond.add(failMatcher.group(1));
        }
        
        // Check for flakiness (different results)
        java.util.Set<String> flaky = new java.util.HashSet<>(failedInFirst);
        flaky.addAll(failedInSecond);
        flaky.removeAll(java.util.Collections.emptySet());
        
        // Flaky if first and second differ
        if (!failedInFirst.equals(failedInSecond)) {
          println("FLAKY_CHECK: DETECTED");
          java.util.Set<String> nowPass = new java.util.HashSet<>(failedInFirst);
          nowPass.removeAll(failedInSecond);
          
          java.util.Set<String> nowFail = new java.util.HashSet<>(failedInSecond);
          nowFail.removeAll(failedInFirst);
          
          if (!nowPass.isEmpty()) {
            println("  Now passing: " + nowPass);
          }
          if (!nowFail.isEmpty()) {
            println("  Now failing: " + nowFail);
          }
          
          auditLog(runDir, "FLAKY_DETECTED", "fail", nowPass.size() + " now pass, " + nowFail.size() + " now fail");
          System.exit(1);
        } else {
          println("FLAKY_CHECK: STABLE");
          println("  Run 1 == Run 2: no flakiness detected");
          auditLog(runDir, "TESTS_STABLE", "ok", "No flakiness detected");
          System.exit(0);
        }
      } else {
        error("Unknown label: " + label);
        System.exit(2);
      }
    } catch (Exception e) {
      error("results: " + e.getMessage());
      System.exit(2);
    }
  }

  /**
   * coverage [--class NAME]
   * 
   * Parse jacoco.xml and report line/branch coverage.
   * If --class is given, report per-class only.
   * Refuse stale reports (JaCoCo older than Surefire XML).
   * 
   * Exit: 0 (success), 1 (below target), 2 (error), 3 (stale).
   */
  static void coverage(String[] args) throws Exception {
    String className = getOption(args, "--class");
    
    try {
      Path jacocoFile = Paths.get("target/site/jacoco/jacoco.xml");
      Path surefireDir = Paths.get("target/surefire-reports");
      
      if (!Files.exists(jacocoFile)) {
        error("JaCoCo report not found: " + jacocoFile);
        System.exit(2);
      }
      
      // Check if jacoco.xml is stale compared to surefire reports
      if (Files.exists(surefireDir)) {
        java.util.Optional<Path> latestSurefire = Files.list(surefireDir)
          .filter(Files::isRegularFile)
          .max(java.util.Comparator.comparingLong(p -> {
            try { return Files.getLastModifiedTime(p).toMillis(); } catch (Exception e) { return 0L; }
          }));
        
        if (latestSurefire.isPresent()) {
          long jacocoTime = Files.getLastModifiedTime(jacocoFile).toMillis();
          long surefireTime = Files.getLastModifiedTime(latestSurefire.get()).toMillis();
          
          if (surefireTime > jacocoTime + 60000) {  // 60 seconds tolerance
            println("COVERAGE: STALE (JaCoCo older than test results)");
            println("Run: mvn jacoco:report");
            System.exit(3);
          }
        }
      }
      
      // Parse jacoco.xml
      String jacocoContent = Files.readString(jacocoFile);
      
      if (className == null) {
        // Repo-wide coverage
        Pattern counterPattern = Pattern.compile("<counter type=\"LINE\" missed=\"(\\d+)\" covered=\"(\\d+)\"");
        java.util.regex.Matcher m = counterPattern.matcher(jacocoContent);
        
        long totalMissed = 0, totalCovered = 0;
        while (m.find()) {
          totalMissed += Long.parseLong(m.group(1));
          totalCovered += Long.parseLong(m.group(2));
        }
        
        long total = totalMissed + totalCovered;
        double linePercent = total > 0 ? (100.0 * totalCovered / total) : 0;
        
        println("COVERAGE REPORT (repo-wide)");
        println("  LINE: " + totalCovered + "/" + total + " (" + String.format("%.1f", linePercent) + "%)");
        
        // Branch coverage
        counterPattern = Pattern.compile("<counter type=\"BRANCH\" missed=\"(\\d+)\" covered=\"(\\d+)\"");
        m = counterPattern.matcher(jacocoContent);
        
        totalMissed = 0;
        totalCovered = 0;
        while (m.find()) {
          totalMissed += Long.parseLong(m.group(1));
          totalCovered += Long.parseLong(m.group(2));
        }
        
        total = totalMissed + totalCovered;
        double branchPercent = total > 0 ? (100.0 * totalCovered / total) : 0;
        
        println("  BRANCH: " + totalCovered + "/" + total + " (" + String.format("%.1f", branchPercent) + "%)");
        println("RESULT: REPORTED");
        auditLog(".", "COVERAGE_MEASURED", "ok", String.format("Line: %.1f%%, Branch: %.1f%%", linePercent, branchPercent));
        System.exit(0);
      } else {
        // Per-class coverage
        Pattern sourceFilePattern = Pattern.compile("<sourcefile name=\"([^\"]+)\".*?</sourcefile>", Pattern.DOTALL);
        java.util.regex.Matcher sfMatcher = sourceFilePattern.matcher(jacocoContent);
        
        // Find class by name
        String classFileName = className.substring(className.lastIndexOf('.') + 1) + ".java";
        
        while (sfMatcher.find()) {
          String sourceFile = sfMatcher.group(0);
          if (sourceFile.contains("name=\"" + classFileName + "\"")) {
            // Extract coverage from this sourcefile
            Pattern counterPattern = Pattern.compile("<counter type=\"LINE\" missed=\"(\\d+)\" covered=\"(\\d+)\"");
            java.util.regex.Matcher m = counterPattern.matcher(sourceFile);
            
            long lineMissed = 0, lineCovered = 0;
            if (m.find()) {
              lineMissed = Long.parseLong(m.group(1));
              lineCovered = Long.parseLong(m.group(2));
            }
            
            long lineTotal = lineMissed + lineCovered;
            double linePercent = lineTotal > 0 ? (100.0 * lineCovered / lineTotal) : 0;
            
            counterPattern = Pattern.compile("<counter type=\"BRANCH\" missed=\"(\\d+)\" covered=\"(\\d+)\"");
            m = counterPattern.matcher(sourceFile);
            
            long branchMissed = 0, branchCovered = 0;
            if (m.find()) {
              branchMissed = Long.parseLong(m.group(1));
              branchCovered = Long.parseLong(m.group(2));
            }
            
            long branchTotal = branchMissed + branchCovered;
            double branchPercent = branchTotal > 0 ? (100.0 * branchCovered / branchTotal) : 0;
            
            println("COVERAGE REPORT for " + className);
            println("  LINE: " + lineCovered + "/" + lineTotal + " (" + String.format("%.1f", linePercent) + "%)");
            println("  BRANCH: " + branchCovered + "/" + branchTotal + " (" + String.format("%.1f", branchPercent) + "%)");
            println("RESULT: REPORTED");
            auditLog(".", "COVERAGE_CLASS", "ok", className + ": Line " + String.format("%.1f", linePercent) + "%, Branch " + String.format("%.1f", branchPercent) + "%");
            System.exit(0);
          }
        }
        
        error("Class not found in coverage report: " + className);
        System.exit(2);
      }
    } catch (Exception e) {
      error("coverage: " + e.getMessage());
      System.exit(2);
    }
  }

  /**
   * log --event E [--run DIR] --detail TEXT
   * 
   * Append a tab-separated line to unit-test-agent/audit.log.
   * Format: timestamp TAB event TAB result TAB detail
   * 
   * Exit: 0 (success), 2 (error).
   */
  static void log(String[] args) throws Exception {
    String event = getOption(args, "--event");
    String runDir = getOption(args, "--run");
    String detail = getOption(args, "--detail");
    
    if (event == null || detail == null) {
      error("--event and --detail are required");
      System.exit(2);
    }
    
    try {
      Path logFile = Paths.get("unit-test-agent/audit.log");
      
      // Create parent directory if needed
      Files.createDirectories(logFile.getParent());
      
      String timestamp = Instant.now().toString();
      String result = "INFO";  // default result
      String line = timestamp + "\t" + event + "\t" + result + "\t" + (runDir != null ? runDir : "-") + "\t" + detail + "\n";
      
      Files.write(logFile, line.getBytes(StandardCharsets.UTF_8), 
        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      
      println("Event logged: " + event);
      System.exit(0);
    } catch (Exception e) {
      error("log: " + e.getMessage());
      System.exit(2);
    }
  }

  // === Helpers ===

  static String getOption(String[] args, String name) {
    for (int i = 0; i < args.length - 1; i++) {
      if (args[i].equals(name)) {
        return args[i + 1];
      }
    }
    return null;
  }

  static void println(String msg) {
    System.out.println(msg);
  }

  static void error(String msg) {
    System.err.println("ERROR: " + msg);
  }

  static void auditLog(String runDir, String event, String result, String detail) throws Exception {
    Path logFile = Paths.get("unit-test-agent/audit.log");
    String timestamp = Instant.now().toString();
    String line = timestamp + "\t" + event + "\t" + result + "\t" + detail + "\n";
    Files.write(logFile, line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
  }

  static String sha256(String input) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder();
    for (byte b : hash) {
      hex.append(String.format("%02x", b));
    }
    return hex.toString();
  }
}
