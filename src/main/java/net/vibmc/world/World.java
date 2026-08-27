package net.vibmc.world;

import net.vibmc.entity.Entity;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import net.vibmc.world.storage.WorldStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class World {
    private final long seed;
    private final String name;
    private final ChunkManager chunkManager;
    private final TimeSystem timeSystem;
    private final WeatherSystem weatherSystem;
    private final WorldStorage storage;
    private final WorldEnvironment environment;
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

    /** Finds the nearest dry-land spawn column within radius, starting from (x, z). */
    public int[] findDrySpawn(int x, int z, int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("radius cannot be negative");
        }
        int bestX = x;
        int bestZ = z;
        int bestDistance = Integer.MAX_VALUE;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int worldX = x + dx;
                int worldZ = z + dz;
                int surfaceY = getTerrainSurfaceY(worldX, worldZ);
                if (surfaceY >= getSeaLevel()
                        && Blocks.same(getBlockAt(worldX, surfaceY + 1, worldZ), Blocks.AIR)
                        && Blocks.same(getBlockAt(worldX, surfaceY + 2, worldZ), Blocks.AIR)) {
                    int distance = dx * dx + dz * dz;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestX = worldX;
                        bestZ = worldZ;
                    }
                }
            }
        }
        return new int[]{bestX, bestZ};
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
