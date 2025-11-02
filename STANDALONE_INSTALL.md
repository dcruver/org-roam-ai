# Standalone MCP Server Installation

This script provides a **one-command installation** for the org-roam-ai MCP server that works without cloning the repository. It installs the MCP server from GitHub Packages.

## One-Command Installation

```bash
curl -fsSL https://raw.githubusercontent.com/dcruver/org-roam-ai/main/install-mcp-standalone.sh | bash
```

**With custom configuration:**
```bash
OLLAMA_URL="http://your-ollama-server:11434" \
OLLAMA_EMBEDDING_MODEL="nomic-embed-text" \
OLLAMA_GENERATION_MODEL="llama3.1:8b" \
ENABLE_CHUNKING="true" \
MIN_CHUNK_SIZE="50" \
curl -fsSL https://raw.githubusercontent.com/dcruver/org-roam-ai/main/install-mcp-standalone.sh | bash
```

**Note**: This installs from GitHub Packages. For public packages, authentication may still be required.

## What It Does

1. **Prerequisites Check**: Verifies Python 3.8+, pip, Emacs
2. **Virtual Environment**: Creates `~/.org-roam-mcp/` with isolated Python environment
3. **MCP Installation**: Installs `org-roam-mcp` from PyPI
4. **Emacs Configuration**: Sets up straight.el and required org-roam packages
5. **Semantic Configuration**: Configures Ollama and semantic search settings from environment variables
6. **Systemd Service**: Creates and enables `org-roam-mcp.service`
7. **Auto-Start**: Service starts automatically on boot

## What You Get

- ✅ **MCP Server**: Runs on http://localhost:8000
- ✅ **Emacs Packages**: Automatically installed and configured
- ✅ **Systemd Service**: Managed service with auto-restart
- ✅ **Isolated Environment**: No conflicts with system Python

## Post-Installation

```bash
# Check status
sudo systemctl status org-roam-mcp.service

# View logs
sudo journalctl -u org-roam-mcp.service -f

# Test API
curl -X POST http://localhost:8000 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "method": "tools/list", "id": 1}'
```

## Architecture

```
User runs: curl ... | bash
    ↓
Script creates: ~/.org-roam-mcp/ (venv)
Script installs: org-roam-mcp (PyPI)
Script configures: ~/.emacs.d/init-org-roam-mcp.el
Script creates: /etc/systemd/system/org-roam-mcp.service
    ↓
Service starts MCP server
MCP server verifies Emacs packages are loaded
MCP server provides API on port 8000
```

## Configuration Options

### Environment Variables

- **`OLLAMA_URL`**: Set custom Ollama server URL (default: `http://localhost:11434`)
- **`OLLAMA_EMBEDDING_MODEL`**: Set embedding model (default: `nomic-embed-text`)
- **`OLLAMA_GENERATION_MODEL`**: Set generation model (default: `llama3.1:8b`)
- **`ENABLE_CHUNKING`**: Enable text chunking for better embeddings (default: `false`)
- **`MIN_CHUNK_SIZE`**: Minimum chunk size for text splitting (default: `100`)

**Example with all options:**
```bash
OLLAMA_URL="http://your-server:11434" \
OLLAMA_EMBEDDING_MODEL="nomic-embed-text" \
OLLAMA_GENERATION_MODEL="gpt-oss:20b" \
ENABLE_CHUNKING="true" \
MIN_CHUNK_SIZE="50" \
curl -fsSL https://raw.githubusercontent.com/dcruver/org-roam-ai/main/install-mcp-standalone.sh | bash
```

## Requirements

- Linux with systemd
- Python 3.8+
- Emacs with straight.el installed
- Internet connection (for package downloads)

## Prerequisites Setup

If straight.el is not installed:

```bash
# Install straight.el
curl -fsSL https://raw.githubusercontent.com/radian-software/straight.el/develop/install.el | emacs --batch -l /dev/stdin

# Then run the MCP installation
curl -fsSL https://raw.githubusercontent.com/dcruver/org-roam-ai/main/install-mcp-standalone.sh | bash
```

## Troubleshooting

**Service won't start:**
```bash
sudo journalctl -u org-roam-mcp.service -n 50
```

**Emacs packages not loading:**
- Check `~/.emacs.d/init-org-roam-mcp.el` exists
- Verify `~/.emacs.d/init.el` loads the MCP config
- Restart Emacs: `emacsclient -e '(kill-emacs)' && emacs --daemon`

**Permission issues:**
- The script uses `sudo` for systemd operations
- Virtual environment is created in user home directory