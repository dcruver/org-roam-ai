#!/bin/bash
set -e

# org-roam-ai Stack Installation Script
# Installs Emacs, org-roam, MCP server, and GOAP agent

# Configuration
INSTALL_DIR="${INSTALL_DIR:-/opt/org-roam-ai}"
ORG_ROAM_PATH="${ORG_ROAM_PATH:-$HOME/org-roam}"
GITEA_TOKEN="${GITEA_TOKEN:-}"
GITEA_USER="dcruver"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo_info() { echo -e "${GREEN}ℹ️  $1${NC}"; }
echo_warn() { echo -e "${YELLOW}⚠️  $1${NC}"; }
echo_error() { echo -e "${RED}❌ $1${NC}"; }
echo_success() { echo -e "${GREEN}✅ $1${NC}"; }

# Check if running as root
if [ "$EUID" -eq 0 ]; then
    echo_error "Please do not run as root. The script will use sudo when needed."
    exit 1
fi

echo_info "Installing org-roam-ai stack to ${INSTALL_DIR}"
echo ""

# ============================================================================
# 1. Check Prerequisites
# ============================================================================
echo_info "Step 1/6: Checking prerequisites..."

# Check for required commands
MISSING_DEPS=()
command -v curl >/dev/null 2>&1 || MISSING_DEPS+=("curl")
command -v git >/dev/null 2>&1 || MISSING_DEPS+=("git")

if [ ${#MISSING_DEPS[@]} -ne 0 ]; then
    echo_error "Missing required dependencies: ${MISSING_DEPS[*]}"
    echo_info "Install with: sudo apt install ${MISSING_DEPS[*]}"
    exit 1
fi

echo_success "Prerequisites OK"

# ============================================================================
# 2. Install Ollama
# ============================================================================
echo_info "Step 2/6: Installing Ollama..."

if command -v ollama >/dev/null 2>&1; then
    echo_warn "Ollama already installed"
else
    curl -fsSL https://ollama.com/install.sh | sh
    echo_success "Ollama installed"
fi

# Start Ollama service
if systemctl is-active --quiet ollama; then
    echo_info "Ollama service already running"
else
    sudo systemctl enable ollama
    sudo systemctl start ollama
    echo_success "Ollama service started"
fi

# Pull required models
echo_info "Pulling Ollama models (this may take a while)..."
ollama pull nomic-embed-text:latest
ollama pull llama3.1:8b  # or gpt-oss:20b if available

echo_success "Ollama configured"

# ============================================================================
# 3. Check Emacs and org-roam-semantic
# ============================================================================
echo_info "Step 3/6: Checking Emacs installation..."

if ! command -v emacs >/dev/null 2>&1; then
    echo_error "Emacs is not installed"
    echo ""
    echo_info "Please install Emacs first:"
    echo_info "  sudo apt install emacs"
    echo ""
    echo_info "Then configure org-roam and org-roam-semantic before running this script."
    echo_info "See: https://github.com/org-roam/org-roam"
    echo_info "     https://github.com/dcruver/org-roam-semantic"
    exit 1
fi

EMACS_VERSION=$(emacs --version | head -n1)
echo_info "Found: ${EMACS_VERSION}"

# Check if org-roam-semantic is installed
echo_info "Checking for org-roam-semantic package..."

# Try to check if org-roam-semantic is available in Emacs
if emacs --batch --eval "(require 'org-roam-semantic)" 2>/dev/null; then
    echo_success "org-roam-semantic is installed"
elif emacs --batch --eval "(require 'org-roam-vector-search)" 2>/dev/null; then
    echo_success "org-roam-vector-search is installed"
else
    echo_error "org-roam-semantic is not installed in Emacs"
    echo ""
    echo_info "Please install org-roam-semantic before running this script."
    echo ""
    echo_info "Installation instructions (via straight.el - recommended):"
    echo_info "Add to your Emacs init file:"
    echo ""
    echo_info "  (straight-use-package"
    echo_info "    '(org-roam-semantic :host github :repo \"dcruver/org-roam-semantic\"))"
    echo_info ""
    echo_info "  (require 'org-roam-vector-search)"
    echo_info "  (require 'org-roam-ai-assistant)"
    echo_info ""
    echo_info "  (customize-set-variable 'org-roam-semantic-ollama-url"
    echo_info "                          \"http://localhost:11434\")"
    echo ""
    echo_info "Restart Emacs and verify: M-x org-roam-semantic-status"
    echo_info "Full instructions: https://github.com/dcruver/org-roam-semantic"
    echo ""
    exit 1
fi

# Create org-roam directory
mkdir -p "${ORG_ROAM_PATH}"
echo_success "org-roam directory: ${ORG_ROAM_PATH}"

# Check if Emacs server is running
if pgrep -x "emacs" > /dev/null; then
    echo_info "Emacs process found"
else
    echo_warn "Emacs daemon not running"
    echo_info "You'll need to start Emacs and run (server-start)"
    echo_info "Or start Emacs daemon: emacs --daemon"
fi

# ============================================================================
# 4. Clone Repository
# ============================================================================
echo_info "Step 4/6: Cloning org-roam-ai repository..."

sudo mkdir -p "${INSTALL_DIR}"
sudo chown $USER:$USER "${INSTALL_DIR}"

if [ -d "${INSTALL_DIR}/.git" ]; then
    echo_info "Repository already cloned, pulling latest..."
    cd "${INSTALL_DIR}"
    git pull
else
    git clone gitea@gitea-backend.cruver.network:${GITEA_USER}/org-roam-ai.git "${INSTALL_DIR}"
fi

cd "${INSTALL_DIR}"
echo_success "Repository cloned/updated"

# ============================================================================
# 5. Install MCP Server (Python)
# ============================================================================
echo_info "Step 5/6: Installing MCP server..."

# Check Python version
if command -v python3 >/dev/null 2>&1; then
    PYTHON_VERSION=$(python3 --version)
    echo_info "Found: ${PYTHON_VERSION}"
else
    echo_error "Python 3 not found"
    exit 1
fi

# Create virtual environment
MCP_VENV="${INSTALL_DIR}/mcp/.venv"
if [ ! -d "${MCP_VENV}" ]; then
    cd "${INSTALL_DIR}/mcp"
    python3 -m venv .venv
    source .venv/bin/activate
    pip install --upgrade pip
    pip install -e ".[dev]"
    deactivate
    echo_success "MCP server installed"
else
    echo_warn "MCP venv already exists, skipping"
fi

# ============================================================================
# 6. Install Agent (Java)
# ============================================================================
echo_info "Step 6/6: Installing GOAP agent..."

# Check Java version
if command -v java >/dev/null 2>&1; then
    JAVA_VERSION=$(java -version 2>&1 | head -n1)
    echo_info "Found: ${JAVA_VERSION}"

    # Check if Java 21+
    JAVA_MAJOR=$(java -version 2>&1 | grep -oP 'version "\K\d+')
    if [ "$JAVA_MAJOR" -lt 21 ]; then
        echo_error "Java 21 or higher required (found Java ${JAVA_MAJOR})"
        echo_info "Install with: sudo apt install openjdk-21-jdk"
        exit 1
    fi
else
    echo_error "Java not found"
    echo_info "Install with: sudo apt install openjdk-21-jdk"
    exit 1
fi

# Download JAR from Gitea or build locally
cd "${INSTALL_DIR}/agent"

if [ -n "$GITEA_TOKEN" ]; then
    echo_info "Downloading agent JAR from Gitea..."
    curl -u "${GITEA_USER}:${GITEA_TOKEN}" \
        -o embabel-note-gardener.jar \
        "https://gitea.cruver.network/api/packages/${GITEA_USER}/maven/com/dcruver/embabel-note-gardener/0.1.0-SNAPSHOT/embabel-note-gardener-0.1.0-SNAPSHOT.jar"
    echo_success "Agent JAR downloaded"
else
    echo_warn "GITEA_TOKEN not set, building from source..."
    ./mvnw clean package -DskipTests
    cp target/embabel-note-gardener-*.jar embabel-note-gardener.jar
    echo_success "Agent JAR built"
fi

# ============================================================================
# Create Systemd Services
# ============================================================================
echo_info "Creating systemd services..."

# MCP Service
sudo tee /etc/systemd/system/org-roam-mcp.service > /dev/null <<EOF
[Unit]
Description=org-roam MCP Server
After=network.target

[Service]
Type=simple
User=$USER
WorkingDirectory=${INSTALL_DIR}/mcp
Environment="PATH=${MCP_VENV}/bin:/usr/local/bin:/usr/bin:/bin"
Environment="EMACS_SERVER_FILE=$HOME/emacs-server/server"
ExecStart=${MCP_VENV}/bin/python -m org_roam_mcp.server
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# Agent Service
sudo tee /etc/systemd/system/org-roam-agent.service > /dev/null <<EOF
[Unit]
Description=org-roam GOAP Agent
After=network.target org-roam-mcp.service
Requires=org-roam-mcp.service

[Service]
Type=simple
User=$USER
WorkingDirectory=${INSTALL_DIR}/agent
Environment="ORG_ROAM_PATH=${ORG_ROAM_PATH}"
Environment="OLLAMA_BASE_URL=http://localhost:11434"
ExecStart=/usr/bin/java -jar ${INSTALL_DIR}/agent/embabel-note-gardener.jar --spring.profiles.active=auto
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
echo_success "Systemd services created"

# ============================================================================
# Summary
# ============================================================================
echo ""
echo_success "=========================================="
echo_success "Installation Complete!"
echo_success "=========================================="
echo ""
echo_info "Installation Directory: ${INSTALL_DIR}"
echo_info "org-roam Notes: ${ORG_ROAM_PATH}"
echo ""
echo_info "Next Steps:"
echo ""
echo "1. Configure Emacs with org-roam and org-roam-semantic:"
echo "   - Add ${INSTALL_DIR}/emacs to your Emacs load-path"
echo "   - Load org-roam-vector-search.el and org-roam-ai-assistant.el"
echo "   - Start Emacs server: emacs --daemon"
echo ""
echo "2. Start services:"
echo "   sudo systemctl start org-roam-mcp"
echo "   sudo systemctl start org-roam-agent"
echo ""
echo "3. Enable services at boot:"
echo "   sudo systemctl enable org-roam-mcp"
echo "   sudo systemctl enable org-roam-agent"
echo ""
echo "4. Check status:"
echo "   sudo systemctl status org-roam-mcp"
echo "   sudo systemctl status org-roam-agent"
echo "   journalctl -u org-roam-mcp -f"
echo "   journalctl -u org-roam-agent -f"
echo ""
echo "5. Test MCP server:"
echo "   curl http://localhost:8000"
echo ""
echo "6. Test Ollama:"
echo "   ollama list"
echo "   curl http://localhost:11434/api/tags"
echo ""
echo_warn "Note: Make sure Emacs daemon is running and org-roam is configured!"
echo ""
