#!/bin/bash
# One-shot installation command for org-roam-ai from GitHub
# Installs the MCP server and integrated Emacs packages

# Set environment variables for local installation
# Modify these as needed for your setup:
export INSTALL_DIR=/opt/org-roam-ai                    # Installation directory
export ORG_ROAM_PATH=$HOME/org-roam                    # Path to org-roam notes
export OLLAMA_URL=http://localhost:11434              # Ollama server URL (use remote URL for remote server)
export OLLAMA_EMBEDDING_MODEL=nomic-embed-text        # Embedding model
export OLLAMA_GENERATION_MODEL=llama3.1:8b           # Generation model
export ENABLE_CHUNKING=false                          # Enable text chunking
export MIN_CHUNK_SIZE=100                             # Minimum chunk size

# Run the local installation script
bash "${INSTALL_DIR}/scripts/install-server.sh"