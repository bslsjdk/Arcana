# Arcana Casting and Weave Design

Status: **design baseline; the current rank rules and registered media are prototypes, not a playable casting system**

Arcana is a system for creating magic, not selecting spells from a fixed catalogue. Players learn magical principles and construct their own workings from Arcana-defined standard symbols, invocations, patterns, and mana pathways. The same construction can serve combat, tools, equipment, rituals, machines, buildings, and environmental interactions.

## 1. Core concepts

Keep these concepts separate:

- **Player knowledge:** what the player understands and can recreate. Knowledge belongs to the player, not to a world-side spell unlock database.
- **Player language definition:** the player's custom vocabulary, grammar, word order, affixes, phrase patterns, and deterministic translation rules. It is data stored in a carrier such as a grimoire; it is not the player's knowledge itself.
- **Arcana standard symbol system:** the single Arcana-defined registry of canonical symbols, operations, ports, and semantics. Players may reference and compose these symbols but cannot create new underlying symbols or redefine their meanings.
- **Canonical representation:** the versioned standard syntax/AST understood by the server and produced by every player-language front end.
- **Magical working:** the actual program or structure authored by the player.
- **Arcane weave:** the structured magical arrangement that describes relationships, flow, conditions, branches, repetition, and composition.
- **Host / carrier:** the item, block, page, or structure that physically bears some or all of a weave.
- **Capability:** a server-registered, permission-checked operation that a validated working may request.

A grimoire is a useful authoring and storage tool, not a key that grants knowledge. A player who remembers a language can recreate its definition without the original book. However, language-definition data and saved programs do not magically follow the player between worlds: unless a carrier or an explicit transfer mechanism is brought over, they must be recreated in the new world.

## 2. Magical design vocabulary

Player-facing design should use magical concepts rather than electronics terminology. The symbols and meanings in this vocabulary are defined by Arcana, not invented by individual players.

| Concept | Meaning |
|---|---|
| Rune / standard symbol | An Arcana-registered symbol with a fixed, defined magical meaning or operation |
| Invocation | A standard semantic role expressing intent, command, or condition |
| Glyph | The canonical symbol or its visual inscription in a magical construction; not a user-defined semantic primitive |
| Mana vein | A magical pathway carrying mana or typed magical information |
| Nexus | An Arcana-defined point where flows meet, divide, or transform |
| Ward | An Arcana-defined boundary or constraint around part of a working |
| Sigil | An Arcana-defined mark identifying a target, location, state, or role |
| Core | An Arcana-defined structure coordinating a working |
| Weave | The complete connected magical construction |

These are standard semantic building blocks, not a fixed spell list. Players freely compose them into new workings, but composition does not grant permission to invent new primitive meanings. The internal implementation may use typed graphs and validation algorithms; those are implementation details. Player-facing UI, documentation, item names, and in-world visuals should speak in terms of magical construction.

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

- references to Arcana-registered standard symbols and their fixed typed inputs/outputs;
- mana and information pathways;
- conditional paths and convergence;
- bounded repetition;
- parallel branches with explicit synchronization;
- reusable sub-weaves and modules;
- parameters and declared effects;
- fragments distributed across multiple hosts.

Validation must establish that symbol references exist in the Arcana registry, ports and types are compatible, required inputs are supplied, branches are well-formed, repetition is bounded, all required fragments are present, and host interfaces can realize the installation. A valid structure still does not gain arbitrary world powers: its outputs resolve only to registered capabilities.

Validation must reject unknown standard-symbol IDs. Player language rules may only emit registered canonical IDs or canonical AST templates; they cannot register symbols, ports, operations, or semantic definitions.

Execution must be bounded by explicit operation, resource, time, and world-access budgets. Client rendering is never authoritative.

## 5. Caster control, complexity, and media

Caster rank is a coarse measure of magical control and the complexity a caster can reliably sustain. It is not a knowledge unlock and must not be represented as a per-spell learning requirement.

Keep independent:
- **Canonical weave complexity:** structural and control demands of the translated working.
- **Caster control:** the complexity the caster can reliably manage.
- **Mana supply:** available energy and delivery rate.
- **Host capacity:** structural load and interface limits of installed carriers.
- **Stability:** whether the combined working can remain coherent.

The player's source-language length has no direct relationship to magic cost. A long expression may translate to one standard symbol; a short expression may expand to a large canonical structure. Mana cost, control requirements, and structural capacity are calculated only after deterministic translation, from the canonical structure and its declared operations/effects. Source length, token count, expansion depth, and translation work have separate technical limits solely to prevent resource exhaustion.

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

1. The player authors a working either directly in the Arcana canonical glyph script or in a player-authored language.
2. Direct-script input is read by the canonical script reader; player-language input is deterministically translated into the same canonical representation.
3. Both routes use the same canonical lexer/parser and produce the same standard representation.
4. Semantic validation checks types, references, structure, and declared effects.
5. Weave validation checks symbol registry references, graph structure, and bounded control flow.
6. Installation validation checks every host, interface, fragment, and connection.
7. Capability resolution maps approved outputs to registered server capabilities.
8. The server checks permissions, targets, world state, mana, materials, cooldowns, and execution budgets.
9. The bounded runtime executes approved stages and commits permitted world changes.

Only canonical, validated structures proceed to execution. The server does not need to know which player language produced them.

## 7. Language and knowledge invariants

- Every player language targets the same Arcana-owned canonical representation.
- The standard symbol registry and symbol semantics are defined only by Arcana.
- Players may define arbitrary language expressions and deterministic mappings, but mappings may target only registered canonical symbols/operations or valid canonical AST templates.
- Players cannot introduce new underlying magical symbols, ports, primitive operations, or semantic meanings.
- The server parses only canonical Arcana, never a separate parser per player language.
- A custom language definition is a tool/data artifact, not knowledge itself.
- The player's understanding is not stored as a learned-spell whitelist.
- There is no per-spell learned=true flag or mandatory learned-spell collection.
- Knowing how to express a working does not bypass structural, resource, permission, host, or capability validation.
- Magic cost and control requirements depend on the translated canonical structure, not source-language character or word count.
- Source and expansion limits are technical anti-abuse limits, not magic-cost rules.
- A language definition saved in a book does not automatically transfer between worlds.
- Unknown, ambiguous, unsupported, or non-deterministic translations fail closed.

## 8. Current implementation boundary

The repository currently contains:
- a bounded canonical-language lexer/parser and a small canonical AST;
- a v0.2 translator with keyword aliases and deterministic phrase rules;
- static semantic validation;
- a loader-independent casting-rules prototype where rank represents control capacity, not knowledge;
- registered grimoire, focus crystal, and scroll items;
- an ArcaneWeave model with a registry of concrete standard-symbol contracts, typed ports, parameter schemas, complexity weights, and capability identifiers;
- a separate legacy free-form graph model retained for structural tests and migration work.

The registry now defines concrete control, data, perception, mana, reference, constraint, and effect symbols. The former nine English IDs remain non-instantiable family headings. The registry is not yet connected to a complete graph validator or runtime, and capability identifiers do not imply implemented world operations. The translator does **not** yet support arbitrary grammar productions, word-order changes, inflection, or syntax templates. Hosts, installations, cross-host connections, capabilities, mana, persistence, and actual casting are not yet implemented.

The older ArcanaCircuit graph is a legacy technical prototype and is not the player-facing design vocabulary.

## 9. Development sequence

1. Migrate the legacy free-form graph validator to use registry-defined ports and control-flow contracts.
2. Validate typed paths, branch semantics, bounded repetition, reusable fragments, and cross-host connections against that registry.
3. Replace obsolete spell-learning and minimum-caster-rank semantics with control/complexity checks.
4. Upgrade the language front end from aliases to deterministic phrase/grammar translation that can emit only registered canonical symbols or valid canonical AST templates.
5. Build a grimoire editor for source, language definitions, diagrams, diagnostics, and page-hosted fragments.
6. Implement host adapters for pages, items, and blocks.
7. Implement a server-authoritative, bounded capability runtime.
8. Prove one harmless end-to-end working before expanding into complex rituals, structures, and world-scale magic.

The first playable experience should let a player author a small working, validate it, install it on a suitable host, activate it through a permitted interface, and receive clear diagnostics when any part is invalid.

## 10. Canonical magical writing system

Direct use of Arcana's own standard magical writing is a first-class authoring route. A player does not need to create a personal language before writing or casting a working. Player-authored languages are optional front ends that converge on the same canonical representation.

Keep three layers separate:
- **Semantic symbol definition:** the Arcana-owned operation or structural role, identified by a stable namespaced ID and defined by registry-owned typed ports, parameter schema, control-flow class, complexity weight, and optional capability mapping.
- **Canonical written glyph:** the visible character/grapheme that writes a reference to a semantic symbol. Its visual outline does not define its meaning.
- **Player language:** a player-authored writing/vocabulary/grammar system that deterministically translates into canonical symbols or canonical AST templates.

The former nine registry IDs are now non-instantiable family headings, not writable symbols or alphabet characters. Concrete symbols have their own narrow IDs, typed ports, parameters, and capability mappings. See [Standard Symbol Catalog v0.1](../language/STANDARD_SYMBOL_CATALOG.md). Their English identifiers are not the visible magical characters. Do not assign permanent Unicode code points before the visual writing system is designed.

A glyph's identity must be its canonical Arcana registry key, not its Unicode code point or font outline. A custom font may use Unicode Private Use Area slots as a rendering/transport detail, but those values must never become semantic identities or authority boundaries. The versioned canonical script table resolves written graphemes to registered symbol IDs. A plain-text fallback notation may be used for tests, diagnostics, and accessibility.

Direct-script and translated input must share one parser, semantic validator, weave validator, and runtime pipeline. There must not be a separate execution engine for direct glyph input. Unknown glyphs, ambiguous grapheme boundaries, and unregistered symbol references fail closed.

The full script specification and implementation sequence are in [Canonical Glyph Script](../language/CANONICAL_GLYPH_SCRIPT.md). This is a design baseline only: the original font, complete character inventory, direct-script reader, and in-game writing UI are not yet implemented.

## 11. Registry-backed canonical symbol model (implementation milestone)

The code now has a registry-backed canonical model:
- `CanonicalGlyph` stores an instance id, an Arcana standard-symbol id, and data parameters only. It cannot declare ports, operations, capabilities, or semantic behavior.
- `CanonicalWeave` is a list of those references.
- `StandardSymbolRegistry.builtins()` exposes immutable concrete definitions, including typed input/output ports, parameter schemas, control-flow classes, complexity weights, and optional capability identifiers.
- The former nine broad names are retained as non-instantiable category headings.
- `validateCanonical` rejects unknown and abstract category IDs, unknown or missing parameters, malformed parameter values, out-of-range values, and duplicate instance IDs.

The concrete inventory is documented in [Standard Symbol Catalog v0.1](../language/STANDARD_SYMBOL_CATALOG.md). The registry is not yet a complete graph validator: it does not yet validate connections between canonical glyph instances, branch/join execution semantics, or capability implementations. The older `Glyph`/free-form port graph remains a legacy structural prototype for existing graph tests; it is not the canonical player-authored symbol model and must not be used as trusted compiler output. Migrating graph validation to registry-defined ports is the next step.
