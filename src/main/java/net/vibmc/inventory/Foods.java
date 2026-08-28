package net.vibmc.inventory;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;

import java.util.HashMap;
import java.util.Map;

/** Edible items and what they restore, in 1.12.2 hunger points and saturation. */
public final class Foods {
    private static final Map<ItemType, float[]> FOODS = new HashMap<>();

    static {
        FOODS.put(ItemTypes.APPLE, new float[]{4, 2.4f});
        FOODS.put(ItemTypes.BREAD, new float[]{5, 6.0f});
        FOODS.put(ItemTypes.GOLDEN_APPLE, new float[]{4, 9.6f});
        FOODS.put(ItemTypes.CARROT, new float[]{3, 3.6f});
        FOODS.put(ItemTypes.POTATO, new float[]{1, 0.6f});
        FOODS.put(ItemTypes.BAKED_POTATO, new float[]{5, 6.0f});
        FOODS.put(ItemTypes.MELON_SLICE, new float[]{2, 1.2f});
        FOODS.put(ItemTypes.COOKIE, new float[]{2, 0.4f});
    }

    private Foods() {}

    public static boolean isFood(ItemStack stack) {
        return stack != null && !stack.isEmpty() && FOODS.containsKey(stack.getType());
    }

    public static int nutrition(ItemStack stack) {
        float[] values = FOODS.get(stack.getType());
        return values == null ? 0 : (int) values[0];
    }

    public static float saturation(ItemStack stack) {
        float[] values = FOODS.get(stack.getType());
        return values == null ? 0.0f : values[1];
    }
}
