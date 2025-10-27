package com.dcruver.orgroam.domain.goals;

import com.dcruver.orgroam.domain.CorpusState;
import com.embabel.agent.core.goal.Goal;
import com.embabel.agent.core.goal.GoalStatus;
import org.springframework.stereotype.Component;

/**
 * GOAP Goal: Establish hierarchical organization for implicit categories.
 *
 * This goal is satisfied when:
 * 1. Fragmentation has been reduced (prerequisite)
 * 2. Implicit categories have been discovered
 * 3. Significant categories have MOCs or hub notes
 */
@Component
public class EstablishHierarchyGoal implements Goal<CorpusState> {

    private static final int MIN_CATEGORY_SIZE = 5;  // Min notes to warrant a MOC

    @Override
    public String getName() {
        return "EstablishHierarchy";
    }

    @Override
    public String getDescription() {
        return "Organize notes into hierarchical structures with MOCs for implicit categories";
    }

    @Override
    public GoalStatus evaluate(CorpusState state) {
        // Prerequisite: fragmentation must be addressed first
        if (state.hasFragmentation()) {
            return GoalStatus.BLOCKED;  // Can't organize if still fragmented
        }

        // Check if category discovery has been done
        if (state.getImplicitCategories() == null || state.getImplicitCategories().isEmpty()) {
            return GoalStatus.UNSATISFIED;  // Need to discover categories first
        }

        // Check if significant categories lack MOCs
        long categoriesNeedingMOCs = state.getImplicitCategories().values().stream()
            .filter(category -> category.size() >= MIN_CATEGORY_SIZE)
            .filter(category -> !category.isHasMOC())
            .count();

        if (categoriesNeedingMOCs > 0) {
            return GoalStatus.UNSATISFIED;
        }

        return GoalStatus.SATISFIED;
    }

    @Override
    public int getPriority() {
        return 70;  // Lower than fragmentation - do this after connecting orphans
    }
}
