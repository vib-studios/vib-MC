package net.vibmc.inventory;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow.WindowClickType;
import net.vibmc.entity.ServerPlayer;
import net.vibmc.world.BlockEntities;
import net.vibmc.world.World;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Window click handling for the player inventory and for open containers. */
class WindowServiceTest {
    private static ItemStack stack(ItemType type, int amount) {
        return ItemStack.builder().type(type).amount(amount).version(ClientVersion.V_1_12_2).build();
    }

    private static ServerPlayer player() {
        return new ServerPlayer(new World(3L, "window-test"), null, "Tester", UUID.randomUUID());
    }

    /** Window-0 slot numbers: 36 is the first hotbar slot, 5-8 are armour. */
    private static final int HOTBAR_0 = 36, HOTBAR_1 = 37, HELMET_SLOT = 5, CHEST_SLOT = 6;

    @Test
    void leftClickPicksUpAndPutsDownAStack() {
        ServerPlayer player = player();
        player.getInventory().setSlot(0, stack(ItemTypes.DIAMOND, 12));

        WindowService.click(player, 0, HOTBAR_0, 0, WindowClickType.PICKUP);
        assertEquals(12, player.getCarriedItem().getAmount(), "the whole stack goes to the cursor");
        assertTrue(player.getInventory().getSlot(0).isEmpty());

        WindowService.click(player, 0, HOTBAR_1, 0, WindowClickType.PICKUP);
        assertTrue(player.getCarriedItem().isEmpty(), "the cursor empties into the new slot");
        assertEquals(12, player.getInventory().getSlot(1).getAmount());
    }

    @Test
    void rightClickSplitsAStackAndPlacesOne() {
        ServerPlayer player = player();
        player.getInventory().setSlot(0, stack(ItemTypes.COBBLESTONE, 9));

        WindowService.click(player, 0, HOTBAR_0, 1, WindowClickType.PICKUP);
        assertEquals(5, player.getCarriedItem().getAmount(), "right click takes half, rounded up");
        assertEquals(4, player.getInventory().getSlot(0).getAmount());

        WindowService.click(player, 0, HOTBAR_1, 1, WindowClickType.PICKUP);
        assertEquals(1, player.getInventory().getSlot(1).getAmount(), "right click places one");
        assertEquals(4, player.getCarriedItem().getAmount());
    }

    @Test
    void stacksOfTheSameItemMerge() {
        ServerPlayer player = player();
        player.getInventory().setSlot(0, stack(ItemTypes.COBBLESTONE, 40));
        player.setCarriedItem(stack(ItemTypes.COBBLESTONE, 40));

        WindowService.click(player, 0, HOTBAR_0, 0, WindowClickType.PICKUP);

        assertEquals(64, player.getInventory().getSlot(0).getAmount(), "the slot fills to the stack limit");
        assertEquals(16, player.getCarriedItem().getAmount(), "the remainder stays on the cursor");
    }

    @Test
    void armorSlotsOnlyAcceptTheMatchingPiece() {
        ServerPlayer player = player();
        player.setCarriedItem(stack(ItemTypes.DIAMOND_HELMET, 1));
        WindowService.click(player, 0, HELMET_SLOT, 0, WindowClickType.PICKUP);
        assertEquals(ItemTypes.DIAMOND_HELMET, player.getArmorPiece(Armor.HELMET).getType());

        player.setCarriedItem(stack(ItemTypes.COBBLESTONE, 1));
        WindowService.click(player, 0, CHEST_SLOT, 0, WindowClickType.PICKUP);
        assertTrue(player.getArmorPiece(Armor.CHESTPLATE).isEmpty(),
                "cobblestone must not go into the chestplate slot");
        assertEquals(ItemTypes.COBBLESTONE, player.getCarriedItem().getType(), "it stays on the cursor");
    }

    @Test
    void shiftClickingArmorEquipsIt() {
        ServerPlayer player = player();
        player.getInventory().setSlot(0, stack(ItemTypes.IRON_CHESTPLATE, 1));

        WindowService.click(player, 0, HOTBAR_0, 0, WindowClickType.QUICK_MOVE);

        assertEquals(ItemTypes.IRON_CHESTPLATE, player.getArmorPiece(Armor.CHESTPLATE).getType(),
                "shift-clicking a chestplate should wear it");
        assertTrue(player.getInventory().getSlot(0).isEmpty());
    }

    @Test
    void craftingInThePlayerGridConsumesIngredients() {
        ServerPlayer player = player();
        // Slots 1-4 are the 2x2 grid; a vertical pair of planks makes sticks.
        player.setWindowSlot(1, stack(ItemTypes.OAK_PLANKS, 2));
        player.setWindowSlot(3, stack(ItemTypes.OAK_PLANKS, 2));
        assertEquals(ItemTypes.STICK, player.windowSlot(0).getType(), "the result slot previews the craft");

        WindowService.click(player, 0, 0, 0, WindowClickType.PICKUP);

        assertEquals(ItemTypes.STICK, player.getCarriedItem().getType());
        assertEquals(4, player.getCarriedItem().getAmount());
        assertEquals(1, player.windowSlot(1).getAmount(), "one plank is consumed from each slot");
        assertEquals(1, player.windowSlot(3).getAmount());
    }

    @Test
    void theResultSlotCannotBeFilled() {
        ServerPlayer player = player();
        player.setCarriedItem(stack(ItemTypes.DIAMOND, 1));
        WindowService.click(player, 0, 0, 0, WindowClickType.PICKUP);
        assertEquals(ItemTypes.DIAMOND, player.getCarriedItem().getType(),
                "an item cannot be placed into the crafting result");
        assertTrue(player.windowSlot(0).isEmpty());
    }

    @Test
    void closingTheWindowReturnsTheCursorAndGrid() {
        ServerPlayer player = player();
        player.setWindowSlot(1, stack(ItemTypes.OAK_PLANKS, 3));
        player.setCarriedItem(stack(ItemTypes.DIAMOND, 2));

        WindowService.close(player, false);

        assertTrue(player.getCarriedItem().isEmpty(), "the cursor is emptied");
        assertTrue(player.windowSlot(1).isEmpty(), "the grid is emptied");
        assertEquals(3, player.getInventory().countItem(ItemTypes.OAK_PLANKS),
                "grid contents go back to the inventory");
        assertEquals(2, player.getInventory().countItem(ItemTypes.DIAMOND),
                "the cursor goes back to the inventory");
    }

    @Test
    void chestSlotsAreSeparateFromTheInventory() {
        ServerPlayer player = player();
        Inventory chest = new Inventory("Chest", BlockEntities.CHEST_SIZE);
        WindowSession session = new WindowSession(3, WindowSession.Type.CHEST, chest, null, 0L);
        player.setOpenWindow(session);
        player.getInventory().setSlot(0, stack(ItemTypes.DIAMOND, 5));

        // In a chest window the player's hotbar starts after 27 chest slots and 27 main slots.
        int hotbarInChestWindow = BlockEntities.CHEST_SIZE + 27;
        WindowService.click(player, 3, hotbarInChestWindow, 0, WindowClickType.QUICK_MOVE);

        assertEquals(5, chest.countItem(ItemTypes.DIAMOND), "shift-click moves the stack into the chest");
        assertTrue(player.getInventory().getSlot(0).isEmpty());

        WindowService.click(player, 3, 0, 0, WindowClickType.QUICK_MOVE);
        assertEquals(5, player.getInventory().countItem(ItemTypes.DIAMOND), "and back out again");
        assertEquals(0, chest.countItem(ItemTypes.DIAMOND));
    }

    @Test
    void clicksForAWindowThatIsNotOpenAreIgnored() {
        ServerPlayer player = player();
        player.getInventory().setSlot(0, stack(ItemTypes.DIAMOND, 5));
        WindowService.click(player, 42, HOTBAR_0, 0, WindowClickType.PICKUP);
        assertEquals(5, player.getInventory().getSlot(0).getAmount(), "a stale window id changes nothing");
        assertTrue(player.getCarriedItem().isEmpty());
    }
}
