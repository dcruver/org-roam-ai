# Embabel Refactor Plan

## Current Problem
- Manual `Action<CorpusState>` classes with explicit `canExecute()` preconditions
- Manual `GOAPPlanner` that just sorts a list
- No real planning - actions always run in same order
- Broken workflow - proposals pile up but never applied
- Can't add complex knowledge structure goals

## Embabel Solution
- Single `@Agent` class with typed `@Action` methods
- Type system defines dependencies (no manual preconditions)
- A* planner automatically sequences based on types
- `@AchievesGoal` marks terminal actions
- Real dynamic planning based on current state

## Domain Type Progression

Progressive enhancement pattern (each type builds on previous):

```
RawCorpus
  ↓ normalizeFormatting()
FormattedCorpus
  ↓ generateEmbeddings()
CorpusWithEmbeddings
  ↓ findOrphanClusters()
OrphanClusters
  ↓ analyzeClusterThemes()
ClustersWithThemes
  ↓ proposeHubNotes() [@AchievesGoal]
HealthyCorpus
```

## Agent Structure

```java
@Agent(description = "Maintains org-roam knowledge base health and structure")
public class OrgRoamMaintenanceAgent {

    // Services injected via Spring
    private final OrgFileReader reader;
    private final OrgFileWriter writer;
    private final OllamaChatService llm;
    private final OrgRoamMcpClient mcp;

    @Action
    public FormattedCorpus normalizeFormatting(RawCorpus corpus) {
        // Fix format issues, return enhanced corpus
    }

    @Action
    public CorpusWithEmbeddings generateEmbeddings(FormattedCorpus corpus) {
        // Generate embeddings via MCP, return enhanced corpus
    }

    @Action
    public OrphanClusters findOrphanClusters(CorpusWithEmbeddings corpus) {
        // Semantic clustering, return clusters
    }

    @Action
    public ClustersWithThemes analyzeClusterThemes(OrphanClusters clusters) {
        // LLM discovers themes, return enhanced clusters
    }

    @AchievesGoal(description = "Organized knowledge structure with hub notes")
    @Action
    public HealthyCorpus proposeHubNotes(ClustersWithThemes clusters) {
        // Generate MOC proposals, return final state
    }
}
```

## Shell Integration

Keep Spring Shell for user interaction:

```java
@ShellCommand
public class GardenerShellCommands {

    private final OrgRoamMaintenanceAgent agent;
    private final CorpusScanner scanner;

    @ShellMethod(key = "audit")
    public String audit() {
        RawCorpus raw = scanner.scanToRawCorpus();
        HealthyCorpus result = agent.run(raw);  // Embabel runs plan
        return formatReport(result);
    }
}
```

## Migration Steps

1. ✅ Commit current state (done)
2. Create domain types (RawCorpus, FormattedCorpus, etc)
3. Create @Agent class with @Action methods
4. Migrate logic from old Action classes to new @Action methods
5. Update Shell commands to invoke agent
6. Test Embabel planning
7. Remove old manual infrastructure (Action stubs, GOAPPlanner)

## Rollback Plan

If Embabel refactor fails:
```bash
git reset --hard 06c3d9f  # Restore to pre-refactor commit
```
