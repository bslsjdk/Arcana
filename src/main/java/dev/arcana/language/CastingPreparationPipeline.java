package dev.arcana.language;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic front-end pipeline for untrusted player incantation text.
 * It translates, parses, and statically validates without executing a capability.
 */
public final class CastingPreparationPipeline {
    private CastingPreparationPipeline() { }

    public enum Status {
        PREPARED,
        TRANSLATION_FAILED,
        PARSE_FAILED,
        SEMANTIC_FAILED,
        INVALID_REQUEST
    }

    public record Result(
            Status status,
            String canonicalSource,
            ArcanaLanguage.AstProgram program,
            List<ArcanaLanguage.Diagnostic> diagnostics) {
        public Result {
            Objects.requireNonNull(status);
            diagnostics = List.copyOf(diagnostics);
        }
        public boolean successful() {
            return status == Status.PREPARED && program != null;
        }
    }

    /**
     * Converts a trigger-stripped player-language payload into a validated AST.
     * The result is data only and is never an executable permission grant.
     */
    public static Result prepare(
            String source,
            ArcanaLanguage.LanguageDefinition language) {
        if (source == null || language == null) {
            return new Result(Status.INVALID_REQUEST, null, null, List.of(
                    new ArcanaLanguage.Diagnostic(
                            ArcanaLanguage.Severity.ERROR,
                            "CAST_PREPARE_INVALID_REQUEST",
                            "Source and language definition are required.",
                            ArcanaLanguage.SourceSpan.point(0, 1, 1))));
        }

        ArcanaLanguage.TranslationResult translated = ArcanaLanguage.translate(source, language);
        if (!translated.successful()) {
            return new Result(Status.TRANSLATION_FAILED, null, null, translated.diagnostics());
        }

        ArcanaLanguage.ParseResult parsed = ArcanaLanguage.parse(translated.canonicalSource());
        if (!parsed.successful()) {
            return new Result(Status.PARSE_FAILED, translated.canonicalSource(),
                    null, parsed.diagnostics());
        }

        ArcanaSemanticValidator.ValidationResult semantic =
                ArcanaSemanticValidator.validate(parsed.program());
        if (!semantic.successful()) {
            return new Result(Status.SEMANTIC_FAILED, translated.canonicalSource(),
                    parsed.program(), semantic.diagnostics());
        }

        return new Result(Status.PREPARED, translated.canonicalSource(),
                parsed.program(), List.of());
    }
}
