package net.vibmc.crafting;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;

import java.util.HashMap;
import java.util.Map;

/** Furnace recipes and fuel values. Without these, mined iron and gold ore are dead ends. */
public final class Smelting {
    private static final Map<ItemType, ItemType> RESULTS = new HashMap<>();
    private static final Map<ItemType, Integer> FUELS = new HashMap<>();

    static {
        RESULTS.put(ItemTypes.IRON_ORE, ItemTypes.IRON_INGOT);
        RESULTS.put(ItemTypes.GOLD_ORE, ItemTypes.GOLD_INGOT);
        RESULTS.put(ItemTypes.SAND, ItemTypes.GLASS);
        RESULTS.put(ItemTypes.COBBLESTONE, ItemTypes.STONE);
        RESULTS.put(ItemTypes.POTATO, ItemTypes.BAKED_POTATO);
        RESULTS.put(ItemTypes.OAK_LOG, ItemTypes.CHARCOAL);

        FUELS.put(ItemTypes.COAL, 1600);
        FUELS.put(ItemTypes.CHARCOAL, 1600);
        FUELS.put(ItemTypes.OAK_LOG, 300);
        FUELS.put(ItemTypes.OAK_PLANKS, 300);
        FUELS.put(ItemTypes.OAK_TRAPDOOR, 300);
        FUELS.put(ItemTypes.CRAFTING_TABLE, 300);
        FUELS.put(ItemTypes.CHEST, 300);
        FUELS.put(ItemTypes.STICK, 100);
        FUELS.put(ItemTypes.LAVA_BUCKET, 20000);
    }

    private Smelting() {}

    /** What one of this item smelts into, or an empty stack when it is not smeltable. */
    public static ItemStack result(ItemStack input) {
        if (input == null || input.isEmpty()) return ItemStack.EMPTY;
        ItemType result = RESULTS.get(input.getType());
        return result == null ? ItemStack.EMPTY
                : ItemStack.builder().type(result).amount(1).version(ClientVersion.V_1_12_2).build();
    }

    /** How many ticks one of this item burns for; 0 when it is not fuel. */
    public static int burnTime(ItemStack fuel) {
        if (fuel == null || fuel.isEmpty()) return 0;
        Integer ticks = FUELS.get(fuel.getType());
        return ticks == null ? 0 : ticks;
    }
}
