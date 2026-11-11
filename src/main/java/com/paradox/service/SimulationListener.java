package com.paradox.service;

import com.paradox.model.SimulationEvent;

/**
 * Anything that wants to react to simulation changes (controllers, the graph
 * view, the dashboard) registers itself here. Every successful service
 * operation fires exactly one event after the database transaction committed.
 */
@FunctionalInterface
public interface SimulationListener {

    void onSimulationChanged(SimulationEvent event);
}
