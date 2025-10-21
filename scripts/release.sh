#!/bin/bash
# Create a new release of org-roam-ai
# This script tags the release, builds artifacts, and prepares for Gitea release

set -e

if [ -z "$1" ]; then
    echo "Usage: ./release.sh VERSION"
    echo "Example: ./release.sh 0.1.0"
    exit 1
fi

VERSION=$1
TAG="v${VERSION}"

cd "$(dirname "$0")/.."

echo "Creating release ${TAG}..."
echo ""

# Check for uncommitted changes
if [ -n "$(git status --porcelain)" ]; then
    echo "Error: You have uncommitted changes. Commit them first."
    exit 1
fi

# Update versions
echo "1. Updating version numbers..."

# Update MCP version
sed -i "s/^version = \".*\"/version = \"${VERSION}\"/" mcp/pyproject.toml
echo "   ✓ Updated mcp/pyproject.toml"

# Update Agent version
sed -i "s/<version>.*-SNAPSHOT<\/version>/<version>${VERSION}<\/version>/" agent/pom.xml
echo "   ✓ Updated agent/pom.xml"

# Update Emacs version
sed -i "s/;; Version: .*/;; Version: ${VERSION}/" emacs/org-roam-vector-search.el
echo "   ✓ Updated emacs/org-roam-vector-search.el"

echo ""

# Build artifacts
echo "2. Building artifacts..."

# Build MCP
echo "   Building MCP package..."
cd mcp
python -m build
cd ..
echo "   ✓ MCP package built"

# Build Agent
echo "   Building Agent JAR..."
cd agent
mvn clean package -DskipTests
cp target/embabel-note-gardener-${VERSION}.jar \
   ../release/embabel-note-gardener-${VERSION}.jar || \
cp target/embabel-note-gardener-*-SNAPSHOT.jar \
   ../release/embabel-note-gardener-${VERSION}.jar
cd ..
echo "   ✓ Agent JAR built"

echo ""

# Commit version changes
echo "3. Committing version changes..."
git add mcp/pyproject.toml agent/pom.xml emacs/org-roam-vector-search.el
git commit -m "Release ${TAG}"
echo "   ✓ Changes committed"

echo ""

# Create git tag
echo "4. Creating git tag..."
git tag -a "${TAG}" -m "Release ${TAG}"
echo "   ✓ Tag ${TAG} created"

echo ""
echo "=========================================="
echo "Release ${TAG} prepared!"
echo "=========================================="
echo ""
echo "Next steps:"
echo ""
echo "1. Push changes and tag:"
echo "   git push origin main"
echo "   git push origin ${TAG}"
echo ""
echo "2. Create Gitea release:"
echo "   - Go to: https://gitea-backend.cruver.network/dcruver/org-roam-ai/releases/new"
echo "   - Tag: ${TAG}"
echo "   - Upload: release/embabel-note-gardener-${VERSION}.jar"
echo ""
echo "3. Publish MCP to Gitea PyPI:"
echo "   ./scripts/publish-mcp.sh"
echo ""
echo "Release artifacts:"
echo "  - MCP: mcp/dist/org_roam_mcp-${VERSION}.tar.gz"
echo "  - MCP: mcp/dist/org_roam_mcp-${VERSION}-py3-none-any.whl"
echo "  - Agent: release/embabel-note-gardener-${VERSION}.jar"
echo ""
