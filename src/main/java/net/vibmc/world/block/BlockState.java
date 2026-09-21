package net.vibmc.world.block;

import net.vibmc.mappings.Mappings;

/**
 * A mutable 1.12 block state: a semantic {@link BlockType} plus the legacy data nibble.
 * This is the drop-in replacement for PacketEvents' {@code WrappedBlockState}; the global id
 * IS the 1.12 combined id ({@code minecraftId << 4 | data}), so chunk encoding, palette
 * comparison and world persistence all operate on the single {@code combined} value.
 */
public final class BlockState {
    private BlockType type;
    private int data;

    public BlockState(BlockType type, int data) {
        this.type = type == null ? BlockType.of("air") : type;
        this.data = data & 0xF;
    }

    private BlockState(BlockState other) {
        this.type = other.type;
        this.data = other.data;
    }

    public static BlockState of(String name) {
        return new BlockState(BlockType.of(name), Mappings.defaultData(name));
    }

    public static BlockState of(String name, int data) {
        return new BlockState(BlockType.of(name), data);
    }

    public static BlockState of(BlockType type, int data) {
        return new BlockState(type, data);
    }

    /** Rebuild a state from a stored 1.12 combined id. Unknown ids degrade to air. */
    public static BlockState fromCombined(int combined) {
        String name = Mappings.blockName(combined);
        return new BlockState(BlockType.of(name), combined & 0xF);
    }

    public BlockType getType() {
        return type;
    }

    public void setType(BlockType type) {
        this.type = type == null ? BlockType.of("air") : type;
    }

    /** Low nibble: the 1.12 data value. */
    public int getData() {
        return data;
    }

    public void setData(int data) {
        this.data = data & 0xF;
    }

    /** The 1.12 combined id used on the wire and in storage. */
    public int getGlobalId() {
        return (Mappings.blockId(type.name()) & ~0xF) | data;
    }

    /** Alias of {@link #getGlobalId()} matching the chunk adapter contract. */
    public int getId() {
        return getGlobalId();
    }

    public boolean hasProperty(StateValue value) {
        String name = type.name();
        switch (value) {
            case LEVEL: return name.equals("water") || name.equals("lava");
            case AXIS: return name.endsWith("_log") || name.equals("quartz") || name.endsWith("_quartz");
            case FACING: return name.endsWith("chest") || name.endsWith("furnace")
                    || name.endsWith("dispenser") || name.endsWith("dropper")
                    || name.endsWith("end_portal_frame") || name.endsWith("piston");
            case HALF: return name.endsWith("_door") || name.endsWith("_trapdoor");
            case ROTATION: return name.endsWith("_skull") || name.endsWith("skull");
            default: return false;
        }
    }

    /** Flow level for fluids, 0 otherwise. */
    public int getLevel() {
        return hasProperty(StateValue.LEVEL) ? data : 0;
    }

    public void setLevel(int level) {
        if (hasProperty(StateValue.LEVEL)) data = Math.max(0, Math.min(15, level)) & 0xF;
    }

    public boolean isLit() {
        return (data & 0x8) != 0 || (data & 0x4) != 0;
    }

    public void setFacing(Facing facing) {
        if (facing != null) data = ((data & ~0x7) | facing.data()) & 0xF;
    }

    /** The axis a log or quartz state points along; Y for blocks without an axis. */
    public Axis getAxis() {
        return Axis.fromLogData(data);
    }

    public Facing getFacing() {
        String name = type.name();
        boolean frame = name.endsWith("end_portal_frame");
        int value = frame ? data & 0x3 : data & 0x7;
        switch (value) {
            case 2: return Facing.NORTH;
            case 3: return Facing.SOUTH;
            case 4: return Facing.WEST;
            case 5: return Facing.EAST;
            default: return Facing.NORTH;
        }
    }

    public void setEye(boolean eye) {
        if (type.name().endsWith("end_portal_frame")) {
            data = ((data & ~0x4) | (eye ? 0x4 : 0)) & 0xF;
        }
    }

    public boolean isEye() {
        return (data & 0x4) != 0;
    }

    public BlockState clone() {
        return new BlockState(this);
    }

    public boolean same(BlockState other) {
        return other != null && getGlobalId() == other.getGlobalId();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BlockState && getGlobalId() == ((BlockState) o).getGlobalId();
    }

    @Override
    public int hashCode() {
        return getGlobalId();
    }

    @Override
    public String toString() {
        return type.name() + "[" + data + "]";
    }
}