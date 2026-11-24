# Paradox – Deadlock Detection and Recovery Laboratory

An interactive Operating Systems laboratory that demonstrates how deadlocks occur,
how operating systems detect them, and how they can be recovered from.

Built with **Java 21 + JavaFX 21 + MySQL 8**, managed by **Maven**, tested with **JUnit 5**.

## Features

| Module | What it does |
|---|---|
| **Dashboard** | Live statistics, system status, recent simulations, recent event log |
| **Process Manager** | Create processes (name, arrival, priority), watch READY/RUNNING/WAITING/BLOCKED/TERMINATED states change automatically, terminate processes |
| **Resource Manager** | Create resource types with instance counts; the invariant `available + allocated = total` is enforced in code *and* by a database CHECK constraint |
| **Allocation Engine** | Direct allocation, requests (granted or process waits), releases that wake waiting processes (FIFO), request cancellation |
| **Detection Engine** | Single-instance RAG cycle detection *and* the multi-instance Work/Finish algorithm (auto-selected), with verdict, deadlocked/finishable processes, final Work vector and a full step-by-step trace |
| **Banker's Algorithm** | Maximum-claim matrix, safety algorithm with safe sequence, resource-request evaluation. Clearly presented as deadlock *avoidance* – an unsafe state is never reported as a deadlock |
| **Visualizer** | Live resource-allocation graph (circles = processes, squares = resources, solid = allocation, dashed = request). Deadlocked processes and cycles are highlighted after detection. Never static – redraws on every change |
| **Recovery Engine** | Terminate one / all deadlocked processes, simulated resource preemption (victim marked for rollback). Every action is verified by a fresh detection and logged |
| **History** | Event log, detection results, recovery logs and automatic per-step snapshots stored in MySQL, with step-by-step replay of every snapshot |
| **Demo scenario** | One-click circular wait (P1-P2-P3 / R1-R2-R3) for classroom demonstration |

## Prerequisites

| Software | Version used | Notes |
|---|---|---|
| JDK | 21 (Temurin) | `java -version` must show 17+ |
| Maven | 3.9.16 | installed at `C:\Users\sabya\tools\apache-maven-3.9.16` (on user PATH) |
| MySQL Server | 8.4.9 | installed at `C:\Program Files\MySQL\MySQL Server 8.4` |

## Database setup

The MySQL server runs from a user-space data directory (no admin rights needed):

- Config file: `C:\Users\sabya\mysql\my.ini`
- Data directory: `C:\Users\sabya\mysql\data`
- Database: `paradox_db` (created; tables are created automatically on first launch)

### Start MySQL

```bash
"C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqld.exe" --defaults-file="C:\Users\sabya\mysql\my.ini"
```

Run this in a separate terminal before starting the application.

### Credentials

user `root` and your MySQL password, stored in `config/database.properties`
(gitignored; see `config/database.properties.example`). Environment variables `PARADOX_DB_URL`,
`PARADOX_DB_USER` and `PARADOX_DB_PASSWORD` override the file. Passwords are
never hardcoded in source code.

## Build, test and run

```bash
# compile + run the full test suite (55 tests)
mvn clean verify

# start the JavaFX application
mvn javafx:run
```

### Ten-minute demo walkthrough (faculty showcase)

1. Start MySQL, run `mvn javafx:run`.
2. Header → **New...** → name it `Demo`, or click **Load demo scenario** on the dashboard.
3. **Process Manager** / **Resource Manager**: inspect P1-P3 and R1-R3 (one instance each).
4. **Allocation Engine**: `P1` holds `R1`, `P2` holds `R2`, `P3` holds `R3`; then request
   `P1 → R2`, `P2 → R3`, `P3 → R1` – all three processes switch to WAITING.
5. **Visualizer**: see the circular wait (P1→R2→P2→R3→P3→R1→P1).
6. **Deadlock Detection**: run detection – verdict DEADLOCK, all three processes listed,
   full step trace shown.
7. **Recovery Engine**: terminate `P3` – its resources are released, `P2` is woken and
   granted `R3`, detection re-runs and confirms the deadlock is resolved.
8. **Banker's Algorithm**: set maximum claims and check safe/unsafe states and the
   safe sequence.
9. **History**: open the simulation, inspect the event log, detection results,
   recovery log and replay any snapshot step. Everything persisted – restart the
   application and the history is still there.

## Project structure

```
database/schema.sql              SQL schema (10 tables, InnoDB, FKs, JSON, CHECKs)
config/database.properties       MySQL credentials (gitignored)
src/main/java/com/paradox/
    MainApp.java                 JavaFX entry point
    AppContext.java              engine + service registry (startup, schema init)
    algorithm/                   pure-Java logic, no JavaFX, no SQL:
                                 SingleInstanceDetector, MultiInstanceDetector,
                                 BankersAlgorithm, ResourceAllocationGraph,
                                 SystemState, DetectionResult, BankersResult
    service/                     SimulationEngine (observer pattern), services for
                                 simulation/process/resource/allocation/detection/
                                 banker's/recovery/history/snapshots/demo
    controller/                  one controller per module view
    model/                       domain classes and enums
    repository/                  10 JDBC DAO classes (prepared statements only)
    database/                    DatabaseConfig, DatabaseConnection, Db (transactions),
                                 SchemaInitializer
    util/                        JsonUtil (writer), JsonParser, AlertUtil, ValidationException
src/main/resources/              FXML views, paradox.css, images
src/test/java/com/paradox/       55 JUnit 5 tests (algorithms, engine, services, DB, JSON)
```

### Architecture notes

- **Layered**: controllers → services → repositories → MySQL. Controllers never
  touch SQL; algorithms never touch JavaFX or JDBC.
- **SimulationEngine** is the single in-memory source of truth. Every committed
  change fires an event; dashboard, tables and graph refresh automatically
  (observer pattern). The per-step snapshot service is just another listener.
- **Consistency order** for every operation: validate against the engine →
  commit to MySQL in one transaction (CHECK constraints re-verify the resource
  invariant) → mirror into the engine → notify listeners. If the transaction
  fails, the engine was never touched.
- MySQL-side safety nets: `CHECK (available + allocated = total)` on resources,
  `ON DELETE RESTRICT` on allocations/requests (a resource in use cannot be
  deleted), `ON DELETE CASCADE` for simulation cleanup.

## Accessibility

Interactive controls display a visible focus indicator for keyboard navigation.
The simulation selector is associated with its label, and the dashboard's
recent-activity views have accessible names. Muted text and accent colors are
selected to meet WCAG AA contrast against their backgrounds.

## Test coverage

- Detection: no deadlock, two-process deadlock, three-process circular wait,
  transitive blocking, multi-instance cases (including "RAG has a cycle but the
  system is NOT deadlocked")
- Banker's Algorithm: classic Silberschatz safe state + verified safe sequence,
  Need matrix, grant/wait/deny request decisions, unsafe ≠ deadlock
- Engine: allocation, release, invalid operations, invariants, matrices, listeners
- Integration (real MySQL): full allocation/waiting/termination flows, deadlock
  recovery (terminate + preemption), recovery logging, cascade deletes
- JSON writer/parser roundtrips and malformed-input handling

## Development phases

- [x] **Phase 1** – Project setup: Maven skeleton, JavaFX, MySQL Connector/J, window
- [x] **Phase 2** – Database: schema (10 tables), JDBC layer, repositories
- [x] **Phase 3** – Core models, SimulationEngine, process/resource/allocation services
- [x] **Phase 4** – Algorithms: RAG, multi-instance, Banker's + unit tests
- [x] **Phase 5** – JavaFX interface: dashboard, managers, allocation screen
- [x] **Phase 6** – Visualization, detection/recovery/banker's UIs
- [x] **Phase 7** – History, replay, demo scenario, final integration
