package com.embabel.agent.core.action;

import lombok.Builder;
import lombok.Data;

/**
 * STUB: Temporary class until actual Embabel package structure is determined.
 * Represents the result of executing a GOAP action.
 *
 * @param <T> The world state type
 */
@Data
@Builder
public class ActionResult<T> {
    private final boolean success;
    private final T resultingState;
    private final String message;
}
