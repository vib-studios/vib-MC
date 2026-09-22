package net.vibmc.registry;

import com.github.retrooper.packetevents.protocol.nbt.*;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData;
import com.viaversion.nbt.tag.Tag;
import com.viaversion.nbt.tag.collection.ListTag;
import com.viaversion.nbt.tag.compound.CompoundTag;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Version-selected Configuration registry data loaded from ViaVersion Mappings via ViaNBT. */
public final class RegistryCodec {
    private static final Map<String, RegistryData> CACHE = new ConcurrentHashMap<>();

    private RegistryCodec() {}

    /** Legacy single-compound registry payload used through the 1.20.3 protocol family. */
    public static NBTCompound create(ClientVersion version) {
        RegistryData data = data(version);
        if (data.legacy == null) {
            throw new IllegalStateException("Client version " + version + " uses split Configuration registries");
        }
        return data.legacy.copy();
    }

    /** True when registry data is represented as one packet per registry (1.20.5+). */
    public static boolean usesSplitRegistries(ClientVersion version) {
        return data(version).legacy == null;
    }

    public static Set<ResourceLocation> referencedTags(ClientVersion version) {
        return data(version).referencedTags;
    }

    public static Map<ResourceLocation, Set<ResourceLocation>> referencedTagsByRegistry(ClientVersion version) {
        return data(version).referencedTagsByRegistry;
    }

    public static Map<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> splitRegistries(ClientVersion version) {
        RegistryData data = data(version);
        if (data.legacy != null) {
            throw new IllegalStateException("Client version " + version + " uses a compound registry codec");
        }
        Map<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> copy = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> entry : data.split.entrySet()) {
            List<WrapperConfigServerRegistryData.RegistryElement> elements = new ArrayList<>();
            for (WrapperConfigServerRegistryData.RegistryElement element : entry.getValue()) {
                NBT val = element.getData();
                elements.add(new WrapperConfigServerRegistryData.RegistryElement(element.getId(), val == null ? null : val.copy()));
            }
            copy.put(entry.getKey(), Collections.unmodifiableList(elements));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static RegistryData data(ClientVersion version) {
        if (version.isOlderThan(ClientVersion.V_1_16_2)) {
            return new RegistryData("legacy", new NBTCompound(), null, Collections.emptySet(), Collections.emptyMap());
        }
        CompoundTag mappingsTag = Registry.get().forClient(version);
        String release = Registry.get().selectRelease(version);
        return CACHE.computeIfAbsent(release, ignored -> load(mappingsTag, release, version));
    }

    private static RegistryData load(CompoundTag root, String release, ClientVersion version) {
        String codecType = root.getString("codecType", "compound");
        Set<ResourceLocation> referencedTags = new LinkedHashSet<>();
        Map<ResourceLocation, Set<ResourceLocation>> tagsByRegistry = new LinkedHashMap<>();

        if ("compound".equals(codecType)) {
            CompoundTag codecVia = root.getCompoundTag("dimensionCodec");
            if (codecVia == null) codecVia = new CompoundTag();
            NBTCompound legacy = NbtMapper.toPacketEventsCompound(codecVia);
            collectTagReferences(legacy, referencedTags);
            forceClassicHeight(legacy);
            return new RegistryData(release, legacy, null, Collections.unmodifiableSet(referencedTags), Collections.emptyMap());
        } else {
            CompoundTag regsVia = root.getCompoundTag("registries");
            Map<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> registries = new LinkedHashMap<>();

            if (regsVia != null) {
                for (Map.Entry<String, Tag> entry : regsVia.entrySet()) {
                    ResourceLocation registryKey = new ResourceLocation(entry.getKey());
                    Set<ResourceLocation> registryTags = new LinkedHashSet<>();
                    List<WrapperConfigServerRegistryData.RegistryElement> elements = new ArrayList<>();

                    if (entry.getValue() instanceof ListTag) {
                        ListTag<?> list = (ListTag<?>) entry.getValue();
                        for (Tag item : list) {
                            if (item instanceof CompoundTag) {
                                CompoundTag elemTag = (CompoundTag) item;
                                String key = elemTag.getString("key", "");
                                Tag valVia = elemTag.get("value");
                                NBT valPE = NbtMapper.toPacketEvents(valVia);

                                if ("minecraft:dimension_type".equals(registryKey.toString()) && valPE instanceof NBTCompound) {
                                    forceClassicDimension((NBTCompound) valPE);
                                }

                                collectTagReferences(valPE, registryTags);
                                collectTagReferences(valPE, referencedTags);
                                elements.add(new WrapperConfigServerRegistryData.RegistryElement(new ResourceLocation(key), valPE));
                            }
                        }
                    }
                    if (!registryTags.isEmpty()) {
                        tagsByRegistry.put(registryKey, Collections.unmodifiableSet(registryTags));
                    }
                    registries.put(registryKey, elements);
                }
            }

            if (version.isNewerThanOrEquals(ClientVersion.V_1_21_5)) {
                overlayPacketEventsRegistries(registries, version);
            }

            for (Map.Entry<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> reg : registries.entrySet()) {
                for (WrapperConfigServerRegistryData.RegistryElement entry : reg.getValue()) {
                    normalizePacketEventsEntry(reg.getKey().toString(), entry.getId(), entry.getData(), version);
                }
            }

            return new RegistryData(release, null, Collections.unmodifiableMap(registries),
                    Collections.unmodifiableSet(referencedTags), Collections.unmodifiableMap(tagsByRegistry));
        }
    }

    @SuppressWarnings("deprecation")
    private static void overlayPacketEventsRegistries(
            Map<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> registries,
            ClientVersion version) {
        putPacketEventsRegistry(registries, "minecraft:worldgen/biome",
                com.github.retrooper.packetevents.protocol.world.biome.Biomes.getRegistry(), version,
                val -> com.github.retrooper.packetevents.protocol.world.biome.Biome.encode(val, version));
        putPacketEventsRegistry(registries, "minecraft:damage_type",
                com.github.retrooper.packetevents.protocol.world.damagetype.DamageTypes.getRegistry(), version,
                val -> com.github.retrooper.packetevents.protocol.world.damagetype.DamageType.DIRECT_CODEC.encode(
                        com.github.retrooper.packetevents.wrapper.PacketWrapper.createDummyWrapper(version), val));
        putPacketEventsRegistry(registries, "minecraft:wolf_variant",
                com.github.retrooper.packetevents.protocol.entity.wolfvariant.WolfVariants.getRegistry(), version,
                val -> com.github.retrooper.packetevents.protocol.entity.wolfvariant.WolfVariant.encode(val, version));
        putPacketEventsRegistry(registries, "minecraft:cat_variant",
                com.github.retrooper.packetevents.protocol.entity.cat.CatVariants.getRegistry(), version,
                val -> com.github.retrooper.packetevents.protocol.entity.cat.CatVariant.encode(val, version));
        putPacketEventsRegistry(registries, "minecraft:cow_variant",
                com.github.retrooper.packetevents.protocol.entity.cow.CowVariants.getRegistry(), version,
                val -> com.github.retrooper.packetevents.protocol.entity.cow.CowVariant.encode(val, version));
        putPacketEventsRegistry(registries, "minecraft:pig_variant",
                com.github.retrooper.packetevents.protocol.entity.pig.PigVariants.getRegistry(), version,
                val -> com.github.retrooper.packetevents.protocol.entity.pig.PigVariant.encode(val, version));
        putPacketEventsRegistry(registries, "minecraft:chicken_variant",
                com.github.retrooper.packetevents.protocol.entity.chicken.ChickenVariants.getRegistry(), version,
                val -> com.github.retrooper.packetevents.protocol.entity.chicken.ChickenVariant.encode(val, version));
        putPacketEventsRegistry(registries, "minecraft:frog_variant",
                com.github.retrooper.packetevents.protocol.entity.frog.FrogVariants.getRegistry(), version,
                val -> com.github.retrooper.packetevents.protocol.entity.frog.FrogVariant.encode(val, version));
        putPacketEventsRegistry(registries, "minecraft:wolf_sound_variant",
                com.github.retrooper.packetevents.protocol.entity.wolfvariant.WolfSoundVariants.getRegistry(), version,
                val -> com.github.retrooper.packetevents.protocol.entity.wolfvariant.WolfSoundVariant.encode(val, version));
    }

    private static <T extends com.github.retrooper.packetevents.protocol.mapper.MappedEntity>
    void putPacketEventsRegistry(
            Map<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> registries,
            String registryName, com.github.retrooper.packetevents.util.mappings.VersionedRegistry<T> registry,
            ClientVersion version, RegistryEncoder<T> encoder) {
        ResourceLocation registryKey = new ResourceLocation(registryName);
        Map<ResourceLocation, NBT> fallbacks = new LinkedHashMap<>();
        List<WrapperConfigServerRegistryData.RegistryElement> existing = registries.get(registryKey);
        if (existing != null) {
            for (WrapperConfigServerRegistryData.RegistryElement entry : existing) {
                fallbacks.put(entry.getId(), entry.getData());
            }
        }
        List<WrapperConfigServerRegistryData.RegistryElement> entries = new ArrayList<>();
        for (T value : registry.getEntries()) {
            if (value.getId(version) < 0) continue;
            NBT encoded = encoder.encode(value);
            mergeMissingData(encoded, fallbacks.get(value.getName()));
            normalizePacketEventsEntry(registryName, value.getName(), encoded, version);
            entries.add(new WrapperConfigServerRegistryData.RegistryElement(value.getName(), encoded));
        }
        if (!entries.isEmpty()) registries.put(registryKey, Collections.unmodifiableList(entries));
    }

    private static void mergeMissingData(NBT preferred, NBT fallback) {
        if (!(preferred instanceof NBTCompound) || !(fallback instanceof NBTCompound)) return;
        NBTCompound target = (NBTCompound) preferred, source = (NBTCompound) fallback;
        for (Map.Entry<String, NBT> entry : source.getTags().entrySet()) {
            NBT current = target.getTagOrNull(entry.getKey());
            if (current == null) target.setTag(entry.getKey(), entry.getValue().copy());
            else mergeMissingData(current, entry.getValue());
        }
    }

    private static void normalizePacketEventsEntry(String registryName, ResourceLocation entryName, NBT encoded, ClientVersion version) {
        if (!(encoded instanceof NBTCompound)) return;
        NBTCompound value = (NBTCompound) encoded;

        if ("minecraft:enchantment".equals(registryName) && version.isNewerThanOrEquals(ClientVersion.V_1_21_2)) {
            rewriteRenamedEnchantmentEffects(value);
        }

        if ("minecraft:worldgen/biome".equals(registryName)) {
            NBTCompound effects = value.getCompoundTagOrNull("effects");
            if (effects != null && version.isOlderThan(ClientVersion.V_1_21_11)) {
                Number fog = effects.getNumberTagValueOrNull("fog_color");
                if (fog == null || fog.intValue() == 0) {
                    int color = "minecraft:nether_wastes".equals(entryName.toString()) ? 0x330808
                            : "minecraft:the_end".equals(entryName.toString()) ? 0xA080A0 : 0xC0D8FF;
                    effects.setTag("fog_color", new NBTInt(color));
                }
                Number waterFog = effects.getNumberTagValueOrNull("water_fog_color");
                if (waterFog == null || waterFog.intValue() == 0xFAFACD) {
                    effects.setTag("water_fog_color", new NBTInt(0x050533));
                }
            }
            if (version.isNewerThanOrEquals(ClientVersion.V_1_21_4) && effects != null && effects.getTagOrNull("music") instanceof NBTCompound) {
                NBTCompound oldMusic = (NBTCompound) effects.removeTag("music");
                NBTCompound weighted = new NBTCompound();
                weighted.setTag("data", oldMusic);
                weighted.setTag("weight", new NBTInt(1));
                NBTList<NBTCompound> music = NBTList.createCompoundList();
                music.addTag(weighted);
                effects.setTag("music", music);
            }
        }

        if ("minecraft:wolf_variant".equals(registryName) && version.isNewerThanOrEquals(ClientVersion.V_1_21_5) && value.getCompoundTagOrNull("assets") == null) {
            NBT wild = value.removeTag("wild_texture"), tame = value.removeTag("tame_texture"), angry = value.removeTag("angry_texture");
            if (wild != null && tame != null && angry != null) {
                NBTCompound assets = new NBTCompound();
                assets.setTag("wild", wild);
                assets.setTag("tame", tame);
                assets.setTag("angry", angry);
                value.setTag("assets", assets);
                value.removeTag("biomes");
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void rewriteRenamedEnchantmentEffects(NBT tag) {
        if (tag instanceof NBTCompound) {
            NBTCompound compound = (NBTCompound) tag;
            for (Map.Entry<String, NBT> entry : new ArrayList<>(compound.getTags().entrySet())) {
                NBT child = entry.getValue();
                if (child instanceof NBTString && "minecraft:damage_item".equals(((NBTString) child).getValue())) {
                    compound.setTag(entry.getKey(), new NBTString("minecraft:change_item_damage"));
                } else rewriteRenamedEnchantmentEffects(child);
            }
        } else if (tag instanceof NBTList) {
            NBTList list = (NBTList) tag;
            for (int i = 0; i < list.size(); i++) {
                NBT child = list.getTag(i);
                if (child instanceof NBTString && "minecraft:damage_item".equals(((NBTString) child).getValue())) {
                    list.setTag(i, new NBTString("minecraft:change_item_damage"));
                } else rewriteRenamedEnchantmentEffects(child);
            }
        }
    }

    private interface RegistryEncoder<T> {
        NBT encode(T value);
    }

    private static void collectTagReferences(NBT element, Set<ResourceLocation> tags) {
        if (element == null) return;
        if (element instanceof NBTString) {
            String value = ((NBTString) element).getValue();
            String candidate = value.startsWith("#") ? value.substring(1) : "";
            if (candidate.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                tags.add(new ResourceLocation(candidate));
            }
        } else if (element instanceof NBTCompound) {
            for (NBT child : ((NBTCompound) element).getTags().values()) {
                collectTagReferences(child, tags);
            }
        } else if (element instanceof NBTList) {
            for (Object child : ((NBTList<?>) element).getTags()) {
                collectTagReferences((NBT) child, tags);
            }
        }
    }

    private static void forceClassicHeight(NBTCompound codec) {
        NBTCompound registry = codec.getCompoundTagOrNull("minecraft:dimension_type");
        if (registry == null) return;
        for (NBTCompound entry : registry.getCompoundListTagOrThrow("value").getTags()) {
            forceClassicDimension(entry.getCompoundTagOrThrow("element"));
        }
    }

    private static void forceClassicDimension(NBTCompound element) {
        element.setTag("logical_height", new NBTInt(256));
        if (element.getTagOrNull("min_y") != null) element.setTag("min_y", new NBTInt(0));
        if (element.getTagOrNull("height") != null) element.setTag("height", new NBTInt(256));
    }

    private static final class RegistryData {
        final String release;
        final NBTCompound legacy;
        final Map<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> split;
        final Set<ResourceLocation> referencedTags;
        final Map<ResourceLocation, Set<ResourceLocation>> referencedTagsByRegistry;

        RegistryData(String release, NBTCompound legacy,
                     Map<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> split,
                     Set<ResourceLocation> referencedTags,
                     Map<ResourceLocation, Set<ResourceLocation>> referencedTagsByRegistry) {
            this.release = release;
            this.legacy = legacy;
            this.split = split;
            this.referencedTags = referencedTags;
            this.referencedTagsByRegistry = referencedTagsByRegistry;
        }
    }
}
