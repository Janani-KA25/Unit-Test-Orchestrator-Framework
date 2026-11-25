# Orchestrator Framework as Maven Plugin

**Share the framework across all repos without copy-pasting!**

---

## 📦 What You Get

A Maven plugin that orchestrates unit test generation in any Java project.

**Usage:**
```bash
mvn orchestrator:test -Dclass=com.example.MyClass
```

No files to copy. No submodules. No Docker. Just add to `pom.xml` and run.

---

## 🚀 Setup (One-Time per Org)

### Step 1: Prepare Your Repository

Your unit-test-orchestrator repo needs:

```
unit-test-orchestrator/
├── pom-plugin.xml                              # Plugin Maven config
├── src/main/java/com/example/orchestrator/maven/
│   └── TestOrchestratorMojo.java              # Plugin implementation
├── .github/workflows/publish.yml               # Auto-publish to GitHub Packages
├── supervisor/                                 # (existing)
├── skills/                                     # (existing)
├── rules/                                      # (existing)
└── templates/                                  # (existing)
```

✅ All files created by this guide.

---

### Step 2: Configure GitHub Packages

**In your unit-test-orchestrator repo:**

1. Go to **Settings → Developer settings → Personal access tokens**
2. Create token with `write:packages` scope
3. Add as **Repository Secret** `PUBLISH_TOKEN`

**OR** (Recommended) use built-in `GITHUB_TOKEN`:
- Already available in GitHub Actions
- No manual setup needed
- Workflow uses it automatically

---

### Step 3: Publish Initial Version

**Push a version tag:**

```bash
cd unit-test-orchestrator
git tag v1.0.0
git push origin v1.0.0
```

**GitHub Actions automatically:**
- Builds plugin
- Publishes to GitHub Packages
- Creates release with install instructions

✅ **Done!** Plugin is now available to all repos.

---

## 📥 Setup (Per Repo - Easy!)

### In Your Target Repo

#### Step 1: Add GitHub Packages as Maven Repository

**Add to `pom.xml`:**

```xml
<repositories>
    <repository>
        <id>github</id>
        <name>GitHub Packages</name>
        <url>https://maven.pkg.github.com/YOUR-ORG/unit-test-orchestrator</url>
        <releases>
            <enabled>true</enabled>
        </releases>
    </repository>
</repositories>

<pluginRepositories>
    <pluginRepository>
        <id>github</id>
        <name>GitHub Packages</name>
        <url>https://maven.pkg.github.com/YOUR-ORG/unit-test-orchestrator</url>
        <releases>
            <enabled>true</enabled>
        </releases>
    </pluginRepository>
</pluginRepositories>
```

#### Step 2: Add Plugin Dependency

**In `pom.xml` `<build><plugins>`:**

```xml
<plugin>
    <groupId>com.example</groupId>
    <artifactId>orchestrator-maven-plugin</artifactId>
    <version>1.0.0</version>
</plugin>
```

#### Step 3: Configure Maven Authentication

**Create `~/.m2/settings.xml`:**

```xml
<settings>
    <servers>
        <server>
            <id>github</id>
            <username>YOUR-GITHUB-USERNAME</username>
            <password>ghp_YOUR-PERSONAL-ACCESS-TOKEN</password>
        </server>
    </servers>
</settings>
```

**OR** (Easier for CI/CD) use environment variables:

```bash
export MAVEN_USERNAME=YOUR-GITHUB-USERNAME
export MAVEN_PASSWORD=ghp_YOUR-PERSONAL-ACCESS-TOKEN
```

---

## 🎯 Usage

### Start Testing a Class

```bash
mvn orchestrator:test -Dclass=com.example.extract.StructToCsvFn
```

**What happens:**
1. Framework detects your class
2. Analyzes it (methods, existing tests, baseline coverage)
3. Asks you which test cases to focus on
4. Generates tests (you approve)
5. Runs and verifies
6. Reports coverage

### Approve a Pending Plan

```bash
mvn orchestrator:test -Dclass=com.example.extract.StructToCsvFn -Daction=approve -Dplan-decision=APPROVE
```

### Resume Testing Another Class

```bash
mvn orchestrator:test -Daction=resume
```

### Check Status

```bash
mvn orchestrator:test -Daction=status
```

---

## 🔧 Configuration

### Override Framework Location

If framework isn't at `./unit-test-orchestrator`:

```bash
mvn orchestrator:test \
  -Dclass=com.example.MyClass \
  -Dorchestrator.framework.dir=./path/to/framework
```

### Supported Properties

| Property | Default | Description |
|----------|---------|-------------|
| `orchestrator.class` | (required) | Class to test: `com.example.MyClass` |
| `orchestrator.action` | `start` | Action: `start`, `approve`, `resume`, `status` |
| `orchestrator.framework.dir` | `unit-test-orchestrator` | Path to framework |

---

## 📋 Requirements

✅ Java 17+ (JDK)  
✅ Maven 3.9+  
✅ GitHub account with read permissions  
✅ Internet access (to fetch plugin from GitHub Packages)  

---

## ❓ Troubleshooting

### "Plugin not found in any repository"

**Fix:** Add pluginRepositories section (see Step 1 above)

```xml
<pluginRepositories>
    <pluginRepository>
        <id>github</id>
        <url>https://maven.pkg.github.com/YOUR-ORG/unit-test-orchestrator</url>
    </pluginRepository>
</pluginRepositories>
```

### "Could not authenticate with repository"

**Fix 1:** Check `~/.m2/settings.xml` has correct credentials

```bash
cat ~/.m2/settings.xml | grep -A2 github
```

**Fix 2:** Use environment variables instead:

```bash
export MAVEN_USERNAME=YOUR-USERNAME
export MAVEN_PASSWORD=ghp_YOUR-TOKEN
mvn orchestrator:test -Dclass=com.example.MyClass
```

**Fix 3:** Verify token has `read:packages` scope (GitHub Settings → Tokens)

### "Framework not found at: ./unit-test-orchestrator"

**Fix:** Clone framework as submodule:

```bash
git submodule add https://github.com/YOUR-ORG/unit-test-orchestrator ./unit-test-orchestrator
mvn orchestrator:test -Dclass=com.example.MyClass
```

**OR** specify location:

```bash
mvn orchestrator:test \
  -Dclass=com.example.MyClass \
  -Dorchestrator.framework.dir=../../unit-test-orchestrator
```

---

## 🌍 CI/CD Integration

### GitHub Actions Example

Add to `.github/workflows/test.yml` in your repo:

```yaml
name: Orchestrated Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Checkout orchestrator framework
        uses: actions/checkout@v4
        with:
          repository: YOUR-ORG/unit-test-orchestrator
          path: unit-test-orchestrator
      
      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Run orchestrator tests
        run: mvn orchestrator:test -Dclass=com.example.extract.StructToCsvFn
        env:
          MAVEN_USERNAME: ${{ github.actor }}
          MAVEN_PASSWORD: ${{ secrets.GITHUB_TOKEN }}
```

### Jenkins Pipeline Example

```groovy
pipeline {
    agent any
    
    stages {
        stage('Checkout') {
            steps {
                checkout(scm)
                sh 'git submodule update --init unit-test-orchestrator'
            }
        }
        
        stage('Orchestrate Tests') {
            steps {
                sh '''
                    mvn orchestrator:test \
                        -Dclass=com.example.extract.StructToCsvFn \
                        -Daction=start
                '''
            }
        }
        
        stage('Report') {
            steps {
                junit 'target/surefire-reports/**/*.xml'
                jacoco execPattern: 'target/jacoco.exec'
            }
        }
    }
}
```

---

## 🔄 Updating the Plugin

### New Version Released

**In your repo:**

```bash
# Update pom.xml version
<version>1.0.1</version>  <!-- changed from 1.0.0 -->

# Download new version
mvn dependency:resolve
```

**OR** use latest:

```xml
<version>LATEST</version>
```

---

## 📚 Integration with Existing Tests

The plugin runs **alongside** your existing tests:

✅ **Existing tests unchanged** (src/test/java/)  
✅ **New tests added** (src/test/java/com/example/extract/StructToCsvFnScalarTest.java)  
✅ **Coverage reports combined** (JaCoCo merges all)  
✅ **CI/CD runs all** (mvn test runs both)  

---

## 🎁 Summary

| Step | Effort | One-Time? |
|------|--------|-----------|
| Org: Publish plugin to GitHub Packages | 5 min | ✅ Yes |
| Repo: Add to pom.xml | 1 min | ✅ Per repo |
| Repo: Configure auth (~/.m2/settings.xml) | 2 min | ✅ Per dev machine |
| **Use:** Test a class | 1 min | ❌ No (per class) |

**Result:** Any Java dev in org can test any class with one Maven command. No copy-pasting. Framework stays current everywhere.

---

## 🤝 Support

Questions? Issues?
- Check [troubleshooting](#-troubleshooting) section above
- Open issue: https://github.com/YOUR-ORG/unit-test-orchestrator/issues
- See framework docs: [README.md](README.md)
