package com.paradox.model;

/**
 * States a simulated process can be in. States change automatically when
 * simulation events occur (request granted, process waiting, terminated...).
 */
public enum ProcessState {
    READY,
    RUNNING,
    WAITING,
    BLOCKED,
    TERMINATED
}
