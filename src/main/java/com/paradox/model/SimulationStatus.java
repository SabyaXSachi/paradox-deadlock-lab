package com.paradox.model;

/** Lifecycle status of a simulation. */
public enum SimulationStatus {
    CREATED,
    RUNNING,
    DEADLOCKED,
    RECOVERED,
    COMPLETED
}
