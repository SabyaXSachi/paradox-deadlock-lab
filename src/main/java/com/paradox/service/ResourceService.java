package com.paradox.service;

import com.paradox.database.Db;
import com.paradox.model.EventType;
import com.paradox.model.Resource;
import com.paradox.util.ValidationException;

/**
 * Creates and removes resource types. The instance accounting invariant
 * (available + allocated == total) is established at creation and enforced
 * on every later update by the engine and the database CHECK constraint.
 */
public class ResourceService {

    private final SimulationEngine engine;
    private final SimulationService simulationService;

    public ResourceService(SimulationEngine engine, SimulationService simulationService) {
        this.engine = engine;
        this.simulationService = simulationService;
    }

    public Resource addResource(String name, int totalInstances) {
        requireOpenSimulation();
        if (name == null || name.isBlank()) {
            throw new ValidationException("Resource name must not be empty");
        }
        if (name.length() > 20) {
            throw new ValidationException("Resource name is limited to 20 characters");
        }
        if (engine.hasResourceName(name)) {
            throw new ValidationException("A resource named " + name + " already exists");
        }
        if (totalInstances <= 0) {
            throw new ValidationException("A resource type needs at least one instance");
        }

        Resource resource = new Resource(engine.getSimulation().getId(), name, totalInstances);
        Db.inTransaction(conn -> {
            simulationService.resourceRepository().insert(conn, resource);
            simulationService.eventRepository().insert(conn,
                    simulationService.newEvent(EventType.RESOURCE_CREATED,
                            "Resource " + name + " created with " + totalInstances
                                    + " instance(s)"));
            return null;
        });
        engine.addResource(resource);
        engine.fireChange(simulationService.newEvent(EventType.RESOURCE_CREATED,
                "Resource " + name + " created (" + totalInstances + " instances)"));
        return resource;
    }

    /**
     * Deletes a resource type only when it is safe: no process may hold
     * instances and no request may target it. The database foreign keys
     * (ON DELETE RESTRICT) back this rule up.
     */
    public void deleteResource(long resourceId) {
        requireOpenSimulation();
        Resource resource = engine.findResource(resourceId)
                .orElseThrow(() -> new ValidationException("Unknown resource " + resourceId));

        boolean allocated = engine.getAllocations().stream()
                .anyMatch(a -> a.getResourceId() == resourceId);
        boolean requested = engine.getPendingRequests().stream()
                .anyMatch(r -> r.getResourceId() == resourceId);
        if (allocated || requested) {
            throw new ValidationException("Resource " + resource.getName()
                    + " cannot be deleted while instances are allocated or requested");
        }

        Db.inTransaction(conn -> {
            simulationService.resourceRepository().delete(conn, resourceId);
            simulationService.eventRepository().insert(conn,
                    simulationService.newEvent(EventType.RESOURCE_DELETED,
                            "Resource " + resource.getName() + " deleted"));
            return null;
        });
        engine.removeResource(resourceId);
        engine.fireChange(simulationService.newEvent(EventType.RESOURCE_DELETED,
                "Resource " + resource.getName() + " deleted"));
    }

    private void requireOpenSimulation() {
        if (!engine.hasSimulation()) {
            throw new ValidationException("Create or open a simulation first");
        }
    }
}
