package com.paradox.model;

/** Lifecycle of a resource request inside the allocation engine. */
public enum RequestStatus {
    PENDING,
    GRANTED,
    CANCELLED
}
