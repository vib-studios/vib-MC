package net.vibmc.inventory;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.sound.SoundCategory;
import com.github.retrooper.packetevents.protocol.sound.Sounds;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow.WindowClickType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import net.vibmc.entity.ServerPlayer;
import net.vibmc.player.GameMode;
import net.vibmc.world.Effects;

import java.util.ArrayList;
import java.util.List;

/**
 * Container windows and the clicks inside them.
 *
 * The server is authoritative and resynchronises the whole window after every click rather
 * than trusting the client's predicted state. That costs a packet per click and makes the
 * unsupported click types (drag-crafting, double-click collect) harmless: the client simply
 * snaps back to what the server holds.
 */
public final class WindowService {
    /** Window 0 is always the player's own inventory. */
    private static final int PLAYER_WINDOW = 0;
    /** Cursor slot in Set Slot, per the vanilla protocol. */
    private static final int CARRIED_SLOT = -1;

    private WindowService() {}

    public static void open(ServerPlayer player, WindowSession session) {
        if (player.getUser() == null) return;
        player.setOpenWindow(session);
        net.kyori.adventure.text.Component title = net.kyori.adventure.text.Component.text(session.type().title());
        boolean modern = player.getUser().getClientVersion().isNewerThanOrEquals(
                com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_14);
        player.getUser().sendPacket(modern
                ? new WrapperPlayServerOpenWindow(session.windowId(), session.type().modernType(), title)
                : new WrapperPlayServerOpenWindow(session.windowId(), session.type().legacyId(), title,
                        session.type().topSize(), 0));
        refresh(player);
    }

    /** Closes whatever is open, returning the cursor and any crafting grid contents. */
    public static void close(ServerPlayer player, boolean tellClient) {
        WindowSession session = player.getOpenWindow();
        returnToInventory(player, player.getCarriedItem());
        player.setCarriedItem(ItemStack.EMPTY);
        if (session != null) {
            if (session.grid() != null) {
                for (ItemStack leftover : session.grid().clearAndCollect()) returnToInventory(player, leftover);
            }
            if (session.type() == WindowSession.Type.CHEST) {
                Effects.sound(player.getWorld(), player.getX(), player.getY(), player.getZ(),
                        Sounds.BLOCK_CHEST_CLOSE, SoundCategory.BLOCK, 0.5f, 1.0f);
            }
            player.setOpenWindow(null);
        }
        // The player's own 2x2 grid empties on close too, exactly like vanilla.
        for (ItemStack leftover : player.getCrafting().clearAndCollect()) returnToInventory(player, leftover);
        if (tellClient && player.getUser() != null) {
            player.getUser().sendPacket(new WrapperPlayServerCloseWindow(
                    session == null ? PLAYER_WINDOW : session.windowId()));
        }
        player.sendInventory();
    }

    private static void returnToInventory(ServerPlayer player, ItemStack item) {
        if (item == null || item.isEmpty()) return;
        player.getInventory().addItem(item);
    }

    public static void click(ServerPlayer player, int windowId, int slot, int button, WindowClickType type) {
        click(player, windowId, slot, button, type, -1);
    }

    /**
     * Applies a click and resynchronises.
     *
     * Clients before 1.17 will not act on another inventory click until the server confirms
     * the previous one, so the transaction has to be answered even when the click is rejected.
     */
    public static void click(ServerPlayer player, int windowId, int slot, int button,
                             WindowClickType type, int actionNumber) {
        try {
            apply(player, windowId, slot, button, type);
        } finally {
            confirm(player, windowId, actionNumber);
        }
    }

    /** Answers the client's transaction so its inventory stops waiting on us. */
    private static void confirm(ServerPlayer player, int windowId, int actionNumber) {
        if (player.getUser() == null || actionNumber < 0) return;
        if (player.getUser().getClientVersion().isNewerThanOrEquals(
                com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_17)) return;
        player.getUser().sendPacket(new com.github.retrooper.packetevents.wrapper.play.server
                .WrapperPlayServerWindowConfirmation(windowId, (short) actionNumber, true));
    }

    private static void apply(ServerPlayer player, int windowId, int slot, int button, WindowClickType type) {
        WindowSession session = player.getOpenWindow();
        // A client only clicks its own inventory when no container is open. If the server
        // still thinks one is, the Close Window packet was lost and the player's inventory
        // would be unusable until they reopened something; treat the click as the close.
        if (windowId == PLAYER_WINDOW && session != null) {
            close(player, false);
            session = null;
        }
        int expected = session == null ? PLAYER_WINDOW : session.windowId();
        if (windowId != expected) { refresh(player); return; }
        int size = session == null ? 46 : session.size();
        // Slot -999 is a click outside the window, which vanilla uses to throw an item away.
        if (slot < 0 || slot >= size) { refresh(player); return; }

        switch (type) {
            case PICKUP:
                pickup(player, session, slot, button);
                break;
            case QUICK_MOVE:
                quickMove(player, session, slot);
                break;
            case SWAP:
                swapWithHotbar(player, session, slot, button);
                break;
            case CLONE:
                if (player.getGameModeEnum() == GameMode.CREATIVE && player.getCarriedItem().isEmpty()) {
                    ItemStack clicked = get(player, session, slot);
                    if (!clicked.isEmpty()) {
                        ItemStack copy = clicked.copy();
                        copy.setAmount(copy.getType().getMaxAmount());
                        player.setCarriedItem(copy);
                    }
                }
                break;
            default:
                // THROW, QUICK_CRAFT, PICKUP_ALL and anything newer: no state change, resync.
                break;
        }
        refresh(player);
    }

    /** Left click swaps or merges the whole stack; right click splits or places one. */
    private static void pickup(ServerPlayer player, WindowSession session, int slot, int button) {
        ItemStack carried = player.getCarriedItem();
        ItemStack clicked = get(player, session, slot);
        boolean resultSlot = isResultSlot(session, slot);

        if (resultSlot) {
            if (clicked.isEmpty()) return;
            if (!carried.isEmpty() && (carried.getType() != clicked.getType()
                    || carried.getAmount() + clicked.getAmount() > carried.getType().getMaxAmount())) return;
            ItemStack taken = clicked.copy();
            if (!carried.isEmpty()) taken.setAmount(carried.getAmount() + clicked.getAmount());
            player.setCarriedItem(taken);
            craftingGrid(player, session).consumeIngredients();
            Effects.soundTo(player, Sounds.ENTITY_ITEM_PICKUP, SoundCategory.PLAYER, 0.3f, 1.6f);
            return;
        }

        if (button == 1) {
            if (carried.isEmpty()) {
                if (clicked.isEmpty()) return;
                int half = (clicked.getAmount() + 1) / 2;
                ItemStack taken = clicked.copy();
                taken.setAmount(half);
                ItemStack left = clicked.copy();
                left.setAmount(clicked.getAmount() - half);
                player.setCarriedItem(taken);
                set(player, session, slot, left.getAmount() <= 0 ? ItemStack.EMPTY : left);
                return;
            }
            if (clicked.isEmpty() || sameKind(clicked, carried)) {
                int existing = clicked.isEmpty() ? 0 : clicked.getAmount();
                if (existing + 1 > carried.getType().getMaxAmount()) return;
                ItemStack placed = carried.copy();
                placed.setAmount(existing + 1);
                if (!accepts(player, session, slot, placed)) return;
                set(player, session, slot, placed);
                ItemStack remaining = carried.copy();
                remaining.setAmount(carried.getAmount() - 1);
                player.setCarriedItem(remaining.getAmount() <= 0 ? ItemStack.EMPTY : remaining);
            }
            return;
        }

        if (carried.isEmpty()) {
            player.setCarriedItem(clicked);
            set(player, session, slot, ItemStack.EMPTY);
            return;
        }
        if (!clicked.isEmpty() && sameKind(clicked, carried)) {
            int room = clicked.getType().getMaxAmount() - clicked.getAmount();
            int moved = Math.min(room, carried.getAmount());
            if (moved <= 0) return;
            ItemStack merged = clicked.copy();
            merged.setAmount(clicked.getAmount() + moved);
            set(player, session, slot, merged);
            ItemStack remaining = carried.copy();
            remaining.setAmount(carried.getAmount() - moved);
            player.setCarriedItem(remaining.getAmount() <= 0 ? ItemStack.EMPTY : remaining);
            return;
        }
        if (!accepts(player, session, slot, carried)) return;
        set(player, session, slot, carried);
        player.setCarriedItem(clicked);
    }

    /** Shift-click: move a stack between the container half and the player half. */
    private static void quickMove(ServerPlayer player, WindowSession session, int slot) {
        ItemStack moving = get(player, session, slot);
        if (moving.isEmpty()) return;
        if (isResultSlot(session, slot)) {
            if (player.getInventory().addItem(moving) == 0) craftingGrid(player, session).consumeIngredients();
            return;
        }
        int top = session == null ? 46 : session.type().topSize();
        boolean fromTop = session == null ? slot < 9 || slot == 45 : slot < top;
        int leftover = fromTop
                ? player.getInventory().addItem(moving)
                : insertIntoTop(player, session, moving);
        if (leftover == moving.getAmount()) return;
        if (leftover <= 0) {
            set(player, session, slot, ItemStack.EMPTY);
        } else {
            ItemStack remaining = moving.copy();
            remaining.setAmount(leftover);
            set(player, session, slot, remaining);
        }
    }

    /** Fills the container half (or, in the player window, the armour slots) with a stack. */
    private static int insertIntoTop(ServerPlayer player, WindowSession session, ItemStack moving) {
        if (session == null) {
            int armorSlot = Armor.slotFor(moving);
            if (armorSlot >= 0 && player.getArmorPiece(armorSlot).isEmpty()) {
                player.setArmorPiece(armorSlot, moving);
                return 0;
            }
            return moving.getAmount();
        }
        if (session.type() == WindowSession.Type.CRAFTING_TABLE) return moving.getAmount();
        return session.container().addItem(moving);
    }

    /** Number keys 1-9 swap the clicked slot with that hotbar slot. */
    private static void swapWithHotbar(ServerPlayer player, WindowSession session, int slot, int button) {
        if (button < 0 || button > 8) return;
        if (isResultSlot(session, slot)) return;
        ItemStack hotbar = player.getInventory().getSlot(button);
        ItemStack clicked = get(player, session, slot);
        if (!clicked.isEmpty() && !accepts(player, session, slot, hotbar) && !hotbar.isEmpty()) return;
        set(player, session, slot, hotbar);
        player.getInventory().setSlot(button, clicked);
    }

    /** Armour slots only take the matching piece; the crafting result takes nothing. */
    private static boolean accepts(ServerPlayer player, WindowSession session, int slot, ItemStack item) {
        if (isResultSlot(session, slot)) return false;
        if (session != null || item == null || item.isEmpty()) return true;
        if (slot >= 5 && slot <= 8) return Armor.slotFor(item) == slot - 5;
        return true;
    }

    private static boolean isResultSlot(WindowSession session, int slot) {
        return session == null ? slot == 0 : session.isResultSlot(slot);
    }

    private static net.vibmc.crafting.CraftingGrid craftingGrid(ServerPlayer player, WindowSession session) {
        return session == null || session.grid() == null ? player.getCrafting() : session.grid();
    }

    private static boolean sameKind(ItemStack first, ItemStack second) {
        return first.getType() == second.getType() && first.getDamageValue() == second.getDamageValue();
    }

    private static ItemStack get(ServerPlayer player, WindowSession session, int slot) {
        if (session == null) return player.windowSlot(slot);
        int top = session.type().topSize();
        if (slot < top) return session.getTop(slot);
        return player.getInventory().getSlot(playerIndex(slot - top));
    }

    private static void set(ServerPlayer player, WindowSession session, int slot, ItemStack item) {
        if (session == null) { player.setWindowSlot(slot, item); return; }
        int top = session.type().topSize();
        if (slot < top) session.setTop(slot, item);
        else player.getInventory().setSlot(playerIndex(slot - top), item);
    }

    /** Container windows list the main inventory first and the hotbar last. */
    private static int playerIndex(int offset) {
        return offset < 27 ? 9 + offset : offset - 27;
    }

    /** Pushes the authoritative window contents and cursor back to the client. */
    public static void refresh(ServerPlayer player) {
        if (player.getUser() == null) return;
        WindowSession session = player.getOpenWindow();
        if (session == null) {
            player.sendInventory();
        } else {
            List<ItemStack> items = new ArrayList<>();
            for (int slot = 0; slot < session.size(); slot++) items.add(get(player, session, slot));
            player.getUser().sendPacket(new WrapperPlayServerWindowItems(session.windowId(), 0, items, ItemStack.EMPTY));
        }
        player.getUser().sendPacket(new WrapperPlayServerSetSlot(CARRIED_SLOT, 0, CARRIED_SLOT, player.getCarriedItem()));
    }
}
