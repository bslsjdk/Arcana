# Arcana

**Arcana** is a Java Edition Minecraft magic mod for Minecraft 1.21.1, NeoForge 21.1.264, and Java 21.

Arcana is about creating magic rather than selecting spells from a fixed catalogue. Players can invent magical languages and construct workings from runes, glyphs, invocations, patterns, and mana pathways. Their languages translate into one canonical, versioned Arcana representation, which is parsed and validated by a shared engine.

The long-term goal is a programmable magic framework for workings, rituals, alchemy, artifacts, magical machines, creatures, structures, and custom dimensions.

## Design pillars

1. **Player expression:** custom vocabulary, symbols, grammar, syntax, and magical constructions.
2. **One canonical meaning:** every player language targets the same standard representation.
3. **Magical authoring:** player-facing concepts are runes, glyphs, invocations, mana veins, nexuses, wards, sigils, and weaves.
4. **Host independence:** a weave is a logical structure; pages, items, blocks, and multi-part structures are possible physical hosts.
5. **Explicit meaning:** unknown or ambiguous input is rejected, never guessed.
6. **Composable complexity:** reusable fragments, typed paths, conditions, branches, and bounded repetition.
7. **Safe execution:** registered capabilities, explicit budgets, permission checks, and server authority.

## Current implementation

The first implementation branch establishes:

- a NeoForge 1.21.1 / Java 21 project;
- a bounded canonical-language lexer and recursive-descent parser;
- a versioned player-language keyword-alias translator;
- a canonical AST with source spans and structured diagnostics;
- static semantic validation for primitive types, sequential variable declarations, duplicate declarations, undefined references, and capability identifier format;
- unit tests for parsing, translation equivalence, semantic validation, and malformed input;
- a loader-independent casting-rules prototype;
- registered grimoire, focus crystal, and scroll items;
- an initial typed graph prototype, currently an internal foundation rather than a finished magical-weave editor.

The v0.1 translator currently maps vocabulary aliases for canonical keywords. It does **not yet** support arbitrary grammar productions, word-order changes, inflection, or syntax templates. The semantic validator is static analysis only: it does not execute capabilities or mutate the Minecraft world.

The grimoire, focus crystal, and scroll are registered as inert items in an Arcana creative tab, with placeholder vanilla models. Spellbook UI, persistent host installations, mana handling, and actual spell execution are **not implemented yet**.

## Compilation pipeline

Player-authored language and source are translated into canonical Arcana, parsed into a canonical representation, semantically validated, checked as a magical weave, checked against its host installation, resolved to registered capabilities, and only then passed to a bounded server runtime.

Only the canonical representation may proceed beyond validation. Player-authored source, language definitions, and saved workings are untrusted input.

See:

- [Architecture](docs/ARCHITECTURE.md)
- [Canonical grammar v0.1](docs/language/CANONICAL_GRAMMAR.md)
- [Casting and Weave Design](docs/casting/SYSTEM_DESIGN.md)

## Build

Requires a 64-bit JDK 21.

- Windows: `gradlew.bat build`
- Linux/macOS: `./gradlew build`

The first Gradle setup downloads Minecraft and NeoForge development dependencies and may take a while.

## Safety and compatibility policy

- No arbitrary Java, reflection, class loading, filesystem, network, or command execution from spell source.
- Player-authored language definitions and workings are untrusted input.
- Language definitions, canonical syntax, and saved programs are versioned.
- All world mutations must pass through server-side capabilities, permissions, resource checks, and execution budgets.
