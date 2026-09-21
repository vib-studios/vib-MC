package net.vibmc.world;

import net.vibmc.world.block.BlockState;
import net.vibmc.world.block.BlockType;
import net.vibmc.world.block.Facing;

/** Semantic block constants backed by the vendored 1.12 mapping, used by gameplay and storage. */
public final class Blocks {
    private Blocks() {}

    public static boolean same(BlockState a, BlockState b) {
        return a != null && b != null && a.getGlobalId() == b.getGlobalId();
    }

    public static final BlockState AIR = BlockState.of("air");
    public static final BlockState STONE = BlockState.of("stone");
    public static final BlockState GRASS = BlockState.of("grass_block");
    public static final BlockState DIRT = BlockState.of("dirt");
    public static final BlockState WOOD = BlockState.of("oak_log");
    public static final BlockState OAK_PLANKS = BlockState.of("oak_planks");
    public static final BlockState LEAVES = BlockState.of("oak_leaves");
    public static final BlockState GLASS = BlockState.of("glass");
    public static final BlockState WATER = BlockState.of("water");
    public static final BlockState LAVA = BlockState.of("lava");
    public static final BlockState CHEST = BlockState.of("chest");
    public static final BlockState FURNACE = BlockState.of("furnace");
    public static final BlockState CRAFTING_TABLE = BlockState.of("crafting_table");
    public static final BlockState DOOR = BlockState.of("oak_door");
    public static final BlockState TRAPDOOR = BlockState.of("oak_trapdoor");
    public static final BlockState SAND = BlockState.of("sand");
    public static final BlockState GRAVEL = BlockState.of("gravel");
    public static final BlockState BEDROCK = BlockState.of("bedrock");
    public static final BlockState ANDESITE = BlockState.of("andesite");
    public static final BlockState DIORITE = BlockState.of("diorite");
    public static final BlockState COAL_ORE = BlockState.of("coal_ore");
    public static final BlockState IRON_ORE = BlockState.of("iron_ore");
    public static final BlockState OBSIDIAN = BlockState.of("obsidian");
    public static final BlockState NETHERRACK = BlockState.of("netherrack");
    public static final BlockState SOUL_SAND = BlockState.of("soul_sand");
    public static final BlockState GLOWSTONE = BlockState.of("glowstone");
    public static final BlockState NETHER_PORTAL = BlockState.of("nether_portal");
    public static final BlockState END_STONE = BlockState.of("end_stone");
    public static final BlockState END_PORTAL = BlockState.of("end_portal");
    public static final BlockState FIRE = BlockState.of("fire");
    public static final BlockState END_PORTAL_FRAME = BlockState.of("end_portal_frame");
    public static final BlockState COBBLESTONE = BlockState.of("cobblestone");
    public static final BlockState CACTUS = BlockState.of("cactus");
    public static final BlockState DEAD_BUSH = BlockState.of("dead_bush");
    public static final BlockState GOLD_ORE = BlockState.of("gold_ore");
    public static final BlockState DIAMOND_ORE = BlockState.of("diamond_ore");
    public static final BlockState REDSTONE_ORE = BlockState.of("redstone_ore");
    public static final BlockState LAPIS_ORE = BlockState.of("lapis_ore");
    public static final BlockState EMERALD_ORE = BlockState.of("emerald_ore");

    public static final BlockState END_PORTAL_FRAME_FILLED = filledFrame();

    private static BlockState filledFrame() {
        BlockState value = END_PORTAL_FRAME.clone();
        value.setEye(true);
        value.setFacing(Facing.NORTH);
        return value;
    }

    /** Compares a block by its kind, ignoring state properties. */
    public static boolean isType(BlockState state, BlockType type) {
        return state != null && state.getType() == type;
    }

    /** Water or lava, at any flow level. */
    public static boolean isFluid(BlockState state) {
        if (state == null) return false;
        BlockType type = state.getType();
        return type == BlockType.of("water") || type == BlockType.of("lava");
    }

    public static boolean isWater(BlockState state) {
        return state != null && state.getType() == BlockType.of("water");
    }

    public static boolean isLava(BlockState state) {
        return state != null && state.getType() == BlockType.of("lava");
    }

    /** A fluid, air, fire, or a decoration: something a placed or flowing block may overwrite. */
    public static boolean isReplaceable(BlockState state) {
        return state == null || same(state, AIR) || isFluid(state) || same(state, FIRE)
                || same(state, DEAD_BUSH);
    }

    /**
     * Whether the block stops movement and supports what is placed on it. Deliberately
     * coarse: vib-MC has no per-block collision shapes, so anything that is not air, a
     * fluid, or a decoration counts as solid.
     */
    public static boolean isSolid(BlockState state) {
        if (state == null) return false;
        if (same(state, AIR) || isFluid(state) || same(state, FIRE) || same(state, DEAD_BUSH)) {
            return false;
        }
        BlockType type = state.getType();
        return type != BlockType.of("nether_portal") && type != BlockType.of("end_portal")
                && type != BlockType.of("torch") && type != BlockType.of("oak_sapling");
    }

    /** Blocks that fall when unsupported. */
    public static boolean isGravityAffected(BlockState state) {
        return same(state, SAND) || same(state, GRAVEL);
    }

    /** A fluid's flow distance from its source; 0 for a source block. */
    public static int fluidLevel(BlockState state) {
        return state != null ? state.getLevel() : 0;
    }

    /** A copy of a fluid state at the given flow level. */
    public static BlockState fluidAt(BlockState fluid, int level) {
        BlockState copy = fluid.clone();
        copy.setLevel(level);
        return copy;
    }
}