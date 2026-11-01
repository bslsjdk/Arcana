# Standard Symbol Catalog v0.1

Status: **first concrete semantic inventory; capability execution is not implemented**

This catalog turns the former nine broad headings into concrete Arcana-owned symbol definitions. A concrete symbol is one writable semantic unit. The English identifier is a stable development name, not its eventual visible glyph shape.

## 1. The former nine names are categories, not writable symbols

The following IDs remain reserved as broad family headings for documentation and migration:

- `arcana:invocation`
- `arcana:transform`
- `arcana:branch`
- `arcana:nexus`
- `arcana:repetition`
- `arcana:ward`
- `arcana:sigil`
- `arcana:core`
- `arcana:manifestation`

They are non-instantiable. A working must use a concrete symbol ID. This prevents a broad heading such as Transform from becoming an undefined "do anything" operation.

## 2. Typed value vocabulary

The first registry vocabulary is deliberately small:

| Type | Meaning |
|---|---|
| `SIGNAL` | Control-flow signal, not arbitrary data |
| `MANA` | Bounded mana quantity/packet |
| `BOOLEAN` | True/false result |
| `NUMBER` | Finite numeric value |
| `TEXT` | Bounded text value |
| `VECTOR3` | World position/vector |
| `ENTITY_REF` | Server-resolved entity reference |
| `BLOCK_REF` | Server-resolved block reference |

A reference is not permission to mutate the referenced object. Every world read/write still needs a registered server capability and permission checks. Ports are fixed by the registry; player data cannot add or alter them.

## 3. Concrete built-in symbols

### Control

| Symbol ID | Inputs | Outputs | Meaning |
|---|---|---|---|
| `arcana:control/entry` | optional `flow:SIGNAL` | `flow:SIGNAL` | Starts a working, or receives the owner signal at a nested control-region entry |
| `arcana:control/branch` | `condition:BOOLEAN`, `flow:SIGNAL` | `whenTrue:SIGNAL`, `whenFalse:SIGNAL` | Selects exactly one path from a validated condition |
| `arcana:control/join` | `left:SIGNAL`, `right:SIGNAL` | `flow:SIGNAL` | Explicitly joins two control paths; runtime synchronization semantics still need specification |
| `arcana:control/repeat` | `flow:SIGNAL` | `body:SIGNAL`, `completed:SIGNAL` | Bounded repetition; required integer `maxIterations` in 1..1024. The body is a nested sub-weave; loop-back is internal runtime behavior, not a raw graph cycle |

### Data

| Symbol ID | Inputs | Outputs | Meaning |
|---|---|---|---|
| `arcana:data/add` | `left:NUMBER`, `right:NUMBER` | `result:NUMBER` | Adds two finite numbers |
| `arcana:data/equal` | `left:NUMBER`, `right:NUMBER` | `result:BOOLEAN` | Tests numeric equality |
| `arcana:data/less_than` | `left:NUMBER`, `right:NUMBER` | `result:BOOLEAN` | Tests whether the first number is less than the second |

### Perception

| Symbol ID | Inputs | Outputs | Capability mapping | Meaning |
|---|---|---|---|---|
| `arcana:sense/block` | `position:VECTOR3` | `block:BLOCK_REF` | `arcana:world/read_block` | Reads a block reference at a position |
| `arcana:sense/entity` | `position:VECTOR3`, `radius:NUMBER` | `entity:ENTITY_REF` | `arcana:world/query_entities` | Queries entities within a bounded radius; result cardinality and filtering need further definition |
| `arcana:sense/light_level` | `position:VECTOR3` | `level:NUMBER` | `arcana:world/read_light` | Reads light level at a position |

### Mana

| Symbol ID | Inputs | Outputs | Capability mapping | Meaning |
|---|---|---|---|---|
| `arcana:mana/absorb` | `source:SIGNAL` | `mana:MANA` | `arcana:mana/absorb` | Requests mana from an authorized connected source |
| `arcana:mana/store` | `mana:MANA`, `flow:SIGNAL` | `stored:SIGNAL` | `arcana:mana/store` | Stores mana in a compatible host |
| `arcana:mana/release` | `stored:SIGNAL` | `mana:MANA` | `arcana:mana/release` | Releases stored mana subject to host and permission limits |

### References

| Symbol ID | Inputs | Outputs | Parameters |
|---|---|---|---|
| `arcana:reference/position` | none | `position:VECTOR3` | Required finite decimal `x`, `y`, `z` |
| `arcana:reference/number` | none | `number:NUMBER` | Required finite decimal `value` |
| `arcana:reference/entity` | none | `entity:ENTITY_REF` | Required identifier `selector`; selector grammar and permissions are not yet implemented |

### Constraints and effects

| Symbol ID | Inputs | Outputs | Capability mapping | Meaning |
|---|---|---|---|---|
| `arcana:constraint/ward` | `area:VECTOR3`, `flow:SIGNAL` | `permitted:SIGNAL` | `arcana:world/ward_check` | Applies a registered ward policy to a candidate operation |
| `arcana:effect/light` | `position:VECTOR3`, `level:NUMBER`, `flow:SIGNAL` | `complete:SIGNAL` | `arcana:world/set_light` | Requests a bounded light effect through a server capability |

Capability IDs in this table are identifiers only. They do not mean that the corresponding world operation, permission policy, or runtime adapter already exists.

## 4. Important gaps intentionally left open

This inventory is not a complete magic system. Before adding more symbols, the following contracts need explicit design:

- whether control flow and value flow are separate connection domains;
- how multi-result entity queries are represented without hidden nondeterminism;
- how a join knows whether it waits for both branches or accepts the first;
- whether additional comparison operators should be added as separate concrete symbols;
- how positions are bounded to dimensions, loaded chunks, and permitted ranges;
- how mana sources, storage capacity, rates, and losses are represented;
- how ward policies are authored from existing registered predicates;
- how output effects are budgeted and prevented from mutating arbitrary world state.

A symbol must have a narrow, testable contract. New capabilities are added as new Arcana-owned definitions, not as player-created operations or arbitrary parameter strings.

## 5. Registry contract

Each concrete definition owns:
- namespaced symbol ID and family;
- immutable typed input and output ports;
- immutable parameter schema, including requiredness and bounds;
- control-flow classification;
- structural complexity weight;
- optional registered capability ID;
- whether it is a concrete instantiable symbol or a non-instantiable family heading.

Validation rejects unknown IDs, attempts to instantiate family headings, unknown or missing parameters, malformed parameter types, and out-of-range values. Canonical connections are checked against the source symbol's registered output ports and target symbol's registered input ports; unknown ports, incompatible types, duplicate bindings, unbound required inputs, and raw cycles are rejected. Structural complexity is summed from registry-owned symbol weights, not from the source language. Player-authored instances contain only an instance ID, a canonical symbol ID, and parameter values. They never contain port declarations, capability mappings, or executable semantics.
