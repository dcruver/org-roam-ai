# Embabel Note Gardener

**[org-roam-ai](../README.md) > Maintenance Agent**

An intelligent GOAP (Goal-Oriented Action Planning) agent for maintaining your Org-roam knowledge base.

## What It Does

The Note Gardener automatically audits your Org-roam notes and recommends improvements:
- **Embeddings**: Generate semantic embeddings for AI-powered search and linking
- **Formatting**: Ensure consistent structure (properties drawer, titles, metadata)
- **Links**: Suggest connections between related notes to reduce orphans
- **Health Scoring**: Track corpus health with configurable metrics
- **Safe by Default**: Dry-run mode, explainable changes, human approval for risky actions

## Quick Start

```bash
# Build
./mvnw clean package

# Run with sample notes
ORG_ROAM_PATH=./samples/notes java -jar target/embabel-note-gardener-*.jar

# Check corpus health
starwars> status

# Generate improvement plan
starwars> audit
```

## Example Output

```
Corpus Health Status

Overall Health: 51.3 / 90

Statistics:
- Total notes: 4
- With embeddings: 0 (fresh) + 0 (stale)
- Missing embeddings: 4
- With format issues: 1
- Orphan notes: 3
- Stale notes: 4

Target health: 90
Improvement needed: 38.8 points
```

```
Audit completed.

Plan: 3 actions recommended

Recommended Actions:
1. [SAFE] NormalizeFormatting
   Fix formatting issues in 1 notes
   Affects 1 notes (cost: 1.0)
2. [SAFE] ComputeEmbeddings
   Compute embeddings for 4 notes (missing or stale)
   Affects 4 notes (cost: 20.0)
3. [PROPOSAL] SuggestLinks
   Suggest links for 3 orphan notes to improve connectivity
   Affects 3 notes (cost: 40.0)

Corpus health is 51.3/90 (38.8 points below target).
Generated 3 actions: 2 safe (auto-apply), 1 proposals (require approval).
```

## Technology Stack

- **Java 21+** with Spring Boot 3.5.5
- **Spring Shell** for interactive CLI
- **Embabel Framework** for GOAP planning
- **Ollama** for LLM and embeddings (local, private)
- **Spring AI** for AI integration

## Architecture

The system uses GOAP (Goal-Oriented Action Planning):
1. **Scan** the corpus and build world state
2. **Evaluate** which actions can execute
3. **Plan** optimal sequence based on costs
4. **Execute** actions safely with backups
5. **Reassess** state after each action

## Current Features

✅ **Implemented:**
- Corpus scanning and state building (`CorpusScanner`)
- Health score calculation (`HealthScoreCalculator`)
- GOAP planning with cost estimation (`GOAPPlanner`)
- `status` command - Check corpus health
- `audit` command - Generate action plan
- `proposals list/show` - View pending changes
- Three actions: ComputeEmbeddings, NormalizeFormatting, SuggestLinks

🚧 **Coming Soon:**
- Action execution engine
- Automatic safe action application
- Proposal approval workflow
- Nightly scheduled audits
- Daily health reports

## Configuration

Edit `application.yml` to customize:
- Notes directory path (`gardener.notes-path`)
- Ollama model selection
- Health score weights
- Linking thresholds
- Staleness definitions

## Running Modes

**Dry-run** (default): Shows what would be done, no changes
```bash
java -jar target/embabel-note-gardener-*.jar
```

**Auto** (future): Automatically applies safe actions
```bash
java -jar target/embabel-note-gardener-*.jar --spring.profiles.active=auto
```

**Custom Notes Path**: Use environment variable
```bash
ORG_ROAM_PATH=/path/to/org-roam java -jar target/embabel-note-gardener-*.jar
```

## Requirements

- Java 21+
- Maven 3.6+
- Ollama running locally (or accessible via network)
- Ollama models: `gpt-oss:20b`, `nomic-embed-text:latest`

## Shell Commands

Available commands in the interactive shell:

- `status` - Check corpus health and statistics
- `audit` - Scan corpus and generate action plan
- `proposals list` - List all pending proposals
- `proposals show <id>` - Show proposal details
- `execute` - Execute the current plan (TODO)
- `apply safe` - Apply safe actions only (TODO)
- `proposals apply <id>` - Apply a specific proposal (TODO)
- `help` - Show available commands
- `exit` - Exit the shell

## Documentation

- [CLAUDE.md](CLAUDE.md) - Detailed architecture and development guide
- [samples/notes/](samples/notes/) - Sample Org-roam notes for testing

## Project Structure

```
com.dcruver.orgroam/
├── app/                    Spring Boot + Shell application
├── domain/                 World state, planning, actions
│   ├── actions/           Action implementations
│   └── planning/          GOAP planner
├── io/                    Org file readers/writers
├── nlp/                   Ollama integration
└── reporting/             Health reports
```

## How GOAP Works

1. **CorpusScanner** reads all .org files and builds `CorpusState`
2. **GOAPPlanner** evaluates available actions based on:
   - Preconditions: Can this action execute?
   - Cost: How expensive is it?
   - Safety: Can it auto-apply?
3. Actions are prioritized: safe first, then by cost
4. Plan shows exactly what will be done to improve health

## License

[Add your license here]
