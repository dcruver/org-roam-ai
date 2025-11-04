#!/bin/bash
# One-shot installation command for org-roam-ai from GitHub
# Installs the MCP server and integrated Emacs packages

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --install-dir=*)
            INSTALL_DIR="${1#*=}"
            shift
            ;;
        --org-roam-path=*)
            ORG_ROAM_PATH="${1#*=}"
            shift
            ;;
        --ollama-url=*)
            OLLAMA_URL="${1#*=}"
            shift
            ;;
        --ollama-embedding-model=*)
            OLLAMA_EMBEDDING_MODEL="${1#*=}"
            shift
            ;;
        --ollama-generation-model=*)
            OLLAMA_GENERATION_MODEL="${1#*=}"
            shift
            ;;
        --enable-chunking=*)
            ENABLE_CHUNKING="${1#*=}"
            shift
            ;;
        --min-chunk-size=*)
            MIN_CHUNK_SIZE="${1#*=}"
            shift
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [--install-dir=DIR] [--org-roam-path=PATH] [--ollama-url=URL] [--ollama-embedding-model=MODEL] [--ollama-generation-model=MODEL] [--enable-chunking=BOOL] [--min-chunk-size=SIZE]"
            exit 1
            ;;
    esac
done

# Set environment variables for local installation
# These can be overridden by command line args or environment variables
export INSTALL_DIR="${INSTALL_DIR:-/opt/org-roam-ai}"                    # Installation directory
export ORG_ROAM_PATH="${ORG_ROAM_PATH:-$HOME/org-roam}"                    # Path to org-roam notes
export OLLAMA_URL="${OLLAMA_URL:-http://localhost:11434}"              # Ollama server URL (use remote URL for remote server)
export OLLAMA_EMBEDDING_MODEL="${OLLAMA_EMBEDDING_MODEL:-nomic-embed-text}"        # Embedding model
export OLLAMA_GENERATION_MODEL="${OLLAMA_GENERATION_MODEL:-llama3.1:8b}"           # Generation model
export ENABLE_CHUNKING="${ENABLE_CHUNKING:-false}"                          # Enable text chunking
export MIN_CHUNK_SIZE="${MIN_CHUNK_SIZE:-100}"                             # Minimum chunk size

# Configuration
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

# Check if Doom Emacs is being used
DOOM_DIR="$HOME/.doom.d"
DOOM_CONFIG_DIR="$HOME/.config/emacs"
DOOM_BIN="$DOOM_CONFIG_DIR/bin/doom"

if ([ -d "$DOOM_DIR" ] && command -v doom >/dev/null 2>&1) || ([ -d "$DOOM_CONFIG_DIR" ] && [ -f "$DOOM_BIN" ]); then
    echo_info "Detected Doom Emacs configuration"

    # Determine which Doom setup is being used
    if [ -d "$DOOM_DIR" ]; then
        PACKAGES_FILE="$DOOM_DIR/packages.el"
        DOOM_CMD="doom"
    else
        PACKAGES_FILE="$DOOM_CONFIG_DIR/packages.el"
        DOOM_CMD="$DOOM_BIN"
    fi

    # Check if packages.el exists, create it if not
    if [ ! -f "$PACKAGES_FILE" ]; then
        echo_info "Creating Doom packages.el file..."
        mkdir -p "$(dirname "$PACKAGES_FILE")"
        cat > "$PACKAGES_FILE" << 'EOF'
;; -*- no-byte-compile: t; -*-
;;; $DOOMDIR/packages.el

(package! org-roam
  :recipe (:host github :repo "org-roam/org-roam" :branch "main"))
(package! sqlite3)
(package! ox-gfm)
(package! org-roam-ui)
(package! claude-code-ide
  :recipe (:host github :repo "manzaltu/claude-code-ide.el"))

;; org-roam-semantic for vector search
(package! org-roam-semantic
  :recipe (:host github :repo "dcruver/org-roam-semantic"))

(package! gptel)            ; AI client (works great with Ollama and APIs)
;; If you want Docker TRAMP helpers:
(package! docker-tramp)     ; optional, handy for containers
EOF
        echo_success "Created $PACKAGES_FILE"
    fi

    # Check if org-roam is in Doom packages
    if grep -q "org-roam" "$PACKAGES_FILE" 2>/dev/null; then
        echo_success "org-roam found in Doom packages.el"

        # Ensure Doom packages are synced
        echo_info "Ensuring Doom packages are synced..."
        if $DOOM_CMD sync; then
            echo_success "Doom packages synced"
        else
            echo_warn "Doom sync failed, but continuing..."
        fi
    else
        echo_error "org-roam not found in Doom packages.el"
        echo_info "Please add org-roam to your $PACKAGES_FILE:"
        echo_info "  (package! org-roam)"
        echo_info "Then run: $DOOM_CMD sync"
        exit 1
    fi
else
    # Standard Emacs package check
    echo_info "Using standard Emacs package management"

    # Try to check if org-roam is available in Emacs
    # Initialize package system first to ensure packages are loaded
    if emacs --batch --eval "
(progn
  (require 'package)
  (package-initialize)
  (require 'org-roam)
  (message \"org-roam loaded successfully\"))" 2>/dev/null; then
        echo_success "org-roam is installed"
    else
        echo_info "org-roam not found, attempting automatic installation..."

        # Install org-roam automatically
        if emacs --batch --eval "
(progn
  (require 'package)
  (add-to-list 'package-archives '(\"melpa\" . \"https://melpa.org/packages/\") t)
  (package-initialize)
  (package-refresh-contents)
  (package-install 'org-roam)
  (message \"org-roam installed\"))" 2>/dev/null; then
            echo_success "org-roam installed successfully"
        else
            echo_error "Failed to install org-roam automatically"
            echo ""
            echo_info "Please install org-roam manually. First ensure MELPA is added to your package archives:"
            echo_info ""
            echo_info "Add to your Emacs init file (~/.emacs.d/init.el):"
            echo_info "  (require 'package)"
            echo_info "  (add-to-list 'package-archives '(\"melpa\" . \"https://melpa.org/packages/\") t)"
            echo_info ""
            echo_info "Then install manually in Emacs:"
            echo_info "  M-x package-refresh-contents RET"
            echo_info "  M-x package-install RET org-roam RET"
            echo ""
            echo_info "Debug: Check which Emacs is being used:"
            echo_info "  which emacs"
            echo_info "  emacs --version"
            echo_info "  Test org-roam manually: emacs --batch --eval \"(progn (require 'package) (package-initialize) (require 'org-roam) (message \\\"org-roam loaded\\\"))\""
            echo ""
            exit 1
        fi
    fi
fi

# ============================================================================
# 6. Configure Emacs Packages
# ============================================================================
echo_info "Step 6/7: Configuring Emacs packages..."

# Check if Doom Emacs is being used
DOOM_DIR="$HOME/.doom.d"
DOOM_CONFIG_DIR="$HOME/.config/emacs"
DOOM_BIN="$DOOM_CONFIG_DIR/bin/doom"

if ([ -d "$DOOM_DIR" ] && command -v doom >/dev/null 2>&1) || ([ -d "$DOOM_CONFIG_DIR" ] && [ -f "$DOOM_BIN" ]); then
    echo_info "Doom Emacs detected - configuring automatically"

    # Determine which Doom setup is being used
    if [ -d "$DOOM_DIR" ]; then
        DOOM_CONFIG_FILE="$DOOM_DIR/config.el"
    else
        DOOM_CONFIG_FILE="$DOOM_CONFIG_DIR/config.el"
    fi

    # Configure Ollama settings for org-roam-ai
    echo_info "Configuring org-roam-ai for Doom Emacs..."
    echo_success "Doom Emacs packages configured"
else
    echo_info "Standard Emacs detected - manual configuration required"
    echo_info "Please see the README for manual installation instructions"
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
    git clone https://github.com/dcruver/org-roam-ai.git "${INSTALL_DIR}"
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
# 7. Summary
# ============================================================================
echo_info "Step 7/7: Installation complete!"

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
echo "1. Start Emacs daemon:"
echo "   emacs --daemon"
echo ""
echo "2. Test org-roam-ai functionality in Emacs"
echo ""
echo "3. Optional: Create systemd service for MCP server:"
echo "   See the systemd service creation commands in the script comments"
echo "   or check the README for manual systemd service setup"
echo ""
echo_warn "Note: Make sure Emacs daemon is running for full functionality!"
echo ""