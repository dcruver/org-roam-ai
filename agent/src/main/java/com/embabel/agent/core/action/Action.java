package com.embabel.agent.core.action;

/**
 * STUB: Temporary interface until actual Embabel package structure is determined.
 * Represents a GOAP action that can be executed to change world state.
 *
 * @param <T> The world state type
 */
public interface Action<T> {
    String getName();
    String getDescription();
    boolean canExecute(T state);
    ActionResult<T> execute(T state);
    double getCost(T state);
    T apply(T state);
}
