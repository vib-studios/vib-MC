package net.vibmc.inventory;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;

import java.util.Locale;

/**
 * Armour values and slot rules, derived from the item's registry name rather than a table of
 * twenty constants. 1.12.2 armour points: each point removes 4% of incoming damage, capped
 * at the vanilla 80%.
 */
public final class Armor {
    /** Player-inventory armour slot order, matching the vanilla window layout. */
    public static final int HELMET = 0, CHESTPLATE = 1, LEGGINGS = 2, BOOTS = 3;
    public static final int SLOTS = 4;

    private Armor() {}

    /** The armour slot this item belongs in, or -1 when it is not armour. */
    public static int slotFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        String name = key(stack.getType());
        if (name.endsWith("_helmet") || name.equals("turtle_helmet")) return HELMET;
        if (name.endsWith("_chestplate") || name.equals("elytra")) return CHESTPLATE;
        if (name.endsWith("_leggings")) return LEGGINGS;
        if (name.endsWith("_boots")) return BOOTS;
        return -1;
    }

    /** Defence points contributed by one piece. */
    public static int points(ItemStack stack) {
        int slot = slotFor(stack);
        if (slot < 0) return 0;
        String name = key(stack.getType());
        int[] byMaterial;
        if (name.startsWith("leather")) byMaterial = new int[]{1, 3, 2, 1};
        else if (name.startsWith("golden") || name.startsWith("gold")) byMaterial = new int[]{2, 5, 3, 1};
        else if (name.startsWith("chainmail")) byMaterial = new int[]{2, 5, 4, 1};
        else if (name.startsWith("iron")) byMaterial = new int[]{2, 6, 5, 2};
        else if (name.startsWith("diamond")) byMaterial = new int[]{3, 8, 6, 3};
        else if (name.startsWith("netherite")) byMaterial = new int[]{3, 8, 6, 3};
        else if (name.startsWith("turtle")) byMaterial = new int[]{2, 0, 0, 0};
        else return 0;
        return byMaterial[slot];
    }

    public static int totalPoints(ItemStack[] worn) {
        int total = 0;
        if (worn != null) for (ItemStack piece : worn) total += points(piece);
        return total;
    }

    /** Applies the 4%-per-point reduction, capped at 80%. */
    public static float reduce(float damage, int points) {
        float factor = 1.0f - Math.min(20, Math.max(0, points)) * 0.04f;
        return Math.max(0.0f, damage * factor);
    }

    private static String key(ItemType type) {
        String name = type.getName().toString().toLowerCase(Locale.ROOT);
        int colon = name.indexOf(':');
        return colon < 0 ? name : name.substring(colon + 1);
    }
}
