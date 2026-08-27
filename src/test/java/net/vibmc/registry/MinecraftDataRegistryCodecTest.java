package net.vibmc.registry;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftDataRegistryCodecTest {
    @Test
    void oneSixteenTwoCodecContainsClassicDimensionsAndBiomes(){
        NBTCompound codec=MinecraftDataRegistryCodec.create(ClientVersion.V_1_16_2);
        NBTCompound dimensions=codec.getCompoundTagOrThrow("minecraft:dimension_type");
        NBTList<NBTCompound> values=dimensions.getCompoundListTagOrThrow("value");
        assertTrue(values.size()>=3);
        NBTCompound overworld=values.getTag(0).getCompoundTagOrThrow("element");
        assertEquals(256,overworld.getNumberTagValueOrThrow("logical_height").intValue());
        NBTList<NBTCompound> biomes=codec.getCompoundTagOrThrow("minecraft:worldgen/biome")
                .getCompoundListTagOrThrow("value");
        assertTrue(biomes.size()>0);
        boolean foundPlains=false;
        for(NBTCompound entry:biomes.getTags()){
            NBTCompound effects=entry.getCompoundTagOrThrow("element").getCompoundTagOrThrow("effects");
            String name=entry.getStringTagValueOrThrow("name");
            assertNotNull(effects.getNumberTagValueOrNull("fog_color"),name);
            assertNotNull(effects.getNumberTagValueOrNull("water_fog_color"),name);
            if("minecraft:plains".equals(name)){
                foundPlains=true;
                assertEquals(0xC0D8FF,effects.getNumberTagValueOrThrow("fog_color").intValue());
                assertEquals(0x050533,effects.getNumberTagValueOrThrow("water_fog_color").intValue());
            }
        }
        assertTrue(foundPlains);

        NBTCompound modernOverworld=MinecraftDataRegistryCodec.create(ClientVersion.V_1_17)
                .getCompoundTagOrThrow("minecraft:dimension_type")
                .getCompoundListTagOrThrow("value").getTag(0).getCompoundTagOrThrow("element");
        assertEquals(0,modernOverworld.getNumberTagValueOrThrow("min_y").intValue());
        assertEquals(256,modernOverworld.getNumberTagValueOrThrow("height").intValue());
    }

    @Test
    void oneNineteenCodecContainsChatTypes(){
        NBTCompound codec=MinecraftDataRegistryCodec.create(ClientVersion.V_1_19);
        NBTList<NBTCompound> values=codec.getCompoundTagOrThrow("minecraft:chat_type")
                .getCompoundListTagOrThrow("value");
        assertTrue(values.size()>0);
        for(NBTCompound entry:values.getTags()){
            NBTCompound narration=entry.getCompoundTagOrThrow("element").getCompoundTagOrNull("narration");
            if(narration!=null)assertNotNull(narration.getStringTagValueOrNull("priority"));
        }
    }

    @Test
    void oneNineteenFourCodecContainsDamageTypes(){
        NBTCompound codec=MinecraftDataRegistryCodec.create(ClientVersion.V_1_19_4);
        assertTrue(codec.getCompoundTagOrThrow("minecraft:damage_type")
                .getCompoundListTagOrThrow("value").size()>0);
    }

    @Test
    void configurationCodecsAreSelectedFromMinecraftDataAndKeepClassicHeight(){
        for(ClientVersion version:new ClientVersion[]{ClientVersion.V_1_20_2,ClientVersion.V_1_20_3}){
            NBTCompound codec=MinecraftDataRegistryCodec.create(version);
            assertFalse(codec.isEmpty(),version.toString());
            NBTList<NBTCompound> dimensions=codec.getCompoundTagOrThrow("minecraft:dimension_type")
                    .getCompoundListTagOrThrow("value");
            assertTrue(dimensions.size()>=3,version.toString());
            for(NBTCompound entry:dimensions.getTags()){
                NBTCompound element=entry.getCompoundTagOrThrow("element");
                assertEquals(256,element.getNumberTagValueOrThrow("logical_height").intValue());
                assertEquals(0,element.getNumberTagValueOrThrow("min_y").intValue());
                assertEquals(256,element.getNumberTagValueOrThrow("height").intValue());
            }
        }
    }

    @Test
    void oneTwentyOneRegistrySnapshotSupportsNbtArrays(){
        assertTrue(MinecraftDataRegistryCodec.usesSplitRegistries(ClientVersion.V_1_21));
        assertTrue(MinecraftDataRegistryCodec.splitRegistries(ClientVersion.V_1_21)
                .containsKey(new com.github.retrooper.packetevents.resources.ResourceLocation("minecraft:dimension_type")));
        assertTrue(MinecraftDataRegistryCodec.referencedTags(ClientVersion.V_1_21_2)
                .contains(new com.github.retrooper.packetevents.resources.ResourceLocation("minecraft:enchantable/head_armor")));

        com.github.retrooper.packetevents.protocol.nbt.NBTIntArray ints=
                (com.github.retrooper.packetevents.protocol.nbt.NBTIntArray)MinecraftDataNbt.decode(
                        com.google.gson.JsonParser.parseString("{\"type\":\"intArray\",\"value\":[0,1,-2]}"));
        assertArrayEquals(new int[]{0,1,-2},ints.getValue());
    }

    @Test
    void oneTwentyFiveUsesSplitRegistriesFromMinecraftData(){
        assertTrue(MinecraftDataRegistryCodec.usesSplitRegistries(ClientVersion.V_1_20_5));
        java.util.Map<com.github.retrooper.packetevents.resources.ResourceLocation,
                java.util.List<com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData.RegistryElement>> registries=
                MinecraftDataRegistryCodec.splitRegistries(ClientVersion.V_1_20_5);
        java.util.List<com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData.RegistryElement> dimensions=
                registries.get(new com.github.retrooper.packetevents.resources.ResourceLocation("minecraft:dimension_type"));
        assertNotNull(dimensions);assertTrue(dimensions.size()>=3);
        for(com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData.RegistryElement entry:dimensions){
            NBTCompound element=(NBTCompound)entry.getData();
            assertEquals(0,element.getNumberTagValueOrThrow("min_y").intValue());
            assertEquals(256,element.getNumberTagValueOrThrow("height").intValue());
            assertEquals(256,element.getNumberTagValueOrThrow("logical_height").intValue());
        }
        assertTrue(registries.containsKey(new com.github.retrooper.packetevents.resources.ResourceLocation("minecraft:worldgen/biome")));
        assertTrue(registries.containsKey(new com.github.retrooper.packetevents.resources.ResourceLocation("minecraft:damage_type")));
    }

    @Test
    void oneTwentyOneFiveNormalizesPacketEventsAndFallbackRegistrySchemas(){
        java.util.Map<com.github.retrooper.packetevents.resources.ResourceLocation,
                java.util.List<com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData.RegistryElement>> registries=
                MinecraftDataRegistryCodec.splitRegistries(ClientVersion.V_1_21_5);
        com.github.retrooper.packetevents.resources.ResourceLocation biomeKey=
                new com.github.retrooper.packetevents.resources.ResourceLocation("minecraft:worldgen/biome");
        NBTCompound badlands=null;
        for(com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData.RegistryElement entry:registries.get(biomeKey))
            if("minecraft:badlands".equals(entry.getId().toString()))badlands=(NBTCompound)entry.getData();
        assertNotNull(badlands);
        assertTrue(badlands.getCompoundTagOrThrow("effects").getTagOrNull("music")
                instanceof com.github.retrooper.packetevents.protocol.nbt.NBTList);
        java.util.List<com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData.RegistryElement> wolves=
                registries.get(new com.github.retrooper.packetevents.resources.ResourceLocation("minecraft:wolf_variant"));
        assertNotNull(wolves);assertFalse(wolves.isEmpty());
        NBTCompound wolf=(NBTCompound)wolves.get(0).getData();
        assertNotNull(wolf.getCompoundTagOrNull("assets"));
        java.util.List<com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData.RegistryElement> cows=
                registries.get(new com.github.retrooper.packetevents.resources.ResourceLocation("minecraft:cow_variant"));
        assertNotNull(cows);assertFalse(cows.isEmpty());
        java.util.List<com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData.RegistryElement> wolfSounds=
                registries.get(new com.github.retrooper.packetevents.resources.ResourceLocation("minecraft:wolf_sound_variant"));
        assertNotNull(wolfSounds);assertFalse(wolfSounds.isEmpty());
    }

    @Test
    void oneTwentyOneNineUsesRenamedItemDamageEnchantmentEffect(){
        java.util.List<com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData.RegistryElement> enchantments=
                MinecraftDataRegistryCodec.splitRegistries(ClientVersion.V_1_21_9).get(
                        new com.github.retrooper.packetevents.resources.ResourceLocation("minecraft:enchantment"));
        assertNotNull(enchantments);assertFalse(enchantments.isEmpty());
        NBTCompound soulSpeed=null;
        for(com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData.RegistryElement entry:enchantments)
            if("minecraft:soul_speed".equals(entry.getId().toString()))soulSpeed=(NBTCompound)entry.getData();
        assertNotNull(soulSpeed);
        assertFalse(containsString(soulSpeed,"minecraft:damage_item"));
        assertTrue(containsString(soulSpeed,"minecraft:change_item_damage"));

        java.util.List<com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData.RegistryElement> damageTypes=
                MinecraftDataRegistryCodec.splitRegistries(ClientVersion.V_1_21_9).get(
                        new com.github.retrooper.packetevents.resources.ResourceLocation("minecraft:damage_type"));
        assertNotNull(damageTypes);
        boolean enderPearl=false;
        for(com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData.RegistryElement entry:damageTypes)
            if("minecraft:ender_pearl".equals(entry.getId().toString()))enderPearl=true;
        assertTrue(enderPearl,"1.21.9 requires minecraft:ender_pearl damage type");

        java.util.List<com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData.RegistryElement> biomes=
                MinecraftDataRegistryCodec.splitRegistries(ClientVersion.V_1_21_9).get(
                        new com.github.retrooper.packetevents.resources.ResourceLocation("minecraft:worldgen/biome"));
        NBTCompound plains=null;
        for(com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData.RegistryElement entry:biomes)
            if("minecraft:plains".equals(entry.getId().toString()))plains=(NBTCompound)entry.getData();
        assertNotNull(plains);
        assertEquals(0xC0D8FF,plains.getCompoundTagOrThrow("effects")
                .getNumberTagValueOrThrow("fog_color").intValue());
    }

    private static boolean containsString(com.github.retrooper.packetevents.protocol.nbt.NBT tag,String value){
        if(tag instanceof com.github.retrooper.packetevents.protocol.nbt.NBTString)
            return value.equals(((com.github.retrooper.packetevents.protocol.nbt.NBTString)tag).getValue());
        if(tag instanceof NBTCompound){
            for(com.github.retrooper.packetevents.protocol.nbt.NBT child:((NBTCompound)tag).getTags().values())
                if(containsString(child,value))return true;
        }else if(tag instanceof com.github.retrooper.packetevents.protocol.nbt.NBTList){
            for(Object child:((com.github.retrooper.packetevents.protocol.nbt.NBTList<?>)tag).getTags())
                if(containsString((com.github.retrooper.packetevents.protocol.nbt.NBT)child,value))return true;
        }
        return false;
    }

    @Test
    void twentySixOneVariantsRetainMinecraftDataBabyAssets(){
        java.util.Map<com.github.retrooper.packetevents.resources.ResourceLocation,
                java.util.List<com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData.RegistryElement>> registries=
                MinecraftDataRegistryCodec.splitRegistries(ClientVersion.V_26_1);
        for(String registry:new String[]{"cat_variant","chicken_variant","cow_variant","pig_variant"}){
            java.util.List<com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData.RegistryElement> entries=
                    registries.get(new com.github.retrooper.packetevents.resources.ResourceLocation("minecraft:"+registry));
            assertNotNull(entries,registry);assertFalse(entries.isEmpty(),registry);
            for(com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData.RegistryElement entry:entries){
                NBTCompound value=(NBTCompound)entry.getData();
                assertNotNull(value.getStringTagValueOrNull("asset_id"),registry+" "+entry.getId());
                assertNotNull(value.getStringTagValueOrNull("baby_asset_id"),registry+" "+entry.getId());
            }
        }
    }

    @Test
    void legacyVersionsDoNotReceiveModernCodec(){
        assertTrue(MinecraftDataRegistryCodec.create(ClientVersion.V_1_12_2).isEmpty());
    }
}
