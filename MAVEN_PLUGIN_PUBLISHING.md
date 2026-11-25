# Publishing & Distributing as Maven Plugin - Complete Guide

## 🎯 Overview

Three simple steps:
1. **Org setup** (one-time, 10 min)
2. **Publish** (automated via GitHub Actions)
3. **Teams use** (one Maven command)

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  Your Org's Central Framework               │
│              github.com/YOUR-ORG/unit-test-orchestrator     │
│                                                             │
│  ├─ pom-plugin.xml (plugin definition)                    │
│  ├─ src/main/java/.../TestOrchestratorMojo.java           │
│  ├─ .github/workflows/publish.yml (auto-publish)          │
│  ├─ supervisor/SKILL.md                                   │
│  └─ skills/                                               │
│                                                             │
└──────────────────────────┬──────────────────────────────────┘
                           │
                    mvn deploy (CI/CD)
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│          GitHub Packages Maven Repository                   │
│  https://maven.pkg.github.com/YOUR-ORG/orchestrator        │
│                                                             │
│  orchestrator-maven-plugin-1.0.0.jar                       │
│  orchestrator-maven-plugin-1.0.0.pom                       │
│  orchestrator-maven-plugin-1.0.0-sources.jar               │
│  orchestrator-maven-plugin-1.0.0-javadoc.jar               │
│                                                             │
└──────────────────────────┬──────────────────────────────────┘
                           │
                   mvn dependency:resolve
                           │
           ┌───────────────┼───────────────┐
           │               │               │
           ▼               ▼               ▼
        ┌──────┐      ┌──────┐      ┌──────────┐
        │Repo 1│      │Repo 2│      │Repo N... │
        │      │      │      │      │          │
        │pom.xml   │pom.xml   │pom.xml   │
        └───┬──┘      └───┬──┘      └────┬─────┘
            │            │             │
         mvn orchestrator:test
            │            │             │
            ▼            ▼             ▼
        ✅ Tests   ✅ Tests       ✅ Tests
```

---

## 📝 Step 1: Org Setup (One-Time)

### A. Prepare Framework Repository

```bash
# Clone your unit-test-orchestrator repo
git clone https://github.com/YOUR-ORG/unit-test-orchestrator
cd unit-test-orchestrator

# Verify files exist:
ls -la pom-plugin.xml
ls -la src/main/java/com/example/orchestrator/maven/TestOrchestratorMojo.java
ls -la .github/workflows/publish.yml
```

✅ All files created by this guide (see above)

### B. Configure GitHub Packages

1. Go to framework repo **Settings → Developer settings → Personal access tokens**
2. Create new token:
   - Scope: `write:packages`, `read:packages`
   - Name: `PUBLISH_TOKEN`
3. Copy token (you'll need it once)
4. Go to framework repo **Settings → Secrets and variables → Repository secrets**
5. Click **New repository secret**
   - Name: `PUBLISH_TOKEN`
   - Value: Paste token
   - Save

**OR** (Easier - use GitHub's built-in token):
- GitHub Actions provides `GITHUB_TOKEN` automatically
- Workflow already uses it
- No manual setup needed ✅

---

## 🚀 Step 2: Publish (Automated)

### Trigger Publishing

```bash
cd unit-test-orchestrator

# Tag a version
git tag v1.0.0
git push origin v1.0.0
```

**What happens automatically:**
1. GitHub Actions detects tag
2. Runs `publish.yml` workflow
3. Builds Maven plugin
4. Publishes to GitHub Packages
5. Creates release with instructions

✅ **Done!** Plugin now available to all repos.

**Check it worked:**
```bash
# Visit GitHub repo → Releases
# Should see v1.0.0 with install instructions

# Or via command line:
curl -H "Authorization: bearer ghp_YOUR_TOKEN" \
  https://maven.pkg.github.com/YOUR-ORG/unit-test-orchestrator/v1.0.0
```

---

## 📥 Step 3: Teams Use (Per Repo)

### Setup in Target Repository

**A. Add repositories to pom.xml**

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/YOUR-ORG/unit-test-orchestrator</url>
        <releases><enabled>true</enabled></releases>
    </repository>
</repositories>

<pluginRepositories>
    <pluginRepository>
        <id>github</id>
        <url>https://maven.pkg.github.com/YOUR-ORG/unit-test-orchestrator</url>
        <releases><enabled>true</enabled></releases>
    </pluginRepository>
</pluginRepositories>
```

**B. Add plugin to pom.xml**

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.example</groupId>
            <artifactId>orchestrator-maven-plugin</artifactId>
            <version>1.0.0</version>
        </plugin>
    </plugins>
</build>
```

**C. Configure Maven auth (pick ONE)**

**Option 1: Local settings.xml (dev machines)**
```bash
# Edit ~/.m2/settings.xml
<server>
    <id>github</id>
    <username>YOUR-GITHUB-USERNAME</username>
    <password>ghp_YOUR-PERSONAL-ACCESS-TOKEN</password>
</server>
```

**Option 2: CI/CD Pipeline (GitHub Actions)**
```yaml
- name: Configure Maven
  run: |
    mkdir -p ~/.m2
    cat > ~/.m2/settings.xml << EOF
    <settings>
        <servers>
            <server>
                <id>github</id>
                <username>${{ github.actor }}</username>
                <password>${{ secrets.GITHUB_TOKEN }}</password>
            </server>
        </servers>
    </settings>
    EOF
```

**Option 3: Environment Variables**
```bash
export MAVEN_USERNAME=YOUR-USERNAME
export MAVEN_PASSWORD=ghp_YOUR-TOKEN
```

---

## ✅ Usage

### Start Testing

```bash
cd any-java-project

# Download plugin (first time)
mvn dependency:resolve

# Test a class
mvn orchestrator:test -Dclass=com.example.extract.StructToCsvFn
```

### No Copy-Pasting!

✅ Plugin downloads automatically from GitHub Packages  
✅ Always uses latest version (or pinned version)  
✅ Works in any repo that has pom.xml configured  
✅ CI/CD pipelines use same command  

---

## 🔄 Updating Plugin

### Org: Release New Version

```bash
cd unit-test-orchestrator

# Make changes
# Commit them
git add .
git commit -m "Add new feature"

# Tag new version
git tag v1.0.1
git push origin v1.0.1

# GitHub Actions auto-publishes
```

### Teams: Get Latest

```bash
# Automatic - Maven updates on next run
mvn clean dependency:resolve

# Or explicitly update version in pom.xml:
# <version>1.0.1</version>  <!-- changed from 1.0.0 -->

# Or use LATEST (auto-updates):
# <version>LATEST</version>
```

---

## 🌍 For CI/CD (GitHub Actions Example)

```yaml
name: Run Orchestrator Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Configure Maven
        run: |
          mkdir -p ~/.m2
          cat > ~/.m2/settings.xml << EOF
          <?xml version="1.0" encoding="UTF-8"?>
          <settings>
              <servers>
                  <server>
                      <id>github</id>
                      <username>${{ github.actor }}</username>
                      <password>${{ secrets.GITHUB_TOKEN }}</password>
                  </server>
              </servers>
          </settings>
          EOF
      
      - name: Run Tests
        run: |
          mvn orchestrator:test -Dclass=com.example.extract.StructToCsvFn
          mvn orchestrator:test -Dclass=com.example.validate.ValidationRules
```

---

## 📊 Comparison: Before vs After

| Task | Before | After |
|------|--------|-------|
| Share framework with team | Copy-paste files × 50 repos | 1 Maven command |
| Update framework | Manual sync in all repos | Auto-updates via Maven |
| New developer joins | Clone, setup submodule | mvn install plugin |
| CI/CD setup | Commit framework to repo | Add pom.xml, download |
| Framework version | Same everywhere (manual) | Pinned or LATEST |

---

## ⚡ Timeline

| Step | Time | Who |
|------|------|-----|
| Org: Prepare files | 5 min | Framework owner |
| Org: Configure GitHub Packages | 2 min | Framework owner |
| Org: First publish (git tag + push) | 1 min | Framework owner |
| Team: Add to pom.xml | 2 min | Dev |
| Team: Configure Maven auth | 2 min | Dev (one-time) |
| Team: Test a class | 1 min | Dev (per class) |
| **Total org setup** | **10 min** | **One-time** |
| **Total team setup** | **5 min** | **Per-team, one-time** |
| **Per-usage** | **1 min** | **Per-class** |

---

## 🎁 Summary

✅ **No copy-pasting** - Maven downloads plugin automatically  
✅ **Centralized updates** - All repos get latest version  
✅ **Easy sharing** - Just add pom.xml snippet  
✅ **CI/CD ready** - Works in GitHub Actions, Jenkins, etc.  
✅ **No dependencies** - Only needs Java + Maven  
✅ **Secure** - Uses GitHub Packages + Personal Access Token  

---

## 📚 Next Steps

1. Push framework to GitHub
2. Create GitHub Packages repository
3. Tag v1.0.0 → auto-publishes
4. Teams add pom.xml snippets
5. Run: `mvn orchestrator:test -Dclass=YourClass`

**All set!** Framework is now accessible org-wide. 🚀
