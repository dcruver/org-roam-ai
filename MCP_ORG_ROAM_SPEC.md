# MCP/org-roam-semantic Ecosystem Specification

## Overview

The MCP/org-roam-semantic ecosystem is a distributed, AI-powered knowledge management system that integrates Emacs-based org-roam with modern AI tooling through the Model Context Protocol (MCP). This specification defines the architecture, components, deployment patterns, and integration protocols.

## System Architecture

### Three-Tier Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    External AI Agents                           │
│  (n8n, Claude Desktop, custom MCP clients)                     │
└─────────────────────────┬───────────────────────────────────────┘
                          │ MCP Protocol (HTTP/JSON-RPC)
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                 MCP Server Layer (Python)                       │
│  - Protocol translation and tool orchestration                 │
│  - HTTP server on port 8000                                    │
│  - JSON-RPC 2.0 compliance                                     │
└─────────────────────────┬───────────────────────────────────────┘
                          │ emacsclient --eval
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                 Emacs Layer (Elisp)                             │
│  - org-roam knowledge base                                     │
│  - Vector embeddings and semantic search                       │
│  - AI assistance and content generation                        │
└─────────────────────────────────────────────────────────────────┘
```

### Core Principles

1. **Separation of Concerns**: Each layer has distinct responsibilities
2. **Protocol Standardization**: MCP provides consistent AI tool integration
3. **Local AI Processing**: vLLM + Infinity ensures privacy and cost control
4. **Portable Knowledge**: Embeddings stored in org files, not external databases
5. **Modular Deployment**: Components can be deployed independently

## Component Specifications

### 1. Emacs Layer (org-roam-semantic)

**Technology**: Emacs Lisp (Emacs 27+)
**Location**: `packages/org-roam-ai/`
**Repository**: https://github.com/dcruver/org-roam-ai/tree/main/packages/org-roam-ai

#### Components

**org-roam-vector-search.el**
- **Purpose**: Semantic search using vector embeddings
- **Key Functions**:
  - `org-roam-semantic-search`: Cosine similarity search
  - `org-roam-semantic-generate-embeddings`: Batch embedding generation
  - `org-roam-semantic-get-similar-data`: Find related notes
- **Dependencies**: Infinity embedding service (nomic-ai/nomic-embed-text-v1.5 model)
- **Data Storage**: Embeddings stored as `:EMBEDDING:` properties in org files

**org-roam-ai-assistant.el**
- **Purpose**: AI-powered content assistance and enhancement
- **Key Functions**:
  - `org-roam-ai-explain`: Explain concepts using related notes
  - `org-roam-ai-enhance`: Improve content with AI suggestions
  - `org-roam-ai-suggest-links`: Propose connections
- **Dependencies**: vLLM (casperhansen/llama-3.3-70b-instruct-awq model)
- **Integration**: Uses vector search for context gathering

**org-roam-api.el**
- **Purpose**: MCP-compatible API functions
- **Key Functions**:
  - `my/api-semantic-search`: MCP tool for semantic search
  - `my/api-create-note`: MCP tool for note creation
  - `my/api-contextual-search`: MCP tool for enhanced search
  - `my/add-daily-entry-structured`: MCP tool for daily entries
- **Design**: Returns JSON strings for MCP consumption

#### Data Format

**Embedding Storage**:
```org
:PROPERTIES:
:ID: abc123-def456-ghi789
:CREATED: [2025-10-20 Mon 09:00]
:EMBEDDING: [0.123, -0.456, 0.789, ...]  ; 768-dim vector
:EMBEDDING_MODEL: nomic-embed-text
:EMBEDDING_TIMESTAMP: [2025-10-20 Mon 09:15]
:END:
```

**Chunked Sections**:
```org
** Section Heading
:PROPERTIES:
:CHUNK_ID: chunk-abc123-1
:EMBEDDING: [0.234, -0.567, 0.890, ...]
:EMBEDDING_MODEL: nomic-embed-text
:EMBEDDING_TIMESTAMP: [2025-10-20 Mon 09:16]
:END:
```

### 2. MCP Server Layer (org-roam-mcp)

**Technology**: Python 3.8+, Starlette, MCP SDK
**Location**: `mcp/`
**Repository**: https://github.com/dcruver/org-roam-ai/tree/main/mcp
**PyPI Package**: `org-roam-mcp` (v0.1.16)

#### Architecture

**Dual Transport Support**:
1. **HTTP Mode** (Default): JSON-RPC 2.0 on port 8000
2. **stdio Mode**: Standard I/O for MCP-compatible clients

**Core Components**:

**EmacsClient** (`emacs_client.py`)
- **Purpose**: Execute elisp functions via emacsclient
- **Key Features**:
  - Server file detection (`~/.emacs.d/server/server`)
  - Parameter escaping for shell safety
  - JSON response parsing
  - 30-second timeout handling
- **Command Pattern**: `emacsclient --server-file=<path> --eval '(function args)'`

**MCP Server** (`server.py`)
- **Purpose**: Tool registration and protocol handling
- **Transports**:
  - HTTP: Starlette application with JSON-RPC endpoints
  - stdio: MCP SDK server for direct client integration
- **Health Check**: `GET /` returns "OK"

#### MCP Tools

| Tool | Elisp Function | Description |
|------|----------------|-------------|
| `semantic_search` | `my/api-semantic-search` | Vector similarity search |
| `contextual_search` | `my/api-contextual-search` | Keyword search with full context |
| `search_notes` | `my/api-search-notes` | Basic title/content search |
| `create_note` | `my/api-create-note` | Create note with auto-embedding |
| `add_daily_entry` | `my/add-daily-entry-structured` | Add structured daily entries |
| `get_daily_content` | `my/get-daily-note-content` | Retrieve daily note content |
| `generate_embeddings` | `my/api-generate-embeddings` | Generate missing embeddings |

#### JSON-RPC Protocol

**Request Format**:
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "semantic_search",
    "arguments": {
      "query": "container networking",
      "limit": 5,
      "cutoff": 0.7
    }
  },
  "id": 1
}
```

**Response Format**:
```json
{
  "jsonrpc": "2.0",
  "result": {
    "notes": [
      {
        "file": "/path/to/note.org",
        "title": "Docker Networking",
        "similarity": 0.85,
        "content": "..."
      }
    ]
  },
  "id": 1
}
```

### 3. AI Services Layer (vLLM + Infinity)

**Technology**: Local LLM server
**Purpose**: Privacy-preserving AI processing
**Models**:
- `nomic-embed-text`: 768-dimensional embeddings
- `llama3.1:8b`: Text generation and analysis

#### Integration Points

**Embedding Generation**:
```http
POST http://localhost:8080/embeddings
{
  "model": "nomic-embed-text",
  "prompt": "note content here..."
}
```

**Text Generation**:
```http
POST http://localhost:8000/v1/chat/completions
{
  "model": "llama3.1:8b",
  "messages": [
    {"role": "system", "content": "You are an org-mode expert..."},
    {"role": "user", "content": "Analyze this content..."}
  ]
}
```

## Code Repositories

### Primary Repository
**org-roam-ai** (Monorepo)
- **URL**: https://github.com/dcruver/org-roam-ai
- **Structure**:
  ```
  org-roam-ai/
  ├── mcp/                    # Python MCP server
  ├── packages/org-roam-ai/   # Emacs packages
  ├── scripts/               # Deployment scripts
  └── docs/                  # Documentation
  ```

### Package Repositories
**org-roam-mcp** (PyPI)
- **Package**: `org-roam-mcp`
- **Version**: 0.1.16
- **Installation**: `pip install org-roam-mcp`
- **Source**: https://pypi.org/project/org-roam-mcp/

## Deployment Locations

### Development Environment
**Local Development**
- **MCP Server**: `~/.org-roam-mcp/` (virtual environment)
- **Emacs Packages**: Installed via straight.el from GitHub
- **vLLM**: `localhost:8000` (text generation)
- **Infinity**: `localhost:8080` (embeddings)
- **Emacs Server**: `~/.emacs.d/server/server`

### Production Deployments

#### org-roam-mcp-backend.cruver.network
**Full Stack Deployment**
- **IP**: 192.168.20.136
- **Components**:
  - Emacs + Doom + integrated packages
  - MCP server (systemd service)
  - AI services (vLLM + Infinity)
- **Configuration**:
  - vLLM URL: `localhost:8000`, Infinity URL: `localhost:8080`
  - Shared volume: `/mnt/org-roam` (mounted from `/external-storage/org-roam`)
  - Emacs server file: `/root/emacs-server/server`
- **Service Management**:
  ```bash
  systemctl status org-roam-mcp
  journalctl -u org-roam-mcp -f
  ```

#### n8n-backend.cruver.network
**MCP Server Only**
- **Components**: MCP server only (no Emacs)
- **Installation**: Package-based (`/opt/org-roam-mcp-venv/`)
- **Version**: 0.1.0 (wheel package)
- **Purpose**: n8n workflow integration
- **Update Process**:
  ```bash
  # Build wheel locally
  cd mcp && python -m build
  # Deploy to server
  scp dist/org_roam_mcp-0.1.0-py3-none-any.whl root@n8n-backend.cruver.network:/tmp/
  # Install
  systemctl stop org-roam-mcp
  /opt/org-roam-mcp-venv/bin/pip install --upgrade /tmp/org_roam_mcp-0.1.0-py3-none-any.whl
  systemctl start org-roam-mcp
  ```

## Integration Patterns

### Data Flow: AI Agent → Knowledge Base

```
1. AI Agent (n8n/Claude) → HTTP POST to MCP server
2. MCP Server validates request → calls EmacsClient
3. EmacsClient → emacsclient --eval '(my/api-semantic-search "query")'
4. Emacs runs elisp function → queries vector embeddings
5. Infinity generates embeddings (if needed)
6. Results returned as JSON → MCP server → AI agent
```

### Component Communication

**MCP Server ↔ Emacs**:
- Transport: `emacsclient` command execution
- Protocol: Elisp function calls with JSON parameters/results
- Error Handling: Timeout (30s), JSON parsing, character array handling

**Emacs ↔ AI Services**:
- Transport: HTTP POST to OpenAI-compatible APIs
- Models: `nomic-embed-text` (embeddings), `llama3.1:8b` (generation)
- Caching: Embeddings stored in org file properties

**External Clients ↔ MCP Server**:
- Transport: HTTP JSON-RPC 2.0 or stdio
- Discovery: `tools/list` endpoint for available tools
- Validation: JSON Schema parameter validation

## Configuration Management

### Environment Variables

**MCP Server**:
- `EMBEDDING_URL`: Embedding service URL (default: `http://localhost:8080`)
- `GENERATION_URL`: Text generation URL (default: `http://localhost:8000/v1`)
- `EMBEDDING_MODEL`: Embedding model (default: `nomic-ai/nomic-embed-text-v1.5`)
- `GENERATION_MODEL`: Generation model (default: `casperhansen/llama-3.3-70b-instruct-awq`)
- `EMACS_SERVER_FILE`: Emacs server socket path

**Emacs Packages**:
- `org-roam-semantic-embedding-url`: Embedding service URL
- `org-roam-semantic-generation-url`: Text generation URL
- `org-roam-semantic-embedding-model`: Embedding model name
- `org-roam-semantic-generation-model`: Generation model name
- `org-roam-directory`: Path to org-roam notes

### Installation Methods

**One-Command Installation**:
```bash
curl -fsSL https://raw.githubusercontent.com/dcruver/org-roam-ai/main/install-mcp-standalone.sh | bash
```

**PyPI Installation**:
```bash
pip install org-roam-mcp
```

**Development Installation**:
```bash
pip install git+https://github.com/dcruver/org-roam-ai.git#subdirectory=mcp
```

## Security Considerations

### Local AI Processing
- All AI processing happens locally via vLLM + Infinity
- No external API keys or cloud dependencies
- Knowledge base content never leaves the local network

### Access Control
- MCP server binds to localhost by default
- Emacs server file permissions restrict access
- No authentication required for local development

### Data Privacy
- Embeddings and content stored in local org files
- No telemetry or external data collection
- All processing contained within deployment boundaries

## Monitoring and Maintenance

### Health Checks

**MCP Server**: `GET http://localhost:8000` → "OK"
**Emacs Server**: `emacsclient --eval '(+ 1 1)'` → 2
**vLLM**: `curl http://localhost:8000/v1/models`
**Infinity**: `curl http://localhost:8080/models`

### Log Locations

**MCP Server**: stdout/stderr (when run manually)
**Systemd**: `journalctl -u org-roam-mcp`
**Emacs**: `*Messages*` buffer, `~/.emacs.d/.log`

### Update Procedures

**MCP Server**:
```bash
# Via PyPI
pip install --upgrade org-roam-mcp

# Via wheel
pip install --upgrade /path/to/org_roam_mcp-0.1.x-py3-none-any.whl
```

**Emacs Packages**:
```bash
# Via straight.el
M-x straight-pull-package org-roam-vector-search
M-x straight-pull-package org-roam-ai-assistant
M-x straight-pull-package org-roam-api
```

## Future Extensions

### Planned Components
- **Agent Layer**: GOAP-based autonomous maintenance (Java/Spring)
- **Web Interface**: REST API for web-based access
- **Multi-user Support**: Database-backed shared knowledge bases
- **Plugin Architecture**: Extensible tool system

### Protocol Extensions
- **Streaming Responses**: Real-time tool execution updates
- **Batch Operations**: Multi-note processing
- **Custom Models**: Support for additional OpenAI-compatible models
- **Federation**: Cross-instance knowledge sharing

---

**Version**: 1.0
**Last Updated**: November 2025
**Authors**: dcruver
**License**: MIT</content>
<parameter name="filePath">MCP_ORG_ROAM_SPEC.md