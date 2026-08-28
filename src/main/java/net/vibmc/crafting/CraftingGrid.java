package net.vibmc.crafting;

import com.github.retrooper.packetevents.protocol.item.ItemStack;

import java.util.Arrays;

/**
 * A square crafting grid plus its result slot. The player carries a 2x2 grid in their own
 * inventory window; a crafting table opens a 3x3 one.
 */
public final class CraftingGrid {
    private final int dimension;
    private final ItemStack[] grid;
    private ItemStack result = ItemStack.EMPTY;

    public CraftingGrid(int dimension) {
        if (dimension != 2 && dimension != 3) {
            throw new IllegalArgumentException("crafting grid must be 2x2 or 3x3");
        }
        this.dimension = dimension;
        this.grid = new ItemStack[dimension * dimension];
        Arrays.fill(grid, ItemStack.EMPTY);
    }

    public int dimension() { return dimension; }
    public int size() { return grid.length; }

    public ItemStack getSlot(int index) {
        return index < 0 || index >= grid.length ? ItemStack.EMPTY : grid[index].copy();
    }

    public void setSlot(int index, ItemStack item) {
        if (index < 0 || index >= grid.length) return;
        grid[index] = item == null ? ItemStack.EMPTY : item.copy();
        refreshResult();
    }

    public ItemStack getResult() { return result.copy(); }

    /** Recomputes the result slot from the current grid contents. */
    public void refreshResult() {
        result = RecipeRegistry.match(grid, dimension);
    }

    /** Consumes one of every ingredient, as taking the result does. */
    public void consumeIngredients() {
        for (int slot = 0; slot < grid.length; slot++) {
            if (grid[slot].isEmpty()) continue;
            grid[slot].setAmount(grid[slot].getAmount() - 1);
            if (grid[slot].getAmount() <= 0) grid[slot] = ItemStack.EMPTY;
        }
        refreshResult();
    }

    /** Empties the grid and returns what was in it, for giving back when a window closes. */
    public ItemStack[] clearAndCollect() {
        ItemStack[] contents = new ItemStack[grid.length];
        for (int slot = 0; slot < grid.length; slot++) {
            contents[slot] = grid[slot];
            grid[slot] = ItemStack.EMPTY;
        }
        result = ItemStack.EMPTY;
        return contents;
    }
}
