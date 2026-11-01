package dev.arcana.casting;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CastingSessionControllerTest {
    private static CastingTriggerRecognizer.TriggerProfile profile() {
        return new CastingTriggerRecognizer.TriggerProfile(
                List.of("Astra"), List.of("Nora"));
    }

    @Test
    void accumulatesPendingIncantationUntilEmbeddedActivation() {
        var controller = new CastingSessionController(profile());

        var pending = controller.accept("shape the light");
        var activated = controller.accept("Astra bind it to the crystal");

        assertEquals(CastingSessionController.State.PREPARED, pending.state());
        assertEquals(CastingSessionController.State.RUNNING, activated.state());
        assertEquals("shape the light bind it to the crystal", activated.payload());
        assertFalse(activated.cancellationRequested());
    }

    @Test
    void activationInOneBatchKeepsWordsOnBothSides() {
        var controller = new CastingSessionController(profile());

        var result = controller.accept("shape Astra bind the light");

        assertEquals(CastingSessionController.State.RUNNING, result.state());
        assertEquals("shape bind the light", result.payload());
    }

    @Test
    void laterOrdinaryBatchesContinueTheActiveIncantation() {
        var controller = new CastingSessionController(profile());
        controller.accept("shape Astra bind");

        var result = controller.accept("the light around the crystal");

        assertEquals(CastingSessionController.State.RUNNING, result.state());
        assertEquals("shape bind the light around the crystal", result.payload());
    }

    @Test
    void cancellationBeforeActivationDiscardsPendingContent() {
        var controller = new CastingSessionController(profile());
        controller.accept("shape the light");

        var result = controller.accept("Nora Astra bind");

        assertEquals(CastingSessionController.State.CANCELLED, result.state());
        assertEquals("", result.payload());
        assertFalse(result.cancellationRequested());
    }

    @Test
    void cancellationAfterActivationSignalsTheActiveRuntime() {
        var controller = new CastingSessionController(profile());
        controller.accept("shape Astra bind");

        var result = controller.accept("the light Nora trailing text");

        assertEquals(CastingSessionController.State.CANCELLED, result.state());
        assertTrue(result.cancellationRequested());
        assertTrue(controller.cancellationRequested());
        assertEquals("", result.payload());
    }

    @Test
    void activationAndCancellationInOneBatchFollowTheirTextualOrder() {
        var activationThenCancel = new CastingSessionController(profile());
        var first = activationThenCancel.accept("shape Astra bind Nora");
        assertEquals(CastingSessionController.State.CANCELLED, first.state());
        assertTrue(first.cancellationRequested());

        var cancelThenActivation = new CastingSessionController(profile());
        var second = cancelThenActivation.accept("shape Nora Astra bind");
        assertEquals(CastingSessionController.State.CANCELLED, second.state());
        assertFalse(second.cancellationRequested());
    }

    @Test
    void ambiguousInputFailsClosedAndCancelsAnActiveRuntime() {
        var overlapping = new CastingTriggerRecognizer.TriggerProfile(
                List.of("red blue"), List.of("blue flame"));
        var controller = new CastingSessionController(overlapping);
        controller.accept("begin red blue end");

        var result = controller.accept("red blue flame");

        assertEquals(CastingSessionController.State.FAILED, result.state());
        assertTrue(result.cancellationRequested());
        assertEquals("", result.payload());
    }

    @Test
    void completedSessionRequiresResetBeforeReuse() {
        var controller = new CastingSessionController(profile());
        controller.accept("shape Astra bind");

        var completed = controller.complete();
        var rejected = controller.accept("another Astra working");
        var reset = controller.reset();

        assertEquals(CastingSessionController.State.COMPLETED, completed.state());
        assertEquals(CastingSessionController.State.COMPLETED, rejected.state());
        assertEquals(CastingSessionController.State.IDLE, reset.state());
        assertFalse(reset.cancellationRequested());
        assertEquals("", reset.payload());
    }

    @Test
    void onlyRunningSessionCanComplete() {
        var controller = new CastingSessionController(profile());

        var result = controller.complete();

        assertEquals(CastingSessionController.State.IDLE, result.state());
        assertTrue(result.diagnostic().contains("Only a running cast"));
    }

    @Test
    void manualFailureSignalsCancellationWhenRuntimeIsActive() {
        var controller = new CastingSessionController(profile());
        controller.accept("shape Astra bind");

        var result = controller.fail("Runtime adapter failed.");

        assertEquals(CastingSessionController.State.FAILED, result.state());
        assertTrue(result.cancellationRequested());
        assertEquals("Runtime adapter failed.", result.diagnostic());
    }
}
