# org-roam-ai

**AI-powered knowledge base management for org-roam**

Transform your org-roam knowledge base with semantic search, intelligent AI assistance, and API services through an integrated architecture that combines Emacs packages with external tool access.

## What is org-roam-ai?

org-roam-ai provides AI-powered semantic search and API services for [org-roam](https://www.orgroam.com/) knowledge bases:

- **Integrated Emacs Packages**: Semantic search, AI assistance, and API functions
- **MCP Server**: HTTP/stdio API for automation workflows and external integrations
- **External Integration**: Powers n8n workflows, chatbots, and external tool access

**All Components Included**: No external dependencies - everything is integrated in this monorepo

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  Emacs Packages (packages/org-roam-ai/)                        │
│  - org-roam-vector-search: Semantic search & embeddings         │
│  - org-roam-ai-assistant: AI enhancement & analysis             │
│  - org-roam-api: MCP integration functions                      │
│  - Interactive commands (C-c v, C-c a)                          │
└────────────────────────┬────────────────────────────────────────┘
                         │ emacsclient
┌────────────────────────▼────────────────────────────────────────┐
│  org-roam-mcp (Python) - MCP Server                             │
│  - HTTP/stdio MCP protocol server                               │
│  - Loads and wraps integrated elisp functions                  │
│  - Semantic search API with embeddings                          │
│  - Note creation with auto-embedding                            │
│  - Integration with n8n, external tools                         │
└─────────────────────────────────────────────────────────────────┘
```

### Component Overview

**[packages/org-roam-ai/](packages/org-roam-ai/) - Emacs Packages**
- `org-roam-vector-search.el`: Semantic search and vector embeddings
- `org-roam-ai-assistant.el`: AI-powered note enhancement and analysis
- `org-roam-api.el`: MCP integration functions
- Automatically loaded by the MCP server

**[mcp/](mcp/README.md) - MCP Server**
- Python-based Model Context Protocol server
- HTTP and stdio transport modes
- Loads and wraps integrated Emacs functions
- Powers n8n workflows and external integrations

## Quick Start

### Prerequisites

**Required:**
1. **Ollama** with required models:
   ```bash
   ollama pull nomic-embed-text    # For semantic embeddings
   ollama pull llama3.1:8b         # For AI text generation
   ```

2. **Emacs** with org-roam installed
   ```bash
   ollama pull nomic-embed-text    # For embeddings
   ollama pull llama3.1:8b         # For AI generation
   ollama pull gpt-oss:20b         # For agent (optional)
   ```

3. **Emacs** with **org-roam** configured
4. **Emacs server** running (`M-x server-start`)

### Installation

#### 1. Emacs Packages (Core Functionality)
```elisp
;; Install via straight.el (recommended)
;; Installs directly from monorepo - only the package files, not the entire repo
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
```

**Key Bindings:**
- `C-c v s` - Semantic search by concept
- `C-c a f` - AI-enhanced note fleshing
- `C-c a e` - Explain concepts at point

#### 2. MCP Server (API Access & n8n Integration)

**Option A: One-command standalone installation (Recommended)**
```bash
# Install everything automatically (virtual env, service, Emacs config)
# Installs from PyPI, requires straight.el prerequisite
curl -fsSL https://raw.githubusercontent.com/dcruver/org-roam-ai/main/install-mcp-standalone.sh | bash

# Service starts automatically
sudo systemctl status org-roam-mcp
```

**Uninstallation**
```bash
# Remove all components installed by the standalone installer
curl -fsSL https://raw.githubusercontent.com/dcruver/org-roam-ai/main/uninstall-mcp.sh | bash
```

**Option B: Manual installation**
```bash
# Install MCP server
cd mcp
pip install -e .

# Run server (HTTP mode for n8n)
org-roam-mcp

# Test API
curl -X POST http://localhost:8000 -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "semantic_search",
    "arguments": {"query": "docker networking", "limit": 5}
  },
  "id": 1
}'
```
**→ See [STANDALONE_INSTALL.md](STANDALONE_INSTALL.md) for one-command setup**  
**→ See [mcp/README.md](mcp/README.md) for API reference**

## Use Cases

### MCP Server - API Integration & Automation
- Integrate org-roam with n8n workflows
- API access to semantic search from external tools
- Automated note creation with embeddings
- Matrix/Discord/Slack chatbots for knowledge base queries
- Python/JavaScript applications needing org-roam access

### GOAP Agent - Knowledge Base Maintenance
- Automated format checking and fixing via LLM
- Intelligent link suggestions using semantic similarity
- Health scoring and corpus analysis
- Scheduled audits and reports
- Autonomous corpus improvement

## How They Work Together

### Example: Automated Note Enhancement

```
1. User creates note in Emacs (or via MCP API)
2. org-roam-semantic generates embedding automatically
3. MCP server provides API access to the note
4. Agent discovers note during next audit
5. Agent analyzes format (LLM), suggests links (via MCP semantic search)
6. Agent generates proposal for improvements
7. User reviews and applies suggestions
```

### Data Flow

- **Embeddings**: Generated by org-roam-semantic, stored in org files, queried via MCP
- **Semantic Search**: org-roam-semantic in Emacs, MCP for API access, Agent via MCP
- **Note Modification**: Agent generates proposals → User approves → Applied via MCP

## Documentation Map

### Getting Started
- [org-roam-semantic](https://github.com/dcruver/org-roam-semantic) - Prerequisite Emacs package
- [MCP Server Setup](mcp/README.md) - API integration
- [Agent Setup](agent/README.md) - Automated maintenance
- [Server Installation](INSTALLATION.md) - Complete stack setup
- [Production Deployments](DEPLOYMENT.md#production-deployments) - Live production configurations

### Architecture & Development
- [ARCHITECTURE.md](ARCHITECTURE.md) - Deep dive into system design
- [DEVELOPMENT.md](DEVELOPMENT.md) - Unified development guide
- [CLAUDE.md](CLAUDE.md) - AI assistant instructions

### Component Documentation
- [MCP: Distribution Guide](mcp/DISTRIBUTION.md)
- [Agent: Implementation Details](agent/CLAUDE.md)

## Requirements

### Prerequisites (All Components)
- **org-roam-ai** packages installed via straight.el
- Emacs 27+ with org-roam
- Emacs server running
- Ollama with nomic-embed-text, llama3.1:8b

### MCP Server
- Python 3.8+
- org-roam-ai packages loaded in Emacs

### Agent
- Java 21+
- Maven 3.6+
- MCP server running
- Ollama with gpt-oss:20b (optional)

## Technology Stack

| Component | Languages | Key Frameworks |
|-----------|-----------|----------------|
| MCP | Python 3.8+ | Starlette, MCP protocol |
| Agent | Java 21 | Spring Boot 3.5, Embabel GOAP, Spring AI |

## Contributing

This is a monorepo containing three integrated projects. When contributing:
- Follow the coding standards for each language/component
- Run tests for affected components
- Update relevant documentation
- See [DEVELOPMENT.md](DEVELOPMENT.md) for detailed guidelines

## License

MIT License

## Acknowledgments

- Built on [org-roam](https://www.orgroam.com/) by Jethro Kuan
- Uses [Ollama](https://ollama.ai/) for local AI/embeddings
- Agent powered by [Embabel GOAP framework](https://embabel.com/)
- MCP protocol by [Anthropic](https://modelcontextprotocol.io/)

---

**Choose your starting point:**
- Need semantic search in Emacs? → [org-roam-semantic](https://github.com/dcruver/org-roam-semantic)
- Building automation workflows? → [mcp/README.md](mcp/README.md)
- Need automated maintenance? → [agent/README.md](agent/README.md)
- Installing the full stack? → [INSTALLATION.md](INSTALLATION.md)
- Understanding the architecture? → [ARCHITECTURE.md](ARCHITECTURE.md)
