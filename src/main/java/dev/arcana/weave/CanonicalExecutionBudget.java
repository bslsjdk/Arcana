package dev.arcana.weave;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-cast resource accounting for the future canonical weave interpreter.
 *
 * A Session belongs to exactly one cast. It is deliberately independent from
 * the immutable weave and compiled plan, so concurrent casts cannot share
 * counters or loop state. This class accounts for resources; it does not run
 * nodes, access the world, or spend mana.
 */
public final class CanonicalExecutionBudget {
    private CanonicalExecutionBudget() { }

    public record Limits(long maximumNodeSteps, int maximumRegionDepth,
                         int maximumIterationsPerLoop, int maximumRuntimeValues) {
        public Limits {
            if (maximumNodeSteps < 1) throw new IllegalArgumentException("maximumNodeSteps must be positive.");
            if (maximumRegionDepth < 1) throw new IllegalArgumentException("maximumRegionDepth must be positive.");
            if (maximumIterationsPerLoop < 1)
                throw new IllegalArgumentException("maximumIterationsPerLoop must be positive.");
            if (maximumRuntimeValues < 1)
                throw new IllegalArgumentException("maximumRuntimeValues must be positive.");
        }
    }

    public enum Admission {
        ACCEPTED,
        LIMIT_REACHED,
        INVALID_REQUEST,
        INVALID_STATE
    }

    public record Snapshot(long nodeSteps, int regionDepth, int runtimeValues,
                           Map<String, Integer> loopIterations) {
        public Snapshot {
            loopIterations = Map.copyOf(Objects.requireNonNull(loopIterations, "loopIterations"));
        }
    }

    /** Mutable accounting state that must never be shared between casts. */
    public static final class Session {
        private final Limits limits;
        private long nodeSteps;
        private int regionDepth;
        private int runtimeValues;
        private final Map<String, Integer> loopIterations = new LinkedHashMap<>();

        public Session(Limits limits) {
            this.limits = Objects.requireNonNull(limits, "limits");
        }

        public Admission consumeNodeStep() {
            if (nodeSteps >= limits.maximumNodeSteps()) return Admission.LIMIT_REACHED;
            nodeSteps++;
            return Admission.ACCEPTED;
        }

        public Admission enterRegion() {
            if (regionDepth >= limits.maximumRegionDepth()) return Admission.LIMIT_REACHED;
            regionDepth++;
            return Admission.ACCEPTED;
        }

        public Admission exitRegion() {
            if (regionDepth == 0) return Admission.INVALID_STATE;
            regionDepth--;
            return Admission.ACCEPTED;
        }

        /**
         * Admits one iteration. LIMIT_REACHED means the loop has completed its
         * declared bound; INVALID_REQUEST means its declaration exceeds the
         * server-side policy or its identifier is malformed.
         */
        public Admission beginLoopIteration(String loopId, String activationId, int declaredMaximum) {
            if (!isIdentifier(loopId) || !isIdentifier(activationId))
                return Admission.INVALID_REQUEST;
            if (declaredMaximum < 1 || declaredMaximum > limits.maximumIterationsPerLoop())
                return Admission.INVALID_REQUEST;
            String activationKey = loopId + "#" + activationId;
            int completed = loopIterations.getOrDefault(activationKey, 0);
            if (completed >= declaredMaximum) return Admission.LIMIT_REACHED;
            loopIterations.put(activationKey, completed + 1);
            return Admission.ACCEPTED;
        }

        private static boolean isIdentifier(String value) {
            return value != null && value.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}");
        }

        /** Reserves runtime value slots without partially changing state on failure. */
        public Admission reserveRuntimeValues(int count) {
            if (count < 0) return Admission.INVALID_REQUEST;
            if (count > limits.maximumRuntimeValues() - runtimeValues) return Admission.LIMIT_REACHED;
            runtimeValues += count;
            return Admission.ACCEPTED;
        }

        public Limits limits() { return limits; }

        public Snapshot snapshot() {
            return new Snapshot(nodeSteps, regionDepth, runtimeValues, loopIterations);
        }
    }
}
