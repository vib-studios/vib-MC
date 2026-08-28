package net.vibmc.world;

import net.vibmc.inventory.Inventory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-world storage for the containers that live at a block position. Chunks persist block
 * states only, so a chest's contents are kept here and saved alongside the world.
 */
public final class BlockEntities {
    public static final int CHEST_SIZE = 27;

    private final Map<Long, Inventory> containers = new ConcurrentHashMap<>();
    private final Map<Long, Furnace> furnaces = new ConcurrentHashMap<>();

    /** The container at a position, created on first use. */
    public Inventory container(int x, int y, int z, String title, int size) {
        return containers.computeIfAbsent(pack(x, y, z), key -> new Inventory(title, size));
    }

    public Inventory existing(int x, int y, int z) {
        return containers.get(pack(x, y, z));
    }

    /** Drops the container when its block is broken. Returns what it held, or null. */
    public Inventory remove(int x, int y, int z) {
        Furnace furnace = furnaces.remove(pack(x, y, z));
        Inventory removed = containers.remove(pack(x, y, z));
        if (furnace != null && removed == null) return furnace.slots();
        return removed;
    }

    /** The furnace at a position, created on first use. */
    public Furnace furnace(int x, int y, int z) {
        return furnaces.computeIfAbsent(pack(x, y, z), key -> new Furnace());
    }

    public Map<Long, Inventory> all() {
        return containers;
    }

    public Map<Long, Furnace> allFurnaces() {
        return furnaces;
    }

    /** Advances every furnace in this world. */
    public void tick(World world) {
        for (Map.Entry<Long, Furnace> entry : furnaces.entrySet()) {
            entry.getValue().tick(world, entry.getKey());
        }
    }

    public void clear() {
        containers.clear();
        furnaces.clear();
    }

    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3ffffff) << 38) | ((long) (y & 0xfff) << 26) | (z & 0x3ffffff);
    }

    public static int unpackX(long packed) { return signed((int) (packed >> 38), 26); }
    public static int unpackY(long packed) { return (int) ((packed >> 26) & 0xfff); }
    public static int unpackZ(long packed) { return signed((int) (packed & 0x3ffffff), 26); }

    private static int signed(int value, int bits) {
        int mask = 1 << (bits - 1);
        return (value & (mask - 1)) - (value & mask);
    }
}
