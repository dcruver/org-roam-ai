#!/bin/bash
# Publish MCP server to Gitea PyPI registry

set -e

cd "$(dirname "$0")/.."

echo "Publishing org-roam-mcp to Gitea PyPI registry..."
echo ""

# Check if .pypirc exists
if [ ! -f ~/.pypirc ]; then
    echo "Error: ~/.pypirc not found"
    echo ""
    echo "Create ~/.pypirc with the following content:"
    echo ""
    cat <<EOF
[distutils]
index-servers =
    gitea

[gitea]
repository = https://gitea-backend.cruver.network/api/packages/dcruver/pypi
username = dcruver
password = YOUR_GITEA_TOKEN
EOF
    echo ""
    exit 1
fi

# Build the package
echo "Building package..."
cd mcp
python -m build

# Upload to Gitea
echo "Uploading to Gitea..."
python -m twine upload --repository gitea dist/*

echo ""
echo "✓ Package published successfully"
echo ""
echo "Install with:"
echo "  pip install org-roam-mcp --index-url https://gitea-backend.cruver.network/api/packages/dcruver/pypi/simple"
