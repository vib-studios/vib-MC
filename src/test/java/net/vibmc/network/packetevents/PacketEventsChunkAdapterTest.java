package net.vibmc.network.packetevents;

import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v1_16.Chunk_v1_9;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import net.vibmc.world.World;
import net.vibmc.world.WorldEnvironment;
import net.vibmc.world.storage.WorldStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PacketEventsChunkAdapterTest {
    @TempDir Path temporaryDirectory;

    @Test void overworldOmitsEmptySectionsAndLightsIncludedSections(){assertSections(packet(WorldEnvironment.OVERWORLD),true);}
    @Test void netherOmitsEmptySectionsAndNeverWritesSkyLightBytes(){assertSections(packet(WorldEnvironment.NETHER),false);}
    @Test void endOmitsEmptySectionsAndNeverWritesSkyLightBytes(){assertSections(packet(WorldEnvironment.END),false);}

    @Test
    void modernChunksUseClassicSixteenSectionHeight() {
        World world=new World(12345L,"modern",new WorldStorage(temporaryDirectory.resolve("modern").toString()),WorldEnvironment.OVERWORLD);
        net.vibmc.world.WorldChunk source=world.getChunk(0,0);
        WrapperPlayServerChunkData packet=PacketEventsChunkAdapter.wrap(source,
                com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_18);
        assertEquals(16,packet.getColumn().getChunks().length);
        for(BaseChunk section:packet.getColumn().getChunks()){
            assertTrue(section instanceof com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18);
            com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18 modern=
                    (com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18)section;
            assertTrue(modern.getChunkData().palette.size()>0,"modern block palette must contain idAt(0)");
            assertTrue(modern.getBiomeData().palette.size()>0,"modern biome palette must contain idAt(0)");
        }
        assertNotNull(packet.getLightData());
        assertEquals(PacketEventsStateMappings.id(source.getBlock(0,0,0),
                com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_18),
                packet.getColumn().getChunks()[0].getBlockId(0,0,0));
    }

    @Test
    void flattenedStatesKeepTheirSemanticBlockIdentity() {
        World world=new World(12345L,"flattened",new WorldStorage(temporaryDirectory.resolve("flattened").toString()),WorldEnvironment.OVERWORLD);
        net.vibmc.world.WorldChunk source=world.getChunk(0,0);
        source.setBlock(0,80,0,net.vibmc.world.Blocks.CRAFTING_TABLE);
        source.setBlock(1,80,0,net.vibmc.world.Blocks.GRASS);
        source.setBlock(2,80,0,net.vibmc.world.Blocks.END_PORTAL_FRAME_FILLED);
        com.github.retrooper.packetevents.protocol.player.ClientVersion version=
                com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_13;
        BaseChunk[] encoded=PacketEventsChunkAdapter.wrap(source,version).getColumn().getChunks();
        int grassState=encoded[5].getBlockId(1,0,0);
        assertTrue(grassState==8||grassState==9,"1.13 grass mapped to state "+grassState);
        assertEquals(3050,encoded[5].getBlockId(0,0,0));
        com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState expectedFrame=
                com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState.getDefaultState(
                        version,com.github.retrooper.packetevents.protocol.world.states.type.StateTypes.END_PORTAL_FRAME,true);
        expectedFrame.setEye(true);
        expectedFrame.setFacing(com.github.retrooper.packetevents.protocol.world.BlockFace.NORTH);
        // PE uses the 1.13.2 registry for base 1.13, so compare the corrected wire ID.
        assertEquals(expectedFrame.getGlobalId()-1,encoded[5].getBlockId(2,0,0));
    }

    @Test
    void largeLegacyPaletteKeepsLegacyFlexiblePacking() throws Exception {
        World world=new World(55L,"large-palette",new WorldStorage(temporaryDirectory.resolve("large-palette").toString()),WorldEnvironment.OVERWORLD);
        net.vibmc.world.WorldChunk source=world.getChunk(0,0);
        com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState[] states={
                net.vibmc.world.Blocks.AIR,net.vibmc.world.Blocks.STONE,net.vibmc.world.Blocks.GRASS,
                net.vibmc.world.Blocks.DIRT,net.vibmc.world.Blocks.WOOD,net.vibmc.world.Blocks.OAK_PLANKS,
                net.vibmc.world.Blocks.LEAVES,net.vibmc.world.Blocks.GLASS,net.vibmc.world.Blocks.WATER,
                net.vibmc.world.Blocks.LAVA,net.vibmc.world.Blocks.CHEST,net.vibmc.world.Blocks.FURNACE,
                net.vibmc.world.Blocks.CRAFTING_TABLE,net.vibmc.world.Blocks.SAND,net.vibmc.world.Blocks.GRAVEL,
                net.vibmc.world.Blocks.BEDROCK,net.vibmc.world.Blocks.ANDESITE,net.vibmc.world.Blocks.DIORITE,
                net.vibmc.world.Blocks.COAL_ORE,net.vibmc.world.Blocks.IRON_ORE,net.vibmc.world.Blocks.OBSIDIAN};
        for(int i=0;i<states.length;i++)source.setBlock(i&15,8+(i>>4),0,states[i]);
        Chunk_v1_9 section=(Chunk_v1_9)PacketEventsChunkAdapter.wrap(source,
                com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_13).getColumn().getChunks()[0];
        java.lang.reflect.Field field=Chunk_v1_9.class.getDeclaredField("dataPalette");field.setAccessible(true);
        com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette palette=
                (com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette)field.get(section);
        assertTrue(palette.storage instanceof com.github.retrooper.packetevents.protocol.world.chunk.storage.LegacyFlexibleStorage);
        assertTrue(palette.storage.getBitsPerEntry()>=5);

        com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18 modern=
                (com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18)
                        PacketEventsChunkAdapter.wrap(source,
                                com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_18)
                                .getColumn().getChunks()[0];
        assertTrue(modern.getChunkData().palette.size()>=17);
        assertEquals(5,modern.getChunkData().storage.getBitsPerEntry());
    }

    @Test
    void sectionPresenceAndContentsMatchAuthoritativeChunksAcrossCoordinates(){
        for(WorldEnvironment environment:WorldEnvironment.values()){
            String name="matrix-"+environment.name().toLowerCase();World world=new World(987654321L,name,new WorldStorage(temporaryDirectory.resolve(name).toString()),environment);
            for(int chunkX=-1;chunkX<=1;chunkX++)for(int chunkZ=-1;chunkZ<=1;chunkZ++){
                net.vibmc.world.WorldChunk source=world.getChunk(chunkX,chunkZ);BaseChunk[] encoded=PacketEventsChunkAdapter.wrap(source).getColumn().getChunks();
                for(int section=0;section<16;section++){
                    boolean containsBlocks=false;
                    for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                        int expected=source.getBlock(x,section*16+y,z).getGlobalId();if(expected!=0)containsBlocks=true;
                        if(encoded[section]!=null)assertEquals(expected,encoded[section].getBlockId(x,y,z),"state mismatch in "+environment+" chunk "+chunkX+","+chunkZ+" section "+section);
                    }
                    assertEquals(containsBlocks,encoded[section]!=null,"section presence mismatch in "+environment+" chunk "+chunkX+","+chunkZ+" section "+section);
                }
            }
        }
    }

    private WrapperPlayServerChunkData packet(WorldEnvironment environment){String name=environment.name().toLowerCase();World world=new World(12345L,name,new WorldStorage(temporaryDirectory.resolve(name).toString()),environment);return PacketEventsChunkAdapter.wrap(world.getChunk(0,0));}

    private static void assertSections(WrapperPlayServerChunkData packet,boolean expectsSkyLight){
        BaseChunk[] sections=packet.getColumn().getChunks();assertEquals(16,sections.length);int included=0,omitted=0;
        for(int index=0;index<sections.length;index++){
            if(sections[index]==null){omitted++;continue;}included++;Chunk_v1_9 section=(Chunk_v1_9)sections[index];assertFalse(section.isEmpty(),"included section "+index+" must contain blocks");assertNotNull(section.getBlockLight(),"section "+index+" block light");if(expectsSkyLight)assertNotNull(section.getSkyLight(),"section "+index+" sky light");else assertNull(section.getSkyLight(),"section "+index+" must omit sky light");
        }
        assertTrue(included>0);assertTrue(omitted>0);
    }
}
