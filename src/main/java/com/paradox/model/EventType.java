package com.paradox.model;

/** Kinds of entries in the append-only simulation event log. */
public enum EventType {
    SIMULATION_CREATED,
    SIMULATION_DELETED,
    PROCESS_CREATED,
    PROCESS_TERMINATED,
    RESOURCE_CREATED,
    RESOURCE_DELETED,
    ALLOCATION,
    REQUEST,
    GRANT,
    RELEASE,
    DETECTION,
    BANKERS_CHECK,
    RECOVERY,
    SNAPSHOT,
    STATE_CHANGE
}
