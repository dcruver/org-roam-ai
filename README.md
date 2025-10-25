# org-roam-ai

**AI-powered knowledge base maintenance for org-roam**

Transform your org-roam knowledge base with semantic search, intelligent AI assistance, and automated maintenance through a three-tier architecture that combines Emacs integration, API services, and autonomous planning.

## What is org-roam-ai?

org-roam-ai provides AI-powered automation and API services for [org-roam](https://www.orgroam.com/) knowledge bases with [org-roam-semantic](https://github.com/dcruver/org-roam-semantic):

- **MCP Server**: HTTP/stdio API for semantic search, note creation, and automation workflows
- **GOAP Agent**: Autonomous maintenance with LLM-powered format fixing and link suggestions
- **External Integration**: Powers n8n workflows, chatbots, and external tool access

**Prerequisite**: [org-roam-semantic](https://github.com/dcruver/org-roam-semantic) must be installed for semantic search and AI features

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  org-roam-semantic (External Prerequisite)                      │
│  https://github.com/dcruver/org-roam-semantic                   │
│  - Vector search within Emacs                                   │
│  - AI-powered note enhancement                                  │
│  - Embedding storage as org properties                          │
│  - Interactive commands (C-c v, C-c a)                          │
└────────────────────────┬────────────────────────────────────────┘
                         │ emacsclient
┌────────────────────────▼────────────────────────────────────────┐
│  org-roam-mcp (Python) - MCP Server                             │
│  - HTTP/stdio MCP protocol server                               │
│  - Semantic search API (via org-roam-semantic)                  │
│  - Note creation with auto-embedding                            │
│  - Integration with n8n, external tools                         │
└────────────────────────┬────────────────────────────────────────┘
                         │ HTTP JSON-RPC
┌────────────────────────▼────────────────────────────────────────┐
│  org-roam-agent (Java) - GOAP Maintenance Agent                 │
│  - Autonomous corpus maintenance                                │
│  - LLM-powered format analysis & fixing                         │
│  - Link suggestion via semantic search                          │
│  - Health scoring & action planning                             │
│  - Spring Shell interactive CLI                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Component Overview

**[org-roam-semantic](https://github.com/dcruver/org-roam-semantic) - Prerequisite**
- Elisp package providing semantic search and AI assistance
- **Must be installed separately** via straight.el
- Provides the foundation for semantic operations
- See: https://github.com/dcruver/org-roam-semantic

**[mcp/](mcp/README.md) - MCP Server**
- Python-based Model Context Protocol server
- HTTP and stdio transport modes
- Wraps org-roam-semantic functions for API access
- Powers n8n workflows and external integrations

**[agent/](agent/README.md) - Maintenance Agent**
- Java/Spring Boot GOAP planning agent
- Autonomous knowledge base maintenance
- LLM-powered formatting, linking, and health analysis
- Calls MCP server for semantic operations

## Quick Start

### Prerequisites

**Required for all components:**
1. **[org-roam-semantic](https://github.com/dcruver/org-roam-semantic)** - Install first via straight.el:
   ```elisp
   (straight-use-package
     '(org-roam-semantic :host github :repo "dcruver/org-roam-semantic"))

   (require 'org-roam-vector-search)
   (require 'org-roam-ai-assistant)
   ```

2. **Ollama** with required models:
   ```bash
   ollama pull nomic-embed-text    # For embeddings
   ollama pull llama3.1:8b         # For AI generation
   ollama pull gpt-oss:20b         # For agent (optional)
   ```

3. **Emacs** with **org-roam** configured
4. **Emacs server** running (`M-x server-start`)

### Installation

#### 1. MCP Server (API Access & n8n Integration)
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

#### 2. Maintenance Agent (Automated Corpus Health)
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
- **org-roam-semantic** installed via straight.el
- Emacs 27+ with org-roam
- Emacs server running
- Ollama with nomic-embed-text, llama3.1:8b

### MCP Server
- Python 3.8+
- org-roam-semantic loaded in Emacs

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
