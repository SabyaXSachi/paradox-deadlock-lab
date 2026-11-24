package com.paradox.controller;

import com.paradox.AppContext;
import com.paradox.algorithm.DetectionResult;
import com.paradox.algorithm.ResourceAllocationGraph;
import com.paradox.model.Resource;
import com.paradox.model.SimProcess;
import com.paradox.service.SimulationListener;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;

/**
 * Deadlock Visualizer: draws the live Resource Allocation Graph.
 *
 * Processes are circles on the left, resources rounded rectangles on the
 * right. Assignment edges (resource -> process) are solid, request edges
 * (process -> resource) dashed. After a detection run, deadlocked processes
 * and cycle edges are highlighted in red. The graph rebuilds on every
 * engine change - it is never static.
 */
public class VisualizerController implements SimulationListener {

    private static final double PROCESS_X = 130;
    private static final double RESOURCE_X = 520;
    private static final double TOP_MARGIN = 60;
    private static final double ROW_HEIGHT = 88;
    private static final double NODE_RADIUS = 26;

    @FXML private ScrollPane graphScroll;
    @FXML private Pane graphPane;
    @FXML private Label graphStatusLabel;

    @FXML
    private void initialize() {
        AppContext.engine().addListener(this);
        redraw();
    }

    private void redraw() {
        graphPane.getChildren().clear();
        SimulationEngineSnapshot snapshot = currentSnapshot();
        if (snapshot == null) {
            graphStatusLabel.setText("Create or open a simulation to see the "
                    + "resource allocation graph.");
            return;
        }

        ResourceAllocationGraph graph = ResourceAllocationGraph.fromEngine(AppContext.engine());
        Set<String> deadlocked = deadlockedKeys();
        Set<String> cycleNodes = cycleNodes(graph);

        int rows = Math.max(graph.getProcessNodes().size(), graph.getResourceNodes().size());
        graphPane.setPrefSize(700, TOP_MARGIN + rows * ROW_HEIGHT + 60);

        // Nodes
        for (int i = 0; i < graph.getProcessNodes().size(); i++) {
            String key = graph.getProcessNodes().get(i);
            double y = TOP_MARGIN + i * ROW_HEIGHT;
            boolean highlight = deadlocked.contains(key);
            drawProcessNode(key, PROCESS_X, y, highlight, cycleNodes.contains(key));
        }
        for (int i = 0; i < graph.getResourceNodes().size(); i++) {
            String key = graph.getResourceNodes().get(i);
            double y = TOP_MARGIN + i * ROW_HEIGHT;
            drawResourceNode(key, RESOURCE_X, y, cycleNodes.contains(key));
        }

        // Edges: assignments R -> P (solid), requests P -> R (dashed).
        for (String[] edge : graph.getAssignmentEdges()) {
            Double[] from = positionOf(graph, edge[0]);
            Double[] to = positionOf(graph, edge[1]);
            drawEdge(from, to, false,
                    isCycleEdge(graph, edge[0], edge[1]));
        }
        for (String[] edge : graph.getRequestEdges()) {
            Double[] from = positionOf(graph, edge[0]);
            Double[] to = positionOf(graph, edge[1]);
            drawEdge(from, to, true,
                    isCycleEdge(graph, edge[0], edge[1]));
        }

        graphStatusLabel.setText(buildStatusText(graph, deadlocked));
    }

    // The visualizer reads only live engine data, so a tiny holder keeps
    // the drawing code free of null checks.
    private record SimulationEngineSnapshot(int processCount, int resourceCount) {
    }

    private SimulationEngineSnapshot currentSnapshot() {
        if (!AppContext.engine().hasSimulation()) {
            return null;
        }
        return new SimulationEngineSnapshot(
                AppContext.engine().getProcesses().size(),
                AppContext.engine().getResources().size());
    }

    private Double[] positionOf(ResourceAllocationGraph graph, String key) {
        List<String> processes = graph.getProcessNodes();
        List<String> resources = graph.getResourceNodes();
        int pIndex = processes.indexOf(key);
        if (pIndex >= 0) {
            return new Double[]{PROCESS_X, TOP_MARGIN + pIndex * ROW_HEIGHT};
        }
        int rIndex = resources.indexOf(key);
        return new Double[]{RESOURCE_X, TOP_MARGIN + rIndex * ROW_HEIGHT};
    }

    private void drawProcessNode(String key, double x, double y, boolean deadlocked,
                                 boolean inCycle) {
        Circle circle = new Circle(x, y, NODE_RADIUS);
        circle.setFill(deadlocked ? Color.valueOf("#e05252") : Color.valueOf("#2559c7"));
        circle.setStroke(inCycle && !deadlocked ? Color.valueOf("#e05252")
                : Color.valueOf("#1f2a44"));
        circle.setStrokeWidth(inCycle || deadlocked ? 3 : 1.5);
        Text label = new Text(x - 16, y + 5, nameOf(key));
        label.setFill(Color.WHITE);
        label.setStyle("-fx-font-weight: bold;");
        graphPane.getChildren().addAll(circle, label);
        if (deadlocked) {
            Text warning = new Text(x + NODE_RADIUS + 4, y + 4, "deadlocked");
            warning.setFill(Color.valueOf("#b32222"));
            warning.setStyle("-fx-font-weight: bold;");
            graphPane.getChildren().add(warning);
        }
    }

    private void drawResourceNode(String key, double x, double y, boolean inCycle) {
        Rectangle rect = new Rectangle(x - NODE_RADIUS, y - NODE_RADIUS,
                NODE_RADIUS * 2, NODE_RADIUS * 2);
        rect.setArcWidth(10);
        rect.setArcHeight(10);
        rect.setFill(Color.valueOf("#f0a13a"));
        rect.setStroke(inCycle ? Color.valueOf("#b32222") : Color.valueOf("#1f2a44"));
        rect.setStrokeWidth(inCycle ? 3 : 1.5);
        Text label = new Text(x - 16, y + 5, nameOf(key));
        label.setFill(Color.WHITE);
        label.setStyle("-fx-font-weight: bold;");
        graphPane.getChildren().addAll(rect, label);
    }

    private void drawEdge(Double[] from, Double[] to, boolean dashed, boolean cycle) {
        double x1 = from[0];
        double y1 = from[1];
        double x2 = to[0];
        double y2 = to[1];
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 1) {
            return;
        }
        double ux = dx / length;
        double uy = dy / length;

        // Start and end outside the node shapes.
        double startX = x1 + ux * (NODE_RADIUS + 4);
        double startY = y1 + uy * (NODE_RADIUS + 4);
        double endX = x2 - ux * (NODE_RADIUS + 6);
        double endY = y2 - uy * (NODE_RADIUS + 6);

        Color color = cycle ? Color.valueOf("#b32222") : Color.valueOf("#5c6b85");
        Line line = new Line(startX, startY, endX, endY);
        line.setStroke(color);
        line.setStrokeWidth(cycle ? 2.5 : 1.6);
        if (dashed) {
            line.getStrokeDashArray().addAll(7d, 5d);
        }
        graphPane.getChildren().add(line);
        drawArrowHead(endX, endY, ux, uy, color);
    }

    private void drawArrowHead(double x, double y, double ux, double uy, Color color) {
        double size = 8;
        double px = -uy;
        double py = ux;
        Polygon head = new Polygon(
                x, y,
                x - ux * size + px * size / 2, y - uy * size + py * size / 2,
                x - ux * size - px * size / 2, y - uy * size - py * size / 2);
        head.setFill(color);
        graphPane.getChildren().add(head);
    }

    private Set<String> deadlockedKeys() {
        DetectionResult result = AppContext.getLastDetectionResult();
        if (result == null || !result.isDeadlockDetected()) {
            return Set.of();
        }
        Set<String> keys = new HashSet<>();
        for (Long id : result.getDeadlockedProcessIds()) {
            keys.add(ResourceAllocationGraph.key(id));
        }
        return keys;
    }

    private Set<String> cycleNodes(ResourceAllocationGraph graph) {
        Set<String> nodes = new HashSet<>();
        for (List<String> cycle : graph.findCycles()) {
            nodes.addAll(cycle);
        }
        return nodes;
    }

    private Set<String> cycleEdgePairs(ResourceAllocationGraph graph) {
        Set<String> pairs = new HashSet<>();
        for (List<String> cycle : graph.findCycles()) {
            for (int i = 0; i < cycle.size() - 1; i++) {
                pairs.add(cycle.get(i) + ">" + cycle.get(i + 1));
            }
        }
        return pairs;
    }

    private boolean isCycleEdge(ResourceAllocationGraph graph, String from, String to) {
        return cycleEdgePairs(graph).contains(from + ">" + to);
    }

    private String nameOf(String key) {
        if (key.startsWith("P")) {
            return AppContext.engine().findProcess(Long.parseLong(key.substring(1)))
                    .map(SimProcess::getName).orElse(key);
        }
        return AppContext.engine().findResource(Long.parseLong(key.substring(1)))
                .map(Resource::getName).orElse(key);
    }

    private String buildStatusText(ResourceAllocationGraph graph, Set<String> deadlocked) {
        String edges = graph.getAssignmentEdges().size() + " allocation edge(s), "
                + graph.getRequestEdges().size() + " request edge(s)";
        if (!deadlocked.isEmpty()) {
            return "DEADLOCK: " + deadlocked.size() + " process(es) highlighted in red. "
                    + edges + ". Run recovery to resolve it.";
        }
        if (graph.hasCycle()) {
            return "The graph contains a cycle (red border), but the resources involved "
                    + "have multiple instances - run detection to decide. " + edges + ".";
        }
        return "No cycle in the graph. " + edges + ".";
    }

    @Override
    public void onSimulationChanged(com.paradox.model.SimulationEvent event) {
        javafx.application.Platform.runLater(this::redraw);
    }
}
