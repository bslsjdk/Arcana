package dev.arcana.weave;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalExecutionBudgetTest {
    private static CanonicalExecutionBudget.Limits limits() {
        return new CanonicalExecutionBudget.Limits(3, 2, 4, 5);
    }

    @Test
    void nodeStepLimitDoesNotIncrementAfterExhaustion() {
        var session = new CanonicalExecutionBudget.Session(limits());

        assertEquals(CanonicalExecutionBudget.Admission.ACCEPTED, session.consumeNodeStep());
        assertEquals(CanonicalExecutionBudget.Admission.ACCEPTED, session.consumeNodeStep());
        assertEquals(CanonicalExecutionBudget.Admission.ACCEPTED, session.consumeNodeStep());
        assertEquals(CanonicalExecutionBudget.Admission.LIMIT_REACHED, session.consumeNodeStep());
        assertEquals(3, session.snapshot().nodeSteps());
    }

    @Test
    void regionDepthCannotExceedLimitOrUnderflow() {
        var session = new CanonicalExecutionBudget.Session(limits());

        assertEquals(CanonicalExecutionBudget.Admission.INVALID_STATE, session.exitRegion());
        assertEquals(CanonicalExecutionBudget.Admission.ACCEPTED, session.enterRegion());
        assertEquals(CanonicalExecutionBudget.Admission.ACCEPTED, session.enterRegion());
        assertEquals(CanonicalExecutionBudget.Admission.LIMIT_REACHED, session.enterRegion());
        assertEquals(2, session.snapshot().regionDepth());
        assertEquals(CanonicalExecutionBudget.Admission.ACCEPTED, session.exitRegion());
        assertEquals(CanonicalExecutionBudget.Admission.ACCEPTED, session.exitRegion());
    }

    @Test
    void loopBoundIsScopedToAnActivation() {
        var session = new CanonicalExecutionBudget.Session(limits());

        for (int i = 0; i < 4; i++) {
            assertEquals(CanonicalExecutionBudget.Admission.ACCEPTED,
                    session.beginLoopIteration("repeat", "activationA", 4));
        }
        assertEquals(CanonicalExecutionBudget.Admission.LIMIT_REACHED,
                session.beginLoopIteration("repeat", "activationA", 4));
        assertEquals(CanonicalExecutionBudget.Admission.ACCEPTED,
                session.beginLoopIteration("repeat", "activationB", 4));
        assertEquals(5, session.snapshot().loopIterations().values().stream()
                .mapToInt(Integer::intValue).sum());
    }

    @Test
    void rejectsMalformedOrOverPolicyLoopDeclarations() {
        var session = new CanonicalExecutionBudget.Session(limits());

        assertEquals(CanonicalExecutionBudget.Admission.INVALID_REQUEST,
                session.beginLoopIteration("../repeat", "activation", 2));
        assertEquals(CanonicalExecutionBudget.Admission.INVALID_REQUEST,
                session.beginLoopIteration("repeat", "activation", 5));
        assertTrue(session.snapshot().loopIterations().isEmpty());
    }

    @Test
    void runtimeValueReservationIsAtomicAndRejectsNegativeCounts() {
        var session = new CanonicalExecutionBudget.Session(limits());

        assertEquals(CanonicalExecutionBudget.Admission.ACCEPTED, session.reserveRuntimeValues(3));
        assertEquals(CanonicalExecutionBudget.Admission.LIMIT_REACHED, session.reserveRuntimeValues(3));
        assertEquals(CanonicalExecutionBudget.Admission.INVALID_REQUEST, session.reserveRuntimeValues(-1));
        assertEquals(CanonicalExecutionBudget.Admission.ACCEPTED, session.reserveRuntimeValues(2));
        assertEquals(5, session.snapshot().runtimeValues());
    }

    @Test
    void sessionsDoNotShareMutableCastState() {
        var first = new CanonicalExecutionBudget.Session(limits());
        var second = new CanonicalExecutionBudget.Session(limits());

        first.consumeNodeStep();
        first.enterRegion();
        first.beginLoopIteration("repeat", "activation", 2);
        first.reserveRuntimeValues(2);

        assertEquals(1, first.snapshot().nodeSteps());
        assertEquals(0, second.snapshot().nodeSteps());
        assertEquals(1, first.snapshot().regionDepth());
        assertEquals(0, second.snapshot().regionDepth());
        assertTrue(second.snapshot().loopIterations().isEmpty());
        assertEquals(0, second.snapshot().runtimeValues());
    }

    @Test
    void snapshotsCannotMutateSessionCounters() {
        var session = new CanonicalExecutionBudget.Session(limits());
        session.beginLoopIteration("repeat", "activation", 2);
        var snapshot = session.snapshot();

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.loopIterations().put("repeat#other", 1));
        assertEquals(1, session.snapshot().loopIterations().get("repeat#activation"));
    }
}
