package net.vibmc.world;

import net.vibmc.server.ServerConfig;
import net.vibmc.server.VibMC;
import net.vibmc.server.util.Logger;
import net.vibmc.world.storage.LevelData;
import net.vibmc.world.storage.WorldStorage;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldManager {
    private final Map<String, World> worlds = new ConcurrentHashMap<>();
    private final World mainWorld;
    private final World netherWorld;
    private final World endWorld;

    public WorldManager(ServerConfig config) {
        Long configuredSeed = config.configuredSeed();
        long newSeed = configuredSeed == null ? new SecureRandom().nextLong() : configuredSeed;
        this.mainWorld = loadWorld(config.worldName(), WorldEnvironment.OVERWORLD, newSeed, configuredSeed);
        worlds.put(mainWorld.name(), mainWorld);

        this.netherWorld = config.allowNether()
                ? loadWorld(config.worldName() + "_nether", WorldEnvironment.NETHER, mainWorld.seed(), null) : null;
        this.endWorld = config.allowEnd()
                ? loadWorld(config.worldName() + "_the_end", WorldEnvironment.END, mainWorld.seed(), null) : null;
        if (netherWorld != null) worlds.put(netherWorld.name(), netherWorld);
        if (endWorld != null) worlds.put(endWorld.name(), endWorld);
    }

    private World loadWorld(String name, WorldEnvironment environment, long newSeed, Long configuredSeed) {
        WorldStorage storage = new WorldStorage(name);
        try {
            storage.prepare();
        } catch (IOException e) {
            warn("Could not create world directory %s: %s", storage.worldDir(), e);
        }

        long seed = newSeed;
        LevelData restored = null;
        if (storage.hasLevelData()) {
            try {
                restored = storage.readLevel();
                if (configuredSeed != null && restored.seed() != configuredSeed) {
                    warn("Configured seed %d differs from saved seed %d; using the saved seed",
                            configuredSeed, restored.seed());
                }
                seed = restored.seed();
            } catch (IOException e) {
                warn("Could not read %s: %s", storage.worldDir().resolve("level.dat"), e.getMessage());
            }
        }

        World world = new World(seed, name, storage, environment);
        try {
            storage.readContainers(world.blockEntities());
        } catch (IOException e) {
            warn("Could not read containers for '%s': %s", name, e.getMessage());
        }
        if (restored != null) {
            world.setWorldTime(restored.worldTime());
            world.setTimeOfDay(restored.timeOfDay());
            world.weatherSystem().setWeather(restored.weather());
            info("Loaded %s world '%s' (seed %d)", environment, name, seed);
        } else {
            try {
                storage.writeLevel(new LevelData(seed, 0L, world.getDayTime(), "clear"));
            } catch (IOException e) {
                warn("Could not write initial level data for '%s': %s", name, e);
            }
            info("Created %s world '%s' with seed %d", environment, name, seed);
        }
        return world;
    }

    public World getMainWorld() { return mainWorld; }
    public World getNetherWorld() { return netherWorld; }
    public World getEndWorld() { return endWorld; }
    public World getWorld(String name) { return worlds.get(name); }
    public void addWorld(World world) { worlds.put(world.name(), world); }
    public Collection<World> getWorlds() { return Collections.unmodifiableCollection(worlds.values()); }

    public int saveAll() {
        int written = 0;
        for (World world : worlds.values()) {
            try {
                world.storage().writeLevel(new LevelData(world.seed(), world.getWorldTime(),
                        world.getDayTime(), world.weatherSystem().weather()));
            } catch (IOException e) {
                warn("Failed to save level data for '%s': %s", world.name(), e);
            }
            try {
                world.storage().writeContainers(world.blockEntities());
            } catch (IOException e) {
                warn("Failed to save containers for '%s': %s", world.name(), e);
            }
            written += world.chunkManager().saveAll();
        }
        return written;
    }

    public int getUnsavedChunkCount() {
        int dirty = 0;
        for (World world : worlds.values()) dirty += world.chunkManager().getUnsavedChunkCount();
        return dirty;
    }

    private static void info(String message, Object... args) {
        Logger logger = logger();
        if (logger != null) logger.info(message, args);
    }

    private static void warn(String message, Object... args) {
        Logger logger = logger();
        if (logger != null) logger.warn(message, args);
    }

    private static Logger logger() {
        VibMC server = VibMC.getInstance();
        return server == null ? null : server.getLogger();
    }
}
