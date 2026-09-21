package net.vibmc.network.packetevents;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.NibbleArray3d;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v1_16.Chunk_v1_9;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.Palette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.ListPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.MapPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.storage.LegacyFlexibleStorage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.PaletteType;
import net.vibmc.world.Blocks;
import net.vibmc.world.WorldChunk;
import net.vibmc.world.WorldEnvironment;
import net.vibmc.world.block.BlockState;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Converts server-owned chunks into a Column matching a protocol-340 PacketEvents wire model.
 * vib-MC only speaks 1.12.2 (protocol 340), so the pre-1.13 legacy palette format is the one
 * path: the combined block ids our world stores are exactly what that format consumes.
 */
public final class PacketEventsChunkAdapter {
    private PacketEventsChunkAdapter() {}

    public static WrapperPlayServerChunkData wrap(WorldChunk source) {
        return wrap(source, ClientVersion.V_1_12_2);
    }

    public static WrapperPlayServerChunkData wrap(WorldChunk source, ClientVersion version) {
        BaseChunk[] sections = new BaseChunk[16];
        boolean skylight = source.world().environment() == WorldEnvironment.OVERWORLD;
        int[] highest = new int[256];
        if (skylight) {
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                highest[z * 16 + x] = source.world().getHighestBlockY(source.chunkX() * 16 + x, source.chunkZ() * 16 + z);
        }

        for (int section = 0; section < 16; section++) {
            Chunk_v1_9 target = createLegacyPaletteSection(source, section);
            NibbleArray3d blockLight = new NibbleArray3d(4096);
            NibbleArray3d skyLight = new NibbleArray3d(4096);
            boolean containsBlocks = false;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                BlockState block = source.getBlock(x, section * 16 + y, z);
                if (!Blocks.same(block, Blocks.AIR)) containsBlocks = true;
                if (skylight && section * 16 + y >= highest[z * 16 + x]) {
                    skyLight.set(x, y, z, 15);
                }
            }
            if (!containsBlocks) continue;
            target.setBlockLight(blockLight);
            target.setSkyLight(skylight ? skyLight : null);
            sections[section] = target;
        }

        byte[] biomes = new byte[256];
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            biomes[z * 16 + x] = (byte) biomeId(source.world().biomeAt(
                    source.chunkX() * 16 + x, source.chunkZ() * 16 + z));
        }
        Column column = new Column(source.chunkX(), source.chunkZ(), true, sections, new TileEntity[0], biomes);
        return new WrapperPlayServerChunkData(column);
    }

    /**
     * Builds the legacy palette at its final size so PE never replaces the packing with the
     * post-1.16 BitStorage format.
     */
    private static Chunk_v1_9 createLegacyPaletteSection(WorldChunk source, int section) {
        int[] states = new int[4096];
        Set<Integer> unique = new LinkedHashSet<>();
        unique.add(Blocks.AIR.getGlobalId());
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int index = (y * 16 + z) * 16 + x;
            int state = source.getBlock(x, section * 16 + y, z).getGlobalId();
            states[index] = state;
            unique.add(state);
        }
        if (unique.size() > 256) throw new IllegalArgumentException(
                "Pre-1.16 section palette exceeds 256 unique states: " + unique.size());
        int bits = 4;
        while ((1 << bits) < unique.size()) bits++;
        Palette palette = bits <= 4 ? new ListPalette(4) : new MapPalette(bits);
        for (int state : unique) palette.stateToId(state);
        LegacyFlexibleStorage storage = new LegacyFlexibleStorage(bits, 4096);
        for (int index = 0; index < states.length; index++) storage.set(index, palette.stateToId(states[index]));
        return new Chunk_v1_9(Integer.MAX_VALUE, new DataPalette(palette, storage, PaletteType.CHUNK));
    }

    /** Resolves a biome name to its pre-1.13 numeric id through PacketEvents' biome registry. */
    public static int biomeId(String biome) {
        com.github.retrooper.packetevents.protocol.world.biome.Biome target =
                com.github.retrooper.packetevents.protocol.world.biome.Biomes.getRegistry()
                        .getByName(ClientVersion.V_1_12_2, biome);
        if (target == null && "minecraft:nether_wastes".equals(biome)) {
            target = com.github.retrooper.packetevents.protocol.world.biome.Biomes.NETHER;
        }
        if (target == null) throw new IllegalArgumentException(
                "Biome " + biome + " does not exist in 1.12.2");
        int id = target.getId(ClientVersion.V_1_12_2);
        if (id < 0 && "minecraft:nether_wastes".equals(biome)) {
            id = com.github.retrooper.packetevents.protocol.world.biome.Biomes.NETHER.getId(ClientVersion.V_1_12_2);
        }
        if (id < 0) throw new IllegalArgumentException("Biome " + biome + " has no 1.12.2 id");
        return id;
    }
}