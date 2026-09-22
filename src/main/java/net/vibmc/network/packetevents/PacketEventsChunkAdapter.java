package net.vibmc.network.packetevents;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.LightData;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.PaletteType;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import net.vibmc.registry.RegistryCodec;
import net.vibmc.world.Blocks;
import net.vibmc.world.WorldChunk;

import java.util.*;

/** Direct conversion from vib-MC WorldChunk into version-safe PacketEvents chunk data. */
public final class PacketEventsChunkAdapter {
    private PacketEventsChunkAdapter() {}

    public static WrapperPlayServerChunkData create(WorldChunk source, ClientVersion version) {
        Column column = column(source, version);
        if (version.isNewerThanOrEquals(ClientVersion.V_1_20_5)) {
            return new WrapperPlayServerChunkData(column);
        }
        BitSet mask = new BitSet();
        for (int i = 0; i < 16; i++) {
            if (!source.isEmptySection(i)) mask.set(i);
        }
        LightData light = version.isNewerThanOrEquals(ClientVersion.V_1_14)
                ? new LightData(false, new BitSet(), new BitSet(), new BitSet(), new BitSet(), Collections.emptyList(), Collections.emptyList())
                : null;
        return new WrapperPlayServerChunkData(column, light);
    }

    public static Column column(WorldChunk source, ClientVersion version) {
        if (version.isOlderThan(ClientVersion.V_1_18)) {
            return legacyColumn(source, version);
        }
        com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk[] sections =
                new com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk[16];
        for (int sectionY = 0; sectionY < 16; sectionY++) {
            sections[sectionY] = section(source, sectionY, version);
        }
        return new Column(source.chunkX(), source.chunkZ(), true, sections, new TileEntity[0]);
    }

    private static Column legacyColumn(WorldChunk source, ClientVersion version) {
        com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk[] sections =
                new com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk[16];
        for (int sectionY = 0; sectionY < 16; sectionY++) {
            if (version.isOlderThan(ClientVersion.V_1_16)) {
                com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_13.Chunk_v1_13 paletteSection =
                        new com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_13.Chunk_v1_13();
                paletteSection.set(0, 0, 0, PacketEventsStateMappings.id(Blocks.AIR, version));
                boolean nonAir = false;
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            WrappedBlockState block = source.getBlock(x, sectionY * 16 + y, z);
                            if (Blocks.same(block, Blocks.AIR)) continue;
                            nonAir = true;
                            paletteSection.set(x, y, z, PacketEventsStateMappings.id(block, version));
                        }
                    }
                }
                sections[sectionY] = nonAir ? paletteSection : new com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_13.Chunk_v1_13();
            } else {
                sections[sectionY] = section(source, sectionY, version);
            }
        }
        if (version.isOlderThan(ClientVersion.V_1_13)) {
            byte[] biomes = new byte[256];
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    biomes[z * 16 + x] = (byte) biomeId(source.world().biomeAt(
                            source.chunkX() * 16 + x, source.chunkZ() * 16 + z), version);
                }
            }
            return new Column(source.chunkX(), source.chunkZ(), true, sections, new TileEntity[0], biomes);
        } else {
            int biomeCount = version.isNewerThanOrEquals(ClientVersion.V_1_15) ? 1024 : 256;
            int[] biomes = new int[biomeCount];
            for (int index = 0; index < biomeCount; index++) {
                int x = (index & 3) * 4;
                int z = ((index >> 2) & 3) * 4;
                if (biomeCount == 1024) {
                    x = (index & 3) * 4;
                    z = ((index >> 2) & 3) * 4;
                }
                biomes[index] = biomeId(source.world().biomeAt(
                        source.chunkX() * 16 + x, source.chunkZ() * 16 + z), version);
            }
            return new Column(source.chunkX(), source.chunkZ(), true, sections, new TileEntity[0], biomes);
        }
    }

    private static com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk section(
            WorldChunk source, int section, ClientVersion version) {
        int[] blockStates = new ArrayList<Integer>().stream().mapToInt(i -> i).toArray();
        List<Integer> statesList = new ArrayList<>();
        statesList.add(PacketEventsStateMappings.id(Blocks.AIR, version));
        int blockCount = 0;
        int[] blockValues = new int[4096];
        Set<Integer> uniqueStates = new LinkedHashSet<>();
        uniqueStates.add(PacketEventsStateMappings.id(Blocks.AIR, version));
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    WrappedBlockState sourceState = source.getBlock(x, section * 16 + y, z);
                    if (!Blocks.same(sourceState, Blocks.AIR)) blockCount++;
                    int state = PacketEventsStateMappings.id(sourceState, version);
                    int index = (y * 16 + z) * 16 + x;
                    blockValues[index] = state;
                    uniqueStates.add(state);
                }
            }
        }
        DataPalette blockPalette = createModernPalette(blockValues, uniqueStates, 0);

        int[] biomeValues = new int[64];
        Set<Integer> biomeStates = new LinkedHashSet<>();
        int i = 0;
        for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 4; x++) {
                    int id = biomeId(source.world().biomeAt(source.chunkX() * 16 + x * 4, source.chunkZ() * 16 + z * 4), version);
                    biomeValues[i++] = id;
                    biomeStates.add(id);
                }
            }
        }
        DataPalette biomePalette = createModernPalette(biomeValues, biomeStates, 1);
        return new Chunk_v1_18(version, blockCount, blockPalette, biomePalette);
    }

    private static DataPalette createModernPalette(int[] values, Set<Integer> unique, int typeIndex) {
        PaletteType paletteType = typeIndex == 0 ? PaletteType.BLOCK_STATES : PaletteType.BIOME;
        if (unique.size() == 1) {
            DataPalette single = DataPalette.createSingleValuePalette(paletteType, values[0]);
            for (int val : values) single.set(0, 0, 0, val);
            return single;
        }
        List<Integer> list = new ArrayList<>(unique);
        int bitsPerEntry = Math.max(typeIndex == 0 ? 4 : 1, (int) Math.ceil(Math.log(list.size()) / Math.log(2)));
        if (typeIndex == 0 && bitsPerEntry > 8) {
            DataPalette direct = DataPalette.createDirectPalette(paletteType, 15);
            int index = 0;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        direct.set(x, y, z, values[index++]);
                    }
                }
            }
            return direct;
        }
        DataPalette indirect = DataPalette.createIndirectPalette(paletteType, bitsPerEntry, list);
        if (typeIndex == 0) {
            int index = 0;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        indirect.set(x, y, z, values[index++]);
                    }
                }
            }
        } else {
            int index = 0;
            for (int y = 0; y < 4; y++) {
                for (int z = 0; z < 4; z++) {
                    for (int x = 0; x < 4; x++) {
                        indirect.set(x, y, z, values[index++]);
                    }
                }
            }
        }
        return indirect;
    }

    public static int biomeId(String biome, ClientVersion version) {
        return RegistryCodec.biomeIndex(biome, version);
    }
}
