package net.vibmc.inventory;

import net.vibmc.mappings.Mappings;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** An item kind identified by its 1.12 name, e.g. {@code oak_planks}. */
public final class ItemType {
    private static final Map<String, ItemType> CACHE = new ConcurrentHashMap<>();
    /** 1.12.2 maximum durability per item name; anything absent never wears out. */
    private static final Map<String, Integer> MAX_DURABILITY = durabilityTable();

    private final String name;
    private final int maxStack;
    private final int maxDurability;

    private ItemType(String name, int maxStack, int maxDurability) {
        this.name = name;
        this.maxStack = maxStack;
        this.maxDurability = maxDurability;
    }

    private static ItemType intern(String name, int maxStack) {
        String key = name == null ? "air" : name.toLowerCase(Locale.ROOT);
        Integer durability = MAX_DURABILITY.get(key);
        return intern(key, maxStack, durability == null ? 0 : durability);
    }

    private static ItemType intern(String name, int maxStack, int maxDurability) {
        String key = name == null ? "air" : name.toLowerCase(Locale.ROOT);
        ItemType existing = CACHE.get(key);
        if (existing != null) return existing;
        ItemType created = new ItemType(key, maxStack, maxDurability);
        ItemType raced = CACHE.putIfAbsent(key, created);
        return raced != null ? raced : created;
    }

    public static ItemType of(String name) {
        return intern(name, 64);
    }

    public static ItemType of(String name, int maxStack) {
        return intern(name, maxStack);
    }

    public String name() {
        return name;
    }

    public int getMaxAmount() {
        return maxStack;
    }

    /** How much wear this item can take before it breaks; 0 means it never wears out. */
    public int getMaxDurability() {
        return maxDurability;
    }

    /** The 1.12 protocol item id for this type. */
    public int getId() {
        return Mappings.itemIdOrAir(name);
    }

    /** The block this item places, or null when it is not placeable. */
    public net.vibmc.world.block.BlockType getPlacedType() {
        return Mappings.isBlock(name) ? net.vibmc.world.block.BlockType.of(name) : null;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ItemType && name.equals(((ItemType) o).name));
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }

    private static Map<String, Integer> durabilityTable() {
        Map<String, Integer> table = new HashMap<>();
        putAll(table, 59, "wooden_sword", "wooden_shovel", "wooden_pickaxe", "wooden_axe", "wooden_hoe");
        putAll(table, 131, "stone_sword", "stone_shovel", "stone_pickaxe", "stone_axe", "stone_hoe");
        putAll(table, 250, "iron_sword", "iron_shovel", "iron_pickaxe", "iron_axe", "iron_hoe");
        putAll(table, 1561, "diamond_sword", "diamond_shovel", "diamond_pickaxe", "diamond_axe", "diamond_hoe");
        putAll(table, 32, "golden_sword", "golden_shovel", "golden_pickaxe", "golden_axe", "golden_hoe");
        putAll(table, 384, "bow");
        putAll(table, 64, "fishing_rod", "flint_and_steel");
        putAll(table, 238, "shears");
        putAll(table, 336, "shield");
        putAll(table, 432, "elytra");
        putAll(table, 25, "carrot_on_a_stick");
        putAll(table, 55, "leather_helmet");
        putAll(table, 80, "leather_chestplate");
        putAll(table, 75, "leather_leggings");
        putAll(table, 65, "leather_boots");
        putAll(table, 165, "chainmail_helmet", "iron_helmet");
        putAll(table, 240, "chainmail_chestplate", "iron_chestplate");
        putAll(table, 225, "chainmail_leggings", "iron_leggings");
        putAll(table, 195, "chainmail_boots", "iron_boots");
        putAll(table, 363, "diamond_helmet");
        putAll(table, 528, "diamond_chestplate");
        putAll(table, 495, "diamond_leggings");
        putAll(table, 429, "diamond_boots");
        putAll(table, 77, "golden_helmet");
        putAll(table, 112, "golden_chestplate");
        putAll(table, 105, "golden_leggings");
        putAll(table, 91, "golden_boots");
        putAll(table, 275, "turtle_helmet");
        return table;
    }

    private static void putAll(Map<String, Integer> table, int durability, String... names) {
        for (String name : names) table.put(name, durability);
    }
}