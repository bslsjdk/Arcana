package dev.arcana.casting;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Recognizes player-defined activation and cancellation phrases embedded in an
 * incantation. This class is deliberately independent of Minecraft and does
 * not start, stop, or execute a cast.
 */
public final class CastingTriggerRecognizer {
    public static final int MAX_SOURCE_LENGTH = 65_536;
    public static final int MAX_TOKENS = 8_192;
    public static final int MAX_TRIGGER_TOKENS = 16;
    public static final int MAX_TRIGGERS_PER_KIND = 32;

    private CastingTriggerRecognizer() { }

    public record TriggerProfile(
            List<String> activationPhrases,
            List<String> cancellationPhrases) {
        public TriggerProfile {
            activationPhrases = compilePhrases(activationPhrases, "activation");
            cancellationPhrases = compilePhrases(cancellationPhrases, "cancellation");
            if (activationPhrases.isEmpty() || cancellationPhrases.isEmpty()) {
                throw new IllegalArgumentException(
                        "A trigger profile requires at least one activation and one cancellation phrase.");
            }
            Set<List<String>> all = new HashSet<>();
            for (String phrase : activationPhrases) {
                if (!all.add(tokenize(phrase).stream().map(Token::normalized).toList())) {
                    throw new IllegalArgumentException("Duplicate activation phrase: " + phrase);
                }
            }
            for (String phrase : cancellationPhrases) {
                if (!all.add(tokenize(phrase).stream().map(Token::normalized).toList())) {
                    throw new IllegalArgumentException(
                            "Activation and cancellation phrases must be distinct: " + phrase);
                }
            }
        }

        private static List<String> compilePhrases(List<String> phrases, String kind) {
            Objects.requireNonNull(phrases, kind + "Phrases");
            if (phrases.size() > MAX_TRIGGERS_PER_KIND) {
                throw new IllegalArgumentException("Too many " + kind + " phrases.");
            }
            List<String> copy = new ArrayList<>();
            for (String phrase : phrases) {
                Objects.requireNonNull(phrase, kind + " phrase");
                if (phrase.isBlank() || phrase.length() > 256) {
                    throw new IllegalArgumentException("Trigger phrases must contain 1 to 256 characters.");
                }
                List<Token> tokens = tokenize(phrase);
                if (tokens.isEmpty() || tokens.size() > MAX_TRIGGER_TOKENS) {
                    throw new IllegalArgumentException("Trigger phrases must contain 1 to 16 words.");
                }
                copy.add(phrase);
            }
            return List.copyOf(copy);
        }
    }

    public enum Status {
        PREPARED,
        ACTIVATED,
        CANCELLED,
        INVALID_INPUT,
        AMBIGUOUS
    }

    public record Result(
            Status status,
            String payload,
            boolean activationDetected,
            boolean cancellationDetected,
            int activationTokenIndex,
            int cancellationTokenIndex,
            String diagnostic) {
        public Result {
            Objects.requireNonNull(status);
            payload = Objects.requireNonNull(payload);
        }
        public boolean mayStartCasting() {
            return status == Status.ACTIVATED;
        }
        public boolean cancelledAnActivatedCast() {
            return status == Status.CANCELLED && activationDetected;
        }
    }

    private record Token(String original, String normalized, int start, int end) { }
    private record Match(int startToken, int endTokenExclusive, boolean activation) { }

    /**
     * Analyzes one complete recognized input batch. Activation can occur at any
     * token position. A cancellation marker terminates the batch and discards
     * everything after it. Trigger phrases are removed from the payload.
     */
    public static Result recognize(String source, TriggerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        if (source == null) {
            return invalid("Input cannot be null.");
        }
        if (source.length() > MAX_SOURCE_LENGTH) {
            return invalid("Input exceeds the 65536-character limit.");
        }

        List<Token> tokens = tokenize(source);
        if (tokens.size() > MAX_TOKENS) {
            return invalid("Input exceeds the 8192-token limit.");
        }

        List<List<String>> activation = normalizedPhrases(profile.activationPhrases());
        List<List<String>> cancellation = normalizedPhrases(profile.cancellationPhrases());
        List<Match> matches = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            Match activationMatch = longestMatch(tokens, i, activation, true);
            Match cancellationMatch = longestMatch(tokens, i, cancellation, false);
            if (activationMatch != null && cancellationMatch != null) {
                return ambiguous("The same token position matches activation and cancellation.");
            }
            if (activationMatch != null) matches.add(activationMatch);
            if (cancellationMatch != null) matches.add(cancellationMatch);
        }

        matches.sort((a, b) -> {
            int byStart = Integer.compare(a.startToken(), b.startToken());
            if (byStart != 0) return byStart;
            return Integer.compare(b.endTokenExclusive(), a.endTokenExclusive());
        });

        for (int i = 1; i < matches.size(); i++) {
            Match previous = matches.get(i - 1);
            Match current = matches.get(i);
            if (current.startToken() < previous.endTokenExclusive()
                    && current.activation() != previous.activation()) {
                return ambiguous("Activation and cancellation phrases overlap.");
            }
        }

        Match firstActivation = matches.stream().filter(Match::activation).findFirst().orElse(null);
        Match firstCancellation = matches.stream().filter(match -> !match.activation())
                .findFirst().orElse(null);

        int activationIndex = firstActivation == null ? -1 : firstActivation.startToken();
        int cancellationIndex = firstCancellation == null ? -1 : firstCancellation.startToken();

        if (firstCancellation != null
                && (firstActivation == null || cancellationIndex < activationIndex)) {
            return new Result(Status.CANCELLED, "", firstActivation != null, true,
                    activationIndex, cancellationIndex,
                    "Cancellation occurred before activation; pending incantation discarded.");
        }

        int payloadEndToken = firstCancellation == null
                ? tokens.size() : firstCancellation.startToken();
        int payloadEndOffset = payloadEndToken == 0
                ? 0 : tokens.get(payloadEndToken - 1).end();
        String payload = removeActivationMarkers(source, tokens, matches,
                firstCancellation == null ? tokens.size() : firstCancellation.startToken(),
                payloadEndOffset);

        if (firstCancellation != null) {
            return new Result(Status.CANCELLED, payload, true, true,
                    activationIndex, cancellationIndex,
                    "Cancellation followed activation; the active casting session must be aborted.");
        }
        if (firstActivation != null) {
            return new Result(Status.ACTIVATED, payload, true, false,
                    activationIndex, -1, null);
        }
        return new Result(Status.PREPARED, source, false, false,
                -1, -1, null);
    }

    private static String removeActivationMarkers(
            String source, List<Token> tokens, List<Match> matches,
            int endTokenExclusive, int endOffset) {
        StringBuilder result = new StringBuilder(source.length());
        int copiedUntil = 0;
        for (Match match : matches) {
            if (!match.activation() || match.startToken() >= endTokenExclusive) continue;
            int start = tokens.get(match.startToken()).start();
            int end = tokens.get(match.endTokenExclusive() - 1).end();
            if (start < copiedUntil || end > endOffset) continue;
            result.append(source, copiedUntil, start);
            result.append(' ');
            copiedUntil = end;
        }
        result.append(source, copiedUntil, endOffset);
        return result.toString().trim();
    }

    private static Match longestMatch(
            List<Token> tokens, int start, List<List<String>> phrases, boolean activation) {
        List<String> longest = null;
        for (List<String> phrase : phrases) {
            if (phrase.size() <= (longest == null ? 0 : longest.size())
                    || start + phrase.size() > tokens.size()) continue;
            boolean same = true;
            for (int offset = 0; offset < phrase.size(); offset++) {
                if (!tokens.get(start + offset).normalized().equals(phrase.get(offset))) {
                    same = false;
                    break;
                }
            }
            if (same) longest = phrase;
        }
        return longest == null ? null
                : new Match(start, start + longest.size(), activation);
    }

    private static List<List<String>> normalizedPhrases(List<String> phrases) {
        return phrases.stream()
                .map(phrase -> tokenize(phrase).stream().map(Token::normalized).toList())
                .toList();
    }

    private static List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        while (index < source.length()) {
            int codePoint = source.codePointAt(index);
            if (!Character.isLetterOrDigit(codePoint) && codePoint != '_') {
                index += Character.charCount(codePoint);
                continue;
            }
            int start = index;
            StringBuilder word = new StringBuilder();
            while (index < source.length()) {
                int current = source.codePointAt(index);
                if (!Character.isLetterOrDigit(current) && current != '_') break;
                word.appendCodePoint(current);
                index += Character.charCount(current);
            }
            String original = word.toString();
            String normalized = Normalizer.normalize(original, Normalizer.Form.NFKC)
                    .toLowerCase(Locale.ROOT);
            tokens.add(new Token(original, normalized, start, index));
            if (tokens.size() > MAX_TOKENS) return tokens;
        }
        return tokens;
    }

    private static Result invalid(String diagnostic) {
        return new Result(Status.INVALID_INPUT, "", false, false, -1, -1, diagnostic);
    }

    private static Result ambiguous(String diagnostic) {
        return new Result(Status.AMBIGUOUS, "", false, false, -1, -1, diagnostic);
    }
}
