package dev.arcana.language;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Static semantic checks for canonical spell ASTs.
 *
 * This stage deliberately does not execute capabilities. It verifies local
 * declarations, expression references, primitive types, and capability IDs.
 */
public final class ArcanaSemanticValidator {
    private ArcanaSemanticValidator() { }

    public static ValidationResult validate(ArcanaLanguage.AstProgram program) {
        Objects.requireNonNull(program, "program");
        List<ArcanaLanguage.Diagnostic> diagnostics = new ArrayList<>();
        Map<String, String> symbols = new LinkedHashMap<>();

        for (ArcanaLanguage.Statement statement : program.statements()) {
            if (statement instanceof ArcanaLanguage.LetStatement let) {
                if (symbols.containsKey(let.name())) {
                    error(diagnostics, "SEM_DUPLICATE_VARIABLE",
                            "Variable is already declared: " + let.name(), let.span());
                    continue;
                }

                String declared = let.declaredType();
                if (!isSupportedType(declared)) {
                    error(diagnostics, "SEM_UNKNOWN_TYPE",
                            "Unsupported type: " + declared, let.span());
                    continue;
                }

                String actual = expressionType(let.initializer(), symbols, diagnostics);
                if (actual != null && !declared.equals(actual)) {
                    error(diagnostics, "SEM_TYPE_MISMATCH",
                            "Cannot assign " + actual + " to " + declared + ".", let.initializer().span());
                    continue;
                }
                if (actual != null) symbols.put(let.name(), declared);
            } else if (statement instanceof ArcanaLanguage.InvokeStatement invoke) {
                if (!invoke.capabilityId().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                    error(diagnostics, "SEM_INVALID_CAPABILITY_ID",
                            "Capability ID must use namespace:path format.", invoke.span());
                }
                invoke.arguments().values().forEach(
                        expression -> expressionType(expression, symbols, diagnostics));
            }
        }
        return new ValidationResult(diagnostics);
    }

    private static String expressionType(
            ArcanaLanguage.Expression expression,
            Map<String, String> symbols,
            List<ArcanaLanguage.Diagnostic> diagnostics) {
        if (expression instanceof ArcanaLanguage.LiteralExpression literal) {
            Object value = literal.value();
            if (value instanceof BigDecimal) return "Number";
            if (value instanceof String) return "Text";
            if (value instanceof Boolean) return "Boolean";
        } else if (expression instanceof ArcanaLanguage.NameExpression name) {
            String type = symbols.get(name.name());
            if (type == null) {
                error(diagnostics, "SEM_UNDEFINED_VARIABLE",
                        "Variable is not declared before use: " + name.name(), name.span());
            }
            return type;
        }
        error(diagnostics, "SEM_UNSUPPORTED_EXPRESSION",
                "Expression type cannot be determined.", expression.span());
        return null;
    }

    private static boolean isSupportedType(String type) {
        return type.equals("Number") || type.equals("Text") || type.equals("Boolean");
    }

    private static void error(List<ArcanaLanguage.Diagnostic> diagnostics,
                              String code, String message, ArcanaLanguage.SourceSpan span) {
        diagnostics.add(new ArcanaLanguage.Diagnostic(
                ArcanaLanguage.Severity.ERROR, code, message, span));
    }

    public record ValidationResult(List<ArcanaLanguage.Diagnostic> diagnostics) {
        public ValidationResult {
            diagnostics = List.copyOf(diagnostics);
        }
        public boolean successful() {
            return diagnostics.stream().noneMatch(
                    diagnostic -> diagnostic.severity() == ArcanaLanguage.Severity.ERROR);
        }
    }
}
