#!/bin/bash
# Installation script for org-roam-ai MCP server
# This script installs the MCP server and sets up systemd service

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
GITEA_URL="https://gitea-backend.cruver.network/dcruver/org-roam-ai"
ORG_ROAM_PATH="${HOME}/org-roam"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}org-roam-ai MCP Server Installation${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Function to check prerequisites
check_prerequisites() {
    echo -e "${YELLOW}Checking prerequisites...${NC}"

    local missing=0

    if ! command -v python3 >/dev/null 2>&1; then
        echo -e "${RED}✗ Python 3 not found${NC}"
        missing=1
    else
        echo -e "${GREEN}✓ Python 3 found${NC}"
    fi

    if ! command -v pip3 >/dev/null 2>&1; then
        echo -e "${RED}✗ pip3 not found${NC}"
        missing=1
    else
        echo -e "${GREEN}✓ pip3 found${NC}"
    fi



    if ! command -v ollama >/dev/null 2>&1; then
        echo -e "${RED}✗ Ollama not found${NC}"
        missing=1
    else
        echo -e "${GREEN}✓ Ollama found${NC}"
    fi

    if ! emacsclient --eval '(+ 1 1)' >/dev/null 2>&1; then
        echo -e "${YELLOW}⚠ Emacs server not running (will be needed for MCP)${NC}"
    else
        echo -e "${GREEN}✓ Emacs server running${NC}"
    fi

    if [ $missing -eq 1 ]; then
        echo -e "${RED}Missing prerequisites. Please install them first.${NC}"
        exit 1
    fi

    echo ""
}

# Function to install MCP server
install_mcp() {
    echo -e "${YELLOW}Installing MCP server...${NC}"

    # Check if already installed
    if command -v org-roam-mcp >/dev/null 2>&1; then
        echo -e "${YELLOW}MCP server already installed. Reinstalling...${NC}"
        pip3 uninstall -y org-roam-mcp
    fi

    # Install from PyPI
    pip3 install --user --upgrade org-roam-mcp

    if command -v org-roam-mcp >/dev/null 2>&1; then
        echo -e "${GREEN}✓ MCP server installed successfully${NC}"
    else
        echo -e "${RED}✗ MCP server installation failed${NC}"
        exit 1
    fi

    echo ""
}



# Function to create systemd services
create_services() {
    echo -e "${YELLOW}Creating systemd services...${NC}"

    # MCP service
    sudo tee /etc/systemd/system/org-roam-mcp.service > /dev/null <<EOF
[Unit]
Description=Org-roam MCP Server
After=network.target

[Service]
Type=simple
User=$USER
Group=$USER
WorkingDirectory=$HOME
Environment="PATH=$HOME/.local/bin:/usr/local/bin:/usr/bin"
Environment="EMACS_SERVER_FILE=$HOME/.emacs.d/server/server"
ExecStart=$HOME/.local/bin/org-roam-mcp
Restart=on-failure
RestartSec=5
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

    echo -e "${GREEN}✓ Created org-roam-mcp.service${NC}"
    echo ""
}

# Function to enable services
enable_services() {
    echo -e "${YELLOW}Enabling systemd services...${NC}"

    sudo systemctl daemon-reload
    sudo systemctl enable org-roam-mcp.service

    echo -e "${GREEN}✓ Service enabled${NC}"
    echo ""
}

# Main installation
main() {
    check_prerequisites
    install_mcp
    create_services
    enable_services

    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}MCP Server installation complete!${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    echo -e "${YELLOW}Next steps:${NC}"
    echo ""
    echo "1. Pull required Ollama models:"
    echo "   ollama pull nomic-embed-text:latest"
    echo "   ollama pull llama3.1:8b"
    echo ""
    echo "2. Ensure Emacs server is running:"
    echo "   emacsclient --eval '(server-start)'"
    echo ""
    echo "3. Start the service:"
    echo "   sudo systemctl start org-roam-mcp.service"
    echo ""
    echo "4. Check service status:"
    echo "   sudo systemctl status org-roam-mcp.service"
    echo ""
    echo "5. View logs:"
    echo "   sudo journalctl -u org-roam-mcp.service -f"
    echo ""
    echo -e "${YELLOW}Configuration:${NC}"
    echo "  MCP Server: http://localhost:8000"
    echo "  Org-roam path: ${ORG_ROAM_PATH}"
    echo ""
}

# Run main installation
main
