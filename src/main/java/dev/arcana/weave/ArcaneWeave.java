package dev.arcana.weave;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Loader-independent domain model for player-authored magical weaves.
 *
 * The model deliberately uses magical authoring vocabulary. Its graph is a
 * logical construction and is not bound to a book, item, block, or ritual circle.
 * Hosts and installations are separate physical concerns.
 */
public final class ArcaneWeave {
    public static final int VERSION = 1;
    public static final int MAX_GLYPHS = 512;
    public static final int MAX_VEINS = 2_048;
    public static final int MAX_REPETITIONS = 1_024;

    private ArcaneWeave() { }

    public enum Essence {
        MANA, SIGNAL, BOOLEAN, NUMBER, TEXT, VECTOR3, ENTITY_REF, BLOCK_REF
    }

    public enum ControlFlow { ENTRY, LINEAR, BRANCH, JOIN, LOOP, TERMINAL, DATA }
    public enum ParameterType { INTEGER, DECIMAL, BOOLEAN, IDENTIFIER }

    public record PortDefinition(String id, Essence essence, boolean required) {
        public PortDefinition {
            requireIdentifier(id, "port id");
            Objects.requireNonNull(essence, "essence");
        }
    }

    public record ParameterDefinition(String id, ParameterType type, boolean required,
                                      Long minimum, Long maximum) {
        public ParameterDefinition {
            requireIdentifier(id, "parameter id");
            Objects.requireNonNull(type, "parameter type");
            if (minimum != null && maximum != null && minimum > maximum)
                throw new IllegalArgumentException("Parameter minimum exceeds maximum.");
            if (type != ParameterType.INTEGER && (minimum != null || maximum != null))
                throw new IllegalArgumentException("Only integer parameters may declare integer bounds.");
        }
    }

    /**
     * Arcana-owned semantic contract. Ports and parameter schemas come only
     * from this definition; player-authored glyph instances cannot override them.
     */
    public record StandardSymbolDefinition(
            String symbolId,
            String family,
            Map<String, PortDefinition> inputs,
            Map<String, PortDefinition> outputs,
            Map<String, ParameterDefinition> parameters,
            ControlFlow controlFlow,
            int complexityWeight,
            String capabilityId,
            boolean instantiable) {
        public StandardSymbolDefinition {
            requireNamespacedId(symbolId, "standard symbol id");
            requireIdentifier(family, "symbol family");
            inputs = immutablePorts(inputs, "inputs");
            outputs = immutablePorts(outputs, "outputs");
            parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
            parameters.forEach((id, definition) -> {
                requireIdentifier(id, "parameter key");
                if (!id.equals(definition.id())) throw new IllegalArgumentException("Parameter key mismatch.");
            });
            Objects.requireNonNull(controlFlow, "control flow");
            if (complexityWeight < 1 || complexityWeight > 10_000)
                throw new IllegalArgumentException("Invalid symbol complexity weight.");
            if (capabilityId != null) requireNamespacedId(capabilityId, "capability id");
            if (!instantiable && capabilityId != null)
                throw new IllegalArgumentException("Abstract symbol categories cannot map to capabilities.");
        }
    }

    /** Canonical symbol reference used by the registry-backed authoring model. */
    public record CanonicalGlyph(String instanceId, String symbolId, Map<String, String> parameters) {
        public CanonicalGlyph {
            requireIdentifier(instanceId, "glyph instance id");
            requireNamespacedId(symbolId, "standard symbol id");
            parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
            if (parameters.size() > 32) throw new IllegalArgumentException("Too many symbol parameters.");
        }
    }

    public record CanonicalWeave(String id, int version, List<CanonicalGlyph> glyphs) {
        public CanonicalWeave {
            requireNamespacedId(id, "weave id");
            if (version < 1) throw new IllegalArgumentException("Weave version must be positive.");
            glyphs = List.copyOf(Objects.requireNonNull(glyphs, "glyphs"));
            if (glyphs.size() > MAX_GLYPHS) throw new IllegalArgumentException("Weave exceeds the glyph limit.");
        }
    }

    public record SymbolDiagnostic(String code, String message, String glyphId) { }

    public record SymbolValidation(List<SymbolDiagnostic> diagnostics) {
        public SymbolValidation { diagnostics = List.copyOf(diagnostics); }
        public boolean valid() { return diagnostics.isEmpty(); }
    }

    /** Immutable Arcana-owned registry. No player-supplied definition can enter it. */
    public static final class StandardSymbolRegistry {
        private final Map<String, StandardSymbolDefinition> definitions;

        private StandardSymbolRegistry(Map<String, StandardSymbolDefinition> definitions) {
            this.definitions = Map.copyOf(new LinkedHashMap<>(definitions));
        }

        public static StandardSymbolRegistry builtins() {
            Map<String, StandardSymbolDefinition> defs = new LinkedHashMap<>();

            // Legacy semantic headings remain discoverable but cannot be instantiated.
            for (String id : List.of("invocation", "transform", "branch", "nexus",
                    "repetition", "ward", "sigil", "core", "manifestation")) {
                add(defs, symbol("arcana:" + id, "category", Map.of(), Map.of(), Map.of(),
                        ControlFlow.DATA, 1, null, false));
            }

            add(defs, symbol("arcana:control/entry", "control",
                    Map.of(), ports("flow", Essence.SIGNAL), Map.of(),
                    ControlFlow.ENTRY, 1, null, true));
            add(defs, symbol("arcana:control/branch", "control",
                    ports("condition", Essence.BOOLEAN, "flow", Essence.SIGNAL),
                    ports("whenTrue", Essence.SIGNAL, "whenFalse", Essence.SIGNAL), Map.of(),
                    ControlFlow.BRANCH, 3, null, true));
            add(defs, symbol("arcana:control/join", "control",
                    ports("left", Essence.SIGNAL, "right", Essence.SIGNAL),
                    ports("flow", Essence.SIGNAL), Map.of(),
                    ControlFlow.JOIN, 2, null, true));
            add(defs, symbol("arcana:control/repeat", "control",
                    ports("flow", Essence.SIGNAL), ports("next", Essence.SIGNAL),
                    params(integerParameter("maxIterations", true, 1, MAX_REPETITIONS)),
                    ControlFlow.LOOP, 4, null, true));

            add(defs, symbol("arcana:data/add", "data",
                    ports("left", Essence.NUMBER, "right", Essence.NUMBER),
                    ports("result", Essence.NUMBER), Map.of(),
                    ControlFlow.DATA, 1, null, true));
            add(defs, symbol("arcana:data/equal", "data",
                    ports("left", Essence.NUMBER, "right", Essence.NUMBER),
                    ports("result", Essence.BOOLEAN), Map.of(),
                    ControlFlow.DATA, 1, null, true));
            add(defs, symbol("arcana:data/less_than", "data",
                    ports("left", Essence.NUMBER, "right", Essence.NUMBER),
                    ports("result", Essence.BOOLEAN), Map.of(),
                    ControlFlow.DATA, 1, null, true));

            add(defs, symbol("arcana:sense/block", "perception",
                    ports("position", Essence.VECTOR3), ports("block", Essence.BLOCK_REF), Map.of(),
                    ControlFlow.DATA, 2, "arcana:world/read_block", true));
            add(defs, symbol("arcana:sense/entity", "perception",
                    ports("position", Essence.VECTOR3, "radius", Essence.NUMBER),
                    ports("entity", Essence.ENTITY_REF), Map.of(),
                    ControlFlow.DATA, 3, "arcana:world/query_entities", true));
            add(defs, symbol("arcana:sense/light_level", "perception",
                    ports("position", Essence.VECTOR3), ports("level", Essence.NUMBER), Map.of(),
                    ControlFlow.DATA, 2, "arcana:world/read_light", true));

            add(defs, symbol("arcana:mana/absorb", "mana",
                    ports("source", Essence.SIGNAL), ports("mana", Essence.MANA), Map.of(),
                    ControlFlow.LINEAR, 2, "arcana:mana/absorb", true));
            add(defs, symbol("arcana:mana/store", "mana",
                    ports("mana", Essence.MANA, "flow", Essence.SIGNAL),
                    ports("stored", Essence.SIGNAL), Map.of(),
                    ControlFlow.LINEAR, 2, "arcana:mana/store", true));
            add(defs, symbol("arcana:mana/release", "mana",
                    ports("stored", Essence.SIGNAL), ports("mana", Essence.MANA), Map.of(),
                    ControlFlow.LINEAR, 2, "arcana:mana/release", true));

            add(defs, symbol("arcana:reference/position", "reference",
                    Map.of(), ports("position", Essence.VECTOR3),
                    params(decimalParameter("x"), decimalParameter("y"), decimalParameter("z")),
                    ControlFlow.DATA, 1, null, true));
            add(defs, symbol("arcana:reference/entity", "reference",
                    Map.of(), ports("entity", Essence.ENTITY_REF),
                    params(identifierParameter("selector")), ControlFlow.DATA, 1, null, true));

            add(defs, symbol("arcana:constraint/ward", "constraint",
                    ports("area", Essence.VECTOR3, "flow", Essence.SIGNAL),
                    ports("permitted", Essence.SIGNAL), Map.of(),
                    ControlFlow.LINEAR, 4, "arcana:world/ward_check", true));
            add(defs, symbol("arcana:effect/light", "effect",
                    ports("position", Essence.VECTOR3, "level", Essence.NUMBER, "flow", Essence.SIGNAL),
                    ports("complete", Essence.SIGNAL), Map.of(),
                    ControlFlow.TERMINAL, 3, "arcana:world/set_light", true));

            return new StandardSymbolRegistry(defs);
        }

        public boolean contains(String symbolId) { return definitions.containsKey(symbolId); }
        public boolean isInstantiable(String symbolId) {
            StandardSymbolDefinition definition = definitions.get(symbolId);
            return definition != null && definition.instantiable();
        }
        public StandardSymbolDefinition definition(String symbolId) { return definitions.get(symbolId); }
        public Set<String> allowedParameters(String symbolId) {
            StandardSymbolDefinition definition = definitions.get(symbolId);
            return definition == null ? Set.of() : definition.parameters().keySet();
        }
        public Set<String> symbolIds() { return definitions.keySet(); }

        private static void add(Map<String, StandardSymbolDefinition> defs, StandardSymbolDefinition definition) {
            if (defs.putIfAbsent(definition.symbolId(), definition) != null)
                throw new IllegalStateException("Duplicate built-in symbol: " + definition.symbolId());
        }
        private static StandardSymbolDefinition symbol(String id, String family,
                Map<String, PortDefinition> inputs, Map<String, PortDefinition> outputs,
                Map<String, ParameterDefinition> parameters, ControlFlow flow,
                int weight, String capability, boolean instantiable) {
            return new StandardSymbolDefinition(id, family, inputs, outputs, parameters,
                    flow, weight, capability, instantiable);
        }
        private static Map<String, PortDefinition> ports(Object... values) {
            if (values.length % 2 != 0) throw new IllegalArgumentException("Port declarations need id/type pairs.");
            Map<String, PortDefinition> result = new LinkedHashMap<>();
            for (int i = 0; i < values.length; i += 2) {
                String id = (String) values[i];
                Essence essence = (Essence) values[i + 1];
                result.put(id, new PortDefinition(id, essence, true));
            }
            return Map.copyOf(result);
        }
        private static Map<String, PortDefinition> immutablePorts(Map<String, PortDefinition> values, String label) {
            Objects.requireNonNull(values, label);
            Map<String, PortDefinition> copy = new LinkedHashMap<>();
            values.forEach((id, port) -> {
                requireIdentifier(id, label + " port id");
                if (!id.equals(port.id())) throw new IllegalArgumentException("Port key mismatch.");
                copy.put(id, port);
            });
            return Map.copyOf(copy);
        }
        private static ParameterDefinition integerParameter(String id, boolean required, long min, long max) {
            return new ParameterDefinition(id, ParameterType.INTEGER, required, min, max);
        }
        private static ParameterDefinition decimalParameter(String id) {
            return new ParameterDefinition(id, ParameterType.DECIMAL, true, null, null);
        }
        private static ParameterDefinition identifierParameter(String id) {
            return new ParameterDefinition(id, ParameterType.IDENTIFIER, true, null, null);
        }
        private static Map<String, ParameterDefinition> params(ParameterDefinition... values) {
            Map<String, ParameterDefinition> result = new LinkedHashMap<>();
            for (ParameterDefinition value : values) result.put(value.id(), value);
            return Map.copyOf(result);
        }
    }

    public static SymbolValidation validateCanonical(CanonicalWeave weave, StandardSymbolRegistry registry) {
        Objects.requireNonNull(weave, "weave");
        Objects.requireNonNull(registry, "registry");
        List<SymbolDiagnostic> issues = new ArrayList<>();
        if (weave.glyphs().isEmpty()) {
            issues.add(new SymbolDiagnostic("CANONICAL_WEAVE_EMPTY", "A canonical weave must contain at least one standard symbol.", null));
        }
        Set<String> instances = new HashSet<>();
        for (CanonicalGlyph glyph : weave.glyphs()) {
            if (!instances.add(glyph.instanceId())) {
                issues.add(new SymbolDiagnostic("CANONICAL_DUPLICATE_INSTANCE", "Duplicate glyph instance id.", glyph.instanceId()));
            }
            StandardSymbolDefinition definition = registry.definition(glyph.symbolId());
            if (definition == null) {
                issues.add(new SymbolDiagnostic("CANONICAL_UNKNOWN_SYMBOL", "Symbol is not registered by Arcana: " + glyph.symbolId(), glyph.instanceId()));
                continue;
            }
            if (!definition.instantiable()) {
                issues.add(new SymbolDiagnostic("CANONICAL_ABSTRACT_SYMBOL",
                        "This registry entry is a semantic category, not a concrete writable symbol.", glyph.instanceId()));
                continue;
            }
            for (String parameter : glyph.parameters().keySet()) {
                if (!definition.parameters().containsKey(parameter)) {
                    issues.add(new SymbolDiagnostic("CANONICAL_UNKNOWN_PARAMETER",
                            "Parameter is not defined by the standard symbol: " + parameter, glyph.instanceId()));
                }
            }
            for (ParameterDefinition parameter : definition.parameters().values()) {
                String value = glyph.parameters().get(parameter.id());
                if (value == null) {
                    if (parameter.required()) issues.add(new SymbolDiagnostic("CANONICAL_MISSING_PARAMETER",
                            "Required parameter is missing: " + parameter.id(), glyph.instanceId()));
                    continue;
                }
                try {
                    switch (parameter.type()) {
                        case INTEGER -> {
                            long parsed = Long.parseLong(value);
                            if ((parameter.minimum() != null && parsed < parameter.minimum())
                                    || (parameter.maximum() != null && parsed > parameter.maximum())) {
                                issues.add(new SymbolDiagnostic("CANONICAL_PARAMETER_RANGE",
                                        "Parameter is outside its allowed range: " + parameter.id(), glyph.instanceId()));
                            }
                        }
                        case DECIMAL -> {
                            double parsed = Double.parseDouble(value);
                            if (!Double.isFinite(parsed)) throw new NumberFormatException();
                        }
                        case BOOLEAN -> {
                            if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException();
                        }
                        case IDENTIFIER -> requireIdentifier(value, "parameter value");
                    }
                } catch (RuntimeException ex) {
                    issues.add(new SymbolDiagnostic("CANONICAL_PARAMETER_TYPE",
                            "Parameter has an invalid value: " + parameter.id(), glyph.instanceId()));
                }
            }
        }
        return new SymbolValidation(issues);
    }

    public enum GlyphKind { INVOCATION, TRANSFORM, BRANCH, NEXUS, REPETITION, WARD, SIGIL, CORE, MANIFESTATION }
    public enum HostKind { PAGE, ITEM, BLOCK, STRUCTURE_PART }

    public record Glyph(String id, GlyphKind kind, Map<String, Essence> offerings,
                        Map<String, Essence> receptions, Map<String, String> inscriptions) {
        public Glyph {
            requireIdentifier(id, "glyph id");
            Objects.requireNonNull(kind, "kind");
            offerings = immutableEssences(offerings, "offerings");
            receptions = immutableEssences(receptions, "receptions");
            inscriptions = Map.copyOf(Objects.requireNonNull(inscriptions, "inscriptions"));
            if (inscriptions.size() > 32) throw new IllegalArgumentException("Too many glyph inscriptions.");
            if (kind == GlyphKind.INVOCATION && (!receptions.isEmpty() || offerings.isEmpty()))
                throw new IllegalArgumentException("An INVOCATION offers essences and receives none.");
            if (kind == GlyphKind.MANIFESTATION && (!offerings.isEmpty() || receptions.isEmpty()))
                throw new IllegalArgumentException("A MANIFESTATION receives essences and offers none.");
            if (kind == GlyphKind.REPETITION) {
                int count;
                try { count = Integer.parseInt(inscriptions.getOrDefault("maxIterations", "")); }
                catch (NumberFormatException ex) { throw new IllegalArgumentException("REPETITION requires a bounded maxIterations."); }
                if (count < 1 || count > MAX_REPETITIONS)
                    throw new IllegalArgumentException("maxIterations is outside the supported bound.");
            }
        }
    }

    /** A mana vein connects one named offering to one compatible reception. */
    public record ManaVein(String fromGlyph, String fromOffering, String toGlyph, String toReception) {
        public ManaVein {
            requireIdentifier(fromGlyph, "source glyph");
            requireIdentifier(fromOffering, "source offering");
            requireIdentifier(toGlyph, "target glyph");
            requireIdentifier(toReception, "target reception");
        }
    }

    public record Weave(String id, int version, List<Glyph> glyphs, List<ManaVein> veins,
                        Set<String> invocations, Set<String> manifestations) {
        public Weave {
            requireNamespacedId(id, "weave id");
            if (version < 1) throw new IllegalArgumentException("Weave version must be positive.");
            glyphs = List.copyOf(Objects.requireNonNull(glyphs));
            veins = List.copyOf(Objects.requireNonNull(veins));
            invocations = Set.copyOf(Objects.requireNonNull(invocations));
            manifestations = Set.copyOf(Objects.requireNonNull(manifestations));
        }
    }

    /** A host exposes physical capacity and named attachment anchors. */
    public record Host(String id, HostKind kind, int glyphCapacity, Set<String> anchors) {
        public Host {
            requireNamespacedId(id, "host id");
            Objects.requireNonNull(kind, "kind");
            if (glyphCapacity < 1 || glyphCapacity > MAX_GLYPHS)
                throw new IllegalArgumentException("Host capacity must be between 1 and " + MAX_GLYPHS + ".");
            anchors = Set.copyOf(Objects.requireNonNull(anchors));
            if (anchors.isEmpty()) throw new IllegalArgumentException("A host must expose at least one anchor.");
            anchors.forEach(anchor -> requireIdentifier(anchor, "host anchor"));
        }
    }

    /** Binds a logical glyph to a physical host anchor. */
    public record Placement(String glyphId, String hostId, String anchor) {
        public Placement {
            requireIdentifier(glyphId, "glyph id");
            requireNamespacedId(hostId, "host id");
            requireIdentifier(anchor, "host anchor");
        }
    }

    /** Connections between different hosts must be explicitly installed. */
    public record HostLink(String firstHostId, String secondHostId) {
        public HostLink {
            requireNamespacedId(firstHostId, "first host id");
            requireNamespacedId(secondHostId, "second host id");
            if (firstHostId.equals(secondHostId)) throw new IllegalArgumentException("A host link must join two distinct hosts.");
        }
        public boolean joins(String first, String second) {
            return (firstHostId.equals(first) && secondHostId.equals(second))
                    || (firstHostId.equals(second) && secondHostId.equals(first));
        }
    }

    public record Installation(String id, String weaveId, List<Host> hosts,
                               List<Placement> placements, Set<HostLink> links) {
        public Installation {
            requireNamespacedId(id, "installation id");
            requireNamespacedId(weaveId, "weave id");
            hosts = List.copyOf(Objects.requireNonNull(hosts));
            placements = List.copyOf(Objects.requireNonNull(placements));
            links = Set.copyOf(Objects.requireNonNull(links));
        }
    }

    public record Diagnostic(String code, String message, String glyphId) {
        public Diagnostic { Objects.requireNonNull(code); Objects.requireNonNull(message); }
    }

    public record Validation(List<Diagnostic> diagnostics) {
        public Validation { diagnostics = List.copyOf(diagnostics); }
        public boolean valid() { return diagnostics.isEmpty(); }
    }

    public static Validation validate(Weave weave) {
        Objects.requireNonNull(weave, "weave");
        List<Diagnostic> issues = new ArrayList<>();
        if (weave.glyphs().isEmpty()) add(issues, "WEAVE_EMPTY", "A weave must contain at least one glyph.", null);
        if (weave.glyphs().size() > MAX_GLYPHS) add(issues, "WEAVE_GLYPH_LIMIT", "Weave exceeds the glyph limit.", null);
        if (weave.veins().size() > MAX_VEINS) add(issues, "WEAVE_VEIN_LIMIT", "Weave exceeds the mana-vein limit.", null);

        Map<String, Glyph> glyphs = new LinkedHashMap<>();
        for (Glyph glyph : weave.glyphs()) {
            if (glyphs.putIfAbsent(glyph.id(), glyph) != null)
                add(issues, "WEAVE_DUPLICATE_GLYPH", "Duplicate glyph id: " + glyph.id(), glyph.id());
        }
        if (weave.invocations().isEmpty()) add(issues, "WEAVE_NO_INVOCATION", "A weave needs an invocation glyph.", null);
        if (weave.manifestations().isEmpty()) add(issues, "WEAVE_NO_MANIFESTATION", "A weave needs a manifestation glyph.", null);
        for (String id : weave.invocations()) {
            Glyph glyph = glyphs.get(id);
            if (glyph == null) add(issues, "WEAVE_UNKNOWN_INVOCATION", "Unknown invocation glyph: " + id, id);
            else if (glyph.kind() != GlyphKind.INVOCATION) add(issues, "WEAVE_INVALID_INVOCATION", "Invocation must reference an INVOCATION glyph.", id);
        }
        for (String id : weave.manifestations()) {
            Glyph glyph = glyphs.get(id);
            if (glyph == null) add(issues, "WEAVE_UNKNOWN_MANIFESTATION", "Unknown manifestation glyph: " + id, id);
            else if (glyph.kind() != GlyphKind.MANIFESTATION) add(issues, "WEAVE_INVALID_MANIFESTATION", "Manifestation must reference a MANIFESTATION glyph.", id);
        }

        Map<String, Integer> incoming = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        Set<String> seenVeins = new HashSet<>();
        for (ManaVein vein : weave.veins()) {
            Glyph from = glyphs.get(vein.fromGlyph()), to = glyphs.get(vein.toGlyph());
            if (from == null || to == null) {
                add(issues, "WEAVE_UNKNOWN_GLYPH", "A mana vein references an unknown glyph.", null);
                continue;
            }
            String key = vein.fromGlyph() + ":" + vein.fromOffering() + "->" + vein.toGlyph() + ":" + vein.toReception();
            if (!seenVeins.add(key)) { add(issues, "WEAVE_DUPLICATE_VEIN", "Duplicate mana vein.", to.id()); continue; }
            Essence offered = from.offerings().get(vein.fromOffering());
            Essence received = to.receptions().get(vein.toReception());
            if (offered == null) { add(issues, "WEAVE_UNKNOWN_OFFERING", "Unknown offering: " + vein.fromOffering(), from.id()); continue; }
            if (received == null) { add(issues, "WEAVE_UNKNOWN_RECEPTION", "Unknown reception: " + vein.toReception(), to.id()); continue; }
            if (offered != received) { add(issues, "WEAVE_ESSENCE_MISMATCH", "Mana-vein endpoints must carry the same essence.", to.id()); continue; }
            String receptionKey = to.id() + ":" + vein.toReception();
            if (incoming.merge(receptionKey, 1, Integer::sum) > 1)
                add(issues, "WEAVE_RECEPTION_ALREADY_BOUND", "A reception may have only one incoming vein.", to.id());
            outgoing.computeIfAbsent(from.id(), ignored -> new ArrayList<>()).add(to.id());
        }

        for (Glyph glyph : glyphs.values()) {
            for (String reception : glyph.receptions().keySet()) {
                if (glyph.kind() != GlyphKind.INVOCATION && !incoming.containsKey(glyph.id() + ":" + reception))
                    add(issues, "WEAVE_UNBOUND_RECEPTION", "Unbound reception: " + reception, glyph.id());
            }
        }
        validateAcyclic(glyphs, outgoing, issues);
        validateReachability(glyphs, outgoing, weave.invocations(), issues);
        return new Validation(issues);
    }

    public static Validation validateInstallation(Weave weave, Installation installation) {
        Objects.requireNonNull(weave, "weave");
        Objects.requireNonNull(installation, "installation");
        List<Diagnostic> issues = new ArrayList<>(validate(weave).diagnostics());
        if (!installation.weaveId().equals(weave.id()))
            add(issues, "INSTALLATION_WEAVE_MISMATCH", "Installation references a different weave.", null);
        Map<String, Host> hosts = new HashMap<>();
        for (Host host : installation.hosts()) {
            if (hosts.putIfAbsent(host.id(), host) != null)
                add(issues, "INSTALLATION_DUPLICATE_HOST", "Duplicate host id: " + host.id(), null);
        }
        Map<String, Glyph> glyphs = new HashMap<>();
        weave.glyphs().forEach(glyph -> glyphs.putIfAbsent(glyph.id(), glyph));
        Map<String, String> placementByGlyph = new HashMap<>();
        Map<String, Integer> loadByHost = new HashMap<>();
        for (Placement placement : installation.placements()) {
            if (!glyphs.containsKey(placement.glyphId())) {
                add(issues, "INSTALLATION_UNKNOWN_GLYPH", "Placement references an unknown glyph.", placement.glyphId());
                continue;
            }
            if (placementByGlyph.putIfAbsent(placement.glyphId(), placement.hostId()) != null)
                add(issues, "INSTALLATION_DUPLICATE_PLACEMENT", "Glyph is placed more than once.", placement.glyphId());
            Host host = hosts.get(placement.hostId());
            if (host == null) { add(issues, "INSTALLATION_UNKNOWN_HOST", "Placement references an unknown host.", placement.glyphId()); continue; }
            if (!host.anchors().contains(placement.anchor()))
                add(issues, "INSTALLATION_UNKNOWN_ANCHOR", "Host does not expose anchor: " + placement.anchor(), placement.glyphId());
            loadByHost.merge(host.id(), 1, Integer::sum);
        }
        for (String glyphId : glyphs.keySet()) {
            if (!placementByGlyph.containsKey(glyphId))
                add(issues, "INSTALLATION_UNPLACED_GLYPH", "Glyph has no physical host placement.", glyphId);
        }
        for (Map.Entry<String, Integer> load : loadByHost.entrySet()) {
            Host host = hosts.get(load.getKey());
            if (host != null && load.getValue() > host.glyphCapacity())
                add(issues, "INSTALLATION_HOST_CAPACITY", "Host capacity is exceeded.", null);
        }
        for (ManaVein vein : weave.veins()) {
            String fromHost = placementByGlyph.get(vein.fromGlyph());
            String toHost = placementByGlyph.get(vein.toGlyph());
            if (fromHost != null && toHost != null && !fromHost.equals(toHost)) {
                boolean linked = installation.links().stream().anyMatch(link -> link.joins(fromHost, toHost));
                if (!linked) add(issues, "INSTALLATION_HOSTS_NOT_LINKED",
                        "A mana vein crossing hosts requires an explicit host link.", vein.toGlyph());
            }
        }
        for (HostLink link : installation.links()) {
            if (!hosts.containsKey(link.firstHostId()) || !hosts.containsKey(link.secondHostId()))
                add(issues, "INSTALLATION_LINK_UNKNOWN_HOST", "Host link references an uninstalled host.", null);
        }
        return new Validation(issues);
    }

    private static void validateAcyclic(Map<String, Glyph> glyphs, Map<String, List<String>> outgoing, List<Diagnostic> issues) {
        Map<String, Integer> indegree = new HashMap<>();
        glyphs.keySet().forEach(id -> indegree.put(id, 0));
        outgoing.values().forEach(targets -> targets.forEach(target -> indegree.merge(target, 1, Integer::sum)));
        List<String> queue = new ArrayList<>();
        indegree.forEach((id, degree) -> { if (degree == 0) queue.add(id); });
        int visited = 0;
        for (int i = 0; i < queue.size(); i++) {
            String id = queue.get(i); visited++;
            for (String target : outgoing.getOrDefault(id, List.of()))
                if (indegree.merge(target, -1, Integer::sum) == 0) queue.add(target);
        }
        if (visited != glyphs.size()) add(issues, "WEAVE_UNBOUNDED_CYCLE",
                "Weave paths must be acyclic; bounded repetition is represented by a REPETITION glyph, not a raw graph cycle.", null);
    }

    private static void validateReachability(Map<String, Glyph> glyphs, Map<String, List<String>> outgoing,
                                             Set<String> invocations, List<Diagnostic> issues) {
        Set<String> reached = new HashSet<>();
        List<String> queue = new ArrayList<>(invocations);
        for (int i = 0; i < queue.size(); i++) {
            String id = queue.get(i);
            if (!reached.add(id) || !glyphs.containsKey(id)) continue;
            queue.addAll(outgoing.getOrDefault(id, List.of()));
        }
        for (String id : glyphs.keySet())
            if (!reached.contains(id)) add(issues, "WEAVE_UNREACHABLE_GLYPH", "Glyph is unreachable from every invocation.", id);
    }

    private static Map<String, Essence> immutableEssences(Map<String, Essence> values, String label) {
        Objects.requireNonNull(values, label);
        if (values.size() > 32) throw new IllegalArgumentException("Too many " + label + ".");
        Map<String, Essence> copy = new LinkedHashMap<>();
        values.forEach((name, essence) -> {
            requireIdentifier(name, label + " name");
            copy.put(name, Objects.requireNonNull(essence, "essence"));
        });
        return Map.copyOf(copy);
    }

    private static void requireIdentifier(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!value.matches("[A-Za-z0-9_.-]{1,64}")) throw new IllegalArgumentException("Invalid " + label + ".");
    }
    private static void requireNamespacedId(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException(label + " must use namespace:path format.");
    }
    private static void add(List<Diagnostic> issues, String code, String message, String glyphId) {
        issues.add(new Diagnostic(code, message, glyphId));
    }
}