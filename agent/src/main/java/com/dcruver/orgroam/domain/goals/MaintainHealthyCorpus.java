package com.dcruver.orgroam.domain.goals;

import com.dcruver.orgroam.domain.CorpusState;
import com.embabel.agent.core.goal.Goal;
import com.embabel.agent.core.goal.GoalStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Top-level goal: Maintain a healthy corpus with target health score.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MaintainHealthyCorpus implements Goal<CorpusState> {

    @Value("${gardener.target-health:90}")
    private int targetHealth;

    @Override
    public String getName() {
        return "MaintainHealthyCorpus";
    }

    @Override
    public String getDescription() {
        return String.format("Maintain corpus health at or above %d%%", targetHealth);
    }

    @Override
    public GoalStatus evaluate(CorpusState state) {
        if (state == null || state.getNotes() == null || state.getNotes().isEmpty()) {
            log.debug("Corpus is empty, goal not applicable");
            return GoalStatus.NOT_APPLICABLE;
        }

        double currentHealth = state.getMeanHealthScore();
        log.debug("Current corpus health: {}, target: {}", currentHealth, targetHealth);

        if (currentHealth >= targetHealth) {
            return GoalStatus.SATISFIED;
        } else {
            return GoalStatus.UNSATISFIED;
        }
    }

    @Override
    public int getPriority() {
        return 100; // Highest priority
    }

    public int getTargetHealth() {
        return targetHealth;
    }
}
