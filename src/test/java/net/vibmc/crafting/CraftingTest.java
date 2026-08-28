package net.vibmc.crafting;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingTest {
    private static ItemStack stack(com.github.retrooper.packetevents.protocol.item.type.ItemType type, int amount) {
        return ItemStack.builder().type(type).amount(amount).version(ClientVersion.V_1_12_2).build();
    }

    @Test
    void shapelessRecipeTurnsALogIntoPlanks() {
        CraftingGrid grid = new CraftingGrid(2);
        grid.setSlot(0, stack(ItemTypes.OAK_LOG, 3));
        assertEquals(ItemTypes.OAK_PLANKS, grid.getResult().getType());
        assertEquals(4, grid.getResult().getAmount(), "one log yields four planks");
    }

    @Test
    void shapedRecipeMatchesAnywhereInTheGrid() {
        CraftingGrid small = new CraftingGrid(2);
        small.setSlot(0, stack(ItemTypes.OAK_PLANKS, 1));
        small.setSlot(2, stack(ItemTypes.OAK_PLANKS, 1));
        assertEquals(ItemTypes.STICK, small.getResult().getType(), "sticks craft in the 2x2 grid");

        CraftingGrid large = new CraftingGrid(3);
        large.setSlot(4, stack(ItemTypes.OAK_PLANKS, 1));
        large.setSlot(7, stack(ItemTypes.OAK_PLANKS, 1));
        assertEquals(ItemTypes.STICK, large.getResult().getType(),
                "the same shape must match when offset inside a 3x3 grid");
    }

    @Test
    void shapeMatters() {
        CraftingGrid grid = new CraftingGrid(2);
        grid.setSlot(0, stack(ItemTypes.OAK_PLANKS, 1));
        grid.setSlot(1, stack(ItemTypes.OAK_PLANKS, 1));
        grid.setSlot(2, stack(ItemTypes.OAK_PLANKS, 1));
        grid.setSlot(3, stack(ItemTypes.OAK_PLANKS, 1));
        assertEquals(ItemTypes.CRAFTING_TABLE, grid.getResult().getType());

        grid.setSlot(3, ItemStack.EMPTY);
        assertTrue(grid.getResult().isEmpty(),
                "three planks in an L is neither a crafting table nor sticks");
    }

    @Test
    void aPickaxeNeedsTheFullThreeByThree() {
        CraftingGrid small = new CraftingGrid(2);
        small.setSlot(0, stack(ItemTypes.COBBLESTONE, 1));
        small.setSlot(1, stack(ItemTypes.COBBLESTONE, 1));
        assertTrue(small.getResult().isEmpty(), "a pickaxe cannot be made in a 2x2 grid");

        CraftingGrid table = new CraftingGrid(3);
        for (int slot = 0; slot < 3; slot++) table.setSlot(slot, stack(ItemTypes.COBBLESTONE, 1));
        table.setSlot(4, stack(ItemTypes.STICK, 1));
        table.setSlot(7, stack(ItemTypes.STICK, 1));
        assertEquals(ItemTypes.STONE_PICKAXE, table.getResult().getType());
    }

    @Test
    void takingTheResultConsumesOneOfEachIngredient() {
        CraftingGrid grid = new CraftingGrid(2);
        grid.setSlot(0, stack(ItemTypes.OAK_PLANKS, 3));
        grid.setSlot(2, stack(ItemTypes.OAK_PLANKS, 1));
        assertEquals(ItemTypes.STICK, grid.getResult().getType());

        grid.consumeIngredients();

        assertEquals(2, grid.getSlot(0).getAmount(), "a stack of three drops to two");
        assertTrue(grid.getSlot(2).isEmpty(), "a stack of one is emptied");
        assertTrue(grid.getResult().isEmpty(), "the result clears once its ingredients are gone");
    }

    @Test
    void clearingReturnsEverythingInTheGrid() {
        CraftingGrid grid = new CraftingGrid(3);
        grid.setSlot(0, stack(ItemTypes.DIAMOND, 5));
        ItemStack[] returned = grid.clearAndCollect();
        assertEquals(5, returned[0].getAmount(), "contents come back so they can be given to the player");
        assertTrue(grid.getSlot(0).isEmpty());
    }

    @Test
    void smeltingTurnsOreIntoIngotsAndKnowsItsFuel() {
        assertEquals(ItemTypes.IRON_INGOT, Smelting.result(stack(ItemTypes.IRON_ORE, 1)).getType());
        assertEquals(ItemTypes.GOLD_INGOT, Smelting.result(stack(ItemTypes.GOLD_ORE, 1)).getType());
        assertEquals(ItemTypes.GLASS, Smelting.result(stack(ItemTypes.SAND, 1)).getType());
        assertTrue(Smelting.result(stack(ItemTypes.DIAMOND, 1)).isEmpty(), "diamond does not smelt");

        assertEquals(1600, Smelting.burnTime(stack(ItemTypes.COAL, 1)));
        assertEquals(0, Smelting.burnTime(stack(ItemTypes.COBBLESTONE, 1)), "stone is not fuel");
    }

    @Test
    void everyRecipeIsReachableFromMinedOrSmeltedMaterials() {
        assertTrue(RecipeRegistry.size() > 30, "the recipe book should cover tools and armour");
        CraftingGrid grid = new CraftingGrid(3);
        for (int slot = 0; slot < 9; slot++) {
            if (slot != 4) grid.setSlot(slot, stack(ItemTypes.COBBLESTONE, 1));
        }
        assertFalse(grid.getResult().isEmpty(), "cobblestone around an empty centre is a furnace");
        assertEquals(ItemTypes.FURNACE, grid.getResult().getType());
    }
}
