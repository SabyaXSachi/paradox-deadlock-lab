package com.paradox.model;

import java.time.LocalDateTime;

/** One entry of the append-only simulation event log. */
public class SimulationEvent {

    private Long id;
    private Long simulationId;
    private Integer stepNumber;   // null when not tied to a specific step
    private EventType eventType;
    private String message;
    private String eventDataJson; // optional structured payload
    private LocalDateTime eventTime;

    public SimulationEvent() {
    }

    public SimulationEvent(Long simulationId, EventType eventType, String message) {
        this.simulationId = simulationId;
        this.eventType = eventType;
        this.message = message;
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

    public Integer getStepNumber() {
        return stepNumber;
    }

    public void setStepNumber(Integer stepNumber) {
        this.stepNumber = stepNumber;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getEventDataJson() {
        return eventDataJson;
    }

    public void setEventDataJson(String eventDataJson) {
        this.eventDataJson = eventDataJson;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalDateTime eventTime) {
        this.eventTime = eventTime;
    }
}
