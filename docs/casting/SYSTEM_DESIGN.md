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
- `CanonicalWeave` stores canonical glyph references plus explicit typed connections.
- `StandardSymbolRegistry.builtins()` exposes immutable concrete definitions, including typed input/output ports, parameter schemas, control-flow classes, complexity weights, and optional capability identifiers.
- The former nine broad names are retained as non-instantiable category headings.
- `validateCanonical` rejects unknown and abstract category IDs, invalid parameters, unknown ports, type mismatches, duplicate input bindings, unbound required inputs, duplicate instances, and raw cycles.
- `canonicalComplexity` sums registry-owned structural weights and is independent of source-language length.

The concrete inventory is documented in [Standard Symbol Catalog v0.1](../language/STANDARD_SYMBOL_CATALOG.md). The registry-backed validator now checks typed connections and required input bindings. It does not yet fully define branch/join execution semantics, nested sub-weave installation, or capability implementations. The older `Glyph`/free-form port graph remains a legacy structural prototype for existing graph tests; it is not the canonical player-authored symbol model and must not be used as trusted compiler output. Migrating graph validation to registry-defined ports is the next step.


## 12. Control-flow compilation and per-cast budget foundation

A validated canonical weave can now be compiled into an immutable `CanonicalControlFlowPlan.Plan`. The plan records deterministic root entries, nested region ownership, branch condition sources and selected-arm join semantics, bounded repeat declarations, completion continuations, and sorted typed connections. This is a data-only compilation artifact; it does not evaluate data nodes, schedule execution, access the world, or spend mana.

`CanonicalExecutionBudget.Session` is the mutable accounting state for one future cast. It independently limits:
- total node steps;
- nested control-region depth;
- iterations for each individual loop activation;
- currently reserved runtime-value slots.

Loop iteration counters are keyed by both the canonical loop instance and a runtime activation ID. This matters when a loop is entered again from an outer loop: a completed activation can be explicitly released without confusing it with the next activation. Runtime value reservations can likewise be released, and rejected reservations do not partially mutate state.

Each cast must own its own budget session. Sessions must never be stored on the immutable weave, compiled plan, player-independent symbol registry, or shared host definition. The budget class is accounting only; the interpreter must consume it before scheduling work or allocating runtime values. Capability authorization, mana accounting, and world mutation remain separate server-authoritative checks.


## 13. Typed runtime value storage (first runtime data milestone)

`CanonicalValueStore` is created for one already-validated `CanonicalWeave`, one Arcana-owned registry, and one per-cast execution-budget session. It indexes the actual canonical glyph instances before accepting writes. Callers identify an output by glyph instance ID; they cannot submit a fabricated glyph definition to impersonate a source node.

The initial runtime value algebra supports bounded decimal numbers, booleans, bounded text, and finite 3D vectors. Entity and block references, mana values, and world-backed values are intentionally not constructible in this first pure-data layer.

A value can be written only to an output port declared by the glyph's registered standard symbol, and its runtime essence must exactly match that port. Input reads follow the weave's explicit incoming connection and check both endpoint declarations and the stored value type. No implicit conversions, name-based fallback, or invented values are allowed. Missing output values remain a distinct runtime condition from an unbound or invalid input.

Each newly stored output reserves one live runtime-value slot; replacing an existing output does not consume another slot. Clearing a glyph's outputs releases those slots. Store construction rejects invalid canonical weaves, and each store is tied to its own weave and budget session.

This milestone is a typed value store, not yet a node scheduler or evaluator. It does not execute arithmetic symbols, evaluate branch conditions, access Minecraft state, resolve entity/block references, spend mana, or authorize capabilities. Those responsibilities must be added explicitly in later runtime layers.


## 14. First pure-data node evaluator

`CanonicalNodeEvaluator` is the first executable layer above the validated value store. It currently evaluates only these standard symbols:

- `arcana:reference/number`: parses its declared decimal parameter and publishes a NUMBER output.
- `arcana:reference/position`: parses finite x/y/z parameters and publishes a VECTOR3 output.
- `arcana:data/add`: reads its two explicitly connected NUMBER inputs and publishes their DECIMAL128 sum.
- `arcana:data/equal`: compares two NUMBER inputs by numeric value (scale-insensitive) and publishes BOOLEAN.
- `arcana:data/less_than`: compares two NUMBER inputs and publishes BOOLEAN.

Every evaluation of a member glyph consumes one node step before evaluation. Unknown glyphs do not consume budget. Registered but not-yet-implemented symbols are rejected as unsupported; there is no fallback behavior based on symbol names. Missing connected runtime values are reported distinctly and do not cause invented defaults.

Numeric outputs remain bounded by the runtime value type's precision and scale limits. Position components must be finite. The evaluator and its value store share the same per-cast budget session. Its exposed budget snapshot is immutable.

This evaluator remains deliberately small and does not itself schedule nodes or interpret control flow. `CanonicalWeaveScheduler` now performs deterministic data-dependency ordering and interprets entry, branch, selected-arm join, and fixed-count repeat control flow around this evaluator. Neither layer resolves entity/block references, inspects Minecraft state, mutates the world, authorizes capabilities, or consumes mana.



## 15. Deterministic pure-data scheduling (first scheduler milestone)

`CanonicalWeaveScheduler` now consumes a validated immutable control-flow plan and a per-cast budget session. Before evaluating anything, it verifies that the plan belongs to the exact weave/version and preflights every symbol. This prevents a partially executed cast from discovering halfway through that it contains an unsupported world or control operation.

For the currently supported pure-data subset, the scheduler builds a dependency graph from explicit canonical connections and performs deterministic topological ordering. Ready nodes are selected by canonical instance ID, so equivalent input orderings do not create arbitrary scheduling differences. Each node is evaluated once; unavailable inputs are not retried in an unbounded loop. The evaluator and scheduler share one per-cast value store and node-step budget.

The scheduler currently supports numeric/position references, addition, numeric equality, and less-than, plus the pure control-flow forms entry, branch, join, and bounded repeat. It rejects registered but unevaluated perception, mana, reference, constraint, and effect operations before executing any node.

Data dependencies are evaluated in deterministic topological order. In control-flow weaves, branch conditions trigger lazy evaluation of only their transitive pure-data dependencies; unrelated or inactive-path data nodes are not eagerly evaluated. Branches read the resulting BOOLEAN condition and execute exactly one declared region. The selected region exit must connect to the corresponding input of the shared join; the join is activated once for that selected arm and never waits for the unselected arm. Repeats execute their body exactly the declared fixed number of times and then follow the completion continuation. They do not invent a loop condition. Region depth, per-activation iteration counts, and total node steps are accounted separately. Cancellation is checked between interpreter steps, and loop/region accounting is released when an execution exits normally or through a bounded failure.

The scheduler remains a pure, one-shot interpreter, not a playable casting runtime: it does not access Minecraft, spend mana, authorize capabilities, mutate the world, install a weave on a host, or persist/resume an execution. Perception and world-effect symbols remain fail-closed until their server-authoritative capability adapters exist.

## 16. Player-defined embedded casting triggers (design baseline)

Casting activation and cancellation phrases belong to a player's language/trigger profile, not to an individual weave or spell. Each profile may define its own activation token(s), cancellation token(s), normalization/tokenization rules, and optional placement constraints. The trigger vocabulary is player-configurable; Arcana does not impose universal spoken words.

An activation phrase may appear **anywhere in the incantation, provided it occurs before the cancellation phrase**. It is not restricted to the beginning, middle, or end. The activation phrase is a control marker, not part of the canonical magical payload: the recognizer removes the matched marker while preserving the order and content of the surrounding incantation. Thus the magical expression may have content both before and after the activation marker, and inserting the marker must not change the translated weave's meaning.

The intended streaming behavior is:
1. Incoming incantation tokens are recognized against the active player's trigger profile while the remaining tokens are accumulated as ordinary magical-language input.
2. Before activation, the system may parse and prepare the accumulated payload, but it must not execute world effects.
3. When an activation marker is recognized, the current prepared working enters the casting runtime. Subsequent non-trigger tokens continue to belong to that same incantation/session; they are not discarded merely because activation occurred in the middle.
4. A cancellation marker aborts the active casting session, or discards a not-yet-activated pending incantation. It is a control instruction and is not translated as magical payload.
5. Trigger order is significant. If activation occurs before cancellation in the same input stream, activation is valid and the later cancellation aborts that cast. If cancellation occurs first, it terminates the current command stream and any later activation marker in that same stream is ignored. Once cancellation is recognized, a later activation requires a new command/input batch.
6. Trigger matching uses the profile's explicit tokenization and normalization rules. It must not use accidental substring matches. Ambiguous or uncertain recognition fails closed rather than guessing.

The activation marker may therefore be embedded among ordinary incantation words, while the cancellation marker remains the explicit abort command. This supports concealed or stylistically natural casting phrases without binding a particular trigger to a particular weave. A player may use one language to describe the working and another trigger vocabulary to activate or cancel it.

The trigger recognizer and casting-session controller are separate from the canonical weave scheduler. The recognizer handles player-language tokens and control markers; the session controller owns pending/active/cancelled cast state; the scheduler remains responsible only for interpreting an already validated canonical weave. Cancellation requests are cooperative runtime signals checked at interpreter boundaries. Effects already committed to the world are not implicitly rolled back; each future capability must define its own transactional or reversible behavior.

This is a design baseline, not an implemented feature. Audio capture, speech recognition, trigger-profile persistence, streaming language translation, casting-session orchestration, and in-game configuration UI are not yet implemented.

## 17. First embedded-trigger recognizer implementation

`CastingTriggerRecognizer` is the first loader-independent implementation slice for the trigger protocol. It accepts a `TriggerProfile` containing player-defined activation and cancellation phrases and analyzes one complete recognized input batch. The current implementation:

- supports multiple activation and cancellation phrases per profile, including multi-word phrases;
- applies Unicode NFKC normalization and case-insensitive matching;
- matches complete letter/digit/underscore tokens rather than substrings;
- permits activation at the beginning, middle, or end of the incantation;
- removes activation markers from the canonical-language payload while preserving the surrounding source text;
- treats cancellation before activation as discarding the pending incantation;
- treats cancellation after activation as an abort request and truncates the current batch at the cancellation marker;
- rejects identical or overlapping activation/cancellation phrases as ambiguous;
- applies bounded source, token, phrase-count, and phrase-length limits.

The recognizer returns a decision and payload only. It does not itself start or stop a cast, invoke `CanonicalWeaveScheduler`, access Minecraft, or mutate the world. A casting-session controller must consume the result and own pending/active/cancelled lifecycle state. The current recognizer analyzes complete batches; incremental audio capture, speech recognition, cross-batch partial-phrase buffering, persistent trigger profiles, and the in-game configuration UI remain future work.

Tests cover activation placement, ordered cancellation, multi-word phrases, exact-token matching, Unicode compatibility normalization, overlapping-trigger rejection, and input limits.
