package com.github.squi2rel.vp.render;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ExternalTextureRegistry {
    private final Map<Integer, Registration> active = new LinkedHashMap<>();
    private long nextGeneration;

    public synchronized Acquisition acquire(int rawTextureId) {
        Registration existing = active.get(rawTextureId);
        if (existing != null) return new Acquisition(existing, false);
        Registration created = new Registration(rawTextureId, ++nextGeneration);
        active.put(rawTextureId, created);
        return new Acquisition(created, true);
    }

    public synchronized Optional<Registration> release(int rawTextureId) {
        return Optional.ofNullable(active.remove(rawTextureId));
    }

    public synchronized List<Registration> clear() {
        List<Registration> registrations = List.copyOf(active.values());
        active.clear();
        return registrations;
    }

    public synchronized int size() {
        return active.size();
    }

    public record Acquisition(Registration registration, boolean created) {
    }

    public record Registration(int rawTextureId, long generation) {
        public String identifierPath() {
            return "external_texture/" + rawTextureId + "/" + generation;
        }
    }
}
