# org-roam-ai

**AI-powered knowledge base maintenance for org-roam**

Transform your org-roam knowledge base with semantic search, intelligent AI assistance, and automated maintenance through a three-tier architecture that combines Emacs integration, API services, and autonomous planning.

## What is org-roam-ai?

org-roam-ai is a comprehensive AI enhancement suite for [org-roam](https://www.orgroam.com/), providing:

- **Semantic Search**: Find notes by meaning, not just keywords, using vector embeddings
- **AI-Powered Assistance**: Context-aware AI that understands your existing knowledge
- **Automated Maintenance**: GOAP-based agent that keeps your knowledge base healthy
- **API Integration**: Clean MCP server for external tools and automation workflows

## Architecture

The system consists of three integrated components:

```
┌─────────────────────────────────────────────────────────────────┐
│                    Emacs Layer (elisp)                          │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ org-roam-semantic                                         │  │
│  │ - Vector search within Emacs                             │  │
│  │ - AI-powered note enhancement                            │  │
│  │ - Embedding storage as org properties                    │  │
│  │ - Interactive commands (C-c v, C-c a)                    │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ emacsclient
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    MCP Server Layer (Python)                    │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ org-roam-mcp                                              │  │
│  │ - HTTP/stdio MCP protocol server                         │  │
│  │ - Semantic search API (via org-roam-semantic)            │  │
│  │ - Note creation with auto-embedding                      │  │
│  │ - Integration with n8n, external tools                   │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ HTTP JSON-RPC
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Agent Layer (Java)                           │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ org-roam-agent (Embabel GOAP)                            │  │
│  │ - Autonomous corpus maintenance                          │  │
│  │ - LLM-powered format analysis & fixing                   │  │
│  │ - Link suggestion via semantic search                    │  │
│  │ - Health scoring & action planning                       │  │
│  │ - Spring Shell interactive CLI                           │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Component Overview

**[emacs/](emacs/README.md) - Emacs Package (org-roam-semantic)**
- Elisp package for semantic search and AI assistance within Emacs
- Vector embeddings stored as org-mode properties
- Interactive commands for search, linking, and AI enhancement
- Direct Ollama integration for embeddings and generation

**[mcp/](mcp/README.md) - MCP Server (org-roam-mcp)**
- Python-based Model Context Protocol server
- HTTP and stdio transport modes
- Wraps Emacs functions for external tool integration
- Powers n8n workflows, API integrations, and agent operations

**[agent/](agent/README.md) - Maintenance Agent (org-roam-agent)**
- Java/Spring Boot GOAP planning agent
- Autonomous knowledge base maintenance
- LLM-powered formatting, linking, and health analysis
- Calls MCP server for semantic operations

## Quick Start

### Prerequisites

All three components require:
- **Ollama** installed and running with models:
  ```bash
  ollama pull nomic-embed-text    # For embeddings
  ollama pull llama3.1:8b         # For AI generation
  ollama pull gpt-oss:20b         # For agent LLM operations
  ```
- **Emacs** with **org-roam** configured
- **Emacs server** running (`M-x server-start` or in init file)

### Installation Paths

Choose the components you need:

#### 1. Emacs Package Only (Basic Semantic Search)
```elisp
;; In your Emacs config
(straight-use-package
  '(org-roam-semantic :local-repo "/path/to/org-roam-ai/emacs"))

(require 'org-roam-vector-search)
(require 'org-roam-ai-assistant)

;; Generate embeddings for existing notes
M-x org-roam-semantic-generate-all-embeddings

;; Try semantic search
C-c v s
```
**→ See [emacs/README.md](emacs/README.md) for full setup**

#### 2. + MCP Server (API Access & n8n Integration)
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
**→ See [mcp/README.md](mcp/README.md) for API reference**

#### 3. + Maintenance Agent (Automated Corpus Health)
```bash
# Build agent
cd agent
./mvnw clean package

# Run interactive shell
java -jar target/embabel-note-gardener-*.jar

# Check corpus health
starwars> status

# Generate maintenance plan
starwars> audit
```
**→ See [agent/README.md](agent/README.md) for full guide**

## Use Cases

### For Individual Users (Emacs Package)
- Find related notes by concept, not keywords
- AI-assisted note enhancement using your existing knowledge
- Automatic embedding generation for new notes
- Interactive semantic linking

### For Automation (MCP Server)
- Integrate org-roam with n8n workflows
- API access to semantic search from other tools
- Automated note creation with embeddings
- Matrix/Discord/Slack chatbots for knowledge base queries

### For Knowledge Base Maintenance (Agent)
- Automated format checking and fixing via LLM
- Intelligent link suggestions using semantic similarity
- Health scoring and corpus analysis
- Scheduled audits and reports

## How They Work Together

### Example: Creating a Note with Full AI Enhancement

```
1. User creates note in Emacs (or via MCP API)
2. org-roam-semantic generates embedding automatically
3. MCP server provides API access to the note
4. Agent discovers note during next audit
5. Agent analyzes format (LLM), suggests links (semantic search)
6. Agent generates proposal for improvements
7. User reviews and applies suggestions
```

### Data Flow

- **Embeddings**: Generated by Emacs package, stored in org files, queried via MCP
- **Semantic Search**: Emacs package for interactive use, MCP for API access, Agent for link suggestions
- **Note Modification**: Agent generates proposals → User approves → Emacs/MCP applies changes

## Documentation Map

### Getting Started
- [Emacs Package Setup](emacs/README.md) - Interactive use
- [MCP Server Setup](mcp/README.md) - API integration
- [Agent Setup](agent/README.md) - Automated maintenance

### Architecture & Development
- [ARCHITECTURE.md](ARCHITECTURE.md) - Deep dive into system design
- [DEVELOPMENT.md](DEVELOPMENT.md) - Unified development guide
- [CLAUDE.md](CLAUDE.md) - AI assistant instructions

### Component Documentation
- [Emacs: Vector Search Guide](emacs/docs/org-roam-vector-search.md)
- [Emacs: AI Assistant Guide](emacs/docs/org-roam-ai-assistant.md)
- [MCP: Distribution Guide](mcp/DISTRIBUTION.md)
- [Agent: Implementation Details](agent/IMPLEMENTATION.md)

## Requirements

### Emacs Package
- Emacs 27+ with org-roam
- Ollama with nomic-embed-text, llama3.1:8b

### MCP Server
- Python 3.8+
- Emacs server accessible via emacsclient
- org-roam-semantic loaded in Emacs

### Agent
- Java 21+
- Maven 3.6+
- MCP server running (for semantic operations)
- Ollama with gpt-oss:20b, nomic-embed-text

## Technology Stack

| Component | Languages | Key Frameworks |
|-----------|-----------|----------------|
| Emacs | Elisp | org-roam, Ollama integration |
| MCP | Python 3.8+ | Starlette, MCP protocol |
| Agent | Java 21 | Spring Boot 3.5, Embabel GOAP, Spring AI |

## Contributing

This is a monorepo containing three integrated projects. When contributing:
- Follow the coding standards for each language/component
- Run tests for affected components
- Update relevant documentation
- See [DEVELOPMENT.md](DEVELOPMENT.md) for detailed guidelines

## License

GPL-3.0-or-later (Emacs package)
MIT License (MCP server)
[To be specified] (Agent)

## Acknowledgments

- Built on [org-roam](https://www.orgroam.com/) by Jethro Kuan
- Uses [Ollama](https://ollama.ai/) for local AI/embeddings
- Agent powered by [Embabel GOAP framework](https://embabel.com/)
- MCP protocol by [Anthropic](https://modelcontextprotocol.io/)

---

**Choose your starting point:**
- Want semantic search in Emacs? → [emacs/README.md](emacs/README.md)
- Building automation workflows? → [mcp/README.md](mcp/README.md)
- Need automated maintenance? → [agent/README.md](agent/README.md)
- Understanding the architecture? → [ARCHITECTURE.md](ARCHITECTURE.md)
