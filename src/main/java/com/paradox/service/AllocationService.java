package com.paradox.service;

import com.paradox.database.Db;
import com.paradox.model.Allocation;
import com.paradox.model.EventType;
import com.paradox.model.ProcessState;
import com.paradox.model.RequestStatus;
import com.paradox.model.Resource;
import com.paradox.model.ResourceRequest;
import com.paradox.model.SimProcess;
import com.paradox.util.JsonUtil;
import com.paradox.util.ValidationException;

import java.sql.Connection;
import java.util.List;

/**
 * The resource allocation engine: grants, requests, releases and cancels.
 *
 * Ordering for every operation:
 *   1. validate against the engine state (throws ValidationException),
 *   2. persist the new state in one database transaction (CHECK constraints
 *      re-verify the instance accounting),
 *   3. mirror the committed change into the engine,
 *   4. notify all listeners.
 *
 * If step 2 fails, the engine was never touched, so memory and database stay
 * consistent.
 */
public class AllocationService {

    private final SimulationEngine engine;
    private final SimulationService simulationService;

    public AllocationService(SimulationEngine engine, SimulationService simulationService) {
        this.engine = engine;
        this.simulationService = simulationService;
    }

    /**
     * Direct allocation (setup of initial holdings). Fails when the resource
     * does not have enough available instances.
     */
    public void allocate(long processId, long resourceId, int instances) {
        SimProcess process = requireRunnableProcess(processId);
        Resource resource = requireResource(resourceId);
        requirePositiveInstances(instances);

        if (instances > resource.getAvailableInstances()) {
            throw new ValidationException("Cannot allocate " + instances + " instance(s) of "
                    + resource.getName() + ": only " + resource.getAvailableInstances()
                    + " available. Use a request instead to make the process wait.");
        }
        int newHeld = engine.allocationOf(processId, resourceId) + instances;

        Db.inTransaction(conn -> {
            persistGrant(conn, simulationService, processId, resourceId, newHeld);
            simulationService.processRepository().updateState(conn, processId,
                    ProcessState.RUNNING);
            simulationService.eventRepository().insert(conn,
                    simulationService.newEvent(EventType.ALLOCATION,
                            process.getName() + " allocated " + instances + " x "
                                    + resource.getName(),
                            grantPayload(process, resource, instances)));
            return null;
        });

        engine.applyAllocation(processId, resourceId, newHeld);
        engine.setProcessState(processId, ProcessState.RUNNING);
        engine.bumpStep();
        engine.fireChange(simulationService.newEvent(EventType.ALLOCATION,
                process.getName() + " holds " + newHeld + " x " + resource.getName()));
    }

    /**
     * A process asks for additional instances. Granted immediately when
     * enough instances are free; otherwise the request stays PENDING and the
     * process enters the WAITING state - the precondition for deadlock.
     */
    public void request(long processId, long resourceId, int instances) {
        SimProcess process = requireRunnableProcess(processId);
        Resource resource = requireResource(resourceId);
        requirePositiveInstances(instances);

        if (instances > resource.getTotalInstances()) {
            throw new ValidationException("Request of " + instances + " x "
                    + resource.getName() + " can never be granted: the resource type only has "
                    + resource.getTotalInstances() + " instance(s) in total");
        }

        if (instances <= resource.getAvailableInstances()) {
            int newHeld = engine.allocationOf(processId, resourceId) + instances;

            ResourceRequest granted = new ResourceRequest(engine.getSimulation().getId(),
                    processId, resourceId, instances);
            granted.setStatus(RequestStatus.GRANTED);
            Db.inTransaction(conn -> {
                persistGrant(conn, simulationService, processId, resourceId, newHeld);
                simulationService.requestRepository().insert(conn, granted);
                simulationService.processRepository().updateState(conn, processId,
                        ProcessState.RUNNING);
                simulationService.eventRepository().insert(conn,
                        simulationService.newEvent(EventType.GRANT,
                                "Request granted: " + process.getName() + " receives "
                                        + instances + " x " + resource.getName(),
                                grantPayload(process, resource, instances)));
                return null;
            });
            engine.applyAllocation(processId, resourceId, newHeld);
            engine.addRequest(granted);
            engine.setProcessState(processId, ProcessState.RUNNING);
            engine.bumpStep();
            engine.fireChange(simulationService.newEvent(EventType.GRANT,
                    process.getName() + " granted " + instances + " x " + resource.getName()));
        } else {
            ResourceRequest pending = new ResourceRequest(engine.getSimulation().getId(),
                    processId, resourceId, instances);
            pending.setStatus(RequestStatus.PENDING);
            Db.inTransaction(conn -> {
                simulationService.requestRepository().insert(conn, pending);
                simulationService.processRepository().updateState(conn, processId,
                        ProcessState.WAITING);
                simulationService.eventRepository().insert(conn,
                        simulationService.newEvent(EventType.REQUEST,
                                process.getName() + " waits for " + instances + " x "
                                        + resource.getName() + " (only "
                                        + resource.getAvailableInstances() + " available)",
                                grantPayload(process, resource, instances)));
                return null;
            });
            engine.addRequest(pending);
            engine.setProcessState(processId, ProcessState.WAITING);
            engine.bumpStep();
            engine.fireChange(simulationService.newEvent(EventType.REQUEST,
                    process.getName() + " is waiting for " + resource.getName()));
        }
    }

    /** Returns instances to the free pool; may wake up waiting processes. */
    public void release(long processId, long resourceId, int instances) {
        SimProcess process = requireRunnableProcess(processId);
        Resource resource = requireResource(resourceId);
        requirePositiveInstances(instances);

        int held = engine.allocationOf(processId, resourceId);
        if (held < instances) {
            throw new ValidationException(process.getName() + " holds only " + held
                    + " instance(s) of " + resource.getName());
        }

        Db.inTransaction(conn -> {
            persistRelease(conn, simulationService, processId, resourceId, instances);
            simulationService.eventRepository().insert(conn,
                    simulationService.newEvent(EventType.RELEASE,
                            process.getName() + " released " + instances + " x "
                                    + resource.getName(),
                            grantPayload(process, resource, instances)));
            return null;
        });

        engine.applyRelease(processId, resourceId, instances);
        engine.bumpStep();
        engine.fireChange(simulationService.newEvent(EventType.RELEASE,
                process.getName() + " released " + instances + " x " + resource.getName()));

        // Freed instances may satisfy waiting processes (OS-style wakeup).
        tryGrantPending();
    }

    /** Cancels a pending request; the process becomes READY again when idle. */
    public void cancelRequest(long requestId) {
        ResourceRequest request = engine.getAllRequests().stream()
                .filter(r -> r.getId() != null && r.getId() == requestId)
                .findFirst()
                .orElseThrow(() -> new ValidationException("Unknown request " + requestId));
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new ValidationException("Only pending requests can be cancelled");
        }
        Db.inTransaction(conn -> {
            simulationService.requestRepository().updateStatus(conn, requestId,
                    RequestStatus.CANCELLED);
            return null;
        });
        engine.updateRequestStatus(requestId, RequestStatus.CANCELLED);
        restoreReadyIfIdle(request.getProcessId());
        engine.bumpStep();
        engine.fireChange(simulationService.newEvent(EventType.REQUEST,
                "Request cancelled (" + describe(request) + ")"));
    }

    /**
     * Grants every PENDING request that the current availability can satisfy,
     * in FIFO order. Called after releases and after resource preemption.
     */
    public void tryGrantPending() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (ResourceRequest pending : engine.getPendingRequests()) {
                Resource resource = engine.findResource(pending.getResourceId())
                        .orElse(null);
                if (resource == null || pending.getInstances() > resource.getAvailableInstances()) {
                    continue;
                }
                grantExisting(pending);
                changed = true;
                break; // list changed; restart the scan
            }
        }
    }

    private void grantExisting(ResourceRequest pending) {
        Resource resource = engine.findResource(pending.getResourceId())
                .orElseThrow(() -> new ValidationException("Unknown resource"));
        SimProcess process = engine.findProcess(pending.getProcessId())
                .orElseThrow(() -> new ValidationException("Unknown process"));
        int newHeld = engine.allocationOf(pending.getProcessId(), pending.getResourceId())
                + pending.getInstances();

        Db.inTransaction(conn -> {
            persistGrant(conn, simulationService, pending.getProcessId(),
                    pending.getResourceId(), newHeld);
            simulationService.requestRepository().updateStatus(conn, pending.getId(),
                    RequestStatus.GRANTED);
            simulationService.processRepository().updateState(conn, pending.getProcessId(),
                    ProcessState.RUNNING);
            simulationService.eventRepository().insert(conn,
                    simulationService.newEvent(EventType.GRANT,
                            "Waiting request granted: " + process.getName() + " receives "
                                    + pending.getInstances() + " x " + resource.getName(),
                            grantPayload(process, resource, pending.getInstances())));
            return null;
        });
        engine.applyAllocation(pending.getProcessId(), pending.getResourceId(), newHeld);
        engine.updateRequestStatus(pending.getId(), RequestStatus.GRANTED);
        engine.setProcessState(pending.getProcessId(), ProcessState.RUNNING);
        engine.bumpStep();
        engine.fireChange(simulationService.newEvent(EventType.GRANT,
                process.getName() + " granted " + pending.getInstances() + " x "
                        + resource.getName() + " after waiting"));
    }

    private void restoreReadyIfIdle(long processId) {
        boolean stillWaiting = engine.getPendingRequests().stream()
                .anyMatch(r -> r.getProcessId() == processId);
        if (!stillWaiting) {
            engine.findProcess(processId).ifPresent(p -> {
                if (p.getState() == ProcessState.WAITING) {
                    engine.setProcessState(processId, ProcessState.READY);
                }
            });
        }
    }

    // ---------- shared transaction-side persistence helpers ----------

    /**
     * Writes the new ownership of one (process, resource) pair and the new
     * resource accounting. Called inside a transaction; the database CHECK
     * constraint is the final guard for the invariant.
     */
    static void persistGrant(Connection conn, SimulationService simulationService,
                             long processId, long resourceId, int newHeldTotal) {
        Resource resource = simulationService.engine().findResource(resourceId)
                .orElseThrow(() -> new ValidationException("Unknown resource"));
        int heldBefore = simulationService.engine().allocationOf(processId, resourceId);
        int delta = newHeldTotal - heldBefore;

        simulationService.resourceRepository().updateCounts(conn, resourceId,
                resource.getTotalInstances(),
                resource.getAvailableInstances() - delta,
                resource.getAllocatedInstances() + delta);
        simulationService.allocationRepository().setInstances(conn,
                resource.getSimulationId(), processId, resourceId, newHeldTotal);
    }

    /** Transaction-side counterpart: returns instances to the free pool. */
    static void persistRelease(Connection conn, SimulationService simulationService,
                               long processId, long resourceId, int instances) {
        Resource resource = simulationService.engine().findResource(resourceId)
                .orElseThrow(() -> new ValidationException("Unknown resource"));
        int heldBefore = simulationService.engine().allocationOf(processId, resourceId);
        int newHeldTotal = heldBefore - instances;

        simulationService.resourceRepository().updateCounts(conn, resourceId,
                resource.getTotalInstances(),
                resource.getAvailableInstances() + instances,
                resource.getAllocatedInstances() - instances);
        if (newHeldTotal == 0) {
            simulationService.allocationRepository().deletePair(conn, processId, resourceId);
        } else {
            simulationService.allocationRepository().setInstances(conn,
                    resource.getSimulationId(), processId, resourceId, newHeldTotal);
        }
    }

    // ---------- small helpers ----------

    private SimProcess requireRunnableProcess(long processId) {
        if (!engine.hasSimulation()) {
            throw new ValidationException("Create or open a simulation first");
        }
        SimProcess process = engine.findProcess(processId)
                .orElseThrow(() -> new ValidationException("Unknown process " + processId));
        if (process.getState() == ProcessState.TERMINATED) {
            throw new ValidationException("Process " + process.getName()
                    + " is terminated");
        }
        return process;
    }

    private Resource requireResource(long resourceId) {
        return engine.findResource(resourceId)
                .orElseThrow(() -> new ValidationException("Unknown resource " + resourceId));
    }

    private void requirePositiveInstances(int instances) {
        if (instances <= 0) {
            throw new ValidationException("Instance count must be at least 1");
        }
    }

    private String grantPayload(SimProcess process, Resource resource, int instances) {
        return "{\"process\":" + JsonUtil.quote(process.getName())
                + ",\"resource\":" + JsonUtil.quote(resource.getName())
                + ",\"instances\":" + instances + "}";
    }

    private String describe(ResourceRequest request) {
        String processName = engine.findProcess(request.getProcessId())
                .map(SimProcess::getName).orElse("?");
        String resourceName = engine.findResource(request.getResourceId())
                .map(Resource::getName).orElse("?");
        return processName + " wanted " + request.getInstances() + " x " + resourceName;
    }

    public List<Allocation> getAllocations() {
        return engine.getAllocations();
    }
}
