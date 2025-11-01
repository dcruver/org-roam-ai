#!/bin/bash
# Create a new release of org-roam-ai MCP server
# This script tags the release, builds MCP artifacts, and prepares for GitHub release

set -e

if [ -z "$1" ]; then
    echo "Usage: ./release.sh VERSION"
    echo "Example: ./release.sh 0.1.0"
    exit 1
fi

VERSION=$1
TAG="v${VERSION}"

cd "$(dirname "$0")/.."

echo "Creating MCP server release ${TAG}..."
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



# Update Emacs package versions
sed -i "s/;; Version: .*/;; Version: ${VERSION}/" packages/org-roam-ai/org-roam-vector-search.el
sed -i "s/;; Version: .*/;; Version: ${VERSION}/" packages/org-roam-ai/org-roam-ai-assistant.el
sed -i "s/;; Version: .*/;; Version: ${VERSION}/" packages/org-roam-ai/org-roam-api.el
echo "   ✓ Updated Emacs package versions"

echo ""

# Build artifacts
echo "2. Building artifacts..."

# Build MCP
echo "   Building MCP package..."
cd mcp
python -m build
cd ..
echo "   ✓ MCP package built"



echo ""

# Commit version changes
echo "3. Committing version changes..."
git add mcp/pyproject.toml packages/org-roam-ai/*.el
git commit -m "Release ${TAG}"
echo "   ✓ Changes committed"

echo ""

# Create git tag
echo "4. Creating git tag..."
git tag -a "${TAG}" -m "Release ${TAG}"
echo "   ✓ Tag ${TAG} created"

echo ""
echo "=========================================="
echo "MCP Server release ${TAG} prepared!"
echo "=========================================="
echo ""
echo "Next steps:"
echo ""
echo "1. Push changes and tag:"
echo "   git push origin main"
echo "   git push origin ${TAG}"
echo ""
echo "2. Emacs packages:"
echo "   - Available directly from monorepo via straight.el :files parameter"
echo "   - No separate publishing needed"
echo ""
echo "3. Publish MCP to GitHub Packages:"
echo "   ./scripts/publish-mcp.sh"
echo ""
echo "4. Create GitHub release:"
echo "   - Go to: https://github.com/dcruver/org-roam-ai/releases/new"
echo "   - Tag: ${TAG}"
echo ""
echo "Release artifacts:"
echo "  - MCP: mcp/dist/org_roam_mcp-${VERSION}.tar.gz"
echo "  - MCP: mcp/dist/org_roam_mcp-${VERSION}-py3-none-any.whl"
echo "  - Emacs packages: Available directly from monorepo"
echo ""
