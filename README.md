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
- a bounded canonical-language lexer and recursive-descent parser;
- a versioned player-language keyword alias translator;
- a canonical AST with source spans and structured diagnostics;
- a semantic validation pass for primitive types, sequential variable declarations, duplicate declarations, undefined references, and capability identifier format;
- unit tests for parsing, translation equivalence, semantic validation, and malformed input.

The v0.1 translator currently maps vocabulary aliases for canonical keywords. It does **not yet** support arbitrary grammar productions, word-order changes, inflection, or syntax templates. Those remain a later language-engine milestone. The semantic validator is static analysis only: it does not execute capabilities or mutate the Minecraft world.\n\nA first loader-independent casting-rules prototype now models caster rank, spell rank, medium capacity, and direct versus ritual casting. The initial casting-media design is documented in [Casting System Design](docs/casting/SYSTEM_DESIGN.md). The grimoire, focus crystal, and scroll are registered as inert items in an Arcana creative tab, with placeholder vanilla models. Spellbook UI, spell storage, magic-circle blocks, learning, mana handling, and actual spell execution are **not implemented yet**. The next gameplay milestone is a usable grimoire + focus crystal + one harmless test spell.

## Compilation pipeline

`player source → language translation → canonical lexer/parser → canonical AST → semantic validation → capability resolution → bounded execution`

Only the canonical AST may proceed beyond validation. Player-authored source and language definitions are untrusted input.

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
