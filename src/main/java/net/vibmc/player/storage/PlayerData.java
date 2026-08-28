package net.vibmc.player.storage;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import net.vibmc.player.GameMode;

/** Immutable versioned snapshot of gameplay state persisted by authenticated UUID. */
public final class PlayerData {
    public final String worldName;
    public final double x, y, z;
    public final float yaw, pitch, health, foodSaturation;
    public final int foodLevel, heldItemSlot;
    public final GameMode gameMode;
    public final boolean flying;
    public final ItemStack[] inventory;
    /** Worn armour, in {@link net.vibmc.inventory.Armor} slot order. */
    public final ItemStack[] armor;
    public final ItemStack offhand;
    public final int airSupply;
    public final float exhaustion;

    public PlayerData(String worldName, double x, double y, double z, float yaw, float pitch,
                      float health, int foodLevel, float foodSaturation, GameMode gameMode,
                      boolean flying, int heldItemSlot, ItemStack[] inventory) {
        this(worldName, x, y, z, yaw, pitch, health, foodLevel, foodSaturation, gameMode, flying,
                heldItemSlot, inventory, new ItemStack[net.vibmc.inventory.Armor.SLOTS],
                ItemStack.EMPTY, 300, 0.0f);
    }

    public PlayerData(String worldName, double x, double y, double z, float yaw, float pitch,
                      float health, int foodLevel, float foodSaturation, GameMode gameMode,
                      boolean flying, int heldItemSlot, ItemStack[] inventory, ItemStack[] armor,
                      ItemStack offhand, int airSupply, float exhaustion) {
        this.worldName = worldName;
        this.x = x; this.y = y; this.z = z;
        this.yaw = yaw; this.pitch = pitch; this.health = health;
        this.foodLevel = foodLevel; this.foodSaturation = foodSaturation;
        this.gameMode = gameMode; this.flying = flying; this.heldItemSlot = heldItemSlot;
        this.inventory = copy(inventory);
        this.armor = copy(armor);
        this.offhand = offhand == null ? ItemStack.EMPTY : offhand.copy();
        this.airSupply = airSupply;
        this.exhaustion = exhaustion;
    }

    private static ItemStack[] copy(ItemStack[] source) {
        ItemStack[] result = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i] == null ? ItemStack.EMPTY : source[i].copy();
        }
        return result;
    }
}
