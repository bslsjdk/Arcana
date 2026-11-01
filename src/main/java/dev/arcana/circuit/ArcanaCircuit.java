package dev.arcana.circuit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Loader-independent typed graph model for player-designed magic circuits. */
public final class ArcanaCircuit {
    public static final int VERSION = 1;
    public static final int MAX_NODES = 512;
    public static final int MAX_EDGES = 2_048;
    public static final int MAX_LOOP_ITERATIONS = 1_024;

    private ArcanaCircuit() { }

    public enum ValueType { SIGNAL, BOOLEAN, NUMBER, TEXT, VECTOR3, MANA }
    public enum NodeKind { INPUT, PROCESS, BRANCH, MERGE, LOOP, OUTPUT }

    public record Node(String id, NodeKind kind, Map<String, ValueType> inputs,
                       Map<String, ValueType> outputs, Map<String, String> parameters) {
        public Node {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kind, "kind");
            inputs = immutablePorts(inputs, "inputs");
            outputs = immutablePorts(outputs, "outputs");
            parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
            if (!id.matches("[a-zA-Z0-9_.-]{1,64}")) {
                throw new IllegalArgumentException("Node id must be 1-64 safe identifier characters.");
            }
            if (parameters.size() > 32) throw new IllegalArgumentException("Too many node parameters.");
        }

        private static Map<String, ValueType> immutablePorts(Map<String, ValueType> ports, String label) {
            Objects.requireNonNull(ports, label);
            if (ports.size() > 32) throw new IllegalArgumentException("Too many " + label + ".");
            Map<String, ValueType> copy = new LinkedHashMap<>();
            ports.forEach((name, type) -> {
                if (name == null || !name.matches("[a-zA-Z0-9_.-]{1,48}")) {
                    throw new IllegalArgumentException("Invalid port name.");
                }
                copy.put(name, Objects.requireNonNull(type, "port type"));
            });
            return Map.copyOf(copy);
        }
    }

    public record Edge(String fromNode, String fromPort, String toNode, String toPort) {
        public Edge {
            Objects.requireNonNull(fromNode);
            Objects.requireNonNull(fromPort);
            Objects.requireNonNull(toNode);
            Objects.requireNonNull(toPort);
        }
    }

    public record Circuit(String id, int version, List<Node> nodes, List<Edge> edges,
                          Set<String> entryNodes, Set<String> outputNodes) {
        public Circuit {
            Objects.requireNonNull(id);
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                throw new IllegalArgumentException("Circuit id must use namespace:path format.");
            }
            if (version < 1) throw new IllegalArgumentException("Circuit version must be positive.");
            nodes = List.copyOf(Objects.requireNonNull(nodes));
            edges = List.copyOf(Objects.requireNonNull(edges));
            entryNodes = Set.copyOf(Objects.requireNonNull(entryNodes));
            outputNodes = Set.copyOf(Objects.requireNonNull(outputNodes));
        }
    }

    public record Diagnostic(String code, String message, String nodeId) {
        public Diagnostic {
            Objects.requireNonNull(code);
            Objects.requireNonNull(message);
        }
    }

    public record ValidationResult(List<Diagnostic> diagnostics) {
        public ValidationResult { diagnostics = List.copyOf(diagnostics); }
        public boolean valid() { return diagnostics.isEmpty(); }
    }

    public static ValidationResult validate(Circuit circuit) {
        Objects.requireNonNull(circuit);
        List<Diagnostic> issues = new ArrayList<>();
        if (circuit.nodes().isEmpty()) {
            add(issues, "CIRCUIT_EMPTY", "A circuit must contain at least one node.", null);
            return new ValidationResult(issues);
        }
        if (circuit.nodes().size() > MAX_NODES) {
            add(issues, "CIRCUIT_NODE_LIMIT", "Circuit exceeds the node limit.", null);
            return new ValidationResult(issues);
        }
        if (circuit.edges().size() > MAX_EDGES) {
            add(issues, "CIRCUIT_EDGE_LIMIT", "Circuit exceeds the edge limit.", null);
            return new ValidationResult(issues);
        }

        Map<String, Node> nodes = new LinkedHashMap<>();
        for (Node node : circuit.nodes()) {
            if (nodes.putIfAbsent(node.id(), node) != null) {
                add(issues, "CIRCUIT_DUPLICATE_NODE", "Duplicate node id: " + node.id(), node.id());
            }
            validateNodeShape(node, issues);
        }

        if (circuit.entryNodes().isEmpty()) add(issues, "CIRCUIT_NO_ENTRY", "A circuit needs an entry node.", null);
        if (circuit.outputNodes().isEmpty()) add(issues, "CIRCUIT_NO_OUTPUT", "A circuit needs an output node.", null);
        for (String id : circuit.entryNodes()) {
            Node node = nodes.get(id);
            if (node == null) add(issues, "CIRCUIT_UNKNOWN_ENTRY", "Unknown entry node: " + id, id);
            else if (node.kind() != NodeKind.INPUT) add(issues, "CIRCUIT_INVALID_ENTRY", "Entry must be INPUT.", id);
        }
        for (String id : circuit.outputNodes()) {
            Node node = nodes.get(id);
            if (node == null) add(issues, "CIRCUIT_UNKNOWN_OUTPUT", "Unknown output node: " + id, id);
            else if (node.kind() != NodeKind.OUTPUT) add(issues, "CIRCUIT_INVALID_OUTPUT", "Target must be OUTPUT.", id);
        }

        Map<String, Integer> incomingCount = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();
        Set<String> uniqueEdges = new HashSet<>();
        for (Edge edge : circuit.edges()) {
            Node from = nodes.get(edge.fromNode());
            Node to = nodes.get(edge.toNode());
            if (from == null || to == null) {
                add(issues, "CIRCUIT_UNKNOWN_ENDPOINT", "Edge references an unknown node.", null);
                continue;
            }
            String edgeKey = edge.fromNode() + ":" + edge.fromPort() + "->" + edge.toNode() + ":" + edge.toPort();
            if (!uniqueEdges.add(edgeKey)) {
                add(issues, "CIRCUIT_DUPLICATE_EDGE", "Duplicate connection.", edge.toNode());
                continue;
            }
            ValueType sourceType = from.outputs().get(edge.fromPort());
            ValueType targetType = to.inputs().get(edge.toPort());
            if (sourceType == null) {
                add(issues, "CIRCUIT_UNKNOWN_OUTPUT_PORT", "Unknown output port: " + edge.fromPort(), from.id());
                continue;
            }
            if (targetType == null) {
                add(issues, "CIRCUIT_UNKNOWN_INPUT_PORT", "Unknown input port: " + edge.toPort(), to.id());
                continue;
            }
            if (sourceType != targetType) {
                add(issues, "CIRCUIT_PORT_TYPE_MISMATCH", "Connected ports must have identical types.", to.id());
                continue;
            }
            String inputKey = to.id() + ":" + edge.toPort();
            if (incomingCount.merge(inputKey, 1, Integer::sum) > 1) {
                add(issues, "CIRCUIT_INPUT_ALREADY_CONNECTED", "An input accepts only one connection.", to.id());
            }
            adjacency.computeIfAbsent(from.id(), ignored -> new ArrayList<>()).add(to.id());
        }

        for (Node node : nodes.values()) {
            for (String port : node.inputs().keySet()) {
                if (node.kind() != NodeKind.INPUT && !incomingCount.containsKey(node.id() + ":" + port)) {
                    add(issues, "CIRCUIT_UNCONNECTED_INPUT", "Unconnected input port: " + port, node.id());
                }
            }
        }
        validateCycles(nodes, adjacency, issues);
        validateReachability(nodes, adjacency, circuit.entryNodes(), issues);
        return new ValidationResult(issues);
    }

    private static void validateNodeShape(Node node, List<Diagnostic> issues) {
        switch (node.kind()) {
            case INPUT -> {
                if (!node.inputs().isEmpty() || node.outputs().isEmpty())
                    add(issues, "CIRCUIT_INPUT_SHAPE", "INPUT has outputs and no inputs.", node.id());
            }
            case PROCESS -> {
                if (node.inputs().isEmpty() || node.outputs().isEmpty())
                    add(issues, "CIRCUIT_PROCESS_SHAPE", "PROCESS needs inputs and outputs.", node.id());
            }
            case BRANCH -> {
                if (node.inputs().get("condition") != ValueType.BOOLEAN
                        || node.outputs().get("true") != ValueType.SIGNAL
                        || node.outputs().get("false") != ValueType.SIGNAL)
                    add(issues, "CIRCUIT_BRANCH_SHAPE",
                            "BRANCH needs BOOLEAN condition and SIGNAL true/false outputs.", node.id());
            }
            case MERGE -> {
                if (node.inputs().size() < 2 || node.outputs().size() != 1) {
                    add(issues, "CIRCUIT_MERGE_SHAPE", "MERGE needs at least two inputs and one output.", node.id());
                } else {
                    ValueType type = node.outputs().values().iterator().next();
                    if (node.inputs().values().stream().anyMatch(input -> input != type))
                        add(issues, "CIRCUIT_MERGE_TYPE", "MERGE input types must match its output.", node.id());
                }
            }
            case LOOP -> {
                if (node.inputs().isEmpty() || node.outputs().isEmpty())
                    add(issues, "CIRCUIT_LOOP_SHAPE", "LOOP needs inputs and outputs.", node.id());
                try {
                    int limit = Integer.parseInt(node.parameters().getOrDefault("maxIterations", ""));
                    if (limit < 1 || limit > MAX_LOOP_ITERATIONS) throw new NumberFormatException();
                } catch (NumberFormatException ex) {
                    add(issues, "CIRCUIT_LOOP_LIMIT",
                            "LOOP maxIterations must be between 1 and " + MAX_LOOP_ITERATIONS + ".", node.id());
                }
            }
            case OUTPUT -> {
                if (node.inputs().isEmpty() || !node.outputs().isEmpty())
                    add(issues, "CIRCUIT_OUTPUT_SHAPE", "OUTPUT has inputs and no outputs.", node.id());
            }
        }
    }

    /** Every directed cycle must pass through a LOOP node with a finite iteration limit. */
    private static void validateCycles(Map<String, Node> nodes, Map<String, List<String>> adjacency,
                                       List<Diagnostic> issues) {
        Map<String, Integer> indegree = new HashMap<>();
        nodes.values().forEach(node -> indegree.put(node.id(), 0));
        for (Map.Entry<String, List<String>> entry : adjacency.entrySet()) {
            for (String target : entry.getValue()) {
                if (nodes.get(target).kind() == NodeKind.LOOP) continue;
                indegree.merge(target, 1, Integer::sum);
            }
        }
        List<String> queue = new ArrayList<>();
        indegree.forEach((id, degree) -> { if (degree == 0) queue.add(id); });
        int visited = 0;
        for (int i = 0; i < queue.size(); i++) {
            String current = queue.get(i);
            visited++;
            for (String target : adjacency.getOrDefault(current, List.of())) {
                if (nodes.get(target).kind() == NodeKind.LOOP) continue;
                if (indegree.merge(target, -1, Integer::sum) == 0) queue.add(target);
            }
        }
        if (visited != nodes.size()) add(issues, "CIRCUIT_UNBOUNDED_CYCLE",
                "Every cycle must pass through an explicitly bounded LOOP node.", null);
    }

    private static void validateReachability(Map<String, Node> nodes, Map<String, List<String>> adjacency,
                                             Set<String> entries, List<Diagnostic> issues) {
        Set<String> visited = new HashSet<>();
        List<String> queue = new ArrayList<>(entries);
        for (int i = 0; i < queue.size(); i++) {
            String current = queue.get(i);
            if (!visited.add(current) || !nodes.containsKey(current)) continue;
            queue.addAll(adjacency.getOrDefault(current, List.of()));
        }
        for (String id : nodes.keySet()) {
            if (!visited.contains(id))
                add(issues, "CIRCUIT_UNREACHABLE_NODE", "Node is unreachable from every entry.", id);
        }
    }

    private static void add(List<Diagnostic> issues, String code, String message, String nodeId) {
        issues.add(new Diagnostic(code, message, nodeId));
    }
}
