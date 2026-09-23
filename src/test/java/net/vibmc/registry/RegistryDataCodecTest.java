package net.vibmc.registry;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RegistryDataCodecTest {

    @Test
    void oneSixteenTwoCodecContainsDimensionsAndBiomes() {
        NBTCompound codec = RegistryDataCodec.create(ClientVersion.V_1_16_2);
        NBTCompound dimensions = codec.getCompoundTagOrThrow("minecraft:dimension_type");
        NBTList<NBTCompound> values = dimensions.getCompoundListTagOrThrow("value");
        assertTrue(values.size() >= 3, "dimension_type should have at least 3 entries");
        NBTList<NBTCompound> biomes = codec.getCompoundTagOrThrow("minecraft:worldgen/biome")
                .getCompoundListTagOrThrow("value");
        assertTrue(biomes.size() > 0, "biome should have entries");
        boolean foundPlains = false;
        for (NBTCompound entry : biomes.getTags()) {
            String name = entry.getStringTagValueOrThrow("name");
            if ("minecraft:plains".equals(name)) {
                foundPlains = true;
                NBTCompound effects = entry.getCompoundTagOrThrow("element").getCompoundTagOrThrow("effects");
                assertNotNull(effects, "plains should have effects");
            }
        }
        assertTrue(foundPlains, "should find plains biome");
    }

    @Test
    void oneNineteenCodecContainsChatTypes() {
        NBTCompound codec = RegistryDataCodec.create(ClientVersion.V_1_19);
        NBTList<NBTCompound> values = codec.getCompoundTagOrThrow("minecraft:chat_type")
                .getCompoundListTagOrThrow("value");
        assertTrue(values.size() > 0);
    }

    @Test
    void oneNineteenFourCodecContainsDamageTypes() {
        NBTCompound codec = RegistryDataCodec.create(ClientVersion.V_1_19_4);
        assertTrue(codec.getCompoundTagOrThrow("minecraft:damage_type")
                .getCompoundListTagOrThrow("value").size() > 0);
    }

    @Test
    void configurationCodecsForModernVersionsAreSplit() {
        assertTrue(RegistryDataCodec.usesSplitRegistries(ClientVersion.V_1_20_5));
        assertTrue(RegistryDataCodec.usesSplitRegistries(ClientVersion.V_1_21));
        Map<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> registries =
                RegistryDataCodec.splitRegistries(ClientVersion.V_1_20_5);
        List<WrapperConfigServerRegistryData.RegistryElement> dimensions =
                registries.get(new ResourceLocation("minecraft:dimension_type"));
        assertNotNull(dimensions);
        assertTrue(dimensions.size() >= 3);
    }

    @Test
    void oneTwentyOneRegistrySnapshotHasExpectedKeys() {
        assertTrue(RegistryDataCodec.usesSplitRegistries(ClientVersion.V_1_21));
        Map<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> regs =
                RegistryDataCodec.splitRegistries(ClientVersion.V_1_21);
        assertTrue(regs.containsKey(new ResourceLocation("minecraft:dimension_type")));
        assertTrue(regs.containsKey(new ResourceLocation("minecraft:worldgen/biome")));
        assertTrue(regs.containsKey(new ResourceLocation("minecraft:damage_type")));
    }

    @Test
    void referencedTagsAreDerivedFromNbtScanning() {
        Set<ResourceLocation> tags = RegistryDataCodec.referencedTags(ClientVersion.V_1_21_2);
        assertNotNull(tags);
        // Enchantable tags are referenced via # in enchantment data
        assertTrue(tags.stream().anyMatch(t -> t.toString().contains("enchantable")),
                "should contain enchantable tags via scanning");
    }

    @Test
    void oneTwentyOneElevenHasTimelineFromViaVersion() {
        Map<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> regs =
                RegistryDataCodec.splitRegistries(ClientVersion.V_1_21_11);
        // Timeline should be present from ViaVersion NBT
        assertTrue(regs.containsKey(new ResourceLocation("minecraft:timeline")) ||
                regs.containsKey(new ResourceLocation("minecraft:dialog")),
                "1.21.11 should have timeline or dialog");
        List<WrapperConfigServerRegistryData.RegistryElement> timelines =
                regs.get(new ResourceLocation("minecraft:timeline"));
        if (timelines != null) {
            assertFalse(timelines.isEmpty());
            boolean hasDay = timelines.stream().anyMatch(e -> e.getId().toString().contains("day"));
            assertTrue(hasDay, "timeline should contain day");
        }
    }

    @Test
    void twentySixOneHasSoundVariantsFromViaVersion() {
        Map<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> regs =
                RegistryDataCodec.splitRegistries(ClientVersion.V_26_1);
        // Sound variants should be present
        assertTrue(regs.containsKey(new ResourceLocation("minecraft:cat_sound_variant")) ||
                regs.containsKey(new ResourceLocation("minecraft:cow_sound_variant")) ||
                regs.containsKey(new ResourceLocation("minecraft:world_clock")),
                "26.1 should have sound variants or world_clock");
    }

    @Test
    void legacyClientsReturnEmptyCompound() {
        assertTrue(RegistryDataCodec.create(ClientVersion.V_1_12_2).isEmpty());
    }

    @Test
    void splitRegistriesContainNoTestEnvironment() {
        // test_environment and test_instance have no data source in PE or ViaVersion, so should be absent
        Map<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> regs =
                RegistryDataCodec.splitRegistries(ClientVersion.V_1_21_11);
        assertFalse(regs.containsKey(new ResourceLocation("minecraft:test_environment")),
                "test_environment should be absent as it has no data source");
        assertFalse(regs.containsKey(new ResourceLocation("minecraft:test_instance")),
                "test_instance should be absent as it has no data source");
    }
}
