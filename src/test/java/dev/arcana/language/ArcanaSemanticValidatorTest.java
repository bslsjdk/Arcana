package dev.arcana.language;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ArcanaSemanticValidatorTest {
    @Test
    void acceptsWellTypedSequentialProgram() {
        var parsed = ArcanaLanguage.parse("""
                spell "typed" {
                    let power: Number = 2;
                    let label: Text = "ember";
                    let enabled: Boolean = true;
                    invoke "arcana:effect/ignite"(power = power, label = label, enabled = enabled);
                }
                """);
        assertTrue(parsed.successful(), () -> parsed.diagnostics().toString());

        var checked = ArcanaSemanticValidator.validate(parsed.program());
        assertTrue(checked.successful(), () -> checked.diagnostics().toString());
    }

    @Test
    void rejectsUndefinedVariableReferences() {
        var parsed = ArcanaLanguage.parse("""
                spell "bad" {
                    invoke "arcana:effect/ignite"(power = missing);
                }
                """);
        assertTrue(parsed.successful(), () -> parsed.diagnostics().toString());

        var checked = ArcanaSemanticValidator.validate(parsed.program());
        assertFalse(checked.successful());
        assertTrue(checked.diagnostics().stream()
                .anyMatch(d -> d.code().equals("SEM_UNDEFINED_VARIABLE")));
    }

    @Test
    void rejectsTypeMismatchAndDuplicateDeclarations() {
        var parsed = ArcanaLanguage.parse("""
                spell "bad" {
                    let power: Number = "hot";
                    let power: Text = "again";
                }
                """);
        assertTrue(parsed.successful(), () -> parsed.diagnostics().toString());

        var checked = ArcanaSemanticValidator.validate(parsed.program());
        assertFalse(checked.successful());
        assertTrue(checked.diagnostics().stream()
                .anyMatch(d -> d.code().equals("SEM_TYPE_MISMATCH")));
        assertTrue(checked.diagnostics().stream()
                .anyMatch(d -> d.code().equals("SEM_DUPLICATE_VARIABLE")));
    }

    @Test
    void rejectsMalformedCapabilityIdsAtSemanticBoundary() {
        var parsed = ArcanaLanguage.parse("""
                spell "bad" {
                    invoke "not a capability id"(target = "self");
                }
                """);
        assertTrue(parsed.successful(), () -> parsed.diagnostics().toString());

        var checked = ArcanaSemanticValidator.validate(parsed.program());
        assertFalse(checked.successful());
        assertTrue(checked.diagnostics().stream()
                .anyMatch(d -> d.code().equals("SEM_INVALID_CAPABILITY_ID")));
    }
}
