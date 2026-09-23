package net.vibmc.registry;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ViaMappingsTest {

    @Test
    void loadsNbtViaPacketEventsReader() throws Exception {
        NBTCompound root = ViaMappings.get().loadNbt("identifier-table.nbt");
        assertNotNull(root);
        assertTrue(root.getTags().containsKey("blocks"));
        assertTrue(root.getTags().containsKey("items"));
    }

    @Test
    void decodesBlockStateMapping13To132() throws Exception {
        int[] inverse = ViaMappings.get().blockStateInverse13To132();
        assertNotNull(inverse);
        assertTrue(inverse.length >= 3052, "inverse should cover at least 3051");
        // From earlier manual decode: 1.13 id 3050 -> 1.13.2 id 3051, so inverse[3051]=3050
        assertEquals(3050, inverse[3051], "crafting table mapping");
        // light_blue_carpet: 1.13 id 6826 -> 1.13.2 id 6827
        assertEquals(6826, inverse[6827], "light_blue_carpet mapping");
        // Check that 1128 maps to 1127 (the -1 shift region)
        assertEquals(1127, inverse[1128]);
        // Gap at 1126 should be -1 (extra TNT state in 1.13.2)
        assertEquals(-1, inverse[1126]);
    }

    @Test
    void decodesIdRanges() {
        // Example from earlier: tags.block.air bytes [0,0,216,5,1] -> ids {0, 729, 730}
        // That encoding is varint pairs: start offset, length
        // We test the decoder directly with a simple case
        // Encode: start 0, length 0 => id 0; then start 728 (729-0-1), length 1 => ids 729,730
        // Varint encoding for 0 is 0x00, for 728 is 0xD8 0x05, for 1 is 0x01
        byte[] data = new byte[]{0, 0, (byte) 0xD8, 0x05, 1};
        List<Integer> ids = ViaMappings.decodeIdRanges(data);
        assertEquals(3, ids.size());
        assertTrue(ids.contains(0));
        assertTrue(ids.contains(729));
        assertTrue(ids.contains(730));
    }

    @Test
    void loadsTimelineRegistryViaPE() throws Exception {
        Map<String, NBTCompound> timeline = ViaMappings.get().loadTimelineRegistry();
        assertNotNull(timeline);
        assertFalse(timeline.isEmpty());
        // Known timelines from 1.21.11
        assertTrue(timeline.containsKey("minecraft:day") || timeline.containsKey("minecraft:moon") ||
                timeline.keySet().stream().anyMatch(k -> k.contains("day")));
    }

    @Test
    void loadsSoundVariantRegistriesViaPE() throws Exception {
        Map<String, Map<String, NBTCompound>> soundRegs = ViaMappings.get().loadSoundVariantRegistries();
        assertNotNull(soundRegs);
        assertFalse(soundRegs.isEmpty());
        // Should contain cat_sound_variant etc.
        assertTrue(soundRegs.containsKey("cat_sound_variant") ||
                soundRegs.containsKey("cow_sound_variant") ||
                soundRegs.containsKey("pig_sound_variant"));
    }
}
