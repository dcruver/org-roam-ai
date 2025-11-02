#!/bin/bash
# Publish MCP server to GitHub Packages

set -e

cd "$(dirname "$0")/.."

echo "Publishing org-roam-mcp to GitHub Packages..."
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
    github

[github]
repository = https://pypi.pkg.github.com/dcruver
username = dcruver
password = YOUR_GITHUB_TOKEN
EOF
    echo ""
    exit 1
fi

# Build the package
echo "Building package..."
cd mcp

# Create temporary virtual environment for build tools
python3 -m venv /tmp/mcp-build-env
/tmp/mcp-build-env/bin/pip install build twine

# Build and upload using the virtual environment
/tmp/mcp-build-env/bin/python -m build
/tmp/mcp-build-env/bin/python -m twine upload --repository github dist/*

echo ""
echo "✓ Package published successfully"
echo ""
echo "Install with:"
echo "  pip install org-roam-mcp --index-url https://pypi.pkg.github.com/dcruver"