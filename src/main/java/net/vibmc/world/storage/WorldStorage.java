package net.vibmc.world.storage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * On-disk layout for a world.
 *
 * <pre>
 * &lt;level-name&gt;/
 *   level.dat              seed, elapsed time, time of day, weather
 *   region/
 *     r.&lt;x&gt;.&lt;z&gt;.chunk     gzipped block data for one chunk
 * </pre>
 *
 * WorldChunk payloads are PacketEvents wrapped-state global IDs, so a saved chunk restores
 * exactly what was in memory rather than being re-derived from the seed. Writes go to
 * a temporary file and are then moved into place, so an interrupted save cannot leave
 * a half-written chunk behind.
 */
public class WorldStorage {
    private static final int LEVEL_MAGIC = 0x56424C56;  // "VBLV"
    private static final int CONTAINER_MAGIC = 0x56424354;  // "VBCT"
    private static final int CONTAINER_VERSION = 1;
    /** Guards against a corrupt file claiming an absurd number of containers. */
    private static final int MAX_CONTAINERS = 1 << 16;
    private static final int MAX_CONTAINER_SLOTS = 256;
    private static final int CHUNK_MAGIC = 0x5642434B;  // "VBCK"
    private static final int FORMAT_VERSION = 2;

    /** 16 x 16 x 256, matching WorldChunk's block array. */
    private static final int BLOCKS_PER_CHUNK = 16 * 16 * 256;
    private static final long MAX_CHUNK_FILE_BYTES = 8L << 20;
    private static final long MAX_CONTAINER_FILE_BYTES = 16L << 20;
    private static final long MAX_LEVEL_FILE_BYTES = 256 * 1024;
    private static final int MAX_WEATHER_BYTES = 64;

    private final Path worldDir;
    private final Path regionDir;
    private final Path levelFile;

    public WorldStorage(String levelName) {
        this.worldDir = Paths.get(levelName);
        this.regionDir = worldDir.resolve("region");
        this.levelFile = worldDir.resolve("level.dat");
    }

    /** Creates the world and region directories if they do not exist yet. */
    public void prepare() throws IOException {
        Files.createDirectories(regionDir);
    }

    public Path worldDir() {
        return worldDir;
    }

    public boolean hasLevelData() {
        return Files.isRegularFile(levelFile);
    }

    public LevelData readLevel() throws IOException {
        if (Files.size(levelFile) > MAX_LEVEL_FILE_BYTES) {
            throw new IOException("level.dat exceeds size limit");
        }
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(levelFile)))) {
            int magic = in.readInt();
            if (magic != LEVEL_MAGIC) {
                throw new IOException("not a vib-MC level.dat (magic " + Integer.toHexString(magic) + ")");
            }
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                throw new IOException("unsupported level.dat version " + version);
            }
            long seed = in.readLong();
            long worldTime = in.readLong();
            long timeOfDay = in.readLong();
            String weather = readBoundedString(in, MAX_WEATHER_BYTES, "weather");
            // Reject unexpected trailing data if present
            try {
                if (in.available() > 0) {
                    int b = in.read();
                    if (b != -1) {
                        throw new IOException("unexpected trailing data in level.dat");
                    }
                }
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("trailing")) throw e;
            }
            return new LevelData(seed, worldTime, timeOfDay, weather);
        }
    }

    public void writeLevel(LevelData level) throws IOException {
        prepare();
        Path tmp = levelFile.resolveSibling("level.dat.tmp");
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(tmp)))) {
            out.writeInt(LEVEL_MAGIC);
            out.writeInt(FORMAT_VERSION);
            out.writeLong(level.seed());
            out.writeLong(level.worldTime());
            out.writeLong(level.timeOfDay());
            writeBoundedString(out, level.weather(), MAX_WEATHER_BYTES, "weather");
        }
        try {
            moveIntoPlace(tmp, levelFile);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Chests and furnaces, keyed by packed block position. Chunk files hold block states only,
     * so a container's contents live here instead.
     */
    public void writeContainers(net.vibmc.world.BlockEntities entities) throws IOException {
        prepare();
        java.util.Map<Long, net.vibmc.inventory.Inventory> chests = entities.all();
        java.util.Map<Long, net.vibmc.world.Furnace> furnaces = entities.allFurnaces();
        Path target = worldDir.resolve("containers.dat");
        if (chests.isEmpty() && furnaces.isEmpty()) {
            Files.deleteIfExists(target);
            return;
        }
        Path temporary = worldDir.resolve("containers.dat.tmp");
        try {
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                    new GZIPOutputStream(Files.newOutputStream(temporary))))) {
                output.writeInt(CONTAINER_MAGIC);
                output.writeInt(CONTAINER_VERSION);
                output.writeInt(chests.size());
                for (java.util.Map.Entry<Long, net.vibmc.inventory.Inventory> entry : chests.entrySet()) {
                    output.writeLong(entry.getKey());
                    writeSlots(output, entry.getValue().getSlots());
                }
                output.writeInt(furnaces.size());
                for (java.util.Map.Entry<Long, net.vibmc.world.Furnace> entry : furnaces.entrySet()) {
                    net.vibmc.world.Furnace furnace = entry.getValue();
                    output.writeLong(entry.getKey());
                    writeSlots(output, furnace.slots().getSlots());
                    output.writeInt(furnace.burnTime());
                    output.writeInt(furnace.burnTimeTotal());
                    output.writeInt(furnace.cookTime());
                }
            }
            move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void readContainers(net.vibmc.world.BlockEntities entities) throws IOException {
        Path source = worldDir.resolve("containers.dat");
        if (!Files.exists(source)) return;
        if (Files.size(source) > MAX_CONTAINER_FILE_BYTES) {
            throw new IOException("containers.dat exceeds size limit");
        }
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(Files.newInputStream(source))))) {
            if (input.readInt() != CONTAINER_MAGIC) throw new IOException("invalid container magic");
            int version = input.readInt();
            if (version != CONTAINER_VERSION) throw new IOException("unsupported container version " + version);
            int chestCount = readCount(input, MAX_CONTAINERS, "container");
            for (int index = 0; index < chestCount; index++) {
                long position = input.readLong();
                com.github.retrooper.packetevents.protocol.item.ItemStack[] slots = readSlots(input);
                if (slots.length > MAX_CONTAINER_SLOTS) {
                    throw new IOException("invalid container slot count " + slots.length);
                }
                net.vibmc.inventory.Inventory chest = entities.container(
                        net.vibmc.world.BlockEntities.unpackX(position),
                        net.vibmc.world.BlockEntities.unpackY(position),
                        net.vibmc.world.BlockEntities.unpackZ(position),
                        "Chest", Math.max(1, slots.length));
                for (int slot = 0; slot < slots.length && slot < chest.getSize(); slot++) {
                    chest.setSlot(slot, slots[slot]);
                }
            }
            int furnaceCount = readCount(input, MAX_CONTAINERS, "furnace");
            for (int index = 0; index < furnaceCount; index++) {
                long position = input.readLong();
                com.github.retrooper.packetevents.protocol.item.ItemStack[] slots = readSlots(input);
                if (slots.length > MAX_CONTAINER_SLOTS) {
                    throw new IOException("invalid container slot count " + slots.length);
                }
                net.vibmc.world.Furnace furnace = entities.furnace(
                        net.vibmc.world.BlockEntities.unpackX(position),
                        net.vibmc.world.BlockEntities.unpackY(position),
                        net.vibmc.world.BlockEntities.unpackZ(position));
                for (int slot = 0; slot < slots.length && slot < furnace.slots().getSize(); slot++) {
                    furnace.slots().setSlot(slot, slots[slot]);
                }
                int burnTime = input.readInt();
                int burnTimeTotal = input.readInt();
                int cookTime = input.readInt();
                if (burnTime < 0 || burnTimeTotal < 0 || cookTime < 0) {
                    throw new IOException("invalid furnace times");
                }
                furnace.restoreState(burnTime, burnTimeTotal, cookTime);
            }
            // Try to detect trailing data
            try {
                if (input.available() > 0) {
                    int b = input.read();
                    if (b != -1) {
                        throw new IOException("unexpected trailing data in containers.dat");
                    }
                }
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("trailing")) throw e;
            }
        }
    }

    private static void writeSlots(DataOutputStream output,
                                   com.github.retrooper.packetevents.protocol.item.ItemStack[] slots) throws IOException {
        output.writeInt(slots.length);
        for (com.github.retrooper.packetevents.protocol.item.ItemStack item : slots) {
            net.vibmc.inventory.ItemCodec.writeItem(output, item);
        }
    }

    private static com.github.retrooper.packetevents.protocol.item.ItemStack[] readSlots(
            DataInputStream input) throws IOException {
        int size = readCount(input, MAX_CONTAINER_SLOTS, "container slot");
        com.github.retrooper.packetevents.protocol.item.ItemStack[] slots =
                new com.github.retrooper.packetevents.protocol.item.ItemStack[size];
        for (int slot = 0; slot < size; slot++) slots[slot] = net.vibmc.inventory.ItemCodec.readItem(input);
        return slots;
    }

    private static int readCount(DataInputStream input, int limit, String label) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > limit) throw new IOException("invalid " + label + " count " + count);
        return count;
    }

    private void move(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public boolean hasChunk(int chunkX, int chunkZ) {
        return Files.isRegularFile(chunkPath(chunkX, chunkZ));
    }

    /**
     * Reads a stored chunk's block array, or returns {@code null} if the chunk has
     * never been saved. A corrupt or truncated file is reported as an IOException so
     * the caller can fall back to generating fresh terrain.
     */
    public com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState[] readChunk(int chunkX, int chunkZ) throws IOException {
        Path path = chunkPath(chunkX, chunkZ);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        if (Files.size(path) > MAX_CHUNK_FILE_BYTES) {
            IOException ex = new IOException("chunk file exceeds size limit");
            quarantineCorruptChunk(path, chunkX, chunkZ, ex);
            throw ex;
        }
        try (InputStream raw = Files.newInputStream(path);
             DataInputStream in = new DataInputStream(
                     new BufferedInputStream(new GZIPInputStream(raw)))) {
            int magic = in.readInt();
            if (magic != CHUNK_MAGIC) {
                IOException ex = new IOException("bad chunk magic " + Integer.toHexString(magic));
                quarantineCorruptChunk(path, chunkX, chunkZ, ex);
                throw ex;
            }
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                IOException ex = new IOException("unsupported chunk version " + version);
                quarantineCorruptChunk(path, chunkX, chunkZ, ex);
                throw ex;
            }
            int storedX = in.readInt();
            int storedZ = in.readInt();
            if (storedX != chunkX || storedZ != chunkZ) {
                IOException ex = new IOException("chunk claims to be " + storedX + "," + storedZ
                        + " but was stored as " + chunkX + "," + chunkZ);
                quarantineCorruptChunk(path, chunkX, chunkZ, ex);
                throw ex;
            }
            com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState[] blocks = new com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState[BLOCKS_PER_CHUNK];
            for (int i = 0; i < BLOCKS_PER_CHUNK; i++) {
                int globalId = in.readInt();
                com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState state = com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState.getByGlobalId(globalId);
                if (state == null) {
                    IOException ex = new IOException("invalid block state id " + globalId + " at index " + i);
                    quarantineCorruptChunk(path, chunkX, chunkZ, ex);
                    throw ex;
                }
                blocks[i] = state;
            }
            // Reject unexpected trailing data
            try {
                if (in.available() > 0) {
                    // Some streams may not support available meaningfully; also try to detect
                    int b = in.read();
                    if (b != -1) {
                        IOException ex = new IOException("unexpected trailing data in chunk file");
                        quarantineCorruptChunk(path, chunkX, chunkZ, ex);
                        throw ex;
                    }
                }
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("trailing")) throw e;
                // ignore other availability issues
            }
            return blocks;
        } catch (IOException e) {
            if (!(e.getMessage() != null && (e.getMessage().contains("chunk claims") || e.getMessage().contains("bad chunk") || e.getMessage().contains("unsupported chunk") || e.getMessage().contains("invalid block state") || e.getMessage().contains("trailing") || e.getMessage().contains("size limit")))) {
                quarantineCorruptChunk(path, chunkX, chunkZ, e);
            }
            throw e;
        }
    }

    private void quarantineCorruptChunk(Path path, int chunkX, int chunkZ, IOException cause) {
        try {
            if (!Files.isRegularFile(path)) {
                return;
            }
            String name = path.getFileName().toString();
            Path quarantineDir = regionDir.resolve("corrupt");
            Files.createDirectories(quarantineDir);
            String timestamp = String.valueOf(System.currentTimeMillis());
            Path target = quarantineDir.resolve(name + "." + timestamp + ".corrupt");
            try {
                Files.move(path, target);
            } catch (Exception ex) {
                // If move fails, copy as fallback to preserve the original
                try {
                    Files.copy(path, target);
                } catch (Exception ignored) {
                }
            }
            net.vibmc.server.VibMC server = net.vibmc.server.VibMC.getInstance();
            if (server != null && server.getLogger() != null) {
                server.getLogger().warn("Quarantined corrupt chunk %d,%d to %s: %s", chunkX, chunkZ, target, cause.getMessage());
            } else {
                System.err.println("Quarantined corrupt chunk " + chunkX + "," + chunkZ + " to " + target + ": " + cause.getMessage());
            }
        } catch (Exception ignored) {
        }
    }

    public void writeChunk(int chunkX, int chunkZ, com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState[] blocks) throws IOException {
        if (blocks.length != BLOCKS_PER_CHUNK) {
            throw new IOException("expected " + BLOCKS_PER_CHUNK + " blocks, got " + blocks.length);
        }
        prepare();
        Path dest = chunkPath(chunkX, chunkZ);
        Path tmp = dest.resolveSibling(dest.getFileName() + ".tmp");
        try (OutputStream raw = Files.newOutputStream(tmp);
             DataOutputStream out = new DataOutputStream(
                     new BufferedOutputStream(new GZIPOutputStream(raw)))) {
            out.writeInt(CHUNK_MAGIC);
            out.writeInt(FORMAT_VERSION);
            out.writeInt(chunkX);
            out.writeInt(chunkZ);
            for (int i = 0; i < BLOCKS_PER_CHUNK; i++) {
                if (blocks[i] == null) {
                    throw new IOException("null block state at index " + i);
                }
                out.writeInt(blocks[i].getGlobalId());
            }
        }
        moveIntoPlace(tmp, dest);
    }

    private static String readBoundedString(DataInputStream input, int maximumBytes, String label) throws IOException {
        int length = input.readUnsignedShort();
        if (length > maximumBytes) throw new IOException(label + " is too long");
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void writeBoundedString(DataOutputStream output, String value, int maximumBytes, String label) throws IOException {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > maximumBytes) throw new IOException(label + " is too long");
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private Path chunkPath(int chunkX, int chunkZ) {
        return regionDir.resolve("r." + chunkX + "." + chunkZ + ".chunk");
    }

    private static void moveIntoPlace(Path tmp, Path dest) throws IOException {
        try {
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
