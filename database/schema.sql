-- ============================================================================
--  Paradox - Deadlock Detection and Recovery Laboratory
--  MySQL schema: paradox_db (MySQL 8+, InnoDB, utf8mb4)
--
--  Idempotent: safe to run multiple times (CREATE TABLE IF NOT EXISTS).
--  The application also executes this file automatically on first launch
--  when the tables are missing (com.paradox.database.SchemaInitializer).
--
--  Tables (parent -> child):
--    1.  simulations            one row per simulation
--    2.  processes              processes of a simulation
--    3.  resources              resource types of a simulation
--    4.  resource_allocation    current allocations (process holds resource)
--    5.  resource_requests      pending / granted requests
--    6.  process_resource_max   Banker's Algorithm maximum claim matrix
--    7.  deadlock_events        detection results with step trace (JSON)
--    8.  recovery_logs          recovery operations and their outcome
--    9.  simulation_snapshots   replayable full-state snapshots (JSON)
--    10. simulation_events      append-only event log for the history view
--
--  Invariants enforced by CHECK constraints:
--    resources:  available_instances + allocated_instances = total_instances
--                and no negative instance counts anywhere.
-- ============================================================================

CREATE TABLE IF NOT EXISTS simulations (
    simulation_id  BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(100) NOT NULL,
    description    VARCHAR(500) NULL,
    status         ENUM('CREATED', 'RUNNING', 'DEADLOCKED', 'RECOVERED', 'COMPLETED')
                                NOT NULL DEFAULT 'CREATED',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (simulation_id),
    KEY idx_simulations_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS processes (
    process_id     BIGINT      NOT NULL AUTO_INCREMENT,
    simulation_id  BIGINT      NOT NULL,
    process_name   VARCHAR(20) NOT NULL,
    arrival_time   INT         NOT NULL DEFAULT 0,
    priority       INT         NOT NULL DEFAULT 0,
    state          ENUM('READY', 'RUNNING', 'WAITING', 'BLOCKED', 'TERMINATED')
                               NOT NULL DEFAULT 'READY',
    PRIMARY KEY (process_id),
    UNIQUE KEY uq_process_name (simulation_id, process_name),
    KEY idx_processes_simulation (simulation_id),
    CONSTRAINT fk_processes_simulation
        FOREIGN KEY (simulation_id) REFERENCES simulations (simulation_id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resources (
    resource_id         BIGINT      NOT NULL AUTO_INCREMENT,
    simulation_id       BIGINT      NOT NULL,
    resource_name       VARCHAR(20) NOT NULL,
    total_instances     INT         NOT NULL,
    available_instances INT         NOT NULL,
    allocated_instances INT         NOT NULL,
    PRIMARY KEY (resource_id),
    UNIQUE KEY uq_resource_name (simulation_id, resource_name),
    KEY idx_resources_simulation (simulation_id),
    CONSTRAINT fk_resources_simulation
        FOREIGN KEY (simulation_id) REFERENCES simulations (simulation_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_resource_total       CHECK (total_instances > 0),
    CONSTRAINT chk_resource_available   CHECK (available_instances >= 0),
    CONSTRAINT chk_resource_allocated   CHECK (allocated_instances >= 0),
    -- The core accounting invariant of the resource manager.
    CONSTRAINT chk_resource_invariant
        CHECK (available_instances + allocated_instances = total_instances)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resource_allocation (
    allocation_id  BIGINT  NOT NULL AUTO_INCREMENT,
    simulation_id  BIGINT  NOT NULL,
    process_id     BIGINT  NOT NULL,
    resource_id    BIGINT  NOT NULL,
    instances      INT     NOT NULL,
    allocated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (allocation_id),
    -- One row per (process, resource) pair; repeated grants raise `instances`.
    UNIQUE KEY uq_allocation_pair (process_id, resource_id),
    KEY idx_allocation_simulation (simulation_id),
    KEY idx_allocation_resource (resource_id),
    CONSTRAINT fk_allocation_simulation
        FOREIGN KEY (simulation_id) REFERENCES simulations (simulation_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_allocation_process
        FOREIGN KEY (process_id) REFERENCES processes (process_id)
        ON DELETE CASCADE,
    -- RESTRICT: a resource that is still allocated cannot be deleted.
    CONSTRAINT fk_allocation_resource
        FOREIGN KEY (resource_id) REFERENCES resources (resource_id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_allocation_instances CHECK (instances > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resource_requests (
    request_id    BIGINT  NOT NULL AUTO_INCREMENT,
    simulation_id BIGINT  NOT NULL,
    process_id    BIGINT  NOT NULL,
    resource_id   BIGINT  NOT NULL,
    instances     INT     NOT NULL,
    status        ENUM('PENDING', 'GRANTED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
    requested_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (request_id),
    KEY idx_requests_simulation (simulation_id),
    KEY idx_requests_status (simulation_id, status),
    CONSTRAINT fk_requests_simulation
        FOREIGN KEY (simulation_id) REFERENCES simulations (simulation_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_requests_process
        FOREIGN KEY (process_id) REFERENCES processes (process_id)
        ON DELETE CASCADE,
    -- RESTRICT: a resource with pending requests cannot be deleted either.
    CONSTRAINT fk_requests_resource
        FOREIGN KEY (resource_id) REFERENCES resources (resource_id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_request_instances CHECK (instances > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Banker's Algorithm: maximum claim of each process for each resource type.
CREATE TABLE IF NOT EXISTS process_resource_max (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    process_id    BIGINT NOT NULL,
    resource_id   BIGINT NOT NULL,
    max_instances INT    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_max_pair (process_id, resource_id),
    CONSTRAINT fk_max_process
        FOREIGN KEY (process_id) REFERENCES processes (process_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_max_resource
        FOREIGN KEY (resource_id) REFERENCES resources (resource_id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_max_instances CHECK (max_instances >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS deadlock_events (
    event_id             BIGINT   NOT NULL AUTO_INCREMENT,
    simulation_id        BIGINT   NOT NULL,
    detection_type       ENUM('RAG', 'MULTI_INSTANCE') NOT NULL,
    deadlock_detected    BOOLEAN  NOT NULL,
    deadlocked_processes JSON     NULL,   -- JSON array of process names
    explanation          TEXT     NULL,
    step_trace           JSON     NULL,   -- ordered list of algorithm steps
    detected_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id),
    KEY idx_deadlock_simulation (simulation_id),
    CONSTRAINT fk_deadlock_simulation
        FOREIGN KEY (simulation_id) REFERENCES simulations (simulation_id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS recovery_logs (
    recovery_id        BIGINT       NOT NULL AUTO_INCREMENT,
    simulation_id      BIGINT       NOT NULL,
    strategy           ENUM('TERMINATE_ONE', 'TERMINATE_ALL', 'PREEMPT_RESOURCE') NOT NULL,
    target_description VARCHAR(200) NOT NULL,
    result_summary     TEXT         NULL,
    success            BOOLEAN      NOT NULL,
    executed_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (recovery_id),
    KEY idx_recovery_simulation (simulation_id),
    CONSTRAINT fk_recovery_simulation
        FOREIGN KEY (simulation_id) REFERENCES simulations (simulation_id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS simulation_snapshots (
    snapshot_id   BIGINT  NOT NULL AUTO_INCREMENT,
    simulation_id BIGINT  NOT NULL,
    step_number   INT     NOT NULL,
    snapshot_data JSON    NOT NULL,   -- full state: processes, resources, matrices
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (snapshot_id),
    UNIQUE KEY uq_snapshot_step (simulation_id, step_number),
    CONSTRAINT fk_snapshot_simulation
        FOREIGN KEY (simulation_id) REFERENCES simulations (simulation_id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS simulation_events (
    event_id      BIGINT       NOT NULL AUTO_INCREMENT,
    simulation_id BIGINT       NOT NULL,
    step_number   INT          NULL,
    event_type    ENUM('SIMULATION_CREATED', 'SIMULATION_DELETED', 'PROCESS_CREATED',
                       'PROCESS_TERMINATED', 'RESOURCE_CREATED', 'RESOURCE_DELETED',
                       'ALLOCATION', 'REQUEST', 'GRANT', 'RELEASE', 'DETECTION',
                       'BANKERS_CHECK', 'RECOVERY', 'SNAPSHOT', 'STATE_CHANGE')
                               NOT NULL,
    message       VARCHAR(500) NOT NULL,
    event_data    JSON         NULL,
    event_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id),
    KEY idx_events_simulation (simulation_id, event_id),
    CONSTRAINT fk_events_simulation
        FOREIGN KEY (simulation_id) REFERENCES simulations (simulation_id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
