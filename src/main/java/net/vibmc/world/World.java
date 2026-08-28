package net.vibmc.world;

import net.vibmc.entity.Entity;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import net.vibmc.world.storage.WorldStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class World {
    /** Widest the expanding dry-spawn search will look before building land instead. */
    private static final int MAX_DRY_SPAWN_RADIUS = 4096;

    private final long seed;
    private final String name;
    private final ChunkManager chunkManager;
    private final TimeSystem timeSystem;
    private final WeatherSystem weatherSystem;
    private final WorldStorage storage;
    private final WorldEnvironment environment;
    private final BlockUpdates blockUpdates = new BlockUpdates(this);
    private final BlockEntities blockEntities = new BlockEntities();
    private final List<Entity> entities = new CopyOnWriteArrayList<>();
    private volatile long worldTime;

    public World(long seed, String name) {
        this(seed, name, new WorldStorage(name), WorldEnvironment.OVERWORLD);
    }

    public World(long seed, String name, WorldStorage storage) {
        this(seed, name, storage, WorldEnvironment.OVERWORLD);
    }

    public World(long seed, String name, WorldStorage storage, WorldEnvironment environment) {
        this.seed = seed;
        this.name = name;
        this.storage = storage;
        this.environment = environment;
        this.timeSystem = new TimeSystem();
        this.weatherSystem = new WeatherSystem();
        this.chunkManager = new ChunkManager(this, storage);
    }

    public WorldChunk chunk(int chunkX, int chunkZ) {
        return chunkManager.getChunk(chunkX, chunkZ);
    }

    public WorldChunk getChunk(int chunkX, int chunkZ) {
        return chunk(chunkX, chunkZ);
    }

    public void tick(long tick) {
        worldTime++;
        timeSystem.tick();
        blockUpdates.tick(tick);
        blockEntities.tick(this);
        if (tick % 100 == 0) {
            weatherSystem.tick();
        }
        for (Entity entity : new ArrayList<>(entities)) {
            entity.tick();
        }
    }

    public void addEntity(Entity entity) {
        entities.add(entity);
    }

    public void removeEntity(Entity entity) {
        entities.remove(entity);
    }

    public List<Entity> getEntities() {
        return Collections.unmodifiableList(entities);
    }

    public WrappedBlockState getBlockAt(int x, int y, int z) {
        if (y < 0 || y >= 256) return Blocks.AIR;
        return chunkManager.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16))
                .getBlock(Math.floorMod(x, 16), y, Math.floorMod(z, 16));
    }

    public boolean setBlockAt(int x, int y, int z, WrappedBlockState block) {
        if (y < 0 || y >= 256) return false;
        WorldChunk chunk = chunkManager.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        int localX = Math.floorMod(x, 16);
        int localZ = Math.floorMod(z, 16);
        if (Blocks.same(chunk.getBlock(localX, y, localZ), block)) return false;
        chunk.setBlock(localX, y, localZ, block);
        return true;
    }

    /**
     * Sets a block, tells every player who can see it, and queues the neighbour updates that
     * make gravity, fluids, and supported blocks react. Gameplay should prefer this over
     * {@link #setBlockAt}; world generation deliberately does not, so a generated ocean does
     * not start flowing the moment it loads.
     */
    public boolean setBlockAndUpdate(int x, int y, int z, WrappedBlockState block) {
        if (!setBlockAt(x, y, z, block)) return false;
        net.vibmc.server.VibMC server = net.vibmc.server.VibMC.getInstance();
        if (server != null) server.getPlayerManager().broadcastBlockChange(this, x, y, z, block);
        blockUpdates.scheduleNeighbours(x, y, z);
        return true;
    }

    public BlockUpdates blockUpdates() {
        return blockUpdates;
    }

    /** Containers keyed by block position; chunk storage holds block states only. */
    public BlockEntities blockEntities() {
        return blockEntities;
    }

    public int getHighestBlockY(int x, int z) {
        WorldChunk chunk = chunkManager.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        int localX = Math.floorMod(x, 16);
        int localZ = Math.floorMod(z, 16);
        for (int y = 255; y >= 0; y--) {
            if (!Blocks.same(chunk.getBlock(localX, y, localZ), Blocks.AIR)) {
                return y;
            }
        }
        return 0;
    }

    public int getTerrainSurfaceY(int x, int z) {
        WorldChunk chunk = chunkManager.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        int localX = Math.floorMod(x, 16), localZ = Math.floorMod(z, 16);
        for (int y = 255; y >= 0; y--) {
            WrappedBlockState id = chunk.getBlock(localX, y, localZ);
            if (!Blocks.same(id, Blocks.AIR) && !Blocks.same(id, Blocks.WATER) && !Blocks.same(id, Blocks.LAVA)
                    && !Blocks.same(id, Blocks.WOOD) && !Blocks.same(id, Blocks.LEAVES)) return y;
        }
        return 0;
    }

    public int getHighestSolidY(int x, int z) {
        WorldChunk chunk = chunkManager.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        int localX = Math.floorMod(x, 16);
        int localZ = Math.floorMod(z, 16);
        for (int y = 255; y >= 0; y--) {
            WrappedBlockState id = chunk.getBlock(localX, y, localZ);
            if (!Blocks.same(id, Blocks.AIR) && !Blocks.same(id, Blocks.WATER) && !Blocks.same(id, Blocks.LAVA)) {
                return y;
            }
        }
        return 0;
    }

    /**
     * Finds the nearest dry-land spawn column, starting from (x, z).
     *
     * The requested radius is scanned block-accurately first. An all-ocean neighbourhood
     * used to fall back to the starting column, which is how players ended up spawning in
     * water; instead the search now keeps widening against the terrain generator until it
     * finds land. Only genuinely dry columns are ever returned.
     */
    public int[] findDrySpawn(int x, int z, int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("radius cannot be negative");
        }
        int[] near = scanDrySpawn(x, z, radius);
        if (near != null) return near;
        int[] far = projectDrySpawn(x, z, radius);
        if (far != null) return far;
        return new int[]{x, z};
    }

    /** Block-accurate scan; null when every column in range is wet or obstructed. */
    private int[] scanDrySpawn(int x, int z, int radius) {
        int bestX = 0;
        int bestZ = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int distance = dx * dx + dz * dz;
                if (distance >= bestDistance) continue;
                if (!isDryStandingColumn(x + dx, z + dz)) continue;
                bestDistance = distance;
                bestX = x + dx;
                bestZ = z + dz;
            }
        }
        return bestDistance == Integer.MAX_VALUE ? null : new int[]{bestX, bestZ};
    }

    /** A surface at or above sea level with two blocks of air to stand in. */
    public boolean isDryStandingColumn(int x, int z) {
        int surfaceY = getTerrainSurfaceY(x, z);
        return surfaceY >= getSeaLevel()
                && Blocks.same(getBlockAt(x, surfaceY + 1, z), Blocks.AIR)
                && Blocks.same(getBlockAt(x, surfaceY + 2, z), Blocks.AIR);
    }

    /**
     * Widens the search beyond an all-ocean neighbourhood. Candidate columns come from the
     * terrain generator - probing real blocks this far out would generate thousands of
     * chunks - and the winner is confirmed against generated blocks before it is returned.
     */
    private int[] projectDrySpawn(int originX, int originZ, int scanned) {
        if (environment != WorldEnvironment.OVERWORLD) return null;
        net.vibmc.world.gen.TerrainGenerator terrain = new net.vibmc.world.gen.TerrainGenerator(seed);
        int alreadyScanned = scanned * scanned;
        for (int radius = Math.max(scanned * 2, 64); radius <= MAX_DRY_SPAWN_RADIUS; radius *= 2) {
            int step = Math.max(1, radius / 64);
            int bestX = 0;
            int bestZ = 0;
            int bestDistance = Integer.MAX_VALUE;
            for (int dz = -radius; dz <= radius; dz += step) {
                for (int dx = -radius; dx <= radius; dx += step) {
                    int distance = dx * dx + dz * dz;
                    if (distance >= bestDistance || distance <= alreadyScanned) continue;
                    if (terrain.getHeight(originX + dx, originZ + dz) < getSeaLevel()) continue;
                    bestDistance = distance;
                    bestX = originX + dx;
                    bestZ = originZ + dz;
                }
            }
            if (bestDistance == Integer.MAX_VALUE) continue;
            int[] confirmed = scanDrySpawn(bestX, bestZ, 16);
            if (confirmed != null) return confirmed;
        }
        return null;
    }

    /**
     * Last-resort spawn guarantee for a world with no reachable land: turns (x, z) into a
     * dry standing column by replacing the water above the seabed with sand. Other
     * dimensions have no water table and keep their own safe-spawn search.
     */
    public int createDryPlatform(int x, int z) {
        if (environment != WorldEnvironment.OVERWORLD) return findSafeSpawnY(x, z);
        int floorY = Math.max(getHighestSolidY(x, z), getSeaLevel());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int columnX = x + dx;
                int columnZ = z + dz;
                for (int y = Math.max(1, getHighestSolidY(columnX, columnZ)); y <= floorY; y++) {
                    setBlockAt(columnX, y, columnZ, Blocks.SAND);
                }
                setBlockAt(columnX, floorY + 1, columnZ, Blocks.AIR);
                setBlockAt(columnX, floorY + 2, columnZ, Blocks.AIR);
            }
        }
        return floorY + 1;
    }

    public int findSafeSpawnY(int x, int z) {
        if (environment != WorldEnvironment.NETHER) {
            return getHighestSolidY(x, z) + 1;
        }
        WorldChunk chunk = chunkManager.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        int localX = Math.floorMod(x, 16);
        int localZ = Math.floorMod(z, 16);
        for (int y = 100; y >= 2; y--) {
            WrappedBlockState floor = chunk.getBlock(localX, y, localZ);
            if (!Blocks.same(floor, Blocks.AIR) && !Blocks.same(floor, Blocks.LAVA)
                    && Blocks.same(chunk.getBlock(localX, y + 1, localZ), Blocks.AIR)
                    && Blocks.same(chunk.getBlock(localX, y + 2, localZ), Blocks.AIR)) {
                return y + 1;
            }
        }
        return 65;
    }

    public String biomeAt(int x, int z) {
        if (environment == WorldEnvironment.NETHER) return "minecraft:nether_wastes";
        if (environment == WorldEnvironment.END) return "minecraft:the_end";
        net.vibmc.world.gen.TerrainGenerator terrain = new net.vibmc.world.gen.TerrainGenerator(seed);
        if (terrain.getHeight(x, z) < 62) return "minecraft:ocean";
        double climate = terrain.fbm(x * 0.0017, z * 0.0017, 3);
        double moisture = terrain.fbm(x * 0.0021 + 500, z * 0.0021 - 500, 3);
        if (climate > 0.38 && moisture < -0.05) return "minecraft:desert";
        if (climate < -0.35) return "minecraft:taiga";
        if (moisture > 0.2) return "minecraft:forest";
        return "minecraft:plains";
    }

    public int getSeaLevel() {
        return environment == WorldEnvironment.OVERWORLD ? 63 : 32;
    }

    public long seed() {
        return seed;
    }

    public String name() {
        return name;
    }

    public long getWorldTime() {
        return worldTime;
    }

    public void setWorldTime(long worldTime) {
        this.worldTime = worldTime;
    }

    public WorldStorage storage() {
        return storage;
    }

    public WorldEnvironment environment() {
        return environment;
    }

    public long getDayTime() {
        return timeSystem.timeOfDay();
    }

    public void setTimeOfDay(long time) {
        timeSystem.setTimeOfDay(time);
    }

    public void addTime(long ticks) {
        timeSystem.addTime(ticks);
    }

    public TimeSystem timeSystem() {
        return timeSystem;
    }

    public WeatherSystem weatherSystem() {
        return weatherSystem;
    }

    public ChunkManager chunkManager() {
        return chunkManager;
    }
}
