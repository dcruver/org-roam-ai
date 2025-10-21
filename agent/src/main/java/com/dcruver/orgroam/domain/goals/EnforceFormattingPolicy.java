package com.dcruver.orgroam.domain.goals;

import com.dcruver.orgroam.domain.CorpusState;
import com.embabel.agent.core.goal.Goal;
import com.embabel.agent.core.goal.GoalStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Subgoal: Ensure all notes follow formatting policy.
 */
@Component
@Slf4j
public class EnforceFormattingPolicy implements Goal<CorpusState> {

    @Override
    public String getName() {
        return "EnforceFormattingPolicy";
    }

    @Override
    public String getDescription() {
        return "Ensure all notes follow formatting policy";
    }

    @Override
    public GoalStatus evaluate(CorpusState state) {
        if (state == null || state.getTotalNotes() == 0) {
            return GoalStatus.NOT_APPLICABLE;
        }

        int formatIssues = state.getNotesWithFormatIssues();
        log.debug("Notes with format issues: {}", formatIssues);

        return formatIssues == 0 ? GoalStatus.SATISFIED : GoalStatus.UNSATISFIED;
    }

    @Override
    public int getPriority() {
        return 70;
    }
}
