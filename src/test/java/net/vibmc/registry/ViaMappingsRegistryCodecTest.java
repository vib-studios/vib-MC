package net.vibmc.registry;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ViaMappingsRegistryCodecTest {
    @Test
    void oneSixteenTwoCodecContainsClassicDimensionsAndBiomes() {
        NBTCompound codec = ViaMappingsRegistryCodec.create(ClientVersion.V_1_16_2);
        NBTCompound dimensions = codec.getCompoundTagOrThrow("minecraft:dimension_type");
        NBTList<NBTCompound> values = dimensions.getCompoundListTagOrThrow("value");
        assertTrue(values.size() >= 3);
        NBTCompound overworld = values.getTag(0).getCompoundTagOrThrow("element");
        assertEquals(256, overworld.getNumberTagValueOrThrow("logical_height").intValue());

        NBTList<NBTCompound> biomes = codec.getCompoundTagOrThrow("minecraft:worldgen/biome")
                .getCompoundListTagOrThrow("value");
        assertTrue(biomes.size() > 0);
        boolean foundPlains = false;
        for (NBTCompound entry : biomes.getTags()) {
            NBTCompound effects = entry.getCompoundTagOrThrow("element").getCompoundTagOrThrow("effects");
            String name = entry.getStringTagValueOrThrow("name");
            assertNotNull(effects.getNumberTagValueOrNull("fog_color"), name);
            assertNotNull(effects.getNumberTagValueOrNull("water_fog_color"), name);
            if ("minecraft:plains".equals(name)) {
                foundPlains = true;
                assertEquals(0xC0D8FF, effects.getNumberTagValueOrThrow("fog_color").intValue());
                assertEquals(0x050533, effects.getNumberTagValueOrThrow("water_fog_color").intValue());
            }
        }
        assertTrue(foundPlains);

        NBTCompound modernOverworld = ViaMappingsRegistryCodec.create(ClientVersion.V_1_17)
                .getCompoundTagOrThrow("minecraft:dimension_type")
                .getCompoundListTagOrThrow("value").getTag(0).getCompoundTagOrThrow("element");
        assertEquals(0, modernOverworld.getNumberTagValueOrThrow("min_y").intValue());
        assertEquals(256, modernOverworld.getNumberTagValueOrThrow("height").intValue());
    }

    @Test
    void oneNineteenCodecContainsChatTypes() {
        NBTCompound codec = ViaMappingsRegistryCodec.create(ClientVersion.V_1_19);
        NBTList<NBTCompound> values = codec.getCompoundTagOrThrow("minecraft:chat_type")
                .getCompoundListTagOrThrow("value");
        assertTrue(values.size() > 0);
        for (NBTCompound entry : values.getTags()) {
            NBTCompound narration = entry.getCompoundTagOrThrow("element").getCompoundTagOrNull("narration");
            if (narration != null) assertNotNull(narration.getStringTagValueOrNull("priority"));
        }
    }

    @Test
    void oneNineteenFourCodecContainsDamageTypes() {
        NBTCompound codec = ViaMappingsRegistryCodec.create(ClientVersion.V_1_19_4);
        assertTrue(codec.getCompoundTagOrThrow("minecraft:damage_type")
                .getCompoundListTagOrThrow("value").size() > 0);
    }

    @Test
    void configurationCodecsKeepClassicHeight() {
        for (ClientVersion version : new ClientVersion[]{ClientVersion.V_1_20_2, ClientVersion.V_1_20_3}) {
            NBTCompound codec = ViaMappingsRegistryCodec.create(version);
            assertFalse(codec.isEmpty(), version.toString());
            NBTList<NBTCompound> dimensions = codec.getCompoundTagOrThrow("minecraft:dimension_type")
                    .getCompoundListTagOrThrow("value");
            assertTrue(dimensions.size() >= 3, version.toString());
            for (NBTCompound entry : dimensions.getTags()) {
                NBTCompound element = entry.getCompoundTagOrThrow("element");
                assertEquals(256, element.getNumberTagValueOrThrow("logical_height").intValue());
                assertEquals(0, element.getNumberTagValueOrThrow("min_y").intValue());
                assertEquals(256, element.getNumberTagValueOrThrow("height").intValue());
            }
        }
    }

    @Test
    void oneTwentyOneRegistrySupportsSplitRegistries() {
        assertTrue(ViaMappingsRegistryCodec.usesSplitRegistries(ClientVersion.V_1_21));
        assertTrue(ViaMappingsRegistryCodec.splitRegistries(ClientVersion.V_1_21)
                .containsKey(new ResourceLocation("minecraft:dimension_type")));
        assertTrue(ViaMappingsRegistryCodec.referencedTags(ClientVersion.V_1_21_2)
                .contains(new ResourceLocation("minecraft:enchantable/head_armor")));
    }

    @Test
    void oneTwentyFiveUsesSplitRegistriesFromViaMappings() {
        assertTrue(ViaMappingsRegistryCodec.usesSplitRegistries(ClientVersion.V_1_20_5));
        Map<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> registries =
                ViaMappingsRegistryCodec.splitRegistries(ClientVersion.V_1_20_5);
        List<WrapperConfigServerRegistryData.RegistryElement> dimensions =
                registries.get(new ResourceLocation("minecraft:dimension_type"));
        assertNotNull(dimensions);
        assertTrue(dimensions.size() >= 3);
        for (WrapperConfigServerRegistryData.RegistryElement entry : dimensions) {
            NBTCompound element = (NBTCompound) entry.getData();
            assertEquals(0, element.getNumberTagValueOrThrow("min_y").intValue());
            assertEquals(256, element.getNumberTagValueOrThrow("height").intValue());
            assertEquals(256, element.getNumberTagValueOrThrow("logical_height").intValue());
        }
        assertTrue(registries.containsKey(new ResourceLocation("minecraft:worldgen/biome")));
        assertTrue(registries.containsKey(new ResourceLocation("minecraft:damage_type")));
    }

    @Test
    void oneTwentyOneFiveNormalizesPacketEventsAndFallbackRegistrySchemas() {
        Map<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> registries =
                ViaMappingsRegistryCodec.splitRegistries(ClientVersion.V_1_21_5);
        ResourceLocation biomeKey = new ResourceLocation("minecraft:worldgen/biome");
        NBTCompound badlands = null;
        for (WrapperConfigServerRegistryData.RegistryElement entry : registries.get(biomeKey)) {
            if ("minecraft:badlands".equals(entry.getId().toString())) badlands = (NBTCompound) entry.getData();
        }
        assertNotNull(badlands);
        assertTrue(badlands.getCompoundTagOrThrow("effects").getTagOrNull("music")
                instanceof NBTList);
        List<WrapperConfigServerRegistryData.RegistryElement> wolves =
                registries.get(new ResourceLocation("minecraft:wolf_variant"));
        assertNotNull(wolves);
        assertFalse(wolves.isEmpty());
        NBTCompound wolf = (NBTCompound) wolves.get(0).getData();
        assertNotNull(wolf.getCompoundTagOrNull("assets"));
    }

    @Test
    void oneTwentyOneNineUsesRenamedItemDamageEnchantmentEffect() {
        List<WrapperConfigServerRegistryData.RegistryElement> enchantments =
                ViaMappingsRegistryCodec.splitRegistries(ClientVersion.V_1_21_9).get(
                        new ResourceLocation("minecraft:enchantment"));
        assertNotNull(enchantments);
        assertFalse(enchantments.isEmpty());
        NBTCompound soulSpeed = null;
        for (WrapperConfigServerRegistryData.RegistryElement entry : enchantments) {
            if ("minecraft:soul_speed".equals(entry.getId().toString())) soulSpeed = (NBTCompound) entry.getData();
        }
        assertNotNull(soulSpeed);
        assertFalse(containsString(soulSpeed, "minecraft:damage_item"));
        assertTrue(containsString(soulSpeed, "minecraft:change_item_damage"));

        List<WrapperConfigServerRegistryData.RegistryElement> damageTypes =
                ViaMappingsRegistryCodec.splitRegistries(ClientVersion.V_1_21_9).get(
                        new ResourceLocation("minecraft:damage_type"));
        assertNotNull(damageTypes);
        boolean enderPearl = false;
        for (WrapperConfigServerRegistryData.RegistryElement entry : damageTypes) {
            if ("minecraft:ender_pearl".equals(entry.getId().toString())) enderPearl = true;
        }
        assertTrue(enderPearl, "1.21.9 requires minecraft:ender_pearl damage type");
    }

    private static boolean containsString(com.github.retrooper.packetevents.protocol.nbt.NBT tag, String value) {
        if (tag instanceof com.github.retrooper.packetevents.protocol.nbt.NBTString) {
            return value.equals(((com.github.retrooper.packetevents.protocol.nbt.NBTString) tag).getValue());
        }
        if (tag instanceof NBTCompound) {
            for (com.github.retrooper.packetevents.protocol.nbt.NBT child : ((NBTCompound) tag).getTags().values()) {
                if (containsString(child, value)) return true;
            }
        } else if (tag instanceof NBTList) {
            for (Object child : ((NBTList<?>) tag).getTags()) {
                if (containsString((com.github.retrooper.packetevents.protocol.nbt.NBT) child, value)) return true;
            }
        }
        return false;
    }

    @Test
    void legacyVersionsDoNotReceiveModernCodec() {
        assertTrue(ViaMappingsRegistryCodec.create(ClientVersion.V_1_12_2).isEmpty());
    }
}
