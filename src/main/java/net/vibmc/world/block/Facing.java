package net.vibmc.world.block;

import java.util.Locale;

/** Horizontal facing, mapped to the four 1.12 block data values used by block entities. */
public final class Facing {
    public static final Facing NORTH = new Facing("north", 2);
    public static final Facing SOUTH = new Facing("south", 3);
    public static final Facing WEST = new Facing("west", 4);
    public static final Facing EAST = new Facing("east", 5);

    private final String name;
    private final int data;

    private Facing(String name, int data) {
        this.name = name;
        this.data = data;
    }

    public String name() {
        return name;
    }

    /** The 1.12 data value for this facing on chests, furnaces and end portal frames. */
    public int data() {
        return data;
    }

    @Override
    public String toString() {
        return "facing=" + name;
    }

    public static Facing byName(String value) {
        String key = value == null ? "" : value.toLowerCase(Locale.ROOT);
        switch (key) {
            case "north": return NORTH;
            case "south": return SOUTH;
            case "west": return WEST;
            case "east": return EAST;
            case "up": return NORTH;
            case "down": return NORTH;
            default: return NORTH;
        }
    }
}