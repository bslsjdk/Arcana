# Arcana

**Arcana** is a Java Edition Minecraft magic mod built around a programmable magic system.

Its central idea is that players may invent their own magical languages, but every player language is translated into one canonical, versioned Arcana language before compilation and execution.

The long-term goal is not merely to add a collection of spells. It is to provide a flexible framework in which spells, rituals, alchemy, artifacts, magical machines, creatures, and even custom dimensions can be described as composable magical programs.

## Design pillars

1. **Player expression is open.** Players define vocabulary, symbols, grammar, and syntax for their own languages.
2. **Execution is canonical.** Player-authored languages translate into the same standard Arcana representation.
3. **Meaning is explicit.** Translation must preserve declared meaning; ambiguity and unknown terms are errors, never guesses.
4. **Power comes from composition.** Reusable modules, functions, types, data, conditions, and long-running plans form complex magic.
5. **Capabilities are extensible.** The mod and compatible addons can register new typed operations without rewriting the language front end.
6. **Execution is controlled.** Compiled magic runs through a bounded runtime with permissions, resource accounting, validation, persistence, and failure handling.
7. **Complexity is meaningful.** Advanced magic is difficult because it expresses many real dependencies and constraints, not because it contains decorative filler.

## Project status

The repository is currently at the design/bootstrap stage. The architecture is being specified before gameplay content is implemented.

See [Architecture](docs/ARCHITECTURE.md) for the proposed system boundaries, compilation pipeline, runtime model, and development milestones.

## Initial development policy

- Keep the language specification independent from Minecraft-version-specific APIs.
- Keep Minecraft integration behind adapters.
- Do not execute arbitrary Java, scripts, reflection payloads, or player-provided bytecode.
- Treat player language definitions and spellbooks as untrusted data.
- Version language definitions, canonical syntax, IR, and saved programs.
- Build automated tests for parsing, translation, type checking, validation, serialization, and runtime behavior before adding large content systems.
