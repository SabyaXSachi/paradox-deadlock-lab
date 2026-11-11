package com.paradox.service;

import com.paradox.database.Db;
import com.paradox.model.Allocation;
import com.paradox.model.ProcessState;
import com.paradox.model.Resource;
import com.paradox.model.ResourceRequest;
import com.paradox.model.SimProcess;
import com.paradox.model.SimulationEvent;
import com.paradox.model.SimulationSnapshot;
import com.paradox.repository.SnapshotRepository;
import com.paradox.util.JsonUtil;

/**
 * Persists a full-state JSON snapshot at every simulation step.
 *
 * Registers itself as an engine listener, so each committed state change
 * automatically produces one replayable snapshot row (step-numbered) without
 * the other services having to know about it.
 */
public class SnapshotService implements SimulationListener {

    private final SimulationEngine engine;
    private final SnapshotRepository snapshotRepository = new SnapshotRepository();

    public SnapshotService(SimulationEngine engine) {
        this.engine = engine;
        engine.addListener(this);
    }

    @Override
    public void onSimulationChanged(SimulationEvent event) {
        if (!engine.hasSimulation()) {
            return;
        }
        String json = snapshotJson();
        SimulationSnapshot snapshot = new SimulationSnapshot();
        snapshot.setSimulationId(engine.getSimulation().getId());
        snapshot.setStepNumber(Math.max(event.getStepNumber(), 0));
        snapshot.setSnapshotDataJson(json);
        try {
            Db.inTransaction(conn -> {
                snapshotRepository.insert(conn, snapshot);
                return null;
            });
        } catch (RuntimeException e) {
            // A snapshot must never break the operation that produced it.
        }
    }

    /**
     * Snapshot layout:
     * {"step":n,"processes":[{"name","state","arrival","priority"}],
     *  "resources":[{"name","total","available","allocated"}],
     *  "allocations":[[processName,resourceName,instances]],
     *  "pendingRequests":[[processName,resourceName,instances]]}
     */
    private String snapshotJson() {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("{\"step\":").append(engine.getStepNumber());

        sb.append(",\"processes\":[");
        boolean first = true;
        for (SimProcess process : engine.getProcesses()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"name\":").append(JsonUtil.quote(process.getName()))
              .append(",\"state\":").append(JsonUtil.quote(process.getState().name()))
              .append(",\"arrival\":").append(process.getArrivalTime())
              .append(",\"priority\":").append(process.getPriority())
              .append('}');
        }
        sb.append(']');

        sb.append(",\"resources\":[");
        first = true;
        for (Resource resource : engine.getResources()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"name\":").append(JsonUtil.quote(resource.getName()))
              .append(",\"total\":").append(resource.getTotalInstances())
              .append(",\"available\":").append(resource.getAvailableInstances())
              .append(",\"allocated\":").append(resource.getAllocatedInstances())
              .append('}');
        }
        sb.append(']');

        sb.append(",\"allocations\":[");
        first = true;
        for (Allocation allocation : engine.getAllocations()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('[')
              .append(JsonUtil.quote(processName(allocation.getProcessId())))
              .append(',')
              .append(JsonUtil.quote(resourceName(allocation.getResourceId())))
              .append(',').append(allocation.getInstances()).append(']');
        }
        sb.append(']');

        sb.append(",\"pendingRequests\":[");
        first = true;
        for (ResourceRequest request : engine.getPendingRequests()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('[')
              .append(JsonUtil.quote(processName(request.getProcessId())))
              .append(',')
              .append(JsonUtil.quote(resourceName(request.getResourceId())))
              .append(',').append(request.getInstances()).append(']');
        }
        sb.append(']');

        // State colors are useful during replay.
        sb.append(",\"waitingProcesses\":").append(
                engine.getProcesses().stream()
                        .filter(p -> p.getState() == ProcessState.WAITING
                                || p.getState() == ProcessState.BLOCKED)
                        .count());

        sb.append(",\"pendingCount\":").append(engine.getPendingRequests().size());
        sb.append('}');
        return sb.toString();
    }

    private String processName(long processId) {
        return engine.findProcess(processId).map(SimProcess::getName).orElse("P" + processId);
    }

    private String resourceName(long resourceId) {
        return engine.findResource(resourceId).map(Resource::getName).orElse("R" + resourceId);
    }
}
