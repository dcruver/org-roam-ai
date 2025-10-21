#!/bin/bash
# Update an existing org-roam-ai deployment
# This script updates MCP and Agent to the latest versions

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

INSTALL_DIR="/opt/org-roam-agent"
VERSION="${1:-latest}"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Updating org-roam-ai${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Update MCP server
update_mcp() {
    echo -e "${YELLOW}Updating MCP server...${NC}"

    # Stop service if running
    if systemctl is-active --quiet org-roam-mcp.service; then
        echo "  Stopping org-roam-mcp service..."
        sudo systemctl stop org-roam-mcp.service
    fi

    # Uninstall old version
    pip3 uninstall -y org-roam-mcp 2>/dev/null || true

    # Install new version
    if [ "$VERSION" = "latest" ]; then
        pip3 install --user --upgrade \
          git+https://gitea-backend.cruver.network/dcruver/org-roam-ai.git#subdirectory=mcp
    else
        pip3 install --user \
          "org-roam-mcp==${VERSION}" \
          --index-url https://gitea-backend.cruver.network/api/packages/dcruver/pypi/simple
    fi

    echo -e "${GREEN}✓ MCP server updated${NC}"
}

# Update Agent
update_agent() {
    echo -e "${YELLOW}Updating Agent...${NC}"

    # Stop service if running
    if systemctl is-active --quiet org-roam-agent.service; then
        echo "  Stopping org-roam-agent service..."
        sudo systemctl stop org-roam-agent.service
    fi

    # Backup current JAR
    if [ -f "${INSTALL_DIR}/embabel-note-gardener.jar" ]; then
        echo "  Backing up current JAR..."
        sudo cp "${INSTALL_DIR}/embabel-note-gardener.jar" \
                "${INSTALL_DIR}/embabel-note-gardener.jar.backup"
    fi

    # Download new version
    if [ "$VERSION" = "latest" ]; then
        # Get latest release
        echo "  Fetching latest release..."
        LATEST_URL=$(curl -s https://gitea-backend.cruver.network/api/v1/repos/dcruver/org-roam-ai/releases | \
                     jq -r '.[0].assets[] | select(.name | endswith(".jar")) | .browser_download_url')

        if [ -z "$LATEST_URL" ]; then
            echo -e "${RED}✗ Could not find latest release${NC}"
            exit 1
        fi

        sudo wget "$LATEST_URL" -O "${INSTALL_DIR}/embabel-note-gardener.jar"
    else
        RELEASE_URL="https://gitea-backend.cruver.network/dcruver/org-roam-ai/releases/download/v${VERSION}/embabel-note-gardener-${VERSION}.jar"
        sudo wget "$RELEASE_URL" -O "${INSTALL_DIR}/embabel-note-gardener.jar"
    fi

    echo -e "${GREEN}✓ Agent updated${NC}"
}

# Restart services
restart_services() {
    echo -e "${YELLOW}Restarting services...${NC}"

    sudo systemctl start org-roam-mcp.service
    sleep 2  # Give MCP time to start

    sudo systemctl start org-roam-agent.service

    echo -e "${GREEN}✓ Services restarted${NC}"
}

# Check status
check_status() {
    echo ""
    echo -e "${YELLOW}Checking service status...${NC}"

    if systemctl is-active --quiet org-roam-mcp.service; then
        echo -e "${GREEN}✓ MCP server running${NC}"
    else
        echo -e "${RED}✗ MCP server not running${NC}"
        echo "  Check logs: sudo journalctl -u org-roam-mcp.service -n 50"
    fi

    if systemctl is-active --quiet org-roam-agent.service; then
        echo -e "${GREEN}✓ Agent running${NC}"
    else
        echo -e "${RED}✗ Agent not running${NC}"
        echo "  Check logs: sudo journalctl -u org-roam-agent.service -n 50"
    fi
}

# Main update
main() {
    update_mcp
    echo ""
    update_agent
    echo ""
    restart_services
    check_status

    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}Update complete!${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    echo "View logs:"
    echo "  sudo journalctl -u org-roam-mcp.service -f"
    echo "  sudo journalctl -u org-roam-agent.service -f"
    echo ""
}

# Run main update
main
