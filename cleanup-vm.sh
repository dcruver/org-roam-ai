#!/bin/bash
# Cleanup script for org-roam-agent-backend.cruver.network
# Removes old installation and sets up fresh installation

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}org-roam-ai VM Cleanup & Fresh Install${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Function to stop and disable services
cleanup_services() {
    echo -e "${YELLOW}Stopping and removing old services...${NC}"

    # Stop services if running
    sudo systemctl stop org-roam-mcp.service 2>/dev/null || true
    sudo systemctl stop org-roam-agent-audit.service 2>/dev/null || true

    # Disable services
    sudo systemctl disable org-roam-mcp.service 2>/dev/null || true
    sudo systemctl disable org-roam-agent-audit.service 2>/dev/null || true

    # Remove service files
    sudo rm -f /etc/systemd/system/org-roam-mcp.service
    sudo rm -f /etc/systemd/system/org-roam-agent-audit.service

    # Reload systemd
    sudo systemctl daemon-reload

    echo -e "${GREEN}✓ Old services cleaned up${NC}"
    echo ""
}

# Function to remove old installations
cleanup_old_installations() {
    echo -e "${YELLOW}Removing old installations...${NC}"

    # Remove old virtual environments
    sudo rm -rf /opt/org-roam-mcp-venv
    sudo rm -rf /opt/org-roam-agent

    # Remove old git clones if any
    sudo rm -rf /tmp/org-roam-mcp
    sudo rm -rf /tmp/org-roam-agent

    # Remove old pip installations
    pip3 uninstall -y org-roam-mcp 2>/dev/null || true

    echo -e "${GREEN}✓ Old installations removed${NC}"
    echo ""
}

# Function to clean up Emacs configuration
cleanup_emacs_config() {
    echo -e "${YELLOW}Cleaning up Emacs configuration...${NC}"

    # Remove old org-roam packages from Doom
    rm -rf ~/.emacs.d/.local/straight/repos/org-roam-vector-search 2>/dev/null || true
    rm -rf ~/.emacs.d/.local/straight/repos/org-roam-ai-assistant 2>/dev/null || true
    rm -rf ~/.emacs.d/.local/straight/repos/org-roam-api 2>/dev/null || true

    # Remove old init file if it exists
    rm -f ~/.emacs.d/init-org-roam-mcp.el

    # Clean straight.el build cache
    rm -rf ~/.emacs.d/.local/straight/build-*/org-roam-* 2>/dev/null || true

    echo -e "${GREEN}✓ Emacs configuration cleaned${NC}"
    echo ""
}

# Function to clean up org-roam database
cleanup_org_roam_db() {
    echo -e "${YELLOW}Cleaning up org-roam database...${NC}"

    # Find and remove org-roam.db files
    find ~ -name "org-roam.db*" -type f -delete 2>/dev/null || true

    echo -e "${GREEN}✓ Org-roam database cleaned${NC}"
    echo ""
}

# Function to perform fresh installation
fresh_install() {
    echo -e "${YELLOW}Performing fresh installation...${NC}"

    # Download and run the installation script
    echo -e "${YELLOW}Running one-shot installation...${NC}"
    curl -fsSL https://raw.githubusercontent.com/dcruver/org-roam-ai/main/install.sh | bash

    echo -e "${GREEN}✓ Fresh installation completed${NC}"
    echo ""
}

# Function to verify installation
verify_installation() {
    echo -e "${YELLOW}Verifying installation...${NC}"

    # Check if MCP server is installed
    if command -v org-roam-mcp >/dev/null 2>&1; then
        echo -e "${GREEN}✓ MCP server installed${NC}"
    else
        echo -e "${RED}✗ MCP server not found${NC}"
    fi

    # Check if service exists
    if sudo systemctl list-units --full -all | grep -q org-roam-mcp.service; then
        echo -e "${GREEN}✓ Systemd service configured${NC}"
    else
        echo -e "${RED}✗ Systemd service not found${NC}"
    fi

    # Check Emacs server
    if emacsclient --eval '(+ 1 1)' >/dev/null 2>&1; then
        echo -e "${GREEN}✓ Emacs server running${NC}"
    else
        echo -e "${YELLOW}⚠ Emacs server not running${NC}"
    fi

    echo ""
}

# Main cleanup process
main() {
    echo -e "${YELLOW}Starting cleanup process...${NC}"
    echo ""

    cleanup_services
    cleanup_old_installations
    cleanup_emacs_config
    cleanup_org_roam_db

    echo -e "${GREEN}Cleanup completed. Ready for fresh installation.${NC}"
    echo ""

    read -p "Do you want to perform fresh installation now? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        fresh_install
        verify_installation
    else
        echo -e "${YELLOW}Skipping fresh installation. You can run it manually later.${NC}"
        echo ""
        echo -e "${YELLOW}To install manually:${NC}"
        echo "curl -fsSL https://raw.githubusercontent.com/dcruver/org-roam-ai/main/install.sh | bash"
    fi

    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}Cleanup process completed!${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    echo -e "${YELLOW}Next steps:${NC}"
    echo "1. Ensure Emacs server is running: emacs --daemon"
    echo "2. Start the service: sudo systemctl start org-roam-mcp.service"
    echo "3. Check logs: sudo journalctl -u org-roam-mcp.service -f"
    echo ""
}

# Run main cleanup
main