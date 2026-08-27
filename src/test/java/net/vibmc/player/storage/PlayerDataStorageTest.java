package net.vibmc.player.storage;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import net.vibmc.player.GameMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerDataStorageTest {
    @TempDir Path temporaryDirectory;

    @Test
    void completePlayerStateAndItemNbtRoundTrip() throws Exception {
        PlayerDataStorage storage = new PlayerDataStorage(temporaryDirectory.resolve("playerdata"));
        UUID uuid = UUID.randomUUID();
        ItemStack sword = ItemStack.builder().type(ItemTypes.DIAMOND_SWORD).amount(1)
                .version(ClientVersion.V_1_12_2).build();
        sword.setDamageValue(27);
        sword.getOrCreateTag().setTag("owner", new NBTString("test-player"));
        ItemStack[] inventory = new ItemStack[36];
        for (int i = 0; i < inventory.length; i++) inventory[i] = ItemStack.EMPTY;
        inventory[4] = sword;
        PlayerData expected = new PlayerData("world_nether", 12.5, 66.0, -8.25,
                90.0f, -10.0f, 17.5f, 14, 3.5f, GameMode.CREATIVE,
                true, 4, inventory);

        storage.write(uuid, expected);
        Optional<PlayerData> result = storage.read(uuid);

        assertTrue(result.isPresent());
        PlayerData actual = result.get();
        assertEquals("world_nether", actual.worldName);
        assertEquals(12.5, actual.x);
        assertEquals(66.0, actual.y);
        assertEquals(-8.25, actual.z);
        assertEquals(GameMode.CREATIVE, actual.gameMode);
        assertTrue(actual.flying);
        assertEquals(4, actual.heldItemSlot);
        assertEquals(ItemTypes.DIAMOND_SWORD, actual.inventory[4].getType());
        assertEquals(27, actual.inventory[4].getDamageValue());
        assertEquals(new NBTString("test-player"), actual.inventory[4].getNBT().getTagOrNull("owner"));
    }

    @Test
    void absentPlayerHasNoState() throws Exception {
        PlayerDataStorage storage = new PlayerDataStorage(temporaryDirectory.resolve("playerdata"));
        assertFalse(storage.read(UUID.randomUUID()).isPresent());
    }

    @Test
    void rejectsCorruptData() throws Exception {
        PlayerDataStorage storage = new PlayerDataStorage(temporaryDirectory.resolve("playerdata"));
        UUID uuid = UUID.randomUUID();
        Files.createDirectories(storage.directory());
        Files.write(storage.directory().resolve(uuid + ".dat"), new byte[]{1, 2, 3});
        assertThrows(java.io.IOException.class, () -> storage.read(uuid));
    }
}
