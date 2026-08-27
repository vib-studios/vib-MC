package net.vibmc.inventory;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryTest {
    @Test
    void stacksItemsAndReturnsTheRemainder() {
        Inventory inventory = new Inventory("test", 2);

        assertEquals(0, inventory.addItem(ItemStack.builder().type(ItemTypes.STONE).amount(100).build()));
        assertEquals(100, inventory.countItem(ItemTypes.STONE));
        assertEquals(36, inventory.getSlot(1).getAmount());
        assertEquals(10, inventory.addItem(ItemStack.builder().type(ItemTypes.STONE).amount(38).build()));
    }

    @Test
    void callersCannotMutateSlotsThroughReturnedValues() {
        Inventory inventory = new Inventory("test", 1);
        inventory.setSlot(0, ItemStack.builder().type(ItemTypes.DIAMOND).amount(3).build());

        inventory.getSlot(0).setAmount(99);
        inventory.getSlots()[0].setAmount(99);

        assertEquals(3, inventory.getSlot(0).getAmount());
    }

    @Test
    void validatesSizeAndSlotIndexes() {
        assertThrows(IllegalArgumentException.class, () -> new Inventory("bad", 0));
        Inventory inventory = new Inventory("test", 1);
        assertThrows(IndexOutOfBoundsException.class, () -> inventory.getSlot(1));
    }
}
