package net.vibmc.network.packetevents;

import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTags;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/** Vanilla tags sourced from PacketEvents where available, plus required dynamic placeholders. */
public final class PacketEventsTags {
    private PacketEventsTags() {}

    public static WrapperPlayServerTags create(ClientVersion version) {
        if (version.isOlderThan(ClientVersion.V_1_13))
            throw new IllegalArgumentException("Registry tags do not exist before Minecraft 1.13");
        return new WrapperPlayServerTags(tagMap(version,Collections.emptySet()));
    }

    public static Map<ResourceLocation,List<WrapperPlayServerTags.Tag>> tagMap(
            ClientVersion version,Set<ResourceLocation> referencedTags){
        return tagMap(version,referencedTags,Collections.emptyMap());
    }

    public static Map<ResourceLocation,List<WrapperPlayServerTags.Tag>> tagMap(
            ClientVersion version,Set<ResourceLocation> referencedTags,
            Map<ResourceLocation,Set<ResourceLocation>> tagsByRegistry){
        Map<ResourceLocation,List<WrapperPlayServerTags.Tag>> registries=new LinkedHashMap<>();
        registries.put(ResourceLocation.minecraft("block"),new ArrayList<>());
        registries.put(ResourceLocation.minecraft("item"),new ArrayList<>());
        registries.put(ResourceLocation.minecraft("fluid"),new ArrayList<>(Arrays.asList(
                new WrapperPlayServerTags.Tag("minecraft:water",Arrays.asList(1,2)),
                new WrapperPlayServerTags.Tag("minecraft:lava",Arrays.asList(3,4)))));
        registries.put(ResourceLocation.minecraft("entity_type"),new ArrayList<>());
        registries.put(ResourceLocation.minecraft("game_event"),new ArrayList<>());

        if(version.isNewerThanOrEquals(ClientVersion.V_1_21_2)){
            addPacketEventsBlockTags(registries.get(ResourceLocation.minecraft("block")),version);
            addPacketEventsItemTags(registries.get(ResourceLocation.minecraft("item")),version);
            // minecraft-data registry entries identify references with '#', but do not ship the
            // corresponding vanilla tag files. Defining unresolved names in every registry that
            // can be referenced lets the client bind the holder sets; PE-provided item/block tags
            // retain their real memberships.
            for(String registry:Arrays.asList("block","item","fluid","entity_type","enchantment","damage_type")){
                List<WrapperPlayServerTags.Tag> values=registries.computeIfAbsent(
                        ResourceLocation.minecraft(registry),ignored->new ArrayList<>());
                for(ResourceLocation tag:referencedTags)addIfAbsent(values,tag,Collections.emptyList());
            }
            // Registry-local references, such as dialog and timeline tags, must be bound in
            // their own dynamic registry rather than only in the common static registries.
            for(Map.Entry<ResourceLocation,Set<ResourceLocation>> registry:tagsByRegistry.entrySet()){
                List<WrapperPlayServerTags.Tag> values=registries.computeIfAbsent(
                        registry.getKey(),ignored->new ArrayList<>());
                for(ResourceLocation tag:registry.getValue())addIfAbsent(values,tag,Collections.emptyList());
            }
            if(version.isNewerThanOrEquals(ClientVersion.V_1_21_11)){
                List<WrapperPlayServerTags.Tag> dialogs=registries.computeIfAbsent(
                        new ResourceLocation("minecraft:dialog"),ignored->new ArrayList<>());
                addIfAbsent(dialogs,new ResourceLocation("minecraft:pause_screen_additions"),Collections.emptyList());
                addIfAbsent(dialogs,new ResourceLocation("minecraft:quick_actions"),Collections.emptyList());
                List<WrapperPlayServerTags.Tag> timelines=registries.computeIfAbsent(
                        new ResourceLocation("minecraft:timeline"),ignored->new ArrayList<>());
                addIfAbsent(timelines,new ResourceLocation("minecraft:in_overworld"),Collections.emptyList());
                addIfAbsent(timelines,new ResourceLocation("minecraft:in_nether"),Collections.emptyList());
                addIfAbsent(timelines,new ResourceLocation("minecraft:in_end"),Collections.emptyList());
            }
            if(version.isNewerThanOrEquals(ClientVersion.V_26_1)){
                List<WrapperPlayServerTags.Tag> damageTypes=registries.computeIfAbsent(
                        new ResourceLocation("minecraft:damage_type"),ignored->new ArrayList<>());
                // Required by 26.1's fire-resistant item component initializer. Neither PE nor
                // the vendored registry snapshot exposes damage-type tag membership.
                addIfAbsent(damageTypes,new ResourceLocation("minecraft:is_fire"),Collections.emptyList());
                addIfAbsent(damageTypes,new ResourceLocation("minecraft:is_explosion"),Collections.emptyList());
                addIfAbsent(damageTypes,new ResourceLocation("minecraft:bypasses_shield"),Collections.emptyList());
                List<WrapperPlayServerTags.Tag> bannerPatterns=registries.computeIfAbsent(
                        new ResourceLocation("minecraft:banner_pattern"),ignored->new ArrayList<>());
                addBannerPatternTag(bannerPatterns,version,"no_item_required",
                        "square_bottom_left","square_bottom_right","square_top_left","square_top_right",
                        "stripe_bottom","stripe_top","stripe_left","stripe_right","stripe_center",
                        "stripe_middle","stripe_downright","stripe_downleft","small_stripes","cross",
                        "straight_cross","triangle_bottom","triangle_top","triangles_bottom","triangles_top",
                        "diagonal_left","diagonal_up_right","diagonal_up_left","diagonal_right","circle",
                        "rhombus","half_vertical","half_horizontal","half_vertical_right",
                        "half_horizontal_bottom","border","gradient","gradient_up");
                addBannerPatternTag(bannerPatterns,version,"pattern_item/bordure_indented","curly_border");
                addBannerPatternTag(bannerPatterns,version,"pattern_item/creeper","creeper");
                addBannerPatternTag(bannerPatterns,version,"pattern_item/field_masoned","bricks");
                addBannerPatternTag(bannerPatterns,version,"pattern_item/flow","flow");
                addBannerPatternTag(bannerPatterns,version,"pattern_item/flower","flower");
                addBannerPatternTag(bannerPatterns,version,"pattern_item/globe","globe");
                addBannerPatternTag(bannerPatterns,version,"pattern_item/guster","guster");
                addBannerPatternTag(bannerPatterns,version,"pattern_item/mojang","mojang");
                addBannerPatternTag(bannerPatterns,version,"pattern_item/piglin","piglin");
                addBannerPatternTag(bannerPatterns,version,"pattern_item/skull","skull");
            }
        }
        Map<ResourceLocation,List<WrapperPlayServerTags.Tag>> result=new LinkedHashMap<>();
        for(Map.Entry<ResourceLocation,List<WrapperPlayServerTags.Tag>> entry:registries.entrySet())
            result.put(entry.getKey(),Collections.unmodifiableList(entry.getValue()));
        return Collections.unmodifiableMap(result);
    }

    private static void addPacketEventsItemTags(List<WrapperPlayServerTags.Tag> output,ClientVersion version){
        Set<String> seen=new HashSet<>();
        for(Field field:com.github.retrooper.packetevents.protocol.world.states.defaulttags.ItemTags.class.getFields()){
            if(!Modifier.isStatic(field.getModifiers())||field.getType()!=
                    com.github.retrooper.packetevents.protocol.world.states.defaulttags.ItemTags.class)continue;
            try{
                com.github.retrooper.packetevents.protocol.world.states.defaulttags.ItemTags tag=
                        (com.github.retrooper.packetevents.protocol.world.states.defaulttags.ItemTags)field.get(null);
                if(tag==null||!seen.add(tag.getName()))continue;
                List<Integer> ids=new ArrayList<>();
                for(ItemType item:tag.getStates()){int id=item.getId(version);if(id>=0)ids.add(id);}
                output.add(new WrapperPlayServerTags.Tag(tag.getName(),ids));
            }catch(IllegalAccessException error){throw new IllegalStateException("Could not read PacketEvents item tags",error);}
        }
    }

    private static void addPacketEventsBlockTags(List<WrapperPlayServerTags.Tag> output,ClientVersion version){
        Set<String> seen=new HashSet<>();
        for(Field field:com.github.retrooper.packetevents.protocol.world.states.defaulttags.BlockTags.class.getFields()){
            if(!Modifier.isStatic(field.getModifiers())||field.getType()!=
                    com.github.retrooper.packetevents.protocol.world.states.defaulttags.BlockTags.class)continue;
            try{
                com.github.retrooper.packetevents.protocol.world.states.defaulttags.BlockTags tag=
                        (com.github.retrooper.packetevents.protocol.world.states.defaulttags.BlockTags)field.get(null);
                if(tag==null||!seen.add(tag.getName()))continue;
                List<Integer> ids=new ArrayList<>();
                for(StateType block:tag.getStates()){int id=block.getMapped().getId(version);if(id>=0)ids.add(id);}
                output.add(new WrapperPlayServerTags.Tag(tag.getName(),ids));
            }catch(IllegalAccessException error){throw new IllegalStateException("Could not read PacketEvents block tags",error);}
        }
    }

    private static void addBannerPatternTag(List<WrapperPlayServerTags.Tag> tags,ClientVersion version,
                                            String tagName,String... patternNames){
        List<Integer> ids=new ArrayList<>();
        for(String patternName:patternNames){
            com.github.retrooper.packetevents.protocol.item.banner.BannerPattern pattern=
                    com.github.retrooper.packetevents.protocol.item.banner.BannerPatterns.getRegistry().getByName(
                            version,new ResourceLocation("minecraft:"+patternName));
            if(pattern!=null){int id=pattern.getId(version);if(id>=0)ids.add(id);}
        }
        ResourceLocation key=new ResourceLocation("minecraft:"+tagName);
        for(int i=0;i<tags.size();i++)if(tags.get(i).getKey().equals(key)){
            tags.set(i,new WrapperPlayServerTags.Tag(key,ids));return;
        }
        tags.add(new WrapperPlayServerTags.Tag(key,ids));
    }

    private static void addIfAbsent(List<WrapperPlayServerTags.Tag> tags,ResourceLocation name,List<Integer> ids){
        for(WrapperPlayServerTags.Tag existing:tags)if(existing.getKey().equals(name))return;
        tags.add(new WrapperPlayServerTags.Tag(name,ids));
    }
}
