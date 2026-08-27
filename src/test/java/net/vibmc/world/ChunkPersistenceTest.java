package net.vibmc.world;

import net.vibmc.world.storage.WorldStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkPersistenceTest {
    private static final long SEED = 987654321L;

    private World worldIn(Path dir) {
        String name = dir.resolve("world").toString();
        return new World(SEED, name, new WorldStorage(name));
    }

    @Test
    void generatedChunkIsDirtyAndSavesOnce(@TempDir Path dir) {
        World world = worldIn(dir);
        WorldChunk chunk = world.getChunk(0, 0);

        assertTrue(chunk.isDirty(), "a freshly generated chunk has never been written");
        assertEquals(1, world.chunkManager().saveAll());
        assertFalse(chunk.isDirty());
        assertEquals(0, world.chunkManager().saveAll(), "an unchanged chunk is not rewritten");
    }

    @Test
    void placedBlocksSurviveAReload(@TempDir Path dir) {
        World first = worldIn(dir);
        WorldChunk chunk = first.getChunk(3, -2);
        com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState generated = chunk.getBlock(9, 0, 9);
        chunk.setBlock(5, 40, 7, Blocks.CHEST);
        first.chunkManager().saveAll();

        World reloaded = worldIn(dir);
        WorldChunk restored = reloaded.getChunk(3, -2);

        assertEquals(Blocks.CHEST, restored.getBlock(5, 40, 7));
        assertEquals(generated, restored.getBlock(9, 0, 9), "generated terrain is preserved too");
        assertFalse(restored.isDirty(), "a chunk read from disk starts clean");
    }

    @Test
    void chunkNotOnDiskIsStillGenerated(@TempDir Path dir) {
        World world = worldIn(dir);
        WorldChunk chunk = world.getChunk(64, 64);

        assertEquals(Blocks.BEDROCK, chunk.getBlock(0, 0, 0));
        assertTrue(chunk.isDirty());
    }

    @Test
    void unreadableChunkFallsBackToGeneration(@TempDir Path dir) throws IOException {
        World world = worldIn(dir);
        world.getChunk(1, 1);
        world.chunkManager().saveAll();

        Path region = dir.resolve("world").resolve("region").resolve("r.1.1.chunk");
        java.nio.file.Files.write(region, new byte[]{1, 2, 3});

        World reloaded = worldIn(dir);
        WorldChunk chunk = reloaded.getChunk(1, 1);

        assertEquals(Blocks.BEDROCK, chunk.getBlock(0, 0, 0),
                "a damaged chunk regenerates instead of taking the server down");
    }

    @Test
    void setBlockOnlyMarksDirtyWhenSomethingChanges(@TempDir Path dir) {
        World world = worldIn(dir);
        WorldChunk chunk = world.getChunk(0, 0);
        world.chunkManager().saveAll();
        assertFalse(chunk.isDirty());

        com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState existing = chunk.getBlock(4, 0, 4);
        chunk.setBlock(4, 0, 4, existing);
        assertFalse(chunk.isDirty(), "rewriting the same block is not a change");

        assertNotEquals(Blocks.LAVA, existing);
        chunk.setBlock(4, 0, 4, Blocks.LAVA);
        assertTrue(chunk.isDirty());
    }

    @Test
    void unsavedChunkCountTracksPendingWork(@TempDir Path dir) {
        World world = worldIn(dir);
        world.getChunk(0, 0);
        world.getChunk(0, 1);

        assertEquals(2, world.chunkManager().getUnsavedChunkCount());
        world.chunkManager().saveAll();
        assertEquals(0, world.chunkManager().getUnsavedChunkCount());
    }
}
