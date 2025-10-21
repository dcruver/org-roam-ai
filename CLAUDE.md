# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in the org-roam-ai repository.

## Project Overview

org-roam-ai is a monorepo containing three integrated components for AI-powered org-roam knowledge base management:

1. **emacs/** - Elisp package for semantic search and AI assistance within Emacs
2. **mcp/** - Python MCP server providing API access to org-roam via HTTP/stdio
3. **agent/** - Java GOAP agent for autonomous knowledge base maintenance

**Key Documents**:
- [README.md](README.md) - User-facing overview and quick start
- [ARCHITECTURE.md](ARCHITECTURE.md) - Technical architecture and integration patterns
- [DEVELOPMENT.md](DEVELOPMENT.md) - Development workflows and conventions

## Quick Reference

**Most Common Development Commands**:

```bash
# Emacs Package (no build needed)
# In Emacs: (add-to-list 'load-path "/path/to/org-roam-ai/emacs")
# Then: (require 'org-roam-vector-search) and (require 'org-roam-ai-assistant)
# Test: M-x org-roam-semantic-status

# MCP Server
cd mcp && source .venv/bin/activate
pip install -e ".[dev]"      # Initial setup
python -m org_roam_mcp.server # Run server
pytest                        # Run tests
black src/ tests/ && ruff check --fix src/ tests/  # Format & lint

# Agent
cd agent
./mvnw clean package          # Build
./mvnw spring-boot:run        # Run
./mvnw test                   # Run tests
./test-audit.sh               # Quick non-interactive test

# Prerequisites (all components)
ollama serve                  # Start Ollama
# In Emacs: (server-start)    # Start Emacs server
curl http://localhost:11434/api/tags  # Verify Ollama
emacsclient --eval '(+ 1 1)'  # Verify Emacs server
```

## Repository Structure

```
org-roam-ai/
├── CLAUDE.md           This file (monorepo overview)
├── emacs/              Elisp package (org-roam-semantic)
│   ├── *.el           Core modules
│   ├── docs/          Feature documentation
│   └── CLAUDE.md      Emacs component guide (if exists)
├── mcp/                Python MCP server (org-roam-mcp)
│   ├── src/           Source code
│   ├── tests/         Test suite
│   ├── pyproject.toml Package configuration
│   └── CLAUDE.md      MCP component guide (if exists)
└── agent/              Java GOAP agent (org-roam-agent)
    ├── src/           Source code
    ├── samples/notes/ Sample notes for testing
    ├── pom.xml        Maven config
    ├── test-audit.sh  Non-interactive test script
    ├── test-llm-integration.sh  LLM integration test
    └── CLAUDE.md      Agent component guide (detailed implementation)
```

**Note**: Each component has its own CLAUDE.md with detailed implementation notes. This root-level file provides the big picture and cross-component integration patterns.

---

# Component: Emacs Package (emacs/)

## Overview

Pure Emacs Lisp package providing semantic search and AI assistance. No build system required.

## Core Files

- **org-roam-vector-search.el** - Vector embedding generation, storage, similarity search
- **org-roam-ai-assistant.el** - Context-aware AI enhancement using related notes

## Development Commands

```elisp
;; Load for development
(add-to-list 'load-path "/path/to/org-roam-ai/emacs")
(require 'org-roam-vector-search)
(require 'org-roam-ai-assistant)

;; Test
M-x org-roam-semantic-status        ; Check embedding coverage
M-x org-roam-semantic-version       ; Version info
M-x org-roam-ai-setup-check         ; Comprehensive check

;; Generate embeddings
M-x org-roam-semantic-generate-all-embeddings

;; Interactive search
C-c v s  ; Search by concept
C-c v i  ; Insert similar notes
```

## Key Configuration Variables

```elisp
org-roam-semantic-ollama-url           ; Default: http://localhost:11434
org-roam-semantic-embedding-model      ; Default: nomic-embed-text
org-roam-semantic-generation-model     ; Default: llama3.1:8b
org-roam-semantic-embedding-dimensions ; Default: 768
org-roam-semantic-similarity-cutoff    ; Default: 0.55
org-roam-semantic-enable-chunking      ; Default: nil
org-roam-semantic-min-chunk-size       ; Default: 100 words
org-roam-semantic-max-chunk-size       ; Default: 1000 words
org-roam-ai-default-model              ; Default: llama3.1:8b
org-roam-ai-context-limit              ; Default: 3 similar notes
```

## Data Storage Architecture

**Embedding Storage**: `:EMBEDDING:` property in org files
```org
:PROPERTIES:
:ID: abc123
:EMBEDDING: [0.123, -0.456, 0.789, ...]
:EMBEDDING_MODEL: nomic-embed-text
:EMBEDDING_TIMESTAMP: [2025-10-20 Mon 09:15]
:END:
```

**Section-level embeddings** (when chunking enabled):
```org
** Section Heading
:PROPERTIES:
:CHUNK_ID: chunk-abc123-1
:EMBEDDING: [...]
:END:
```

## API Integration

- **HTTP to Ollama**: Built-in `url-retrieve-synchronously`
- **JSON**: Native `json-read` and `json-encode`
- **Org-to-markdown**: `ox-md` for content preprocessing
- **Timeout**: 30 seconds for API calls

## Debugging

```elisp
;; Enable debug mode
(setq debug-on-error t)

;; Test embedding generation
(org-roam-semantic-generate-embedding)

;; Test similarity calculation
(org-roam-semantic-get-similar-data "query" 5)
```

## Common Issues

**"Symbols function definition is void: fourth"**
- Missing `cl-lib` dependency
- Fix: Add `(require 'cl-lib)` before loading package

**"Error calling Ollama"**
- Ollama not running or wrong URL
- Fix: Verify with `curl http://localhost:11434/api/tags`

**Multi-heading processing issues**
- Headings below word count threshold not processed
- Check `org-roam-semantic-min-chunk-size` setting

---

# Component: MCP Server (mcp/)

## Overview

Python-based Model Context Protocol server providing HTTP and stdio access to org-roam functionality.

## Development Environment

```bash
cd mcp
python -m venv .venv
source .venv/bin/activate
pip install -e ".[dev]"
```

## Running

```bash
# HTTP mode (default, port 8000)
python -m org_roam_mcp.server
# or
org-roam-mcp

# Test
curl http://localhost:8000  # Health check
```

## Architecture

**Dual-Mode Transport**:
- **HTTP**: JSON-RPC 2.0 on port 8000 (for n8n, web apps)
- **stdio**: MCP protocol via stdin/stdout (for MCP clients)

**Core Components**:
- `server.py` - Main server, tool registration, both transports
- `emacs_client.py` - Wraps emacsclient calls, parameter escaping, JSON parsing
- `tools/` - Individual tool implementations

## Available MCP Tools

| Tool | Elisp Function | Purpose |
|------|----------------|---------|
| `semantic_search` | `my/api-semantic-search` | Vector similarity via org-roam-semantic |
| `contextual_search` | `my/api-contextual-search` | Keyword search with full context |
| `search_notes` | `my/api-search-notes` | Basic title/content search |
| `create_note` | `my/api-create-note` | Create note with auto-embedding |
| `add_daily_entry` | `my/add-daily-entry-structured` | Structured daily journal entry |
| `get_daily_content` | `my/get-daily-note-content` | Retrieve daily note |
| `sync_database` | `org-roam-db-sync` | Force database sync |

## Data Flow

```
HTTP POST → Starlette → Server → EmacsClient
  → emacsclient --eval '(elisp-function args)'
  → Emacs executes → Returns JSON string
  → EmacsClient parses → Server formats → HTTP response
```

## Testing

```bash
# Run tests
pytest

# Specific test
pytest tests/test_emacs_client.py

# With coverage
pytest --cov=src/

# Verbose
pytest -v
```

## Code Quality

```bash
# Format
black src/ tests/

# Lint
ruff check src/ tests/

# Fix auto-fixable
ruff check --fix src/ tests/

# Type check
mypy src/
```

## Critical Implementation Notes

**Parameter Escaping**:
- All strings escaped via `EmacsClient._escape_for_elisp()`
- Prevents shell injection

**JSON Response Handling**:
- Character arrays (numeric keys) auto-reconstructed to strings
- Handled by `_parse_json_response()`

**Elisp Function Signatures** (important!):
- `my/add-daily-entry-structured`: 5 params (timestamp, title, points_list, steps_list, tags_list)
- `my/api-create-note`: 4 params (title, content, type, confidence)
- `my/get-daily-note-content`: Optional date param (defaults to today)

**Daily Entry Formatting**:
- TODO entries get "TODO " prefix added in Python before calling elisp
- `entry_type` parameter is client-side only (not passed to elisp)

**Embedding Auto-Generation**:
- `my/api-create-note` auto-generates embeddings if org-roam-semantic available
- No separate step needed for new notes

## Environment Variables

```bash
EMACS_SERVER_FILE  # Path to Emacs server socket (default: ~/emacs-server/server)
```

## Common Issues

**"coroutine 'main' was never awaited"**
- Fixed in current version
- Reinstall if encountered: `pip install -e .`

**"ModuleNotFoundError"**
- Wrong virtual environment or package not installed
- Fix: `source .venv/bin/activate && pip install -e .`

**Connection to Emacs fails**
- Check Emacs server running: `emacsclient --eval '(+ 1 1)'`
- Verify `EMACS_SERVER_FILE` points to correct socket

**JSON parsing errors**
- Usually character arrays from Emacs (automatically handled)
- Check for unexpected elisp return format

## Distribution

```bash
# Build
python -m build

# Publish to Gitea
python -m twine upload --repository gitea dist/*

# See DISTRIBUTION.md for full guide
```

---

# Component: Agent (agent/)

## Overview

Java 21 Spring Boot application using Embabel GOAP framework for autonomous knowledge base maintenance.

## Build & Run

```bash
cd agent

# Build
./mvnw clean package

# Build without tests
./mvnw clean package -DskipTests

# Run
./mvnw spring-boot:run
# or
java -jar target/embabel-note-gardener-*.jar

# With custom notes path
ORG_ROAM_PATH=/path/to/notes java -jar target/*.jar

# With profile
java -jar target/*.jar --spring.profiles.active=auto
```

## Testing

```bash
# Run tests
./mvnw test

# Specific test
./mvnw test -Dtest=CorpusScannerTest

# Automated audit test (non-interactive shell mode)
./test-audit.sh
# Uses: ORG_ROAM_PATH=./samples/notes and pipes commands via stdin
# Tests: status, audit commands

# LLM integration test (quick verification)
./test-llm-integration.sh
# Times out after 30s, checks for "Audit completed" in output
```

**Non-Interactive Testing**:
```bash
# Run shell commands via stdin (for CI/testing)
java -Dspring.shell.interactive.enabled=false \
     -Dspring.shell.command.script.enabled=true \
     -jar target/embabel-note-gardener-*.jar <<EOF
status
audit
apply safe
exit
EOF
```

## Architecture

**GOAP Planning** (Goal-Oriented Action Planning):
1. `CorpusScanner` scans org files, builds `CorpusState`
2. `GOAPPlanner` evaluates actions based on preconditions, costs
3. Generates `ActionPlan` with prioritized actions
4. Executes actions, reassesses state after each

**World State**: `CorpusState`
- Per-note: embeddings, format status, link count, health score
- Corpus-wide: total notes, orphans, health distribution

**Goals**:
- `MaintainHealthyCorpus` - Overall health ≥ target
- `EnsureEmbeddingsFresh` - All notes have recent embeddings
- `EnforceFormattingPolicy` - Proper org structure
- `ReduceOrphans` - Minimize isolated notes

**Actions** (implemented):
- `ComputeEmbeddings` - Generate missing/stale embeddings (via MCP)
- `NormalizeFormatting` - LLM-based format fixing
- `SuggestLinks` - Semantic search for link proposals (via MCP)

## Shell Commands

```
status              Show corpus health ✅ Working
audit               Scan with LLM format checking, generate plan ✅ Working
execute             Execute current plan ✅ Working
apply safe          Apply safe actions only ✅ Working
proposals list      List pending proposals ✅ Working
proposals show <id> Show proposal details ✅ Working
proposals apply <id> Apply proposal (TODO)
report              Generate daily report (TODO)
```

**Action Execution**:
- `execute` - Runs all actions in the plan (safe + proposal actions)
- `apply safe` - Runs only safe actions, skips proposals
- Both commands show detailed execution results with status indicators

## Dependencies

**Key Maven Dependencies**:
- `spring-boot-starter-parent`: 3.5.5
- `embabel-agent-starter-shell`: 0.1.2 (includes Spring Shell, Spring AI)
- **IMPORTANT**: Do NOT add `spring-ai-ollama-spring-boot-starter` separately

**Embabel Repository**:
```xml
<repository>
    <id>embabel-releases</id>
    <url>https://repo.embabel.com/artifactory/libs-release</url>
</repository>
```

## LLM Integration

**OllamaChatService**:
- Format analysis during audit: `analyzeOrgFormatting(content)`
- Format fixing: `normalizeOrgFormatting(content, noteId)`
- Link rationale: LLM generates explanation for proposals

**Configuration** (`application.yml`):
```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: gpt-oss:20b
      embedding:
        options:
          model: nomic-embed-text:latest

embabel:
  models:
    default-llm: gpt-oss:20b

gardener:
  embedding-model: nomic-embed-text:latest
  mcp:
    enabled: true
    base-url: http://localhost:8000
    timeout: 30000
```

## MCP Integration

**Why Agent Calls MCP**:
1. Reuse org-roam-mcp's semantic search implementation
2. Separation of concerns (agent = planning, MCP = org-roam ops)
3. No duplication of embedding logic

**Implementation** (`OrgRoamMcpClient.java`):
- ✅ HTTP JSON-RPC client for MCP server communication
- ✅ Semantic search using vector embeddings
- ✅ Contextual search with full note content
- ✅ Server health checking and availability detection
- ✅ Configurable timeout and base URL
- ✅ Used by SuggestLinks action for link proposals

## LLM-Based Formatting

**During Audit** (`CorpusScanner.checkFormatWithLLM()`):
- Called for notes failing simple format check
- LLM identifies specific issues (missing properties, malformed headings)
- Results cached in `NoteMetadata`
- Why audit takes 15-30 seconds (LLM call per problematic note)

**During Execution** (`NormalizeFormattingAction.execute()`):
- `OllamaChatService.normalizeOrgFormatting(content, noteId)`
- LLM returns corrected content
- Preserves all information, fixes structure
- Cost: 3.0 per note (2 LLM calls: analyze + fix)

## Action Safety Classification

**Safe Actions** (auto-apply in auto mode):
- `NormalizeFormatting` - Structure fixes only
- `ComputeEmbeddings` - Additive, no content change

**Proposal Actions** (require human approval):
- `SuggestLinks` - Changes note semantics
- `ProposeSplitOrMerge` - Content modification

## Health Score

**Components** (total: 100 points):
- Formatting: 10
- Provenance: 25
- Embeddings freshness: 15
- Links: 20
- Taxonomy: 10
- Contradictions (inverse): 10
- Staleness (inverse): 10

**Calculated by**: `HealthScoreCalculator.calculateNoteHealth(NoteMetadata)`

## Package Structure

```
com.dcruver.orgroam/
├── app/          Spring Boot + Shell
├── domain/       State, goals, actions, planning
│   ├── actions/  Action implementations
│   └── planning/ GOAPPlanner, ActionPlan
├── io/           File I/O, patches, backups
├── nlp/          Ollama integration
└── reporting/    Health reports

com.embabel.agent.core/  Embabel stubs
├── action/       Action base class
└── goal/         Goal base class
```

## Common Issues

**Ollama connection failed**:
- Check: `curl http://localhost:11434/api/tags`
- Verify models pulled: `ollama list`

**Model not found**:
- Pull models: `ollama pull gpt-oss:20b && ollama pull nomic-embed-text:latest`

**Spring AI version conflicts**:
- DO NOT add spring-ai-ollama separately
- Embabel starter includes correct version

**MCP connection issues**:
- Verify MCP running: `curl http://localhost:8000`
- Check `gardener.mcp.base-url` in application.yml

## Implementation Status

**✅ Completed & Working**:
- Corpus scanning with LLM format checking
- GOAP planning with cost estimates
- Health score calculation
- Action execution engine (ActionExecutor)
- Shell commands: status, audit, execute, apply safe, proposals list/show
- NormalizeFormatting action (LLM-based, fully working)
- SuggestLinks action (MCP-based semantic search, fully working)
- MCP client integration (OrgRoamMcpClient, fully working)
- ComputeEmbeddings action (delegates to MCP)

**📋 Next Priority**:
- Proposal application workflow (proposals apply command)
- File watching for background daemon mode
- Daily report generation

---

# Cross-Component Workflows

## Workflow 1: Adding New MCP Tool

**When**: Need new Emacs functionality accessible via API

**Steps**:
1. Add elisp function in `emacs/` (or reference existing in org-roam-api.el)
2. Add tool definition in `mcp/src/org_roam_mcp/server.py`
3. Test via HTTP: `curl -X POST http://localhost:8000 ...`
4. Agent can now call via MCP client (when implemented)
5. Update `mcp/README.md` with tool documentation

## Workflow 2: Adding New Agent Action

**When**: Need new automated maintenance capability

**Steps**:
1. Create action class in `agent/src/.../domain/actions/`
2. Implement: preconditions, cost, execute logic
3. May call MCP for semantic ops (via HTTP client)
4. May call LLM for analysis/rationale (via OllamaChatService)
5. Annotate with `@Component` (auto-registered)
6. Test with sample notes: `ORG_ROAM_PATH=./samples/notes`

## Workflow 3: Updating Embedding Model

**When**: Want to use different Ollama model

**Steps**:
1. Pull model: `ollama pull new-model`
2. Update Emacs config:
   ```elisp
   (setq org-roam-semantic-embedding-model "new-model")
   (setq org-roam-semantic-embedding-dimensions <check-model-docs>)
   ```
3. Update agent config (`application.yml`):
   ```yaml
   spring.ai.ollama.embedding.options.model: new-model
   gardener.embedding-model: new-model
   ```
4. Regenerate embeddings: `M-x org-roam-semantic-generate-all-embeddings`

## Workflow 4: Debugging Integration Issues

**When**: Agent can't connect to MCP, or MCP can't reach Emacs

**Checklist**:
1. Verify Ollama: `curl http://localhost:11434/api/tags`
2. Verify Emacs server: `emacsclient --eval '(+ 1 1)'`
3. Verify MCP server: `curl http://localhost:8000`
4. Test MCP tool manually:
   ```bash
   curl -X POST http://localhost:8000 -d '{
     "method": "tools/call",
     "params": {"name": "search_notes", "arguments": {"query": "test"}}
   }'
   ```
5. Check agent config: `gardener.mcp.base-url` in application.yml
6. Review logs: `tail -f agent/embabel-note-gardener.log`

---

# Development Best Practices

## Code Style

**Emacs**:
- Dash-separated names, prefix `org-roam-semantic-`
- Docstrings for all public functions
- Line limit: 80 chars where practical

**Python**:
- Black format (line length 88)
- Ruff linting
- Type hints required
- Google-style docstrings

**Java**:
- Spring conventions (4-space indent)
- Javadoc for all public classes/methods
- Descriptive test names

## Testing

**Emacs**: Manual testing in Emacs
**MCP**: `pytest` with mocks
**Agent**: JUnit with Spring Boot Test

## Git Commits

Follow Conventional Commits:
```
<type>(<scope>): <description>

feat(emacs): add section-level chunking
fix(mcp): handle character arrays in JSON
docs(architecture): add integration diagrams
```

**Scopes**: `emacs`, `mcp`, `agent`, `docs`, `ci`

## Performance Optimization

**Emacs**:
- Only regenerate embeddings when content changes
- Lazy load embeddings for search

**MCP**:
- Keep Emacs server running (don't restart frequently)
- Cache frequently accessed notes (future)

**Agent**:
- Cache format check results
- Incremental audits for changed notes (future)
- Parallel LLM calls where independent (future)

---

# Key Technical Decisions

## Why Store Embeddings in Org Files?

- No external database required
- Portable with notes
- Git-trackable
- Directly accessible to any tool

## Why MCP Server Between Agent and Emacs?

- Reuse proven org-roam-semantic integration
- Separation of concerns (agent = planning, MCP = org-roam ops)
- Language strengths (Java for GOAP, Python for Emacs integration)
- No duplication of embedding logic

## Why Ollama Instead of Cloud APIs?

- Privacy (knowledge never leaves machine)
- No API costs
- Consistent models for all users
- Offline capability
- User control over models/versions

## Why GOAP for Agent?

- Dynamic planning based on current state
- Cost-based action selection
- Reassessment after each action (adaptive)
- Explainable decisions (preconditions + effects)

---

# Important File Paths

**Emacs Package**:
- Code: `emacs/*.el`
- Docs: `emacs/docs/*.md`

**MCP Server**:
- Code: `mcp/src/org_roam_mcp/`
- Tests: `mcp/tests/`
- Config: `mcp/pyproject.toml`

**Agent**:
- Code: `agent/src/main/java/com/dcruver/orgroam/`
- Tests: `agent/src/test/java/`
- Config: `agent/src/main/resources/application.yml`
- Sample notes: `agent/samples/notes/`

**Top-level Docs**:
- [README.md](README.md) - User overview
- [ARCHITECTURE.md](ARCHITECTURE.md) - Technical deep dive
- [DEVELOPMENT.md](DEVELOPMENT.md) - Dev workflows

---

**When working on this codebase**:
1. Identify which component(s) are affected
2. Review component-specific section above
3. Check ARCHITECTURE.md for integration patterns
4. Follow code style conventions for the language
5. Run tests for affected components
6. Update relevant documentation

**For cross-component changes**:
1. Consider impact on all three layers
2. Update integration docs in ARCHITECTURE.md
3. Test full stack integration if possible
4. Document new integration patterns
