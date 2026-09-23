package net.vibmc.registry;

import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.component.IComponentMap;
import com.github.retrooper.packetevents.protocol.dialog.CommonDialogData;
import com.github.retrooper.packetevents.protocol.dialog.Dialog;
import com.github.retrooper.packetevents.protocol.dialog.DialogListDialog;
import com.github.retrooper.packetevents.protocol.dialog.button.ActionButton;
import com.github.retrooper.packetevents.protocol.entity.cat.CatVariant;
import com.github.retrooper.packetevents.protocol.entity.chicken.ChickenVariant;
import com.github.retrooper.packetevents.protocol.entity.cow.CowVariant;
import com.github.retrooper.packetevents.protocol.entity.pig.PigVariant;
import com.github.retrooper.packetevents.protocol.item.enchantment.EnchantmentDefinition;
import com.github.retrooper.packetevents.protocol.item.enchantment.type.EnchantmentType;
import com.github.retrooper.packetevents.protocol.item.enchantment.type.EnchantmentTypes;
import com.github.retrooper.packetevents.protocol.mapper.MappedEntity;
import com.github.retrooper.packetevents.protocol.mapper.MappedEntityRef;
import com.github.retrooper.packetevents.protocol.mapper.MappedEntityRefSet;
import com.github.retrooper.packetevents.protocol.mapper.MappedEntitySet;
import com.github.retrooper.packetevents.protocol.nbt.NBT;
import com.github.retrooper.packetevents.protocol.nbt.NBTByte;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTDouble;
import com.github.retrooper.packetevents.protocol.nbt.NBTFloat;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import com.github.retrooper.packetevents.protocol.nbt.NBTLong;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.attributes.EnvironmentAttributeMap;
import com.github.retrooper.packetevents.protocol.world.attributes.timelines.Timeline;
import com.github.retrooper.packetevents.protocol.world.attributes.timelines.Timelines;
import com.github.retrooper.packetevents.protocol.world.clock.WorldClock;
import com.github.retrooper.packetevents.protocol.world.clock.WorldClocks;
import com.github.retrooper.packetevents.protocol.world.damagetype.DamageType;
import com.github.retrooper.packetevents.protocol.world.damagetype.DamageTypes;
import com.github.retrooper.packetevents.protocol.world.dimension.DimensionType;
import com.github.retrooper.packetevents.protocol.world.dimension.DimensionTypes;
import com.github.retrooper.packetevents.protocol.util.NbtCodec;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.resources.TagKey;
import com.github.retrooper.packetevents.util.mappings.IRegistry;
import com.github.retrooper.packetevents.util.mappings.VersionedRegistry;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry codec built from PacketEvents versioned registries and ViaVersion mapping NBT assets.
 * Workarounds:
 * - PE 2.13.0 StackOverflow in MappedEntitySet.encodeRefSet for enchantment/dimension/dialog
 * - PE 2.13.0 off-by-one for baby_asset_id (isNewerThan vs isNewerThanOrEquals for 26.1)
 * - PE registry CODECs that return reference strings (forRegistry) vs direct compound – we use DIRECT_CODEC/encodeDirect for registry payloads
 */
public final class RegistryDataCodec {
    private static final Map<ClientVersion, RegistryData> CACHE = new ConcurrentHashMap<>();
    private static final NbtCodec<MappedEntityRef<WorldClock>> WORLD_CLOCK_REF_CODEC = MappedEntityRef.codec(WorldClock.CODEC);

    private RegistryDataCodec() {}

    public static NBTCompound create(ClientVersion version) {
        RegistryData data = data(version);
        if (data.legacy == null) {
            throw new IllegalStateException("Version " + version + " uses split registries");
        }
        return data.legacy.copy();
    }

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
            throw new IllegalStateException("Version " + version + " uses a compound codec");
        }
        Map<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> copy = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> e : data.split.entrySet()) {
            List<WrapperConfigServerRegistryData.RegistryElement> list = new ArrayList<>();
            for (WrapperConfigServerRegistryData.RegistryElement el : e.getValue()) {
                NBT v = el.getData();
                list.add(new WrapperConfigServerRegistryData.RegistryElement(el.getId(), v == null ? null : v.copy()));
            }
            copy.put(e.getKey(), Collections.unmodifiableList(list));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static RegistryData data(ClientVersion version) {
        if (version.isOlderThan(ClientVersion.V_1_16_2)) {
            return new RegistryData(version.getReleaseName(), new NBTCompound(), null,
                    Collections.emptySet(), Collections.emptyMap());
        }
        return CACHE.computeIfAbsent(version, RegistryDataCodec::load);
    }

    private static RegistryData load(ClientVersion version) {
        boolean split = version.isNewerThanOrEquals(ClientVersion.V_1_20_5);
        Map<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> registries = new LinkedHashMap<>();

        buildRegistries(version, registries);

        Set<ResourceLocation> referencedTags = new LinkedHashSet<>();
        Map<ResourceLocation, Set<ResourceLocation>> tagsByRegistry = new LinkedHashMap<>();

        if (split) {
            for (Map.Entry<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> entry : registries.entrySet()) {
                Set<ResourceLocation> localTags = new LinkedHashSet<>();
                for (WrapperConfigServerRegistryData.RegistryElement el : entry.getValue()) {
                    collectTagReferences(el.getData(), referencedTags);
                    collectTagReferences(el.getData(), localTags);
                }
                if (!localTags.isEmpty()) {
                    tagsByRegistry.put(entry.getKey(), Collections.unmodifiableSet(localTags));
                }
            }
            return new RegistryData(version.getReleaseName(), null,
                    Collections.unmodifiableMap(registries),
                    Collections.unmodifiableSet(referencedTags),
                    Collections.unmodifiableMap(tagsByRegistry));
        } else {
            NBTCompound compound = new NBTCompound();
            for (Map.Entry<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> entry : registries.entrySet()) {
                ResourceLocation registryKey = entry.getKey();
                NBTCompound registryCompound = new NBTCompound();
                registryCompound.setTag("type", new NBTString(registryKey.toString()));
                NBTList<NBTCompound> valueList = NBTList.createCompoundList();
                int id = 0;
                for (WrapperConfigServerRegistryData.RegistryElement el : entry.getValue()) {
                    NBTCompound entryCompound = new NBTCompound();
                    entryCompound.setTag("name", new NBTString(el.getId().toString()));
                    entryCompound.setTag("id", new NBTInt(id++));
                    if (el.getData() != null) {
                        entryCompound.setTag("element", el.getData().copy());
                        collectTagReferences(el.getData(), referencedTags);
                    }
                    valueList.addTag(entryCompound);
                }
                registryCompound.setTag("value", valueList);
                compound.setTag(registryKey.toString(), registryCompound);
            }
            return new RegistryData(version.getReleaseName(), compound, null,
                    Collections.unmodifiableSet(referencedTags),
                    Collections.emptyMap());
        }
    }

    @SuppressWarnings("deprecation")
    private static void buildRegistries(ClientVersion version,
                                        Map<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> out) {
        if (version.isNewerThanOrEquals(ClientVersion.V_1_16_2)) {
            putRegistryWithCodec(out, "minecraft:dimension_type",
                    DimensionTypes.getRegistry(),
                    version,
                    (wrapper, value) -> encodeDimensionSafe(wrapper, value));
            putRegistry(out, "minecraft:worldgen/biome",
                    com.github.retrooper.packetevents.protocol.world.biome.Biomes.getRegistry(),
                    version,
                    value -> com.github.retrooper.packetevents.protocol.world.biome.Biome.encode(value, version));
        }

        if (version.isNewerThanOrEquals(ClientVersion.V_1_19)) {
            putRegistry(out, "minecraft:chat_type",
                    com.github.retrooper.packetevents.protocol.chat.ChatTypes.getRegistry(),
                    version,
                    value -> com.github.retrooper.packetevents.protocol.chat.ChatType.encode(value, version));
        }

        if (version.isNewerThanOrEquals(ClientVersion.V_1_19_4)) {
            // Use DIRECT_CODEC for damage_type – CODEC is forRegistry (returns string reference)
            putRegistryWithCodec(out, "minecraft:damage_type",
                    DamageTypes.getRegistry(),
                    version,
                    (wrapper, value) -> DamageType.DIRECT_CODEC.encode(wrapper, value));
            putRegistry(out, "minecraft:trim_material",
                    com.github.retrooper.packetevents.protocol.item.trimmaterial.TrimMaterials.getRegistry(),
                    version,
                    value -> com.github.retrooper.packetevents.protocol.item.trimmaterial.TrimMaterial.encode(value, version));
            putRegistry(out, "minecraft:trim_pattern",
                    com.github.retrooper.packetevents.protocol.item.trimpattern.TrimPatterns.getRegistry(),
                    version,
                    value -> com.github.retrooper.packetevents.protocol.item.trimpattern.TrimPattern.encode(value, version));
        }

        if (version.isNewerThanOrEquals(ClientVersion.V_1_20_5)) {
            putRegistry(out, "minecraft:banner_pattern",
                    com.github.retrooper.packetevents.protocol.item.banner.BannerPatterns.getRegistry(),
                    version,
                    value -> com.github.retrooper.packetevents.protocol.item.banner.BannerPattern.encode(value, version));
            putRegistryWithCodec(out, "minecraft:wolf_variant",
                    com.github.retrooper.packetevents.protocol.entity.wolfvariant.WolfVariants.getRegistry(),
                    version,
                    (wrapper, value) -> com.github.retrooper.packetevents.protocol.entity.wolfvariant.WolfVariant.CODEC.encode(wrapper, value));
        }

        if (version.isNewerThanOrEquals(ClientVersion.V_1_21)) {
            putRegistryWithCodec(out, "minecraft:enchantment",
                    com.github.retrooper.packetevents.protocol.item.enchantment.type.EnchantmentTypes.getRegistry(),
                    version,
                    (wrapper, value) -> encodeEnchantmentSafe(wrapper, value));
            putRegistry(out, "minecraft:jukebox_song",
                    com.github.retrooper.packetevents.protocol.item.jukebox.JukeboxSongs.getRegistry(),
                    version,
                    value -> com.github.retrooper.packetevents.protocol.item.jukebox.IJukeboxSong.encode(value, version));
            putRegistry(out, "minecraft:painting_variant",
                    com.github.retrooper.packetevents.protocol.world.painting.PaintingVariants.getRegistry(),
                    version,
                    value -> com.github.retrooper.packetevents.protocol.world.painting.PaintingVariant.encode(value, version));
        }

        if (version.isNewerThanOrEquals(ClientVersion.V_1_21_2)) {
            putRegistry(out, "minecraft:instrument",
                    com.github.retrooper.packetevents.protocol.item.instrument.Instruments.getRegistry(),
                    version,
                    value -> com.github.retrooper.packetevents.protocol.item.instrument.Instrument.encode(value, version));
        }

        if (version.isNewerThanOrEquals(ClientVersion.V_1_21_11)) {
            // Variants with baby_asset_id bug in PE 2.13.0 – use safe encoders that always include baby_asset_id for 26.1+
            putRegistryWithCodec(out, "minecraft:cat_variant",
                    com.github.retrooper.packetevents.protocol.entity.cat.CatVariants.getRegistry(),
                    version,
                    (wrapper, value) -> encodeCatVariantSafe(wrapper, value));
            putRegistryWithCodec(out, "minecraft:chicken_variant",
                    com.github.retrooper.packetevents.protocol.entity.chicken.ChickenVariants.getRegistry(),
                    version,
                    (wrapper, value) -> encodeChickenVariantSafe(wrapper, value));
            putRegistryWithCodec(out, "minecraft:cow_variant",
                    com.github.retrooper.packetevents.protocol.entity.cow.CowVariants.getRegistry(),
                    version,
                    (wrapper, value) -> encodeCowVariantSafe(wrapper, value));
            putRegistryWithCodec(out, "minecraft:pig_variant",
                    com.github.retrooper.packetevents.protocol.entity.pig.PigVariants.getRegistry(),
                    version,
                    (wrapper, value) -> encodePigVariantSafe(wrapper, value));

            putRegistry(out, "minecraft:frog_variant",
                    com.github.retrooper.packetevents.protocol.entity.frog.FrogVariants.getRegistry(),
                    version,
                    value -> com.github.retrooper.packetevents.protocol.entity.frog.FrogVariant.encode(value, version));
            putRegistry(out, "minecraft:wolf_sound_variant",
                    com.github.retrooper.packetevents.protocol.entity.wolfvariant.WolfSoundVariants.getRegistry(),
                    version,
                    value -> com.github.retrooper.packetevents.protocol.entity.wolfvariant.WolfSoundVariant.encode(value, version));
            putRegistry(out, "minecraft:zombie_nautilus_variant",
                    com.github.retrooper.packetevents.protocol.entity.nautilus.ZombieNautilusVariants.getRegistry(),
                    version,
                    value -> com.github.retrooper.packetevents.protocol.entity.nautilus.ZombieNautilusVariant.encode(value, version));

            putRegistryWithCodec(out, "minecraft:dialog",
                    com.github.retrooper.packetevents.protocol.dialog.Dialogs.getRegistry(),
                    version,
                    (wrapper, value) -> encodeDialogSafe(wrapper, value));

            try {
                Map<String, NBTCompound> timelineEntries = ViaMappings.get().loadTimelineRegistry();
                List<WrapperConfigServerRegistryData.RegistryElement> timelineList = new ArrayList<>();
                List<Map.Entry<String, NBTCompound>> sortedEntries = new ArrayList<>(timelineEntries.entrySet());
                sortedEntries.sort((a, b) -> {
                    Timeline ta = Timelines.getRegistry().getByName(version, new ResourceLocation(a.getKey()));
                    Timeline tb = Timelines.getRegistry().getByName(version, new ResourceLocation(b.getKey()));
                    int ida = ta != null ? ta.getId(version) : Integer.MAX_VALUE;
                    int idb = tb != null ? tb.getId(version) : Integer.MAX_VALUE;
                    if (ida != idb) return Integer.compare(ida, idb);
                    return a.getKey().compareTo(b.getKey());
                });
                for (Map.Entry<String, NBTCompound> e : sortedEntries) {
                    Timeline peTimeline =
                            Timelines.getRegistry().getByName(version, new ResourceLocation(e.getKey()));
                    NBT data;
                    if (peTimeline != null) {
                        PacketWrapper<?> wrapper = PacketWrapper.createDummyWrapper(version);
                        try {
                            data = Timeline.CODEC.encode(wrapper, peTimeline);
                        } catch (Throwable t) {
                            data = e.getValue().copy();
                        }
                    } else {
                        data = e.getValue().copy();
                    }
                    timelineList.add(new WrapperConfigServerRegistryData.RegistryElement(
                            new ResourceLocation(e.getKey()), data));
                }
                if (!timelineList.isEmpty()) {
                    out.put(new ResourceLocation("minecraft:timeline"), Collections.unmodifiableList(timelineList));
                }
            } catch (IOException ex) {
                putRegistryWithCodec(out, "minecraft:timeline",
                        Timelines.getRegistry(),
                        version,
                        (wrapper, value) -> Timeline.CODEC.encode(wrapper, value));
            }
        }

        if (version.isNewerThanOrEquals(ClientVersion.V_26_1)) {
            try {
                Map<String, Map<String, NBTCompound>> soundRegistries = ViaMappings.get().loadSoundVariantRegistries();
                for (Map.Entry<String, Map<String, NBTCompound>> registryEntry : soundRegistries.entrySet()) {
                    String viaKey = registryEntry.getKey();
                    String mcKey = "minecraft:" + viaKey;
                    List<Map.Entry<String, NBTCompound>> sortedVariants = new ArrayList<>(registryEntry.getValue().entrySet());
                    sortedVariants.sort((a, b) -> {
                        int ida = getSoundVariantId(mcKey, new ResourceLocation(a.getKey()), version);
                        int idb = getSoundVariantId(mcKey, new ResourceLocation(b.getKey()), version);
                        if (ida != idb) return Integer.compare(ida, idb);
                        return a.getKey().compareTo(b.getKey());
                    });
                    List<WrapperConfigServerRegistryData.RegistryElement> list = new ArrayList<>();
                    for (Map.Entry<String, NBTCompound> variant : sortedVariants) {
                        NBT data = variant.getValue().copy();
                        ResourceLocation id = new ResourceLocation(variant.getKey());
                        NBT peEncoded = tryEncodeSoundVariant(mcKey, id, version);
                        if (peEncoded != null) {
                            data = peEncoded;
                        }
                        list.add(new WrapperConfigServerRegistryData.RegistryElement(id, data));
                    }
                    if (!list.isEmpty()) {
                        out.put(new ResourceLocation(mcKey), Collections.unmodifiableList(list));
                    }
                }
            } catch (IOException ex) {
                putRegistryWithCodec(out, "minecraft:cat_sound_variant",
                        com.github.retrooper.packetevents.protocol.entity.cat.CatSoundVariants.getRegistry(),
                        version,
                        (wrapper, value) -> com.github.retrooper.packetevents.protocol.entity.cat.CatSoundVariant.CODEC.encode(wrapper, value));
                putRegistryWithCodec(out, "minecraft:cow_sound_variant",
                        com.github.retrooper.packetevents.protocol.entity.cow.CowSoundVariants.getRegistry(),
                        version,
                        (wrapper, value) -> com.github.retrooper.packetevents.protocol.entity.cow.CowSoundVariant.CODEC.encode(wrapper, value));
                putRegistryWithCodec(out, "minecraft:pig_sound_variant",
                        com.github.retrooper.packetevents.protocol.entity.pig.PigSoundVariants.getRegistry(),
                        version,
                        (wrapper, value) -> com.github.retrooper.packetevents.protocol.entity.pig.PigSoundVariant.CODEC.encode(wrapper, value));
                putRegistryWithCodec(out, "minecraft:chicken_sound_variant",
                        com.github.retrooper.packetevents.protocol.entity.chicken.ChickenSoundVariants.getRegistry(),
                        version,
                        (wrapper, value) -> com.github.retrooper.packetevents.protocol.entity.chicken.ChickenSoundVariant.CODEC.encode(wrapper, value));
            }

            // WorldClock: use DIRECT_CODEC (unit) not forRegistry CODEC which returns string reference
            putRegistryWithCodec(out, "minecraft:world_clock",
                    WorldClocks.getRegistry(),
                    version,
                    (wrapper, value) -> WorldClock.DIRECT_CODEC.encode(wrapper, value));
        }

        if (version.isNewerThanOrEquals(ClientVersion.V_26_2)) {
            try {
                putRegistryWithCodec(out, "minecraft:sulfur_cube_archetype",
                        com.github.retrooper.packetevents.protocol.entity.sulfurcube.SulfurCubeArchtypes.getRegistry(),
                        version,
                        (wrapper, value) -> com.github.retrooper.packetevents.protocol.entity.sulfurcube.SulfurCubeArchtype.DIRECT_CODEC.encode(wrapper, value));
            } catch (Throwable ignored) {}
        }

        if (version.isNewerThanOrEquals(ClientVersion.V_26_3)) {
            try {
                NBTCompound root = ViaMappings.get().loadNbt("block-transformer-registry-26.3.nbt");
                List<WrapperConfigServerRegistryData.RegistryElement> list = new ArrayList<>();
                List<Map.Entry<String, NBT>> entries = new ArrayList<>(root.getTags().entrySet());
                entries.sort(Map.Entry.comparingByKey());
                for (Map.Entry<String, NBT> e : entries) {
                    list.add(new WrapperConfigServerRegistryData.RegistryElement(
                            new ResourceLocation(e.getKey()), e.getValue().copy()));
                }
                if (!list.isEmpty()) {
                    out.put(new ResourceLocation("minecraft:block_transformer"), Collections.unmodifiableList(list));
                }
            } catch (Throwable ignored) {}

            try {
                NBTCompound root = ViaMappings.get().loadNbt("decorated-pot-pattern-registry-26.3.nbt");
                List<WrapperConfigServerRegistryData.RegistryElement> list = new ArrayList<>();
                List<Map.Entry<String, NBT>> entries = new ArrayList<>(root.getTags().entrySet());
                entries.sort(Map.Entry.comparingByKey());
                for (Map.Entry<String, NBT> e : entries) {
                    list.add(new WrapperConfigServerRegistryData.RegistryElement(
                            new ResourceLocation(e.getKey()), e.getValue().copy()));
                }
                if (!list.isEmpty()) {
                    out.put(new ResourceLocation("minecraft:decorated_pot_pattern"), Collections.unmodifiableList(list));
                }
            } catch (Throwable ignored) {}
        }

    }

    // ---- safe encoders ----

    private static <T extends MappedEntity> NBT safeEncodeRefSet(PacketWrapper<?> wrapper, MappedEntityRefSet<T> refSet, IRegistry<T> registry) {
        try {
            if (refSet.isEmpty()) {
                return NBTList.createStringList();
            }
            MappedEntitySet<T> resolved = refSet.resolve(wrapper, registry);
            return MappedEntitySet.encode(wrapper, resolved);
        } catch (Throwable t) {
            return NBTList.createStringList();
        }
    }

    private static NBT encodeEnchantmentSafe(PacketWrapper<?> wrapper, EnchantmentType type) {
        try {
            NBTCompound compound = new NBTCompound();
            EnchantmentDefinition.encode(compound, wrapper, type.getDefinition());
            compound.set("description", type.getDescription(), wrapper.getSerializers(), wrapper);
            if (!type.getExclusiveRefSet().isEmpty()) {
                NBT exclusive = safeEncodeRefSet(wrapper, type.getExclusiveRefSet(), EnchantmentTypes.getRegistry());
                compound.setTag("exclusive_set", exclusive);
            }
            if (!type.getEffects().isEmpty()) {
                compound.set("effects", type.getEffects(), IComponentMap::encode, wrapper);
            }
            return compound;
        } catch (Throwable t) {
            try {
                NBTCompound compound = new NBTCompound();
                EnchantmentDefinition.encode(compound, wrapper, type.getDefinition());
                compound.set("description", type.getDescription(), wrapper.getSerializers(), wrapper);
                if (!type.getEffects().isEmpty()) {
                    compound.set("effects", type.getEffects(), IComponentMap::encode, wrapper);
                }
                return compound;
            } catch (Throwable t2) {
                return new NBTCompound();
            }
        }
    }

    private static NBT encodeDimensionSafe(PacketWrapper<?> wrapper, DimensionType value) {
        try {
            NBT encoded = DimensionType.CODEC.encode(wrapper, value);
            if (encoded instanceof NBTCompound) {
                NBTCompound compound = (NBTCompound) encoded;
                // Force classic 256 height for all dimensions to match WorldChunk (0..255).
                // PE overworld is -64/384 for 1.18+, but our world is 256 high, so override.
                ServerVersion sv = wrapper.getServerVersion();
                if (sv.isNewerThanOrEquals(ServerVersion.V_1_17)) {
                    compound.setTag("min_y", new NBTInt(0));
                    compound.setTag("height", new NBTInt(256));
                }
                compound.setTag("logical_height", new NBTInt(256));
                return compound;
            }
            return encoded;
        } catch (Throwable t) {
            try {
                return encodeDimensionWithSafeTimelines(wrapper, value);
            } catch (Throwable t2) {
                return new NBTCompound();
            }
        }
    }

    private static NBT encodeDimensionWithSafeTimelines(PacketWrapper<?> wrapper, DimensionType value) {
        NBTCompound compound = new NBTCompound();
        ServerVersion serverVersion = wrapper.getServerVersion();
        try {
            if (serverVersion.isNewerThanOrEquals(ServerVersion.V_1_21_11)) {
                if (value.hasFixedTime()) {
                    compound.setTag("has_fixed_time", new NBTByte(true));
                }
                DimensionType.Skybox skybox = value.getSkybox();
                if (skybox != DimensionType.Skybox.OVERWORLD) {
                    compound.set("skybox", skybox, DimensionType.Skybox.CODEC, wrapper);
                }
                DimensionType.CardinalLight cardinalLight = value.getCardinalLight();
                if (cardinalLight != DimensionType.CardinalLight.DEFAULT) {
                    compound.set("cardinal_light", cardinalLight, DimensionType.CardinalLight.CODEC, wrapper);
                }
                EnvironmentAttributeMap env = value.getAttributes();
                if (!env.isEmpty()) {
                    compound.set("attributes", env, EnvironmentAttributeMap.CODEC, wrapper);
                }
                MappedEntityRefSet<Timeline> timelines = value.getTimelinesRef();
                if (!timelines.isEmpty()) {
                    NBT timelinesNbt = safeEncodeRefSet(wrapper, timelines, Timelines.getRegistry());
                    compound.setTag("timelines", timelinesNbt);
                }
                if (serverVersion.isNewerThanOrEquals(ServerVersion.V_26_1)) {
                    MappedEntityRef<WorldClock> defaultClock = value.getDefaultClockRef();
                    if (defaultClock != null) {
                        compound.set("default_clock", defaultClock, WORLD_CLOCK_REF_CODEC, wrapper);
                    }
                    compound.setTag("has_ender_dragon_fight", new NBTByte(value.isHasEnderDragonFight()));
                }
            } else {
                OptionalLong fixedTime = value.getFixedTime();
                if (fixedTime.isPresent()) {
                    compound.setTag("fixed_time", new NBTLong(fixedTime.getAsLong()));
                }
                compound.setTag("ultrawarm", new NBTByte(value.isUltraWarm()));
                compound.setTag("natural", new NBTByte(value.isNatural()));
                compound.setTag("bed_works", new NBTByte(value.isBedWorking()));
                compound.setTag("respawn_anchor_works", new NBTByte(value.isRespawnAnchorWorking()));
                compound.setTag("piglin_safe", new NBTByte(value.isPiglinSafe()));
                compound.setTag("has_raids", new NBTByte(value.hasRaids()));
                if (serverVersion.isNewerThanOrEquals(ServerVersion.V_1_16_2)) {
                    compound.set("effects", value.getEffectsLocation(), ResourceLocation.CODEC, wrapper);
                }
                if (serverVersion.isNewerThanOrEquals(ServerVersion.V_1_21_6)) {
                    Integer cloudHeight = value.getCloudHeight();
                    if (cloudHeight != null) {
                        compound.setTag("cloud_height", new NBTInt(cloudHeight));
                    }
                }
            }
            if (serverVersion.isNewerThanOrEquals(ServerVersion.V_1_16_2)) {
                compound.setTag("coordinate_scale", new NBTDouble(value.getCoordinateScale()));
                if (serverVersion.isNewerThanOrEquals(ServerVersion.V_1_17)) {
                    // Classic height: keep 0..256 for all dims, not -64/384
                    compound.setTag("min_y", new NBTInt(0));
                    compound.setTag("height", new NBTInt(256));
                    if (serverVersion.isNewerThanOrEquals(ServerVersion.V_1_19)) {
                        compound.setTag("monster_spawn_light_level", value.getMonsterSpawnLightLevel());
                        compound.setTag("monster_spawn_block_light_limit", new NBTInt(value.getMonsterSpawnBlockLightLimit()));
                    }
                }
            } else {
                compound.setTag("shrunk", new NBTByte(value.isShrunk()));
            }
            compound.setTag("has_skylight", new NBTByte(value.hasSkyLight()));
            compound.setTag("has_ceiling", new NBTByte(value.hasCeiling()));
            compound.setTag("logical_height", new NBTInt(256));
            if (serverVersion.isNewerThanOrEquals(ServerVersion.V_1_18_2)) {
                compound.set("infiniburn", value.getInfiniburn(), TagKey.CODEC, wrapper);
            } else {
                compound.set("infiniburn", value.getInfiniburn().getId(), ResourceLocation.CODEC, wrapper);
            }
            compound.setTag("ambient_light", new NBTFloat(value.getAmbientLight()));
        } catch (Throwable ignored) {
        }
        return compound;
    }

    private static NBT encodeDialogSafe(PacketWrapper<?> wrapper, Dialog dialog) {
        try {
            // For registry payload we need direct compound, not reference string
            return Dialog.encodeDirect(dialog, wrapper);
        } catch (Throwable t) {
            try {
                if (dialog instanceof DialogListDialog) {
                    DialogListDialog listDialog = (DialogListDialog) dialog;
                    NBTCompound compound = new NBTCompound();
                    compound.setTag("type", new NBTString(listDialog.getType().getName().toString()));
                    CommonDialogData common = listDialog.getCommon();
                    CommonDialogData.encode(compound, wrapper, common);
                    MappedEntityRefSet<Dialog> dialogsRef = listDialog.getDialogs();
                    if (!dialogsRef.isEmpty()) {
                        NBT dialogsNbt = safeEncodeRefSet(wrapper, dialogsRef,
                                com.github.retrooper.packetevents.protocol.dialog.Dialogs.getRegistry());
                        compound.setTag("dialogs", dialogsNbt);
                    }
                    ActionButton exitAction = listDialog.getExitAction();
                    if (exitAction != null) {
                        compound.set("exit_action", exitAction, ActionButton::encode, wrapper);
                    }
                    if (listDialog.getColumns() != 2) {
                        compound.setTag("columns", new NBTInt(listDialog.getColumns()));
                    }
                    if (listDialog.getButtonWidth() != 150) {
                        compound.setTag("button_width", new NBTInt(listDialog.getButtonWidth()));
                    }
                    return compound;
                }
                // fallback to direct for other dialog types
                return Dialog.encodeDirect(dialog, wrapper);
            } catch (Throwable ignored) {}
            return new NBTCompound();
        }
    }

    private static NBT encodeCatVariantSafe(PacketWrapper<?> wrapper, CatVariant value) {
        try {
            NBTCompound compound = new NBTCompound();
            // PE 2.13.0 bug: isNewerThan instead of isNewerThanOrEquals for 26.1, so we always include baby_asset_id for 26.1+
            compound.set("asset_id", value.getAssetId(), ResourceLocation.CODEC, wrapper);
            if (wrapper.getServerVersion().isNewerThanOrEquals(ServerVersion.V_26_1)) {
                compound.set("baby_asset_id", value.getBabyAssetId(), ResourceLocation.CODEC, wrapper);
            } else {
                // use PE codec for older versions
                return CatVariant.CODEC.encode(wrapper, value);
            }
            return compound;
        } catch (Throwable t) {
            try {
                return CatVariant.CODEC.encode(wrapper, value);
            } catch (Throwable t2) {
                NBTCompound c = new NBTCompound();
                try {
                    c.setTag("asset_id", new NBTString(value.getAssetId().toString()));
                    c.setTag("baby_asset_id", new NBTString(value.getBabyAssetId().toString()));
                } catch (Throwable ignored) {}
                return c;
            }
        }
    }

    private static NBT encodeChickenVariantSafe(PacketWrapper<?> wrapper, ChickenVariant value) {
        try {
            NBTCompound compound = new NBTCompound();
            compound.set("model", value.getModelType(), ChickenVariant.ModelType.CODEC, wrapper);
            compound.set("asset_id", value.getAssetId(), ResourceLocation.CODEC, wrapper);
            if (wrapper.getServerVersion().isNewerThanOrEquals(ServerVersion.V_26_1)) {
                compound.set("baby_asset_id", value.getBabyAssetId(), ResourceLocation.CODEC, wrapper);
            }
            return compound;
        } catch (Throwable t) {
            try {
                return ChickenVariant.CODEC.encode(wrapper, value);
            } catch (Throwable t2) {
                return new NBTCompound();
            }
        }
    }

    private static NBT encodeCowVariantSafe(PacketWrapper<?> wrapper, CowVariant value) {
        try {
            NBTCompound compound = new NBTCompound();
            compound.set("model", value.getModelType(), CowVariant.ModelType.CODEC, wrapper);
            compound.set("asset_id", value.getAssetId(), ResourceLocation.CODEC, wrapper);
            if (wrapper.getServerVersion().isNewerThanOrEquals(ServerVersion.V_26_1)) {
                compound.set("baby_asset_id", value.getBabyAssetId(), ResourceLocation.CODEC, wrapper);
            }
            return compound;
        } catch (Throwable t) {
            try {
                return CowVariant.CODEC.encode(wrapper, value);
            } catch (Throwable t2) {
                return new NBTCompound();
            }
        }
    }

    private static NBT encodePigVariantSafe(PacketWrapper<?> wrapper, PigVariant value) {
        try {
            NBTCompound compound = new NBTCompound();
            compound.set("model", value.getModelType(), PigVariant.ModelType.CODEC, wrapper);
            compound.set("asset_id", value.getAssetId(), ResourceLocation.CODEC, wrapper);
            if (wrapper.getServerVersion().isNewerThanOrEquals(ServerVersion.V_26_1)) {
                compound.set("baby_asset_id", value.getBabyAssetId(), ResourceLocation.CODEC, wrapper);
            }
            return compound;
        } catch (Throwable t) {
            try {
                return PigVariant.CODEC.encode(wrapper, value);
            } catch (Throwable t2) {
                return new NBTCompound();
            }
        }
    }

    private static int getSoundVariantId(String registryKey, ResourceLocation id, ClientVersion version) {
        try {
            switch (registryKey) {
                case "minecraft:cat_sound_variant": {
                    com.github.retrooper.packetevents.protocol.entity.cat.CatSoundVariant v =
                            com.github.retrooper.packetevents.protocol.entity.cat.CatSoundVariants.getRegistry().getByName(version, id);
                    return v != null ? v.getId(version) : Integer.MAX_VALUE;
                }
                case "minecraft:cow_sound_variant": {
                    com.github.retrooper.packetevents.protocol.entity.cow.CowSoundVariant v =
                            com.github.retrooper.packetevents.protocol.entity.cow.CowSoundVariants.getRegistry().getByName(version, id);
                    return v != null ? v.getId(version) : Integer.MAX_VALUE;
                }
                case "minecraft:pig_sound_variant": {
                    com.github.retrooper.packetevents.protocol.entity.pig.PigSoundVariant v =
                            com.github.retrooper.packetevents.protocol.entity.pig.PigSoundVariants.getRegistry().getByName(version, id);
                    return v != null ? v.getId(version) : Integer.MAX_VALUE;
                }
                case "minecraft:chicken_sound_variant": {
                    com.github.retrooper.packetevents.protocol.entity.chicken.ChickenSoundVariant v =
                            com.github.retrooper.packetevents.protocol.entity.chicken.ChickenSoundVariants.getRegistry().getByName(version, id);
                    return v != null ? v.getId(version) : Integer.MAX_VALUE;
                }
                default:
                    return Integer.MAX_VALUE;
            }
        } catch (Throwable ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private static NBT tryEncodeSoundVariant(String registryKey, ResourceLocation id, ClientVersion version) {
        try {
            PacketWrapper<?> wrapper = PacketWrapper.createDummyWrapper(version);
            switch (registryKey) {
                case "minecraft:cat_sound_variant": {
                    com.github.retrooper.packetevents.protocol.entity.cat.CatSoundVariant v =
                            com.github.retrooper.packetevents.protocol.entity.cat.CatSoundVariants.getRegistry().getByName(version, id);
                    if (v != null) {
                        return com.github.retrooper.packetevents.protocol.entity.cat.CatSoundVariant.CODEC.encode(wrapper, v);
                    }
                    break;
                }
                case "minecraft:cow_sound_variant": {
                    com.github.retrooper.packetevents.protocol.entity.cow.CowSoundVariant v =
                            com.github.retrooper.packetevents.protocol.entity.cow.CowSoundVariants.getRegistry().getByName(version, id);
                    if (v != null) {
                        return com.github.retrooper.packetevents.protocol.entity.cow.CowSoundVariant.CODEC.encode(wrapper, v);
                    }
                    break;
                }
                case "minecraft:pig_sound_variant": {
                    com.github.retrooper.packetevents.protocol.entity.pig.PigSoundVariant v =
                            com.github.retrooper.packetevents.protocol.entity.pig.PigSoundVariants.getRegistry().getByName(version, id);
                    if (v != null) {
                        return com.github.retrooper.packetevents.protocol.entity.pig.PigSoundVariant.CODEC.encode(wrapper, v);
                    }
                    break;
                }
                case "minecraft:chicken_sound_variant": {
                    com.github.retrooper.packetevents.protocol.entity.chicken.ChickenSoundVariant v =
                            com.github.retrooper.packetevents.protocol.entity.chicken.ChickenSoundVariants.getRegistry().getByName(version, id);
                    if (v != null) {
                        return com.github.retrooper.packetevents.protocol.entity.chicken.ChickenSoundVariant.CODEC.encode(wrapper, v);
                    }
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static <T extends MappedEntity>
    void putRegistry(Map<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> out,
                     String registryName,
                     VersionedRegistry<T> registry,
                     ClientVersion version,
                     Encoder<T> encoder) {
        ResourceLocation key = new ResourceLocation(registryName);
        List<T> sorted = new ArrayList<>(registry.getEntries());
        sorted.removeIf(v -> v.getId(version) < 0);
        sorted.sort((a, b) -> Integer.compare(a.getId(version), b.getId(version)));
        List<WrapperConfigServerRegistryData.RegistryElement> entries = new ArrayList<>();
        for (T value : sorted) {
            try {
                NBT encoded = encoder.encode(value);
                entries.add(new WrapperConfigServerRegistryData.RegistryElement(value.getName(), encoded));
            } catch (Throwable t) {
            }
        }
        if (!entries.isEmpty()) {
            out.put(key, Collections.unmodifiableList(entries));
        }
    }

    private static <T extends MappedEntity>
    void putRegistryWithCodec(Map<ResourceLocation, List<WrapperConfigServerRegistryData.RegistryElement>> out,
                              String registryName,
                              VersionedRegistry<T> registry,
                              ClientVersion version,
                              CodecEncoder<T> encoder) {
        ResourceLocation key = new ResourceLocation(registryName);
        List<T> sorted = new ArrayList<>(registry.getEntries());
        sorted.removeIf(v -> v.getId(version) < 0);
        sorted.sort((a, b) -> Integer.compare(a.getId(version), b.getId(version)));
        List<WrapperConfigServerRegistryData.RegistryElement> entries = new ArrayList<>();
        for (T value : sorted) {
            try {
                PacketWrapper<?> wrapper = PacketWrapper.createDummyWrapper(version);
                NBT encoded = encoder.encode(wrapper, value);
                entries.add(new WrapperConfigServerRegistryData.RegistryElement(value.getName(), encoded));
            } catch (Throwable ex) {
            }
        }
        if (!entries.isEmpty()) {
            out.put(key, Collections.unmodifiableList(entries));
        }
    }

    private interface Encoder<T> {
        NBT encode(T value);
    }

    private interface CodecEncoder<T> {
        NBT encode(PacketWrapper<?> wrapper, T value) throws Exception;
    }

    private static void collectTagReferences(NBT tag, Set<ResourceLocation> out) {
        if (tag == null) return;
        if (tag instanceof NBTString) {
            String s = ((NBTString) tag).getValue();
            if (s.startsWith("#")) {
                String candidate = s.substring(1);
                if (candidate.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                    try {
                        out.add(new ResourceLocation(candidate));
                    } catch (Exception ignored) {
                    }
                }
            }
        } else if (tag instanceof NBTCompound) {
            NBTCompound compound = (NBTCompound) tag;
            for (NBT value : compound.getTags().values()) {
                collectTagReferences(value, out);
            }
        } else if (tag instanceof NBTList) {
            NBTList<?> list = (NBTList<?>) tag;
            for (int i = 0; i < list.size(); i++) {
                collectTagReferences(list.getTag(i), out);
            }
        }
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
