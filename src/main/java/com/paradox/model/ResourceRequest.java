package com.paradox.model;

import java.time.LocalDateTime;

/** A process asking for additional resource instances. */
public class ResourceRequest {

    private Long id;
    private Long simulationId;
    private Long processId;
    private Long resourceId;
    private int instances;
    private RequestStatus status = RequestStatus.PENDING;
    private LocalDateTime requestedAt;

    public ResourceRequest() {
    }

    public ResourceRequest(Long simulationId, Long processId, Long resourceId, int instances) {
        this.simulationId = simulationId;
        this.processId = processId;
        this.resourceId = resourceId;
        this.instances = instances;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSimulationId() {
        return simulationId;
    }

    public void setSimulationId(Long simulationId) {
        this.simulationId = simulationId;
    }

    public Long getProcessId() {
        return processId;
    }

    public void setProcessId(Long processId) {
        this.processId = processId;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public int getInstances() {
        return instances;
    }

    public void setInstances(int instances) {
        this.instances = instances;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }
}
