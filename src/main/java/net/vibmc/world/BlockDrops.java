package net.vibmc.world;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;

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

    private static final Map<StateType, ItemType> DROPS = new HashMap<>();
    private static final Map<StateType, int[]> DROP_AMOUNTS = new HashMap<>();
    private static final Map<StateType, Tier> PICKAXE_TIERS = new HashMap<>();
    private static final Map<ItemType, Tier> PICKAXE_LEVELS = new HashMap<>();

    static {
        // Blocks whose drop differs from the block itself.
        DROPS.put(StateTypes.STONE, ItemTypes.COBBLESTONE);
        DROPS.put(StateTypes.GRASS_BLOCK, ItemTypes.DIRT);
        DROPS.put(StateTypes.COAL_ORE, ItemTypes.COAL);
        DROPS.put(StateTypes.DIAMOND_ORE, ItemTypes.DIAMOND);
        DROPS.put(StateTypes.EMERALD_ORE, ItemTypes.EMERALD);
        DROPS.put(StateTypes.REDSTONE_ORE, ItemTypes.REDSTONE);
        DROPS.put(StateTypes.LAPIS_ORE, ItemTypes.LAPIS_LAZULI);
        DROPS.put(StateTypes.OAK_LEAVES, ItemTypes.AIR);
        DROPS.put(StateTypes.FIRE, ItemTypes.AIR);
        DROPS.put(StateTypes.NETHER_PORTAL, ItemTypes.AIR);
        DROPS.put(StateTypes.END_PORTAL, ItemTypes.AIR);
        DROPS.put(StateTypes.GLASS, ItemTypes.AIR);
        DROPS.put(StateTypes.DEAD_BUSH, ItemTypes.AIR);

        // Multi-item drops as {minimum, maximum}.
        DROP_AMOUNTS.put(StateTypes.REDSTONE_ORE, new int[]{4, 5});
        DROP_AMOUNTS.put(StateTypes.LAPIS_ORE, new int[]{4, 8});

        // Tool requirements. Anything absent drops without a tool.
        PICKAXE_TIERS.put(StateTypes.STONE, Tier.WOOD);
        PICKAXE_TIERS.put(StateTypes.COBBLESTONE, Tier.WOOD);
        PICKAXE_TIERS.put(StateTypes.ANDESITE, Tier.WOOD);
        PICKAXE_TIERS.put(StateTypes.DIORITE, Tier.WOOD);
        PICKAXE_TIERS.put(StateTypes.COAL_ORE, Tier.WOOD);
        PICKAXE_TIERS.put(StateTypes.FURNACE, Tier.WOOD);
        PICKAXE_TIERS.put(StateTypes.IRON_ORE, Tier.STONE);
        PICKAXE_TIERS.put(StateTypes.LAPIS_ORE, Tier.STONE);
        PICKAXE_TIERS.put(StateTypes.GOLD_ORE, Tier.IRON);
        PICKAXE_TIERS.put(StateTypes.DIAMOND_ORE, Tier.IRON);
        PICKAXE_TIERS.put(StateTypes.EMERALD_ORE, Tier.IRON);
        PICKAXE_TIERS.put(StateTypes.REDSTONE_ORE, Tier.IRON);
        PICKAXE_TIERS.put(StateTypes.OBSIDIAN, Tier.DIAMOND);

        PICKAXE_LEVELS.put(ItemTypes.WOODEN_PICKAXE, Tier.WOOD);
        PICKAXE_LEVELS.put(ItemTypes.GOLDEN_PICKAXE, Tier.WOOD);
        PICKAXE_LEVELS.put(ItemTypes.STONE_PICKAXE, Tier.STONE);
        PICKAXE_LEVELS.put(ItemTypes.IRON_PICKAXE, Tier.IRON);
        PICKAXE_LEVELS.put(ItemTypes.DIAMOND_PICKAXE, Tier.DIAMOND);
    }

    private BlockDrops() {}

    /**
     * The item a block drops as itself. Block-state names are not namespaced while item names
     * are, so the qualified form has to be tried as well.
     */
    private static ItemType itemFor(StateType type) {
        String name = type.getName().toString();
        ItemType item = ItemTypes.getByName(name);
        if (item == null && name.indexOf(':') < 0) item = ItemTypes.getByName("minecraft:" + name);
        return item;
    }

    /** The tool tier required to get a drop out of this block. */
    public static Tier requiredTier(WrappedBlockState block) {
        Tier tier = PICKAXE_TIERS.get(block.getType());
        return tier == null ? Tier.NONE : tier;
    }

    /** The tier the held item provides. */
    public static Tier heldTier(ItemStack held) {
        if (held == null || held.isEmpty()) return Tier.NONE;
        Tier tier = PICKAXE_LEVELS.get(held.getType());
        return tier == null ? Tier.NONE : tier;
    }

    public static boolean canHarvest(WrappedBlockState block, ItemStack held) {
        return heldTier(held).ordinal() >= requiredTier(block).ordinal();
    }

    /**
     * The stack a block yields, or an empty stack when it yields nothing. {@code random} is
     * the world's generator so variable drops stay reproducible within a session.
     */
    public static ItemStack drop(WrappedBlockState block, ItemStack held, java.util.Random random) {
        if (block == null || Blocks.same(block, Blocks.AIR) || Blocks.isFluid(block)) return ItemStack.EMPTY;
        if (!canHarvest(block, held)) return ItemStack.EMPTY;
        // Leaves are the only food source in a world with no farming or mobs, so they drop
        // apples more generously than vanilla's 0.5%.
        if (block.getType() == StateTypes.OAK_LEAVES) {
            return random.nextInt(20) == 0
                    ? ItemStack.builder().type(ItemTypes.APPLE).amount(1)
                        .version(ClientVersion.V_1_12_2).build()
                    : ItemStack.EMPTY;
        }
        ItemType type = DROPS.get(block.getType());
        if (type == null) type = itemFor(block.getType());
        if (type == null || type == ItemTypes.AIR) return ItemStack.EMPTY;
        int amount = 1;
        int[] range = DROP_AMOUNTS.get(block.getType());
        if (range != null) amount = range[0] + random.nextInt(range[1] - range[0] + 1);
        // Gravel occasionally yields flint instead of itself, as in vanilla.
        if (Blocks.same(block, Blocks.GRAVEL) && random.nextInt(10) == 0) type = ItemTypes.FLINT;
        return ItemStack.builder().type(type).amount(amount).version(ClientVersion.V_1_12_2).build();
    }
}
