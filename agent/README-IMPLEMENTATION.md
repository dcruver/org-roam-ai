# Org-Roam Gardener

A production-ready Embabel (GOAP) agent service that maintains a local Org/Org-roam knowledge base by auditing and fixing issues related to embeddings, formatting, links, taxonomy, and staleness. All edits are explainable, reversible, and safe by default.

## Features

- **GOAP-Based Planning**: Uses Embabel's Goal-Oriented Action Planning to find optimal maintenance plans
- **Safe by Default**: Dry-run mode by default; all changes create patches and backups
- **Ollama Integration**: Uses local Ollama for LLM operations and embeddings (no cloud API keys required)
- **Spring Shell Interface**: Interactive CLI for managing your knowledge base
- **Comprehensive Reporting**: Daily Org-format reports of all maintenance activities

## Prerequisites

- **Java 21+**
- **Ollama** running locally or accessible via network
- **Maven** (or use included Maven wrapper)

### Install Required Ollama Models

```bash
ollama pull gpt-oss:20b
ollama pull nomic-embed-text:latest
```

## Quick Start

### 1. Configure Your Org-Roam Path

Edit `src/main/resources/application.yml`:

```yaml
gardener:
  notes-path: /path/to/your/org-roam
```

Or set environment variable:

```bash
export ORG_ROAM_PATH=~/org-roam
```

### 2. Build the Project

```bash
./mvnw clean package
```

### 3. Run the Application

```bash
./mvnw spring-boot:run
```

Or as an executable JAR:

```bash
java -jar target/embabel-note-gardener-*.jar
```

### 4. Use Shell Commands

Once the shell starts, try:

```
status              # Show corpus health
audit               # Run full audit
execute             # Execute plan
apply safe          # Apply only safe actions
proposals list      # List pending proposals
report              # Generate daily report
help                # Show all commands
```

## Execution Modes

### Dry-Run Mode (Default)

No `.org` files are modified. All changes produce:
- `.patch` files showing proposed changes
- `.change.json` files with rationale and stats
- Timestamped backups in `.gardener/backups/`

```bash
java -jar target/embabel-note-gardener-*.jar --spring.profiles.active=dry-run
```

### Auto Mode

Safe actions are applied automatically; risky changes still create proposals:

```bash
java -jar target/embabel-note-gardener-*.jar --spring.profiles.active=auto
```

## Actions

### Safe Actions (Auto-Applied in Auto Mode)

- **ComputeEmbeddings**: Generate embeddings for notes missing them or with stale embeddings
- **NormalizeFormatting**: Ensure properties drawer, title, and final newline
- **FixDeadLinks**: Retry dead HTTP(S) links and create issue records
- **QueueStaleReview**: Flag notes untouched for > N days

### Semi-Auto Actions (Create Proposals)

- **SuggestLinks**: Find related notes using embedding similarity and propose links
- **CanonicalizeTags**: Apply 1:1 tag mappings; multi-mappings become proposals

### Proposal Actions (Always Require Approval)

- **ProposeSplitOrMerge**: Analyze topic drift and suggest note splits/merges

## Health Score

Each note is scored 0-100 based on:

| Component                | Weight |
|--------------------------|--------|
| Formatting               | 10     |
| Provenance               | 25     |
| Embeddings Freshness     | 15     |
| Links                    | 20     |
| Taxonomy                 | 10     |
| Contradictions (inverse) | 10     |
| Staleness (inverse)      | 10     |

**Total:** 100 points

The GOAP planner chooses actions to raise mean corpus health to the target (default: 90).

## Configuration

Key configuration options in `application.yml`:

```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434    # Ollama server
      chat:
        options:
          model: gpt-oss:20b               # LLM model
      embedding:
        options:
          model: nomic-embed-text:latest   # Embedding model

gardener:
  notes-path: ~/org-roam                   # Your notes directory
  target-health: 90                        # Target health score

  embeddings:
    max-age-days: 90                       # Recompute after this
    chunk-size: 1600                       # Characters per chunk
    chunk-overlap-percent: 12

  linking:
    confidence-threshold: 0.72             # Min similarity for links
    max-links-per-run: 7
    auto-link-source-notes: false          # Source notes are read-only
```

## Safety Mechanisms

### 1. Opt-Out Tag

Add `#agents:off` to any note to prevent modifications:

```org
* My Private Note #agents:off

The gardener will not modify this note.
```

### 2. Source Notes Are Immutable

Notes classified as `Source` type have read-only content (metadata can be updated).

### 3. Backups

Every change creates a timestamped backup in `.gardener/backups/`.

### 4. Proposals

Risky changes create proposal files in `.gardener/proposals/`:
- `note-id-proposal-id.json` - Metadata and rationale
- `note-id-proposal-id.patch` - Unified diff

Review with `proposals show <id>` and apply with `proposals apply <id>`.

## Deployment on Proxmox

```bash
# Build
./mvnw clean package

# Copy to server
scp target/embabel-note-gardener-*.jar proxmox-server:/opt/org-roam-agent/

# Run (with custom config)
java -jar /opt/org-roam-agent/embabel-note-gardener-*.jar \
  --spring.config.location=/opt/org-roam-agent/application.yml
```

### Systemd Service Example

Create `/etc/systemd/system/org-roam-gardener.service`:

```ini
[Unit]
Description=Org-Roam Gardener
After=network.target

[Service]
Type=simple
User=youruser
WorkingDirectory=/opt/org-roam-agent
ExecStart=/usr/bin/java -jar embabel-note-gardener-*.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

Then:

```bash
sudo systemctl enable org-roam-gardener
sudo systemctl start org-roam-gardener
```

## GOAP Architecture

### Goals

Goals define desired states:
- `MaintainHealthyCorpus` - Top-level goal (health ≥ target)
- `EnsureEmbeddingsFresh` - All notes have fresh embeddings
- `EnforceFormattingPolicy` - All notes properly formatted
- `ReduceOrphans` - Minimize orphan notes

### Actions

Actions have:
- **Preconditions**: When the action can execute
- **Effects**: State changes produced
- **Cost**: For planner optimization
- **Execute**: Implementation

### Planning

The Embabel GOAP planner:
1. Evaluates current world state (CorpusState)
2. Checks which goals are unsatisfied
3. Finds action sequences to satisfy goals
4. Chooses minimal-cost plan
5. Executes actions, reassessing after each

## Extension Points

### Add New Actions

1. Implement `com.embabel.agent.core.action.Action<CorpusState>`
2. Define preconditions, effects, cost, and execute logic
3. Annotate with `@Component`
4. Spring will auto-register it

### Add New Goals

1. Implement `com.embabel.agent.core.goal.Goal<CorpusState>`
2. Define evaluation logic and priority
3. Annotate with `@Component`

### Use Different LLM Models

Edit `application.yml`:

```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          model: llama3:70b  # Or any Ollama model
```

## Testing

Run tests:

```bash
./mvnw test
```

Key test coverage:
- **Formatting idempotence**: Read/write cycles preserve content
- **Round-trip preservation**: Whitespace and structure maintained
- **Health score calculation**: Correct scoring per weights
- **Action preconditions**: Only execute when conditions met

## Troubleshooting

### Ollama Connection Failed

Check Ollama is running:

```bash
curl http://localhost:11434/api/tags
```

If on different host, update `spring.ai.ollama.base-url` in config.

### Embeddings Not Generated

Ensure `nomic-embed-text:latest` is pulled:

```bash
ollama list
ollama pull nomic-embed-text:latest
```

### Notes Not Being Modified (Dry-Run)

This is intentional! Dry-run mode (default) only creates proposals.

Switch to auto mode to apply safe actions:

```bash
java -jar target/embabel-note-gardener-*.jar --spring.profiles.active=auto
```

## Project Structure

```
embabel-note-gardener/
├── src/main/java/com/dcruver/orgroam/
│   ├── app/          Spring Boot + Shell application
│   ├── domain/       World state, goals, actions
│   ├── io/           Org file I/O, patches, backups
│   ├── nlp/          Ollama integration, embeddings
│   ├── policy/       Configuration models
│   ├── proposals/    Proposal storage
│   └── reporting/    Daily report generation
├── src/test/java/    Unit tests
├── samples/notes/    Sample org files for testing
└── pom.xml          Maven dependencies
```

## License

[Specify your license]

## Contributing

Contributions welcome! Please ensure:
- Tests pass (`./mvnw test`)
- Formatting idempotence is preserved
- New actions follow GOAP pattern (preconditions, effects, cost)
