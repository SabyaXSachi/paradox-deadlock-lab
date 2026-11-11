package com.paradox.service;

import com.paradox.model.Simulation;

/**
 * One-click demonstration scenario from the specification (section 8):
 * three processes, three single-instance resources, circular wait
 * P1 holds R1 wants R2; P2 holds R2 wants R3; P3 holds R3 wants R1.
 */
public class DemoScenarioService {

    private final SimulationService simulationService;
    private final ProcessService processService;
    private final ResourceService resourceService;
    private final AllocationService allocationService;

    public DemoScenarioService(SimulationService simulationService,
                               ProcessService processService,
                               ResourceService resourceService,
                               AllocationService allocationService) {
        this.simulationService = simulationService;
        this.processService = processService;
        this.resourceService = resourceService;
        this.allocationService = allocationService;
    }

    /** Builds the circular-wait scenario and returns the open simulation. */
    public Simulation loadCircularWaitDemo() {
        Simulation simulation = simulationService.createSimulation(
                "Circular Wait Demo (P1-P2-P3)",
                "Three processes, three single-instance resources. P1 holds R1 and "
                        + "requests R2; P2 holds R2 and requests R3; P3 holds R3 and "
                        + "requests R1.");

        processService.addProcess("P1", 0, 1);
        processService.addProcess("P2", 0, 1);
        processService.addProcess("P3", 0, 1);
        var r1 = resourceService.addResource("R1", 1);
        var r2 = resourceService.addResource("R2", 1);
        var r3 = resourceService.addResource("R3", 1);

        var processes = simulationService.engine().getProcesses();
        long p1 = processes.get(0).getId();
        long p2 = processes.get(1).getId();
        long p3 = processes.get(2).getId();

        allocationService.allocate(p1, r1.getId(), 1);
        allocationService.allocate(p2, r2.getId(), 1);
        allocationService.allocate(p3, r3.getId(), 1);
        // These requests cannot be granted: every process must wait -> deadlock.
        allocationService.request(p1, r2.getId(), 1);
        allocationService.request(p2, r3.getId(), 1);
        allocationService.request(p3, r1.getId(), 1);

        return simulation;
    }
}
