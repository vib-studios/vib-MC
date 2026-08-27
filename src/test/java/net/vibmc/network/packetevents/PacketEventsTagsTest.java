package net.vibmc.network.packetevents;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTags;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PacketEventsTagsTest {
    @Test
    void postFlatteningClientsReceiveVanillaFluidMembership() {
        for(ClientVersion version:new ClientVersion[]{ClientVersion.V_1_13,ClientVersion.V_1_16_2,
                ClientVersion.V_1_17,ClientVersion.V_1_20}){
            Map<ResourceLocation,List<WrapperPlayServerTags.Tag>> registries=
                    PacketEventsTags.create(version).getTagMap();
            List<WrapperPlayServerTags.Tag> fluids=registries.get(ResourceLocation.minecraft("fluid"));
            assertNotNull(fluids,version.toString());
            assertEquals(2,fluids.size(),version.toString());
            assertEquals("minecraft:water",fluids.get(0).getName());
            assertEquals(java.util.Arrays.asList(1,2),fluids.get(0).getValues());
            assertEquals("minecraft:lava",fluids.get(1).getName());
            assertEquals(java.util.Arrays.asList(3,4),fluids.get(1).getValues());
        }
    }

    @Test
    void oneTwentyTwoDefinesTagsRequiredByDynamicEnchantments(){
        java.util.Set<ResourceLocation> referenced=new java.util.LinkedHashSet<>(java.util.Arrays.asList(
                new ResourceLocation("minecraft:enchantable/head_armor"),
                new ResourceLocation("minecraft:sensitive_to_bane_of_arthropods"),
                new ResourceLocation("minecraft:exclusive_set/armor")));
        Map<ResourceLocation,List<WrapperPlayServerTags.Tag>> registries=
                PacketEventsTags.tagMap(ClientVersion.V_1_21_2,referenced);
        WrapperPlayServerTags.Tag head=find(registries.get(ResourceLocation.minecraft("item")),
                "minecraft:enchantable/head_armor");
        assertNotNull(head);assertFalse(head.getValues().isEmpty());
        assertNotNull(find(registries.get(ResourceLocation.minecraft("entity_type")),
                "minecraft:sensitive_to_bane_of_arthropods"));
        assertNotNull(find(registries.get(ResourceLocation.minecraft("enchantment")),
                "minecraft:exclusive_set/armor"));
    }

    @Test
    void oneTwentyOneElevenBindsDynamicRegistryLocalTags(){
        ClientVersion version=ClientVersion.V_1_21_11;
        Map<ResourceLocation,List<WrapperPlayServerTags.Tag>> registries=PacketEventsTags.tagMap(
                version,net.vibmc.registry.MinecraftDataRegistryCodec.referencedTags(version),
                net.vibmc.registry.MinecraftDataRegistryCodec.referencedTagsByRegistry(version));
        assertNotNull(find(registries.get(new ResourceLocation("minecraft:dialog")),
                "minecraft:pause_screen_additions"));
        assertNotNull(find(registries.get(new ResourceLocation("minecraft:dialog")),
                "minecraft:quick_actions"));
        assertNotNull(find(registries.get(new ResourceLocation("minecraft:timeline")),
                "minecraft:in_overworld"));
        assertNotNull(find(registries.get(new ResourceLocation("minecraft:timeline")),
                "minecraft:in_nether"));
        assertNotNull(find(registries.get(new ResourceLocation("minecraft:timeline")),
                "minecraft:in_end"));
    }

    @Test
    void twentySixOneBindsRequiredFireDamageTypeTag(){
        ClientVersion version=ClientVersion.V_26_1;
        Map<ResourceLocation,List<WrapperPlayServerTags.Tag>> registries=PacketEventsTags.tagMap(
                version,net.vibmc.registry.MinecraftDataRegistryCodec.referencedTags(version),
                net.vibmc.registry.MinecraftDataRegistryCodec.referencedTagsByRegistry(version));
        assertNotNull(find(registries.get(new ResourceLocation("minecraft:damage_type")),
                "minecraft:is_fire"));
        assertNotNull(find(registries.get(new ResourceLocation("minecraft:damage_type")),
                "minecraft:is_explosion"));
        assertNotNull(find(registries.get(new ResourceLocation("minecraft:damage_type")),
                "minecraft:bypasses_shield"));
        List<WrapperPlayServerTags.Tag> bannerPatterns=registries.get(
                new ResourceLocation("minecraft:banner_pattern"));
        for(String tag:new String[]{"no_item_required","pattern_item/bordure_indented",
                "pattern_item/creeper","pattern_item/field_masoned","pattern_item/flow",
                "pattern_item/flower","pattern_item/globe","pattern_item/guster",
                "pattern_item/mojang","pattern_item/piglin","pattern_item/skull"}){
            WrapperPlayServerTags.Tag value=find(bannerPatterns,"minecraft:"+tag);
            assertNotNull(value,tag);assertFalse(value.getValues().isEmpty(),tag);
        }
    }

    private static WrapperPlayServerTags.Tag find(List<WrapperPlayServerTags.Tag> tags,String name){
        if(tags!=null)for(WrapperPlayServerTags.Tag tag:tags)if(name.equals(tag.getName()))return tag;
        return null;
    }

    @Test
    void legacyClientsDoNotHaveATagsPacket() {
        assertThrows(IllegalArgumentException.class,
                ()->PacketEventsTags.create(ClientVersion.V_1_12_2));
    }
}
