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

# Check for Doom Emacs
if ([ -d "$HOME/.doom.d" ] && command -v doom >/dev/null 2>&1) || ([ -d "$HOME/.config/emacs" ] && [ -f "$HOME/.config/emacs/bin/doom" ]); then
    echo_info "Doom Emacs detected - org-roam-ai is compatible!"
    echo_info "Doom packages should be automatically managed"
    echo_success "Doom Emacs configuration complete"
else
    echo_info "Standard Emacs detected"
    echo_info "Please ensure org-roam is installed via MELPA"
fi

# ============================================================================
# 5. Configure Emacs Packages
# ============================================================================
echo_info "Step 5/7: Configuring Emacs packages..."

# Check if Doom Emacs is being used
DOOM_DIR="$HOME/.doom.d"
DOOM_CONFIG_DIR="$HOME/.config/emacs"
DOOM_BIN="$DOOM_CONFIG_DIR/bin/doom"

if ([ -d "$DOOM_DIR" ] && command -v doom >/dev/null 2>&1) || ([ -d "$DOOM_CONFIG_DIR" ] && [ -f "$DOOM_BIN" ]); then
    echo_info "Doom Emacs detected - configuring automatically"

    # Determine which Doom setup is being used
    if [ -d "$DOOM_DIR" ]; then
        DOOM_CONFIG_FILE="$DOOM_DIR/config.el"
        DOOM_PACKAGES_FILE="$DOOM_DIR/packages.el"
    else
        DOOM_CONFIG_FILE="$DOOM_CONFIG_DIR/config.el"
        DOOM_PACKAGES_FILE="$DOOM_CONFIG_DIR/packages.el"
    fi

    # Configure straight.el packages for org-roam-ai
    echo_info "Adding org-roam-ai packages to Doom configuration..."

    # Add to packages.el if not already present
    if ! grep -q "org-roam-ai" "$DOOM_PACKAGES_FILE" 2>/dev/null; then
        cat >> "$DOOM_PACKAGES_FILE" << 'EOF'

;; org-roam-ai packages
(package! org-roam-ai-assistant :recipe (:host github :repo "dcruver/org-roam-ai" :files ("packages/org-roam-ai/org-roam-ai-assistant.el")))
(package! org-roam-api :recipe (:host github :repo "dcruver/org-roam-ai" :files ("packages/org-roam-ai/org-roam-api.el")))
(package! org-roam-vector-search :recipe (:host github :repo "dcruver/org-roam-ai" :files ("packages/org-roam-ai/org-roam-vector-search.el")))
EOF
        echo_success "Added org-roam-ai packages to packages.el"
    else
        echo_info "org-roam-ai packages already configured in packages.el"
    fi

    # Configure Ollama settings in config.el
    if ! grep -q "org-roam-ai" "$DOOM_CONFIG_FILE" 2>/dev/null; then
        cat >> "$DOOM_CONFIG_FILE" << EOF

;; org-roam-ai configuration
(after! org-roam
  (require 'org-roam-ai-assistant)
  (require 'org-roam-api)
  (require 'org-roam-vector-search)

  ;; Configure Ollama settings
  (setq org-roam-ai-ollama-url "${OLLAMA_URL}")
  (setq org-roam-ai-embedding-model "${OLLAMA_EMBEDDING_MODEL}")
  (setq org-roam-ai-generation-model "${OLLAMA_GENERATION_MODEL}")
  (setq org-roam-ai-enable-chunking ${ENABLE_CHUNKING})
  (setq org-roam-ai-min-chunk-size ${MIN_CHUNK_SIZE})

  ;; Set org-roam directory
  (setq org-roam-directory "${ORG_ROAM_PATH}")
)
EOF
        echo_success "Added org-roam-ai configuration to config.el"
    else
        echo_info "org-roam-ai already configured in config.el"
    fi

    echo_success "Doom Emacs packages configured"
else
    echo_info "Standard Emacs detected - configuring with straight.el"

    # Check if straight.el is available
    if emacs --batch --eval "(require 'straight)" 2>/dev/null; then
        echo_info "straight.el detected - configuring packages"

        # Configure straight.el packages
        emacs --batch --eval "
(require 'straight)
(straight-use-package
 '(org-roam-ai-assistant :host github :repo \"dcruver/org-roam-ai\"
                        :files (\"packages/org-roam-ai/org-roam-ai-assistant.el\")))
(straight-use-package
 '(org-roam-api :host github :repo \"dcruver/org-roam-ai\"
                :files (\"packages/org-roam-ai/org-roam-api.el\")))
(straight-use-package
 '(org-roam-vector-search :host github :repo \"dcruver/org-roam-ai\"
                         :files (\"packages/org-roam-ai/org-roam-vector-search.el\")))
" 2>/dev/null && echo_success "straight.el packages installed" || echo_warn "Failed to install straight.el packages"

    else
        echo_warn "straight.el not found - manual configuration required"
        echo_info "Please install straight.el and add the org-roam-ai packages manually"
        echo_info "See: https://github.com/radian-software/straight.el"
    fi
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
# 4. Install MCP Server (Python)
# ============================================================================
echo_info "Step 4/6: Installing MCP server..."

# Check Python version
if command -v python3 >/dev/null 2>&1; then
    PYTHON_VERSION=$(python3 --version)
    echo_info "Found: ${PYTHON_VERSION}"
else
    echo_error "Python 3 not found"
    exit 1
fi

# Create virtual environment in user's home directory
MCP_DIR="$HOME/.org-roam-ai-mcp"
MCP_VENV="${MCP_DIR}/.venv"

if [ ! -d "${MCP_VENV}" ]; then
    echo_info "Setting up MCP server..."
    mkdir -p "${MCP_DIR}"
    cd "${MCP_DIR}"

    # Install MCP server from PyPI
    python3 -m venv .venv
    source .venv/bin/activate
    pip install --upgrade pip
    pip install org-roam-mcp
    deactivate
    echo_success "MCP server installed to ${MCP_DIR}"
else
    echo_warn "MCP server already installed, skipping"
fi

# ============================================================================
# 6. Summary
# ============================================================================
echo_info "Step 6/6: Installation complete!"

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