# Casting System Design

Status: **first rules prototype implemented; Minecraft items and UI are not implemented yet**

This document defines the player-facing casting layer. The language engine describes a spell, but a player needs a learned spell and a suitable medium to invoke it in the world.

## 1. Three separate concepts

Arcana must not collapse these into one level:
- **Caster rank:** the player's current magical capability and control.
- **Spell rank:** the complexity and power of the spell itself.
- **Medium capacity:** the maximum spell rank a particular tool can safely channel or anchor.

A high-rank caster may cast lower-rank spells. A low-rank caster cannot bypass their own rank limit by holding a powerful focus crystal. A high-rank caster also cannot force a weak focus crystal to channel a spell beyond that wand's capacity.

The spell may additionally declare a minimum caster rank. This represents knowledge, control, or a special prerequisite that cannot be bypassed merely by having a powerful medium.

## 2. Initial rank ladder

| Rank | Name | Intended progression |
|---:|---|---|
| 0 | Novice | Basic magical awareness and simple cantrips |
| 1 | Apprentice | Elementary spells and basic tools |
| 2 | Adept | Reliable combat, utility, and crafting magic |
| 3 | Expert | Complex effects and multi-step spells |
| 4 | Master | Advanced spell structures and large-area effects |
| 5 | Archmage | High-order magic, advanced rituals, space manipulation research |
| 6 | Sovereign | Exceptional world-scale magic and dimension projects |

These names and boundaries are data-driven design defaults, not final balance values. Spell rank, required caster rank, resource cost, and medium capacity remain independent fields.

## 3. Casting media

### Grimoire / spellbook
- Stores learned spells, notes, diagrams, language definitions, and spell source.
- Provides the primary interface for browsing, studying, editing, compiling, and organizing spells.
- A normal grimoire is not automatically a casting focus. It does not directly cast a spell unless a special book explicitly has a casting capability.
- Advanced books may contain multiple volumes, modules, annotations, and ritual instructions.

### Focus crystal
- A non-weapon magical focus for stabilizing and directing a spell.
- Its quality may improve precision or support a limited spell rank.
- It does not grant the holder knowledge or caster rank.

### Scroll
- A prepared, usually consumable spell carrier.
- Stores a compiled and validated spell payload.
- The user still needs to satisfy the scroll's activation requirements unless the scroll explicitly contains a permitted release mechanism.
- Imported or player-authored scroll data must be revalidated by the server.

### Magic circle
- A ritual anchor, not simply a larger wand.
- Defines geometry, participants, anchors, materials, and staged conditions.
- Supports effects that are too long-running, spatially extensive, or resource-intensive for ordinary direct casting.
- May distribute eligible costs and roles across participants, but must not silently bypass each participant's permissions or ritual requirements.

Later media may include rings, talismans, catalysts, relics, magical machines, and innate casting. Each must declare its function rather than receiving an unrestricted generic bonus.

## 4. Casting modes
- **Direct:** one caster invokes a spell through a compatible direct-casting medium.
- **Ritual:** a prepared circle or other ritual anchor coordinates a multi-stage operation.
- **Prepared release:** a validated scroll or charged artifact releases a stored effect under explicit activation rules.
- **Innate:** a future special path for high-rank casters or creatures, with its own limitations and balancing.

A grimoire is primarily an authoring and knowledge interface. A magic circle is primarily a ritual interface. They should not be treated as interchangeable items.

## 5. Learning and ownership

Possessing a spell source is not identical to understanding the spell.

A spell-learning flow should eventually support:
1. discover or receive a spell source;
2. inspect its language, diagrams, prerequisites, and warnings;
3. decode or study it;
4. satisfy its knowledge and caster-rank requirements;
5. practice or complete a research task;
6. add the spell to a player's learned spell collection.

Some simple spells may be taught directly. Complex spells may require research, multiple prerequisites, or a complete ritual theory. A book can preserve a spell that its current holder has not yet learned.

## 6. Server-side casting pipeline

1. Player selects a learned spell.
2. The server resolves the canonical spell identity and version.
3. The server checks caster rank and spell-specific prerequisites.
4. The server checks the medium, mode, and medium capacity.
5. The server checks target, world state, permissions, materials, mana, cooldown, and execution budget.
6. Resources are reserved.
7. The server creates a casting or ritual execution record.
8. The runtime executes bounded stages and commits approved world changes.
9. The server records success, failure, cancellation, and resource settlement.

Client UI is presentation only. The client never decides that a spell is valid and never performs authoritative world mutation.

## 7. High-rank casting rule

The default direct-casting rank rule is:

    spell rank <= caster rank

The spell's minimum caster rank must also be met, and the medium must support the spell's rank and casting mode.

This naturally permits a Master to cast Apprentice spells without requiring a separate exception for every lower tier. It does not permit an Apprentice to cast a Master spell merely by borrowing a Sovereign's staff.

Special spells may impose additional requirements, such as a particular school, learned prerequisite, ritual role, or world condition. Such requirements must be explicit spell metadata, not hidden exceptions in item code.

## 8. Current implementation boundary

dev.arcana.casting.ArcanaCastingRules currently provides a loader-independent rules prototype for:
- caster rank;
- spell rank and minimum caster rank;
- medium kind and maximum supported spell rank;
- direct versus ritual casting support;
- compatible medium declarations;
- structured rejection codes.

It does **not** register Minecraft items, add inventory or GUI interactions, consume mana, implement learning, or execute spells. Those are separate implementation steps and must not be described as finished.

## 9. Next vertical slice

Implement the first playable loop in this order:
1. Register a basic grimoire item that opens a spellbook screen.
2. Add a simple spell collection data model and a few built-in test spells.
3. Register a basic wand as the first direct-casting medium.
4. Add a server-authoritative cast request and validation using ArcanaCastingRules.
5. Add a harmless test spell with a visible effect.
6. Add a magic-circle block/entity as a ritual anchor only after direct casting works.
7. Add rank progression and spell learning after the first casting loop is reliable.

The first playable slice should prove that a player can open a book, select a learned spell, use a wand, receive clear rejection reasons when requirements fail, and successfully cast one harmless spell.