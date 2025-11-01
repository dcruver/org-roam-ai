# Server Installation Guide

Complete guide for installing the org-roam-ai integrated stack on a server.

## Quick Start

```bash
# Clone the repository
git clone gitea@gitea-backend.cruver.network:dcruver/org-roam-ai.git
cd org-roam-ai

# Run installation script
./scripts/install-server.sh

# Or with custom settings
INSTALL_DIR=/opt/org-roam-ai \
ORG_ROAM_PATH=$HOME/org-roam \
GITEA_TOKEN=your_token \
./scripts/install-server.sh
```

## What Gets Installed

The installation script sets up the integrated stack:

1. **MCP Server** (Python)
    - Service: `org-roam-mcp.service`
    - Port: 8000
    - Virtual env: `/opt/org-roam-ai/mcp/.venv`
    - Automatically loads integrated Emacs packages

2. **Integrated Emacs Packages** (loaded automatically)
    - org-roam-vector-search (semantic search)
    - org-roam-ai-assistant (AI enhancements)
    - org-roam-api (MCP integration)

## Prerequisites

- **OS**: Ubuntu 20.04+ or Debian 11+
- **User**: Non-root user with sudo privileges
- **RAM**: 8GB+ recommended (for LLMs)
- **Disk**: 10GB+ free space (for models)

### Ollama Installation

Install Ollama and required models:

```bash
# Install Ollama
curl -fsSL https://ollama.com/install.sh | sh

# Start Ollama service
sudo systemctl enable ollama
sudo systemctl start ollama

# Pull required models (may take several minutes)
ollama pull nomic-embed-text:latest
ollama pull llama3.1:8b

# Verify installation
ollama list
curl http://localhost:11434/api/tags
```

## Manual Configuration Required

### 1. Emacs Configuration

The script doesn't configure Emacs automatically. You need to:

**Install org-roam:**
```elisp
(use-package org-roam
  :ensure t
  :custom
  (org-roam-directory "~/org-roam")
  :config
  (org-roam-db-autosync-mode))
```

**Configure Ollama (optional - for AI features):**
```elisp
;; Configure Ollama connection (if using AI features)
(customize-set-variable 'org-roam-semantic-ollama-url "http://localhost:11434")
(customize-set-variable 'org-roam-semantic-embedding-model "nomic-embed-text")
(customize-set-variable 'org-roam-semantic-generation-model "llama3.1:8b")
```

**Note:** The MCP server automatically loads the integrated Emacs packages. No manual loading required.

**Start Emacs server:**
```bash
# As daemon
emacs --daemon

# Or in your init file
(server-start)
```

### 2. Environment Variables

The systemd service uses these environment variables (configured in the script):

**MCP Service:**
- `EMACS_SERVER_FILE`: Path to Emacs server socket

To customize, edit the service file:
```bash
sudo systemctl edit org-roam-mcp.service
```

## Post-Installation

### Start Services

```bash
# Start MCP server
sudo systemctl start org-roam-mcp
sudo systemctl enable org-roam-mcp
```

### Verify Installation

```bash
# Check Ollama
curl http://localhost:11434/api/tags
ollama list

# Check MCP server
curl http://localhost:8000

# Check Emacs server
emacsclient --eval '(+ 1 1)'

# Check service logs
journalctl -u org-roam-mcp -f
```

### Test Semantic Search

In Emacs:
```elisp
;; Check status
M-x org-roam-semantic-status

;; Search by concept
M-x org-roam-semantic-search RET your query RET

;; Create a test note
M-x org-roam-node-find RET test note RET
```

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│ Emacs + org-roam                                            │
│ - org-roam database                                         │
│ - Integrated packages (vector search, AI assistant)         │
│ - Server socket: ~/emacs-server/server                      │
└────────────────────────┬────────────────────────────────────┘
                         │ emacsclient
┌────────────────────────▼────────────────────────────────────┐
│ MCP Server (Python) - Port 8000                             │
│ Service: org-roam-mcp.service                               │
│ - HTTP JSON-RPC API                                         │
│ - Semantic search                                           │
│ - Note creation                                             │
│ - AI enhancements                                           │
└────────────────────────┬────────────────────────────────────┘
                         │ HTTP
┌────────────────────────▼────────────────────────────────────┐
│ Ollama - Port 11434                                         │
│ Service: ollama.service                                     │
│ - LLM: llama3.1:8b                                          │
│ - Embeddings: nomic-embed-text                              │
└─────────────────────────────────────────────────────────────┘
```

## Updating

### Update All Components

```bash
cd /opt/org-roam-ai
git pull

# Update MCP server
cd mcp
source .venv/bin/activate
pip install -e ".[dev]"
deactivate

# Restart service
sudo systemctl restart org-roam-mcp
```

### Update Individual Components

**MCP Server:**
```bash
cd /opt/org-roam-ai
git pull
cd mcp
source .venv/bin/activate
pip install -e ".[dev]"
sudo systemctl restart org-roam-mcp
```

**Emacs packages:**
```bash
cd /opt/org-roam-ai
git pull
# Packages are automatically loaded by MCP server
# No manual reload required
```

## Troubleshooting

### MCP Service Won't Start

**Check Emacs server:**
```bash
emacsclient --eval '(+ 1 1)'
```

**Check logs:**
```bash
journalctl -u org-roam-mcp -n 50
```

**Common issues:**
- Emacs server not running → Start with `emacs --daemon`
- Wrong server file path → Check `EMACS_SERVER_FILE` in service
- org-roam not configured → Set up org-roam in Emacs

### Ollama Connection Issues

```bash
# Check service
sudo systemctl status ollama

# Test API
curl http://localhost:11434/api/tags

# Pull missing models
ollama pull nomic-embed-text:latest
ollama pull llama3.1:8b
```

## Uninstallation

```bash
# Stop and disable services
sudo systemctl stop org-roam-mcp
sudo systemctl disable org-roam-mcp
sudo rm /etc/systemd/system/org-roam-mcp.service
sudo systemctl daemon-reload

# Remove installation directory
sudo rm -rf /opt/org-roam-ai

# Remove Ollama (optional)
sudo systemctl stop ollama
sudo systemctl disable ollama
curl -fsSL https://ollama.com/install.sh | sh -s uninstall
```

## Configuration Files

- **MCP Service**: `/etc/systemd/system/org-roam-mcp.service`
- **Emacs Server**: `~/emacs-server/server`

## Support

For issues:
- Check logs: `journalctl -u <service-name>`
- Review documentation: `README.md`, `ARCHITECTURE.md`
- File issues: https://gitea.cruver.network/dcruver/org-roam-ai/issues
