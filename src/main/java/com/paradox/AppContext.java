package com.paradox;

import com.paradox.database.DatabaseConnection;
import com.paradox.database.SchemaInitializer;
import com.paradox.service.AllocationService;
import com.paradox.service.BankersService;
import com.paradox.service.DemoScenarioService;
import com.paradox.service.DetectionService;
import com.paradox.service.HistoryService;
import com.paradox.service.ProcessService;
import com.paradox.service.RecoveryService;
import com.paradox.service.ResourceService;
import com.paradox.service.SimulationEngine;
import com.paradox.service.SimulationService;
import com.paradox.service.SnapshotService;

/**
 * Simple application context: one engine, one set of services, shared by
 * every controller for the lifetime of the application. Also owns the
 * startup duties (connection check and schema initialization).
 */
public final class AppContext {

    private static final SimulationEngine ENGINE = new SimulationEngine();
    private static final SimulationService SIMULATION_SERVICE = new SimulationService(ENGINE);
    private static final ProcessService PROCESS_SERVICE =
            new ProcessService(ENGINE, SIMULATION_SERVICE);
    private static final ResourceService RESOURCE_SERVICE =
            new ResourceService(ENGINE, SIMULATION_SERVICE);
    private static final AllocationService ALLOCATION_SERVICE =
            new AllocationService(ENGINE, SIMULATION_SERVICE);
    private static final DetectionService DETECTION_SERVICE =
            new DetectionService(ENGINE, SIMULATION_SERVICE);
    private static final BankersService BANKERS_SERVICE =
            new BankersService(ENGINE, SIMULATION_SERVICE);
    private static final RecoveryService RECOVERY_SERVICE =
            new RecoveryService(ENGINE, SIMULATION_SERVICE, PROCESS_SERVICE);
    /** Snapshots every committed simulation step (engine listener). */
    private static final SnapshotService SNAPSHOT_SERVICE =
            new SnapshotService(ENGINE);
    private static final HistoryService HISTORY_SERVICE = new HistoryService();
    private static final DemoScenarioService DEMO_SCENARIO_SERVICE =
            new DemoScenarioService(SIMULATION_SERVICE, PROCESS_SERVICE,
                    RESOURCE_SERVICE, ALLOCATION_SERVICE);

    private static boolean databaseReady;

    private AppContext() {
    }

    /** Called once from MainApp before the UI is built. */
    public static void initialize() {
        try {
            databaseReady = DatabaseConnection.isReachable();
            if (databaseReady) {
                SchemaInitializer.initializeIfMissing();
            }
        } catch (Exception e) {
            databaseReady = false;
        }
    }

    public static boolean isDatabaseReady() {
        return databaseReady;
    }

    public static SimulationEngine engine() {
        return ENGINE;
    }

    public static SimulationService simulationService() {
        return SIMULATION_SERVICE;
    }

    public static ProcessService processService() {
        return PROCESS_SERVICE;
    }

    public static ResourceService resourceService() {
        return RESOURCE_SERVICE;
    }

    public static AllocationService allocationService() {
        return ALLOCATION_SERVICE;
    }

    public static DetectionService detectionService() {
        return DETECTION_SERVICE;
    }

    public static BankersService bankersService() {
        return BANKERS_SERVICE;
    }

    public static RecoveryService recoveryService() {
        return RECOVERY_SERVICE;
    }

    public static HistoryService historyService() {
        return HISTORY_SERVICE;
    }

    public static DemoScenarioService demoScenarioService() {
        return DEMO_SCENARIO_SERVICE;
    }

    // ---------- shared UI state ----------

    private static volatile com.paradox.algorithm.DetectionResult lastDetectionResult;

    /** Result of the most recent detection run, for cross-view highlighting. */
    public static com.paradox.algorithm.DetectionResult getLastDetectionResult() {
        return lastDetectionResult;
    }

    public static void setLastDetectionResult(
            com.paradox.algorithm.DetectionResult result) {
        lastDetectionResult = result;
    }
}
