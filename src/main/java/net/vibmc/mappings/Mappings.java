package net.vibmc.mappings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Loads the vendored ViaVersion 1.12 (protocol 340) mapping snapshot. This replaces the
 * PrismarineJS minecraft-data vendored tree and PacketEvents' internal registries.
 *
 * <p>The 1.12 block mapping is a flat combined id dict ({@code key = blockId << 4 | data})
 * whose values are {@code name[property=value,...]} strings. Item mappings are a plain
 * id-to-name dict; sounds are an indexed list.
 */
public final class Mappings {
    private static final String RESOURCE = "/vendored/via-mappings/mapping-1.12.json";
    private static Map<Integer, String> blockData = new HashMap<>();
    private static Map<String, Integer> blockBase = new LinkedHashMap<>();
    private static Map<String, Integer> blockBaseData = new LinkedHashMap<>();
    private static Map<Integer, String> items = new HashMap<>();
    private static Map<String, Integer> itemKeys = new HashMap<>();
    private static Map<String, Integer> sounds = new HashMap<>();
    private static boolean loaded;

    private Mappings() {}

    public static synchronized void load() {
        if (loaded) return;
        JsonObject root;
        try (InputStream in = Mappings.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("Missing mapping resource " + RESOURCE);
            root = com.google.gson.JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + RESOURCE, e);
        }
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("blocks").entrySet()) {
            int id = Integer.parseInt(e.getKey());
            blockData.put(id, e.getValue().getAsString());
        }
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("items").entrySet()) {
            int key = Integer.parseInt(e.getKey());
            String name = e.getValue().getAsString();
            items.put(key, name);
            itemKeys.put(name, key);
        }
        JsonArray soundList = root.getAsJsonArray("sounds");
        for (int i = 0; i < soundList.size(); i++) sounds.put(soundList.get(i).getAsString(), i);
        loaded = true;
    }

    private static String nameOf(int combined) {
        String value = blockData.get(combined);
        return value == null ? "air" : value;
    }

    /** The semantic block name for the given 1.12 combined id, without properties. */
    public static String blockName(int combined) {
        String name = nameOf(combined);
        int bracket = name.indexOf('[');
        return bracket < 0 ? name : name.substring(0, bracket);
    }

    /** Whether the combined id exists in the 1.12 block mapping. */
    public static boolean hasBlock(int combined) {
        return blockData.containsKey(combined);
    }

    /**
     * The base combined id for a block name: the block's wire id shifted left by four. The
     * data nibble is zero, even when (as with a chest or torch) that exact combined id is not
     * present in the mapping; {@link #defaultData} supplies the data nibble that is present.
     */
    public static int blockId(String name) {
        return blockBaseId(name) << 4;
    }

    /** Combined id for a block name with an explicit data nibble. */
    public static int blockId(String name, int data) {
        return (blockBaseId(name) << 4) | (data & 0xF);
    }

    /**
     * The data nibble of the lowest present combined id for the name: {@code chest -> 2} (a
     * facing belongs to every chest state), {@code torch -> 5}, {@code water -> 0}.
     */
    public static int defaultData(String name) {
        if (name == null || name.isEmpty()) return 0;
        Integer cached = blockBaseData.get(name);
        if (cached != null) return cached;
        int best = -1;
        int bestData = 0;
        for (Map.Entry<Integer, String> e : blockData.entrySet()) {
            int combined = e.getKey();
            int data = combined & 0xF;
            if (!blockName(combined).equals(name)) continue;
            if (best < 0 || combined < best) {
                best = combined;
                bestData = data;
            }
        }
        if (best < 0) return 0;
        blockBaseData.put(name, bestData);
        return bestData;
    }

    private static int blockBaseId(String name) {
        Integer base = blockBase.get(name);
        if (base != null) return base;
        int found = -1;
        for (Map.Entry<Integer, String> e : blockData.entrySet()) {
            int combined = e.getKey();
            if (!blockName(combined).equals(name)) continue;
            int id = combined >> 4;
            if (id == 0) continue;
            if (found < 0 || id < found) found = id;
        }
        if (found < 0) return 0;
        blockBase.put(name, found);
        return found;
    }

    /** The block's base name for the given type, resolving stored ids without properties. */
    public static boolean isBlock(String name) {
        return blockBaseId(name) != 0 || blockData.containsKey(0);
    }

    public static Collection<String> blockNames() {
        Set<String> names = Collections.newSetFromMap(new HashMap<>());
        for (String value : blockData.values()) names.add(value.substring(0, value.indexOf('[') < 0 ? value.length() : value.indexOf('[')));
        return names;
    }

    /**
     * The 1.12 wire item id for the given item name, or -1 when unknown. Item mapping keys
     * encode the id and damage together ({@code key = wireId << 4 | damage}), so the wire id
     * is the key shifted right.
     */
    public static int itemId(String name) {
        Integer key = itemKeys.get(name);
        return key == null ? -1 : key >> 4;
    }

    /** Item wire id for the given name, falling back to 0 (air) when unknown. */
    public static int itemIdOrAir(String name) {
        Integer key = itemKeys.get(name);
        return key == null ? 0 : key >> 4;
    }

    /**
     * The damage value that must accompany the wire id to represent the named variant, e.g.
     * {@code lapis_lazuli -> 4} but {@code torch -> 0}. Defaults to zero for unknown names.
     */
    public static int itemDamage(String name) {
        Integer key = itemKeys.get(name);
        return key == null ? 0 : key & 0xF;
    }

    /** Item name at the given wire id with a specific damage, or air when unknown. */
    public static String itemName(int id, int damage) {
        String name = items.get(((id & 0xFFFF) << 4) | (damage & 0xF));
        return name == null ? "air" : name;
    }

    /** Item name at the given wire id with its default (damage 0) variant. */
    public static String itemName(int id) {
        return itemName(id, 0);
    }

    /** The combined key ({@code wireId << 4 | damage}) for the given item name, or -1. */
    public static int itemKey(String name) {
        Integer key = itemKeys.get(name);
        return key == null ? -1 : key;
    }

    public static boolean hasItem(String name) {
        return itemKeys.containsKey(name);
    }

    public static int soundId(String name) {
        Integer id = sounds.get(name);
        return id == null ? -1 : id;
    }

    public static boolean isLoaded() {
        return loaded;
    }
}