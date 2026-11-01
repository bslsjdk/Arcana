# Canonical Glyph Script

Status: **identity table and codec implemented; final visual design, direct weave parser, and custom font are not implemented yet**

## 1. The distinction that the model must preserve

Arcana has three different layers. They must not be collapsed into one list of English labels.

1. **Semantic symbol definition**: an Arcana-owned operation or structural role with a stable namespaced ID, typed interfaces, parameter schema, validation contract, and bounded runtime meaning.
2. **Canonical written glyph**: the visible character or grapheme used to write a reference to one semantic symbol. It has a stable script identity and a rendering glyph. It does not define or extend the symbol's semantics.
3. **Player language**: a player-authored vocabulary and grammar that translates into the same canonical symbol stream or canonical AST.

The existing nine IDs (`invocation`, `transform`, `branch`, `nexus`, `repetition`, `ward`, `sigil`, `core`, and `manifestation`) are provisional semantic building blocks. They are **not** the finished alphabet, and their English names are not the glyphs. Some are broad structural categories that will need multiple concrete, narrowly defined symbols beneath them.

See [Standard Symbol Catalog v0.1](STANDARD_SYMBOL_CATALOG.md) for the first concrete semantic inventory. The nine legacy names are non-instantiable family headings; actual constructions must reference concrete registered symbols such as control, data, perception, mana, reference, constraint, and effect symbols.

## 2. The canonical script is a real writing system

Players must be able to author a working directly in the Arcana standard script without creating or loading a personal language definition.

The canonical script should have:
- a distinct, original visual glyph for each concrete canonical script character;
- a stable character identity independent of its font outline;
- explicit grapheme boundaries, so adjacent glyphs cannot accidentally merge into a different token;
- a readable writing direction and layout rules;
- syntax for grouping, nesting, parameters, references, and connections;
- a plain-text fallback notation for accessibility, debugging, and devices without the Arcana font.

The script is not limited to Latin letters with decorative strokes. It may be an original alphabet, abugida, syllabary, or a mixed system. The final choice must be consistent across the entire inventory rather than improvised one glyph at a time.

## 3. Glyph identity is not Unicode identity

A glyph's canonical identity is its Arcana registry key, not the numeric Unicode value used to display it.

The implementation should maintain a mapping:

`script character / grapheme -> canonical symbol ID`

The client font supplies the visual outline. A private-use Unicode code point may be used as a transport/rendering slot for custom font characters, but it is only an encoding choice: it must never become the semantic identity, registry key, or authority boundary. The server resolves the character through the versioned canonical script table and then validates the referenced symbol against the standard symbol registry.

Do not allocate permanent code points to the current nine broad categories before the concrete symbol inventory and visual system have been designed. Otherwise later splitting a broad category into real operations will create a needless compatibility mess.

## 4. Direct authoring and custom-language authoring converge

Both authoring routes must produce exactly the same canonical representation:

- Direct route: canonical glyph script -> canonical lexer/parser -> canonical AST/symbol references.
- Custom route: player language -> deterministic translator -> canonical lexer/parser -> canonical AST/symbol references.

There must not be a second execution engine for direct-script input. The language translator is optional; the canonical parser and validators are not.

A text fallback such as `⟦arcana:invocation⟧` may be used in development tools and diagnostics. It is not a new player-defined symbol and is not a replacement for the in-world glyph font.

## 5. Composition rules

The canonical writing system must be able to express:
- sequential composition;
- explicit typed connections;
- grouped and nested sub-weaves;
- conditional branches;
- bounded repetition;
- parallel paths and explicit synchronization;
- parameters and references;
- fragments that can be split across pages or other hosts.

Visual proximity alone must not create an executable connection. Connections and grouping need unambiguous syntax or explicit structural marks. A handwritten diagram may be a visual editor for the same AST, but the server must validate the resulting canonical structure rather than infer semantics from arbitrary pixels.

## 6. Player-created writing systems

A player may create an entirely new language, including its own visible characters, vocabulary, word order, affixes, and grammar. Its character shapes are part of that player's language data and may be rendered by a custom writing table or font-like mapping.

That freedom applies only to the front end:
- custom characters map to player-language tokens;
- grammar and phrase rules deterministically translate those tokens;
- the result may reference only Arcana-registered canonical symbols and valid canonical structures;
- custom characters cannot register new canonical symbols or change existing semantics.

A player may also use canonical glyphs and personal-language text together in one authoring document, provided token boundaries and translation scope are explicit.

## 7. Validation and safety invariants

- Unknown canonical glyphs are rejected.
- A glyph cannot resolve to a player-supplied symbol definition.
- A custom language cannot redefine a canonical glyph's meaning.
- Ambiguous or conflicting translation rules fail closed.
- Canonical input and translated input pass through the same parser and semantic validator.
- Costs and control requirements are calculated from the validated canonical structure, never from the visual length or source-language length.
- Source size, token count, nesting depth, and translation work have separate technical limits.
- Font rendering is cosmetic; changing a font cannot change a working's meaning.

## 8. Implementation sequence

1. Finish the concrete canonical symbol inventory and each symbol's typed contract.
2. Design the original script's visual grammar and grapheme inventory.
3. Add a versioned canonical character-to-symbol mapping.
4. Add direct canonical-script lexing/parsing, with a textual fallback for tests.
5. Add a custom font and in-game writing/rendering support.
6. Make the custom-language translator target the same canonical token/AST model.
7. Add round-trip, unknown-glyph, ambiguous-boundary, version-migration, and direct-vs-translated equivalence tests.

Until these steps are complete, the current registry-backed symbol IDs are an ownership prototype, not a finished magic alphabet or a playable direct-writing system.


## 9. Canonical script identity table v1

The code now contains `CanonicalScript`, a versioned identity table for the 18 currently concrete built-in symbols.

- Each concrete symbol ID maps to one stable script slot.
- The current transport slots occupy Unicode Private Use Area code points U+E000 through U+E011.
- The mapping is owned by Arcana and exposed as an immutable table.
- Encoding an unknown symbol ID fails; decoding an unknown character produces a diagnostic instead of inventing a symbol.
- Whitespace is currently ignored between glyphs.
- Round-trip tests verify that encoding and decoding preserve the exact canonical symbol IDs.

| Slot | Canonical symbol ID |
|---|---|
| U+E000 | `arcana:control/entry` |
| U+E001 | `arcana:control/branch` |
| U+E002 | `arcana:control/join` |
| U+E003 | `arcana:control/repeat` |
| U+E004 | `arcana:data/add` |
| U+E005 | `arcana:data/equal` |
| U+E006 | `arcana:data/less_than` |
| U+E007 | `arcana:sense/block` |
| U+E008 | `arcana:sense/entity` |
| U+E009 | `arcana:sense/light_level` |
| U+E00A | `arcana:mana/absorb` |
| U+E00B | `arcana:mana/store` |
| U+E00C | `arcana:mana/release` |
| U+E00D | `arcana:reference/position` |
| U+E00E | `arcana:reference/number` |
| U+E00F | `arcana:reference/entity` |
| U+E010 | `arcana:constraint/ward` |
| U+E011 | `arcana:effect/light` |

These PUA values are **not the final visual alphabet**. Without the Arcana font, they may render as empty boxes or fallback symbols. They are stable identity/transport slots for this script-table version, not semantic IDs. A future font can draw original forms for these slots without changing their meanings.

The current codec only decodes a flat sequence of symbol references. It does not yet encode parameters, instance identifiers, typed connections, nesting, or complete canonical weaves. A sequence of glyphs is therefore not automatically a valid executable construction; the graph model and validator remain authoritative until the structured script grammar is implemented.


## 10. Structured direct-writing codec v1

A first structured codec now exists in `CanonicalScript`. It converts direct standard glyph writing into the existing `CanonicalWeave` model and runs the exact same `validateCanonical` registry validator before returning a parsed weave.

The current textual structure uses the actual canonical character followed by an explicit instance identifier:

```text
<entry-glyph>#entry;
<position-glyph>#position{x=1,y=64,z=-2.5};
<number-glyph>#level{value=12};
<light-glyph>#light;
@entry.flow>light.flow;
@position.position>light.position;
@level.number>light.level;
```

Here, each `<...-glyph>` placeholder means the actual one-character private-use glyph, not the placeholder text. The connection declaration starts with `@` and explicitly names source instance/port and target instance/port. Whitespace is permitted around declarations. Glyph declarations and connections are separated by semicolons.

Current v1 scalar parameters are deliberately limited to finite-looking decimal/integer syntax and boolean literals; the registry remains the authority for the actual parameter type and range. String literals and arbitrary expressions are not supported. Duplicate instance IDs, duplicate parameters, unknown characters, malformed connections, and any registry validation error fail closed.

The codec supports deterministic `encodeWeave` and `parseWeave` round trips for the current flat canonical graph model. It does **not** yet add nested sub-weaves, explicit branch-region bodies, or bounded-loop body ownership. The existing repeat symbol is still only a registered symbol with a bounded `maxIterations` parameter; the runtime meaning of its body remains future work. Do not treat this codec as a complete executable magic language yet.


## 11. Structured control regions v1

The canonical weave model now carries explicit `CanonicalRegion` records in addition to glyph instances and typed connections. A region records:

- a stable region ID;
- the controlling glyph instance and its standard output port;
- entry and exit glyph instances;
- the complete member-glyph list;
- an optional parent-region ID.

A branch must own exactly the two named arms `whenTrue` and `whenFalse`. A bounded repeat must own its `body` region. Other symbol families cannot claim control regions. Region IDs and members are validated, region boundaries must be members, a region cannot contain its own owner, and nested regions must remain entirely inside their parent. Unrelated/sibling regions cannot claim the same glyph instances, and parent references must form an acyclic containment forest.

The direct script codec now serializes regions with declarations of this form:

```text
^repeatBody(repeat.body)[bodyStart>bodyEnd](bodyStart,work,bodyEnd)@-;
^nestedArm(branch.whenTrue)[innerStart>innerEnd](innerStart,innerEnd)@outerArm;
```

The parent marker `-` means a top-level region; otherwise it names the containing region. Member IDs are explicit and ordered. These declarations are metadata for structural ownership, not a claim that branch/repeat runtime execution is already implemented.

This milestone establishes **structural containment**, not a complete control-flow interpreter. The next required step is to validate boundary connections and define the exact execution contract for branch selection, repeat iteration, body completion, and loop exit. Until that exists, region-aware canonical weaves can be validated and serialized but must not be executed.
