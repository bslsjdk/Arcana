# Canonical Arcana Grammar v0.1

Status: implemented initial parser subset.

The canonical language is the only source format accepted by the compiler boundary. Player-created languages are translated into this representation first.

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

## Translation v0.1

A player language currently provides a versioned, case-insensitive alias table for canonical syntax keywords and built-in type names. Translation is deterministic and preserves string literals and formatting.

This is the first vertical slice, not the final language-authoring model. Custom grammar productions, word-order changes, affixes, and syntax templates are planned for a later translator milestone. They must translate into the same canonical syntax tree and must never infer missing meaning.
