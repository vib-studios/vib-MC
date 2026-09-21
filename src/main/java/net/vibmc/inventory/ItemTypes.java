package net.vibmc.inventory;

/** Compile-time item constants replacing PacketEvents' {@code ItemTypes}. */
public final class ItemTypes {
    private ItemTypes() {}

    public static final ItemType AIR = item("air");
    public static final ItemType STONE = item("stone");
    public static final ItemType GRANITE = item("granite");
    public static final ItemType COBBLESTONE = item("cobblestone");
    public static final ItemType DIRT = item("dirt");
    public static final ItemType SAND = item("sand");
    public static final ItemType GRAVEL = item("gravel");
    public static final ItemType OAK_LOG = item("oak_log");
    public static final ItemType OAK_PLANKS = item("oak_planks");
    public static final ItemType DARK_OAK_PLANKS = item("dark_oak_planks");
    public static final ItemType WHITE_CARPET = item("white_carpet");
    public static final ItemType LIGHT_BLUE_CARPET = item("light_blue_carpet");
    public static final ItemType CACTUS = item("cactus");
    public static final ItemType GLASS = item("glass");
    public static final ItemType TORCH = item("torch");
    public static final ItemType CHEST = item("chest");
    public static final ItemType FURNACE = item("furnace");
    public static final ItemType CRAFTING_TABLE = item("crafting_table");
    public static final ItemType OAK_DOOR = item("oak_door");
    public static final ItemType OAK_TRAPDOOR = item("oak_trapdoor");
    public static final ItemType COAL = item("coal");
    public static final ItemType CHARCOAL = item("charcoal");
    public static final ItemType REDSTONE = item("redstone");
    public static final ItemType LAPIS_LAZULI = item("lapis_lazuli");
    public static final ItemType IRON_ORE = item("iron_ore");
    public static final ItemType GOLD_ORE = item("gold_ore");
    public static final ItemType IRON_INGOT = item("iron_ingot");
    public static final ItemType GOLD_INGOT = item("gold_ingot");
    public static final ItemType OBSIDIAN = item("obsidian");
    public static final ItemType DIAMOND = item("diamond");
    public static final ItemType EMERALD = item("emerald");
    public static final ItemType FLINT = item("flint");
    public static final ItemType STICK = item("stick");
    public static final ItemType LEATHER = item("leather");
    public static final ItemType WHEAT = item("wheat");
    public static final ItemType APPLE = item("apple");
    public static final ItemType BREAD = item("bread");
    public static final ItemType COOKIE = item("cookie");
    public static final ItemType CARROT = item("carrot");
    public static final ItemType POTATO = item("potato");
    public static final ItemType BAKED_POTATO = item("baked_potato");
    public static final ItemType MELON_SLICE = item("melon");
    public static final ItemType BUCKET = item("bucket", 1);
    public static final ItemType LAVA_BUCKET = item("lava_bucket", 1);
    public static final ItemType GOLDEN_APPLE = item("golden_apple");

    public static final ItemType WOODEN_SWORD = tool("wooden_sword");
    public static final ItemType WOODEN_SHOVEL = tool("wooden_shovel");
    public static final ItemType WOODEN_PICKAXE = tool("wooden_pickaxe");
    public static final ItemType WOODEN_AXE = tool("wooden_axe");
    public static final ItemType WOODEN_HOE = tool("wooden_hoe");
    public static final ItemType STONE_SWORD = tool("stone_sword");
    public static final ItemType STONE_SHOVEL = tool("stone_shovel");
    public static final ItemType STONE_PICKAXE = tool("stone_pickaxe");
    public static final ItemType STONE_AXE = tool("stone_axe");
    public static final ItemType STONE_HOE = tool("stone_hoe");
    public static final ItemType IRON_SWORD = tool("iron_sword");
    public static final ItemType IRON_SHOVEL = tool("iron_shovel");
    public static final ItemType IRON_PICKAXE = tool("iron_pickaxe");
    public static final ItemType IRON_AXE = tool("iron_axe");
    public static final ItemType IRON_HOE = tool("iron_hoe");
    public static final ItemType GOLDEN_SWORD = tool("golden_sword");
    public static final ItemType GOLDEN_SHOVEL = tool("golden_shovel");
    public static final ItemType GOLDEN_PICKAXE = tool("golden_pickaxe");
    public static final ItemType GOLDEN_AXE = tool("golden_axe");
    public static final ItemType GOLDEN_HOE = tool("golden_hoe");
    public static final ItemType DIAMOND_SWORD = tool("diamond_sword");
    public static final ItemType DIAMOND_SHOVEL = tool("diamond_shovel");
    public static final ItemType DIAMOND_PICKAXE = tool("diamond_pickaxe");
    public static final ItemType DIAMOND_AXE = tool("diamond_axe");
    public static final ItemType DIAMOND_HOE = tool("diamond_hoe");
    public static final ItemType FLINT_AND_STEEL = tool("flint_and_steel");
    public static final ItemType ENDER_EYE = tool("ender_eye");

    public static final ItemType LEATHER_HELMET = armor("leather_helmet");
    public static final ItemType LEATHER_CHESTPLATE = armor("leather_chestplate");
    public static final ItemType LEATHER_LEGGINGS = armor("leather_leggings");
    public static final ItemType LEATHER_BOOTS = armor("leather_boots");
    public static final ItemType IRON_HELMET = armor("iron_helmet");
    public static final ItemType IRON_CHESTPLATE = armor("iron_chestplate");
    public static final ItemType IRON_LEGGINGS = armor("iron_leggings");
    public static final ItemType IRON_BOOTS = armor("iron_boots");
    public static final ItemType GOLDEN_HELMET = armor("golden_helmet");
    public static final ItemType GOLDEN_CHESTPLATE = armor("golden_chestplate");
    public static final ItemType GOLDEN_LEGGINGS = armor("golden_leggings");
    public static final ItemType GOLDEN_BOOTS = armor("golden_boots");
    public static final ItemType DIAMOND_HELMET = armor("diamond_helmet");
    public static final ItemType DIAMOND_CHESTPLATE = armor("diamond_chestplate");
    public static final ItemType DIAMOND_LEGGINGS = armor("diamond_leggings");
    public static final ItemType DIAMOND_BOOTS = armor("diamond_boots");

    private static ItemType item(String name) {
        return ItemType.of(name);
    }

    private static ItemType item(String name, int maxStack) {
        return ItemType.of(name, maxStack);
    }

    private static ItemType tool(String name) {
        return ItemType.of(name, 1);
    }

    private static ItemType armor(String name) {
        return ItemType.of(name, 1);
    }

    public static ItemType getByName(String name) {
        if (name == null) return null;
        String key = name.toLowerCase(java.util.Locale.ROOT);
        return CACHE_LOOKUP.get(key);
    }

    private static final java.util.Map<String, ItemType> CACHE_LOOKUP = buildLookup();

    private static java.util.Map<String, ItemType> buildLookup() {
        java.util.Map<String, ItemType> map = new java.util.HashMap<>();
        for (java.lang.reflect.Field field : ItemTypes.class.getFields()) {
            try {
                Object value = field.get(null);
                if (value instanceof ItemType) {
                    map.put(((ItemType) value).name(), (ItemType) value);
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        return map;
    }
}