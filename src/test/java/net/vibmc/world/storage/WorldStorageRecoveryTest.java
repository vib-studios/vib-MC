package net.vibmc.world.storage;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import net.vibmc.world.Blocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldStorageRecoveryTest {
    private static final int BLOCKS_PER_CHUNK = 16 * 16 * 256;

    private WorldStorage storageIn(Path dir) {
        return new WorldStorage(dir.resolve("world").toString());
    }

    private WrappedBlockState[] emptyStates(int size) {
        WrappedBlockState[] states = new WrappedBlockState[size];
        Arrays.fill(states, Blocks.AIR);
        return states;
    }

    @Test
    void truncatedChunkFileIsRejectedAndPreserved(@TempDir Path dir) throws IOException {
        WorldStorage storage = storageIn(dir);
        storage.prepare();
        Path region = dir.resolve("world").resolve("region");
        Path chunk = region.resolve("r.0.0.chunk");
        Files.write(chunk, new byte[]{0x56, 0x42, 0x43, 0x4B}); // magic only
        assertThrows(IOException.class, () -> storage.readChunk(0, 0));
        // Original should be quarantined or preserved somewhere
        long count = Files.walk(dir).filter(p -> p.getFileName().toString().contains("r.0.0.chunk")).count();
        assertTrue(count >= 1);
    }

    @Test
    void incorrectMagicIsRejected(@TempDir Path dir) throws IOException {
        WorldStorage storage = storageIn(dir);
        storage.prepare();
        Path region = dir.resolve("world").resolve("region");
        Files.write(region.resolve("r.1.0.chunk"), new byte[]{1, 2, 3, 4, 5});
        assertThrows(IOException.class, () -> storage.readChunk(1, 0));
    }

    @Test
    void unsupportedChunkVersionIsRejected(@TempDir Path dir) throws IOException {
        WorldStorage storage = storageIn(dir);
        storage.prepare();
        Path region = dir.resolve("world").resolve("region");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(baos))) {
            out.writeInt(0x5642434B);
            out.writeInt(999); // bad version
            out.writeInt(2); out.writeInt(0);
            out.writeInt(2);
        }
        Files.write(region.resolve("r.2.0.chunk"), baos.toByteArray());
        assertThrows(IOException.class, () -> storage.readChunk(2, 0));
    }

    @Test
    void mismatchedChunkCoordinatesRejected(@TempDir Path dir) throws IOException {
        WorldStorage storage = storageIn(dir);
        storage.writeChunk(7, 3, emptyStates(BLOCKS_PER_CHUNK));
        Path region = dir.resolve("world").resolve("region");
        Files.move(region.resolve("r.7.3.chunk"), region.resolve("r.1.1.chunk"));
        assertThrows(IOException.class, () -> storage.readChunk(1, 1));
    }

    @Test
    void invalidBlockStateIdRejected(@TempDir Path dir) throws IOException {
        WorldStorage storage = storageIn(dir);
        storage.prepare();
        Path region = dir.resolve("world").resolve("region");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(baos))) {
            out.writeInt(0x5642434B);
            out.writeInt(2);
            out.writeInt(5); out.writeInt(5);
            out.writeInt(5);
            for (int i = 0; i < BLOCKS_PER_CHUNK; i++) {
                if (i == 10) out.writeInt(-1);
                else out.writeInt(Blocks.AIR.getGlobalId());
            }
        }
        Files.write(region.resolve("r.5.5.chunk"), baos.toByteArray());
        assertThrows(IOException.class, () -> storage.readChunk(5, 5));
    }

    @Test
    void unexpectedTrailingDataRejected(@TempDir Path dir) throws IOException {
        WorldStorage storage = storageIn(dir);
        storage.prepare();
        Path region = dir.resolve("world").resolve("region");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(baos))) {
            out.writeInt(0x5642434B);
            out.writeInt(2);
            out.writeInt(6); out.writeInt(6);
            out.writeInt(6);
            for (int i = 0; i < BLOCKS_PER_CHUNK; i++) {
                out.writeInt(Blocks.AIR.getGlobalId());
            }
            out.writeByte(0xFF);
        }
        Files.write(region.resolve("r.6.6.chunk"), baos.toByteArray());
        assertThrows(IOException.class, () -> storage.readChunk(6, 6));
    }

    @Test
    void oversizedContainerFileRejected(@TempDir Path dir) throws IOException {
        WorldStorage storage = storageIn(dir);
        storage.prepare();
        Path world = dir.resolve("world");
        // Create a file larger than limit
        byte[] big = new byte[(int) (16L * 1024 * 1024 + 100)];
        Files.write(world.resolve("containers.dat"), big);
        net.vibmc.world.BlockEntities entities = new net.vibmc.world.BlockEntities();
        assertThrows(IOException.class, () -> storage.readContainers(entities));
    }

    @Test
    void failedWriteLeavesPreviousValidFileIntact(@TempDir Path dir) throws IOException {
        WorldStorage storage = storageIn(dir);
        WrappedBlockState[] blocks = emptyStates(BLOCKS_PER_CHUNK);
        blocks[0] = Blocks.BEDROCK;
        storage.writeChunk(0, 0, blocks);
        Path chunkPath = dir.resolve("world").resolve("region").resolve("r.0.0.chunk");
        long before = Files.size(chunkPath);
        // Try to write bad chunk (null) should throw
        WrappedBlockState[] bad = emptyStates(BLOCKS_PER_CHUNK);
        bad[5] = null;
        assertThrows(IOException.class, () -> storage.writeChunk(0, 0, bad));
        assertTrue(Files.exists(chunkPath));
        assertEquals(before, Files.size(chunkPath));
    }
}
