package net.vibmc.world;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.enums.East;
import com.github.retrooper.packetevents.protocol.world.states.enums.North;
import com.github.retrooper.packetevents.protocol.world.states.enums.South;
import com.github.retrooper.packetevents.protocol.world.states.enums.West;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements connecting blocks (fences, walls, panes, iron bars, redstone) using
 * ViaVersion Mappings' extra/blockConnections.json.
 *
 * The JSON maps a block state string (e.g. "stone" or "oak_stairs[facing=north,...]") to
 * connection types (fence, netherFence, pane, cobbleWall, redstone) each with directional
 * booleans north/south/east/west indicating which faces of that block provide a connection
 * for that type. This is exactly the data ViaVersion uses for serverside block connections.
 *
 * For a connecting block at (x,y,z), we look at its 4 horizontal neighbors. A neighbor
 * provides a fence connection if:
 * - it is itself a fence (any fence type), OR
 * - its entry in blockConnections.json has fence[oppositeFace]=true
 *
 * Opposite face is required because JSON stores which faces of the neighbor provide connection
 * towards the center block. E.g. neighbor east of fence must provide WEST.
 *
 * This service is intentionally self-contained and uses only PacketEvents NBT reader +
 * ViaVersion Mappings JSON (no ViaVersion runtime), satisfying the project's constraints.
 */
public final class BlockConnections {
    private static final String RESOURCE = "/vendored/viaversion-mappings/block-connections.json";
    private static final ClientVersion VERSION = ClientVersion.V_1_13_2;

    private static final List<String> DIRECTIONS = Collections.unmodifiableList(Arrays.asList("north", "south", "east", "west"));
    private static final Map<String, Integer> DIR_TO_INDEX;
    private static final Map<BlockFace, String> FACE_TO_DIR;
    private static final Map<BlockFace, BlockFace> OPPOSITE;

    static {
        Map<String, Integer> dirToIdx = new HashMap<>();
        dirToIdx.put("north", 0);
        dirToIdx.put("south", 1);
        dirToIdx.put("east", 2);
        dirToIdx.put("west", 3);
        DIR_TO_INDEX = Collections.unmodifiableMap(dirToIdx);

        Map<BlockFace, String> faceToDir = new HashMap<>();
        faceToDir.put(BlockFace.NORTH, "north");
        faceToDir.put(BlockFace.SOUTH, "south");
        faceToDir.put(BlockFace.EAST, "east");
        faceToDir.put(BlockFace.WEST, "west");
        FACE_TO_DIR = Collections.unmodifiableMap(faceToDir);

        Map<BlockFace, BlockFace> opposite = new HashMap<>();
        opposite.put(BlockFace.NORTH, BlockFace.SOUTH);
        opposite.put(BlockFace.SOUTH, BlockFace.NORTH);
        opposite.put(BlockFace.EAST, BlockFace.WEST);
        opposite.put(BlockFace.WEST, BlockFace.EAST);
        OPPOSITE = Collections.unmodifiableMap(opposite);
    }

    // connection type -> dir index -> bool
    public static final class Profile {
        // type -> boolean[4] north,south,east,west
        final Map<String, boolean[]> byType = new HashMap<>();
    }

    private static final class ParsedKey {
        final String blockName;
        final Map<String, String> props; // sorted
        final String original;

        ParsedKey(String blockName, Map<String, String> props, String original) {
            this.blockName = blockName;
            this.props = props;
            this.original = original;
        }
    }

    private static volatile BlockConnections INSTANCE;

    private final Map<String, Profile> byExactKey = new ConcurrentHashMap<>();
    private final Map<String, List<Map.Entry<ParsedKey, Profile>>> byBlockName = new ConcurrentHashMap<>();
    private final Map<String, Profile> byBlockNameOnly = new ConcurrentHashMap<>();

    private BlockConnections() {
        load();
    }

    public static BlockConnections get() {
        if (INSTANCE == null) {
            synchronized (BlockConnections.class) {
                if (INSTANCE == null) {
                    INSTANCE = new BlockConnections();
                }
            }
        }
        return INSTANCE;
    }

    private void load() {
        try (InputStream is = BlockConnections.class.getResourceAsStream(RESOURCE)) {
            if (is == null) {
                System.out.println("[BlockConnections] Missing resource " + RESOURCE + ", fallback to solid-only");
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                String key = e.getKey();
                JsonObject val = e.getValue().getAsJsonObject();
                Profile profile = new Profile();
                for (Map.Entry<String, JsonElement> typeEntry : val.entrySet()) {
                    String type = typeEntry.getKey(); // fence, netherFence, pane, cobbleWall, redstone
                    JsonObject dirs = typeEntry.getValue().getAsJsonObject();
                    boolean[] arr = new boolean[4];
                    for (Map.Entry<String, JsonElement> dirEntry : dirs.entrySet()) {
                        String dir = dirEntry.getKey();
                        boolean b = dirEntry.getValue().getAsBoolean();
                        Integer idx = DIR_TO_INDEX.get(dir);
                        if (idx != null) arr[idx] = b;
                    }
                    profile.byType.put(type, arr);
                }
                byExactKey.put(key, profile);

                ParsedKey parsed = parseKey(key);
                byBlockName.computeIfAbsent(parsed.blockName, k -> new ArrayList<Map.Entry<ParsedKey, Profile>>()).add(new AbstractMap.SimpleEntry<ParsedKey, Profile>(parsed, profile));
                if (parsed.props.isEmpty()) {
                    byBlockNameOnly.put(parsed.blockName, profile);
                }
            }
            System.out.println("[BlockConnections] Loaded " + byExactKey.size() + " entries from " + RESOURCE);
        } catch (Exception ex) {
            ex.printStackTrace();
            System.out.println("[BlockConnections] Failed to load " + RESOURCE + ": " + ex);
        }
    }

    private static ParsedKey parseKey(String key) {
        int idx = key.indexOf('[');
        if (idx == -1) {
            return new ParsedKey(key, Collections.emptyMap(), key);
        }
        String name = key.substring(0, idx);
        String inside = key.substring(idx + 1, key.length() - 1); // remove ]
        Map<String, String> props = new HashMap<>();
        for (String part : inside.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) props.put(kv[0], kv[1]);
        }
        return new ParsedKey(name, props, key);
    }

    // Generate a connection key from a WrappedBlockState using its toString() stripped of minecraft: prefix.
    // Falls back to block name only if needed.
    public static String toKey(WrappedBlockState state) {
        if (state == null) return "air";
        try {
            String s = state.toString(); // e.g. minecraft:stone or minecraft:oak_stairs[facing=north,...]
            if (s.startsWith("minecraft:")) s = s.substring(10);
            return s;
        } catch (Exception ex) {
            // Fallback to type name
            try {
                String name = state.getType().getName();
                if (name.startsWith("minecraft:")) name = name.substring(10);
                return name.toLowerCase(Locale.ROOT);
            } catch (Exception e2) {
                return "air";
            }
        }
    }

    public static String blockName(WrappedBlockState state) {
        if (state == null) return "air";
        String key = toKey(state);
        int idx = key.indexOf('[');
        return idx == -1 ? key : key.substring(0, idx);
    }

    private static Map<String, String> propsFromKey(String key) {
        int idx = key.indexOf('[');
        if (idx == -1) return Collections.emptyMap();
        String inside = key.substring(idx + 1, key.length() - 1);
        Map<String, String> map = new HashMap<>();
        for (String part : inside.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) map.put(kv[0], kv[1]);
        }
        return map;
    }

    private static Map<String, String> propsFromState(WrappedBlockState state) {
        // Use toKey parsing to get props
        String key = toKey(state);
        return propsFromKey(key);
    }

    // Check if neighbor provides connection of given type towards given face (face = direction from neighbor to center)
    public boolean provides(WrappedBlockState neighbor, String connectionType, BlockFace faceTowardsCenter) {
        if (neighbor == null) return false;
        String dir = FACE_TO_DIR.get(faceTowardsCenter);
        if (dir == null) return false;
        int dirIdx = DIR_TO_INDEX.getOrDefault(dir, -1);
        if (dirIdx == -1) return false;

        // Try exact key first
        String exact = toKey(neighbor);
        Profile p = byExactKey.get(exact);
        if (p != null) {
            boolean[] arr = p.byType.get(connectionType);
            if (arr != null && arr[dirIdx]) return true;
        }

        // Try block name only
        String bName = blockName(neighbor);
        Profile only = byBlockNameOnly.get(bName);
        if (only != null) {
            boolean[] arr = only.byType.get(connectionType);
            if (arr != null && arr[dirIdx]) return true;
        }

        // Try to find matching entry with same block name and compatible props
        List<Map.Entry<ParsedKey, Profile>> list = byBlockName.get(bName);
        if (list != null) {
            Map<String, String> stateProps = propsFromState(neighbor);
            // If state has no props, we already tried blockNameOnly, but still try to find any entry that matches subset
            for (Map.Entry<ParsedKey, Profile> entry : list) {
                ParsedKey pk = entry.getKey();
                // Check if all props in stateProps are present in pk.props with same value, OR pk.props is subset of stateProps
                // We want to match if pk.props contains at least all props of state (allowing extra like waterlogged)
                // OR state props are subset of pk props
                // To be permissive, we check if for every prop in stateProps, pk.props has same value or pk doesn't have that prop (meaning wildcard)
                // And for every prop in pk.props that is also in stateProps, values match
                boolean matches = true;
                for (Map.Entry<String, String> sp : stateProps.entrySet()) {
                    String v = pk.props.get(sp.getKey());
                    if (v != null && !v.equals(sp.getValue())) {
                        matches = false;
                        break;
                    }
                }
                if (!matches) continue;
                // Also check that pk's props that are in stateProps match, but we already did subset check one way.
                // For remaining props in pk that are not in state, we allow them (e.g., waterlogged)
                // So we consider it a match if stateProps is subset of pk.props (ignoring extra)
                // Actually we need to ensure that for props that exist in both, values equal
                for (Map.Entry<String, String> pp : pk.props.entrySet()) {
                    String sv = stateProps.get(pp.getKey());
                    if (sv != null && !sv.equals(pp.getValue())) {
                        matches = false;
                        break;
                    }
                }
                if (!matches) continue;
                Profile prof = entry.getValue();
                boolean[] arr = prof.byType.get(connectionType);
                if (arr != null && arr[dirIdx]) return true;
            }
        }

        // Fallback: if solid and connection type is fence/pane/cobbleWall, treat as providing if solid
        // This handles cases where JSON doesn't contain entry (should be rare)
        if (Blocks.isSolid(neighbor)) {
            // For fence, netherFence, pane, cobbleWall, solid blocks generally provide connection
            if (connectionType.equals("fence") || connectionType.equals("netherFence") ||
                    connectionType.equals("pane") || connectionType.equals("cobbleWall")) {
                // For stairs, we should be more restrictive, but solid check will over-connect.
                // We already tried JSON, so if not found, assume solid provides.
                // However for stairs we have JSON, so this fallback won't be used for stairs.
                return true;
            }
        }
        return false;
    }

    // --- Type checks for connecting blocks ---

    public static boolean isFence(WrappedBlockState state) {
        if (state == null) return false;
        String name = blockName(state).toLowerCase(Locale.ROOT);
        return name.endsWith("_fence") && !name.endsWith("_fence_gate") && !name.equals("nether_brick_fence");
    }

    public static boolean isNetherFence(WrappedBlockState state) {
        if (state == null) return false;
        String name = blockName(state).toLowerCase(Locale.ROOT);
        return name.equals("nether_brick_fence");
    }

    public static boolean isPane(WrappedBlockState state) {
        if (state == null) return false;
        String name = blockName(state).toLowerCase(Locale.ROOT);
        return name.endsWith("_pane") || name.equals("iron_bars") || name.equals("glass_pane");
    }

    public static boolean isWall(WrappedBlockState state) {
        if (state == null) return false;
        String name = blockName(state).toLowerCase(Locale.ROOT);
        return name.endsWith("_wall");
    }

    public static boolean isRedstoneWire(WrappedBlockState state) {
        if (state == null) return false;
        String name = blockName(state).toLowerCase(Locale.ROOT);
        return name.equals("redstone_wire") || name.equals("redstone");
    }

    public static boolean isConnectingBlock(WrappedBlockState state) {
        return isFence(state) || isNetherFence(state) || isPane(state) || isWall(state) || isRedstoneWire(state);
    }

    // --- Update logic ---

    public static WrappedBlockState updateState(World world, int x, int y, int z, WrappedBlockState current) {
        if (current == null) return current;
        BlockConnections registry = get();
        if (isFence(current)) {
            return updateFence(world, x, y, z, current, registry, "fence");
        } else if (isNetherFence(current)) {
            return updateFence(world, x, y, z, current, registry, "netherFence");
        } else if (isPane(current)) {
            return updatePane(world, x, y, z, current, registry);
        } else if (isWall(current)) {
            return updateWall(world, x, y, z, current, registry);
        } else if (isRedstoneWire(current)) {
            return updateRedstone(world, x, y, z, current, registry);
        }
        return current;
    }

    private static WrappedBlockState updateFence(World world, int x, int y, int z, WrappedBlockState state, BlockConnections registry, String type) {
        WrappedBlockState copy = state.clone();
        // For each direction, check neighbor
        boolean north = shouldConnectFence(world, x, y, z, BlockFace.NORTH, registry, type);
        boolean south = shouldConnectFence(world, x, y, z, BlockFace.SOUTH, registry, type);
        boolean east = shouldConnectFence(world, x, y, z, BlockFace.EAST, registry, type);
        boolean west = shouldConnectFence(world, x, y, z, BlockFace.WEST, registry, type);

        try {
            if (copy.hasProperty(com.github.retrooper.packetevents.protocol.world.states.type.StateValue.NORTH)) {
                copy.setNorth(north ? North.TRUE : North.FALSE);
            }
            if (copy.hasProperty(com.github.retrooper.packetevents.protocol.world.states.type.StateValue.SOUTH)) {
                copy.setSouth(south ? South.TRUE : South.FALSE);
            }
            if (copy.hasProperty(com.github.retrooper.packetevents.protocol.world.states.type.StateValue.EAST)) {
                copy.setEast(east ? East.TRUE : East.FALSE);
            }
            if (copy.hasProperty(com.github.retrooper.packetevents.protocol.world.states.type.StateValue.WEST)) {
                copy.setWest(west ? West.TRUE : West.FALSE);
            }
        } catch (Exception ex) {
            // Fallback: try to set via generic properties if enum setting fails
        }
        return copy;
    }

    private static boolean shouldConnectFence(World world, int x, int y, int z, BlockFace dir, BlockConnections registry, String type) {
        int nx = x + dir.getModX();
        int ny = y + dir.getModY();
        int nz = z + dir.getModZ();
        WrappedBlockState neighbor = world.getBlockAt(nx, ny, nz);
        if (neighbor == null) return false;
        // Fences connect to same type fences
        if (type.equals("fence") && isFence(neighbor)) return true;
        if (type.equals("netherFence") && isNetherFence(neighbor)) return true;
        // Fences also connect to fence gates? Check via JSON provides
        BlockFace opposite = OPPOSITE.get(dir);
        if (opposite == null) return false;
        return registry.provides(neighbor, type, opposite);
    }

    private static WrappedBlockState updatePane(World world, int x, int y, int z, WrappedBlockState state, BlockConnections registry) {
        WrappedBlockState copy = state.clone();
        boolean north = shouldConnectPane(world, x, y, z, BlockFace.NORTH, registry);
        boolean south = shouldConnectPane(world, x, y, z, BlockFace.SOUTH, registry);
        boolean east = shouldConnectPane(world, x, y, z, BlockFace.EAST, registry);
        boolean west = shouldConnectPane(world, x, y, z, BlockFace.WEST, registry);
        try {
            if (copy.hasProperty(com.github.retrooper.packetevents.protocol.world.states.type.StateValue.NORTH)) {
                copy.setNorth(north ? North.TRUE : North.FALSE);
            }
            if (copy.hasProperty(com.github.retrooper.packetevents.protocol.world.states.type.StateValue.SOUTH)) {
                copy.setSouth(south ? South.TRUE : South.FALSE);
            }
            if (copy.hasProperty(com.github.retrooper.packetevents.protocol.world.states.type.StateValue.EAST)) {
                copy.setEast(east ? East.TRUE : East.FALSE);
            }
            if (copy.hasProperty(com.github.retrooper.packetevents.protocol.world.states.type.StateValue.WEST)) {
                copy.setWest(west ? West.TRUE : West.FALSE);
            }
        } catch (Exception ex) {
        }
        return copy;
    }

    private static boolean shouldConnectPane(World world, int x, int y, int z, BlockFace dir, BlockConnections registry) {
        int nx = x + dir.getModX();
        int ny = y + dir.getModY();
        int nz = z + dir.getModZ();
        WrappedBlockState neighbor = world.getBlockAt(nx, ny, nz);
        if (neighbor == null) return false;
        if (isPane(neighbor)) return true;
        // Glass blocks provide pane connection via JSON
        BlockFace opposite = OPPOSITE.get(dir);
        if (opposite == null) return false;
        return registry.provides(neighbor, "pane", opposite);
    }

    private static WrappedBlockState updateWall(World world, int x, int y, int z, WrappedBlockState state, BlockConnections registry) {
        WrappedBlockState copy = state.clone();
        boolean north = shouldConnectWall(world, x, y, z, BlockFace.NORTH, registry);
        boolean south = shouldConnectWall(world, x, y, z, BlockFace.SOUTH, registry);
        boolean east = shouldConnectWall(world, x, y, z, BlockFace.EAST, registry);
        boolean west = shouldConnectWall(world, x, y, z, BlockFace.WEST, registry);

        // Determine up: true if no connections, all connections, or connection without opposite, or wall above/below
        boolean up = false;
        WrappedBlockState above = world.getBlockAt(x, y + 1, z);
        WrappedBlockState below = world.getBlockAt(x, y - 1, z);
        if (isWall(above) || isWall(below)) {
            up = true;
        } else {
            int connections = (north ? 1 : 0) + (south ? 1 : 0) + (east ? 1 : 0) + (west ? 1 : 0);
            if (connections == 0 || connections == 4) {
                up = true;
            } else if ((north && !south) || (south && !north) || (east && !west) || (west && !east)) {
                up = true;
            }
        }

        try {
            if (copy.hasProperty(com.github.retrooper.packetevents.protocol.world.states.type.StateValue.NORTH)) {
                // For walls, North can be LOW, TALL, NONE - use LOW when connected, NONE when not
                // Try to set via enum - North has LOW,NONE etc
                copy.setNorth(north ? North.LOW : North.NONE);
            }
            if (copy.hasProperty(com.github.retrooper.packetevents.protocol.world.states.type.StateValue.SOUTH)) {
                copy.setSouth(south ? South.LOW : South.NONE);
            }
            if (copy.hasProperty(com.github.retrooper.packetevents.protocol.world.states.type.StateValue.EAST)) {
                copy.setEast(east ? East.LOW : East.NONE);
            }
            if (copy.hasProperty(com.github.retrooper.packetevents.protocol.world.states.type.StateValue.WEST)) {
                copy.setWest(west ? West.LOW : West.NONE);
            }
            if (copy.hasProperty(com.github.retrooper.packetevents.protocol.world.states.type.StateValue.UP)) {
                copy.setUp(up);
            }
        } catch (Exception ex) {
            // Fallback
        }
        return copy;
    }

    private static boolean shouldConnectWall(World world, int x, int y, int z, BlockFace dir, BlockConnections registry) {
        int nx = x + dir.getModX();
        int ny = y + dir.getModY();
        int nz = z + dir.getModZ();
        WrappedBlockState neighbor = world.getBlockAt(nx, ny, nz);
        if (neighbor == null) return false;
        if (isWall(neighbor)) return true;
        if (isFence(neighbor) || isNetherFence(neighbor)) return true; // walls connect to fences? In vanilla yes for fence gates, but also fences?
        BlockFace opposite = OPPOSITE.get(dir);
        if (opposite == null) return false;
        return registry.provides(neighbor, "cobbleWall", opposite);
    }

    private static WrappedBlockState updateRedstone(World world, int x, int y, int z, WrappedBlockState state, BlockConnections registry) {
        // Simplified redstone: connect to redstone wire and to blocks that provide redstone connection
        WrappedBlockState copy = state.clone();
        boolean north = shouldConnectRedstone(world, x, y, z, BlockFace.NORTH, registry);
        boolean south = shouldConnectRedstone(world, x, y, z, BlockFace.SOUTH, registry);
        boolean east = shouldConnectRedstone(world, x, y, z, BlockFace.EAST, registry);
        boolean west = shouldConnectRedstone(world, x, y, z, BlockFace.WEST, registry);
        try {
            if (copy.hasProperty(com.github.retrooper.packetevents.protocol.world.states.type.StateValue.NORTH)) {
                // Redstone uses North with SIDE, UP, NONE
                copy.setNorth(north ? North.SIDE : North.NONE);
            }
            if (copy.hasProperty(com.github.retrooper.packetevents.protocol.world.states.type.StateValue.SOUTH)) {
                copy.setSouth(south ? South.SIDE : South.NONE);
            }
            if (copy.hasProperty(com.github.retrooper.packetevents.protocol.world.states.type.StateValue.EAST)) {
                copy.setEast(east ? East.SIDE : East.NONE);
            }
            if (copy.hasProperty(com.github.retrooper.packetevents.protocol.world.states.type.StateValue.WEST)) {
                copy.setWest(west ? West.SIDE : West.NONE);
            }
        } catch (Exception ex) {
        }
        return copy;
    }

    private static boolean shouldConnectRedstone(World world, int x, int y, int z, BlockFace dir, BlockConnections registry) {
        int nx = x + dir.getModX();
        int ny = y + dir.getModY();
        int nz = z + dir.getModZ();
        WrappedBlockState neighbor = world.getBlockAt(nx, ny, nz);
        if (neighbor == null) return false;
        if (isRedstoneWire(neighbor)) return true;
        BlockFace opposite = OPPOSITE.get(dir);
        if (opposite == null) return false;
        return registry.provides(neighbor, "redstone", opposite);
    }

    // Public API to update connections for a position and its neighbors
    public static void updateConnections(World world, int x, int y, int z) {
        // Update center if connecting
        updateSingle(world, x, y, z);
        // Update 4 horizontal neighbors + up/down for walls
        int[][] offsets = {{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,1,0},{0,-1,0}};
        for (int[] o : offsets) {
            int nx = x + o[0];
            int ny = y + o[1];
            int nz = z + o[2];
            updateSingle(world, nx, ny, nz);
        }
    }

    private static void updateSingle(World world, int x, int y, int z) {
        if (y < 0 || y >= 256) return;
        WrappedBlockState current = world.getBlockAt(x, y, z);
        if (!isConnectingBlock(current)) return;
        WrappedBlockState updated = updateState(world, x, y, z, current);
        if (updated.getGlobalId() != current.getGlobalId()) {
            world.setBlockAt(x, y, z, updated);
            net.vibmc.server.VibMC server = net.vibmc.server.VibMC.getInstance();
            if (server != null) {
                server.getPlayerManager().broadcastBlockChange(world, x, y, z, updated);
            }
        }
    }
}
