# Orchestrator Framework Maven Plugin - Setup Snippets

Copy-paste these snippets into your project's `pom.xml`.

---

## ✅ Quick Add (3 pieces)

### 1️⃣ Add Plugin Repositories

Add this to your `<project>` root (after `</dependencies>`, before `</project>`):

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

### 2️⃣ Add Plugin to Build

Add this inside `<build><plugins>`:

```xml
<plugin>
    <groupId>com.example</groupId>
    <artifactId>orchestrator-maven-plugin</artifactId>
    <version>1.0.0</version>
</plugin>
```

### 3️⃣ Configure Maven Auth

**Option A: Edit ~/.m2/settings.xml**

Add this inside `<settings><servers>`:

```xml
<server>
    <id>github</id>
    <username>YOUR-GITHUB-USERNAME</username>
    <password>ghp_YOUR-PERSONAL-ACCESS-TOKEN</password>
</server>
```

**Option B: Use Environment Variables**

```bash
export MAVEN_USERNAME=YOUR-GITHUB-USERNAME
export MAVEN_PASSWORD=ghp_YOUR-PERSONAL-ACCESS-TOKEN
```

**Option C: Use GitHub Actions Secret**

In `.github/workflows/ci.yml`:

```yaml
- name: Configure Maven
  run: |
    mkdir -p ~/.m2
    cat > ~/.m2/settings.xml << EOF
    <?xml version="1.0" encoding="UTF-8"?>
    <settings>
        <servers>
            <server>
                <id>github</id>
                <username>\${{ github.actor }}</username>
                <password>\${{ secrets.GITHUB_TOKEN }}</password>
            </server>
        </servers>
    </settings>
    EOF
```

---

## 📋 Minimal pom.xml Example

If you're starting fresh, here's a complete minimal pom.xml:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>my-project</artifactId>
    <version>1.0.0</version>

    <name>My Project</name>

    <!-- GitHub Packages Repositories -->
    <repositories>
        <repository>
            <id>github</id>
            <url>https://maven.pkg.github.com/YOUR-ORG/unit-test-orchestrator</url>
            <releases>
                <enabled>true</enabled>
            </releases>
        </repository>
    </repositories>

    <pluginRepositories>
        <pluginRepository>
            <id>github</id>
            <url>https://maven.pkg.github.com/YOUR-ORG/unit-test-orchestrator</url>
            <releases>
                <enabled>true</enabled>
            </releases>
        </pluginRepository>
    </pluginRepositories>

    <!-- Dependencies -->
    <dependencies>
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <version>5.9.0</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <!-- Build Plugins -->
    <build>
        <plugins>
            <!-- Orchestrator Test Framework -->
            <plugin>
                <groupId>com.example</groupId>
                <artifactId>orchestrator-maven-plugin</artifactId>
                <version>1.0.0</version>
            </plugin>

            <!-- Maven Surefire (runs tests) -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.0.0</version>
            </plugin>

            <!-- JaCoCo (coverage reporting) -->
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>0.8.10</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>prepare-agent</goal>
                        </goals>
                    </execution>
                    <execution>
                        <id>report</id>
                        <phase>test</phase>
                        <goals>
                            <goal>report</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>

</project>
```

---

## 🔧 Existing Project Integration

If you already have a pom.xml:

1. Find `<repositories>` section → add GitHub repo (Snippet 1a)
2. Find `<pluginRepositories>` section → add GitHub plugin repo (Snippet 1b)
3. Find `<build><plugins>` section → add orchestrator plugin (Snippet 2)
4. Configure Maven auth (Snippet 3, any option)

---

## ✅ Verify Installation

```bash
# Download the plugin
mvn dependency:resolve -DgroupId=com.example -DartifactId=orchestrator-maven-plugin

# See plugin info
mvn help:describe -Dplugin=com.example:orchestrator-maven-plugin

# Test it!
mvn orchestrator:test -Dclass=com.example.MyClass
```

---

## 🆘 Common Issues

### "Plugin not found"
→ Check you added both `<repositories>` AND `<pluginRepositories>` (Snippet 1)

### "Could not authenticate"
→ Check Maven settings.xml has correct GitHub username & token (Snippet 3)

### "Cannot read pom.xml"
→ Verify XML is well-formed (use IDE's XML validator)

---

## 🚀 Next Steps

1. Add snippets to your pom.xml
2. Configure `~/.m2/settings.xml`
3. Run: `mvn orchestrator:test -Dclass=com.example.MyClass`
4. Done! No copy-pasting needed.
