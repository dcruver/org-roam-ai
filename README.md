> ⚠️ **DEPRECATED**: This repository has been merged into [org-roam-second-brain](https://github.com/dcruver/org-roam-second-brain).
> 
> All functionality from this repo (including `org-roam-api.el` and the MCP server) is now part of org-roam-second-brain.
> Please use that repository for the latest updates.

---

# org-roam-ai

**MCP Server and API for org-roam knowledge base integration**

This repository provides the MCP (Model Context Protocol) server that enables external tools to interact with your org-roam knowledge base.

## What's Here vs. What's Elsewhere

| Component | Location | Purpose |
|-----------|----------|---------|
| **MCP Server** | This repo (`mcp/`) | HTTP/stdio API for n8n, chatbots, external tools |
| **org-roam-api.el** | This repo (`packages/org-roam-ai/`) | Elisp functions called by MCP server |
| **org-roam-second-brain** | [Separate repo](https://github.com/dcruver/org-roam-second-brain) | User-facing Emacs packages (vector-search, second-brain) |

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  org-roam-second-brain (separate package)                       │
│  - org-roam-vector-search.el: Semantic search & embeddings      │
│  - org-roam-second-brain.el: Structured notes & surfacing       │
│  - Install via: (straight-use-package                           │
│      '(org-roam-second-brain :host github                       │
│        :repo "dcruver/org-roam-second-brain"))                  │
└────────────────────────┬────────────────────────────────────────┘
                         │ provides semantic search functions
┌────────────────────────▼────────────────────────────────────────┐
│  org-roam-api.el (this repo)                                    │
│  - API wrapper functions (my/api-*)                             │
│  - Used by MCP server via emacsclient                           │
└────────────────────────┬────────────────────────────────────────┘
                         │ emacsclient --eval
┌────────────────────────▼────────────────────────────────────────┐
│  org-roam-mcp (Python) - MCP Server                             │
│  - HTTP server on port 8001                                     │
│  - JSON-RPC 2.0 protocol                                        │
│  - Tools: semantic_search, create_note, contextual_search, etc. │
│  - Used by: n8n workflows, Matrix chatbots                      │
└─────────────────────────────────────────────────────────────────┘
```

## Quick Start

### For Emacs Users

Install the user-facing packages from the separate repository:

```elisp
;; In your Doom config.el or init.el
(straight-use-package
  '(org-roam-second-brain :host github :repo "dcruver/org-roam-second-brain"))

(require 'org-roam-second-brain)

;; Configure embedding service
(setq org-roam-semantic-embedding-url "http://your-embedding-server:8080")
(setq org-roam-semantic-embedding-model "nomic-ai/nomic-embed-text-v1.5")
```

### For MCP Server Deployment

See [mcp/README.md](mcp/README.md) for MCP server installation and deployment.

**Quick deploy to a server:**

```bash
# Build wheel
cd mcp && python -m build

# Copy to server
scp dist/org_roam_mcp-*.whl user@server:/tmp/

# Install on server
ssh user@server
pip install /tmp/org_roam_mcp-*.whl
org-roam-mcp  # Starts on port 8001
```

**Required on MCP server:**
1. Emacs with org-roam configured
2. Emacs daemon running (`emacs --daemon`)
3. `org-roam-second-brain` package installed in Emacs
4. `org-roam-api.el` loaded (copy from this repo to your doom config)

## Repository Structure

```
org-roam-ai/
├── mcp/                           # MCP Server (Python)
│   ├── src/org_roam_mcp/         # Server source code
│   ├── pyproject.toml            # Package configuration
│   └── README.md                 # MCP-specific documentation
│
├── packages/org-roam-ai/          # API functions for MCP
│   └── org-roam-api.el           # my/api-* functions
│
└── README.md                      # This file
```

## MCP Tools Available

| Tool | Description |
|------|-------------|
| `semantic_search` | Vector-based semantic search |
| `contextual_search` | Keyword search with context |
| `search_notes` | Basic title/content search |
| `create_note` | Create new org-roam note |
| `add_daily_entry` | Add entry to daily note |
| `add_inbox_entry` | Log to inbox for audit trail |
| `sync_database` | Sync org-roam database |

## Related Repositories

- **[org-roam-second-brain](https://github.com/dcruver/org-roam-second-brain)**: User-facing Emacs packages
- **[org-roam](https://github.com/org-roam/org-roam)**: The base org-roam package

## License

GPL-3.0
