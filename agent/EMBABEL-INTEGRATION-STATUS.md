# Embabel Integration Status

## Current Status

The project **builds successfully** and **all tests pass**, but the Embabel GOAP framework integration is currently using **stub interfaces** instead of the actual Embabel classes.

## What's Working

- ✅ Maven dependency for `embabel-agent-starter:0.1.2-SNAPSHOT` is resolved
- ✅ Project compiles successfully
- ✅ All unit tests pass
- ✅ Spring Boot application structure in place
- ✅ Spring Shell commands framework ready
- ✅ Ollama integration (Spring AI) working
- ✅ Core domain models (Goals, Actions, State) implemented

## Stub Interfaces Created

The following stub interfaces were created in `/src/main/java/com/embabel/agent/core/` to allow compilation:

- `com.embabel.agent.core.goal.Goal<T>`
- `com.embabel.agent.core.goal.GoalStatus`
- `com.embabel.agent.core.action.Action<T>`
- `com.embabel.agent.core.action.ActionResult<T>`

These match the expected API contract but are not the actual Embabel classes.

## What Needs To Be Done

### 1. Determine Correct Embabel Package Structure

The `embabel-agent-starter:0.1.2-SNAPSHOT` dependency downloads successfully, but the package structure differs from what was expected. You need to:

1. Examine the actual JAR to see what packages/classes it provides:
   ```bash
   jar -tf ~/.m2/repository/com/embabel/agent/embabel-agent-starter/0.1.2-SNAPSHOT/embabel-agent-starter-*.jar | grep -E "\.class$" | head -20
   ```

2. Or check the Embabel documentation at https://docs.embabel.com

### 2. Replace Stub Interfaces

Once you know the correct package structure:

1. Delete the stub files in `src/main/java/com/embabel/agent/core/`
2. Update imports in:
   - `src/main/java/com/dcruver/orgroam/domain/goals/*.java`
   - `src/main/java/com/dcruver/orgroam/domain/actions/*.java`
3. Uncomment `@EnableAgentShell` in `OrgRoamGardenerApplication.java`

### 3. Wire Up GOAP Planner

The actual Embabel planner needs to be integrated to:
- Evaluate goals against CorpusState
- Generate action plans
- Execute actions in sequence
- Reassess state after each action

This wiring should go in the shell commands (`GardenerShellCommands.java`), particularly:
- `audit()` - Run planner to generate plan
- `execute()` - Execute the generated plan
- `apply safe()` - Filter and execute only safe actions

## Alternative: Remove Embabel Dependency

If the Embabel integration proves too complex for now, you could:

1. Remove the `embabel-agent-starter` dependency from pom.xml
2. Keep the stub interfaces as your own simple GOAP implementation
3. Implement a basic planner that evaluates goals and selects actions
4. Add Embabel integration later when the package structure is better documented

## Current Files Structure

```
src/main/java/
├── com/embabel/agent/core/          # STUB interfaces (temporary)
│   ├── action/
│   │   ├── Action.java
│   │   └── ActionResult.java
│   └── goal/
│       ├── Goal.java
│       └── GoalStatus.java
└── com/dcruver/orgroam/
    ├── domain/
    │   ├── actions/                  # Implements Action<CorpusState>
    │   │   ├── ComputeEmbeddingsAction.java
    │   │   ├── NormalizeFormattingAction.java
    │   │   └── SuggestLinksAction.java
    │   └── goals/                    # Implements Goal<CorpusState>
    │       ├── MaintainHealthyCorpus.java
    │       ├── EnsureEmbeddingsFresh.java
    │       ├── EnforceFormattingPolicy.java
    │       └── ReduceOrphans.java
    └── app/
        └── GardenerShellCommands.java  # TODO: Wire up planner
```

## Testing Without Full Embabel Integration

The application can still be tested and developed:

```bash
# Build
./mvnw clean package

# Run (Spring Shell will work, but GOAP planning won't be active yet)
./mvnw spring-boot:run

# The shell commands are placeholders that need planner integration
```

##Next Steps (Recommended Order)

1. Investigate actual Embabel package structure
2. Decide: integrate actual Embabel OR keep simple custom GOAP
3. If integrating Embabel: replace stubs and wire up planner
4. If custom GOAP: implement basic planner logic in shell commands
5. Test with real org-roam notes
6. Deploy to Proxmox and configure Ollama integration
