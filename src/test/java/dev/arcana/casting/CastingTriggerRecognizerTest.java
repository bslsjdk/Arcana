package dev.arcana.casting;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CastingTriggerRecognizerTest {
    private static CastingTriggerRecognizer.TriggerProfile profile() {
        return new CastingTriggerRecognizer.TriggerProfile(
                List.of("Astra", "by the silver flame"),
                List.of("Nora", "the rite is ended"));
    }

    @Test
    void activationCanAppearAtTheBeginning() {
        var result = CastingTriggerRecognizer.recognize(
                "Astra shape the light", profile());

        assertEquals(CastingTriggerRecognizer.Status.ACTIVATED, result.status());
        assertEquals("shape the light", result.payload());
        assertTrue(result.mayStartCasting());
    }

    @Test
    void activationCanAppearInTheMiddleAndPreservesBothSides() {
        var result = CastingTriggerRecognizer.recognize(
                "shape the light Astra bind it to the crystal", profile());

        assertEquals(CastingTriggerRecognizer.Status.ACTIVATED, result.status());
        assertEquals("shape the light bind it to the crystal", result.payload());
        assertTrue(result.activationDetected());
    }

    @Test
    void activationCanAppearAtTheEnd() {
        var result = CastingTriggerRecognizer.recognize(
                "shape the light Astra", profile());

        assertEquals(CastingTriggerRecognizer.Status.ACTIVATED, result.status());
        assertEquals("shape the light", result.payload());
    }

    @Test
    void cancellationAfterActivationCancelsTheCastAndTruncatesTheBatch() {
        var result = CastingTriggerRecognizer.recognize(
                "shape Astra bind Nora ignore everything after this", profile());

        assertEquals(CastingTriggerRecognizer.Status.CANCELLED, result.status());
        assertTrue(result.cancelledAnActivatedCast());
        assertEquals("shape bind", result.payload());
        assertFalse(result.mayStartCasting());
    }

    @Test
    void cancellationBeforeActivationDiscardsThePendingIncantation() {
        var result = CastingTriggerRecognizer.recognize(
                "shape Nora Astra bind", profile());

        assertEquals(CastingTriggerRecognizer.Status.CANCELLED, result.status());
        assertFalse(result.activationDetected());
        assertEquals("", result.payload());
    }

    @Test
    void noActivationLeavesTheIncantationPreparedButNotExecutable() {
        var result = CastingTriggerRecognizer.recognize(
                "shape the light and bind it", profile());

        assertEquals(CastingTriggerRecognizer.Status.PREPARED, result.status());
        assertEquals("shape the light and bind it", result.payload());
        assertFalse(result.mayStartCasting());
    }

    @Test
    void matchingIsCaseInsensitiveAndUsesWholeTokens() {
        var exact = CastingTriggerRecognizer.recognize(
                "shape ASTRA bind", profile());
        var substring = CastingTriggerRecognizer.recognize(
                "shape Astralia bind", profile());

        assertEquals(CastingTriggerRecognizer.Status.ACTIVATED, exact.status());
        assertEquals(CastingTriggerRecognizer.Status.PREPARED, substring.status());
    }

    @Test
    void supportsMultiWordActivationAndCancellationPhrases() {
        var activated = CastingTriggerRecognizer.recognize(
                "shape BY the SILVER flame bind", profile());
        var cancelled = CastingTriggerRecognizer.recognize(
                "shape Astra bind THE RITE IS ENDED trailing", profile());

        assertEquals(CastingTriggerRecognizer.Status.ACTIVATED, activated.status());
        assertEquals("shape bind", activated.payload());
        assertEquals(CastingTriggerRecognizer.Status.CANCELLED, cancelled.status());
        assertTrue(cancelled.cancelledAnActivatedCast());
    }

    @Test
    void cancellationWinsWhenItAppearsBeforeActivation() {
        var result = CastingTriggerRecognizer.recognize(
                "Nora Astra", profile());

        assertEquals(CastingTriggerRecognizer.Status.CANCELLED, result.status());
        assertFalse(result.activationDetected());
        assertFalse(result.mayStartCasting());
    }

    @Test
    void overlappingActivationAndCancellationPhrasesFailClosed() {
        var overlapping = new CastingTriggerRecognizer.TriggerProfile(
                List.of("red blue"), List.of("blue flame"));
        var result = CastingTriggerRecognizer.recognize(
                "begin red blue flame", overlapping);

        assertEquals(CastingTriggerRecognizer.Status.AMBIGUOUS, result.status());
        assertFalse(result.mayStartCasting());
    }

    @Test
    void detectsCrossKindOverlapEvenWhenSameKindMatchIsNestedBetweenThem() {
        var overlapping = new CastingTriggerRecognizer.TriggerProfile(
                List.of("red blue green yellow", "blue"),
                List.of("green yellow"));
        var result = CastingTriggerRecognizer.recognize(
                "begin red blue green yellow end", overlapping);

        assertEquals(CastingTriggerRecognizer.Status.AMBIGUOUS, result.status());
        assertFalse(result.mayStartCasting());
    }

    @Test
    void profileRejectsIdenticalActivationAndCancellationPhrases() {
        assertThrows(IllegalArgumentException.class, () ->
                new CastingTriggerRecognizer.TriggerProfile(
                        List.of("Astra"), List.of("astra")));
    }

    @Test
    void profileRejectsEmptyOrOversizedTriggerPhrases() {
        assertThrows(IllegalArgumentException.class, () ->
                new CastingTriggerRecognizer.TriggerProfile(List.of(" "), List.of("Nora")));
        assertThrows(IllegalArgumentException.class, () ->
                new CastingTriggerRecognizer.TriggerProfile(
                        List.of("one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen"),
                        List.of("Nora")));
    }

    @Test
    void rejectsOversizedInputWithoutTriggering() {
        var result = CastingTriggerRecognizer.recognize(
                "x".repeat(CastingTriggerRecognizer.MAX_SOURCE_LENGTH + 1), profile());

        assertEquals(CastingTriggerRecognizer.Status.INVALID_INPUT, result.status());
        assertFalse(result.mayStartCasting());
    }

    @Test
    void unicodeCompatibilityNormalizationIsAppliedToTriggerTokens() {
        var result = CastingTriggerRecognizer.recognize(
                "begin ＡＳＴＲＡ finish", profile());

        assertEquals(CastingTriggerRecognizer.Status.ACTIVATED, result.status());
    }
}
