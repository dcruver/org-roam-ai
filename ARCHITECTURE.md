# org-roam-ai Architecture

**[org-roam-ai](README.md) > Architecture**

This document provides a deep technical overview of how the three components of org-roam-ai integrate to provide semantic search, AI assistance, and automated knowledge base maintenance.

## System Overview

org-roam-ai is a **three-tier architecture** designed for modularity and separation of concerns:

1. **Emacs Layer** - User interaction and embedding storage
2. **MCP Server Layer** - API gateway and protocol translation
3. **Agent Layer** - Autonomous planning and maintenance

Each layer can function independently, but together they provide a comprehensive AI-powered knowledge management solution.

## Component Architecture

### Layer 1: Emacs Package (org-roam-semantic)

**Technology**: Emacs Lisp
**Location**: `emacs/`
**Purpose**: Interactive semantic search and AI assistance within Emacs

#### Core Components

**org-roam-vector-search.el**
- Vector embedding generation via Ollama HTTP API
- Cosine similarity search in-memory
- Embedding storage as `:EMBEDDING:` org properties
- Automatic embedding generation on save (via `before-save-hook`)
- Support for both file-level and section-level (chunking) embeddings

**org-roam-ai-assistant.el**
- Context-aware AI assistance using related notes
- Multiple workflows: explain, enhance, suggest connections
- Retrieves similar notes via vector search
- Sends context + prompt to Ollama for generation

#### Key Design Decisions

**Why store embeddings in org files?**
- No external database required
- Portable with your notes
- Git-trackable (though large)
- Directly accessible to any tool reading org files

**Why Ollama?**
- Runs locally (privacy, no API costs)
- Consistent embedding model (nomic-embed-text)
- Same infrastructure for embeddings + generation

#### Data Storage Format

```org
:PROPERTIES:
:ID: abc123-def456-ghi789
:CREATED: [2025-10-20 Mon 09:00]
:EMBEDDING: [0.123, -0.456, 0.789, ...]  ; 768-dimensional vector
:EMBEDDING_MODEL: nomic-embed-text
:EMBEDDING_TIMESTAMP: [2025-10-20 Mon 09:15]
:END:
```

For chunked sections:
```org
** Section Heading
:PROPERTIES:
:CHUNK_ID: chunk-abc123-1
:EMBEDDING: [0.234, -0.567, 0.890, ...]
:EMBEDDING_MODEL: nomic-embed-text
:EMBEDDING_TIMESTAMP: [2025-10-20 Mon 09:16]
:END:
```

#### API Integration

- **HTTP calls to Ollama**: Uses built-in `url-retrieve-synchronously`
- **JSON parsing**: Native `json-read` and `json-encode`
- **Org-to-markdown conversion**: `ox-md` for content preprocessing
- **Timeout handling**: 30-second default for API calls

---

### Layer 2: MCP Server (org-roam-mcp)

**Technology**: Python 3.8+, Starlette, MCP SDK
**Location**: `mcp/`
**Purpose**: Protocol translation between external tools and Emacs

#### Dual-Mode Architecture

The MCP server supports two transport modes:

**1. HTTP Mode (Default)**
- Runs on port 8000
- JSON-RPC 2.0 protocol
- Used by n8n AI Agent nodes, web apps, REST clients
- Health check endpoint: `GET http://localhost:8000`

**2. stdio Mode**
- Standard input/output communication
- Used by MCP-compatible clients
- Launched by client process with stdio transport

**Implementation**: Both modes share the same tool implementations via `server.py`

#### Core Components

**EmacsClient** (`emacs_client.py`)
- Executes elisp via `emacsclient --server-file=<path> --eval`
- Parameter escaping for shell safety
- JSON response parsing (handles Emacs character arrays)
- 30-second timeout with error handling

**MCP Server** (`server.py`)
- `mcp.server.Server` for stdio protocol
- Starlette app for HTTP JSON-RPC
- Tool registration with JSON schemas
- Unified error handling across transports

**Tool Interface**
- Each tool maps to an elisp function in `org-roam-api.el`
- Strict parameter validation
- Structured JSON responses

#### Available MCP Tools

| Tool | Elisp Function | Purpose |
|------|----------------|---------|
| `semantic_search` | `my/api-semantic-search` | Vector similarity search via org-roam-semantic |
| `contextual_search` | `my/api-contextual-search` | Keyword-based search with full context |
| `search_notes` | `my/api-search-notes` | Basic title/content search |
| `create_note` | `my/api-create-note` | Create note with auto-embedding |
| `add_daily_entry` | `my/add-daily-entry-structured` | Add structured daily journal entries |
| `get_daily_content` | `my/get-daily-note-content` | Retrieve daily note content |
| `sync_database` | `org-roam-db-sync` | Force org-roam database sync |

#### Data Flow: External Tool → Emacs

```
1. n8n AI Agent makes HTTP POST to localhost:8000
2. Starlette receives JSON-RPC request
3. Server validates tool name and parameters
4. EmacsClient constructs escaped elisp expression
5. Executes: emacsclient --eval '(my/api-search-notes "query")'
6. Emacs runs function, returns JSON string
7. EmacsClient parses JSON, handles character arrays
8. Server formats response as JSON-RPC result
9. n8n receives structured data
```

#### Why MCP Protocol?

- **Standardized**: Industry-standard protocol for AI tool integration
- **Typed**: JSON schemas for all parameters and responses
- **Discoverable**: Tools are self-documenting via `tools/list`
- **Flexible**: Supports both HTTP and stdio transports

---

### Layer 3: Maintenance Agent (org-roam-agent)

**Technology**: Java 21, Spring Boot 3.5, Embabel GOAP framework, Spring AI
**Location**: `agent/`
**Purpose**: Autonomous knowledge base maintenance via goal-oriented planning

#### GOAP Architecture (Goal-Oriented Action Planning)

**World State**: `CorpusState`
- Per-note metadata: embeddings, format status, link count, health score
- Corpus-wide statistics: total notes, orphans, health distribution

**Goals**: Desired states to achieve
- `MaintainHealthyCorpus`: Overall health ≥ target (e.g., 90/100)
- `EnsureEmbeddingsFresh`: All notes have recent embeddings
- `EnforceFormattingPolicy`: All notes properly formatted
- `ReduceOrphans`: Minimize isolated notes

**Actions**: State transformations
- `ComputeEmbeddings`: Generate missing/stale embeddings (via MCP)
- `NormalizeFormatting`: Fix org structure via LLM analysis
- `SuggestLinks`: Propose connections via semantic search (via MCP)
- `FixDeadLinks`: Retry HTTP links, create issue records
- `QueueStaleReview`: Flag notes needing attention

**Planner**: `GOAPPlanner`
1. Evaluate current state via `CorpusScanner`
2. Check which goals are unsatisfied
3. Find actions whose preconditions are met
4. Calculate costs for each action
5. Sort by safety (safe first) then cost (cheaper first)
6. Generate `ActionPlan` with prioritized actions
7. Execute actions, reassess state after each

#### LLM Integration

**OllamaChatService**
- Format analysis: "Analyze this org-mode file structure..."
- Format fixing: "Fix formatting issues while preserving content..."
- Link rationale: "Explain why these notes should be linked..."

**Spring AI Configuration**
- `ChatModel` bean via `OllamaApi`
- `EmbeddingModel` bean (not actively used - delegates to MCP)
- Model configuration via `application.yml`

**LLM Use Cases in Agent**
1. **Format Analysis** (audit phase)
   - `CorpusScanner` calls `OllamaChatService.analyzeOrgFormatting()`
   - Detects missing `:PROPERTIES:`, malformed headings, etc.
   - Caches results in `NoteMetadata`

2. **Format Fixing** (action execution)
   - `NormalizeFormattingAction` calls `OllamaChatService.normalizeOrgFormatting()`
   - LLM returns corrected org content
   - Preserves all information, fixes structure

3. **Link Rationale** (proposal generation)
   - `SuggestLinksAction` calls MCP for semantic search
   - LLM generates explanation for each suggested link
   - Included in proposal for human review

#### MCP Integration

**Why does the agent call MCP instead of Emacs directly?**
1. **Reuse**: org-roam-mcp already has working semantic search
2. **Separation**: Agent focuses on planning, MCP on org-roam operations
3. **Language strengths**: Java for GOAP, Python for Emacs integration
4. **No duplication**: Avoid reimplementing embedding queries

**MCP Client** (`OrgRoamMcpClient.java` - ✅ Implemented)
```java
// HTTP JSON-RPC client for MCP server
@Service
public class OrgRoamMcpClient {
  // Semantic search via MCP
  POST http://localhost:8000
  {
    "method": "tools/call",
    "params": {
      "name": "semantic_search",
      "arguments": {"query": "note content", "limit": 5}
    }
  }

  // Response with similar notes
  Response: {
    "result": {
      "notes": [
        {"file": "abc.org", "title": "...", "similarity": 0.85},
        ...
      ]
    }
  }
}
```

**Implementation Status**: ✅ Fully working
- HTTP client with configurable base URL and timeout
- Semantic search for link suggestions
- Contextual search with full note content
- Server health checking and availability detection
- Used by `SuggestLinks` action

#### Health Score Calculation

**Components** (total: 100 points)
- Formatting: 10 points (has properties drawer, title, final newline)
- Provenance: 25 points (has :ID:, :CREATED:, proper metadata)
- Embeddings freshness: 15 points (has embedding, not stale)
- Links: 20 points (not orphan, reasonable link density)
- Taxonomy: 10 points (tags are canonical)
- Contradictions: 10 points (inverse - fewer is better)
- Staleness: 10 points (inverse - recently modified is better)

**Calculation**: `HealthScoreCalculator.calculateNoteHealth(NoteMetadata)`

#### Action Safety Classification

**Safe Actions** (auto-applied in auto mode)
- Read-only analysis
- Format normalization (structure only)
- Embedding generation (additive)
- Metadata updates

**Proposal Actions** (require human approval)
- Content modification
- Link additions (changes semantics)
- Note splits/merges
- Tag changes with ambiguity

#### Spring Shell Interface

**Commands** (defined in `GardenerShellCommands.java`)
- `status` - Display corpus health and statistics
- `audit` - Scan corpus with LLM format checking, generate plan
- `execute` - Run the current action plan
- `apply safe` - Execute only safe actions
- `proposals list` - Show all pending proposals
- `proposals show <id>` - Display proposal details with diff
- `proposals apply <id>` - Apply a specific proposal
- `report` - Generate daily org-mode report

**Shell Modes**
- Interactive (default): User-driven command execution
- Non-interactive: For CI/testing via stdin or script files

---

## Integration Patterns

### Pattern 1: Emacs User → Direct Semantic Search

```
User in Emacs
  ↓ C-c v s (org-roam-semantic-search)
  ↓ org-roam-vector-search.el
  ↓ Generate query embedding via Ollama
  ↓ Load all note embeddings from :PROPERTIES:
  ↓ Calculate cosine similarities
  ↓ Display results in clickable buffer
```

**Key**: No network calls beyond Ollama, all data in org files

---

### Pattern 2: n8n Workflow → MCP → Emacs

```
n8n receives message
  ↓ AI Agent Node (configured with MCP tools)
  ↓ HTTP POST to localhost:8000
  ↓ MCP Server receives JSON-RPC request
  ↓ Validates tool and parameters
  ↓ EmacsClient constructs elisp
  ↓ emacsclient --eval '(my/api-semantic-search "query" 5 0.7)'
  ↓ Emacs: org-roam-semantic loads embeddings
  ↓ Emacs: Calculates similarities, returns JSON
  ↓ MCP parses JSON, formats as MCP response
  ↓ n8n receives structured results
  ↓ n8n formats response to user
```

**Key**: MCP handles protocol translation, Emacs does actual search

---

### Pattern 3: Agent → MCP → Emacs (Automated Maintenance)

```
Agent scheduled audit
  ↓ CorpusScanner reads all .org files from filesystem
  ↓ For each note: Check format via LLM (OllamaChatService)
  ↓ Build CorpusState with health metrics
  ↓ GOAPPlanner generates action plan
  ↓ User reviews plan in shell: `proposals list`
  ↓ User approves: `proposals apply <id>`
  ↓
  ↓ SuggestLinks action executes:
  ↓   For orphan note:
  ↓     Read note content from filesystem
  ↓     HTTP POST to MCP: semantic_search tool
  ↓     MCP → emacsclient → Emacs semantic search
  ↓     Emacs returns similar notes with scores
  ↓     Agent LLM generates link rationale
  ↓     Create proposal with diff showing link additions
  ↓
  ↓ NormalizeFormatting action executes:
  ↓   Read note content from filesystem
  ↓   OllamaChatService.normalizeOrgFormatting()
  ↓   LLM returns corrected content
  ↓   Write back to filesystem (auto-applied)
  ↓   Create backup in .gardener/backups/
```

**Key**: Agent uses MCP for semantic ops, LLM for format/rationale, filesystem for reads/writes

---

## Data Storage & Synchronization

### Embedding Storage

**Primary Storage**: Org file `:EMBEDDING:` properties (managed by emacs package)

**Retrieval Paths**:
1. **Emacs**: Direct property read from current buffer or org-roam-db query
2. **MCP**: Via emacsclient calling elisp that reads properties
3. **Agent**: Queries via MCP HTTP API (does not read properties directly)

**Synchronization**: Embeddings are regenerated when:
- Note content changes (auto via `before-save-hook` in Emacs)
- Embedding is older than `max-age-days` (agent audit finds stale)
- Manual trigger: `M-x org-roam-semantic-generate-all-embeddings`

### Database Layers

**org-roam SQLite database**
- Managed by org-roam
- Contains: nodes, links, tags, backlinks
- Updated via `org-roam-db-sync`
- Used by: All three components

**Agent local database** (planned)
- SQLite: `~/.gardener/embeddings.db`
- Caches embedding metadata for audit performance
- Not the source of truth (org files are)

### File System Operations

| Component | Reads | Writes | Method |
|-----------|-------|--------|--------|
| Emacs | Current buffer | Current buffer | Native elisp |
| MCP | Via emacsclient | Via emacsclient | Elisp functions |
| Agent | Direct filesystem | Direct filesystem | Java NIO |

**Concurrency Handling**
- Agent creates backups before writes
- MCP uses Emacs locking (single-threaded emacsclient)
- No concurrent writes to same file (enforced by workflow)

---

## Security & Safety

### Emacs Package
- Local Ollama only (no cloud API)
- No external network calls beyond Ollama
- Embeddings stored in user's org files (privacy)

### MCP Server
- Input sanitization: All elisp parameters escaped via `_escape_for_elisp()`
- No arbitrary code execution: Only predefined elisp functions
- Server file path validation (prevents directory traversal)
- Timeout enforcement: 30-second max per emacsclient call

### Agent
- Safe-by-default: Dry-run mode by default (no file modification)
- Backup system: Timestamped copies before any write
- Proposal system: Risky changes require human approval
- Opt-out: `#agents:off` tag prevents modifications
- Read-only sources: Notes tagged as Source are content-immutable

---

## Performance Considerations

### Emacs Package
- **Embedding generation**: ~1-2 seconds per note (Ollama API call)
- **Similarity search**: In-memory, sub-second for hundreds of notes
- **Batch processing**: `generate-all-embeddings` processes sequentially

**Optimization**: Only regenerate embeddings when content changes

### MCP Server
- **API latency**: ~1-3 seconds per search (emacsclient + Emacs processing)
- **Bottleneck**: Sequential elisp execution in Emacs
- **Timeout**: 30 seconds prevents hung requests

**Optimization**: Keep Emacs server running, avoid database sync on every call

### Agent
- **Audit scan**: 15-30 seconds for ~100 notes (includes LLM format checks)
- **LLM calls**: ~2-5 seconds per note for format analysis
- **Batch operations**: Processes notes sequentially to avoid overwhelming Ollama

**Optimization**: Cache format check results, only re-analyze when file modified

---

## Extension Points

### Adding New MCP Tools

1. Define elisp function in `emacs/org-roam-api.el`
2. Add tool definition in `mcp/src/org_roam_mcp/server.py`
3. Implement handler in `mcp/src/org_roam_mcp/tools/`
4. Update MCP README with tool documentation

### Adding New Agent Actions

1. Create action class extending `com.embabel.agent.core.action.Action<CorpusState>`
2. Define preconditions (when it can execute)
3. Define effects (what state changes)
4. Implement cost calculation
5. Implement execute logic (may call MCP, LLM, or filesystem)
6. Annotate with `@Component` (auto-registered by Spring)

### Adding New Ollama Models

**Emacs**: Update `org-roam-semantic-embedding-model` or `org-roam-semantic-generation-model`

**MCP**: No config change needed (uses Emacs models)

**Agent**: Update `application.yml`:
```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          model: llama3:70b  # New model
```

---

## Failure Modes & Recovery

### Ollama Unavailable

**Emacs**: Graceful degradation - search/AI features disabled, manual usage unaffected
**MCP**: Returns error to client with explanation
**Agent**: Skips embedding-related actions, continues with format checks

### Emacs Server Down

**Emacs**: N/A (runs in Emacs)
**MCP**: Connection error returned to API caller
**Agent**: Cannot perform semantic search, falls back to filesystem-only operations

### Corrupted Embeddings

**Emacs**: `M-x org-roam-semantic-generate-all-embeddings` regenerates
**MCP**: Individual note regeneration via `create_note` update
**Agent**: Audit detects missing/invalid embeddings, queues for regeneration

### Invalid Org Format

**Emacs**: LLM format fixing via AI assistant
**MCP**: Returns parse error to caller
**Agent**: `NormalizeFormattingAction` fixes via LLM, creates backup

---

## Production Deployment Architecture

### Deployment Pattern: Dedicated Agent Server

The recommended production deployment uses a **dedicated LXC container** for the full org-roam-ai stack, separate from n8n or other services.

#### org-roam-agent-backend (192.168.20.136)

**Full Stack Deployment** on dedicated Proxmox LXC container:

```
┌─────────────────────────────────────────────────────────────┐
│          org-roam-agent-backend (192.168.20.136)            │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Agent (Java + Spring Boot + Embabel GOAP)            │  │
│  │ • Location: /opt/org-roam-agent/                    │  │
│  │ • JAR: embabel-note-gardener-0.1.0-SNAPSHOT.jar      │  │
│  │ • Config: application.yml                            │  │
│  │ • Systemd: org-roam-agent-audit.timer (2 AM daily)   │  │
│  └──────────────────┬───────────────────────────────────┘  │
│                     │ HTTP calls                            │
│                     ▼                                        │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ MCP Server (Python HTTP)                             │  │
│  │ • Location: /opt/org-roam-mcp-venv/                 │  │
│  │ • Port: 8000                                         │  │
│  │ • Service: org-roam-mcp.service                      │  │
│  └──────────────────┬───────────────────────────────────┘  │
│                     │ emacsclient                           │
│                     ▼                                        │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Emacs + Doom + org-roam + org-roam-semantic          │  │
│  │ • Socket: /root/emacs-server/server                  │  │
│  │ • Notes: /mnt/org-roam/files/ (shared volume)       │  │
│  │ • Config: /mnt/org-roam/doom/ (shared)              │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
         │                                      │
         │ Ollama API                          │ Notes volume
         ▼                                      ▼
feynman.cruver.network:11434        /external-storage/org-roam
```

**Key Configuration**:
- **Ollama**: Remote instance on `feynman.cruver.network:11434`
- **Shared Volume**: `/external-storage/org-roam` mounted at `/mnt/org-roam`
  - Notes: `/mnt/org-roam/files/`
  - Doom config: `/mnt/org-roam/doom/`
- **Environment**: `EMACS_SERVER_FILE=/root/emacs-server/server`
- **Profile**: `dry-run` (default, requires user approval for changes)

**Service Management**:
```bash
# MCP Server
systemctl status org-roam-mcp
journalctl -u org-roam-mcp -f

# Agent (nightly audits via timer)
systemctl status org-roam-agent-audit.timer
systemctl list-timers
journalctl -u org-roam-agent-audit -f

# Manual agent execution
cd /opt/org-roam-agent
java -Dspring.shell.interactive.enabled=false \
     -Dspring.shell.command.script.enabled=true \
     -jar embabel-note-gardener-0.1.0-SNAPSHOT.jar \
     status  # or audit, execute, etc.
```

#### n8n-backend (n8n-backend.cruver.network)

**MCP Server Only** for n8n AI Agent integration:

```
┌─────────────────────────────────────────────────────────────┐
│          n8n-backend.cruver.network                          │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ n8n Workflows                                         │  │
│  │ • AI Agent nodes                                      │  │
│  │ • Chatbot integrations                               │  │
│  └──────────────────┬───────────────────────────────────┘  │
│                     │ HTTP JSON-RPC                         │
│                     ▼                                        │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ MCP Server (Python HTTP)                             │  │
│  │ • Location: /opt/org-roam-mcp-venv/                 │  │
│  │ • Port: 8000                                         │  │
│  │ • Service: org-roam-mcp.service                      │  │
│  └──────────────────┬───────────────────────────────────┘  │
│                     │ emacsclient                           │
│                     ▼                                        │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Emacs + Doom + org-roam + org-roam-semantic          │  │
│  │ • Socket: /home/dcruver/emacs-server/server          │  │
│  │ • Notes: /home/dcruver/org-roam/                     │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

**Key Configuration**:
- **Purpose**: API access for n8n workflows only (no agent)
- **User**: Runs as root but connects to dcruver's Emacs server
- **Environment**: `EMACS_SERVER_FILE=/home/dcruver/emacs-server/server`

### Deployment Decisions

**Why Dedicated Container for Agent?**
- **Isolation**: Agent, MCP, and Emacs run independently from n8n
- **Performance**: No resource contention with n8n workloads
- **Independence**: Full stack operates without network dependencies
- **Maintenance**: Separate update cycles for different services

**Why Local MCP on Agent Server?**
- **Speed**: Direct emacsclient calls, no network latency
- **Reliability**: No network dependency for core operations
- **Simplicity**: All components on same machine

**Why Remote Ollama (feynman)?**
- **Centralization**: Single Ollama instance for all services
- **Resources**: feynman has better CPU/GPU for LLM inference
- **Flexibility**: Easy to scale or change models centrally
- **Consistency**: All deployments use same models

**Why Shared Volume for Notes?**
- **Consistency**: Same notes accessible from multiple Emacs instances
- **Backup**: Centralized backup strategy for notes
- **Flexibility**: Can add more containers accessing same notes

### Installation Methods

**Package-Based (Recommended)**:
- MCP: Python wheel package installed in dedicated venv
- Agent: JAR file copied directly to server
- No git repositories on production servers
- Clean, minimal installation footprint

**Update Procedure**:
```bash
# MCP Server Update
# Build wheel locally
cd /home/dcruver/Projects/org-roam-ai/mcp
python -m build
scp dist/org_roam_mcp-0.1.0-py3-none-any.whl root@192.168.20.136:/tmp/
ssh root@192.168.20.136 "
  /opt/org-roam-mcp-venv/bin/pip install --force-reinstall /tmp/org_roam_mcp-0.1.0-py3-none-any.whl
  systemctl restart org-roam-mcp
"

# Agent Update
cd /home/dcruver/Projects/org-roam-ai/agent
./mvnw clean package -DskipTests
scp target/embabel-note-gardener-0.1.0-SNAPSHOT.jar root@192.168.20.136:/opt/org-roam-agent/
```

### Monitoring and Maintenance

**Health Checks**:
```bash
# Verify MCP Server
curl http://192.168.20.136:8000  # Should return: OK

# Verify Ollama Connection
curl -s http://feynman.cruver.network:11434/api/tags | jq '.models[].name'

# Verify Emacs Integration
ssh root@192.168.20.136 "export EMACS_SERVER_FILE=/root/emacs-server/server && emacsclient --eval '(+ 1 1)'"
```

**Scheduled Audits**:
```bash
# Enable nightly audits at 2 AM
systemctl enable org-roam-agent-audit.timer
systemctl start org-roam-agent-audit.timer
systemctl list-timers  # Verify timer is active
```

**Log Monitoring**:
```bash
# MCP Server
ssh root@192.168.20.136 "journalctl -u org-roam-mcp -f"

# Agent Audits
ssh root@192.168.20.136 "journalctl -u org-roam-agent-audit -f"
```

---

## Design Rationale

### Why Three Separate Components?

**Modularity**: Each serves different users
- Emacs users may not need MCP or Agent
- Automation users may not use Emacs directly
- Agent users need both Emacs and MCP

**Technology Fit**
- Elisp: Best for Emacs integration
- Python: Excellent for API servers and scripting
- Java: Strong for complex planning and enterprise deployment

**Independent Evolution**: Each component can be updated without breaking others

### Why Not a Monolithic Application?

**Different Deployment Models**
- Emacs: Runs in user's editor
- MCP: Long-running server process
- Agent: Scheduled jobs or interactive shell

**Different Users**
- Emacs: Knowledge workers
- MCP: System integrators
- Agent: DevOps/automation engineers

### Why Ollama Instead of Cloud APIs?

- **Privacy**: Knowledge base never leaves user's machine
- **Cost**: No per-token API fees
- **Consistency**: Same models for all users
- **Offline**: Works without internet
- **Control**: User chooses models and versions

---

## Future Architecture Considerations

### Potential Enhancements

1. **Agent ↔ Emacs Direct Integration**
   - Pro: Faster than HTTP calls via MCP
   - Con: Tighter coupling, Java ↔ Emacs communication complexity
   - Decision: Use MCP for now (proven integration)

2. **Distributed Embeddings**
   - Pro: Separate embedding storage from org files
   - Con: Breaks portability, requires sync mechanism
   - Decision: Keep in org files (simpler, more portable)

3. **Web UI for Agent**
   - Pro: Better UX than Spring Shell
   - Con: Additional complexity, security concerns
   - Decision: Shell-first, web optional in future

4. **Real-time File Watching**
   - Pro: Immediate response to note changes
   - Con: Resource intensive, complex change detection
   - Decision: Scheduled audits + manual triggers for now

---

**Next Steps**: See [DEVELOPMENT.md](DEVELOPMENT.md) for development workflows and [README.md](README.md) for quick start guides.
