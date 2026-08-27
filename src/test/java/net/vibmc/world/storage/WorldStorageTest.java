package net.vibmc.world.storage;

import org.junit.jupiter.api.Test;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import net.vibmc.world.Blocks;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldStorageTest {
    private static final int BLOCKS_PER_CHUNK = 16 * 16 * 256;

    private WorldStorage storageIn(Path dir) {
        return new WorldStorage(dir.resolve("world").toString());
    }

    @Test
    void prepareCreatesWorldAndRegionDirectories(@TempDir Path dir) throws IOException {
        WorldStorage storage = storageIn(dir);
        storage.prepare();

        assertTrue(Files.isDirectory(dir.resolve("world")));
        assertTrue(Files.isDirectory(dir.resolve("world").resolve("region")));
    }

    @Test
    void levelDataSurvivesARoundTrip(@TempDir Path dir) throws IOException {
        WorldStorage storage = storageIn(dir);
        storage.writeLevel(new LevelData(42L, 1234L, 15000L, "rain"));

        LevelData read = new WorldStorage(dir.resolve("world").toString()).readLevel();

        assertEquals(42L, read.seed());
        assertEquals(1234L, read.worldTime());
        assertEquals(15000L, read.timeOfDay());
        assertEquals("rain", read.weather());
    }

    @Test
    void chunkBlocksSurviveARoundTrip(@TempDir Path dir) throws IOException {
        WorldStorage storage = storageIn(dir);
        WrappedBlockState[] blocks = new WrappedBlockState[BLOCKS_PER_CHUNK];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = new com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState[]{Blocks.AIR,Blocks.STONE,Blocks.GRASS,Blocks.DIRT,Blocks.WOOD,Blocks.LEAVES,Blocks.WATER,Blocks.LAVA,Blocks.CHEST,Blocks.FURNACE,Blocks.CRAFTING_TABLE,Blocks.DOOR,Blocks.TRAPDOOR,Blocks.SAND,Blocks.GRAVEL,Blocks.BEDROCK,Blocks.ANDESITE,Blocks.DIORITE}[i % 18];
        }

        storage.writeChunk(3, -2, blocks);
        WrappedBlockState[] read = new WorldStorage(dir.resolve("world").toString()).readChunk(3, -2);

        assertArrayEquals(blocks, read);
    }

    @Test
    void negativeChunkCoordinatesRoundTrip(@TempDir Path dir) throws IOException {
        WorldStorage storage = storageIn(dir);
        WrappedBlockState[] blocks = new WrappedBlockState[BLOCKS_PER_CHUNK];
        java.util.Arrays.fill(blocks,Blocks.AIR);
        blocks[0] = Blocks.BEDROCK;

        storage.writeChunk(-7, -13, blocks);

        assertTrue(storage.hasChunk(-7, -13));
        assertArrayEquals(blocks, storage.readChunk(-7, -13));
    }

    @Test
    void unsavedChunkReadsAsNull(@TempDir Path dir) throws IOException {
        WorldStorage storage = storageIn(dir);
        storage.prepare();

        assertNull(storage.readChunk(0, 0));
        assertTrue(!storage.hasChunk(0, 0));
    }

    @Test
    void corruptChunkIsReportedRatherThanReturningGarbage(@TempDir Path dir) throws IOException {
        WorldStorage storage = storageIn(dir);
        storage.prepare();
        Files.write(dir.resolve("world").resolve("region").resolve("r.1.1.chunk"),
                "definitely not a chunk".getBytes(StandardCharsets.UTF_8));

        assertThrows(IOException.class, () -> storage.readChunk(1, 1));
    }

    @Test
    void chunkStoredUnderDifferentCoordinatesIsRejected(@TempDir Path dir) throws IOException {
        WorldStorage storage = storageIn(dir);
        storage.writeChunk(2, 2, emptyStates(BLOCKS_PER_CHUNK));

        Path region = dir.resolve("world").resolve("region");
        Files.move(region.resolve("r.2.2.chunk"), region.resolve("r.5.5.chunk"));

        assertThrows(IOException.class, () -> storage.readChunk(5, 5));
    }

    @Test
    void writingLeavesNoTemporaryFilesBehind(@TempDir Path dir) throws IOException {
        WorldStorage storage = storageIn(dir);
        storage.writeLevel(new LevelData(1L, 0L, 0L, "clear"));
        storage.writeChunk(0, 0, emptyStates(BLOCKS_PER_CHUNK));

        try (java.util.stream.Stream<Path> files = Files.walk(dir)) {
            assertTrue(files.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void wrongBlockCountIsRejected(@TempDir Path dir) {
        WorldStorage storage = storageIn(dir);

        assertThrows(IOException.class, () -> storage.writeChunk(0, 0, emptyStates(10)));
    }
    private static WrappedBlockState[] emptyStates(int size){WrappedBlockState[] states=new WrappedBlockState[size];java.util.Arrays.fill(states,Blocks.AIR);return states;}
}
