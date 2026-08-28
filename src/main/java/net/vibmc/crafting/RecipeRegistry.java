package net.vibmc.crafting;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * The built-in recipe book.
 *
 * Scope follows what a player can actually obtain in vib-MC: blocks they mine, plus the two
 * smelted metals the furnace produces. Nothing here needs an entity, so no recipe depends on
 * mob drops.
 */
public final class RecipeRegistry {
    private static final List<Recipe> RECIPES = new ArrayList<>();

    static {
        // Basics
        RECIPES.add(Recipe.shapeless(ItemTypes.OAK_PLANKS, 4, ItemTypes.OAK_LOG));
        RECIPES.add(Recipe.shaped(ItemTypes.STICK, 4, new String[]{"P", "P"}, 'P', ItemTypes.OAK_PLANKS));
        RECIPES.add(Recipe.shaped(ItemTypes.CRAFTING_TABLE, 1, new String[]{"PP", "PP"},
                'P', ItemTypes.OAK_PLANKS));
        RECIPES.add(Recipe.shaped(ItemTypes.CHEST, 1, new String[]{"PPP", "P P", "PPP"},
                'P', ItemTypes.OAK_PLANKS));
        RECIPES.add(Recipe.shaped(ItemTypes.FURNACE, 1, new String[]{"CCC", "C C", "CCC"},
                'C', ItemTypes.COBBLESTONE));
        RECIPES.add(Recipe.shaped(ItemTypes.TORCH, 4, new String[]{"C", "S"},
                'C', ItemTypes.COAL, 'S', ItemTypes.STICK));
        RECIPES.add(Recipe.shaped(ItemTypes.OAK_DOOR, 3, new String[]{"PP", "PP", "PP"},
                'P', ItemTypes.OAK_PLANKS));
        RECIPES.add(Recipe.shaped(ItemTypes.OAK_TRAPDOOR, 2, new String[]{"PPP", "PPP"},
                'P', ItemTypes.OAK_PLANKS));
        RECIPES.add(Recipe.shaped(ItemTypes.FLINT_AND_STEEL, 1, new String[]{"I ", " F"},
                'I', ItemTypes.IRON_INGOT, 'F', ItemTypes.FLINT));
        RECIPES.add(Recipe.shaped(ItemTypes.BUCKET, 1, new String[]{"I I", " I "},
                'I', ItemTypes.IRON_INGOT));
        RECIPES.add(Recipe.shaped(ItemTypes.BREAD, 1, new String[]{"WWW"}, 'W', ItemTypes.WHEAT));

        // Tools and armour, one family per material.
        tools(ItemTypes.OAK_PLANKS, ItemTypes.WOODEN_PICKAXE, ItemTypes.WOODEN_AXE,
                ItemTypes.WOODEN_SHOVEL, ItemTypes.WOODEN_SWORD, ItemTypes.WOODEN_HOE);
        tools(ItemTypes.COBBLESTONE, ItemTypes.STONE_PICKAXE, ItemTypes.STONE_AXE,
                ItemTypes.STONE_SHOVEL, ItemTypes.STONE_SWORD, ItemTypes.STONE_HOE);
        tools(ItemTypes.IRON_INGOT, ItemTypes.IRON_PICKAXE, ItemTypes.IRON_AXE,
                ItemTypes.IRON_SHOVEL, ItemTypes.IRON_SWORD, ItemTypes.IRON_HOE);
        tools(ItemTypes.GOLD_INGOT, ItemTypes.GOLDEN_PICKAXE, ItemTypes.GOLDEN_AXE,
                ItemTypes.GOLDEN_SHOVEL, ItemTypes.GOLDEN_SWORD, ItemTypes.GOLDEN_HOE);
        tools(ItemTypes.DIAMOND, ItemTypes.DIAMOND_PICKAXE, ItemTypes.DIAMOND_AXE,
                ItemTypes.DIAMOND_SHOVEL, ItemTypes.DIAMOND_SWORD, ItemTypes.DIAMOND_HOE);

        armor(ItemTypes.IRON_INGOT, ItemTypes.IRON_HELMET, ItemTypes.IRON_CHESTPLATE,
                ItemTypes.IRON_LEGGINGS, ItemTypes.IRON_BOOTS);
        armor(ItemTypes.GOLD_INGOT, ItemTypes.GOLDEN_HELMET, ItemTypes.GOLDEN_CHESTPLATE,
                ItemTypes.GOLDEN_LEGGINGS, ItemTypes.GOLDEN_BOOTS);
        armor(ItemTypes.DIAMOND, ItemTypes.DIAMOND_HELMET, ItemTypes.DIAMOND_CHESTPLATE,
                ItemTypes.DIAMOND_LEGGINGS, ItemTypes.DIAMOND_BOOTS);
        armor(ItemTypes.LEATHER, ItemTypes.LEATHER_HELMET, ItemTypes.LEATHER_CHESTPLATE,
                ItemTypes.LEATHER_LEGGINGS, ItemTypes.LEATHER_BOOTS);
    }

    private RecipeRegistry() {}

    private static void tools(ItemType material, ItemType pickaxe, ItemType axe, ItemType shovel,
                              ItemType sword, ItemType hoe) {
        RECIPES.add(Recipe.shaped(pickaxe, 1, new String[]{"MMM", " S ", " S "},
                'M', material, 'S', ItemTypes.STICK));
        RECIPES.add(Recipe.shaped(axe, 1, new String[]{"MM", "MS", " S"},
                'M', material, 'S', ItemTypes.STICK));
        RECIPES.add(Recipe.shaped(shovel, 1, new String[]{"M", "S", "S"},
                'M', material, 'S', ItemTypes.STICK));
        RECIPES.add(Recipe.shaped(sword, 1, new String[]{"M", "M", "S"},
                'M', material, 'S', ItemTypes.STICK));
        RECIPES.add(Recipe.shaped(hoe, 1, new String[]{"MM", " S", " S"},
                'M', material, 'S', ItemTypes.STICK));
    }

    private static void armor(ItemType material, ItemType helmet, ItemType chestplate,
                              ItemType leggings, ItemType boots) {
        RECIPES.add(Recipe.shaped(helmet, 1, new String[]{"MMM", "M M"}, 'M', material));
        RECIPES.add(Recipe.shaped(chestplate, 1, new String[]{"M M", "MMM", "MMM"}, 'M', material));
        RECIPES.add(Recipe.shaped(leggings, 1, new String[]{"MMM", "M M", "M M"}, 'M', material));
        RECIPES.add(Recipe.shaped(boots, 1, new String[]{"M M", "M M"}, 'M', material));
    }

    /** The result for a square grid, or an empty stack when nothing matches. */
    public static ItemStack match(ItemStack[] grid, int dimension) {
        for (Recipe recipe : RECIPES) {
            if (recipe.matches(grid, dimension)) return recipe.result();
        }
        return ItemStack.EMPTY;
    }

    public static int size() {
        return RECIPES.size();
    }
}
