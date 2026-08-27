package net.vibmc.network.packetevents;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.NibbleArray3d;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.protocol.world.chunk.LightData;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v1_16.Chunk_v1_9;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTLongArray;
import java.util.BitSet;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v1_8.Chunk_v1_8;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.PaletteType;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.Palette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.ListPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.MapPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.storage.LegacyFlexibleStorage;
import com.github.retrooper.packetevents.protocol.world.chunk.storage.BitStorage;
import java.util.LinkedHashSet;
import java.util.Set;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import net.vibmc.world.Blocks;
import net.vibmc.world.WorldChunk;
import net.vibmc.world.WorldEnvironment;


/** Converts server-owned chunks into a Column matching one connection's PacketEvents wire model. */
public final class PacketEventsChunkAdapter {
    private PacketEventsChunkAdapter() {}

    public static WrapperPlayServerChunkData wrap(WorldChunk source) {
        return wrap(source, ClientVersion.V_1_12_2);
    }

    public static WrapperPlayServerChunkData wrap(WorldChunk source, ClientVersion version) {
        boolean modern18 = version.isNewerThanOrEquals(ClientVersion.V_1_18);
        boolean legacy18 = version.isOlderThanOrEquals(ClientVersion.V_1_8);
        boolean legacyPalette = !legacy18 && version.isOlderThan(ClientVersion.V_1_16);
        BaseChunk[] sections = modern18 ? new Chunk_v1_18[16]
                : legacy18 ? new Chunk_v1_8[16] : new BaseChunk[16];
        boolean skylight = source.world().environment() == WorldEnvironment.OVERWORLD;
        int[] highest=new int[256];
        if(skylight)for(int z=0;z<16;z++)for(int x=0;x<16;x++)highest[z*16+x]=
                source.world().getHighestBlockY(source.chunkX()*16+x,source.chunkZ()*16+z);

        for (int section = 0; section < 16; section++) {
            BaseChunk target;
            if (modern18) {
                target = createModernPaletteSection(source,section,version);
            } else if (legacy18) {
                target = new Chunk_v1_8(skylight);
            } else if (legacyPalette) {
                target = createLegacyPaletteSection(source, section, version);
            } else {
                Chunk_v1_9 paletteSection = new Chunk_v1_9(0, PaletteType.CHUNK.create());
                paletteSection.set(0, 0, 0, PacketEventsStateMappings.id(Blocks.AIR, version));
                target = paletteSection;
            }
            NibbleArray3d blockLight = new NibbleArray3d(4096);
            NibbleArray3d skyLight = new NibbleArray3d(4096);
            boolean containsBlocks = false;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                WrappedBlockState block = source.getBlock(x, section * 16 + y, z);
                if (!legacyPalette && !modern18) {
                    target.set(x, y, z, PacketEventsStateMappings.id(block, version));
                }
                if (!Blocks.same(block, Blocks.AIR)) containsBlocks = true;
                if (skylight && section * 16 + y >= highest[z*16+x]) {
                    skyLight.set(x, y, z, 15);
                }
            }
            if (!modern18 && !containsBlocks) continue;
            if (target instanceof Chunk_v1_9) {
                Chunk_v1_9 paletteSection = (Chunk_v1_9) target;
                // 1.14+ moved lighting out of chunk section payloads. PE's writer emits any
                // attached arrays blindly, so attaching them would shift every later section.
                boolean inlineLight = version.isOlderThan(ClientVersion.V_1_14);
                paletteSection.setBlockLight(inlineLight ? blockLight : null);
                paletteSection.setSkyLight(inlineLight && skylight ? skyLight : null);
            }
            sections[section] = target;
        }

        Column column;
        if (modern18) {
            NBTCompound heightmaps=new NBTCompound();
            long[] heights=heightmap(source);
            heightmaps.setTag("MOTION_BLOCKING",new NBTLongArray(heights));
            heightmaps.setTag("WORLD_SURFACE",new NBTLongArray(heights.clone()));
            column=new Column(source.chunkX(),source.chunkZ(),true,sections,new TileEntity[0],heightmaps);
            return new WrapperPlayServerChunkData(column,lightData(skylight));
        } else if (version.isOlderThan(ClientVersion.V_1_13)) {
            byte[] biomes = new byte[256];
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                biomes[z * 16 + x] = (byte) biomeId(source.world().biomeAt(
                        source.chunkX() * 16 + x, source.chunkZ() * 16 + z), version);
            }
            column = new Column(source.chunkX(), source.chunkZ(), true, sections, new TileEntity[0], biomes);
        } else {
            int biomeCount = version.isNewerThanOrEquals(ClientVersion.V_1_15) ? 1024 : 256;
            int[] biomes = new int[biomeCount];
            for (int index = 0; index < biomeCount; index++) {
                int x = index & 15;
                int z = (index >> 4) & 15;
                if (biomeCount == 1024) {
                    x = (index & 3) * 4;
                    z = ((index >> 2) & 3) * 4;
                }
                biomes[index] = biomeId(source.world().biomeAt(
                        source.chunkX() * 16 + x, source.chunkZ() * 16 + z), version);
            }
            column = new Column(source.chunkX(), source.chunkZ(), true, sections, new TileEntity[0], biomes);
        }
        return new WrapperPlayServerChunkData(column);
    }

    /** Builds modern palettes at final size so 17-state sections never pass through PE resize logic. */
    private static Chunk_v1_18 createModernPaletteSection(WorldChunk source,int section,
                                                           ClientVersion version){
        int[] blocks=new int[4096];Set<Integer> blockStates=new LinkedHashSet<>();
        blockStates.add(PacketEventsStateMappings.id(Blocks.AIR,version));int blockCount=0;
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            WrappedBlockState sourceState=source.getBlock(x,section*16+y,z);
            int state=PacketEventsStateMappings.id(sourceState,version),index=(y*16+z)*16+x;
            blocks[index]=state;blockStates.add(state);if(!Blocks.same(sourceState,Blocks.AIR))blockCount++;
        }
        DataPalette blockPalette=createModernPalette(blocks,blockStates,4);

        int[] biomeValues=new int[64];Set<Integer> biomeStates=new LinkedHashSet<>();int i=0;
        for(int y=0;y<4;y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++){
            int id=biomeId(source.world().biomeAt(source.chunkX()*16+x*4,source.chunkZ()*16+z*4),version);
            biomeValues[i++]=id;biomeStates.add(id);
        }
        DataPalette biomePalette=createModernPalette(biomeValues,biomeStates,1);
        return new Chunk_v1_18(version,blockCount,blockPalette,biomePalette);
    }

    private static DataPalette createModernPalette(int[] values,Set<Integer> unique,int minimumBits){
        int bits=minimumBits;while((1<<bits)<unique.size())bits++;
        if(bits>8)throw new IllegalArgumentException("Modern indirect palette exceeds 256 states: "+unique.size());
        Palette palette=bits<=4?new ListPalette(bits):new MapPalette(bits);
        for(int state:unique)palette.stateToId(state);
        BitStorage storage=new BitStorage(bits,values.length);
        for(int index=0;index<values.length;index++)storage.set(index,palette.stateToId(values[index]));
        return new DataPalette(palette,storage,values.length==64?PaletteType.BIOME:PaletteType.CHUNK);
    }

    /** Builds pre-1.16 palettes at their final size so PE never replaces legacy packing with BitStorage. */
    private static Chunk_v1_9 createLegacyPaletteSection(WorldChunk source,int section,
                                                          ClientVersion version){
        int[] states=new int[4096];
        Set<Integer> unique=new LinkedHashSet<>();
        unique.add(PacketEventsStateMappings.id(Blocks.AIR,version));
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            int index=(y*16+z)*16+x;
            int state=PacketEventsStateMappings.id(source.getBlock(x,section*16+y,z),version);
            states[index]=state;unique.add(state);
        }
        if(unique.size()>256)throw new IllegalArgumentException(
                "Pre-1.16 section palette exceeds 256 unique states: "+unique.size());
        int bits=4;while((1<<bits)<unique.size())bits++;
        Palette palette=bits<=4?new ListPalette(4):new MapPalette(bits);
        for(int state:unique)palette.stateToId(state);
        LegacyFlexibleStorage storage=new LegacyFlexibleStorage(bits,4096);
        for(int index=0;index<states.length;index++)storage.set(index,palette.stateToId(states[index]));
        return new Chunk_v1_9(Integer.MAX_VALUE,new DataPalette(palette,storage,PaletteType.CHUNK));
    }

    private static long[] heightmap(WorldChunk source){
        int bits=9,valuesPerLong=64/bits;
        long[] data=new long[(256+valuesPerLong-1)/valuesPerLong];
        for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            int index=z*16+x;
            long value=source.world().getHighestBlockY(source.chunkX()*16+x,source.chunkZ()*16+z)+1L;
            int longIndex=index/valuesPerLong,offset=(index%valuesPerLong)*bits;
            data[longIndex]|=value<<offset;
        }
        return data;
    }

    private static LightData lightData(boolean skylight){
        final int layers=18; // 16 classic sections plus one boundary layer on each side
        BitSet skyMask=new BitSet(layers),blockMask=new BitSet(layers);
        BitSet emptySky=new BitSet(layers),emptyBlock=new BitSet(layers);
        emptyBlock.set(0,layers);
        byte[][] skyArrays;
        if(skylight){
            skyMask.set(0,layers);
            skyArrays=new byte[layers][2048];
            for(byte[] array:skyArrays)java.util.Arrays.fill(array,(byte)0xff);
        }else{
            emptySky.set(0,layers);
            skyArrays=new byte[0][];
        }
        return new LightData(true,blockMask,skyMask,emptyBlock,emptySky,
                skyArrays.length,0,skyArrays,new byte[0][]);
    }

    public static int biomeId(String biome, ClientVersion version) {
        com.github.retrooper.packetevents.protocol.world.biome.Biome target=
                com.github.retrooper.packetevents.protocol.world.biome.Biomes.getRegistry()
                        .getByName(version,biome);
        if(target==null&&"minecraft:nether_wastes".equals(biome)
                &&version.isOlderThan(ClientVersion.V_1_16)){
            target=com.github.retrooper.packetevents.protocol.world.biome.Biomes.NETHER;
        }
        if(target==null)throw new IllegalArgumentException(
                "Biome "+biome+" does not exist in "+version.getReleaseName());
        int id=target.getId(version);
        if(id<0&&"minecraft:nether_wastes".equals(biome)
                &&version.isOlderThan(ClientVersion.V_1_16)){
            id=com.github.retrooper.packetevents.protocol.world.biome.Biomes.NETHER.getId(version);
        }
        if(id<0)throw new IllegalArgumentException(
                "Biome "+biome+" has no ID in "+version.getReleaseName());
        return id;
    }

}
