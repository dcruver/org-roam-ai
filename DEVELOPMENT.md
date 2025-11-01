# Development Guide

**[org-roam-ai](README.md) > Development**

This guide covers development workflows for all three components of org-roam-ai.

## Prerequisites for All Components

### Required Software

- **Ollama** (all components)
  ```bash
  curl -fsSL https://ollama.ai/install.sh | sh
  ollama serve  # Start server

  # Pull required models
  ollama pull nomic-embed-text:latest    # Embeddings
  ollama pull llama3.1:8b                # General LLM
  ollama pull gpt-oss:20b                # Agent LLM
  ```

- **Emacs** 27+ with org-roam (all components)
  ```elisp
  ;; Ensure Emacs server is running
  (server-start)
  ```

- **Python** 3.8+ (MCP server)
- **Java** 21+ (Agent)
- **Maven** 3.6+ or use Maven wrapper (Agent)

### Verify Prerequisites

```bash
# Check Ollama
curl http://localhost:11434/api/tags

# Check Emacs server
emacsclient --eval '(+ 1 1)'  # Should return 2

# Check Python
python3 --version

# Check Java
java -version

# Check Maven
mvn --version
```

---

## Development Environment Setup

### Full Stack Development (All Components)

```bash
# Clone/navigate to repository
cd org-roam-ai

# Setup Emacs package
cd emacs
# Load in Emacs - see Emacs section below

# Setup MCP server
cd ../mcp
python -m venv .venv
source .venv/bin/activate
pip install -e ".[dev]"

# Setup Agent
cd ../agent
./mvnw clean install
```

### Component-Specific Setup

#### Emacs Package Development

```elisp
;; In your Emacs init file or scratch buffer

;; Add to load path
(add-to-list 'load-path "/path/to/org-roam-ai/emacs")

;; Load dependencies
(require 'org-roam)
(require 'json)
(require 'url)
(require 'ox-md)
(require 'cl-lib)

;; Load modules
(require 'org-roam-vector-search)
(require 'org-roam-ai-assistant)

;; Configure
(setq org-roam-semantic-ollama-url "http://localhost:11434")

;; Test
(org-roam-semantic-version)  ; Check version
(org-roam-semantic-status)   ; Check embedding coverage
```

**Development Workflow**:
1. Make changes to `.el` files
2. Reload: `M-x eval-buffer` or restart Emacs
3. Test interactively: `C-c v s`, etc.
4. Check for errors: `*Messages*` buffer

**Debugging**:
```elisp
;; Enable debug on error
(setq debug-on-error t)

;; Test embedding generation
(org-roam-semantic-generate-embedding)

;; Check similarity calculation
(org-roam-semantic-get-similar-data "your query" 5)
```

#### MCP Server Development

```bash
cd mcp

# Activate virtual environment
source .venv/bin/activate  # Linux/Mac
# or
.venv\Scripts\activate     # Windows

# Install in development mode
pip install -e ".[dev]"

# Run server
python -m org_roam_mcp.server

# Or use installed script
org-roam-mcp
```

**Development Workflow**:
1. Make changes to Python files in `src/org_roam_mcp/`
2. Server auto-reloads on file changes (Starlette dev mode)
3. Test via HTTP:
   ```bash
   curl -X POST http://localhost:8000 -H "Content-Type: application/json" -d '{
     "jsonrpc": "2.0",
     "method": "tools/list",
     "id": 1
   }'
   ```

**Testing**:
```bash
# Run all tests
pytest

# Run specific test file
pytest tests/test_emacs_client.py

# Run with coverage
pytest --cov=src/

# Run with verbose output
pytest -v
```

**Code Quality**:
```bash
# Format code
black src/ tests/

# Lint
ruff check src/ tests/

# Fix auto-fixable issues
ruff check --fix src/ tests/

# Type checking
mypy src/
```

#### Agent Development

```bash
cd agent

# Build project
./mvnw clean package

# Skip tests for faster build
./mvnw clean package -DskipTests

# Run application
./mvnw spring-boot:run

# Or run as JAR
java -jar target/embabel-note-gardener-*.jar
```

**Development Workflow**:
1. Make changes to Java files in `src/main/java/`
2. Build: `./mvnw clean package`
3. Run tests: `./mvnw test`
4. Run application: `./mvnw spring-boot:run`

**Testing**:
```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=CorpusScannerTest

# Run with sample notes
ORG_ROAM_PATH=./samples/notes ./mvnw spring-boot:run

# Non-interactive testing
./test-audit.sh
```

**Debugging**:
```bash
# Run with debug logging
./mvnw spring-boot:run -Dlogging.level.com.dcruver.orgroam=DEBUG

# Run with remote debugging
./mvnw spring-boot:run -Dagentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
```

---

## Common Development Tasks

### Task 1: Add New MCP Tool

**Scenario**: Add a new tool to search by tag

**Steps**:

1. **Add Elisp function** (`packages/org-roam-ai/org-roam-api.el`):
   ```elisp
   (defun my/api-search-by-tag (tag)
     "Search notes by tag, return JSON."
     (json-encode
       (mapcar (lambda (node)
                 `((file . ,(org-roam-node-file node))
                   (title . ,(org-roam-node-title node))))
               (org-roam-db-query
                [:select [nodes:file nodes:title]
                 :from tags
                 :left-join nodes :on (= tags:node-id nodes:id)
                 :where (= tags:tag $s1)]
                tag))))
   ```

2. **Add MCP tool definition** (`mcp/src/org_roam_mcp/server.py`):
   ```python
   @mcp.tool()
   async def search_by_tag(tag: str) -> dict:
       """Search notes by tag."""
       result = emacs_client.call_elisp(
           "my/api-search-by-tag",
           tag
       )
       return {"notes": result}
   ```

3. **Test**:
   ```bash
   # Restart MCP server
   org-roam-mcp

   # Test via curl
   curl -X POST http://localhost:8000 -d '{
     "method": "tools/call",
     "params": {"name": "search_by_tag", "arguments": {"tag": "AI"}}
   }'
   ```

4. **Document** in `mcp/README.md`

### Task 2: Add New Agent Action

**Scenario**: Add action to detect duplicate notes

**Steps**:

1. **Create action class** (`agent/src/main/java/com/dcruver/orgroam/domain/actions/DetectDuplicatesAction.java`):
   ```java
   @Component
   public class DetectDuplicatesAction implements Action<CorpusState> {

       @Override
       public boolean canExecute(CorpusState state) {
           return state.getNotes().size() > 1;
       }

       @Override
       public double estimateCost(CorpusState state) {
           // Compare all pairs: O(n²) semantic similarity
           return state.getNotes().size() * state.getNotes().size() * 0.5;
       }

       @Override
       public ActionResult execute(CorpusState state) {
           // Use MCP semantic search to find similar notes
           // Generate proposal with merge suggestions
           return ActionResult.success("Found X potential duplicates");
       }
   }
   ```

2. **Test**:
   ```bash
   # Build
   ./mvnw clean package

   # Run with sample notes
   ORG_ROAM_PATH=./samples/notes java -jar target/embabel-note-gardener-*.jar

   # In shell
   starwars> audit
   # Should show DetectDuplicates action in plan
   ```

3. **Document** in `agent/README.md`

### Task 3: Update Embedding Model

**Scenario**: Switch to a different Ollama embedding model

**Steps**:

1. **Pull new model**:
   ```bash
   ollama pull mxbai-embed-large
   ```

2. **Update Emacs config**:
   ```elisp
   (setq org-roam-semantic-embedding-model "mxbai-embed-large")
   (setq org-roam-semantic-embedding-dimensions 1024)  ; Check model docs
   ```

3. **Update agent config** (`agent/src/main/resources/application.yml`):
   ```yaml
   spring:
     ai:
       ollama:
         embedding:
           options:
             model: mxbai-embed-large

   gardener:
     embedding-model: mxbai-embed-large
   ```

4. **Regenerate embeddings**:
   ```elisp
   M-x org-roam-semantic-generate-all-embeddings
   ```

### Task 4: Debug Integration Issues

**Scenario**: Agent can't connect to MCP server

**Steps**:

1. **Verify MCP server is running**:
   ```bash
   curl http://localhost:8000
   # Should return: {"message": "org-roam-mcp server running"}
   ```

2. **Check Emacs server**:
   ```bash
   emacsclient --eval '(+ 1 1)'
   # Should return: 2
   ```

3. **Test MCP tool manually**:
   ```bash
   curl -X POST http://localhost:8000 -d '{
     "method": "tools/call",
     "params": {"name": "search_notes", "arguments": {"query": "test"}}
   }'
   ```

4. **Check agent logs**:
   ```bash
   # Look for MCP connection errors
   tail -f agent/embabel-note-gardener.log
   ```

5. **Verify configuration** (`agent/application.yml`):
   ```yaml
   gardener:
     mcp:
       enabled: true
       base-url: http://localhost:8000
   ```

---

## Testing Strategy

### Unit Tests

**Emacs**: Manual testing in Emacs (no automated test framework currently)
```elisp
;; Test embedding generation
(org-roam-semantic-generate-embedding)

;; Test search
(org-roam-semantic-get-similar-data "machine learning" 5)
```

**MCP Server**: Pytest with mocking
```bash
cd mcp
pytest tests/test_emacs_client.py -v
```

**Agent**: JUnit with Spring Boot Test
```bash
cd agent
./mvnw test
./mvnw test -Dtest=CorpusScannerTest
```

### Integration Tests

**MCP → Emacs**:
```bash
# Start Emacs server
emacs --daemon

# Start MCP server
cd mcp
org-roam-mcp &

# Test via HTTP
curl -X POST http://localhost:8000 -d '{
  "method": "tools/call",
  "params": {"name": "search_notes", "arguments": {"query": "test"}}
}'
```

**Agent → MCP → Emacs**:
```bash
# Start full stack
emacs --daemon
cd mcp && org-roam-mcp &
cd agent && java -jar target/embabel-note-gardener-*.jar

# In agent shell
starwars> audit
starwars> proposals list
```

### End-to-End Tests

**Scenario**: Create note via MCP, search via Emacs, agent suggests links

```bash
# 1. Create note via MCP
curl -X POST http://localhost:8000 -d '{
  "method": "tools/call",
  "params": {
    "name": "create_note",
    "arguments": {
      "title": "Test Note",
      "content": "This is about machine learning.",
      "type": "permanent"
    }
  }
}'

# 2. Search in Emacs
# In Emacs: C-c v s → "machine learning"
# Should show new note

# 3. Run agent audit
# In agent shell: audit
# Should suggest links to related ML notes
```

---

## Code Style & Conventions

### Emacs Lisp

- Follow Emacs Lisp conventions (dash-separated names)
- Prefix all public functions with `org-roam-semantic-`
- Document all public functions with docstrings
- Use `defcustom` for user-configurable variables
- Limit lines to 80 characters where practical

### Python

- **Format**: Black (line length: 88)
- **Lint**: Ruff with default rules
- **Type hints**: Required for all public functions
- **Docstrings**: Google style
- **Imports**: Grouped by stdlib, third-party, local

```python
def search_notes(query: str, limit: int = 10) -> dict:
    """Search org-roam notes.

    Args:
        query: Search query string
        limit: Maximum results to return

    Returns:
        Dictionary with 'notes' key containing list of results
    """
```

### Java

- **Format**: Spring Java conventions (4-space indent)
- **Style**: Follow existing codebase patterns
- **Docs**: Javadoc for all public classes/methods
- **Tests**: JUnit 5 with descriptive test names

```java
/**
 * Scans the corpus and builds state for planning.
 */
@Component
public class CorpusScanner {
    /**
     * Scan all notes and build corpus state.
     *
     * @param notesPath Path to org-roam directory
     * @return Complete corpus state with health metrics
     */
    public CorpusState scan(Path notesPath) { ... }
}
```

---

## Git Workflow

### Branch Strategy

- `main` - Stable, deployable code
- `develop` - Integration branch for features
- `feature/<name>` - Feature development
- `fix/<name>` - Bug fixes

### Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

**Types**: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`
**Scopes**: `emacs`, `mcp`, `agent`, `docs`, `ci`

**Examples**:
```
feat(emacs): add chunking support for section-level embeddings

fix(mcp): handle character arrays in JSON responses

docs(architecture): add MCP integration diagrams

refactor(agent): extract LLM service to separate component
```

### Pull Request Process

1. Create feature branch
2. Make changes with tests
3. Run linters/formatters for affected components
4. Update documentation
5. Submit PR with description and testing notes
6. Address review feedback
7. Squash and merge to develop

---

## Release Process

### Versioning

- **Semantic Versioning**: MAJOR.MINOR.PATCH
- **Emacs**: Tag in git, update version in `.el` files
- **MCP**: Update `pyproject.toml` version, build, publish to PyPI/Gitea
- **Agent**: Update `pom.xml` version, build JAR

### Release Checklist

- [ ] All tests passing
- [ ] Documentation updated
- [ ] CHANGELOG.md updated
- [ ] Version bumped in all relevant files
- [ ] Tag created: `git tag -a v1.2.0 -m "Release v1.2.0"`
- [ ] Build artifacts created
- [ ] Release notes written

---

## Troubleshooting Development Issues

### "Symbols function definition is void: fourth"

**Component**: Emacs
**Cause**: Missing `cl-lib` dependency
**Fix**: Add `(require 'cl-lib)` before loading org-roam-semantic

### "ModuleNotFoundError: No module named 'org_roam_mcp'"

**Component**: MCP
**Cause**: Package not installed or wrong virtual environment
**Fix**:
```bash
source .venv/bin/activate
pip install -e .
```

### "Connection refused" to Ollama

**All components**
**Cause**: Ollama not running or wrong URL
**Fix**:
```bash
ollama serve  # Start Ollama
curl http://localhost:11434/api/tags  # Verify
```

### "No such file or directory: emacs-server/server"

**Component**: MCP
**Cause**: Emacs server not started or wrong path
**Fix**:
```elisp
;; In Emacs
(server-start)

;; Or set environment variable
export EMACS_SERVER_FILE="/home/user/.emacs.d/server/server"
```

### Maven dependency resolution fails

**Component**: Agent
**Cause**: Embabel repository not accessible
**Fix**: Check `pom.xml` has correct repository URL:
```xml
<repository>
    <id>embabel-releases</id>
    <url>https://repo.embabel.com/artifactory/libs-release</url>
</repository>
```

---

## IDE Setup Recommendations

### Emacs Development

- Use Emacs itself for development
- Install `flycheck` for syntax checking
- Use `company-mode` for completion
- Consider `lsp-mode` with `emacs-lisp-ls`

### Python Development (MCP)

**VS Code**:
- Install Python extension
- Configure Black formatter
- Enable Ruff linting
- Set interpreter to `.venv/bin/python`

**PyCharm**:
- Set project interpreter to `.venv`
- Enable Black on save
- Configure Ruff as external tool

### Java Development (Agent)

**IntelliJ IDEA** (recommended):
- Import as Maven project
- Install Spring Boot plugin
- Configure Java 21 SDK
- Enable Lombok plugin

**VS Code**:
- Install Java Extension Pack
- Install Spring Boot Extension Pack
- Configure Maven

---

## Performance Optimization Tips

### Emacs Package

- **Batch embedding generation**: Process multiple notes in one session
- **Cache embeddings**: Don't regenerate unless content changed
- **Lazy loading**: Only load embeddings for search, not on startup

### MCP Server

- **Keep Emacs server warm**: Don't restart frequently
- **Connection pooling**: Reuse emacsclient connections (future)
- **Caching**: Cache frequently accessed notes (future)

### Agent

- **Incremental audits**: Only scan changed notes (future)
- **Parallel LLM calls**: Use async for independent operations (future)
- **Database indexing**: Index embeddings table for faster queries

---

## Documentation Standards

- **README files**: User-focused, quick start, examples
- **Architecture docs**: Technical depth, diagrams, rationale
- **Code comments**: Why, not what (code shows what)
- **API docs**: All parameters, return types, examples
- **Changelogs**: User-facing changes, migration notes

---

**Next Steps**:
- See [ARCHITECTURE.md](ARCHITECTURE.md) for system design details
- See [README.md](README.md) for user-facing documentation
- See component READMEs for specific setup instructions
