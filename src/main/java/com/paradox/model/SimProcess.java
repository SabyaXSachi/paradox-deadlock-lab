package com.paradox.model;

/**
 * A process inside a simulation. Named "SimProcess" to avoid clashing with
 * {@link java.lang.Process}.
 */
public class SimProcess {

    private Long id;
    private Long simulationId;
    private String name;          // P1, P2, ...
    private int arrivalTime;
    private int priority;
    private ProcessState state = ProcessState.READY;

    public SimProcess() {
    }

    public SimProcess(Long simulationId, String name, int arrivalTime, int priority) {
        this.simulationId = simulationId;
        this.name = name;
        this.arrivalTime = arrivalTime;
        this.priority = priority;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(int arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public ProcessState getState() {
        return state;
    }

    public void setState(ProcessState state) {
        this.state = state;
    }
}
