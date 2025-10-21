package com.dcruver.orgroam.domain.goals;

import com.dcruver.orgroam.domain.CorpusState;
import com.embabel.agent.core.goal.Goal;
import com.embabel.agent.core.goal.GoalStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Subgoal: Ensure all notes have fresh embeddings.
 */
@Component
@Slf4j
public class EnsureEmbeddingsFresh implements Goal<CorpusState> {

    @Override
    public String getName() {
        return "EnsureEmbeddingsFresh";
    }

    @Override
    public String getDescription() {
        return "Ensure all notes have fresh embeddings";
    }

    @Override
    public GoalStatus evaluate(CorpusState state) {
        if (state == null || state.getTotalNotes() == 0) {
            return GoalStatus.NOT_APPLICABLE;
        }

        int needingEmbeddings = state.getNotesNeedingEmbeddings();
        log.debug("Notes needing embeddings: {}", needingEmbeddings);

        return needingEmbeddings == 0 ? GoalStatus.SATISFIED : GoalStatus.UNSATISFIED;
    }

    @Override
    public int getPriority() {
        return 80;
    }
}
