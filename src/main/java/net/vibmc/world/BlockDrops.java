package net.vibmc.world;

import net.vibmc.inventory.ItemStack;
import net.vibmc.inventory.ItemType;
import net.vibmc.inventory.ItemTypes;
import net.vibmc.world.block.BlockState;
import net.vibmc.world.block.BlockType;

import java.util.HashMap;
import java.util.Map;

/**
 * What a broken block yields, and which tool is required to yield anything.
 *
 * vib-MC has no item entities, so drops go straight into the breaking player's inventory.
 * The tables follow 1.12.2 semantics: stone yields cobblestone, ores that need smelting drop
 * themselves, and ores that do not drop their material.
 */
public final class BlockDrops {
    /** Mining tiers, ordered. A block is only harvestable by a tool at or above its tier. */
    public enum Tier { NONE, WOOD, STONE, IRON, DIAMOND }

    /** Drop mapping and rule tables keyed by semantic block name. */
    private static final Map<String, ItemType> DROPS = new HashMap<>();
    private static final Map<String, int[]> DROP_AMOUNTS = new HashMap<>();
    private static final Map<String, Tier> PICKAXE_TIERS = new HashMap<>();
    private static final Map<String, Tier> PICKAXE_LEVELS = new HashMap<>();

    static {
        // Blocks whose drop differs from the block itself.
        DROPS.put("stone", ItemTypes.COBBLESTONE);
        DROPS.put("grass_block", ItemTypes.DIRT);
        DROPS.put("coal_ore", ItemTypes.COAL);
        DROPS.put("diamond_ore", ItemTypes.DIAMOND);
        DROPS.put("emerald_ore", ItemTypes.EMERALD);
        DROPS.put("redstone_ore", ItemTypes.REDSTONE);
        DROPS.put("lapis_ore", ItemTypes.LAPIS_LAZULI);
        DROPS.put("oak_leaves", ItemTypes.AIR);
        DROPS.put("fire", ItemTypes.AIR);
        DROPS.put("nether_portal", ItemTypes.AIR);
        DROPS.put("end_portal", ItemTypes.AIR);
        DROPS.put("glass", ItemTypes.AIR);
        DROPS.put("dead_bush", ItemTypes.AIR);

        // Multi-item drops as {minimum, maximum}.
        DROP_AMOUNTS.put("redstone_ore", new int[]{4, 5});
        DROP_AMOUNTS.put("lapis_ore", new int[]{4, 8});

        // Tool requirements. Anything absent drops without a tool.
        PICKAXE_TIERS.put("stone", Tier.WOOD);
        PICKAXE_TIERS.put("cobblestone", Tier.WOOD);
        PICKAXE_TIERS.put("andesite", Tier.WOOD);
        PICKAXE_TIERS.put("diorite", Tier.WOOD);
        PICKAXE_TIERS.put("coal_ore", Tier.WOOD);
        PICKAXE_TIERS.put("furnace", Tier.WOOD);
        PICKAXE_TIERS.put("iron_ore", Tier.STONE);
        PICKAXE_TIERS.put("lapis_ore", Tier.STONE);
        PICKAXE_TIERS.put("gold_ore", Tier.IRON);
        PICKAXE_TIERS.put("diamond_ore", Tier.IRON);
        PICKAXE_TIERS.put("emerald_ore", Tier.IRON);
        PICKAXE_TIERS.put("redstone_ore", Tier.IRON);
        PICKAXE_TIERS.put("obsidian", Tier.DIAMOND);

        PICKAXE_LEVELS.put("wooden_pickaxe", Tier.WOOD);
        PICKAXE_LEVELS.put("golden_pickaxe", Tier.WOOD);
        PICKAXE_LEVELS.put("stone_pickaxe", Tier.STONE);
        PICKAXE_LEVELS.put("iron_pickaxe", Tier.IRON);
        PICKAXE_LEVELS.put("diamond_pickaxe", Tier.DIAMOND);
    }

    private BlockDrops() {}

    /** The item a block drops as itself. Item names match the block-entity names. */
    private static ItemType itemFor(BlockType type) {
        return type == null ? null : ItemTypes.getByName(type.name());
    }

    /** The tool tier required to get a drop out of this block. */
    public static Tier requiredTier(BlockState block) {
        Tier tier = block == null ? null : PICKAXE_TIERS.get(block.getType().name());
        return tier == null ? Tier.NONE : tier;
    }

    /** The tier the held item provides. */
    public static Tier heldTier(ItemStack held) {
        if (held == null || held.isEmpty()) return Tier.NONE;
        Tier tier = PICKAXE_LEVELS.get(held.getType().name());
        return tier == null ? Tier.NONE : tier;
    }

    public static boolean canHarvest(BlockState block, ItemStack held) {
        return heldTier(held).ordinal() >= requiredTier(block).ordinal();
    }

    /**
     * The stack a block yields, or an empty stack when it yields nothing. {@code random} is
     * the world's generator so variable drops stay reproducible within a session.
     */
    public static ItemStack drop(BlockState block, ItemStack held, java.util.Random random) {
        if (block == null || Blocks.same(block, Blocks.AIR) || Blocks.isFluid(block)) return ItemStack.EMPTY;
        if (!canHarvest(block, held)) return ItemStack.EMPTY;
        String name = block.getType().name();
        // Leaves are the only food source in a world with no farming or mobs, so they drop
        // apples more generously than vanilla's 0.5%.
        if (name.equals("oak_leaves")) {
            return random.nextInt(20) == 0 ? ItemStack.of("apple") : ItemStack.EMPTY;
        }
        ItemType type = DROPS.get(name);
        if (type == null) type = itemFor(block.getType());
        if (type == null || type == ItemTypes.AIR) return ItemStack.EMPTY;
        int amount = 1;
        int[] range = DROP_AMOUNTS.get(name);
        if (range != null) amount = range[0] + random.nextInt(range[1] - range[0] + 1);
        // Gravel occasionally yields flint instead of itself, as in vanilla.
        if (Blocks.same(block, Blocks.GRAVEL) && random.nextInt(10) == 0) type = ItemTypes.FLINT;
        return ItemStack.of(type.name(), amount);
    }
}