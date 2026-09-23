package net.vibmc.network.packetevents;

import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTags;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vanilla tags sourced from PacketEvents where available, plus required dynamic placeholders.
 * <p>
 * Rewritten to use {@code BlockTags.getByName} and {@code ItemTags.getByName} instead of reflection
 * over private {@code byName} maps or public static fields. Fluid tags {@code minecraft:water}
 * and {@code minecraft:lava} are kept as minimal vanilla constants because neither PacketEvents
 * nor ViaVersion mappings expose fluid id membership.
 */
public final class PacketEventsTags {
    private PacketEventsTags() {}

    public static WrapperPlayServerTags create(ClientVersion version) {
        if (version.isOlderThan(ClientVersion.V_1_13)) {
            throw new IllegalArgumentException("Registry tags do not exist before Minecraft 1.13");
        }
        return new WrapperPlayServerTags(tagMap(version, Collections.emptySet()));
    }

    public static Map<ResourceLocation, List<WrapperPlayServerTags.Tag>> tagMap(
            ClientVersion version, Set<ResourceLocation> referencedTags) {
        return tagMap(version, referencedTags, Collections.emptyMap());
    }

    public static Map<ResourceLocation, List<WrapperPlayServerTags.Tag>> tagMap(
            ClientVersion version, Set<ResourceLocation> referencedTags,
            Map<ResourceLocation, Set<ResourceLocation>> tagsByRegistry) {

        Map<ResourceLocation, List<WrapperPlayServerTags.Tag>> registries = new LinkedHashMap<>();
        registries.put(ResourceLocation.minecraft("block"), new ArrayList<>());
        registries.put(ResourceLocation.minecraft("item"), new ArrayList<>());
        // Minimal vanilla fluid constants – no data source provides these ids in either PE or ViaVersion
        registries.put(ResourceLocation.minecraft("fluid"), new ArrayList<>(Arrays.asList(
                new WrapperPlayServerTags.Tag("minecraft:water", Arrays.asList(1, 2)),
                new WrapperPlayServerTags.Tag("minecraft:lava", Arrays.asList(3, 4)))));
        registries.put(ResourceLocation.minecraft("entity_type"), new ArrayList<>());
        registries.put(ResourceLocation.minecraft("game_event"), new ArrayList<>());

        if (version.isNewerThanOrEquals(ClientVersion.V_1_21_2)) {
            // Resolve block/item/entity_type tags that are actually referenced, using getByName (no reflection)
            for (ResourceLocation tag : referencedTags) {
                String rawName = tag.toString();
                String bare = rawName.startsWith("minecraft:") ? rawName.substring("minecraft:".length()) : rawName;

                // Block tags
                com.github.retrooper.packetevents.protocol.world.states.defaulttags.BlockTags blockTag =
                        com.github.retrooper.packetevents.protocol.world.states.defaulttags.BlockTags.getByName(bare);
                if (blockTag != null) {
                    List<Integer> ids = new ArrayList<>();
                    for (StateType state : blockTag.getStates()) {
                        int id = state.getMapped().getId(version);
                        if (id >= 0) ids.add(id);
                    }
                    addOrReplace(registries.get(ResourceLocation.minecraft("block")), new WrapperPlayServerTags.Tag(tag, ids));
                    continue;
                }

                // Item tags – ItemTags.getByName is instance method, use any known instance (e.g. WOOL)
                com.github.retrooper.packetevents.protocol.world.states.defaulttags.ItemTags itemTag =
                        com.github.retrooper.packetevents.protocol.world.states.defaulttags.ItemTags.WOOL.getByName(bare);
                if (itemTag != null) {
                    List<Integer> ids = new ArrayList<>();
                    for (ItemType item : itemTag.getStates()) {
                        int id = item.getId(version);
                        if (id >= 0) ids.add(id);
                    }
                    addOrReplace(registries.get(ResourceLocation.minecraft("item")), new WrapperPlayServerTags.Tag(tag, ids));
                    continue;
                }

                // Entity type tags – resolved from hardcoded vanilla definitions
                if (ENTITY_TYPE_TAGS.containsKey(tag.toString()) || ENTITY_TYPE_TAGS.containsKey("minecraft:" + bare)) {
                    List<Integer> ids = resolveEntityTypeTag(tag.toString(), version, new HashSet<>());
                    addOrReplace(registries.get(ResourceLocation.minecraft("entity_type")), new WrapperPlayServerTags.Tag(tag, ids));
                    continue;
                }
                // Also try bare name without namespace for entity tags map that uses full keys
                String fullKey = tag.toString().contains(":") ? tag.toString() : "minecraft:" + bare;
                if (ENTITY_TYPE_TAGS.containsKey(fullKey)) {
                    List<Integer> ids = resolveEntityTypeTag(fullKey, version, new HashSet<>());
                    addOrReplace(registries.get(ResourceLocation.minecraft("entity_type")), new WrapperPlayServerTags.Tag(tag, ids));
                    continue;
                }

                // For other registries, add empty placeholder in each possible registry
                for (String registryName : Arrays.asList("block", "item", "fluid", "entity_type", "enchantment", "damage_type")) {
                    List<WrapperPlayServerTags.Tag> values = registries.computeIfAbsent(
                            ResourceLocation.minecraft(registryName), ignored -> new ArrayList<>());
                    addIfAbsent(values, tag, Collections.emptyList());
                }
            }

            // Ensure all known entity_type tags are present for modern clients (1.21.2+)
            // Vanilla clients validate that tags referenced in enchantment predicates exist,
            // even if our NBT scanner missed them due to codec differences. Providing the full
            // vanilla set prevents \"Missing tag: 'minecraft:arrows' in 'minecraft:entity_type'\".
            List<WrapperPlayServerTags.Tag> entityTypes = registries.computeIfAbsent(
                    ResourceLocation.minecraft("entity_type"), ignored -> new ArrayList<>());
            for (Map.Entry<String, List<String>> entry : ENTITY_TYPE_TAGS.entrySet()) {
                ResourceLocation key = new ResourceLocation(entry.getKey());
                // Only add if not already present or if present was empty placeholder and we have real ids
                boolean already = false;
                for (WrapperPlayServerTags.Tag existing : entityTypes) {
                    if (existing.getKey().equals(key)) {
                        already = true;
                        // Replace empty placeholder with real ids if we have them
                        if (existing.getValues().isEmpty()) {
                            List<Integer> realIds = resolveEntityTypeTag(entry.getKey(), version, new HashSet<>());
                            if (!realIds.isEmpty()) {
                                // replace
                                for (int i = 0; i < entityTypes.size(); i++) {
                                    if (entityTypes.get(i).getKey().equals(key)) {
                                        entityTypes.set(i, new WrapperPlayServerTags.Tag(key, realIds));
                                        break;
                                    }
                                }
                            }
                        }
                        break;
                    }
                }
                if (!already) {
                    List<Integer> ids = resolveEntityTypeTag(entry.getKey(), version, new HashSet<>());
                    // Even if ids empty (entity not in this version), we still add placeholder so tag is considered present
                    // – vanilla allows empty tags, but missing tag causes crash.
                    addIfAbsent(entityTypes, key, ids);
                }
            }

            // Registry-local references (dialog, timeline, etc.) must be bound in their own dynamic registry
            for (Map.Entry<ResourceLocation, Set<ResourceLocation>> registry : tagsByRegistry.entrySet()) {
                List<WrapperPlayServerTags.Tag> values = registries.computeIfAbsent(
                        registry.getKey(), ignored -> new ArrayList<>());
                for (ResourceLocation tag : registry.getValue()) {
                    addIfAbsent(values, tag, Collections.emptyList());
                }
            }

            // Dialog and timeline tags for 1.21.11+ – no data source in PE or ViaVersion provides tag membership,
            // but client requires these tags to bind holder sets. They are also discovered via scanning
            // dialog registry (pause_screen_additions, quick_actions), but we ensure minimal placeholders
            // for robustness when PE's DimensionTypes lack timeline references.
            if (version.isNewerThanOrEquals(ClientVersion.V_1_21_11)) {
                List<WrapperPlayServerTags.Tag> dialogs = registries.computeIfAbsent(
                        new ResourceLocation("minecraft:dialog"), ignored -> new ArrayList<>());
                addIfAbsent(dialogs, new ResourceLocation("minecraft:pause_screen_additions"), Collections.emptyList());
                addIfAbsent(dialogs, new ResourceLocation("minecraft:quick_actions"), Collections.emptyList());

                List<WrapperPlayServerTags.Tag> timelines = registries.computeIfAbsent(
                        new ResourceLocation("minecraft:timeline"), ignored -> new ArrayList<>());
                addIfAbsent(timelines, new ResourceLocation("minecraft:in_overworld"), Collections.emptyList());
                addIfAbsent(timelines, new ResourceLocation("minecraft:in_nether"), Collections.emptyList());
                addIfAbsent(timelines, new ResourceLocation("minecraft:in_end"), Collections.emptyList());
            }

            // Damage-type and banner-pattern tags for 26.1+ – no data source in PE or ViaVersion provides
            // membership for these, but 26.1's fire-resistant component initializer and banner logic require them.
            if (version.isNewerThanOrEquals(ClientVersion.V_26_1)) {
                List<WrapperPlayServerTags.Tag> damageTypes = registries.computeIfAbsent(
                        new ResourceLocation("minecraft:damage_type"), ignored -> new ArrayList<>());
                addIfAbsent(damageTypes, new ResourceLocation("minecraft:is_fire"), Collections.emptyList());
                addIfAbsent(damageTypes, new ResourceLocation("minecraft:is_explosion"), Collections.emptyList());
                addIfAbsent(damageTypes, new ResourceLocation("minecraft:bypasses_shield"), Collections.emptyList());

                List<WrapperPlayServerTags.Tag> bannerPatterns = registries.computeIfAbsent(
                        new ResourceLocation("minecraft:banner_pattern"), ignored -> new ArrayList<>());
                addBannerPatternTag(bannerPatterns, version, "no_item_required",
                        "square_bottom_left", "square_bottom_right", "square_top_left", "square_top_right",
                        "stripe_bottom", "stripe_top", "stripe_left", "stripe_right", "stripe_center",
                        "stripe_middle", "stripe_downright", "stripe_downleft", "small_stripes", "cross",
                        "straight_cross", "triangle_bottom", "triangle_top", "triangles_bottom", "triangles_top",
                        "diagonal_left", "diagonal_up_right", "diagonal_up_left", "diagonal_right", "circle",
                        "rhombus", "half_vertical", "half_horizontal", "half_vertical_right",
                        "half_horizontal_bottom", "border", "gradient", "gradient_up");
                addBannerPatternTag(bannerPatterns, version, "pattern_item/bordure_indented", "curly_border");
                addBannerPatternTag(bannerPatterns, version, "pattern_item/creeper", "creeper");
                addBannerPatternTag(bannerPatterns, version, "pattern_item/field_masoned", "bricks");
                addBannerPatternTag(bannerPatterns, version, "pattern_item/flow", "flow");
                addBannerPatternTag(bannerPatterns, version, "pattern_item/flower", "flower");
                addBannerPatternTag(bannerPatterns, version, "pattern_item/globe", "globe");
                addBannerPatternTag(bannerPatterns, version, "pattern_item/guster", "guster");
                addBannerPatternTag(bannerPatterns, version, "pattern_item/mojang", "mojang");
                addBannerPatternTag(bannerPatterns, version, "pattern_item/piglin", "piglin");
                addBannerPatternTag(bannerPatterns, version, "pattern_item/skull", "skull");
            }
        }

        Map<ResourceLocation, List<WrapperPlayServerTags.Tag>> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, List<WrapperPlayServerTags.Tag>> entry : registries.entrySet()) {
            result.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    private static void addBannerPatternTag(List<WrapperPlayServerTags.Tag> tags, ClientVersion version,
                                            String tagName, String... patternNames) {
        List<Integer> ids = new ArrayList<>();
        for (String patternName : patternNames) {
            com.github.retrooper.packetevents.protocol.item.banner.BannerPattern pattern =
                    com.github.retrooper.packetevents.protocol.item.banner.BannerPatterns.getRegistry().getByName(
                            version, new ResourceLocation("minecraft:" + patternName));
            if (pattern != null) {
                int id = pattern.getId(version);
                if (id >= 0) ids.add(id);
            }
        }
        ResourceLocation key = new ResourceLocation("minecraft:" + tagName);
        for (int i = 0; i < tags.size(); i++) {
            if (tags.get(i).getKey().equals(key)) {
                tags.set(i, new WrapperPlayServerTags.Tag(key, ids));
                return;
            }
        }
        tags.add(new WrapperPlayServerTags.Tag(key, ids));
    }

    private static void addIfAbsent(List<WrapperPlayServerTags.Tag> tags, ResourceLocation name, List<Integer> ids) {
        for (WrapperPlayServerTags.Tag existing : tags) {
            if (existing.getKey().equals(name)) return;
        }
        tags.add(new WrapperPlayServerTags.Tag(name, ids));
    }

    private static void addOrReplace(List<WrapperPlayServerTags.Tag> tags, WrapperPlayServerTags.Tag newTag) {
        for (int i = 0; i < tags.size(); i++) {
            if (tags.get(i).getKey().equals(newTag.getKey())) {
                tags.set(i, newTag);
                return;
            }
        }
        tags.add(newTag);
    }

    // ---- entity_type tags (vanilla definitions, required for 1.21+ enchantment predicates) ----

    private static final Map<String, List<String>> ENTITY_TYPE_TAGS;

    static {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("minecraft:accepts_iron_golem_gift", Arrays.asList("minecraft:copper_golem"));
        map.put("minecraft:aquatic", Arrays.asList("minecraft:axolotl","minecraft:cod","minecraft:dolphin","minecraft:elder_guardian","minecraft:glow_squid","minecraft:guardian","minecraft:nautilus","minecraft:pufferfish","minecraft:salmon","minecraft:squid","minecraft:tadpole","minecraft:tropical_fish","minecraft:turtle","minecraft:zombie_nautilus"));
        map.put("minecraft:arrows", Arrays.asList("minecraft:arrow","minecraft:spectral_arrow"));
        map.put("minecraft:arthropod", Arrays.asList("minecraft:bee","minecraft:cave_spider","minecraft:endermite","minecraft:silverfish","minecraft:spider"));
        map.put("minecraft:axolotl_always_hostiles", Arrays.asList("minecraft:drowned","minecraft:elder_guardian","minecraft:guardian"));
        map.put("minecraft:axolotl_hunt_targets", Arrays.asList("minecraft:cod","minecraft:glow_squid","minecraft:pufferfish","minecraft:salmon","minecraft:squid","minecraft:tadpole","minecraft:tropical_fish"));
        map.put("minecraft:beehive_inhabitors", Arrays.asList("minecraft:bee"));
        map.put("minecraft:boats", Arrays.asList("minecraft:oak_boat","minecraft:spruce_boat","minecraft:birch_boat","minecraft:jungle_boat","minecraft:acacia_boat","minecraft:dark_oak_boat","minecraft:mangrove_boat","minecraft:cherry_boat","minecraft:bamboo_raft","minecraft:pale_oak_boat","minecraft:oak_chest_boat","minecraft:spruce_chest_boat","minecraft:birch_chest_boat","minecraft:jungle_chest_boat","minecraft:acacia_chest_boat","minecraft:dark_oak_chest_boat","minecraft:mangrove_chest_boat","minecraft:cherry_chest_boat","minecraft:bamboo_chest_raft","minecraft:pale_oak_chest_boat"));
        map.put("minecraft:burn_in_daylight", Arrays.asList("minecraft:skeleton","minecraft:stray","minecraft:zombie","minecraft:zombie_villager","minecraft:drowned","minecraft:husk","minecraft:phantom","minecraft:skeleton_horse","minecraft:zombie_horse","minecraft:bogged"));
        map.put("minecraft:can_breathe_under_water", Arrays.asList("minecraft:axolotl","minecraft:cod","minecraft:dolphin","minecraft:elder_guardian","minecraft:glow_squid","minecraft:guardian","minecraft:nautilus","minecraft:pufferfish","minecraft:salmon","minecraft:squid","minecraft:tadpole","minecraft:tropical_fish","minecraft:turtle","minecraft:zombie_nautilus"));
        map.put("minecraft:can_equip_harness", Arrays.asList("minecraft:happy_ghast"));
        map.put("minecraft:can_equip_saddle", Arrays.asList("minecraft:horse","minecraft:donkey","minecraft:mule","minecraft:camel","minecraft:skeleton_horse","minecraft:zombie_horse","minecraft:pig","minecraft:strider"));
        map.put("minecraft:can_float_while_ridden", Arrays.asList("minecraft:horse","minecraft:skeleton_horse","minecraft:zombie_horse","minecraft:donkey","minecraft:mule","minecraft:camel"));
        map.put("minecraft:can_turn_in_boats", Arrays.asList("minecraft:chicken","minecraft:parrot"));
        map.put("minecraft:can_wear_horse_armor", Arrays.asList("minecraft:horse"));
        map.put("minecraft:can_wear_nautilus_armor", Arrays.asList("minecraft:nautilus"));
        map.put("minecraft:candidate_for_iron_golem_gift", Arrays.asList("minecraft:copper_golem"));
        map.put("minecraft:deflects_projectiles", Arrays.asList("minecraft:breeze"));
        map.put("minecraft:dismounts_underwater", Arrays.asList("minecraft:camel","minecraft:chicken","minecraft:donkey","minecraft:horse","minecraft:llama","minecraft:mule","minecraft:pig","minecraft:ravager","minecraft:spider","minecraft:strider","minecraft:trader_llama","minecraft:zombie_horse"));
        map.put("minecraft:fall_damage_immune", Arrays.asList("minecraft:iron_golem","minecraft:snow_golem","minecraft:shulker","minecraft:allay","minecraft:bat","minecraft:bee","minecraft:blaze","minecraft:cat","minecraft:chicken","minecraft:ghast","minecraft:phantom","minecraft:magma_cube","minecraft:ocelot","minecraft:parrot","minecraft:wither","minecraft:breeze","minecraft:happy_ghast","minecraft:ghastling"));
        map.put("minecraft:followable_friendly_mobs", Arrays.asList("minecraft:allay","minecraft:bee","minecraft:camel","minecraft:cat","minecraft:chicken","minecraft:cow","minecraft:donkey","minecraft:fox","minecraft:frog","minecraft:goat","minecraft:horse","minecraft:llama","minecraft:mooshroom","minecraft:mule","minecraft:ocelot","minecraft:panda","minecraft:parrot","minecraft:pig","minecraft:rabbit","minecraft:sheep","minecraft:trader_llama","minecraft:turtle","minecraft:wolf"));
        map.put("minecraft:freeze_hurts_extra_types", Arrays.asList("minecraft:strider","minecraft:blaze","minecraft:magma_cube"));
        map.put("minecraft:freeze_immune_entity_types", Arrays.asList("minecraft:stray","minecraft:polar_bear","minecraft:snow_golem","minecraft:wither"));
        map.put("minecraft:frog_food", Arrays.asList("minecraft:slime","minecraft:magma_cube"));
        map.put("minecraft:ignores_poison_and_regen", Arrays.asList("minecraft:zombie","minecraft:zombie_villager","minecraft:husk","minecraft:drowned","minecraft:skeleton","minecraft:stray","minecraft:wither_skeleton","minecraft:zombified_piglin","minecraft:skeleton_horse","minecraft:zombie_horse","minecraft:wither","minecraft:warden","minecraft:iron_golem","minecraft:snow_golem"));
        map.put("minecraft:illager", Arrays.asList("minecraft:vindicator","minecraft:evoker","minecraft:pillager","minecraft:ravager","minecraft:illusioner","minecraft:witch","minecraft:vex"));
        map.put("minecraft:illager_friends", Arrays.asList("minecraft:ravager","minecraft:vex"));
        map.put("minecraft:immune_to_infested", Arrays.asList("minecraft:silverfish"));
        map.put("minecraft:immune_to_oozing", Arrays.asList("minecraft:slime"));
        map.put("minecraft:impact_projectiles", Arrays.asList("#minecraft:arrows","minecraft:breeze_wind_charge","minecraft:dragon_fireball","minecraft:egg","minecraft:fireball","minecraft:firework_rocket","minecraft:small_fireball","minecraft:snowball","minecraft:trident","minecraft:wind_charge"));
        map.put("minecraft:inverted_healing_and_harm", Arrays.asList("minecraft:zombie","minecraft:zombie_villager","minecraft:husk","minecraft:drowned","minecraft:zombified_piglin","minecraft:skeleton","minecraft:stray","minecraft:wither_skeleton","minecraft:skeleton_horse","minecraft:zombie_horse","minecraft:wither","minecraft:phantom"));
        map.put("minecraft:nautilus_hostiles", Arrays.asList("minecraft:drowned","minecraft:guardian","minecraft:elder_guardian"));
        map.put("minecraft:no_anger_from_wind_charge", Arrays.asList("minecraft:breeze"));
        map.put("minecraft:non_controlling_rider", Arrays.asList("minecraft:slime","minecraft:magma_cube"));
        map.put("minecraft:not_affected_by_geysers", Arrays.asList("minecraft:ender_dragon","minecraft:wither"));
        map.put("minecraft:not_scary_for_pufferfish", Arrays.asList("minecraft:turtle","minecraft:pufferfish"));
        map.put("minecraft:powder_snow_walkable_mobs", Arrays.asList("minecraft:rabbit","minecraft:endermite","minecraft:silverfish","minecraft:fox"));
        map.put("minecraft:raiders", Arrays.asList("minecraft:evoker","minecraft:pillager","minecraft:ravager","minecraft:vindicator","minecraft:illusioner","minecraft:witch"));
        map.put("minecraft:redirectable_projectile", Arrays.asList("minecraft:breeze_wind_charge","minecraft:fireball","minecraft:wind_charge"));
        map.put("minecraft:sensitive_to_bane_of_arthropods", Arrays.asList("#minecraft:arthropod"));
        map.put("minecraft:sensitive_to_impaling", Arrays.asList("#minecraft:aquatic"));
        map.put("minecraft:sensitive_to_smite", Arrays.asList("#minecraft:undead"));
        map.put("minecraft:skeletons", Arrays.asList("minecraft:skeleton","minecraft:stray","minecraft:wither_skeleton","minecraft:bogged"));
        map.put("minecraft:undead", Arrays.asList("minecraft:zombie","minecraft:zombie_villager","minecraft:husk","minecraft:drowned","minecraft:zombified_piglin","minecraft:skeleton","minecraft:stray","minecraft:wither_skeleton","minecraft:skeleton_horse","minecraft:zombie_horse","minecraft:wither","minecraft:phantom","minecraft:bogged"));
        map.put("minecraft:wither_friends", Arrays.asList("minecraft:wither_skeleton","minecraft:wither"));
        map.put("minecraft:zombies", Arrays.asList("minecraft:zombie","minecraft:zombie_villager","minecraft:husk","minecraft:drowned","minecraft:zombified_piglin"));
        // 26.2+ new tags – placeholders to avoid missing-tag crashes if referenced
        map.put("minecraft:can_equip_harness", Arrays.asList("minecraft:happy_ghast"));
        ENTITY_TYPE_TAGS = Collections.unmodifiableMap(map);
    }

    private static List<Integer> resolveEntityTypeTag(String tagKey, ClientVersion version, Set<String> visited) {
        if (tagKey == null) return Collections.emptyList();
        String normalized = tagKey.startsWith("#") ? tagKey.substring(1) : tagKey;
        if (!normalized.contains(":")) normalized = "minecraft:" + normalized;
        if (!visited.add(normalized)) {
            return Collections.emptyList(); // cycle protection
        }
        List<String> entries = ENTITY_TYPE_TAGS.get(normalized);
        if (entries == null) {
            // Try to resolve as direct entity type if not a tag
            int directId = getEntityTypeId(normalized, version);
            if (directId >= 0) return Collections.singletonList(directId);
            return Collections.emptyList();
        }
        List<Integer> result = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (String entry : entries) {
            if (entry.startsWith("#")) {
                List<Integer> nested = resolveEntityTypeTag(entry, version, visited);
                for (int id : nested) if (seen.add(id)) result.add(id);
            } else {
                int id = getEntityTypeId(entry, version);
                if (id >= 0 && seen.add(id)) result.add(id);
            }
        }
        return result;
    }

    private static int getEntityTypeId(String name, ClientVersion version) {
        try {
            String key = name;
            if (!key.contains(":")) key = "minecraft:" + key;
            com.github.retrooper.packetevents.protocol.entity.type.EntityType type =
                    com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.getRegistry()
                            .getByName(version, new ResourceLocation(key));
            if (type == null) {
                // Fallback to getByName without version (some PE versions)
                type = com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.getByName(key.substring(key.indexOf(':') + 1));
            }
            if (type == null) return -1;
            return type.getId(version);
        } catch (Throwable t) {
            return -1;
        }
    }
}
