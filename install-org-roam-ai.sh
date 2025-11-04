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

# Clone repository if not already cloned
if [ -d "${INSTALL_DIR}/.git" ]; then
    echo_info "Repository already cloned, pulling latest..."
    cd "${INSTALL_DIR}"
    git pull
else
    echo_info "Cloning org-roam-ai repository..."
    sudo mkdir -p "${INSTALL_DIR}"
    sudo chown $USER:$USER "${INSTALL_DIR}"
    git clone https://github.com/dcruver/org-roam-ai.git "${INSTALL_DIR}"
fi

cd "${INSTALL_DIR}"
echo_success "Repository cloned/updated"

# Run the local installation script
bash "${INSTALL_DIR}/scripts/install-server.sh"