package net.vibmc.world.block;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A block's semantic kind, e.g. {@code oak_leaves}. Instances are interned by name so the
 * legacy {@code ==} comparisons used for entity clearing, leaf decay and drop lookup keep
 * working after the PacketEvents {@code StateType} removal.
 */
public final class BlockType {
    private static final Map<String, BlockType> CACHE = new ConcurrentHashMap<>();

    private final String name;

    private BlockType(String name) {
        this.name = name;
    }

    public static BlockType of(String name) {
        String key = name == null ? "air" : name.toLowerCase(Locale.ROOT);
        return CACHE.computeIfAbsent(key, BlockType::new);
    }

    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof BlockType && name.equals(((BlockType) o).name));
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}