package net.vibmc.inventory;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import net.vibmc.crafting.CraftingGrid;

/**
 * An open container window. Slot indices follow the vanilla layout for the window type: the
 * container's own slots come first, then the player's main inventory, then their hotbar.
 */
public final class WindowSession {
    public enum Type {
        CRAFTING_TABLE("minecraft:crafting_table", 1, "Crafting", 10),
        CHEST("minecraft:chest", 0, "Chest", 27),
        FURNACE("minecraft:furnace", 14, "Furnace", 3);

        private final String legacyId;
        private final int modernType;
        private final String title;
        private final int topSize;

        Type(String legacyId, int modernType, String title, int topSize) {
            this.legacyId = legacyId;
            this.modernType = modernType;
            this.title = title;
            this.topSize = topSize;
        }

        public String legacyId() { return legacyId; }
        public int modernType() { return modernType; }
        public String title() { return title; }
        /** Slots belonging to the container itself, before the player's inventory. */
        public int topSize() { return topSize; }
    }

    private final int windowId;
    private final Type type;
    private final Inventory container;
    private final CraftingGrid grid;
    private final long blockPosition;

    public WindowSession(int windowId, Type type, Inventory container, CraftingGrid grid, long blockPosition) {
        this.windowId = windowId;
        this.type = type;
        this.container = container;
        this.grid = grid;
        this.blockPosition = blockPosition;
    }

    public int windowId() { return windowId; }
    public Type type() { return type; }
    public Inventory container() { return container; }
    public CraftingGrid grid() { return grid; }
    public long blockPosition() { return blockPosition; }
    public int size() { return type.topSize() + 36; }

    /** True for the crafting result, which can be taken from but never placed into. */
    public boolean isResultSlot(int slot) {
        return type == Type.CRAFTING_TABLE && slot == 0;
    }

    public ItemStack getTop(int slot) {
        if (type == Type.CRAFTING_TABLE) {
            return slot == 0 ? grid.getResult() : grid.getSlot(slot - 1);
        }
        return container.getSlot(slot);
    }

    public void setTop(int slot, ItemStack item) {
        if (type == Type.CRAFTING_TABLE) {
            if (slot > 0) grid.setSlot(slot - 1, item);
            return;
        }
        container.setSlot(slot, item);
    }
}
