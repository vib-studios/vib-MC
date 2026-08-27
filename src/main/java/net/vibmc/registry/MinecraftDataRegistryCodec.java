package net.vibmc.registry;

import com.github.retrooper.packetevents.protocol.nbt.NBT;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Version-selected Configuration registry data loaded from minecraft-data. */
public final class MinecraftDataRegistryCodec {
    private static final Map<String,RegistryData> CACHE=new ConcurrentHashMap<>();
    private MinecraftDataRegistryCodec(){}

    /** Legacy single-compound registry payload used through the 1.20.3 protocol family. */
    public static NBTCompound create(ClientVersion version){
        RegistryData data=data(version);
        if(data.legacy==null)throw new IllegalStateException(
                "minecraft-data "+data.release+" uses split Configuration registries");
        return data.legacy.copy();
    }

    /** True when minecraft-data represents Registry Data as one packet per registry. */
    public static boolean usesSplitRegistries(ClientVersion version){return data(version).legacy==null;}

    public static java.util.Set<ResourceLocation> referencedTags(ClientVersion version){
        return data(version).referencedTags;
    }

    public static Map<ResourceLocation,java.util.Set<ResourceLocation>> referencedTagsByRegistry(
            ClientVersion version){return data(version).referencedTagsByRegistry;}

    public static Map<ResourceLocation,List<WrapperConfigServerRegistryData.RegistryElement>>
    splitRegistries(ClientVersion version){
        RegistryData data=data(version);
        if(data.legacy!=null)throw new IllegalStateException(
                "minecraft-data "+data.release+" uses a compound registry codec");
        Map<ResourceLocation,List<WrapperConfigServerRegistryData.RegistryElement>> copy=new LinkedHashMap<>();
        for(Map.Entry<ResourceLocation,List<WrapperConfigServerRegistryData.RegistryElement>> registry:data.split.entrySet()){
            List<WrapperConfigServerRegistryData.RegistryElement> entries=new ArrayList<>();
            for(WrapperConfigServerRegistryData.RegistryElement element:registry.getValue()){
                NBT value=element.getData();
                entries.add(new WrapperConfigServerRegistryData.RegistryElement(
                        element.getId(),value==null?null:value.copy()));
            }
            copy.put(registry.getKey(),Collections.unmodifiableList(entries));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static RegistryData data(ClientVersion version){
        if(version.isOlderThan(ClientVersion.V_1_16_2))
            return new RegistryData(version.getReleaseName(),new NBTCompound(),null,
                    Collections.emptySet(),Collections.emptyMap());
        MinecraftDataRegistry.VersionData selected=MinecraftDataRegistry.get().forClient(version);
        return CACHE.computeIfAbsent(selected.release(),ignored->load(selected,version));
    }

    private static RegistryData load(MinecraftDataRegistry.VersionData data,ClientVersion version){
        if(!data.has("loginPacket"))throw new IllegalArgumentException(
                "minecraft-data "+data.release()+" has no loginPacket registry data");
        JsonObject login=data.data("loginPacket").getAsJsonObject();
        JsonObject codec=login.getAsJsonObject("dimensionCodec");
        if(codec==null)throw new IllegalArgumentException(
                "minecraft-data "+data.release()+" has no dimensionCodec");
        java.util.Set<ResourceLocation> referencedTags=new java.util.LinkedHashSet<>();
        Map<ResourceLocation,java.util.Set<ResourceLocation>> tagsByRegistry=new LinkedHashMap<>();
        collectTagReferences(codec,referencedTags);
        RegistryData loaded;
        if(codec.has("type")){
            NBTCompound legacy=(NBTCompound)MinecraftDataNbt.decode(codec);
            forceClassicHeight(legacy);
            loaded=new RegistryData(data.release(),legacy,null,Collections.unmodifiableSet(referencedTags),
                    Collections.emptyMap());
        }else{
            Map<ResourceLocation,List<WrapperConfigServerRegistryData.RegistryElement>> registries=
                    new LinkedHashMap<>();
            for(Map.Entry<String,JsonElement> mapped:codec.entrySet()){
                JsonObject registry=mapped.getValue().getAsJsonObject();
                ResourceLocation key=new ResourceLocation(registry.get("id").getAsString());
                java.util.Set<ResourceLocation> registryTags=new java.util.LinkedHashSet<>();
                collectTagReferences(registry,registryTags);
                if(!registryTags.isEmpty())tagsByRegistry.put(key,Collections.unmodifiableSet(registryTags));
                List<WrapperConfigServerRegistryData.RegistryElement> entries=new ArrayList<>();
                for(JsonElement rawEntry:registry.getAsJsonArray("entries")){
                    JsonObject entry=rawEntry.getAsJsonObject();
                    NBT value=entry.has("value")&&!entry.get("value").isJsonNull()
                            ?MinecraftDataNbt.decode(entry.get("value")):null;
                    if("minecraft:dimension_type".equals(key.toString())&&value instanceof NBTCompound)
                        forceClassicDimension((NBTCompound)value);
                    entries.add(new WrapperConfigServerRegistryData.RegistryElement(
                            new ResourceLocation(entry.get("key").getAsString()),value));
                }
                registries.put(key,Collections.unmodifiableList(entries));
            }
            if(version.isNewerThanOrEquals(ClientVersion.V_1_21_5))
                overlayPacketEventsRegistries(registries,version);
            for(Map.Entry<ResourceLocation,List<WrapperConfigServerRegistryData.RegistryElement>> registry:registries.entrySet())
                for(WrapperConfigServerRegistryData.RegistryElement entry:registry.getValue())
                    normalizePacketEventsEntry(registry.getKey().toString(),entry.getId(),entry.getData(),version);
            loaded=new RegistryData(data.release(),null,Collections.unmodifiableMap(registries),
                    Collections.unmodifiableSet(referencedTags),Collections.unmodifiableMap(tagsByRegistry));
        }
        data.discard("loginPacket");
        return loaded;
    }

    /** PacketEvents is the preferred source when it exposes a version-aware registry codec. */
    @SuppressWarnings("deprecation") // PE exposes these registry NBT encoders as compatibility APIs.
    private static void overlayPacketEventsRegistries(
            Map<ResourceLocation,List<WrapperConfigServerRegistryData.RegistryElement>> registries,
            ClientVersion version){
        putPacketEventsRegistry(registries,"minecraft:worldgen/biome",
                com.github.retrooper.packetevents.protocol.world.biome.Biomes.getRegistry(),version,
                value->com.github.retrooper.packetevents.protocol.world.biome.Biome.encode(value,version));
        putPacketEventsRegistry(registries,"minecraft:damage_type",
                com.github.retrooper.packetevents.protocol.world.damagetype.DamageTypes.getRegistry(),version,
                value->com.github.retrooper.packetevents.protocol.world.damagetype.DamageType.DIRECT_CODEC.encode(
                        com.github.retrooper.packetevents.wrapper.PacketWrapper.createDummyWrapper(version),value));
        putPacketEventsRegistry(registries,"minecraft:wolf_variant",
                com.github.retrooper.packetevents.protocol.entity.wolfvariant.WolfVariants.getRegistry(),version,
                value->com.github.retrooper.packetevents.protocol.entity.wolfvariant.WolfVariant.encode(value,version));
        putPacketEventsRegistry(registries,"minecraft:cat_variant",
                com.github.retrooper.packetevents.protocol.entity.cat.CatVariants.getRegistry(),version,
                value->com.github.retrooper.packetevents.protocol.entity.cat.CatVariant.encode(value,version));
        putPacketEventsRegistry(registries,"minecraft:cow_variant",
                com.github.retrooper.packetevents.protocol.entity.cow.CowVariants.getRegistry(),version,
                value->com.github.retrooper.packetevents.protocol.entity.cow.CowVariant.encode(value,version));
        putPacketEventsRegistry(registries,"minecraft:pig_variant",
                com.github.retrooper.packetevents.protocol.entity.pig.PigVariants.getRegistry(),version,
                value->com.github.retrooper.packetevents.protocol.entity.pig.PigVariant.encode(value,version));
        putPacketEventsRegistry(registries,"minecraft:chicken_variant",
                com.github.retrooper.packetevents.protocol.entity.chicken.ChickenVariants.getRegistry(),version,
                value->com.github.retrooper.packetevents.protocol.entity.chicken.ChickenVariant.encode(value,version));
        putPacketEventsRegistry(registries,"minecraft:frog_variant",
                com.github.retrooper.packetevents.protocol.entity.frog.FrogVariants.getRegistry(),version,
                value->com.github.retrooper.packetevents.protocol.entity.frog.FrogVariant.encode(value,version));
        putPacketEventsRegistry(registries,"minecraft:wolf_sound_variant",
                com.github.retrooper.packetevents.protocol.entity.wolfvariant.WolfSoundVariants.getRegistry(),version,
                value->com.github.retrooper.packetevents.protocol.entity.wolfvariant.WolfSoundVariant.encode(value,version));
    }

    private static <T extends com.github.retrooper.packetevents.protocol.mapper.MappedEntity>
    void putPacketEventsRegistry(
            Map<ResourceLocation,List<WrapperConfigServerRegistryData.RegistryElement>> registries,
            String registryName,com.github.retrooper.packetevents.util.mappings.VersionedRegistry<T> registry,
            ClientVersion version,RegistryEncoder<T> encoder){
        ResourceLocation registryKey=new ResourceLocation(registryName);
        Map<ResourceLocation,NBT> fallbacks=new LinkedHashMap<>();
        List<WrapperConfigServerRegistryData.RegistryElement> existing=registries.get(registryKey);
        if(existing!=null)for(WrapperConfigServerRegistryData.RegistryElement entry:existing)
            fallbacks.put(entry.getId(),entry.getData());
        List<WrapperConfigServerRegistryData.RegistryElement> entries=new ArrayList<>();
        for(T value:registry.getEntries()){
            if(value.getId(version)<0)continue;
            NBT encoded=encoder.encode(value);
            mergeMissingData(encoded,fallbacks.get(value.getName()));
            normalizePacketEventsEntry(registryName,value.getName(),encoded,version);
            entries.add(new WrapperConfigServerRegistryData.RegistryElement(value.getName(),encoded));
        }
        if(!entries.isEmpty())registries.put(registryKey,Collections.unmodifiableList(entries));
    }

    private static void mergeMissingData(NBT preferred,NBT fallback){
        if(!(preferred instanceof NBTCompound)||!(fallback instanceof NBTCompound))return;
        NBTCompound target=(NBTCompound)preferred,source=(NBTCompound)fallback;
        for(Map.Entry<String,NBT> entry:source.getTags().entrySet()){
            NBT current=target.getTagOrNull(entry.getKey());
            if(current==null)target.setTag(entry.getKey(),entry.getValue().copy());
            else mergeMissingData(current,entry.getValue());
        }
    }

    private static void normalizePacketEventsEntry(String registryName,ResourceLocation entryName,NBT encoded,ClientVersion version){
        if(!(encoded instanceof NBTCompound))return;
        NBTCompound value=(NBTCompound)encoded;
        if("minecraft:enchantment".equals(registryName)
                &&version.isNewerThanOrEquals(ClientVersion.V_1_21_2)){
            rewriteRenamedEnchantmentEffects(value);
        }
        if("minecraft:worldgen/biome".equals(registryName)){
            NBTCompound effects=value.getCompoundTagOrNull("effects");
            if(effects!=null&&version.isOlderThan(ClientVersion.V_1_21_11)){
                Number fog=effects.getNumberTagValueOrNull("fog_color");
                if(fog==null||fog.intValue()==0){
                    int color="minecraft:nether_wastes".equals(entryName.toString())?0x330808
                            :"minecraft:the_end".equals(entryName.toString())?0xA080A0:0xC0D8FF;
                    effects.setTag("fog_color",new NBTInt(color));
                }
                Number waterFog=effects.getNumberTagValueOrNull("water_fog_color");
                if(waterFog==null||waterFog.intValue()==0xFAFACD)
                    effects.setTag("water_fog_color",new NBTInt(0x050533));
            }
            if(version.isNewerThanOrEquals(ClientVersion.V_1_21_4)
                    &&effects!=null&&effects.getTagOrNull("music") instanceof NBTCompound){
                NBTCompound oldMusic=(NBTCompound)effects.removeTag("music");
                NBTCompound weighted=new NBTCompound();weighted.setTag("data",oldMusic);
                weighted.setTag("weight",new NBTInt(1));
                NBTList<NBTCompound> music=NBTList.createCompoundList();music.addTag(weighted);
                effects.setTag("music",music);
            }
        }
        if("minecraft:wolf_variant".equals(registryName)
                &&version.isNewerThanOrEquals(ClientVersion.V_1_21_5)
                &&value.getCompoundTagOrNull("assets")==null){
            NBT wild=value.removeTag("wild_texture"),tame=value.removeTag("tame_texture"),
                    angry=value.removeTag("angry_texture");
            if(wild!=null&&tame!=null&&angry!=null){
                NBTCompound assets=new NBTCompound();assets.setTag("wild",wild);
                assets.setTag("tame",tame);assets.setTag("angry",angry);
                value.setTag("assets",assets);value.removeTag("biomes");
            }
        }
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static void rewriteRenamedEnchantmentEffects(NBT tag){
        if(tag instanceof NBTCompound){
            NBTCompound compound=(NBTCompound)tag;
            for(Map.Entry<String,NBT> entry:new ArrayList<>(compound.getTags().entrySet())){
                NBT child=entry.getValue();
                if(child instanceof com.github.retrooper.packetevents.protocol.nbt.NBTString
                        &&"minecraft:damage_item".equals(((com.github.retrooper.packetevents.protocol.nbt.NBTString)child).getValue())){
                    compound.setTag(entry.getKey(),new com.github.retrooper.packetevents.protocol.nbt.NBTString(
                            "minecraft:change_item_damage"));
                }else rewriteRenamedEnchantmentEffects(child);
            }
        }else if(tag instanceof NBTList){
            NBTList list=(NBTList)tag;
            for(int i=0;i<list.size();i++){
                NBT child=list.getTag(i);
                if(child instanceof com.github.retrooper.packetevents.protocol.nbt.NBTString
                        &&"minecraft:damage_item".equals(((com.github.retrooper.packetevents.protocol.nbt.NBTString)child).getValue())){
                    list.setTag(i,new com.github.retrooper.packetevents.protocol.nbt.NBTString(
                            "minecraft:change_item_damage"));
                }else rewriteRenamedEnchantmentEffects(child);
            }
        }
    }

    private interface RegistryEncoder<T>{NBT encode(T value);}

    private static void collectTagReferences(JsonElement element,java.util.Set<ResourceLocation> tags){
        if(element==null||element.isJsonNull())return;
        if(element.isJsonPrimitive()&&element.getAsJsonPrimitive().isString()){
            String value=element.getAsString();
            String candidate=value.startsWith("#")?value.substring(1):"";
            if(candidate.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))tags.add(new ResourceLocation(candidate));
        }else if(element.isJsonArray()){
            for(JsonElement child:element.getAsJsonArray())collectTagReferences(child,tags);
        }else if(element.isJsonObject()){
            for(Map.Entry<String,JsonElement> child:element.getAsJsonObject().entrySet())
                collectTagReferences(child.getValue(),tags);
        }
    }

    private static void forceClassicHeight(NBTCompound codec){
        NBTCompound registry=codec.getCompoundTagOrNull("minecraft:dimension_type");
        if(registry==null)return;
        for(NBTCompound entry:registry.getCompoundListTagOrThrow("value").getTags())
            forceClassicDimension(entry.getCompoundTagOrThrow("element"));
    }

    private static void forceClassicDimension(NBTCompound element){
        element.setTag("logical_height",new NBTInt(256));
        if(element.getTagOrNull("min_y")!=null)element.setTag("min_y",new NBTInt(0));
        if(element.getTagOrNull("height")!=null)element.setTag("height",new NBTInt(256));
    }

    private static final class RegistryData{
        final String release;final NBTCompound legacy;
        final Map<ResourceLocation,List<WrapperConfigServerRegistryData.RegistryElement>> split;
        final java.util.Set<ResourceLocation> referencedTags;
        final Map<ResourceLocation,java.util.Set<ResourceLocation>> referencedTagsByRegistry;
        RegistryData(String release,NBTCompound legacy,
                     Map<ResourceLocation,List<WrapperConfigServerRegistryData.RegistryElement>> split,
                     java.util.Set<ResourceLocation> referencedTags,
                     Map<ResourceLocation,java.util.Set<ResourceLocation>> referencedTagsByRegistry){
            this.release=release;this.legacy=legacy;this.split=split;this.referencedTags=referencedTags;
            this.referencedTagsByRegistry=referencedTagsByRegistry;
        }
    }
}
