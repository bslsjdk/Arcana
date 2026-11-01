# Arcana Architecture

Status: **proposed baseline, subject to implementation tests**

This document defines the initial architecture for Arcana. It deliberately separates the language system from Minecraft integration so the project can grow without coupling every feature to game APIs.

## 1. Product model

Arcana is a programmable magic framework with four distinct layers:

1. **Authoring layer:** player-defined languages, spellbooks, modules, and editing tools.
2. **Translation layer:** converts a player's language into canonical Arcana source or canonical syntax trees.
3. **Compilation layer:** parses, type-checks, resolves capabilities, validates costs and constraints, then produces a safe execution plan.
4. **Runtime layer:** executes approved operations against a controlled view of the Minecraft world.

The player's language is an interface, not an execution environment. The runtime never executes the player's original text.

## 2. Non-negotiable invariants

- Every executable program has a canonical meaning independent of the player's chosen language.
- Translation is deterministic for a fixed language-definition version and input.
- Unknown words, ambiguous mappings, malformed grammar, and unsupported constructs fail closed.
- Translation may not invent capabilities or silently change meaning.
- The canonical language and intermediate representation are versioned.
- Runtime operations are selected from registered capabilities; source text cannot name Java classes, methods, reflection targets, or arbitrary files.
- All world mutations pass through permission, dimension, chunk, resource, and execution-budget checks.
- Long-running rituals are resumable state machines, not one enormous tick.
- A failed phase must not leave partially committed world state where rollback or staged commit is possible.
- Server authority is final in multiplayer; clients submit requests, never authoritative world changes.

## 3. High-level pipeline

`Player source`
-> `Player-language lexer/parser`
-> `Player-language AST`
-> `Name and grammar resolution`
-> `Canonical Arcana AST`
-> `Type and semantic analysis`
-> `Capability and dependency resolution`
-> `Cost and safety validation`
-> `Execution-plan generation`
-> `Bounded runtime`
-> `Minecraft adapters`

Keep source maps through every transformation so diagnostics can point back to the player's original text.

### Translation contract

A language definition declares:
- language ID and version;
- character/symbol rules;
- lexical rules;
- grammar rules;
- vocabulary entries;
- each vocabulary entry's canonical concept or typed construct;
- aliases and disambiguation rules;
- optional formatting and display metadata.

A vocabulary entry maps to a known canonical symbol or typed construct. It does not register a new runtime capability.

If a player's language cannot express a canonical construct, the translator reports that limitation. It must not guess.

### Translation output

Prefer translating into a canonical AST directly, with optional canonical source rendering for books, debugging, and sharing. This avoids fragile string-to-string rewriting.

## 4. Canonical Arcana language

The standard language should be a small, explicit, typed language. It needs enough expressive power for complex magic, but no unrestricted host-language escape hatch.

Initial language concepts:

- declarations and immutable/mutable bindings;
- primitive and domain types;
- records and structured configuration;
- functions and reusable modules;
- typed inputs and outputs;
- expressions and bounded arithmetic;
- conditionals and pattern matching;
- bounded iteration;
- event declarations;
- effect declarations;
- resource declarations;
- dependency declarations;
- staged transactions;
- diagnostics and recoverable failure values.

Avoid unrestricted recursion and unbounded loops in the first runtime. Advanced constructs can be added only with static limits or runtime budgets.

## 5. Type system and domain model

The type system should prevent nonsensical combinations before execution.

Initial generic types:
- Boolean, Integer, Decimal, Text, Identifier;
- Vector3, Rotation, Region, Duration;
- List, Map, Optional, Result;
- Record and Function.

Initial domain types (registered by capabilities):
- ManaAmount, ElementProfile, MaterialStack;
- EntityReference, BlockReference, WorldReference;
- DimensionSpec, BiomeProfile, TerrainProfile;
- RitualPlan, SpellEffect, MagicalField.

Types describe data and compatibility. They do not themselves grant permission to perform an operation.

## 6. Capability registry

All executable world interactions are exposed through a registry of typed capabilities.

A capability descriptor contains:
- stable namespaced ID and version;
- typed input/output schema;
- required permissions;
- resource-cost estimator;
- execution-budget category;
- world-access declaration;
- side (server/client/common);
- deterministic or nondeterministic classification;
- implementation adapter;
- compatibility metadata.

Examples of capability families:
- observe world state;
- manipulate approved blocks and fluids;
- apply entity effects;
- create particles and sounds;
- manage magical energy networks;
- generate terrain through controlled world-generation APIs;
- create and manage dimensions through the dimension subsystem.

An addon may register a new capability through Java code and a documented API. A player-authored language can provide new names and syntax for existing capabilities, but cannot implement arbitrary Java behavior.

## 7. Runtime and execution plans

Do not execute a spell as an unrestricted script on the server thread.

Compile programs into a safe execution plan made of typed operations and explicit dependencies.

The runtime needs:
- per-tick operation budget;
- per-program total budget;
- mana and material reservations;
- permission checks at execution time;
- chunk-loading policy;
- cancellation;
- pause/resume;
- checkpoints;
- timeout and failure states;
- server-side persistence;
- staged world mutation and commit boundaries.

Execution states should include:
`CREATED -> VALIDATING -> PREPARED -> RUNNING -> SUSPENDED -> COMMITTING -> COMPLETED`
with explicit `FAILED` and `CANCELLED` terminal states. Not every transition is valid; encode and test the transition table.

For large rituals, each phase declares inputs, outputs, preconditions, resource reservations, and commit behavior. A dimension-creation ritual is a long-running workflow composed from these phases.

## 8. Dimension creation as a capability domain

Dimension creation is a late-stage feature, not a shortcut in the core language.

Model it as a declarative `DimensionSpec` plus a staged creation workflow:
1. validate requested dimension properties;
2. validate server/platform support and unique identifiers;
3. prepare world-generation configuration;
4. provision persistent dimension metadata;
5. initialize supported world systems;
6. register or activate the dimension using the target Minecraft loader's supported lifecycle;
7. verify save/reload behavior;
8. publish the dimension and its portal/anchor connection.

Important: dynamic dimension registration support and lifecycle constraints must be researched and tested for the chosen Minecraft version and mod loader before promising runtime creation. If the platform requires startup-time registration, Arcana must use a compatible predeclared dimension strategy or clearly constrain creation semantics. Never assume a dimension can safely be registered at an arbitrary point during play.

## 9. Persistence and compatibility

Persist:
- language definitions and versions;
- canonical spell/module source or AST;
- compiled-plan version and source hash;
- ritual execution state and checkpoints;
- capability IDs and versions required by a program;
- player ownership and permissions;
- dimension metadata and migration version.

Never rely solely on serialized Java object graphs. Use explicit, versioned data formats and migrations.

## 10. Security and multiplayer

Treat all player-created languages, books, and programs as untrusted input.

- No arbitrary Java, reflection, class loading, filesystem access, network access, or command execution from the language.
- Validate sizes, nesting depth, token count, AST node count, numeric ranges, and compile time.
- Enforce server-side authority and permissions.
- Revalidate mutable world preconditions when executing, not only when compiling.
- Bound world reads, chunk access, entity scans, and block mutations.
- Prevent resource-cost underflow, duplicate commits, and replayed ritual completion.
- Keep client rendering and UI separate from authoritative execution.

## 11. Proposed package boundaries

```
dev.arcana
  api
    capability
    type
    module
    event
  language
    definition
    lexer
    parser
    translator
    canonical
  compiler
    ast
    semantic
    typecheck
    resolver
    validation
    plan
  runtime
    scheduler
    execution
    transaction
    resource
    persistence
  domain
    mana
    elements
    space
    time
    matter
    life
    alchemy
    ritual
    dimension
  integration
    minecraft
    networking
    datagen
  client
    editor
    grimoire
    debugger
```

Keep package boundaries enforced by dependency direction:
- language and compiler must not depend on Minecraft classes;
- domain interfaces should avoid loader-specific classes;
- Minecraft adapters implement the domain/runtime ports;
- client code must not be referenced by dedicated-server classes.

## 12. Development milestones

### M0 - Project bootstrap
- Choose Minecraft version, loader, Java toolchain, mappings, and build system.
- Create a minimal mod that builds and launches.
- Add formatting, unit tests, CI, and contribution conventions.

### M1 - Canonical language prototype
- Write a versioned grammar.
- Implement lexer, parser, AST, diagnostics, and serializer.
- Add golden tests and malformed-input tests.

### M2 - Player-language translator
- Define a versioned language-definition format.
- Map custom tokens and grammar constructs to canonical AST nodes.
- Test ambiguity, unknown terms, determinism, and round-trip display.

### M3 - Compiler and capability model
- Add type checking, symbol resolution, capability descriptors, and validation.
- Generate a safe execution plan.
- No world-changing effects yet.

### M4 - Minimal runtime
- Add bounded scheduler, mana/resource accounting, cancellation, persistence, and test capabilities.
- Demonstrate one harmless observable effect and one controlled world effect.

### M5 - In-game authoring tools
- Language editor, spell editor, diagnostics, spellbook storage, import/export.
- Server validates imported definitions and programs.

### M6+ - Content domains
- Elements and effects;
- alchemy and artifacts;
- rituals and magical machines;
- life and creatures;
- space/time operations;
- dimension creation after platform lifecycle validation.

## 13. First vertical-slice acceptance test

Before adding a large spell catalogue, prove this end-to-end path:

1. A player defines a tiny custom language with two or three tokens and a simple grammar.
2. The translator converts a sentence into the expected canonical AST.
3. The compiler type-checks it and resolves a registered harmless capability.
4. The validator calculates and approves the resource cost.
5. The server runtime executes the capability within a fixed budget.
6. A second language with different words and word order translates to the same canonical AST.
7. Invalid, ambiguous, oversized, or unsupported inputs are rejected with useful diagnostics.

This proves the defining promise of Arcana: different player-created languages, one shared and verifiable magic system.
