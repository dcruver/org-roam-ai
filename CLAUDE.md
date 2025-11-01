# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in the org-roam-ai repository.

## Project Overview

org-roam-ai is a **monorepo** containing integrated components for AI-powered org-roam knowledge base management:

1. **mcp/** - Python MCP server providing API access to org-roam via HTTP/stdio
2. **emacs/** - Integrated Emacs packages providing semantic search and AI assistance

**Integrated Components**: All Emacs packages (org-roam-vector-search, org-roam-ai-assistant, org-roam-api) are included in the monorepo and loaded automatically by the MCP server.

## Documentation Map

**Component-Specific Guides** (detailed implementation):
- **[mcp/CLAUDE.md](mcp/CLAUDE.md)** - MCP server development, tool implementations, Emacs integration
- **[emacs/](emacs/)** - Integrated Emacs packages (org-roam-vector-search, org-roam-ai-assistant, org-roam-api)

**Architecture & Development**:
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Deep dive into system design, data flow, integration patterns
- **[DEVELOPMENT.md](DEVELOPMENT.md)** - Unified development workflows, testing, code style

**User Documentation**:
- **[README.md](README.md)** - User-facing overview and quick start
- **Component READMEs**: [mcp/README.md](mcp/README.md)

**This file focuses on**: Cross-component integration, monorepo workflows, big-picture architecture understanding, and production deployment.

## Production Deployment

### org-roam-mcp-backend

**Integrated deployment** with Emacs and MCP server:

**System Architecture**:
```
┌────────────────────────────────────────────────────────────┐
│         org-roam-mcp-backend                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ MCP Server (Python) → /opt/org-roam-mcp-venv/        │  │
│  │ Emacs + Doom + integrated packages                   │  │
│  │ Notes: /mnt/org-roam/files/ (shared volume)          │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────┘
         │ Ollama API
         ▼
feynman.cruver.network:11434
(nomic-embed-text, llama3.1:8b)
```

**Key Configuration Differences**:
- **Ollama**: Points to `feynman.cruver.network:11434` (not localhost)
- **Shared volume**: `/external-storage/org-roam` mounted at `/mnt/org-roam`
- **Integrated packages**: Emacs packages loaded automatically from monorepo

**Service Management**:
```bash
# On org-roam-mcp-backend
ssh root@192.168.20.136

# MCP Server (always running)
systemctl status org-roam-mcp
journalctl -u org-roam-mcp -f
```

**Configuration Files**:
- MCP: `/opt/org-roam-mcp-venv/` (package-based installation)
- MCP: `/opt/org-roam-mcp-venv/` (package-based installation)
- Emacs: `~/.doom.d` → `/mnt/org-roam/doom` (symlink)

**Important**: `EMACS_SERVER_FILE=/root/emacs-server/server` must be set for all emacsclient calls.

### n8n-backend.cruver.network

**MCP Server only** (upgraded to package-based installation):
- Location: `/opt/org-roam-mcp-venv/`
- Version: 0.1.0 (wheel package)
- Systemd service: `org-roam-mcp.service`
- Used by: n8n workflows for note creation and search
- Old git clone removed, now using proper Python wheel

**Update procedure**:
```bash
# 1. Build wheel locally
cd mcp && python -m build

# 2. Copy to server
scp dist/org_roam_mcp-0.1.0-py3-none-any.whl root@n8n-backend.cruver.network:/tmp/

# 3. Install
ssh root@n8n-backend.cruver.network
systemctl stop org-roam-mcp
/opt/org-roam-mcp-venv/bin/pip install --upgrade /tmp/org_roam_mcp-0.1.0-py3-none-any.whl
systemctl start org-roam-mcp
```

## Quick Reference

**Prerequisites**:
```bash
# 1. Start required services
ollama serve                  # Start Ollama (required for semantic features)
emacs --daemon                # Start Emacs server (if not running)

# 2. Pull required Ollama models
ollama pull nomic-embed-text  # For semantic embeddings
ollama pull llama3.1:8b       # For AI text generation

# 3. Verify setup
emacsclient --eval '(+ 1 1)'              # Should return: 2
curl http://localhost:11434/api/tags      # Should list Ollama models
ollama list | grep "nomic-embed-text"     # Verify embedding model installed
```

**Common Development Commands**:
```bash
# MCP Server (see mcp/CLAUDE.md for details)
cd mcp
source .venv/bin/activate
pip install -e ".[dev]"
python -m org_roam_mcp.server  # Starts on port 8000
pytest                          # Run tests

# Full stack integration test
# Terminal 1: Start MCP
cd mcp && python -m org_roam_mcp.server
# Terminal 2: Test API
curl -X POST http://localhost:8000 -d '{"jsonrpc": "2.0", "method": "tools/list", "id": 1}'
```

## Repository Structure

```
org-roam-ai/
├── CLAUDE.md              This file (monorepo overview)
├── ARCHITECTURE.md        Technical deep dive
├── DEVELOPMENT.md         Development workflows
├── README.md              User-facing overview
│
├── mcp/                   Python MCP server (org-roam-mcp)
│   ├── CLAUDE.md         → Component-specific development guide
│   ├── README.md         → User documentation
│   ├── src/              → Python source code
│   ├── tests/            → Pytest test suite
│   └── pyproject.toml    → Package configuration
│
└── emacs/                 Integrated Emacs packages
    ├── org-roam-api.el          → API functions for MCP
    ├── org-roam-vector-search.el → Semantic search engine
    └── org-roam-ai-assistant.el  → AI enhancement tools
```

**Navigation**: For detailed implementation information, consult component-specific CLAUDE.md files. This file focuses on cross-component integration.

---

## System Architecture (Big Picture)

The integrated two-tier architecture provides semantic search and API access:

```
┌─────────────────────────────────────────────────────────────────┐
│  Integrated Emacs Packages (emacs/)                             │
│  - org-roam-vector-search: Vector embeddings & semantic search  │
│  - org-roam-ai-assistant: AI enhancement & analysis             │
│  - org-roam-api: MCP integration functions                      │
│  - Embedding generation via Ollama                              │
│  - Storage as :PROPERTIES: in org files                         │
│  - Interactive commands (C-c v s, C-c a)                        │
└────────────────────────┬────────────────────────────────────────┘
                         │ emacsclient --eval
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│  org-roam-mcp (Python MCP Server)                               │
│  - HTTP/stdio transport (port 8000)                             │
│  - Loads and wraps integrated elisp functions                  │
│  - Tools: semantic_search, create_note, contextual_search       │
│  - Used by: n8n workflows, external integrations                │
│  See: mcp/CLAUDE.md for implementation details                  │
└─────────────────────────────────────────────────────────────────┘
```

**Key Integration Points**:
1. **org-roam-semantic → Emacs**: Elisp functions for semantic search and embedding generation
2. **MCP → org-roam-semantic**: Python calls elisp via `emacsclient --eval`
3. **Agent → MCP**: Java HTTP client calls MCP server on port 8000
4. **Agent → Ollama**: Direct HTTP calls for LLM-based format analysis

**Data Flow Example** (Agent suggests links):
```
Agent.audit() → CorpusScanner → finds orphan notes
  → SuggestLinksAction.execute()
    → OrgRoamMcpClient.semanticSearch(note.content)
      → HTTP POST to MCP server
        → EmacsClient.call_elisp("my/api-semantic-search", ...)
          → emacsclient --eval '(my/api-semantic-search "..." 5 0.7)'
            → Emacs: org-roam-semantic-get-similar-data
              → Returns JSON with similar notes + scores
        → MCP parses, returns to Agent
    → Agent LLM generates link rationale
    → Creates proposal with diff
```

---

## Cross-Component Workflows

### Workflow 1: Adding New MCP Tool

**When**: Need new org-roam functionality accessible via API

**Steps**:
1. **Add elisp function** in `emacs/` directory
   - Add to appropriate file: `org-roam-api.el`, `org-roam-vector-search.el`, or `org-roam-ai-assistant.el`
   - Must return JSON string
   - Follow existing patterns for function naming and error handling

2. **Add MCP tool** in `mcp/src/org_roam_mcp/server.py`
   - Define with `@mcp.tool()` decorator
   - Call elisp via `emacs_client.call_elisp()`
   - See: mcp/CLAUDE.md for parameter escaping rules

3. **Test via HTTP**:
   ```bash
   curl -X POST http://localhost:8000 -d '{
     "jsonrpc": "2.0",
     "method": "tools/call",
     "params": {"name": "your_tool", "arguments": {...}}
   }'
   ```

4. **Document** in `mcp/README.md`

### Workflow 2: Enhancing AI Assistant Features

**When**: Need new AI-powered enhancement capabilities

**Steps**:
1. **Add elisp function** in `emacs/org-roam-ai-assistant.el`
   - Follow existing patterns for context gathering and AI calls
   - Use `org-roam-ai--get-context-for-note()` for relevant context
   - Implement with `org-roam-ai--call-ai-with-context()`

2. **Add key binding** in the assistant map
   - Update `org-roam-ai-assistant-map` with new command
   - Follow existing naming conventions

3. **Test interactively** in Emacs
   - Load the updated package
   - Test the new functionality on sample notes

3. **Annotate** with `@Component` (Spring auto-registers)

4. **Test** with sample corpus:
   ```bash
   ORG_ROAM_PATH=./samples/notes ./mvnw spring-boot:run
   starwars> audit  # Should show new action in plan
   ```

5. **Document** in `agent/README.md`

### Workflow 3: Changing Embedding Model

**When**: Switch to different Ollama model (e.g., `mxbai-embed-large`)

**Impact**: All three components must be updated

**Steps**:
1. **Pull model**: `ollama pull mxbai-embed-large`

2. **Update org-roam-semantic** (in Emacs config):
   ```elisp
   (setq org-roam-semantic-embedding-model "mxbai-embed-large")
   (setq org-roam-semantic-embedding-dimensions 1024)  ; Check model docs
   ```

3. **Update agent** (`agent/src/main/resources/application.yml`):
   ```yaml
   spring.ai.ollama.embedding.options.model: mxbai-embed-large
   gardener.embedding-model: mxbai-embed-large
   ```

4. **MCP auto-detects** (uses Emacs configuration, no change needed)

5. **Regenerate embeddings**:
   ```elisp
   M-x org-roam-semantic-generate-all-embeddings
   ```

### Workflow 3: Debugging Integration Issues

**Symptom**: MCP errors, Emacs unreachable, or semantic features not working

**Systematic Diagnosis**:
```bash
# 1. Verify Ollama
curl http://localhost:11434/api/tags
ollama list  # Check models installed

# 2. Verify Emacs server
emacsclient --eval '(+ 1 1)'  # Should return: 2
emacsclient --eval '(featurep (quote org-roam))'  # Should return: t

# 3. Verify MCP server
curl http://localhost:8000  # Health check
curl -X POST http://localhost:8000 -d '{
  "jsonrpc": "2.0",
  "method": "tools/list",
  "id": 1
}'  # List available tools

# 4. Test MCP tool manually
curl -X POST http://localhost:8000 -d '{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "search_notes",
    "arguments": {"query": "test"}
  },
  "id": 1
}'

# 5. Check MCP logs
tail -f mcp/org-roam-mcp.log  # If using logging
```

**Common Issues**:
- **MCP → Emacs**: `EMACS_SERVER_FILE` environment variable incorrect
- **Agent → MCP**: `gardener.mcp.base-url` not set or MCP not running
- **org-roam-semantic not loaded**: Check Emacs `*Messages*` buffer for load errors

---

## Key Technical Decisions

### Why Two Integrated Components?

**Simplified Architecture**: Combined semantic features with API access
- Emacs users get semantic search and AI assistance (C-c v s, C-c a)
- Automation users get MCP API (n8n workflows, chatbots)
- Single monorepo reduces complexity and maintenance overhead

**Technology Fit**: Right tools for the job
- Elisp: Best for Emacs integration and org-mode manipulation
- Python: Excellent for API servers and external integrations

**Integrated Evolution**: All components evolve together in the monorepo

### Why Integrated Emacs Packages?

**Eliminates External Dependencies**:
- No need to separately install org-roam-semantic
- All semantic features included in the monorepo
- Easier deployment and version management

**Simplified User Experience**:
- Single installation process
- Consistent versioning across all components
- Automatic package loading by MCP server

### Why Store Embeddings in Org Files?

**Portability**: No external database, notes are self-contained

**Git-trackable**: Embeddings version with content (though large diffs)

**Tool-agnostic**: Any tool can read `:PROPERTIES:` drawer

**Simplicity**: No sync between database and files

### Why Ollama Not Cloud APIs?

**Privacy**: Knowledge base never leaves machine

**Cost**: No per-token fees for embeddings or LLM calls

**Offline**: Works without internet

**Consistency**: All users have same models, reproducible results

**Control**: User chooses models and versions

### Why GOAP for Agent?

**Dynamic planning**: Adapts to current corpus state, not hardcoded rules

**Cost-based selection**: Automatically chooses cheapest plan

**Explainability**: Every action shows preconditions, cost, rationale

**Reassessment**: Re-evaluates after each action (adaptive)

---

## Important Cross-Component Interfaces

### MCP JSON-RPC Protocol

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
      "similarity_cutoff": 0.7
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

**Agent Integration**: `OrgRoamMcpClient.java` handles this protocol

### Ollama HTTP API

**Used by**:
- org-roam-semantic (embeddings + generation)
- Agent (LLM format analysis)

**Embedding Request** (org-roam-semantic):
```http
POST http://localhost:11434/api/embeddings
{
  "model": "nomic-embed-text",
  "prompt": "note content here..."
}
```

**Chat Request** (Agent):
```http
POST http://localhost:11434/api/chat
{
  "model": "gpt-oss:20b",
  "messages": [
    {"role": "system", "content": "You are an org-mode expert..."},
    {"role": "user", "content": "Analyze this file..."}
  ]
}
```

### Org File Format (Shared Understanding)

**All components must respect**:
```org
:PROPERTIES:
:ID: unique-id-12345
:CREATED: [2025-10-21 Tue 10:00]
:EMBEDDING: [0.123, -0.456, ...]  ; org-roam-semantic stores here
:EMBEDDING_MODEL: nomic-embed-text
:EMBEDDING_TIMESTAMP: [2025-10-21 Tue 10:05]
:END:
#+title: Note Title

Content goes here...
```

**Rules**:
- Agent reads/writes directly to filesystem
- MCP reads/writes via Emacs (preserves Emacs state)
- org-roam-semantic manages `:EMBEDDING:` properties
- All must preserve `:PROPERTIES:` drawer structure

---

## Development Checklist

**When adding cross-component features**:

- [ ] Identify which component(s) are affected
- [ ] Check if new MCP tool needed (Workflow 1)
- [ ] Check if new Agent action needed (Workflow 2)
- [ ] Update integration tests (see DEVELOPMENT.md)
- [ ] Update ARCHITECTURE.md if data flow changes
- [ ] Update component-specific CLAUDE.md files
- [ ] Test full stack integration (all components running)
- [ ] Document new integration patterns in this file

**For component-specific work**:
- [ ] Consult component CLAUDE.md (mcp/CLAUDE.md or agent/CLAUDE.md)
- [ ] Follow component coding standards (see DEVELOPMENT.md)
- [ ] Run component-specific tests
- [ ] Update component README if user-facing changes

**Git commits** (Conventional Commits):
```
<type>(<scope>): <description>

feat(mcp): add search_by_tag tool
fix(agent): handle missing embeddings gracefully
docs(architecture): update integration diagrams
chore(ci): add full stack integration test
```

**Scopes**: `mcp`, `agent`, `emacs`, `docs`, `ci`, `integration`

---

## Quick Troubleshooting Guide

| Symptom | Check | Fix |
|---------|-------|-----|
| MCP tools fail | Emacs server running? | `emacsclient --eval '(+ 1 1)'` |
| Agent can't connect | MCP server running? | `curl http://localhost:8000` |
| No semantic search | org-roam-semantic loaded? | Check Emacs `(featurep 'org-roam-semantic)` |
| LLM errors | Ollama running? | `curl http://localhost:11434/api/tags` |
| Missing embeddings | Model installed? | `ollama pull nomic-embed-text` |
| Agent finds no notes | Path correct? | Check `ORG_ROAM_PATH` env var |

**For detailed troubleshooting**: See component-specific CLAUDE.md files and DEVELOPMENT.md

---

## Next Steps

**New to the codebase?**
1. Read [README.md](README.md) for user-facing overview
2. Read [ARCHITECTURE.md](ARCHITECTURE.md) for technical deep dive
3. Choose component: [mcp/CLAUDE.md](mcp/CLAUDE.md) or [agent/CLAUDE.md](agent/CLAUDE.md)
4. Follow [DEVELOPMENT.md](DEVELOPMENT.md) for setup

**Making changes?**
1. Identify affected components (MCP, Agent, or both)
2. Review relevant integration workflows above
3. Consult component CLAUDE.md for implementation details
4. Test integration points (MCP ↔ Emacs, Agent ↔ MCP)
5. Update documentation (this file if integration changes)

**Common tasks**:
- Add MCP tool → Workflow 1 above + [mcp/CLAUDE.md](mcp/CLAUDE.md)
- Add Agent action → Workflow 2 above + [agent/CLAUDE.md](agent/CLAUDE.md)
- Change models → Workflow 3 above
- Debug issues → Workflow 4 above
