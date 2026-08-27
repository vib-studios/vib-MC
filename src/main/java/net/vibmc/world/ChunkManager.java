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
            com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState[] stored = storage.readChunk(chunkX, chunkZ);
            if (stored != null) {
                return WorldChunk.fromStored(world, chunkX, chunkZ, stored);
            }
        } catch (IOException e) {
            warn("Could not read chunk %d,%d (%s); regenerating it", chunkX, chunkZ, e.getMessage());
        }
        return WorldChunk.generate(world, chunkX, chunkZ);
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
                storage.writeChunk(chunk.chunkX(), chunk.chunkZ(), chunk.blocks());
                chunk.markSaved();
                written++;
            } catch (IOException e) {
                warn("Failed to save chunk %d,%d: %s", chunk.chunkX(), chunk.chunkZ(), e);
            }
        }
        return written;
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

    private static long chunkKey(int chunkX, int chunkZ) {
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
