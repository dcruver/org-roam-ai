package com.dcruver.orgroam.domain.goals;

import com.dcruver.orgroam.domain.CorpusState;
import com.embabel.agent.core.goal.Goal;
import com.embabel.agent.core.goal.GoalStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Subgoal: Reduce number of orphan notes (notes with no links).
 */
@Component
@Slf4j
public class ReduceOrphans implements Goal<CorpusState> {

    private static final int ACCEPTABLE_ORPHAN_THRESHOLD = 5; // 5% of notes can be orphans

    @Override
    public String getName() {
        return "ReduceOrphans";
    }

    @Override
    public String getDescription() {
        return "Reduce number of orphan notes";
    }

    @Override
    public GoalStatus evaluate(CorpusState state) {
        if (state == null || state.getTotalNotes() == 0) {
            return GoalStatus.NOT_APPLICABLE;
        }

        double orphanPercentage = (state.getOrphanNotes() * 100.0) / state.getTotalNotes();
        log.debug("Orphan notes: {} ({}%)", state.getOrphanNotes(), orphanPercentage);

        return orphanPercentage <= ACCEPTABLE_ORPHAN_THRESHOLD
            ? GoalStatus.SATISFIED
            : GoalStatus.UNSATISFIED;
    }

    @Override
    public int getPriority() {
        return 60;
    }
}
