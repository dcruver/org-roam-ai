package com.embabel.agent.core.goal;

/**
 * STUB: Temporary interface until actual Embabel package structure is determined.
 * This represents a GOAP goal that can be satisfied or unsatisfied.
 *
 * @param <T> The world state type
 */
public interface Goal<T> {
    String getName();
    String getDescription();
    GoalStatus evaluate(T state);
    int getPriority();
}
