# Canonical Glyph Script

Status: **design specification; direct glyph authoring and the custom font are not implemented yet**

## 1. The distinction that the model must preserve

Arcana has three different layers. They must not be collapsed into one list of English labels.

1. **Semantic symbol definition**: an Arcana-owned operation or structural role with a stable namespaced ID, typed interfaces, parameter schema, validation contract, and bounded runtime meaning.
2. **Canonical written glyph**: the visible character or grapheme used to write a reference to one semantic symbol. It has a stable script identity and a rendering glyph. It does not define or extend the symbol's semantics.
3. **Player language**: a player-authored vocabulary and grammar that translates into the same canonical symbol stream or canonical AST.

The existing nine IDs (`invocation`, `transform`, `branch`, `nexus`, `repetition`, `ward`, `sigil`, `core`, and `manifestation`) are provisional semantic building blocks. They are **not** the finished alphabet, and their English names are not the glyphs. Some are broad structural categories that will need multiple concrete, narrowly defined symbols beneath them.

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
