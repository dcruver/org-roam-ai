#!/bin/bash
# Uninstall script for org-roam-ai MCP server
# Removes all components installed by the installation script

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration (matches install script)
INSTALL_DIR="${INSTALL_DIR:-/opt/org-roam-ai}"
VENV_DIR="${INSTALL_DIR}/mcp/.venv"
SERVICE_NAME="org-roam-mcp"
EMACS_CONFIG_FILE="$HOME/.emacs.d/init-org-roam-ai.el"

# Parse command line arguments
SKIP_CONFIRMATION=false
while [[ $# -gt 0 ]]; do
    case $1 in
        --yes|-y)
            SKIP_CONFIRMATION=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [--yes|-y]"
            exit 1
            ;;
    esac
done

echo -e "${RED}========================================${NC}"
echo -e "${RED}org-roam-ai MCP Server Uninstallation${NC}"
echo -e "${RED}========================================${NC}"
echo ""

# Function to prompt for confirmation
confirm() {
    local message="$1"

    # If confirmation is skipped, always return true
    if [ "$SKIP_CONFIRMATION" = true ]; then
        echo -e "${YELLOW}$message${NC}"
        echo -e "${GREEN}Confirmation skipped (--yes flag used)${NC}"
        return 0
    fi

    # Check if running interactively (stdin is a terminal)
    if [ -t 0 ]; then
        echo -e "${YELLOW}$message${NC}"
        read -p "Continue? (y/N): " -n 1 -r
        echo
        [[ $REPLY =~ ^[Yy]$ ]]
    else
        echo -e "${YELLOW}$message${NC}"
        echo -e "${YELLOW}Running non-interactively. Use --yes flag to skip confirmation.${NC}"
        return 1  # Default to no when non-interactive
    fi
}

# Function to stop and disable systemd service
stop_service() {
    echo -e "${YELLOW}Stopping and disabling systemd service...${NC}"

    if systemctl is-active --quiet ${SERVICE_NAME}.service 2>/dev/null; then
        sudo systemctl stop ${SERVICE_NAME}.service
        echo -e "${GREEN}✓ Service stopped${NC}"
    else
        echo -e "${YELLOW}⚠ Service not running${NC}"
    fi

    if systemctl is-enabled --quiet ${SERVICE_NAME}.service 2>/dev/null; then
        sudo systemctl disable ${SERVICE_NAME}.service
        echo -e "${GREEN}✓ Service disabled${NC}"
    else
        echo -e "${YELLOW}⚠ Service not enabled${NC}"
    fi

    echo ""
}

# Function to remove systemd service file
remove_service_file() {
    echo -e "${YELLOW}Removing systemd service file...${NC}"

    if [ -f /etc/systemd/system/${SERVICE_NAME}.service ]; then
        sudo rm /etc/systemd/system/${SERVICE_NAME}.service
        sudo systemctl daemon-reload
        echo -e "${GREEN}✓ Service file removed${NC}"
    else
        echo -e "${YELLOW}⚠ Service file not found${NC}"
    fi

    echo ""
}

# Function to remove Python virtual environment
remove_venv() {
    echo -e "${YELLOW}Removing Python virtual environment...${NC}"

    if [ -d "$VENV_DIR" ]; then
        rm -rf "$VENV_DIR"
        echo -e "${GREEN}✓ Virtual environment removed${NC}"
    else
        echo -e "${YELLOW}⚠ Virtual environment not found${NC}"
    fi

    echo ""
}

# Function to remove installation directory
remove_install_dir() {
    echo -e "${YELLOW}Removing installation directory...${NC}"

    if [ -d "$INSTALL_DIR" ]; then
        sudo rm -rf "$INSTALL_DIR"
        echo -e "${GREEN}✓ Installation directory removed${NC}"
    else
        echo -e "${YELLOW}⚠ Installation directory not found${NC}"
    fi

    echo ""
}

# Function to remove Emacs configuration
remove_emacs_config() {
    echo -e "${YELLOW}Removing Emacs configuration...${NC}"

    # Remove the MCP config file
    if [ -f "$EMACS_CONFIG_FILE" ]; then
        rm "$EMACS_CONFIG_FILE"
        echo -e "${GREEN}✓ Emacs config file removed${NC}"
    else
        echo -e "${YELLOW}⚠ Emacs config file not found${NC}"
    fi

    # Remove the load line from init.el
    if [ -f "$HOME/.emacs.d/init.el" ]; then
        if grep -q "init-org-roam-mcp.el" "$HOME/.emacs.d/init.el"; then
            sed -i '/init-org-roam-mcp\.el/d' "$HOME/.emacs.d/init.el"
            echo -e "${GREEN}✓ Removed MCP config from init.el${NC}"
        else
            echo -e "${YELLOW}⚠ MCP config not found in init.el${NC}"
        fi
    else
        echo -e "${YELLOW}⚠ init.el not found${NC}"
    fi

    echo ""
}

# Function to clean up any remaining artifacts
cleanup_artifacts() {
    echo -e "${YELLOW}Checking for remaining artifacts...${NC}"

    local found_artifacts=false

    # Check for any org-roam-mcp related files
    if find "$HOME" -name "*org-roam-mcp*" -type f 2>/dev/null | grep -q .; then
        echo -e "${YELLOW}Found additional org-roam-mcp files:${NC}"
        find "$HOME" -name "*org-roam-mcp*" -type f 2>/dev/null
        found_artifacts=true
    fi

    # Check for org-roam-mcp directories
    if find "$HOME" -name "*org-roam-mcp*" -type d 2>/dev/null | grep -q .; then
        echo -e "${YELLOW}Found additional org-roam-mcp directories:${NC}"
        find "$HOME" -name "*org-roam-mcp*" -type d 2>/dev/null
        found_artifacts=true
    fi

    if [ "$found_artifacts" = false ]; then
        echo -e "${GREEN}✓ No additional artifacts found${NC}"
    fi

    echo ""
}

# Main uninstallation
main() {
    echo -e "${YELLOW}This will completely remove the org-roam-ai MCP server installation.${NC}"
    echo -e "${YELLOW}The following will be removed:${NC}"
    echo "  - Installation directory: ${INSTALL_DIR}"
    echo "  - Systemd service: ${SERVICE_NAME}.service"
    echo "  - Virtual environment: ${VENV_DIR}"
    echo "  - Emacs config: ${EMACS_CONFIG_FILE}"
    echo "  - MCP config from: ~/.emacs.d/init.el"
    echo ""

    if [ "$SKIP_CONFIRMATION" = false ] && ! confirm "Are you sure you want to proceed with uninstallation?"; then
        echo -e "${GREEN}Uninstallation cancelled.${NC}"
        exit 0
    fi

    stop_service
    remove_service_file
    remove_venv
    remove_install_dir
    remove_emacs_config
    cleanup_artifacts

    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}Uninstallation complete!${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    echo -e "${YELLOW}Note: You may want to restart Emacs to ensure all changes take effect.${NC}"
    echo -e "${YELLOW}If you had any custom configurations in the MCP config file, they are now lost.${NC}"
    echo ""
}

# Run main uninstallation
main