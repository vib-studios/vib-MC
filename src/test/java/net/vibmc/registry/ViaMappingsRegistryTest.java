package net.vibmc.registry;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.viaversion.nbt.tag.compound.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ViaMappingsRegistryTest {
    @BeforeAll
    static void initialize() throws Exception {
        ViaMappingsRegistry.initialize();
    }

    @Test
    void resolvesAndLoadsViaVersionMappingsNbtSnapshots() {
        CompoundTag canonical = ViaMappingsRegistry.get().forClient(ClientVersion.V_1_12_2);
        assertNotNull(canonical);

        CompoundTag modern = ViaMappingsRegistry.get().forClient(ClientVersion.V_1_19_4);
        assertNotNull(modern);
        assertEquals("1.19.4", modern.getString("release"));

        CompoundTag split = ViaMappingsRegistry.get().forClient(ClientVersion.V_1_20_5);
        assertNotNull(split);
        assertEquals("split", split.getString("codecType"));
    }

    @Test
    void mappingsNbtIsParsedAndCached() {
        CompoundTag tag1 = ViaMappingsRegistry.get().forClient(ClientVersion.V_1_20);
        CompoundTag tag2 = ViaMappingsRegistry.get().forClient(ClientVersion.V_1_20);
        assertSame(tag1, tag2);
    }
}
