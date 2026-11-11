package com.paradox.model;

/** One cell of the Banker's Algorithm maximum-claim matrix. */
public class ProcessResourceMax {

    private Long id;
    private Long processId;
    private Long resourceId;
    private int maxInstances;

    public ProcessResourceMax() {
    }

    public ProcessResourceMax(Long processId, Long resourceId, int maxInstances) {
        this.processId = processId;
        this.resourceId = resourceId;
        this.maxInstances = maxInstances;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public int getMaxInstances() {
        return maxInstances;
    }

    public void setMaxInstances(int maxInstances) {
        this.maxInstances = maxInstances;
    }
}
