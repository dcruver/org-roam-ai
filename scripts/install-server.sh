#!/bin/bash
set -e

# org-roam-ai Installation Script
# Installs Emacs, org-roam, and MCP server with integrated packages

# Configuration
INSTALL_DIR="${INSTALL_DIR:-/opt/org-roam-ai}"
ORG_ROAM_PATH="${ORG_ROAM_PATH:-$HOME/org-roam}"
GITEA_TOKEN="${GITEA_TOKEN:-}"
GITEA_USER="dcruver"

# Ollama Configuration
OLLAMA_URL="${OLLAMA_URL:-http://localhost:11434}"
OLLAMA_EMBEDDING_MODEL="${OLLAMA_EMBEDDING_MODEL:-nomic-embed-text}"
OLLAMA_GENERATION_MODEL="${OLLAMA_GENERATION_MODEL:-llama3.1:8b}"
ENABLE_CHUNKING="${ENABLE_CHUNKING:-false}"
MIN_CHUNK_SIZE="${MIN_CHUNK_SIZE:-100}"

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
# 2. Check Ollama
# ============================================================================
echo_info "Step 2/6: Checking Ollama configuration..."

# Check if using remote Ollama server
if [ "$OLLAMA_URL" != "http://localhost:11434" ] && [ -n "$OLLAMA_URL" ]; then
    echo_info "Using remote Ollama server: $OLLAMA_URL"
    echo_info "Skipping local Ollama installation check"

    # Test remote Ollama connection
    if curl -s "$OLLAMA_URL/api/tags" >/dev/null 2>&1; then
        echo_success "Remote Ollama server is accessible"
    else
        echo_warn "Cannot connect to remote Ollama server at $OLLAMA_URL"
        echo_warn "Make sure the remote server is running and accessible"
    fi
else
    echo_info "Using local Ollama installation"

    if ! command -v ollama >/dev/null 2>&1; then
        echo_error "Ollama is not installed"
        echo ""
        echo_info "Please install Ollama first:"
        echo_info "  curl -fsSL https://ollama.com/install.sh | sh"
        echo_info "  sudo systemctl enable ollama"
        echo_info "  sudo systemctl start ollama"
        echo_info "  ollama pull nomic-embed-text:latest"
        echo_info "  ollama pull llama3.1:8b"
        echo ""
        echo_info "See: https://ollama.com"
        exit 1
    fi

    # Check Ollama service
    if systemctl is-active --quiet ollama; then
        echo_info "Ollama service is running"
    else
        echo_error "Ollama service is not running"
        echo ""
        echo_info "Please start Ollama first:"
        echo_info "  sudo systemctl start ollama"
        echo_info "  sudo systemctl enable ollama"
        echo ""
        exit 1
    fi

    # Check required models
    echo_info "Checking Ollama models..."
    MISSING_MODELS=()
    if ! ollama list | grep -q "nomic-embed-text"; then
        MISSING_MODELS+=("nomic-embed-text")
    fi
    if ! ollama list | grep -q "llama3.1:8b"; then
        MISSING_MODELS+=("llama3.1:8b")
    fi

    if [ ${#MISSING_MODELS[@]} -ne 0 ]; then
        echo_error "Missing required Ollama models: ${MISSING_MODELS[*]}"
        echo_info "Install with:"
        for model in "${MISSING_MODELS[@]}"; do
            echo_info "  ollama pull $model"
        done
        exit 1
    fi
fi

echo_success "Ollama configured"

# ============================================================================
# 3. Check Emacs and org-roam
# ============================================================================
echo_info "Step 3/6: Checking Emacs installation..."

if ! command -v emacs >/dev/null 2>&1; then
    echo_error "Emacs is not installed"
    echo ""
    echo_info "Please install Emacs first:"
    echo_info "  sudo apt install emacs"
    echo ""
    echo_info "Then configure org-roam before running this script."
    echo_info "See: https://github.com/org-roam/org-roam"
    exit 1
fi

EMACS_VERSION=$(emacs --version | head -n1)
echo_info "Found: ${EMACS_VERSION}"

# Check if org-roam is installed
echo_info "Checking for org-roam package..."

# Try to check if org-roam is available in Emacs
if emacs --batch --eval "(require 'org-roam)" 2>/dev/null; then
    echo_success "org-roam is installed"
else
    echo_error "org-roam is not installed in Emacs"
    echo ""
    echo_info "Please install and configure org-roam before running this script."
    echo ""
    echo_info "Installation instructions (via package.el or straight.el):"
    echo_info "  M-x package-install RET org-roam RET"
    echo_info ""
    echo_info "Or via straight.el:"
    echo_info "  (straight-use-package 'org-roam)"
    echo_info ""
    echo_info "Full instructions: https://github.com/org-roam/org-roam"
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
# 6. Configure Emacs Packages
# ============================================================================
echo_info "Step 6/7: Configuring Emacs packages..."

# Create Emacs configuration for org-roam packages
EMACS_CONFIG_FILE="$HOME/.emacs.d/init-org-roam-ai.el"

cat > "$EMACS_CONFIG_FILE" << 'EOF'
;; Configuration for org-roam-ai packages
;; This file is automatically generated by the org-roam-ai installer

;; Bootstrap straight.el if not already done
(unless (featurep 'straight)
  (defvar bootstrap-version)
  (let ((bootstrap-file
         (expand-file-name "straight/repos/straight.el/bootstrap.el" user-emacs-directory))
        (bootstrap-version 5))
    (unless (file-exists-p bootstrap-file)
      (with-current-buffer
          (url-retrieve-synchronously
           "https://raw.githubusercontent.com/radian-software/straight.el/develop/install.el"
           'silent 'inhibit-cookies)
        (goto-char (point-max))
        (eval-print-last-sexp)))
    (load bootstrap-file nil 'nomessage)))

;; Install required packages
(straight-use-package
  '(org-roam-vector-search
    :type git
    :host github
    :repo "dcruver/org-roam-ai"
    :files ("packages/org-roam-ai/org-roam-vector-search.el")))

(straight-use-package
  '(org-roam-ai-assistant
    :type git
    :host github
    :repo "dcruver/org-roam-ai"
    :files ("packages/org-roam-ai/org-roam-ai-assistant.el")))

(straight-use-package
  '(org-roam-api
    :type git
    :host github
    :repo "dcruver/org-roam-ai"
    :files ("packages/org-roam-ai/org-roam-api.el")))

;; Load org-roam if available
(when (require 'org-roam nil t)
  (org-roam-db-autosync-mode))
EOF

# Add Ollama configuration if environment variables are set
if [ "$OLLAMA_URL" != "http://localhost:11434" ]; then
    echo_info "Configuring Ollama URL: $OLLAMA_URL"
    cat >> "$EMACS_CONFIG_FILE" << EOF
(customize-set-variable 'org-roam-semantic-ollama-url "$OLLAMA_URL")
EOF
fi

if [ "$OLLAMA_EMBEDDING_MODEL" != "nomic-embed-text" ]; then
    echo_info "Configuring embedding model: $OLLAMA_EMBEDDING_MODEL"
    cat >> "$EMACS_CONFIG_FILE" << EOF
(customize-set-variable 'org-roam-semantic-embedding-model "$OLLAMA_EMBEDDING_MODEL")
EOF
fi

if [ "$OLLAMA_GENERATION_MODEL" != "llama3.1:8b" ]; then
    echo_info "Configuring generation model: $OLLAMA_GENERATION_MODEL"
    cat >> "$EMACS_CONFIG_FILE" << EOF
(customize-set-variable 'org-roam-semantic-generation-model "$OLLAMA_GENERATION_MODEL")
EOF
fi

if [ "$ENABLE_CHUNKING" = "true" ]; then
    echo_info "Enabling text chunking"
    cat >> "$EMACS_CONFIG_FILE" << EOF
(customize-set-variable 'org-roam-semantic-enable-chunking t)
EOF
fi

if [ "$MIN_CHUNK_SIZE" != "100" ]; then
    echo_info "Configuring minimum chunk size: $MIN_CHUNK_SIZE"
    cat >> "$EMACS_CONFIG_FILE" << EOF
(customize-set-variable 'org-roam-semantic-min-chunk-size $MIN_CHUNK_SIZE)
EOF
fi

# Check if init.el already loads this file
if ! grep -q "init-org-roam-ai.el" "$HOME/.emacs.d/init.el" 2>/dev/null; then
    echo_info "Adding org-roam-ai config to init.el..."
    echo "(load \"~/.emacs.d/init-org-roam-ai.el\")" >> "$HOME/.emacs.d/init.el"
fi

echo_success "Emacs packages configured"

# ============================================================================
# 7. Create Systemd Services
# ============================================================================
echo_info "Step 7/7: Creating systemd services..."

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
if [ "$OLLAMA_URL" != "http://localhost:11434" ]; then
    echo_info "Ollama URL: ${OLLAMA_URL}"
fi
echo ""
echo_info "Next Steps:"
echo ""
echo "1. Configure Emacs with org-roam:"
echo "   - Ensure org-roam is installed and configured"
echo "   - Start Emacs server: emacs --daemon"
echo ""
echo "2. Start MCP service:"
echo "   sudo systemctl start org-roam-mcp"
echo ""
echo "3. Enable service at boot:"
echo "   sudo systemctl enable org-roam-mcp"
echo ""
echo "4. Check status:"
echo "   sudo systemctl status org-roam-mcp"
echo "   journalctl -u org-roam-mcp -f"
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
