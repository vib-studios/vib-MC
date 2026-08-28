package net.vibmc.crafting;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One crafting recipe. Shaped recipes match a pattern anywhere in the grid; shapeless ones
 * only care about the multiset of ingredients.
 */
public final class Recipe {
    private final boolean shaped;
    private final int width, height;
    /** Row-major ingredients for a shaped recipe; null means "must be empty". */
    private final ItemType[] pattern;
    private final List<ItemType> ingredients;
    private final ItemType result;
    private final int resultAmount;

    private Recipe(boolean shaped, int width, int height, ItemType[] pattern,
                   List<ItemType> ingredients, ItemType result, int resultAmount) {
        this.shaped = shaped;
        this.width = width;
        this.height = height;
        this.pattern = pattern;
        this.ingredients = ingredients;
        this.result = result;
        this.resultAmount = resultAmount;
    }

    /**
     * A shaped recipe. Rows use a character per slot and a space for an empty slot, with the
     * key giving the item for each character - the same shorthand vanilla's data files use.
     */
    public static Recipe shaped(ItemType result, int amount, String[] rows, Object... key) {
        int height = rows.length;
        int width = 0;
        for (String row : rows) width = Math.max(width, row.length());
        ItemType[] pattern = new ItemType[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                char symbol = x < rows[y].length() ? rows[y].charAt(x) : ' ';
                if (symbol == ' ') continue;
                pattern[y * width + x] = lookup(key, symbol);
            }
        }
        return new Recipe(true, width, height, pattern, Collections.<ItemType>emptyList(), result, amount);
    }

    public static Recipe shapeless(ItemType result, int amount, ItemType... ingredients) {
        return new Recipe(false, 0, 0, null, Arrays.asList(ingredients), result, amount);
    }

    private static ItemType lookup(Object[] key, char symbol) {
        for (int i = 0; i + 1 < key.length; i += 2) {
            if (key[i] instanceof Character && (Character) key[i] == symbol) return (ItemType) key[i + 1];
        }
        throw new IllegalArgumentException("recipe key has no entry for '" + symbol + "'");
    }

    public ItemStack result() {
        return ItemStack.builder().type(result).amount(resultAmount).version(ClientVersion.V_1_12_2).build();
    }

    /** Whether this recipe is satisfied by a square grid of the given dimension. */
    public boolean matches(ItemStack[] grid, int dimension) {
        return shaped ? matchesShaped(grid, dimension) : matchesShapeless(grid);
    }

    private boolean matchesShaped(ItemStack[] grid, int dimension) {
        if (width > dimension || height > dimension) return false;
        for (int offsetY = 0; offsetY + height <= dimension; offsetY++) {
            for (int offsetX = 0; offsetX + width <= dimension; offsetX++) {
                if (matchesAt(grid, dimension, offsetX, offsetY)) return true;
            }
        }
        return false;
    }

    private boolean matchesAt(ItemStack[] grid, int dimension, int offsetX, int offsetY) {
        for (int y = 0; y < dimension; y++) {
            for (int x = 0; x < dimension; x++) {
                ItemStack slot = grid[y * dimension + x];
                boolean inside = x >= offsetX && x < offsetX + width && y >= offsetY && y < offsetY + height;
                ItemType expected = inside ? pattern[(y - offsetY) * width + (x - offsetX)] : null;
                if (expected == null) {
                    if (slot != null && !slot.isEmpty()) return false;
                } else if (slot == null || slot.isEmpty() || slot.getType() != expected) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean matchesShapeless(ItemStack[] grid) {
        List<ItemType> remaining = new ArrayList<>(ingredients);
        for (ItemStack slot : grid) {
            if (slot == null || slot.isEmpty()) continue;
            if (!remaining.remove(slot.getType())) return false;
        }
        return remaining.isEmpty();
    }
}
