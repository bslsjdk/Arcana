# Canonical Arcana Grammar v0.1

Status: implemented initial parser subset.

The canonical language is the only source format accepted by the compiler boundary. Player-created languages are translated into this representation first. The canonical representation and standard symbol meanings are owned by Arcana.

## Program structure

A program begins with the canonical keyword spell, followed by a quoted spell name and a block.

Supported statements:

- Variable declaration: let, variable name, colon, type, equals, expression, semicolon.
- Capability invocation: invoke, quoted capability identifier, parenthesized named arguments, semicolon.

Supported expressions in v0.1 are strings, decimal numbers, booleans, and variable references.

## Example

    spell "spark" {
        let power: Number = 2;
        invoke "arcana:effect/ignite"(power = power, target = "self");
    }

## Player-language translation boundary

A player language is free to use its own vocabulary and, in future versions, its own grammar, word order, affixes, and phrase structures. Translation must produce only valid canonical Arcana syntax and references to Arcana-registered standard symbols/operations. A language definition cannot create a new standard symbol, port, operation, or semantic meaning.

The mapping cardinality is intentionally unrestricted by the design:

- one player word may map to one canonical symbol;
- one player word may expand to many canonical symbols;
- many player words may map to one canonical symbol;
- a long sentence may map to one symbol;
- a short phrase may expand to a large canonical structure.

All mappings must be deterministic. Conflicting rules, ambiguous matches without a unique contextual resolution, unknown canonical targets, and unsupported constructs must be rejected rather than guessed.

## Translation v0.2

A player language provides:
- a versioned, case-insensitive alias table for canonical syntax keywords and built-in type names;
- deterministic phrase rules that match token sequences and replace them with canonical Arcana source fragments;
- longest-match selection when phrase patterns overlap by prefix;
- rejection of duplicate phrase patterns and collisions between single-token phrase rules and keyword aliases.

Phrase rules support many source tokens mapping to one canonical token, and one short source token expanding into a larger canonical program. Replacement output is bounded to 65536 characters. Phrase matching operates on the current canonical lexer token model, preserves untouched source text, and does not rewrite text inside unmatched string literals.

This is an incremental phrase-expansion layer, not a complete natural-language grammar engine. Arbitrary grammar productions, word-order transformations, inflection/morphology, syntax trees as rule patterns, and context-sensitive disambiguation remain future work. Translation output must still be parsed and semantically validated by the canonical pipeline; unsupported or ambiguous meaning must never be guessed.

## Cost and resource accounting

Source character count and source token count are not magical complexity. A long source phrase may translate to one canonical symbol, while a short token may expand into a large canonical structure. Magic cost and control/host requirements must be computed from the translated canonical AST/weave and its registered operations.

Source size, token count, expansion depth, and total translation work still require independent technical limits to prevent denial-of-service inputs. These limits are compiler safety limits, not mana costs.
