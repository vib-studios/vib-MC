package net.vibmc.world;

import net.vibmc.server.VibMC;
import net.vibmc.server.util.Logger;
import net.vibmc.world.storage.WorldStorage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe cache that loads, generates, and persists chunks for one world. */
public final class ChunkManager {
    private final World world;
    private final WorldStorage storage;
    private final Map<Long, WorldChunk> loadedChunks = new ConcurrentHashMap<>();

    public ChunkManager(World world) {
        this(world, world.storage());
    }

    public ChunkManager(World world, WorldStorage storage) {
        this.world = world;
        this.storage = storage;
    }

    public WorldChunk getChunk(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        return loadedChunks.computeIfAbsent(key, ignored -> loadOrGenerate(chunkX, chunkZ));
    }

    private WorldChunk loadOrGenerate(int chunkX, int chunkZ) {
        try {
            long start = System.nanoTime();
            com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState[] stored = storage.readChunk(chunkX, chunkZ);
            long elapsed = System.nanoTime() - start;
            if (elapsed > 50_000_000L) {
                warn("Slow chunk read %d,%d took %.2f ms", chunkX, chunkZ, elapsed / 1_000_000.0);
            }
            if (stored != null) {
                return WorldChunk.fromStored(world, chunkX, chunkZ, stored);
            }
        } catch (IOException e) {
            warn("Could not read chunk %d,%d (%s); regenerating it (corrupted/original preserved via quarantine if applicable)", chunkX, chunkZ, e.getMessage());
        }
        long genStart = System.nanoTime();
        WorldChunk generated = WorldChunk.generate(world, chunkX, chunkZ);
        long genElapsed = System.nanoTime() - genStart;
        if (genElapsed > 100_000_000L) {
            warn("Slow chunk generation %d,%d took %.2f ms", chunkX, chunkZ, genElapsed / 1_000_000.0);
        }
        return generated;
    }

    public List<WorldChunk> listLoadedChunks() {
        return new ArrayList<>(loadedChunks.values());
    }

    public int getLoadedChunkCount() {
        return loadedChunks.size();
    }

    /** Writes every loaded chunk that has unsaved changes. */
    public int saveAll() {
        int written = 0;
        for (WorldChunk chunk : listLoadedChunks()) {
            if (!chunk.isDirty()) {
                continue;
            }
            try {
                long start = System.nanoTime();
                storage.writeChunk(chunk.chunkX(), chunk.chunkZ(), chunk.blocks());
                long elapsed = System.nanoTime() - start;
                if (elapsed > 50_000_000L) { // >50ms
                    warn("Slow chunk save %d,%d took %.2f ms", chunk.chunkX(), chunk.chunkZ(), elapsed / 1_000_000.0);
                }
                chunk.markSaved();
                written++;
            } catch (IOException e) {
                warn("Failed to save chunk %d,%d: %s (chunk remains dirty)", chunk.chunkX(), chunk.chunkZ(), e.getMessage());
            }
        }
        return written;
    }

    /**
     * Drops loaded chunks that {@code keep} rejects. Nothing used to leave the cache, so a
     * server that players explored grew until it ran out of memory. A dirty chunk is written
     * first and stays loaded if that fails, or if saving is off and it cannot be written.
     */
    public int unloadExcept(java.util.function.LongPredicate keep, boolean saveDirty) {
        int unloaded = 0;
        for (Map.Entry<Long, WorldChunk> entry : loadedChunks.entrySet()) {
            if (keep.test(entry.getKey())) continue;
            WorldChunk chunk = entry.getValue();
            if (chunk.isDirty()) {
                if (!saveDirty) continue;
                try {
                    storage.writeChunk(chunk.chunkX(), chunk.chunkZ(), chunk.blocks());
                    chunk.markSaved();
                } catch (IOException e) {
                    warn("Failed to save chunk %d,%d before unloading: %s", chunk.chunkX(), chunk.chunkZ(), e);
                    continue;
                }
            }
            if (loadedChunks.remove(entry.getKey(), chunk)) unloaded++;
        }
        return unloaded;
    }

    public int getUnsavedChunkCount() {
        int dirty = 0;
        for (WorldChunk chunk : listLoadedChunks()) {
            if (chunk.isDirty()) {
                dirty++;
            }
        }
        return dirty;
    }

    static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static void warn(String message, Object... args) {
        VibMC server = VibMC.getInstance();
        Logger logger = server == null ? null : server.getLogger();
        if (logger != null) {
            logger.warn(message, args);
        }
    }
}
