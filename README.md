# Arcana

**Arcana** is a Java Edition Minecraft magic mod for Minecraft 1.21.1, NeoForge 21.1.264, and Java 21.

Its defining idea is that players may invent their own magical languages. Those languages are translated into one canonical, versioned Arcana language, then parsed and validated by a shared engine. The player's original text is never executed directly.

The long-term goal is not merely a collection of spells. Arcana aims to provide a programmable magic framework for spells, rituals, alchemy, artifacts, magical machines, creatures, and custom dimensions.

## Design pillars

1. Player expression is open: custom vocabulary, symbols, grammar, and syntax.
2. Execution is canonical: every language targets the same standard representation.
3. Meaning is explicit: unknown or ambiguous input is rejected, never guessed.
4. Power comes from composition: typed operations, reusable modules, and complex programs.
5. Capabilities are extensible through documented mod APIs.
6. Execution is bounded, permission-checked, and server-authoritative.
7. Complexity comes from meaningful rules and dependencies, not decorative text.

## Current implementation

The first implementation branch establishes:
- a NeoForge 1.21.1 / Java 21 project;
- a bounded canonical-language lexer;
- a recursive-descent parser for the initial spell syntax;
- a versioned player-language keyword alias translator;
- unit tests for parsing, translation equivalence, and malformed input.

The v0.1 translator currently maps vocabulary aliases for canonical keywords. It does **not yet** support arbitrary grammar rewrites or word-order changes. Those are planned next; the engine must never guess missing grammar or meaning.

See:
- [Architecture](docs/ARCHITECTURE.md)
- [Canonical grammar v0.1](docs/language/CANONICAL_GRAMMAR.md)

## Build

Requires a 64-bit JDK 21.

- Windows: `gradlew.bat build`
- Linux/macOS: `./gradlew build`

The first Gradle setup downloads Minecraft and NeoForge development dependencies and may take a while.

## Safety and compatibility policy

- No arbitrary Java, reflection, class loading, filesystem, network, or command execution from spell source.
- Player-authored language definitions and spellbooks are untrusted input.
- Language definitions, canonical syntax, and saved programs are versioned.
- All world mutations must pass through server-side capabilities, permissions, resource checks, and execution budgets.
