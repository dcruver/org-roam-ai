# True GOAP Implementation Design

## Vision: Knowledge Base Optimization

The goal is to transform the org-roam corpus into an **optimized semantic knowledge base** through:
1. **Semantic coherence** - Each note should contain one cohesive idea
2. **Optimal chunk size** - Notes sized for effective embeddings (300-800 tokens)
3. **Rich connectivity** - Every note reachable via meaningful links
4. **Minimal redundancy** - No duplicate or overlapping content

This requires **true declarative GOAP**, not the current imperative action list.

## Why True GOAP Is Essential

### Current Limitation (Imperative)

```java
// GOAPPlanner.java:44-48 - Hardcoded action list
List<Action<CorpusState>> availableActions = List.of(
    normalizeFormattingAction,  // Fixed order
    computeEmbeddingsAction,
    suggestLinksAction
);
```

**Problems**:
- Can't discover complex action sequences (e.g., Split → Re-embed → Link fragments → Maybe merge with existing note)
- Can't balance competing goals (coherence vs. chunk size vs. discoverability)
- Can't adapt when LLM analysis reveals new optimization opportunities

### True GOAP Solution

**Backward chaining from goals**:
1. Goal: `OptimizeSemanticCoherence` unsatisfied (note has multiple topics)
2. Planner searches: What actions produce semantic coherence?
3. Finds: `SplitNote` action (effect: increases coherence)
4. Checks preconditions: `MultipleTopicsDetected`, `HasClearBoundaries`
5. Discovers: Need `AnalyzeNoteStructure` first (provides split points)
6. Chains: `AnalyzeNoteStructure` → `SplitNote` → `ComputeEmbeddings` → `SuggestLinks`

## Goal Hierarchy

### Top-Level Goal: Optimized Knowledge Base

```java
@Component
public class OptimizeKnowledgeBase extends Goal<CorpusState> {
    @Override
    public GoalStatus evaluate(CorpusState state) {
        boolean coherent = state.getSemanticCoherenceScore() >= 0.85;
        boolean wellSized = state.getOptimalChunkRatio() >= 0.90;
        boolean connected = state.getAveragePathLength() <= 3.0;
        boolean nonRedundant = state.getRedundancyScore() <= 0.05;

        return (coherent && wellSized && connected && nonRedundant)
            ? GoalStatus.SATISFIED
            : GoalStatus.UNSATISFIED;
    }

    @Override
    public List<Goal<CorpusState>> decompose() {
        return List.of(
            new OptimizeSemanticCoherence(),
            new OptimizeChunkSize(),
            new MaximizeDiscoverability(),
            new MinimizeRedundancy()
        );
    }
}
```

### Subgoals

#### 1. Semantic Coherence
**Definition**: Each note discusses one cohesive topic

```java
@Component
public class OptimizeSemanticCoherence extends Goal<CorpusState> {
    @Override
    public GoalStatus evaluate(CorpusState state) {
        // Check if notes have high internal semantic similarity
        // but low cross-note topic overlap
        for (NoteMetadata note : state.getAllNotes()) {
            if (note.hasMultipleTopics() || note.getLowInternalCoherence() > 0.3) {
                return GoalStatus.UNSATISFIED;
            }
        }
        return GoalStatus.SATISFIED;
    }
}
```

**Actions that satisfy this goal**:
- `SplitNote` - Separate multi-topic notes
- `MergeNotes` - Combine fragmented related content
- `RefactorContent` - LLM rewrites to improve focus (advanced)

#### 2. Optimal Chunk Size
**Definition**: Notes are 300-800 tokens (optimal for embeddings)

```java
@Component
public class OptimizeChunkSize extends Goal<CorpusState> {
    private static final int MIN_TOKENS = 300;
    private static final int MAX_TOKENS = 800;

    @Override
    public GoalStatus evaluate(CorpusState state) {
        double optimalRatio = state.getNotesInRange(MIN_TOKENS, MAX_TOKENS)
            / (double) state.getTotalNotes();
        return (optimalRatio >= 0.90) ? GoalStatus.SATISFIED : GoalStatus.UNSATISFIED;
    }
}
```

**Actions that satisfy this goal**:
- `SplitNote` - Break large notes
- `MergeNotes` - Combine small notes
- `ExpandNote` - LLM adds context to tiny notes (advanced)

#### 3. Maximize Discoverability
**Definition**: All notes reachable within 2-3 link hops

```java
@Component
public class MaximizeDiscoverability extends Goal<CorpusState> {
    @Override
    public GoalStatus evaluate(CorpusState state) {
        return (state.getAveragePathLength() <= 3.0 && state.getOrphanCount() == 0)
            ? GoalStatus.SATISFIED
            : GoalStatus.UNSATISFIED;
    }
}
```

**Actions that satisfy this goal**:
- `SuggestLinks` - Add semantic links
- `CreateMOC` - Map of Content for dense clusters
- `AddBacklinks` - Ensure bidirectional connectivity

#### 4. Minimize Redundancy
**Definition**: No duplicate or highly overlapping content

```java
@Component
public class MinimizeRedundancy extends Goal<CorpusState> {
    @Override
    public GoalStatus evaluate(CorpusState state) {
        // Check for notes with >80% semantic similarity
        return (state.getRedundancyScore() <= 0.05)
            ? GoalStatus.SATISFIED
            : GoalStatus.UNSATISFIED;
    }
}
```

**Actions that satisfy this goal**:
- `MergeNotes` - Combine duplicates
- `DetectRedundancy` - LLM identifies overlapping content
- `ConsolidateContent` - LLM merges while preserving unique info

## Enhanced World State

### New Metrics for CorpusState

```java
public class CorpusState {
    // Existing
    private Map<String, NoteMetadata> notes;
    private double meanHealthScore;

    // NEW: Semantic coherence
    private double semanticCoherenceScore;  // 0.0-1.0
    private Map<String, Double> noteCoherence;  // Per-note internal coherence

    // NEW: Chunk size optimization
    private double optimalChunkRatio;  // % notes in 300-800 token range
    private Map<String, Integer> noteTokenCounts;

    // NEW: Connectivity
    private double averagePathLength;  // Average shortest path between notes
    private int orphanCount;
    private Map<String, Set<String>> linkGraph;

    // NEW: Redundancy
    private double redundancyScore;  // % of corpus that's duplicate content
    private Map<String, List<String>> redundantNoteGroups;

    // NEW: Split/merge candidates (from LLM analysis)
    private Set<String> splitCandidates;
    private Map<String, List<SplitPoint>> noteSplitPoints;
    private Map<String, List<String>> mergeCandidates;  // note -> related notes
}
```

### New Metadata in NoteMetadata

```java
public class NoteMetadata {
    // Existing
    private String noteId;
    private String title;
    private String filePath;
    private boolean formatOk;
    private boolean hasEmbedding;

    // NEW: Structure analysis (from LLM)
    private boolean hasMultipleTopics;
    private List<String> detectedTopics;
    private List<SplitPoint> suggestedSplitPoints;
    private double internalCoherence;  // 0.0-1.0

    // NEW: Chunk size
    private int tokenCount;
    private boolean tooSmall;  // < 300 tokens
    private boolean tooLarge;  // > 800 tokens

    // NEW: Merge analysis
    private List<String> semanticallySimilarNotes;  // IDs of similar notes
    private Map<String, Double> similarityScores;  // noteId -> similarity
}

public class SplitPoint {
    private int characterOffset;
    private String beforeHeading;  // Heading before split
    private String afterHeading;   // Heading after split
    private String rationale;      // Why split here
}
```

## Action Implementations

### 1. AnalyzeNoteStructure (Discovery Action)

**Purpose**: LLM analyzes each note to discover split/merge candidates

```java
@Component
public class AnalyzeNoteStructure extends Action<CorpusState> {
    private final OllamaChatService llm;
    private final OrgRoamMcpClient mcp;

    @Override
    public String getName() { return "AnalyzeNoteStructure"; }

    @Override
    public boolean canExecute(CorpusState state) {
        // Can always analyze
        return true;
    }

    @Override
    public Set<Effect<CorpusState>> getEffects() {
        return Set.of(
            new PopulatesSplitCandidates(),
            new PopulatesMergeCandidates(),
            new UpdatesCoherenceScores()
        );
    }

    @Override
    public ActionResult<CorpusState> execute(CorpusState state) {
        for (NoteMetadata note : state.getAllNotes()) {
            // LLM analyzes semantic coherence
            String prompt = String.format(
                "Analyze this org-roam note for semantic coherence:\n\n%s\n\n" +
                "Questions:\n" +
                "1. Does it discuss multiple distinct topics? If so, list them.\n" +
                "2. What is the internal semantic coherence (0.0-1.0)?\n" +
                "3. If it should be split, suggest split points with headings.\n" +
                "4. Token count: %d. Is this optimal (300-800)?\n" +
                "Return JSON: {topics: [], coherence: 0.0, splitPoints: [], optimal: bool}",
                note.getContent(),
                note.getTokenCount()
            );

            StructureAnalysis analysis = llm.analyzeStructure(prompt);

            // Update state based on analysis
            if (analysis.hasMultipleTopics()) {
                state.addSplitCandidate(note.getNoteId());
                state.setSplitPoints(note.getNoteId(), analysis.getSplitPoints());
                note.setHasMultipleTopics(true);
                note.setDetectedTopics(analysis.getTopics());
            }

            note.setInternalCoherence(analysis.getCoherence());

            // Check for merge candidates via semantic search
            if (note.isTooSmall() || analysis.getCoherence() < 0.5) {
                List<SemanticSearchResult> similar = mcp.semanticSearch(
                    note.getContent(), 5
                );

                for (SemanticSearchResult result : similar) {
                    if (result.getScore() > 0.85) {
                        state.addMergeCandidate(
                            note.getNoteId(),
                            result.getNoteId()
                        );
                    }
                }
            }
        }

        state.recalculateSemanticCoherence();
        return ActionResult.success(state);
    }
}
```

### 2. SplitNote (Transformation Action)

**Purpose**: Split multi-topic note into focused fragments

```java
@Component
public class SplitNote extends Action<CorpusState> {
    private final OllamaChatService llm;
    private final OrgFileWriter writer;
    private final PatchWriter patchWriter;

    @Override
    public String getName() { return "SplitNote"; }

    @Override
    public Set<Precondition<CorpusState>> getPreconditions() {
        return Set.of(
            new HasSplitCandidates(),
            new SplitPointsIdentified(),
            new NoteTooLarge(minTokens = 600)  // Only worth splitting if substantial
        );
    }

    @Override
    public Set<Effect<CorpusState>> getEffects() {
        return Set.of(
            new CreatesNewNotes(),           // Adds fragments to corpus
            new InvalidatesEmbeddings(),      // Fragments need embeddings
            new IncreasesCoherence(),         // Each fragment more focused
            new BreaksExistingLinks(),        // Backlinks need updating
            new RequiresLinkSuggestions()     // Fragments might link to each other
        );
    }

    @Override
    public ActionResult<CorpusState> execute(CorpusState state) {
        List<String> processedNotes = new ArrayList<>();

        for (String noteId : state.getSplitCandidates()) {
            NoteMetadata note = state.getNote(noteId);
            List<SplitPoint> splits = state.getSplitPoints(noteId);

            // LLM performs the actual split
            List<NoteFragment> fragments = llm.splitNote(
                note.getContent(),
                splits
            );

            // Create new notes for each fragment
            for (int i = 0; i < fragments.size(); i++) {
                NoteFragment frag = fragments.get(i);
                String newId = UUID.randomUUID().toString();
                String newPath = note.getFilePath().replace(
                    ".org",
                    String.format("-part%d.org", i + 1)
                );

                // Write fragment as new note
                writer.writeNote(newPath, frag.getContent());

                // Add to state
                NoteMetadata fragMeta = NoteMetadata.builder()
                    .noteId(newId)
                    .title(frag.getTitle())
                    .filePath(newPath)
                    .hasEmbedding(false)  // Needs embedding!
                    .tokenCount(frag.getTokenCount())
                    .internalCoherence(1.0)  // Single topic
                    .build();

                state.addNote(fragMeta);
                processedNotes.add(newId);
            }

            // Create proposal to delete original note
            patchWriter.createProposal(
                noteId,
                "DELETE",
                String.format("Split into %d focused notes", fragments.size())
            );

            // Mark original as pending deletion
            state.markForDeletion(noteId);
        }

        state.recalculateSemanticCoherence();

        return ActionResult.success(state)
            .withMessage(String.format("Split %d notes into fragments",
                processedNotes.size()));
    }
}
```

### 3. MergeNotes (Consolidation Action)

**Purpose**: Combine semantically similar or fragmented notes

```java
@Component
public class MergeNotes extends Action<CorpusState> {
    private final OllamaChatService llm;
    private final OrgFileWriter writer;

    @Override
    public String getName() { return "MergeNotes"; }

    @Override
    public Set<Precondition<CorpusState>> getPreconditions() {
        return Set.of(
            new HasMergeCandidates(),
            new HighSemanticSimilarity(threshold = 0.85),
            new CombinedSizeOptimal()  // Merged note won't be too large
        );
    }

    @Override
    public Set<Effect<CorpusState>> getEffects() {
        return Set.of(
            new ReducesNoteCount(),
            new InvalidatesEmbeddings(),   // Merged note needs new embedding
            new IncreasesCoherence(),      // Combined content more complete
            new ConsolidatesLinks(),       // Merge backlinks
            new ReducesRedundancy()        // Eliminates duplicate content
        );
    }

    @Override
    public ActionResult<CorpusState> execute(CorpusState state) {
        Map<String, List<String>> mergeCandidates = state.getMergeCandidates();

        for (Map.Entry<String, List<String>> entry : mergeCandidates.entrySet()) {
            String primaryId = entry.getKey();
            List<String> mergeTargets = entry.getValue();

            NoteMetadata primary = state.getNote(primaryId);
            List<String> contents = new ArrayList<>();
            contents.add(primary.getContent());

            for (String targetId : mergeTargets) {
                contents.add(state.getNote(targetId).getContent());
            }

            // LLM merges content intelligently
            String mergedContent = llm.mergeNotes(
                contents,
                String.format("Merge %d related notes into cohesive whole",
                    contents.size())
            );

            // Update primary note
            writer.writeNote(primary.getFilePath(), mergedContent);
            primary.setHasEmbedding(false);  // Needs re-embedding
            primary.setTokenCount(countTokens(mergedContent));

            // Mark merged notes for deletion
            for (String targetId : mergeTargets) {
                state.markForDeletion(targetId);
            }
        }

        state.recalculateRedundancy();

        return ActionResult.success(state);
    }
}
```

## True GOAP Planner Implementation

### Backward Chaining Algorithm

```java
@Component
public class TrueGOAPPlanner {
    private final List<Action<CorpusState>> allActions;
    private final List<Goal<CorpusState>> topLevelGoals;

    /**
     * Generate plan using backward chaining from goals
     */
    public ActionPlan generatePlan(CorpusState state) {
        List<PlannedAction> plan = new ArrayList<>();

        // 1. Find unsatisfied goals
        List<Goal<CorpusState>> unsatisfiedGoals = findUnsatisfiedGoals(state);

        if (unsatisfiedGoals.isEmpty()) {
            return ActionPlan.empty("All goals satisfied");
        }

        // 2. Prioritize goals by importance
        unsatisfiedGoals.sort(Comparator.comparing(Goal::getPriority).reversed());

        // 3. For each goal, backward chain to find action sequence
        for (Goal<CorpusState> goal : unsatisfiedGoals) {
            List<Action<CorpusState>> actionSequence = backwardChain(
                goal,
                state,
                new HashSet<>()  // Prevent cycles
            );

            if (actionSequence != null) {
                plan.addAll(actionSequence.stream()
                    .map(a -> toPlannedAction(a, state))
                    .toList());
            }
        }

        // 4. Remove duplicates, optimize order
        plan = optimizePlan(plan, state);

        return new ActionPlan(plan);
    }

    /**
     * Backward chain from goal to find action sequence
     */
    private List<Action<CorpusState>> backwardChain(
        Goal<CorpusState> goal,
        CorpusState state,
        Set<Goal<CorpusState>> visited
    ) {
        if (goal.evaluate(state) == GoalStatus.SATISFIED) {
            return List.of();  // Goal already satisfied
        }

        if (visited.contains(goal)) {
            return null;  // Cycle detected
        }

        visited.add(goal);

        // Find actions whose effects satisfy this goal
        List<Action<CorpusState>> candidateActions = findActionsSatisfying(goal);

        for (Action<CorpusState> action : candidateActions) {
            // Check if action's preconditions can be satisfied
            Set<Precondition<CorpusState>> preconditions = action.getPreconditions();

            if (allPreconditionsMet(preconditions, state)) {
                // This action can execute now!
                return List.of(action);
            }

            // Preconditions not met - try to satisfy them recursively
            List<Action<CorpusState>> subPlan = satisfyPreconditions(
                preconditions,
                state,
                visited
            );

            if (subPlan != null) {
                // Found a plan! Prepend sub-actions, append this action
                List<Action<CorpusState>> fullPlan = new ArrayList<>(subPlan);
                fullPlan.add(action);
                return fullPlan;
            }
        }

        // No plan found for this goal
        return null;
    }

    /**
     * Find actions whose effects contribute to satisfying goal
     */
    private List<Action<CorpusState>> findActionsSatisfying(Goal<CorpusState> goal) {
        return allActions.stream()
            .filter(action -> actionSatisfiesGoal(action, goal))
            .sorted(Comparator.comparing(a -> a.getCost(currentState)))
            .toList();
    }

    /**
     * Check if action's effects help satisfy goal
     */
    private boolean actionSatisfiesGoal(Action<CorpusState> action, Goal<CorpusState> goal) {
        // This is domain-specific logic
        // Example: SplitNote has effect "IncreasesCoherence"
        //          which satisfies "OptimizeSemanticCoherence" goal

        Set<Effect<CorpusState>> effects = action.getEffects();
        Set<Effect<CorpusState>> goalRequirements = goal.getRequiredEffects();

        return effects.stream().anyMatch(goalRequirements::contains);
    }
}
```

## Implementation Phases

### Phase 1: Enhance Imperative (Quick Win)
**Timeline**: 1-2 weeks

**Add actions without true GOAP**:
1. `AnalyzeNoteStructure` - LLM structure analysis
2. `SplitNote` - Split multi-topic notes
3. `MergeNotes` - Combine similar notes

**Update GOAPPlanner**:
```java
List<Action<CorpusState>> availableActions = List.of(
    analyzeStructureAction,      // First - discover candidates
    normalizeFormattingAction,
    computeEmbeddingsAction,
    splitNoteAction,             // After analysis
    mergeNotesAction,
    suggestLinksAction           // Last - after structure is optimal
);
```

**Pros**: Fast implementation, proven pattern
**Cons**: Still can't discover complex sequences

### Phase 2: Add Preconditions/Effects (Foundation)
**Timeline**: 2-3 weeks

**Enhance Action interface**:
```java
public interface Action<S> {
    String getName();
    boolean canExecute(S state);
    ActionResult<S> execute(S state);
    double getCost(S state);

    // NEW: Declarative planning support
    Set<Precondition<S>> getPreconditions();
    Set<Effect<S>> getEffects();
}
```

**Create effect/precondition system**:
- `CreatesNewNotes`, `InvalidatesEmbeddings`, etc.
- Formalize relationships between actions

**Pros**: Enables true GOAP, better documentation
**Cons**: Requires refactoring all actions

### Phase 3: True GOAP Planner (Full Implementation)
**Timeline**: 3-4 weeks

**Implement backward chaining**:
- `TrueGOAPPlanner` class
- `backwardChain()` algorithm
- Goal decomposition
- Action discovery

**Add goal hierarchy**:
- `OptimizeKnowledgeBase` top goal
- `OptimizeSemanticCoherence`, `OptimizeChunkSize`, etc.

**Pros**: Full declarative planning, discovers novel sequences
**Cons**: Complex, harder to debug, more testing required

## Recommended Approach

### Start with Phase 1
**Reason**: Prove the concept with imperative approach first

**Deliverables**:
1. LLM-based structure analysis working
2. Split/merge actions implemented
3. Real results on your org-roam corpus

**Then evaluate**: Did imperative work well enough? Or do you need true GOAP?

### Move to Phase 2/3 When:
- Action sequences get too complex to hardcode
- You want planner to discover non-obvious optimizations
- Multiple competing goals need balancing
- System needs to adapt to LLM discoveries dynamically

## Testing Strategy

### Phase 1 Tests
```java
@Test
void testSplitMultiTopicNote() {
    // Note with Docker + Kubernetes content
    NoteMetadata note = createNote("docker-k8s.org", 1200 tokens);

    // LLM should detect two topics
    StructureAnalysis analysis = analyzeStructure(note);
    assertTrue(analysis.hasMultipleTopics());
    assertEquals(2, analysis.getTopics().size());

    // Split should create two focused notes
    ActionResult result = splitNoteAction.execute(state);
    assertEquals(2, result.getNewNotes().size());
    assertTrue(result.getNewNotes().get(0).getInternalCoherence() > 0.9);
}
```

### Phase 3 Tests
```java
@Test
void testBackwardChainingDiscoversSequence() {
    // Setup: Large multi-topic note, no embeddings
    CorpusState state = new CorpusState();
    state.addNote(createLargeMultiTopicNote());

    // Goal: Optimize semantic coherence
    Goal<CorpusState> goal = new OptimizeSemanticCoherence();
    assertTrue(goal.evaluate(state) == GoalStatus.UNSATISFIED);

    // Planner should discover: Analyze → Split → ComputeEmbeddings
    ActionPlan plan = planner.generatePlan(state);

    List<String> actionNames = plan.getActions().stream()
        .map(PlannedAction::getActionName)
        .toList();

    assertEquals(List.of(
        "AnalyzeNoteStructure",
        "SplitNote",
        "ComputeEmbeddings",
        "SuggestLinks"  // Might suggest links between fragments
    ), actionNames);
}
```

## Conclusion

**For your vision of knowledge base optimization, true GOAP is the right architecture.**

**Recommended path**:
1. **Now**: Implement Phase 1 (imperative split/merge)
2. **Validate**: Run on your corpus, see if it improves semantic search quality
3. **Then decide**: If action sequences get complex, move to Phase 2/3

The imperative approach will teach you what action sequences work well, which will inform the declarative implementation later.
