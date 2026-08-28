package net.vibmc.world.storage;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import net.vibmc.inventory.Inventory;
import net.vibmc.world.BlockEntities;
import net.vibmc.world.Furnace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chest and furnace contents survive a restart; chunk files hold block states only. */
class ContainerPersistenceTest {
    private static ItemStack stack(com.github.retrooper.packetevents.protocol.item.type.ItemType type, int amount) {
        return ItemStack.builder().type(type).amount(amount).version(ClientVersion.V_1_12_2).build();
    }

    @Test
    void chestContentsRoundTrip(@TempDir Path directory) throws IOException {
        WorldStorage storage = new WorldStorage(directory.resolve("world").toString());
        storage.prepare();
        BlockEntities entities = new BlockEntities();
        Inventory chest = entities.container(10, 64, -20, "Chest", BlockEntities.CHEST_SIZE);
        chest.setSlot(0, stack(ItemTypes.DIAMOND, 7));
        chest.setSlot(26, stack(ItemTypes.COBBLESTONE, 64));

        storage.writeContainers(entities);

        BlockEntities restored = new BlockEntities();
        storage.readContainers(restored);
        Inventory reloaded = restored.existing(10, 64, -20);
        assertEquals(7, reloaded.getSlot(0).getAmount());
        assertEquals(ItemTypes.DIAMOND, reloaded.getSlot(0).getType());
        assertEquals(64, reloaded.getSlot(26).getAmount(), "the last slot survives too");
    }

    @Test
    void furnaceSlotsAndTimersRoundTrip(@TempDir Path directory) throws IOException {
        WorldStorage storage = new WorldStorage(directory.resolve("world").toString());
        storage.prepare();
        BlockEntities entities = new BlockEntities();
        Furnace furnace = entities.furnace(-5, 70, 8);
        furnace.slots().setSlot(Furnace.INPUT, stack(ItemTypes.IRON_ORE, 3));
        furnace.slots().setSlot(Furnace.FUEL, stack(ItemTypes.COAL, 2));
        furnace.restoreState(400, 1600, 120);

        storage.writeContainers(entities);

        BlockEntities restored = new BlockEntities();
        storage.readContainers(restored);
        Furnace reloaded = restored.furnace(-5, 70, 8);
        assertEquals(3, reloaded.slots().getSlot(Furnace.INPUT).getAmount());
        assertEquals(ItemTypes.COAL, reloaded.slots().getSlot(Furnace.FUEL).getType());
        assertEquals(400, reloaded.burnTime(), "a part-burnt furnace keeps its fuel timer");
        assertEquals(120, reloaded.cookTime());
    }

    @Test
    void negativeCoordinatesArePreserved(@TempDir Path directory) throws IOException {
        WorldStorage storage = new WorldStorage(directory.resolve("world").toString());
        storage.prepare();
        BlockEntities entities = new BlockEntities();
        entities.container(-1000, 12, -2000, "Chest", BlockEntities.CHEST_SIZE)
                .setSlot(1, stack(ItemTypes.EMERALD, 2));

        storage.writeContainers(entities);
        BlockEntities restored = new BlockEntities();
        storage.readContainers(restored);

        assertEquals(ItemTypes.EMERALD, restored.existing(-1000, 12, -2000).getSlot(1).getType(),
                "packed positions must round-trip through negative coordinates");
    }

    @Test
    void anEmptyWorldWritesNoContainerFile(@TempDir Path directory) throws IOException {
        WorldStorage storage = new WorldStorage(directory.resolve("world").toString());
        storage.prepare();
        storage.writeContainers(new BlockEntities());
        assertFalse(Files.exists(storage.worldDir().resolve("containers.dat")),
                "a world with no containers should not leave an empty file behind");
    }

    @Test
    void readingAWorldWithNoContainerFileIsHarmless(@TempDir Path directory) throws IOException {
        WorldStorage storage = new WorldStorage(directory.resolve("world").toString());
        storage.prepare();
        BlockEntities entities = new BlockEntities();
        storage.readContainers(entities);
        assertTrue(entities.all().isEmpty(), "an absent file simply means no containers");
    }
}
