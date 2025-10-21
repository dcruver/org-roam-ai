#!/bin/bash
# Complete installation script for org-roam-ai
# This script installs all three components and sets up systemd services

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
INSTALL_DIR="/opt/org-roam-agent"
AGENT_VERSION="0.1.0"
GITEA_URL="https://gitea-backend.cruver.network/dcruver/org-roam-ai"
ORG_ROAM_PATH="${HOME}/org-roam"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}org-roam-ai Installation Script${NC}"
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

    if ! command -v java >/dev/null 2>&1; then
        echo -e "${RED}✗ Java not found${NC}"
        missing=1
    else
        java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
        if [ "$java_version" -ge 21 ]; then
            echo -e "${GREEN}✓ Java 21+ found${NC}"
        else
            echo -e "${RED}✗ Java 21+ required (found Java $java_version)${NC}"
            missing=1
        fi
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

    # Install from git subdirectory
    pip3 install --user "git+${GITEA_URL}.git#subdirectory=mcp"

    if command -v org-roam-mcp >/dev/null 2>&1; then
        echo -e "${GREEN}✓ MCP server installed successfully${NC}"
    else
        echo -e "${RED}✗ MCP server installation failed${NC}"
        exit 1
    fi

    echo ""
}

# Function to install Agent
install_agent() {
    echo -e "${YELLOW}Installing Agent...${NC}"

    # Create install directory
    sudo mkdir -p ${INSTALL_DIR}
    sudo chown $USER:$USER ${INSTALL_DIR}

    # Download JAR from releases (or use local build)
    if [ -f "agent/target/embabel-note-gardener-0.1.0-SNAPSHOT.jar" ]; then
        echo -e "${YELLOW}Using local build${NC}"
        cp agent/target/embabel-note-gardener-0.1.0-SNAPSHOT.jar \
           ${INSTALL_DIR}/embabel-note-gardener.jar
    else
        echo -e "${YELLOW}Downloading from Gitea releases...${NC}"
        wget "${GITEA_URL}/releases/download/v${AGENT_VERSION}/embabel-note-gardener-${AGENT_VERSION}.jar" \
          -O ${INSTALL_DIR}/embabel-note-gardener.jar 2>/dev/null || {
            echo -e "${RED}✗ Failed to download from releases. Build locally first.${NC}"
            exit 1
        }
    fi

    echo -e "${GREEN}✓ Agent installed successfully${NC}"
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

    # Agent service
    sudo tee /etc/systemd/system/org-roam-agent.service > /dev/null <<EOF
[Unit]
Description=Org-roam Maintenance Agent
After=network.target org-roam-mcp.service ollama.service
Requires=org-roam-mcp.service

[Service]
Type=simple
User=$USER
Group=$USER
WorkingDirectory=${INSTALL_DIR}
Environment="ORG_ROAM_PATH=${ORG_ROAM_PATH}"
Environment="OLLAMA_BASE_URL=http://localhost:11434"
Environment="MCP_BASE_URL=http://localhost:8000"
ExecStart=/usr/bin/java -jar ${INSTALL_DIR}/embabel-note-gardener.jar --spring.profiles.active=dry-run
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

    echo -e "${GREEN}✓ Created org-roam-agent.service${NC}"
    echo ""
}

# Function to enable services
enable_services() {
    echo -e "${YELLOW}Enabling systemd services...${NC}"

    sudo systemctl daemon-reload
    sudo systemctl enable org-roam-mcp.service
    sudo systemctl enable org-roam-agent.service

    echo -e "${GREEN}✓ Services enabled${NC}"
    echo ""
}

# Main installation
main() {
    check_prerequisites
    install_mcp
    install_agent
    create_services
    enable_services

    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}Installation complete!${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    echo -e "${YELLOW}Next steps:${NC}"
    echo ""
    echo "1. Pull required Ollama models:"
    echo "   ollama pull nomic-embed-text:latest"
    echo "   ollama pull gpt-oss:20b"
    echo ""
    echo "2. Ensure Emacs server is running:"
    echo "   emacsclient --eval '(server-start)'"
    echo ""
    echo "3. Start the services:"
    echo "   sudo systemctl start org-roam-mcp.service"
    echo "   sudo systemctl start org-roam-agent.service"
    echo ""
    echo "4. Check service status:"
    echo "   sudo systemctl status org-roam-mcp.service"
    echo "   sudo systemctl status org-roam-agent.service"
    echo ""
    echo "5. View logs:"
    echo "   sudo journalctl -u org-roam-mcp.service -f"
    echo "   sudo journalctl -u org-roam-agent.service -f"
    echo ""
    echo -e "${YELLOW}Configuration:${NC}"
    echo "  MCP Server: http://localhost:8000"
    echo "  Agent: Interactive shell or systemd service"
    echo "  Org-roam path: ${ORG_ROAM_PATH}"
    echo ""
}

# Run main installation
main
