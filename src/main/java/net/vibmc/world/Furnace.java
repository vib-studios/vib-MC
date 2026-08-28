package net.vibmc.world;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import net.vibmc.crafting.Smelting;
import net.vibmc.entity.ServerPlayer;
import net.vibmc.inventory.Inventory;
import net.vibmc.server.VibMC;

/**
 * A furnace block entity: three slots, a fuel timer, and a cook timer. Ticked by the world it
 * lives in, and it pushes its two progress bars to anyone with the window open.
 */
public final class Furnace {
    public static final int INPUT = 0, FUEL = 1, OUTPUT = 2;
    /** Ticks to smelt one item, matching vanilla. */
    private static final int COOK_TIME = 200;

    private final Inventory slots = new Inventory("Furnace", 3);
    private int burnTime;
    private int burnTimeTotal;
    private int cookTime;

    public Inventory slots() { return slots; }
    public int burnTime() { return burnTime; }
    public int burnTimeTotal() { return burnTimeTotal; }
    public int cookTime() { return cookTime; }

    public void restoreState(int burnTime, int burnTimeTotal, int cookTime) {
        this.burnTime = Math.max(0, burnTime);
        this.burnTimeTotal = Math.max(0, burnTimeTotal);
        this.cookTime = Math.max(0, Math.min(COOK_TIME, cookTime));
    }

    /** One tick of burning and cooking. Returns true when anything visible changed. */
    boolean tick(World world, long position) {
        boolean changed = false;
        boolean slotsChanged = false;
        if (burnTime > 0) {
            burnTime--;
            changed = true;
        }
        ItemStack input = slots.getSlot(INPUT);
        ItemStack result = Smelting.result(input);
        boolean canCook = !result.isEmpty() && fits(result);

        if (burnTime == 0 && canCook) {
            int fuelTicks = Smelting.burnTime(slots.getSlot(FUEL));
            if (fuelTicks > 0) {
                burnTime = fuelTicks;
                burnTimeTotal = fuelTicks;
                slots.removeItem(FUEL, 1);
                changed = true;
                slotsChanged = true;
            }
        }
        if (burnTime > 0 && canCook) {
            if (++cookTime >= COOK_TIME) {
                cookTime = 0;
                smelt(result);
                slotsChanged = true;
            }
            changed = true;
        } else if (cookTime > 0) {
            cookTime = Math.max(0, cookTime - 2);
            changed = true;
        }
        if (changed) broadcastProgress(world, position, slotsChanged);
        return changed;
    }

    private boolean fits(ItemStack result) {
        ItemStack output = slots.getSlot(OUTPUT);
        if (output.isEmpty()) return true;
        return output.getType() == result.getType()
                && output.getAmount() + result.getAmount() <= output.getType().getMaxAmount();
    }

    private void smelt(ItemStack result) {
        ItemStack output = slots.getSlot(OUTPUT);
        if (output.isEmpty()) {
            slots.setSlot(OUTPUT, result);
        } else {
            ItemStack merged = output.copy();
            merged.setAmount(output.getAmount() + result.getAmount());
            slots.setSlot(OUTPUT, merged);
        }
        slots.removeItem(INPUT, 1);
    }

    /** Window properties 0-3 drive the flame and arrow the client draws. */
    private void broadcastProgress(World world, long position, boolean slotsChanged) {
        VibMC server = VibMC.getInstance();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerManager().getOnlinePlayers()) {
            net.vibmc.inventory.WindowSession window = player.getOpenWindow();
            if (window == null || player.getWorld() != world) continue;
            if (window.type() != net.vibmc.inventory.WindowSession.Type.FURNACE) continue;
            if (window.blockPosition() != position) continue;
            sendProperty(player, window.windowId(), 0, burnTime);
            sendProperty(player, window.windowId(), 1, burnTimeTotal);
            sendProperty(player, window.windowId(), 2, cookTime);
            sendProperty(player, window.windowId(), 3, COOK_TIME);
            // The item slots only move when fuel is consumed or an item finishes smelting;
            // resending the whole window every tick would be pure waste.
            if (slotsChanged) net.vibmc.inventory.WindowService.refresh(player);
        }
    }

    private static void sendProperty(ServerPlayer player, int windowId, int property, int value) {
        if (player.getUser() == null) return;
        player.getUser().sendPacket(
                new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowProperty(
                        windowId, property, value));
    }
}
