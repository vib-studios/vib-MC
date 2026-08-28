package net.vibmc.inventory;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;

import java.util.Locale;

/** Melee damage per held item, using 1.12.2 values. A bare hand does one heart. */
public final class Weapons {
    private static final float FIST = 1.0f;

    private Weapons() {}

    public static float attackDamage(ItemStack held) {
        if (held == null || held.isEmpty()) return FIST;
        String name = key(held.getType());
        float material;
        if (name.startsWith("wooden") || name.startsWith("golden") || name.startsWith("gold")) material = 0.0f;
        else if (name.startsWith("stone")) material = 1.0f;
        else if (name.startsWith("iron")) material = 2.0f;
        else if (name.startsWith("diamond")) material = 3.0f;
        else if (name.startsWith("netherite")) material = 4.0f;
        else return FIST;
        if (name.endsWith("_sword")) return 4.0f + material;
        if (name.endsWith("_axe")) return 7.0f + (material > 0 ? material - 1.0f : 0.0f);
        if (name.endsWith("_pickaxe")) return 2.0f + material;
        if (name.endsWith("_shovel")) return 1.0f + material;
        if (name.endsWith("_hoe")) return FIST;
        return FIST;
    }

    private static String key(ItemType type) {
        String name = type.getName().toString().toLowerCase(Locale.ROOT);
        int colon = name.indexOf(':');
        return colon < 0 ? name : name.substring(colon + 1);
    }
}
