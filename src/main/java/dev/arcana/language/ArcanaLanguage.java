package dev.arcana.language;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * First independent language-engine slice for Arcana.
 *
 * Player-authored source is translated to canonical source, parsed into a
 * canonical AST, and never executed directly. This package has no Minecraft
 * dependencies so it can be unit-tested without starting the game.
 */
public final class ArcanaLanguage {
    public static final int VERSION = 1;
    private ArcanaLanguage() { }

    public static LexResult lex(String source) {
        return Lexer.scan(source);
    }

    public static ParseResult parse(String canonicalSource) {
        LexResult lexed = Lexer.scan(canonicalSource);
        if (lexed.hasErrors()) return new ParseResult(null, lexed.diagnostics());
        Parser parser = new Parser(lexed.tokens());
        AstProgram program = parser.parseProgram();
        return new ParseResult(program, parser.diagnostics);
    }

    public static TranslationResult translate(String source, LanguageDefinition language) {
        Objects.requireNonNull(language, "language");
        if (source == null) {
            return new TranslationResult(null, List.of(new Diagnostic(
                    Severity.ERROR, "TRANS_NULL_SOURCE", "Source cannot be null.",
                    SourceSpan.point(0, 1, 1))));
        }
        LexResult lexed = Lexer.scan(source);
        if (lexed.hasErrors()) return new TranslationResult(null, lexed.diagnostics());

        List<Token> tokens = lexed.tokens();
        StringBuilder output = new StringBuilder(source.length());
        int copiedUntil = 0;
        for (int i = 0; i < tokens.size() - 1;) {
            PhraseRule matched = null;
            int matchedLength = 0;
            for (PhraseRule rule : language.phraseRules()) {
                List<String> pattern = tokenPattern(rule.sourcePhrase());
                if (pattern.size() <= matchedLength || i + pattern.size() > tokens.size() - 1) continue;
                boolean same = true;
                for (int offset = 0; offset < pattern.size(); offset++) {
                    if (!pattern.get(offset).equals(tokenKey(tokens.get(i + offset)))) {
                        same = false;
                        break;
                    }
                }
                if (same) {
                    matched = rule;
                    matchedLength = pattern.size();
                }
            }
            if (matched != null) {
                Token first = tokens.get(i);
                Token last = tokens.get(i + matchedLength - 1);
                output.append(source, copiedUntil, first.span().startOffset());
                output.append(matched.canonicalReplacement());
                copiedUntil = last.span().endOffset();
                i += matchedLength;
                continue;
            }

            Token token = tokens.get(i++);
            if (token.type() != TokenType.IDENTIFIER) continue;
            String replacement = language.keywordAliases().get(token.lexeme().toLowerCase(Locale.ROOT));
            if (replacement == null) continue;
            output.append(source, copiedUntil, token.span().startOffset());
            output.append(replacement);
            copiedUntil = token.span().endOffset();
        }
        output.append(source, copiedUntil, source.length());
        if (output.length() > 65_536) {
            return new TranslationResult(null, List.of(new Diagnostic(
                    Severity.ERROR, "TRANS_OUTPUT_TOO_LARGE", "Translated output exceeds 65536 characters.",
                    SourceSpan.point(0, 1, 1))));
        }
        return new TranslationResult(output.toString(), List.of());
    }

    public enum Severity { INFO, WARNING, ERROR }

    public record SourceSpan(int startOffset, int endOffset, int line, int column) {
        public SourceSpan {
            if (startOffset < 0 || endOffset < startOffset || line < 1 || column < 1) {
                throw new IllegalArgumentException("Invalid source span");
            }
        }
        public static SourceSpan point(int offset, int line, int column) {
            return new SourceSpan(offset, offset, line, column);
        }
    }

    public record Diagnostic(Severity severity, String code, String message, SourceSpan span) {
        public Diagnostic {
            Objects.requireNonNull(severity);
            Objects.requireNonNull(code);
            Objects.requireNonNull(message);
            Objects.requireNonNull(span);
        }
    }

    public enum TokenType {
        IDENTIFIER, STRING, NUMBER,
        LEFT_BRACE, RIGHT_BRACE, LEFT_PAREN, RIGHT_PAREN,
        COLON, EQUALS, SEMICOLON, COMMA, EOF
    }

    public record Token(TokenType type, String lexeme, Object literal, SourceSpan span) { }

    public record LexResult(List<Token> tokens, List<Diagnostic> diagnostics) {
        public LexResult {
            tokens = List.copyOf(tokens);
            diagnostics = List.copyOf(diagnostics);
        }
        public boolean hasErrors() {
            return diagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR);
        }
    }

    public record TranslationResult(String canonicalSource, List<Diagnostic> diagnostics) {
        public TranslationResult { diagnostics = List.copyOf(diagnostics); }
        public boolean successful() {
            return canonicalSource != null
                    && diagnostics.stream().noneMatch(d -> d.severity() == Severity.ERROR);
        }
    }

    public record ParseResult(AstProgram program, List<Diagnostic> diagnostics) {
        public ParseResult { diagnostics = List.copyOf(diagnostics); }
        public boolean successful() {
            return program != null && diagnostics.stream().noneMatch(d -> d.severity() == Severity.ERROR);
        }
    }

    /** A phrase may expand to any canonical Arcana source fragment. */
    public record PhraseRule(String sourcePhrase, String canonicalReplacement) {
        public PhraseRule {
            Objects.requireNonNull(sourcePhrase);
            Objects.requireNonNull(canonicalReplacement);
            if (sourcePhrase.isBlank() || canonicalReplacement.isBlank()) {
                throw new IllegalArgumentException("Phrase rules cannot be blank.");
            }
            List<String> tokens = tokenPattern(sourcePhrase);
            if (tokens.isEmpty() || tokens.size() > 64) {
                throw new IllegalArgumentException("Phrase patterns must contain 1 to 64 tokens.");
            }
            if (tokens.stream().anyMatch(key -> !key.startsWith("IDENTIFIER:"))) {
                throw new IllegalArgumentException("Phrase patterns may contain words only; literals and punctuation are not rewritable.");
            }
        }
    }

    public record LanguageDefinition(String id, int version, Map<String, String> keywordAliases,
                                     List<PhraseRule> phraseRules) {
        private static final Set<String> CANONICAL = Set.of(
                "spell", "let", "invoke", "true", "false", "Number", "Text", "Boolean");

        public LanguageDefinition(String id, int version, Map<String, String> keywordAliases) {
            this(id, version, keywordAliases, List.of());
        }

        public LanguageDefinition {
            Objects.requireNonNull(id);
            Objects.requireNonNull(keywordAliases);
            phraseRules = List.copyOf(Objects.requireNonNull(phraseRules));
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                throw new IllegalArgumentException("Language id must be namespaced.");
            }
            if (version < 1) throw new IllegalArgumentException("Language version must be positive.");
            Map<String, String> normalized = new LinkedHashMap<>();
            keywordAliases.forEach((alias, canonical) -> {
                String key = alias.toLowerCase(Locale.ROOT);
                if (key.isBlank()) throw new IllegalArgumentException("Aliases cannot be blank.");
                if (!CANONICAL.contains(canonical)) {
                    throw new IllegalArgumentException("Unsupported canonical keyword: " + canonical);
                }
                if (normalized.putIfAbsent(key, canonical) != null) {
                    throw new IllegalArgumentException("Duplicate alias: " + alias);
                }
            });
            keywordAliases = Map.copyOf(normalized);
            Set<List<String>> patterns = new java.util.HashSet<>();
            for (PhraseRule rule : phraseRules) {
                List<String> pattern = tokenPattern(rule.sourcePhrase());
                if (!patterns.add(pattern)) {
                    throw new IllegalArgumentException("Conflicting duplicate phrase pattern: " + rule.sourcePhrase());
                }
                if (pattern.size() == 1 && pattern.getFirst().startsWith("IDENTIFIER:")) {
                    String alias = pattern.getFirst().substring("IDENTIFIER:".length());
                    if (keywordAliases.containsKey(alias)) {
                        throw new IllegalArgumentException("A phrase rule conflicts with a keyword alias: " + alias);
                    }
                }
            }
        }
    }

    private static List<String> tokenPattern(String phrase) {
        LexResult result = Lexer.scan(phrase);
        if (result.hasErrors()) throw new IllegalArgumentException("Invalid phrase pattern.");
        return result.tokens().stream().filter(token -> token.type() != TokenType.EOF)
                .map(ArcanaLanguage::tokenKey).toList();
    }

    private static String tokenKey(Token token) {
        String value = token.type() == TokenType.IDENTIFIER
                ? token.lexeme().toLowerCase(Locale.ROOT) : token.lexeme();
        return token.type().name() + ":" + value;
    }

    public sealed interface Statement permits LetStatement, InvokeStatement {
        SourceSpan span();
    }
    public sealed interface Expression permits LiteralExpression, NameExpression {
        SourceSpan span();
    }
    public record AstProgram(String name, List<Statement> statements, SourceSpan span) {
        public AstProgram {
            Objects.requireNonNull(name);
            statements = List.copyOf(statements);
            Objects.requireNonNull(span);
        }
    }
    public record LetStatement(String name, String declaredType, Expression initializer,
                               SourceSpan span) implements Statement { }
    public record InvokeStatement(String capabilityId, Map<String, Expression> arguments,
                                  SourceSpan span) implements Statement {
        public InvokeStatement { arguments = Map.copyOf(arguments); }
    }
    public record LiteralExpression(Object value, SourceSpan span) implements Expression { }
    public record NameExpression(String name, SourceSpan span) implements Expression { }

    private static final class Lexer {
        private static final int MAX_SOURCE_LENGTH = 65_536;
        private static final int MAX_TOKENS = 8_192;
        private final String source;
        private final List<Token> tokens = new ArrayList<>();
        private final List<Diagnostic> diagnostics = new ArrayList<>();
        private int start, current, line = 1, column = 1, startLine = 1, startColumn = 1;

        private Lexer(String source) { this.source = source; }

        static LexResult scan(String source) {
            if (source == null) return new LexResult(List.of(), List.of(new Diagnostic(
                    Severity.ERROR, "LEX_NULL_SOURCE", "Source cannot be null.",
                    SourceSpan.point(0, 1, 1))));
            if (source.length() > MAX_SOURCE_LENGTH) return new LexResult(List.of(), List.of(new Diagnostic(
                    Severity.ERROR, "LEX_SOURCE_TOO_LARGE", "Source exceeds 65536 characters.",
                    SourceSpan.point(0, 1, 1))));
            return new Lexer(source).scanTokens();
        }

        private LexResult scanTokens() {
            while (!atEnd() && tokens.size() < MAX_TOKENS) {
                start = current;
                startLine = line;
                startColumn = column;
                scanToken();
            }
            if (!atEnd()) diagnostics.add(error("LEX_TOO_MANY_TOKENS",
                    "Source exceeds 8192 tokens.", SourceSpan.point(current, line, column)));
            tokens.add(new Token(TokenType.EOF, "", null, SourceSpan.point(current, line, column)));
            return new LexResult(tokens, diagnostics);
        }

        private void scanToken() {
            char c = advance();
            switch (c) {
                case '{' -> add(TokenType.LEFT_BRACE);
                case '}' -> add(TokenType.RIGHT_BRACE);
                case '(' -> add(TokenType.LEFT_PAREN);
                case ')' -> add(TokenType.RIGHT_PAREN);
                case ':' -> add(TokenType.COLON);
                case '=' -> add(TokenType.EQUALS);
                case ';' -> add(TokenType.SEMICOLON);
                case ',' -> add(TokenType.COMMA);
                case ' ', '\r', '\t', '\n' -> { }
                case '"' -> scanString();
                case '/' -> {
                    if (match('/')) while (peek() != '\n' && !atEnd()) advance();
                    else diagnostics.add(error("LEX_UNEXPECTED_CHARACTER",
                            "Unexpected '/'. Use // for comments.", span()));
                }
                default -> {
                    if (digit(c)) scanNumber();
                    else if (identifierStart(c)) scanIdentifier();
                    else diagnostics.add(error("LEX_UNEXPECTED_CHARACTER",
                            "Unexpected character '" + c + "'.", span()));
                }
            }
        }

        private void scanString() {
            StringBuilder value = new StringBuilder();
            while (!atEnd() && peek() != '"') {
                char c = advance();
                if (c == '\n') {
                    diagnostics.add(error("LEX_NEWLINE_IN_STRING",
                            "String literals cannot contain an unescaped newline.", span()));
                    return;
                }
                if (c == '\\') {
                    if (atEnd()) break;
                    char escaped = advance();
                    switch (escaped) {
                        case '"' -> value.append('"');
                        case '\\' -> value.append('\\');
                        case 'n' -> value.append('\n');
                        case 't' -> value.append('\t');
                        default -> {
                            diagnostics.add(error("LEX_INVALID_ESCAPE",
                                    "Unsupported string escape.", span()));
                            value.append(escaped);
                        }
                    }
                } else value.append(c);
            }
            if (atEnd()) {
                diagnostics.add(error("LEX_UNTERMINATED_STRING", "Unterminated string literal.", span()));
                return;
            }
            advance();
            add(TokenType.STRING, value.toString());
        }

        private void scanNumber() {
            while (digit(peek())) advance();
            if (peek() == '.' && digit(peekNext())) {
                advance();
                while (digit(peek())) advance();
            }
            String text = source.substring(start, current);
            try { add(TokenType.NUMBER, new BigDecimal(text)); }
            catch (NumberFormatException ex) {
                diagnostics.add(error("LEX_INVALID_NUMBER", "Invalid number: " + text, span()));
            }
        }

        private void scanIdentifier() {
            while (identifierPart(peek())) advance();
            add(TokenType.IDENTIFIER);
        }

        private void add(TokenType type) { add(type, null); }
        private void add(TokenType type, Object literal) {
            tokens.add(new Token(type, source.substring(start, current), literal, span()));
        }
        private Diagnostic error(String code, String message, SourceSpan at) {
            return new Diagnostic(Severity.ERROR, code, message, at);
        }
        private SourceSpan span() { return new SourceSpan(start, current, startLine, startColumn); }
        private char advance() {
            char c = source.charAt(current++);
            if (c == '\n') { line++; column = 1; } else column++;
            return c;
        }
        private boolean match(char expected) {
            if (atEnd() || source.charAt(current) != expected) return false;
            advance();
            return true;
        }
        private char peek() { return atEnd() ? '\0' : source.charAt(current); }
        private char peekNext() { return current + 1 >= source.length() ? '\0' : source.charAt(current + 1); }
        private boolean atEnd() { return current >= source.length(); }
        private static boolean digit(char c) { return c >= '0' && c <= '9'; }
        private static boolean identifierStart(char c) { return c == '_' || Character.isLetter(c); }
        private static boolean identifierPart(char c) { return identifierStart(c) || digit(c); }
    }

    private static final class Parser {
        private final List<Token> tokens;
        private final List<Diagnostic> diagnostics = new ArrayList<>();
        private int current;
        private Parser(List<Token> tokens) { this.tokens = tokens; }

        private AstProgram parseProgram() {
            Token start = peek();
            if (!matchKeyword("spell")) {
                report(peek(), "PARSE_EXPECTED_SPELL", "Program must begin with the spell keyword.");
                return null;
            }
            Token name = consume(TokenType.STRING, "PARSE_EXPECTED_NAME", "Expected a quoted spell name.");
            consume(TokenType.LEFT_BRACE, "PARSE_EXPECTED_BLOCK", "Expected '{' to begin spell body.");
            List<Statement> statements = new ArrayList<>();
            while (!check(TokenType.RIGHT_BRACE) && !check(TokenType.EOF)) {
                int before = current;
                Statement statement = parseStatement();
                if (statement != null) statements.add(statement);
                if (current == before) advance();
            }
            Token end = consume(TokenType.RIGHT_BRACE, "PARSE_EXPECTED_BLOCK_END", "Expected '}' to close spell.");
            consume(TokenType.EOF, "PARSE_TRAILING_INPUT", "Unexpected content after spell.");
            if (name == null || end == null) return null;
            return new AstProgram((String) name.literal(), statements, merge(start.span(), end.span()));
        }

        private Statement parseStatement() {
            if (matchKeyword("let")) return parseLet(previous());
            if (matchKeyword("invoke")) return parseInvoke(previous());
            report(peek(), "PARSE_UNKNOWN_STATEMENT", "Expected let or invoke statement.");
            synchronize();
            return null;
        }

        private Statement parseLet(Token start) {
            Token name = consume(TokenType.IDENTIFIER, "PARSE_EXPECTED_VARIABLE", "Expected variable name.");
            consume(TokenType.COLON, "PARSE_EXPECTED_TYPE_SEPARATOR", "Expected ':' before type.");
            Token type = consume(TokenType.IDENTIFIER, "PARSE_EXPECTED_TYPE", "Expected type name.");
            consume(TokenType.EQUALS, "PARSE_EXPECTED_EQUALS", "Expected '=' before initializer.");
            Expression value = expression();
            Token end = consume(TokenType.SEMICOLON, "PARSE_EXPECTED_SEMICOLON", "Expected ';'.");
            if (name == null || type == null || value == null || end == null) return null;
            return new LetStatement(name.lexeme(), type.lexeme(), value, merge(start.span(), end.span()));
        }

        private Statement parseInvoke(Token start) {
            Token capability = consume(TokenType.STRING, "PARSE_EXPECTED_CAPABILITY",
                    "Expected quoted capability identifier.");
            consume(TokenType.LEFT_PAREN, "PARSE_EXPECTED_ARGUMENTS", "Expected '(' after capability.");
            Map<String, Expression> arguments = new LinkedHashMap<>();
            if (!check(TokenType.RIGHT_PAREN)) {
                do {
                    Token key = consume(TokenType.IDENTIFIER, "PARSE_EXPECTED_ARGUMENT_NAME",
                            "Expected argument name.");
                    consume(TokenType.EQUALS, "PARSE_EXPECTED_ARGUMENT_EQUALS", "Expected '=' after argument name.");
                    Expression value = expression();
                    if (key != null && value != null && arguments.putIfAbsent(key.lexeme(), value) != null) {
                        report(key, "PARSE_DUPLICATE_ARGUMENT", "Duplicate argument: " + key.lexeme());
                    }
                } while (match(TokenType.COMMA));
            }
            consume(TokenType.RIGHT_PAREN, "PARSE_EXPECTED_ARGUMENT_END", "Expected ')' after arguments.");
            Token end = consume(TokenType.SEMICOLON, "PARSE_EXPECTED_SEMICOLON", "Expected ';'.");
            if (capability == null || end == null) return null;
            return new InvokeStatement((String) capability.literal(), arguments, merge(start.span(), end.span()));
        }

        private Expression expression() {
            if (match(TokenType.STRING, TokenType.NUMBER)) {
                Token token = previous();
                return new LiteralExpression(token.literal(), token.span());
            }
            if (matchKeyword("true")) return new LiteralExpression(Boolean.TRUE, previous().span());
            if (matchKeyword("false")) return new LiteralExpression(Boolean.FALSE, previous().span());
            if (match(TokenType.IDENTIFIER)) {
                Token token = previous();
                return new NameExpression(token.lexeme(), token.span());
            }
            report(peek(), "PARSE_EXPECTED_EXPRESSION", "Expected literal or variable reference.");
            if (!check(TokenType.EOF) && !check(TokenType.RIGHT_BRACE)) advance();
            return null;
        }

        private void synchronize() {
            while (!check(TokenType.EOF)) {
                if (previous().type() == TokenType.SEMICOLON || check(TokenType.RIGHT_BRACE)
                        || checkKeyword("let") || checkKeyword("invoke")) return;
                advance();
            }
        }
        private Token consume(TokenType type, String code, String message) {
            if (check(type)) return advance();
            report(peek(), code, message);
            return null;
        }
        private boolean matchKeyword(String keyword) {
            if (!checkKeyword(keyword)) return false;
            advance();
            return true;
        }
        private boolean checkKeyword(String keyword) {
            return check(TokenType.IDENTIFIER) && peek().lexeme().equals(keyword);
        }
        private boolean match(TokenType... types) {
            for (TokenType type : types) if (check(type)) { advance(); return true; }
            return false;
        }
        private boolean check(TokenType type) { return peek().type() == type; }
        private Token advance() { if (!check(TokenType.EOF)) current++; return previous(); }
        private Token peek() { return tokens.get(Math.min(current, tokens.size() - 1)); }
        private Token previous() { return tokens.get(Math.max(0, current - 1)); }
        private void report(Token token, String code, String message) {
            diagnostics.add(new Diagnostic(Severity.ERROR, code, message, token.span()));
        }
        private static SourceSpan merge(SourceSpan a, SourceSpan b) {
            return new SourceSpan(a.startOffset(), b.endOffset(), a.line(), a.column());
        }
    }
}
