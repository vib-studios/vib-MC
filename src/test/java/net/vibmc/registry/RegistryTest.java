package net.vibmc.registry;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegistryTest {
    @BeforeAll
    static void initialize() throws Exception {
        Registry.initialize();
    }

    @Test
    void resolvesAndLoadsViaVersionMappingsNbtSnapshotsWithPacketEventsNbt() {
        NBTCompound canonical = Registry.get().forClient(ClientVersion.V_1_12_2);
        assertNotNull(canonical);

        NBTCompound modern = Registry.get().forClient(ClientVersion.V_1_19_4);
        assertNotNull(modern);
        assertEquals("1.19.4", modern.getStringTagValueOrNull("release"));

        NBTCompound split = Registry.get().forClient(ClientVersion.V_1_20_5);
        assertNotNull(split);
        assertEquals("split", split.getStringTagValueOrNull("codecType"));
    }

    @Test
    void loadsExtraMappingResources() {
        NBTCompound fluids = Registry.get().extraResource("fluids-26.1.nbt");
        assertNotNull(fluids);
        assertNotNull(fluids.getTagOrNull("fluids"));

        NBTCompound enchantments = Registry.get().extraResource("enchantments-1.21.nbt");
        assertNotNull(enchantments);
        assertNotNull(enchantments.getTagOrNull("entries"));
    }

    @Test
    void mappingsNbtIsParsedAndCached() {
        NBTCompound tag1 = Registry.get().forClient(ClientVersion.V_1_20);
        NBTCompound tag2 = Registry.get().forClient(ClientVersion.V_1_20);
        assertSame(tag1, tag2);
    }
}
