# Server Installation Guide

Complete guide for installing the org-roam-ai stack on a server.

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

The installation script sets up the complete stack:

1. **Ollama** - Local LLM backend
   - Service: `ollama.service`
   - Models: `nomic-embed-text:latest`, `llama3.1:8b`
   - Port: 11434

2. **MCP Server** (Python)
   - Service: `org-roam-mcp.service`
   - Port: 8000
   - Virtual env: `/opt/org-roam-ai/mcp/.venv`

3. **GOAP Agent** (Java)
   - Service: `org-roam-agent.service`
   - JAR: `/opt/org-roam-ai/agent/embabel-note-gardener.jar`

4. **Emacs Components** (manual configuration required)
   - org-roam-semantic (vector search)
   - org-roam-ai-assistant (AI enhancements)

## Prerequisites

- **OS**: Ubuntu 20.04+ or Debian 11+
- **User**: Non-root user with sudo privileges
- **RAM**: 8GB+ recommended (for LLMs)
- **Disk**: 10GB+ free space (for models)

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

**Load org-roam-ai packages:**
```elisp
;; Add to load-path
(add-to-list 'load-path "/opt/org-roam-ai/emacs")

;; Load semantic search
(require 'org-roam-vector-search)
(setq org-roam-semantic-ollama-url "http://localhost:11434"
      org-roam-semantic-embedding-model "nomic-embed-text"
      org-roam-semantic-generation-model "llama3.1:8b")

;; Load AI assistant
(require 'org-roam-ai-assistant)

;; Generate embeddings for existing notes
(org-roam-semantic-generate-all-embeddings)
```

**Load org-roam API (for MCP):**
```elisp
(load-file "/opt/org-roam-ai/mcp/org-roam-api.el")
```

**Start Emacs server:**
```bash
# As daemon
emacs --daemon

# Or in your init file
(server-start)
```

### 2. Environment Variables

The systemd services use these environment variables (configured in the script):

**MCP Service:**
- `EMACS_SERVER_FILE`: Path to Emacs server socket

**Agent Service:**
- `ORG_ROAM_PATH`: Directory containing .org files
- `OLLAMA_BASE_URL`: Ollama API endpoint

To customize, edit the service files:
```bash
sudo systemctl edit org-roam-mcp.service
sudo systemctl edit org-roam-agent.service
```

## Post-Installation

### Start Services

```bash
# Start MCP server
sudo systemctl start org-roam-mcp
sudo systemctl enable org-roam-mcp

# Start agent (requires MCP running)
sudo systemctl start org-roam-agent
sudo systemctl enable org-roam-agent
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
journalctl -u org-roam-agent -f
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
│ - org-roam-semantic (embeddings)                            │
│ - Server socket: ~/emacs-server/server                      │
└────────────────────────┬────────────────────────────────────┘
                         │ emacsclient
┌────────────────────────▼────────────────────────────────────┐
│ MCP Server (Python) - Port 8000                             │
│ Service: org-roam-mcp.service                               │
│ - HTTP JSON-RPC API                                         │
│ - Semantic search                                           │
│ - Note creation                                             │
└────────────────────────┬────────────────────────────────────┘
                         │ HTTP
┌────────────────────────▼────────────────────────────────────┐
│ GOAP Agent (Java)                                           │
│ Service: org-roam-agent.service                             │
│ - Corpus auditing                                           │
│ - Format normalization                                      │
│ - Link suggestions                                          │
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

# Update MCP
cd mcp
source .venv/bin/activate
pip install -e ".[dev]"
deactivate

# Update Agent (if GITEA_TOKEN set)
cd ../agent
GITEA_TOKEN=your_token ../scripts/deploy-agent.sh

# Or rebuild from source
./mvnw clean package -DskipTests
cp target/embabel-note-gardener-*.jar embabel-note-gardener.jar

# Restart services
sudo systemctl restart org-roam-mcp
sudo systemctl restart org-roam-agent
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

**Agent:**
```bash
cd /opt/org-roam-ai/agent
# Download from Gitea
GITEA_TOKEN=your_token ./scripts/deploy-agent.sh
sudo systemctl restart org-roam-agent
```

**Emacs packages:**
```bash
cd /opt/org-roam-ai
git pull
# Reload in Emacs
M-x load-file RET /opt/org-roam-ai/emacs/org-roam-vector-search.el
M-x load-file RET /opt/org-roam-ai/emacs/org-roam-ai-assistant.el
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
- org-roam-api.el not loaded → Load in Emacs init

### Agent Service Won't Start

**Check MCP is running:**
```bash
curl http://localhost:8000
sudo systemctl status org-roam-mcp
```

**Check Ollama:**
```bash
curl http://localhost:11434/api/tags
ollama list
```

**Check logs:**
```bash
journalctl -u org-roam-agent -n 50
```

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
sudo systemctl stop org-roam-mcp org-roam-agent
sudo systemctl disable org-roam-mcp org-roam-agent
sudo rm /etc/systemd/system/org-roam-{mcp,agent}.service
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
- **Agent Service**: `/etc/systemd/system/org-roam-agent.service`
- **Agent Config**: `/opt/org-roam-ai/agent/src/main/resources/application.yml`
- **Emacs Server**: `~/emacs-server/server`

## Support

For issues:
- Check logs: `journalctl -u <service-name>`
- Review documentation: `README.md`, `ARCHITECTURE.md`
- File issues: https://gitea.cruver.network/dcruver/org-roam-ai/issues
