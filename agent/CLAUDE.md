# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 🚀 CURRENT STATUS (2025-10-23)

**COMPLETED**: MCP-based embedding generation integration implemented!

**What was done**:
1. ✅ Added `generateEmbeddings()` method to `OrgRoamMcpClient.java` (lines 200-261)
2. ✅ Added `GenerateEmbeddingsResult` DTO class (lines 366-388)
3. ✅ Updated `ComputeEmbeddingsAction.java` to call MCP instead of local SQLite (lines 64-118)
4. ✅ Removed local embedding generation using `OllamaEmbeddingService` and `EmbeddingStore`
5. ✅ Built JAR: `target/embabel-note-gardener-0.1.0-SNAPSHOT.jar` (87MB)

**PENDING DEPLOYMENT**:
- ❌ Agent JAR NOT YET deployed to production server (192.168.20.136)
- The old JAR on the server still uses local SQLite for embeddings
- MCP server IS deployed and working (verified with direct HTTP test)

**Next steps to complete**:
```bash
# 1. Deploy agent JAR to server
cd /home/dcruver/Projects/org-roam-ai/agent
./mvnw clean package -DskipTests
scp target/embabel-note-gardener-0.1.0-SNAPSHOT.jar root@192.168.20.136:/opt/org-roam-agent/

# 2. Test agent with MCP integration
ssh root@192.168.20.136 "cd /opt/org-roam-agent && java -Dspring.shell.interactive.enabled=false -Dspring.shell.command.script.enabled=true -jar embabel-note-gardener-0.1.0-SNAPSHOT.jar status"

# 3. Verify embeddings delegated to MCP (check logs for MCP calls, not local generation)
ssh root@192.168.20.136 "journalctl -u org-roam-agent-audit -n 100 | grep -i 'embedding\|mcp'"

# 4. Confirm nightly job works
ssh root@192.168.20.136 "systemctl status org-roam-agent-audit.timer"
```

**Key architectural change**:
- **BEFORE**: Agent generated embeddings locally using `OllamaEmbeddingService` and stored in `~/.gardener/embeddings.db`
- **AFTER**: Agent calls `OrgRoamMcpClient.generateEmbeddings()` → MCP server → Emacs `org-roam-semantic-generate-all-embeddings`
- Embeddings now stored in org files as `:PROPERTIES:`, managed by org-roam-semantic

**Files modified**:
- `src/main/java/com/dcruver/orgroam/nlp/OrgRoamMcpClient.java` - Added generateEmbeddings method
- `src/main/java/com/dcruver/orgroam/domain/actions/ComputeEmbeddingsAction.java` - Updated to use MCP

---

## Project Overview

This is a production-ready Embabel (GOAP - Goal-Oriented Action Planning) agent service built with Spring Boot, Spring Shell, and Java 21+. The service maintains a local Org/Org-roam knowledge base by auditing and fixing issues related to formatting, links, taxonomy, and staleness. All edits are explainable, reversible, and safe by default.

This is modeled after the Embabel Shell demo pattern and runs as an executable JAR on a Proxmox server.

**Note**: This service integrates with the separate `org-roam-mcp` Python server (see Integration Architecture section) for semantic search and embedding operations, leveraging its existing org-roam-semantic integration.

## Build & Run Commands

**Note**: This project uses Maven Wrapper (`./mvnw`), so you don't need Maven installed locally.

```bash
# Build executable JAR
./mvnw clean package

# Build without tests (faster)
./mvnw clean package -DskipTests

# Run the application (interactive shell)
./mvnw spring-boot:run

# Run as executable JAR
java -jar target/embabel-note-gardener-*.jar

# Run with custom notes directory
ORG_ROAM_PATH=/path/to/your/org-roam java -jar target/embabel-note-gardener-*.jar

# Run with specific profile (dry-run is default)
java -jar target/embabel-note-gardener-*.jar --spring.profiles.active=dry-run

# Run in auto mode (applies safe actions automatically)
java -jar target/embabel-note-gardener-*.jar --spring.profiles.active=auto

# Run tests
./mvnw test

# Test with sample notes
ORG_ROAM_PATH=./samples/notes java -jar target/embabel-note-gardener-*.jar
```

## Testing

The project includes test scripts for automated testing:

```bash
# Run automated audit test (non-interactive)
./test-audit.sh

# Test LLM integration (requires Ollama running)
./test-llm-integration.sh
```

**Non-Interactive Shell Mode** (for CI/testing):
```bash
# Run shell commands via stdin
java -jar target/embabel-note-gardener-*.jar \
  --spring.shell.interactive.enabled=false \
  --spring.shell.command.script.enabled=true <<EOF
status
audit
exit
EOF
```

## Quick Start Example

```bash
# 1. Build the application
./mvnw clean package

# 2. Run with sample notes
ORG_ROAM_PATH=./samples/notes java -jar target/embabel-note-gardener-*.jar

# 3. In the shell, check corpus health
starwars> status

# 4. Generate an action plan
starwars> audit

# 5. View any existing proposals
starwars> proposals list

# 6. Exit
starwars> exit
```

**Expected Output:**
- `status` shows corpus health (51.3/90 for sample notes), statistics on embeddings, formatting, orphans, etc.
- `audit` generates a GOAP plan with 3 actions (2 safe, 1 proposal) prioritized by cost
- Plan shows exactly what the system would do to improve corpus health

## Shell Commands

The application uses Spring Shell with Embabel's `@EnableAgents` annotation. Shell commands are defined in `GardenerShellCommands.java`:

- `audit` or `audit now` - Trigger immediate full audit and produce plan ✅ Working
- `status` - Show current corpus health status ✅ Working
- `execute` or `x` - Execute the current plan ✅ Working
- `apply safe` - Apply all safe actions from current plan ✅ Working
- `proposals list` - List all pending proposals ✅ Working
- `proposals show <id>` - Show details of a specific proposal ✅ Working
- `proposals apply <id>` - Apply a specific proposal (TODO: implementation)
- `report` or `report daily` - Generate daily Org report note (TODO: implementation)
- `help` - Show available commands (Spring Shell built-in)

**Action Execution Details**:
- `execute` runs all actions in the plan (safe + proposals)
- `apply safe` runs only safe actions, skips proposals requiring human approval
- Both commands display detailed execution results with status indicators (✓ ✗ ⊘)
- Actions are executed sequentially with state updates between each
- Failed actions are logged but don't stop the entire plan execution

**Interactive vs Non-Interactive Modes**:
- By default, the application runs in interactive shell mode
- For testing/CI, use non-interactive mode with `--spring.shell.interactive.enabled=false`
- Commands can be piped via stdin or provided via script file
- See "Testing" section above for examples

## Integration Architecture

### Overview

The org-roam-agent works alongside the **org-roam-mcp** server to provide a complete knowledge base maintenance solution:

```
┌─────────────────────────────────────────────────────────────────┐
│                    org-roam-agent (Java)                        │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ GOAP Planning & Execution                                 │  │
│  │ - Audit corpus health                                     │  │
│  │ - Generate action plans                                   │  │
│  │ - LLM-based format analysis & fixing                      │  │
│  │ - Health score calculation                                │  │
│  └──────────────────────────────────────────────────────────┘  │
│                           │                                      │
│                           │ HTTP calls                           │
│                           ▼                                      │
└───────────────────────────┼──────────────────────────────────────┘
                            │
                            │
┌───────────────────────────▼──────────────────────────────────────┐
│                 org-roam-mcp (Python HTTP Server)                │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ Semantic Operations                                       │  │
│  │ - Semantic search (vector embeddings via org-roam-semantic)│
│  │ - Note creation with auto-embedding generation           │  │
│  │ - Database sync                                           │  │
│  │ - Contextual search for link suggestions                 │  │
│  └──────────────────────────────────────────────────────────┘  │
│                           │                                      │
│                           │ emacsclient                          │
│                           ▼                                      │
└───────────────────────────┼──────────────────────────────────────┘
                            │
                            │
┌───────────────────────────▼──────────────────────────────────────┐
│                      Emacs + org-roam                            │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ - org-roam database                                       │  │
│  │ - org-roam-semantic (Ollama integration)                  │  │
│  │ - Embedding storage                                       │  │
│  │ - Note files (~/org-roam/*.org)                          │  │
│  └──────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

### Division of Responsibilities

**org-roam-agent (this project)**:
- ✅ LLM-based format analysis during audit (calls LLM to verify Org-mode structure)
- ✅ Health score calculation
- ✅ GOAP action planning
- ✅ Format normalization (LLM-based fixing of :PROPERTIES:, titles, etc.)
- 🔄 Action execution engine (in progress)
- 📋 Link suggestions via semantic search (delegates to org-roam-mcp)
- 📋 Embedding verification (queries org-roam-mcp)

**org-roam-mcp** (sibling project at `../org-roam-mcp`):
- ✅ Semantic search using vector embeddings (org-roam-semantic + Ollama)
- ✅ Note creation with automatic embedding generation
- ✅ Contextual search with full content and backlinks
- ✅ org-roam database synchronization
- ✅ HTTP JSON-RPC server (port 8000)

**Why this architecture?**
1. **Reuse existing work**: org-roam-mcp already has working Ollama/org-roam-semantic integration
2. **Separation of concerns**: Agent focuses on maintenance, MCP focuses on semantic ops
3. **Language strengths**: Java for GOAP/planning, Python for Emacs integration
4. **No duplication**: Avoid reimplementing embedding storage and org-roam queries

### MCP Integration

The agent will call org-roam-mcp's HTTP API for:

1. **Semantic Link Suggestions**:
   ```java
   POST http://localhost:8000
   {
     "jsonrpc": "2.0",
     "method": "tools/call",
     "params": {
       "name": "semantic_search",
       "arguments": {
         "query": "note content...",
         "limit": 5
       }
     }
   }
   ```

2. **Embedding Status Check**:
   - Query MCP to check if embeddings exist for notes
   - Used during audit to determine embedding freshness

3. **Note Creation** (future):
   - When creating new notes, optionally delegate to MCP for auto-embedding

### Configuration

**Prerequisites**:
1. org-roam-mcp server must be running on `http://localhost:8000`
2. Emacs with org-roam and org-roam-semantic must be configured
3. Ollama must be running with `nomic-embed-text` model

**Agent Configuration** (application.yml):
```yaml
gardener:
  mcp:
    enabled: true  # Enable MCP integration
    base-url: http://localhost:8000
    timeout: 30000  # 30 second timeout
```

## Architecture

### Dependencies

**Key Maven Dependencies**:
- `spring-boot-starter-parent`: 3.5.5
- `embabel-agent-starter-shell`: 0.1.2 (from Embabel repository - includes Spring Shell and Spring AI with compatible versions)
- Java 21+ required

**IMPORTANT**: Do NOT add `spring-ai-ollama-spring-boot-starter` separately - it's included transitively via `embabel-agent-starter-shell` with the correct version. Adding it separately causes version conflicts.

**Embabel Framework Integration**:
- The project includes stub implementations of Embabel core classes in `com.embabel.agent.core` package
- These stubs (`Action`, `ActionResult`, `Goal`, `GoalStatus`) provide the base interfaces for GOAP actions and goals
- The actual Embabel agent framework is provided by the `embabel-agent-starter-shell` dependency
- Actions extend `com.embabel.agent.core.action.Action` and goals extend `com.embabel.agent.core.goal.Goal`

**Maven pom.xml Configuration**:

```xml
<properties>
    <java.version>21</java.version>
    <embabel-agent.version>0.1.2</embabel-agent.version>
</properties>

<dependencies>
    <!-- Embabel Agent Framework - includes Spring Boot, Spring Shell, and Spring AI -->
    <dependency>
        <groupId>com.embabel.agent</groupId>
        <artifactId>embabel-agent-starter-shell</artifactId>
        <version>${embabel-agent.version}</version>
    </dependency>

    <!-- Other dependencies: Lombok, Commons, SQLite, etc. -->
</dependencies>

<repositories>
    <repository>
        <id>embabel-releases</id>
        <url>https://repo.embabel.com/artifactory/libs-release</url>
        <releases><enabled>true</enabled></releases>
        <snapshots><enabled>false</enabled></snapshots>
    </repository>
    <repository>
        <id>spring-milestones</id>
        <url>https://repo.spring.io/milestone</url>
    </repository>
</repositories>
```

### Package Structure

Single Maven module with the following Java packages:

```
com.dcruver.orgroam/
├── app/                    Spring Boot + Spring Shell application, scheduler
│   └── GardenerShellCommands.java  Shell commands (status, audit, execute, apply safe, proposals)
├── domain/                 World state, goals, actions, cost model, planning
│   ├── actions/           Action implementations
│   │   ├── NormalizeFormattingAction.java    ✅ LLM-based format fixing
│   │   ├── SuggestLinksAction.java          ✅ MCP semantic search for links
│   │   └── ComputeEmbeddingsAction.java     ✅ Delegates to MCP
│   ├── goals/             Goal implementations
│   ├── planning/          GOAP planner and plan representations
│   │   ├── GOAPPlanner.java                 ✅ Generates action plans
│   │   ├── ActionExecutor.java              ✅ NEW - Executes plans
│   │   ├── ActionPlan.java                  Plan representation
│   │   └── PlannedAction.java               Individual action in plan
│   ├── CorpusScanner.java                   ✅ Scans corpus with LLM format checking
│   └── HealthScoreCalculator.java           ✅ Computes health scores
├── io/                    FS adapters for Org files (read/write, diff/patch, backup)
│   ├── OrgFileReader.java                   ✅ Read org files
│   ├── OrgFileWriter.java                   ✅ Write org files
│   └── PatchWriter.java                     ✅ Create proposals
├── nlp/                   Ollama and MCP integration
│   ├── OllamaChatService.java               ✅ LLM operations
│   ├── OllamaEmbeddingService.java          ✅ Embedding operations
│   ├── OrgRoamMcpClient.java               ✅ NEW - MCP HTTP client
│   └── EmbeddingStore.java                  ✅ Local embedding cache
├── policy/                Formatting rules, tag map, thresholds (YAML)
├── proposals/             Patch & issue storage (JSON + .patch)
├── reporting/             Health dashboard, daily summary note generator
└── config/                Spring configuration classes
    └── SpringAIConfiguration.java           ✅ Ollama ChatModel/EmbeddingModel setup

com.embabel.agent.core/    Embabel GOAP framework stubs
├── action/                Action base class
└── goal/                  Goal base class
```

**Key Components Added Today**:
- `ActionExecutor` - Executes GOAP action plans with state updates
- `OrgRoamMcpClient` - HTTP JSON-RPC client for MCP server
- Enhanced shell commands with execute and apply safe functionality

### Core Concepts

**Note Types** (maintained on filesystem, e.g., ~/org-roam):
1. **Source notes** - Verbatim transcripts/docs; read-only except metadata
2. **Literature notes** - Faithful summaries with citations
3. **Permanent notes** - Atomic, one idea; heavy linking

**GOAP Domain**:
- **World State** (per-note): hasEmbeddings, embedModel, embedAt, formatOK, linkCount, orphan, tagsCanonical, provenanceOK, staleDays, noteType, healthScore
- **Top Goal**: MaintainHealthyCorpus(targetHealth=90)
- **Subgoals**: EnsureEmbeddingsFresh, EnforceFormattingPolicy, ImproveLinkDensity, CanonicalizeTaxonomy, ReduceOrphans, QueueStaleForReview
- **Planning**: GOAP is a path-finding algorithm that reassesses conditions after each action execution; plans are dynamically formulated by the system, not explicitly programmed

**GOAP Planning Algorithm** (Implemented in `GOAPPlanner`):
1. **Scan Corpus**: `CorpusScanner` reads all .org files and builds `CorpusState` with health metrics
2. **Evaluate Actions**: For each registered action, check if `canExecute()` preconditions are met
3. **Calculate Costs**: Each executable action returns a cost estimate
   - `ComputeEmbeddings`: 5.0 per note (LLM embedding generation)
   - `NormalizeFormatting`: 3.0 per note (2 LLM calls: analyze + fix)
   - `SuggestLinks`: 10.0 per note (embedding similarity + LLM rationale)
4. **Prioritize**: Sort actions by safety (safe first) then by cost (lower first)
5. **Generate Plan**: Return `ActionPlan` with ordered list of `PlannedAction` objects
6. **Execute** (TODO): Run actions in order, updating state after each, reassessing preconditions

**Action Classification**:
- **Safe Actions**: Auto-applicable in auto mode (ComputeEmbeddings, NormalizeFormatting)
- **Proposal Actions**: Require human approval (SuggestLinks)
- Each action specifies: name, description, cost, safety, rationale, affected notes count

### Actions Implementation

Each action must be implemented as a concrete class with:
- **Cost estimate** - Used by planner to choose minimal-cost plan
- **Preconditions** - Conditions assessed before execution
- **Effects** - State changes produced by the action
- **Verification** - Post-execution check that effects were achieved

**Safe Actions** (auto-applied in auto mode):
- `NormalizeFormatting` - **LLM-based formatting**: ✅ **Implemented and working**
  - Uses Ollama LLM to analyze Org-mode structure during audit
  - Identifies issues: missing :PROPERTIES:, missing :ID:/:CREATED:, no title, no final newline
  - Fixes formatting while preserving all original content
  - Cost: 3.0 per note (2 LLM calls: analyze + fix)
  - **Note**: Format checking now uses LLM during audit scan (not just rule-based)

- `ComputeEmbeddings` - **Delegates to org-roam-mcp**: 📋 Planned
  - Check embedding status via MCP semantic search API
  - Trigger embedding generation via MCP note creation/update
  - Cost: 5.0 per note
  - **Rationale**: Reuse org-roam-mcp's existing org-roam-semantic integration

- `FixDeadLinks` - Auto-retry HTTP(S) links; create issue records for persistent failures (TODO)
- `QueueStaleReview` - Add review task for untouched notes (>N days or health <τ) (TODO)

**LLM-Based Formatting Details** (`NormalizeFormattingAction`):

The formatting action uses LLM at two stages:

1. **During Audit** (`CorpusScanner.checkFormatWithLLM()`):
   - Called for each note that fails simple format check
   - `OllamaChatService.analyzeOrgFormatting(content)` - LLM identifies specific issues
   - Returns whether formatting is OK (caches in NoteMetadata)
   - This is why audit now takes 15-30 seconds instead of being instant

2. **During Execution** (`NormalizeFormattingAction.execute()`):
   - `OllamaChatService.normalizeOrgFormatting(content, noteId)` - LLM returns corrected content
   - Ensures: :PROPERTIES: drawer, :ID: and :CREATED: properties, level-1 heading, final newline
   - Preserves all original information while fixing structure

**Semi-Auto Actions** (generate proposals):
- `SuggestLinks` - **Uses org-roam-mcp for semantic search**: 📋 Planned
  - Calls MCP semantic_search API to find related notes
  - Gets semantic similarity scores + full context
  - LLM generates rationale for each suggested link
  - Creates proposal with diff showing link additions
  - Cost: 10.0 per note (semantic search + LLM rationale)
  - **Rationale**: Much better than BM25; reuses proven org-roam-semantic integration

- `CanonicalizeTags` - Apply 1→1 mappings automatically; multi-map becomes proposal (TODO)

**Proposal Actions** (require human approval via shell):
- `ProposeSplitOrMerge` - Use signals: Topic Drift, Dependency Tightness, Reuse Potential, Redundancy; generate patch plan with boundaries (TODO)

### Filesystem Interaction

**Read/Write Guarantees**:
- Preserve whitespace & drawers; round-trip tests for idempotence
- All changes go through `PatchWriter` that produces:
  - `note-id.patch` - The actual diff
  - `note-id.change.json` - Rationale, before/after stats
  - Backups in `.gardener/` with timestamped copies

**Safety Mechanisms**:
- Dry-run mode (default): Only emit patches & reports, no .org modification
- Auto mode: Apply safe actions, leave proposals as files for human approval via shell
- `#agents:off` tag: Opt-out mechanism preventing changes (advisory issue only)
- Source notes: Content is immutable (metadata updates only)

### LLM & Integration Configuration

**Agent LLM Backend** (for formatting):
- All LLM operations use **Ollama**
- Default model: **gpt-oss:20b** (configurable via application.yml)
- Spring AI Ollama integration with custom `SpringAIConfiguration`
- Ollama must be running and accessible (default: http://localhost:11434)
- **Used for**: Format analysis, format fixing, link rationale generation

**Embeddings** (delegated to org-roam-mcp):
- **Not implemented locally** - uses org-roam-mcp's semantic search API
- org-roam-mcp uses org-roam-semantic with Ollama/nomic-embed-text
- Agent queries MCP to check embedding status and perform semantic searches
- **Rationale**: Avoid duplicating embedding storage; reuse proven integration

**Semantic Search Strategy**:
- Delegate to org-roam-mcp's `semantic_search` tool
- Get vector similarity scores and full note content
- Use for link suggestions and related note discovery
- Falls back to simple file scanning if MCP unavailable

**Configuration** (application.yml):

```yaml
spring:
  ai:
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      chat:
        enabled: true
        options:
          model: ${OLLAMA_CHAT_MODEL:gpt-oss:20b}
          temperature: 0.7
      embedding:
        enabled: true
        options:
          model: ${OLLAMA_EMBEDDING_MODEL:nomic-embed-text:latest}

embabel:
  models:
    default-llm: ${OLLAMA_CHAT_MODEL:gpt-oss:20b}
    llms:
      better: ${OLLAMA_CHAT_MODEL:gpt-oss:20b}
      cheaper: ${OLLAMA_CHAT_MODEL:gpt-oss:20b}
      faster: ${OLLAMA_CHAT_MODEL:gpt-oss:20b}

gardener:
  # MCP Integration
  mcp:
    enabled: ${MCP_ENABLED:true}
    base-url: ${MCP_BASE_URL:http://localhost:8000}
    timeout: 30000  # 30 seconds

  # Embedding model (for reference - actual embeddings via MCP)
  embedding-model: ${OLLAMA_EMBEDDING_MODEL:nomic-embed-text:latest}
```

**Note**:
- Embabel's `@EnableAgents(localModels = {LocalModels.OLLAMA})` discovers Ollama models but we create ChatModel/EmbeddingModel beans manually via `SpringAIConfiguration`
- `ChatModel` and `EmbeddingModel` beans are created with proper model management options to avoid auto-pulling models

### Health Score Calculation

Weights sum to 100:
- Formatting: 10
- Provenance: 25
- Embeddings freshness: 15
- Links: 20
- Taxonomy: 10
- Contradictions inverse: 10
- Staleness inverse: 10

### Configuration & Thresholds

All policies are configurable via YAML:
- **Linking**: confidence τ_link=0.72; max 7 links/note/run; never auto-link inside Source notes; always add backlinks to MOCs when ≥ τ_link+δ
- **Taxonomy**: External YAML map of canonical tags/aliases
- **Embeddings**: Model pin, max age, chunk size/overlap

### Scheduling & Triggers

1. **Nightly audit** - Full pass with plan/execution (Spring @Scheduled)
2. **On-change** (debounced watch) - Light pass: ComputeEmbeddings, NormalizeFormatting, quick link suggestions
3. **Manual** - Via Spring Shell commands

## Development Practices

### Embabel Integration

**Main Application Configuration**:
```java
@SpringBootApplication
@EnableAgents(
    loggingTheme = LoggingThemes.STAR_WARS,  // or other available themes
    localModels = {LocalModels.OLLAMA}
)
@EnableScheduling
@ConfigurationPropertiesScan
public class OrgRoamGardenerApplication {
    // ...
}
```

**Spring Shell Components**:
- Use `@ShellComponent` for command classes
- Use `@ShellMethod` for individual commands
- Embabel's `@EnableAgents` automatically configures the shell environment
- Spring automatically injects and registers shell commands

### Testing Requirements

All tests must demonstrate:
1. **Formatting idempotence** on .org samples - same input → same output when run twice
2. **No-op behavior** when preconditions aren't met
3. **Safe vs proposal behavior** - safe actions auto-apply, risky ones create proposals
4. **Planner cost optimization** - choosing cheaper plan when many issues exist
5. **Round-trip preservation** - read/write cycle preserves whitespace & drawers
6. **GOAP condition reassessment** - conditions are reassessed after each action execution

### Extension Points

The service is designed for extensibility:
1. **New actions** - Implement action interface with cost, preconditions, effects, verification
2. **New LLM models** - Configure different Ollama models via application.yml
3. **New goals** - Add to GOAP domain with associated actions
4. **New policies** - Add to YAML configuration
5. **New shell commands** - Create @ShellComponent with @ShellMethod

### Logging & Observability

Every action must log:
- Goal that triggered it
- Chosen action and why
- Preconditions met
- Cost estimate
- Effects produced
- Verification result
- Rationale text

Daily Org report should include:
- Embeddings updated: X (missing Y)
- Format fixes: X
- Links added (auto): X / Proposed: Y
- Issues opened/closed by type
- Mean health score change: +Δ

## Troubleshooting

### Ollama Connection Issues
**Problem**: Application fails with "Connection refused" to localhost:11434
**Solution**:
- Ensure Ollama is running: `systemctl status ollama` or `ollama serve`
- Check Ollama is accessible: `curl http://localhost:11434/api/tags`
- Verify models are pulled: `ollama list` should show `gpt-oss:20b` and `nomic-embed-text:latest`
- If running remotely, set `OLLAMA_BASE_URL` environment variable

### Model Not Found
**Problem**: "Model 'gpt-oss:20b' not found" or similar
**Solution**:
```bash
ollama pull gpt-oss:20b
ollama pull nomic-embed-text:latest
```

### Embeddings Database Issues
**Problem**: SQLite errors or corrupted embeddings database
**Solution**:
- Database location: `~/.gardener/embeddings.db` (configurable via `gardener.embeddings-db`)
- To reset: `rm ~/.gardener/embeddings.db` (embeddings will be regenerated on next audit)
- Check disk space and permissions

### Org File Parsing Errors
**Problem**: "Failed to parse note" or formatting errors
**Solution**:
- Ensure .org files have proper encoding (UTF-8)
- Check that :PROPERTIES: drawers are properly formatted
- Review error logs for specific file paths
- Test with sample notes first: `ORG_ROAM_PATH=./samples/notes`

### Spring Shell Not Starting
**Problem**: Application exits immediately or shell doesn't appear
**Solution**:
- Check that `spring.main.web-application-type=none` is set
- Verify no conflicting autoconfiguration
- Review logs for Spring Boot startup errors
- Ensure Java 21+ is installed: `java -version`

### Memory Issues with Large Corpus
**Problem**: OutOfMemoryError when scanning large note collections
**Solution**:
- Increase heap size: `java -Xmx4g -jar target/embabel-note-gardener-*.jar`
- Process notes in batches (future enhancement)
- Check for circular link references in notes

### Spring AI Version Conflicts
**Problem**: `NoSuchMethodError` or version conflicts with Spring AI Ollama
**Solution**:
- **DO NOT** add `spring-ai-ollama-spring-boot-starter` dependency manually
- The Embabel framework (`embabel-agent-starter-shell`) includes compatible Spring AI versions transitively
- Only configure via `embabel.models` in application.yml
- Let `@EnableAgents(localModels = {LocalModels.OLLAMA})` handle autoconfiguration

### LLM Not Being Called
**Problem**: Actions run but don't appear to use LLM
**Solution**:
- Verify Ollama is running and accessible
- Check logs for "Successfully registered Ollama LLM" messages
- Ensure `@EnableAgents` annotation is present with `localModels = {LocalModels.OLLAMA}`
- Check that `OllamaChatService` and `OllamaEmbeddingService` beans are being created
- Look for WARN messages about missing `ChatModel` or `EmbeddingModel` beans

## Production Deployment

### org-roam-agent-backend (192.168.20.136)

**Status**: ✅ Fully operational (deployed 2025-10-21)

**Configuration**: Full stack deployment on dedicated Proxmox LXC container

**Components**:
- JAR: `/opt/org-roam-agent/embabel-note-gardener-0.1.0-SNAPSHOT.jar` (87MB)
- Config: `/opt/org-roam-agent/application.yml`
- Timer: `org-roam-agent-audit.timer` (nightly at 2 AM)
- Profile: `dry-run` (default - requires approval for changes)

**Key Configuration Differences**:
```yaml
spring:
  ai:
    ollama:
      base-url: http://feynman.cruver.network:11434  # Remote Ollama!

gardener:
  notes-path: /mnt/org-roam/files  # Shared volume
  mcp:
    enabled: true
    base-url: http://localhost:8000  # Local MCP server
```

**Deployment Commands**:
```bash
# Build locally
./mvnw clean package -DskipTests

# Deploy to server
scp target/embabel-note-gardener-0.1.0-SNAPSHOT.jar root@192.168.20.136:/opt/org-roam-agent/

# Manual execution (non-interactive)
ssh root@192.168.20.136 "cd /opt/org-roam-agent && \
  export SPRING_CONFIG_LOCATION=file:./application.yml && \
  java -Dspring.shell.interactive.enabled=false \
       -Dspring.shell.command.script.enabled=true \
       -jar embabel-note-gardener-0.1.0-SNAPSHOT.jar \
       status"

# Service management
ssh root@192.168.20.136 "systemctl status org-roam-agent-audit.timer"
ssh root@192.168.20.136 "systemctl list-timers"
ssh root@192.168.20.136 "journalctl -u org-roam-agent-audit -f"
```

**Health Checks**:
```bash
# Verify Ollama (remote)
curl -s http://feynman.cruver.network:11434/api/tags | jq '.models[].name'

# Verify MCP (local)
ssh root@192.168.20.136 "curl http://localhost:8000"

# Verify Emacs
ssh root@192.168.20.136 "export EMACS_SERVER_FILE=/root/emacs-server/server && emacsclient --eval '(+ 1 1)'"

# Test agent status
ssh root@192.168.20.136 "cd /opt/org-roam-agent && timeout 60 java -Dspring.shell.interactive.enabled=false -Dspring.shell.command.script.enabled=true -jar embabel-note-gardener-0.1.0-SNAPSHOT.jar status 2>&1 | grep -i 'scanning corpus'"
```

**Architecture Notes**:
- **Remote Ollama**: Uses `feynman.cruver.network:11434` instead of localhost
- **Shared Volume**: Notes at `/mnt/org-roam/files` from Proxmox host `/external-storage/org-roam`
- **Local MCP**: MCP server runs on same container for minimal latency
- **Timer-based**: Uses systemd timer for nightly audits rather than persistent service

**Systemd Services**:
```ini
# /etc/systemd/system/org-roam-agent-audit.service (oneshot)
[Service]
Type=oneshot
WorkingDirectory=/opt/org-roam-agent
Environment="SPRING_CONFIG_LOCATION=file:/opt/org-roam-agent/application.yml"
ExecStart=/usr/bin/java -Dspring.shell.interactive.enabled=false -Dspring.shell.command.script.enabled=true -jar embabel-note-gardener-0.1.0-SNAPSHOT.jar audit

# /etc/systemd/system/org-roam-agent-audit.timer
[Timer]
OnCalendar=daily
OnCalendar=02:00
Persistent=true
```

**Documentation**: See `/tmp/agent_installation_summary.md` on server for complete details

## Key Design Principles

1. **Embabel GOAP idioms** - Follow Embabel's planning model (goals, actions, preconditions/effects, planner, executor)
2. **Deterministic structure + agentic prioritization** - GOAP finds paths never explicitly programmed from actions you add
3. **Safe by default** - Dry-run mode, immutable source notes, opt-out tags, proposals for risky changes
4. **Explainability** - Every change produces rationale and diff
5. **Reversibility** - Timestamped backups, patch files for review
6. **No external DB required** - Local file + embedded store only
7. **Ollama integration** - All LLM and embedding operations via Ollama (Spring AI integration)
8. **Spring Shell interaction** - Interactive shell for manual control and monitoring

## Current Implementation Status

### ✅ Completed & Working (Agent is Functional!)
- Maven project structure with Spring Boot 3.5.5 and Java 21
- Embabel agent framework integration (v0.1.2) with Ollama local models
- **Spring AI configuration** (`SpringAIConfiguration`) - Manually creates ChatModel/EmbeddingModel beans with proper model management
- **Corpus scanning with LLM format checking** - `CorpusScanner` scans org-roam directory and **calls LLM to verify formatting**
- **GOAP planner** - `GOAPPlanner` generates action plans with cost estimates
- **Health score calculation** - `HealthScoreCalculator` computes per-note and corpus health
- Core domain classes: `NoteMetadata`, `CorpusState`, `CorpusScanner`
- Planning classes: `ActionPlan`, `PlannedAction`, `GOAPPlanner`
- **Action execution engine** - `ActionExecutor` ✅ **FULLY WORKING**:
  - Executes actions in sequence with state updates
  - Handles safe vs. proposal actions
  - Detailed execution results with status indicators
  - Supports safe-only mode
- **NormalizeFormatting action** - ✅ **FULLY WORKING**:
  - `CorpusScanner` calls LLM during audit to analyze formatting
  - Detects missing :PROPERTIES:, missing :ID:/:CREATED:, missing title, etc.
  - Audit takes 15-30 seconds (LLM analysis per note with issues)
  - Action execution applies LLM-based fixes
- **SuggestLinks action** - ✅ **FULLY WORKING**:
  - Uses MCP semantic search to find related notes
  - Generates link proposals with LLM rationale
  - Creates proposals with diffs showing link additions
- **ComputeEmbeddings action** - ✅ **Ready** (delegates to MCP)
- **MCP Client Integration** - `OrgRoamMcpClient` ✅ **FULLY WORKING**:
  - HTTP JSON-RPC client for MCP server
  - Semantic search and contextual search
  - Health checking and availability detection
  - Used by SuggestLinks action
- I/O infrastructure: `OrgFileReader`, `OrgFileWriter`, `PatchWriter`
- Proposal management: `ChangeProposal`, proposal listing/viewing commands
- **NLP components with working Ollama integration:**
  - `OllamaChatService` - ✅ Working LLM chat (formatting analysis + fixing + rationale)
  - `OllamaEmbeddingService` - ✅ Bean created (not actively used - delegates to MCP)
- Configuration properties for all policies
- Test infrastructure with Org file round-trip tests
- **Shell commands implemented:**
  - `status` - Show corpus health statistics ✅ Working
  - `audit` - Scan corpus with LLM format checking and generate action plan ✅ Working
  - `execute` - Execute the entire action plan ✅ Working
  - `apply safe` - Execute only safe actions ✅ Working
  - `proposals list` - List all pending proposals ✅ Working
  - `proposals show <id>` - Show proposal details ✅ Working

### 📋 Next Priority (Enhancements)
1. **Proposal application** - Implement `proposals apply <id>`
   - Apply patch files to notes
   - Mark proposals as applied
   - Update corpus state

2. **File watching for background daemon mode**
   - Java NIO WatchService for file monitoring
   - Debounced execution on file changes
   - Automatic triggering without shell interaction

### 🔮 Future Enhancements
- Scheduler for nightly audits and file watching
- Remaining actions: `FixDeadLinks`, `QueueStaleReview`, `CanonicalizeTags`, `ProposeSplitOrMerge`
- Daily report generation
- Embedding freshness tracking (query MCP for status)

## Sample Notes Location

`/samples/notes` - Test corpus for development and testing
