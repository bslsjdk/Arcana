# Arcana

**Arcana** is a Java Edition Minecraft magic mod for Minecraft 1.21.1, NeoForge 21.1.264, and Java 21.

Arcana is about creating magic rather than selecting spells from a fixed catalogue. Players can invent their own languages, vocabularies, grammar, word order, and expressions. Those languages translate into one canonical, versioned Arcana representation built only from Arcana-defined standard symbols and operations. Players cannot create new underlying magical symbols or redefine their meanings.

The long-term goal is a programmable magic framework for workings, rituals, alchemy, artifacts, magical machines, creatures, structures, and custom dimensions.

## Design pillars

1. **Player expression:** custom vocabulary, grammar, syntax, word order, inflection, and phrase-level translation rules.
2. **One canonical meaning:** every player language targets the same standard representation and the same Arcana-owned symbol semantics.
3. **Magical authoring:** player-facing concepts are Arcana-defined runes, glyphs, invocations, patterns, and mana pathways. Players compose and reference these; they do not invent new underlying symbol meanings.
4. **Host independence:** a weave is a logical structure; pages, items, blocks, and multi-part structures are possible physical hosts.
5. **Explicit meaning:** unknown or ambiguous input is rejected, never guessed.
6. **Composable complexity:** reusable fragments, typed paths, conditions, branches, and bounded repetition.
7. **Safe execution:** registered capabilities, explicit budgets, permission checks, and server authority.

## Language and canonical symbols

**Direct standard-script authoring is also a first-class route.** Players may write Arcana's own canonical magical characters directly, without defining a personal language. A personal language is an optional front end; both routes must converge on the same canonical parser and validators. The current nine registry IDs are provisional semantic building blocks, not the finished alphabet, and their English labels are not the visible glyphs. See [Canonical Glyph Script](docs/language/CANONICAL_GLYPH_SCRIPT.md) for the design boundary and implementation sequence.

A player's language is an input layer, not a second magic system. It may use arbitrary vocabulary and, as the language engine grows, arbitrary grammar and phrase structures. A long sentence may translate to one standard symbol; a short word may translate to a large sequence of standard symbols. Multiple words may combine into one symbol, and one phrase may expand into a complete canonical structure.

The translation output is restricted to symbols and operations registered by Arcana. A language definition may choose how its own expressions map to those existing meanings, but cannot declare a new symbol ID, invent a new operation, or change the meaning of an existing symbol. Unknown targets, conflicting rules, and ambiguous translations must fail closed.

Magic cost and execution requirements are calculated from the translated canonical structure, never from the number of characters or words in the player's source language. Source size and translation expansion still have independent technical limits to prevent resource exhaustion.

## Current implementation

The first implementation branch establishes:

- a NeoForge 1.21.1 / Java 21 project;
- a bounded canonical-language lexer and recursive-descent parser;
- a versioned player-language translator with keyword aliases and deterministic phrase expansion;
- a canonical AST with source spans and structured diagnostics;
- static semantic validation for primitive types, sequential variable declarations, duplicate declarations, undefined references, and capability identifier format;
- unit tests for parsing, translation equivalence, semantic validation, and malformed input;
- a loader-independent casting-rules prototype;
- registered grimoire, focus crystal, and scroll items;
- an immutable standard-symbol registry with concrete typed ports, parameter schemas, control-flow classifications, complexity weights, and capability identifiers;
- canonical graph validation against registry-owned ports, including type checks, required-input binding, duplicate-connection rejection, and raw-cycle rejection;
- a structural complexity score derived from canonical symbol weights, independent of player-language source length;
- a versioned canonical script character table and structured direct-writing codec that round-trips validated canonical weaves;
- explicit branch-arm and bounded-repeat body regions, with nested-region containment, boundary membership, sibling-overlap, and parent-cycle validation;
- explicit region entry/exit wiring, branch convergence through a shared join, repeat-body exit capture, and rejection of control-port fanout or boundary escapes;
- deterministic validated control-flow plan compilation for root entries, mutually exclusive branch arms, shared joins, bounded repeat bodies, and completion continuations (plan only; no runtime execution yet);
- per-cast execution-budget accounting for node steps, nested-region depth, per-activation loop iterations, and runtime value slots; this is resource accounting only, not an interpreter or world execution;
- validated-weave-bound typed runtime value storage with explicit connection-based input resolution and budgeted output slots; pure data storage only, with no node evaluation or world access yet;
- deterministic pure-data evaluation for numeric and position references, addition, numeric equality, and less-than comparison; each evaluation consumes the per-cast node-step budget, with no world access or mana execution;\n- deterministic dependency scheduling and bounded interpretation of the supported pure-data subset plus mutually exclusive branch arms, selected-arm joins, and fixed-count repeat regions; cancellation, per-cast isolation, and node/region/loop budgets are enforced; world capabilities and mana execution remain unsupported;
- a loader-independent player-defined casting trigger recognizer and per-player session controller supporting embedded activation phrases, ordered cancellation, multi-word triggers, Unicode normalization, exact-token matching, ambiguity rejection, pending-source accumulation, and cooperative cancellation signals;
- a deterministic casting source-preparation pipeline that translates player language, parses canonical source, and performs static semantic validation without executing capabilities;
- the older free-form graph retained only as a legacy prototype.

The v0.2 translator supports vocabulary aliases and deterministic token-phrase rules. Phrase rules can expand a short expression into a larger canonical source fragment or compress a longer phrase into a short canonical token. It does **not yet** support arbitrary grammar productions, word-order transformations, inflection, syntax-tree patterns, or context-sensitive disambiguation. The semantic validator is static analysis only: it does not execute capabilities or mutate the Minecraft world.

The grimoire, focus crystal, and scroll are registered as inert items in an Arcana creative tab, with placeholder vanilla models. Spellbook UI, persistent host installations, mana handling, and actual spell execution are **not implemented yet**.

## Compilation pipeline

Player-authored language and source are translated into canonical Arcana, parsed into a canonical representation, semantically validated, checked as a magical weave, checked against its host installation, resolved to registered capabilities, and only then passed to a bounded server runtime.

Only the canonical representation may proceed beyond validation. Player-authored source, language definitions, and saved workings are untrusted input.

See:

- [Architecture](docs/ARCHITECTURE.md)
- [Canonical grammar v0.1](docs/language/CANONICAL_GRAMMAR.md)
- [Canonical glyph script](docs/language/CANONICAL_GLYPH_SCRIPT.md)
- [Standard symbol catalog v0.1](docs/language/STANDARD_SYMBOL_CATALOG.md)
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
