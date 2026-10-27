package com.infyss.projects;

import java.lang.reflect.Array;
import java.util.*;
import java.util.regex.Pattern;

/** Reloadable field rules. Each payload uses one immutable policy snapshot. */
public final class MaskPolicy {
    private static final Pattern NOISE = Pattern.compile("[_\\-\\s]");
    private static final int MAX_DEPTH = 128;
    private static final class Snapshot {
        final Map<String, Masks.Mask> rules;
        final Masks.Mask fallback;
        Snapshot(Map<String, Masks.Mask> rules, Masks.Mask fallback) {
            this.rules = Collections.unmodifiableMap(new HashMap<>(rules));
            this.fallback = fallback;
        }
        Masks.Mask mask(String field) { return rules.getOrDefault(key(field), fallback); }
    }
    private volatile Snapshot state = new Snapshot(Collections.emptyMap(), Masks.none());

    public MaskPolicy defaultRule(String spec) { return defaultRule(Masks.parse(spec)); }
    public synchronized MaskPolicy defaultRule(Masks.Mask mask) {
        state = new Snapshot(state.rules, Objects.requireNonNull(mask, "mask"));
        return this;
    }
    public synchronized MaskPolicy rule(String field, Masks.Mask mask) {
        Map<String, Masks.Mask> next = new HashMap<>(state.rules);
        next.put(key(field), Objects.requireNonNull(mask, "mask"));
        state = new Snapshot(next, state.fallback);
        return this;
    }
    public MaskPolicy rule(String field, String spec) { return rule(field, Masks.parse(spec)); }

    /** Replaces rules atomically; invalid or ambiguous configuration leaves the policy unchanged. */
    public synchronized MaskPolicy load(Map<String, String> fieldToSpec) {
        Objects.requireNonNull(fieldToSpec, "fieldToSpec");
        Map<String, Masks.Mask> next = new HashMap<>();
        for (Map.Entry<String, String> e : fieldToSpec.entrySet()) {
            String field = key(e.getKey());
            if (next.containsKey(field)) throw new IllegalArgumentException("Duplicate normalized field: " + field);
            next.put(field, Masks.parse(e.getValue()));
        }
        state = new Snapshot(next, state.fallback);
        return this;
    }
    public MaskPolicy load(Properties props, String prefix) {
        Objects.requireNonNull(props, "props");
        Objects.requireNonNull(prefix, "prefix");
        Map<String, String> specs = new HashMap<>();
        for (String name : props.stringPropertyNames()) {
            if (name.startsWith(prefix)) specs.put(name.substring(prefix.length()), props.getProperty(name));
        }
        return load(specs);
    }
    public String apply(String field, String value) {
        Snapshot snapshot = state;
        return snapshot.mask(field).apply(value);
    }

    /**
     * Copies maps, iterables and arrays recursively. Collection elements inherit their field rule;
     * nested map entries use their own field names. Non-string scalar values retain their type
     * when unchanged, otherwise become masked strings. Unsupported objects and cycles are rejected.
     */
    public Map<String, Object> applyAll(Map<?, ?> in) {
        Objects.requireNonNull(in, "in");
        return copyMap(in, state, new IdentityHashMap<>(), 0);
    }
    private static Map<String, Object> copyMap(Map<?, ?> in, Snapshot snapshot,
                                              IdentityHashMap<Object, Boolean> active, int depth) {
        enter(in, active, depth);
        try {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : in.entrySet()) {
                if (!(e.getKey() instanceof String)) throw new IllegalArgumentException("Map keys must be strings");
                String field = (String) e.getKey();
                out.put(field, copy(e.getValue(), field, snapshot, active, depth + 1));
            }
            return out;
        } finally { active.remove(in); }
    }
    private static Object copy(Object value, String field, Snapshot snapshot,
                               IdentityHashMap<Object, Boolean> active, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("Payload exceeds maximum depth " + MAX_DEPTH);
        if (value == null) return null;
        if (value instanceof Map) return copyMap((Map<?, ?>) value, snapshot, active, depth);
        if (value instanceof Iterable || value.getClass().isArray()) {
            enter(value, active, depth);
            try {
                List<Object> out = new ArrayList<>();
                if (value instanceof Iterable) {
                    for (Object element : (Iterable<?>) value) out.add(copy(element, field, snapshot, active, depth + 1));
                } else {
                    for (int i = 0; i < Array.getLength(value); i++)
                        out.add(copy(Array.get(value, i), field, snapshot, active, depth + 1));
                }
                return out;
            } finally { active.remove(value); }
        }
        if (!(value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value instanceof Character))
            throw new IllegalArgumentException("Unsupported payload type: " + value.getClass().getName());
        String text = value.toString();
        String masked = snapshot.mask(field).apply(text);
        return text.equals(masked) ? value : masked;
    }
    private static void enter(Object value, IdentityHashMap<Object, Boolean> active, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("Payload exceeds maximum depth " + MAX_DEPTH);
        if (active.put(value, Boolean.TRUE) != null) throw new IllegalArgumentException("Cyclic payload");
    }
    private static String key(String field) {
        String normalized = NOISE.matcher(Objects.requireNonNull(field, "field")).replaceAll("").toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("Field name must not be empty");
        return normalized;
    }
}
