package com.paradox.service;

import com.paradox.model.Allocation;
import com.paradox.model.ProcessState;
import com.paradox.model.RequestStatus;
import com.paradox.model.Resource;
import com.paradox.model.ResourceRequest;
import com.paradox.model.SimProcess;
import com.paradox.model.Simulation;
import com.paradox.model.SimulationEvent;
import com.paradox.util.ValidationException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory state of the simulation currently open in the laboratory.
 *
 * This class is the single source of truth for every module: services apply
 * validated changes here, the UI and the algorithms read from here, and
 * registered {@link SimulationListener}s are notified after every committed
 * change so tables, dashboard and graph always reflect the live state.
 *
 * The engine contains no SQL and no JavaFX code, which keeps it fully
 * unit-testable. Persistence order is always: validate against the engine,
 * commit to MySQL, mirror into the engine, then notify listeners.
 */
public class SimulationEngine {

    private Simulation simulation;
    private final List<SimProcess> processes = new ArrayList<>();
    private final List<Resource> resources = new ArrayList<>();
    private final List<Allocation> allocations = new ArrayList<>();
    private final List<ResourceRequest> requests = new ArrayList<>();
    /** Banker's Algorithm maximum claims: processId -> resourceId -> max. */
    private final Map<Long, Map<Long, Integer>> maxClaims = new HashMap<>();
    private final List<SimulationListener> listeners = new ArrayList<>();

    /** Increments on every state-changing operation; used by events/snapshots. */
    private int stepNumber = 0;

    // ---------- lifecycle ----------

    public boolean hasSimulation() {
        return simulation != null;
    }

    public Simulation getSimulation() {
        return simulation;
    }

    /** Replaces the whole state; used when a saved simulation is opened. */
    public void load(Simulation loadedSimulation,
                     List<SimProcess> loadedProcesses,
                     List<Resource> loadedResources,
                     List<Allocation> loadedAllocations,
                     List<ResourceRequest> loadedRequests,
                     Map<Long, Map<Long, Integer>> loadedMaxClaims) {
        this.simulation = loadedSimulation;
        this.processes.clear();
        this.processes.addAll(loadedProcesses);
        this.resources.clear();
        this.resources.addAll(loadedResources);
        this.allocations.clear();
        this.allocations.addAll(loadedAllocations);
        this.requests.clear();
        this.requests.addAll(loadedRequests);
        this.maxClaims.clear();
        this.maxClaims.putAll(loadedMaxClaims);
        this.stepNumber = 0;
    }

    public void clear() {
        load(null, List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    // ---------- listeners ----------

    public void addListener(SimulationListener listener) {
        listeners.add(listener);
    }

    public void removeListener(SimulationListener listener) {
        listeners.remove(listener);
    }

    public void fireChange(SimulationEvent event) {
        event.setStepNumber(stepNumber);
        for (SimulationListener listener : listeners) {
            listener.onSimulationChanged(event);
        }
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public void bumpStep() {
        stepNumber++;
    }

    // ---------- read access ----------

    public List<SimProcess> getProcesses() {
        return List.copyOf(processes);
    }

    public List<Resource> getResources() {
        return List.copyOf(resources);
    }

    public List<Allocation> getAllocations() {
        return List.copyOf(allocations);
    }

    public List<ResourceRequest> getPendingRequests() {
        return requests.stream()
                .filter(r -> r.getStatus() == RequestStatus.PENDING)
                .toList();
    }

    public List<ResourceRequest> getAllRequests() {
        return List.copyOf(requests);
    }

    public Optional<SimProcess> findProcess(long processId) {
        return processes.stream().filter(p -> p.getId() == processId).findFirst();
    }

    public Optional<Resource> findResource(long resourceId) {
        return resources.stream().filter(r -> r.getId() == resourceId).findFirst();
    }

    public boolean hasProcessName(String name) {
        return processes.stream().anyMatch(p -> p.getName().equalsIgnoreCase(name));
    }

    public boolean hasResourceName(String name) {
        return resources.stream().anyMatch(r -> r.getName().equalsIgnoreCase(name));
    }

    /** Instances a process currently holds of a resource (0 when none). */
    public int allocationOf(long processId, long resourceId) {
        return allocations.stream()
                .filter(a -> a.getProcessId() == processId && a.getResourceId() == resourceId)
                .mapToInt(Allocation::getInstances)
                .findFirst()
                .orElse(0);
    }

    public Optional<Allocation> findAllocation(long processId, long resourceId) {
        return allocations.stream()
                .filter(a -> a.getProcessId() == processId && a.getResourceId() == resourceId)
                .findFirst();
    }

    public List<Allocation> allocationsOf(long processId) {
        return allocations.stream()
                .filter(a -> a.getProcessId() == processId)
                .toList();
    }

    public Map<Long, Map<Long, Integer>> getMaxClaims() {
        return maxClaims;
    }

    public int maxClaimOf(long processId, long resourceId) {
        return maxClaims.getOrDefault(processId, Map.of())
                .getOrDefault(resourceId, 0);
    }

    // ---------- vectors and matrices for the algorithms ----------

    /** Available vector ordered like {@link #getResources()}. */
    public int[] availableVector() {
        return resources.stream().mapToInt(Resource::getAvailableInstances).toArray();
    }

    /** Allocation matrix [process][resource] using the same fixed ordering. */
    public int[][] allocationMatrix() {
        return matrixFrom((p, r) -> allocationOf(p.getId(), r.getId()));
    }

    /** Request matrix built from the PENDING requests only. */
    public int[][] requestMatrix() {
        return matrixFrom((p, r) -> pendingRequestOf(p.getId(), r.getId()));
    }

    public int pendingRequestOf(long processId, long resourceId) {
        return requests.stream()
                .filter(r -> r.getStatus() == RequestStatus.PENDING
                        && r.getProcessId() == processId
                        && r.getResourceId() == resourceId)
                .mapToInt(ResourceRequest::getInstances)
                .sum();
    }

    private interface CellValue {
        int value(SimProcess process, Resource resource);
    }

    private int[][] matrixFrom(CellValue cellValue) {
        int[][] matrix = new int[processes.size()][resources.size()];
        for (int i = 0; i < processes.size(); i++) {
            for (int j = 0; j < resources.size(); j++) {
                matrix[i][j] = cellValue.value(processes.get(i), resources.get(j));
            }
        }
        return matrix;
    }

    // ---------- mutations (called by services after validation/commit) ----------

    public void addProcess(SimProcess process) {
        processes.add(process);
    }

    public void addResource(Resource resource) {
        resources.add(resource);
    }

    public void removeResource(long resourceId) {
        resources.removeIf(r -> r.getId() == resourceId);
        allocations.removeIf(a -> a.getResourceId() == resourceId);
        requests.removeIf(q -> q.getResourceId() == resourceId);
        maxClaims.values().forEach(m -> m.remove(resourceId));
    }

    public void removeProcess(long processId) {
        processes.removeIf(p -> p.getId() == processId);
        allocations.removeIf(a -> a.getProcessId() == processId);
        requests.removeIf(q -> q.getProcessId() == processId);
        maxClaims.remove(processId);
    }

    public void setProcessState(long processId, ProcessState state) {
        findProcess(processId).ifPresent(p -> p.setState(state));
    }

    /** Adds or replaces the owned instance count of one (process, resource) pair. */
    public void applyAllocation(long processId, long resourceId, int newInstances) {
        Resource resource = findResource(resourceId)
                .orElseThrow(() -> new ValidationException("Unknown resource"));
        int oldInstances = allocationOf(processId, resourceId);
        int delta = newInstances - oldInstances;

        resource.setAvailableInstances(resource.getAvailableInstances() - delta);
        resource.setAllocatedInstances(resource.getAllocatedInstances() + delta);
        checkInvariant(resource);

        if (newInstances == 0) {
            allocations.removeIf(a ->
                    a.getProcessId() == processId && a.getResourceId() == resourceId);
            return;
        }
        findAllocation(processId, resourceId).ifPresentOrElse(
                a -> a.setInstances(newInstances),
                () -> allocations.add(new Allocation(
                        simulation.getId(), processId, resourceId, newInstances)));
    }

    /** Releases instances back to the available pool. */
    public void applyRelease(long processId, long resourceId, int instances) {
        int held = allocationOf(processId, resourceId);
        if (instances > held) {
            throw new ValidationException("Process holds only " + held + " instance(s)");
        }
        applyAllocation(processId, resourceId, held - instances);
    }

    public void addRequest(ResourceRequest request) {
        requests.add(request);
    }

    public void updateRequestStatus(long requestId, RequestStatus status) {
        requests.stream()
                .filter(r -> r.getId() != null && r.getId() == requestId)
                .findFirst()
                .ifPresent(r -> r.setStatus(status));
    }

    public void setMaxClaim(long processId, long resourceId, int maxInstances) {
        maxClaims.computeIfAbsent(processId, k -> new HashMap<>()).put(resourceId, maxInstances);
    }

    /** Mirrors a termination: drop holdings, pending requests, set state. */
    public void terminateProcess(long processId) {
        for (Allocation allocation : allocationsOf(processId)) {
            applyRelease(processId, allocation.getResourceId(), allocation.getInstances());
        }
        requests.removeIf(r -> r.getProcessId() == processId && r.getStatus() == RequestStatus.PENDING);
        setProcessState(processId, ProcessState.TERMINATED);
    }

    private void checkInvariant(Resource resource) {
        if (resource.getAvailableInstances() < 0
                || resource.getAllocatedInstances() < 0
                || resource.getAvailableInstances() + resource.getAllocatedInstances()
                        != resource.getTotalInstances()) {
            throw new ValidationException(
                    "Resource invariant violated for " + resource.getName()
                    + " (available " + resource.getAvailableInstances()
                    + " + allocated " + resource.getAllocatedInstances()
                    + " != total " + resource.getTotalInstances() + ")");
        }
    }
}
