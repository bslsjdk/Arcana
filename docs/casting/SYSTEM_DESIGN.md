# Arcana Casting and Weave Design

Status: **design baseline; the current rank rules and registered media are prototypes, not a playable casting system**

Arcana is a system for creating magic, not selecting spells from a fixed catalogue. Players learn magical principles and construct their own workings from runes, glyphs, invocations, patterns, and mana pathways. The same construction can serve combat, tools, equipment, rituals, machines, buildings, and environmental interactions.

## 1. Core concepts

Keep these concepts separate:

- **Player knowledge:** what the player understands and can recreate. Knowledge belongs to the player, not to a world-side spell unlock database.
- **Language definition:** the player's custom vocabulary, symbols, grammar, word order, and inflection rules. It is data stored in a carrier such as a grimoire; it is not the player's knowledge itself.
- **Arcana canonical language:** the one standard representation understood by the server.
- **Magical working:** the actual program or structure authored by the player.
- **Arcane weave:** the structured magical arrangement that describes relationships, flow, conditions, branches, repetition, and composition.
- **Host / carrier:** the item, block, page, or structure that physically bears some or all of a weave.
- **Capability:** a server-registered, permission-checked operation that a validated working may request.

A grimoire is a useful authoring and storage tool, not a key that grants knowledge. A player who remembers a language can recreate its definition without the original book. However, language-definition data and saved programs do not magically follow the player between worlds: unless a carrier or an explicit transfer mechanism is brought over, they must be recreated in the new world.

## 2. Magical design vocabulary

Player-facing design should use magical concepts rather than electronics terminology.

| Concept | Meaning |
|---|---|
| Rune | A symbol expressing a defined magical concept or operation |
| Invocation | An expressed intent, command, or condition |
| Glyph | A mark describing structure or a relationship |
| Mana vein | A magical pathway carrying mana or typed magical information |
| Nexus | A point where flows meet, divide, or transform |
| Ward | A boundary or constraint around part of a working |
| Sigil | A mark identifying a target, location, state, or role |
| Core | A central structure coordinating a working |
| Weave | The complete connected magical construction |

These are extensible semantic building blocks, not a fixed spell list. A rune does not equal one spell. Its meaning is defined, and different valid arrangements can produce different workings. Player-created rune systems must declare how their symbols map to canonical concepts; unknown or ambiguous meanings are rejected rather than guessed.

The internal implementation may use typed graphs and validation algorithms, but those are implementation details. Player-facing UI, documentation, item names, and in-world visuals should speak in terms of magical construction.

## 3. The weave is independent of its host

An Arcane Weave is a logical structure. It is not inherently a ground circle, a book, a weapon, or a block.

A Host is a physical carrier that exposes supported attachment points, capacity, interfaces, persistence, and connection rules. An installation binds one or more weave fragments to one or more hosts.

Supported host families should be extensible rather than hard-coded:

- **Pages:** a page can bear a fragment; multiple pages may form a larger grimoire construction.
- **Items:** a weave may be inscribed on a surface, embedded internally, or attached through a supported interface.
- **Blocks:** a block may contain a construction, expose anchors, receive mana, or connect to neighbouring hosts.
- **Structures:** multiple items and blocks may form one distributed working.

Host constraints affect what can be installed and sustained: available space, material compatibility, supported interfaces, stability, mana supply, distance, loaded state, and connection integrity. The same logical weave may be adapted to different hosts, but an installation must validate the actual host capabilities.

A **magic circle** is one spatial or ritual host arrangement. It is not another name for every weave and is not required for ordinary item-, page-, or block-based magic.

## 4. Weave structure and validation

The logical structure must support, where appropriate:

- typed magical inputs and outputs;
- runes and glyphs with declared semantics;
- mana and information pathways;
- conditional paths and convergence;
- bounded repetition;
- parallel branches with explicit synchronization;
- reusable sub-weaves and modules;
- parameters and declared effects;
- fragments distributed across multiple hosts.

Validation must establish that references and ports exist, types are compatible, required inputs are supplied, branches are well-formed, repetition is bounded, all required fragments are present, and host interfaces can realize the installation. A valid structure still does not gain arbitrary world powers: its outputs resolve only to registered capabilities.

Execution must be bounded by explicit operation, resource, time, and world-access budgets. Client rendering is never authoritative.

## 5. Caster control, complexity, and media

Caster rank is a coarse measure of magical control and the complexity a caster can reliably sustain. It is not a knowledge unlock and must not be represented as a per-spell learning requirement.

Keep independent:
- **Weave complexity:** structural and control demands of the working.
- **Caster control:** the complexity the caster can reliably manage.
- **Mana supply:** available energy and delivery rate.
- **Host capacity:** structural load and interface limits of installed carriers.
- **Stability:** whether the combined working can remain coherent.

A more experienced caster can use simpler workings. A large mana reserve does not automatically provide control over a complex weave, and a powerful caster cannot force an unsuitable host to carry a construction beyond its capacity.

### Grimoire
Stores source, notes, diagrams, language definitions, and weave fragments. Each page may itself be a host. A book can connect page fragments into a larger authored construction, but storage does not automatically mean direct casting capability.

### Focus crystal
A non-weapon focus for stabilizing or directing a working. Quality may affect precision, stability, and supported control load. It grants no knowledge.

### Scroll
A prepared carrier containing a validated, usually consumable working or fragment. The server revalidates imported data and activation conditions.

### Magic circle
A spatial ritual arrangement with anchors, participants, materials, and staged conditions. It is one host/installation form for workings that need spatial extent or coordinated ritual stages.

No wand or staff is part of this design baseline.

## 6. Server-side authoring and execution pipeline

Player-authored source and custom language definitions are untrusted.

1. The player authors a working in a chosen language or directly in canonical Arcana.
2. The player's language front end translates it deterministically into canonical Arcana.
3. The canonical lexer/parser produces the standard representation.
4. Semantic validation checks types, references, structure, and declared effects.
5. Weave validation checks graph structure and bounded control flow.
6. Installation validation checks every host, interface, fragment, and connection.
7. Capability resolution maps approved outputs to registered server capabilities.
8. The server checks permissions, targets, world state, mana, materials, cooldowns, and execution budgets.
9. The bounded runtime executes approved stages and commits permitted world changes.

Only canonical, validated structures proceed to execution. The server does not need to know which player language produced them.

## 7. Language and knowledge invariants

- Every player language targets the same canonical Arcana representation.
- The server parses only canonical Arcana, never a separate parser per player language.
- A custom language definition is a tool/data artifact, not knowledge itself.
- The player's understanding is not stored as a learned-spell whitelist.
- There is no per-spell learned=true flag or mandatory learned-spell collection.
- Knowing how to express a working does not bypass structural, resource, permission, host, or capability validation.
- A language definition saved in a book does not automatically transfer between worlds.
- Unknown, ambiguous, unsupported, or non-deterministic translations fail closed.

## 8. Current implementation boundary

The repository currently contains:
- a bounded canonical-language lexer/parser and a small canonical AST;
- a v0.1 translator that maps vocabulary aliases only;
- static semantic validation;
- a loader-independent casting-rules prototype;
- registered grimoire, focus crystal, and scroll items;
- an initial typed graph prototype in ArcanaCircuit.

The translator does **not** yet support arbitrary grammar productions, word-order changes, inflection, or syntax templates. The graph prototype is an internal technical foundation, not a finished player-facing magical-weave editor. Hosts, installations, cross-host connections, capabilities, mana, persistence, and actual casting are not yet implemented.

## 9. Development sequence

1. Establish the loader-independent magical-weave and host/installation domain model.
2. Validate typed paths, branch semantics, bounded repetition, reusable fragments, and cross-host connections.
3. Replace obsolete spell-learning and minimum-caster-rank semantics with control/complexity checks.
4. Upgrade the language front end from aliases to declarative grammar and deterministic canonical translation.
5. Build a grimoire editor for source, language definitions, diagrams, diagnostics, and page-hosted fragments.
6. Implement host adapters for pages, items, and blocks.
7. Implement a server-authoritative, bounded capability runtime.
8. Prove one harmless end-to-end working before expanding into complex rituals, structures, and world-scale magic.

The first playable experience should let a player author a small working, validate it, install it on a suitable host, activate it through a permitted interface, and receive clear diagnostics when any part is invalid.
