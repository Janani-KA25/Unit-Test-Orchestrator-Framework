#!/bin/bash

# Quick setup script for Orchestrator Framework Maven Plugin
# Run this in your Java project root to configure the plugin

set -e

ORG="${1:-YOUR-ORG}"
GITHUB_USERNAME="${2:-}"
GITHUB_TOKEN="${3:-}"

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  Orchestrator Framework - Maven Plugin Setup                 ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

if [ -z "$ORG" ]; then
    echo "❌ Usage: $0 <org-name> [github-username] [github-token]"
    echo ""
    echo "Examples:"
    echo "  $0 my-company"
    echo "  $0 my-company myusername ghp_abc123..."
    exit 1
fi

echo "✓ Organization: $ORG"
echo ""

# Step 1: Check if pom.xml exists
if [ ! -f "pom.xml" ]; then
    echo "❌ pom.xml not found. Run this script in your Maven project root."
    exit 1
fi

echo "Step 1: Backing up pom.xml..."
cp pom.xml pom.xml.backup
echo "✓ Backup created: pom.xml.backup"
echo ""

# Step 2: Add pluginRepositories
echo "Step 2: Adding GitHub Packages plugin repository..."
if ! grep -q "maven.pkg.github.com" pom.xml; then
    # Find the closing </project> tag and insert before it
    sed -i.bak "/<\/project>/i \\
\\
    <pluginRepositories>\\
        <pluginRepository>\\
            <id>github<\/id>\\
            <name>GitHub Packages<\/name>\\
            <url>https:\/\/maven.pkg.github.com\/${ORG}\/unit-test-orchestrator<\/url>\\
            <releases>\\
                <enabled>true<\/enabled>\\
            <\/releases>\\
        <\/pluginRepository>\\
    <\/pluginRepositories>\\
\\
    <repositories>\\
        <repository>\\
            <id>github<\/id>\\
            <name>GitHub Packages<\/name>\\
            <url>https:\/\/maven.pkg.github.com\/${ORG}\/unit-test-orchestrator<\/url>\\
            <releases>\\
                <enabled>true<\/enabled>\\
            <\/releases>\\
        <\/repository>\\
    <\/repositories>" pom.xml
    rm pom.xml.bak
    echo "✓ Repositories added"
else
    echo "✓ Repositories already configured"
fi
echo ""

# Step 3: Add plugin dependency
echo "Step 3: Adding plugin dependency to pom.xml..."
if ! grep -q "orchestrator-maven-plugin" pom.xml; then
    # Try to add to build/plugins section
    if grep -q "<build>" pom.xml; then
        sed -i.bak "/<\/build>/i \\
        <plugin>\\
            <groupId>com.example<\/groupId>\\
            <artifactId>orchestrator-maven-plugin<\/artifactId>\\
            <version>1.0.0<\/version>\\
        <\/plugin>" pom.xml
    else
        # Create build section
        sed -i.bak "/<\/project>/i \\
\\
    <build>\\
        <plugins>\\
            <plugin>\\
                <groupId>com.example<\/groupId>\\
                <artifactId>orchestrator-maven-plugin<\/artifactId>\\
                <version>1.0.0<\/version>\\
            <\/plugin>\\
        <\/plugins>\\
    <\/build>" pom.xml
    fi
    rm pom.xml.bak
    echo "✓ Plugin dependency added"
else
    echo "✓ Plugin dependency already configured"
fi
echo ""

# Step 4: Configure Maven settings
echo "Step 4: Configuring Maven authentication..."
SETTINGS_FILE="${HOME}/.m2/settings.xml"

if [ ! -f "$SETTINGS_FILE" ]; then
    mkdir -p "${HOME}/.m2"
    
    if [ -z "$GITHUB_USERNAME" ]; then
        echo ""
        echo "⚠️  GitHub credentials not provided."
        echo ""
        echo "Option A: Set environment variables and re-run:"
        echo "  export MAVEN_USERNAME=YOUR-USERNAME"
        echo "  export MAVEN_PASSWORD=ghp_YOUR-TOKEN"
        echo "  $0 $ORG"
        echo ""
        echo "Option B: Get template and edit manually:"
        cp templates/settings.xml "$SETTINGS_FILE"
        echo "✓ Template created at: $SETTINGS_FILE"
        echo "  Edit with your GitHub username and personal access token"
        echo "  Visit: https://github.com/settings/tokens"
    else
        # Create settings.xml with credentials
        cat > "$SETTINGS_FILE" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
    <servers>
        <server>
            <id>github</id>
            <username>$GITHUB_USERNAME</username>
            <password>$GITHUB_TOKEN</password>
        </server>
    </servers>
</settings>
EOF
        chmod 600 "$SETTINGS_FILE"
        echo "✓ Maven settings configured at: $SETTINGS_FILE"
    fi
else
    echo "✓ Maven settings already exist at: $SETTINGS_FILE"
fi
echo ""

# Step 5: Verify setup
echo "Step 5: Verifying Maven setup..."
mvn help:describe -Dplugin=com.example:orchestrator-maven-plugin 2>/dev/null || {
    echo "⚠️  Plugin not yet accessible (normal if first time)"
    echo "  First download:"
    echo "  mvn dependency:resolve -DgroupId=com.example -DartifactId=orchestrator-maven-plugin"
}
echo ""

# Step 6: Test run
echo "Step 6: Ready to test!"
echo ""
echo "✅ Setup complete!"
echo ""
echo "Next step: Test the plugin"
echo "  mvn orchestrator:test -Dclass=com.example.MyClass"
echo ""
echo "Need to test your whole org's repos?"
echo "  Repeat this setup in each repo (steps 2-3 are copy-paste)"
echo ""
