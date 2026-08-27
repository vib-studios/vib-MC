package net.vibmc.world.structure;

import net.vibmc.world.Blocks;
import net.vibmc.world.WorldChunk;
import net.vibmc.world.World;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StructureGenerationTest {
    @Test
    void defaultTreeTemplateDecoratesNewChunks() throws Exception {
        StructureRegistry.clear();
        StructureRegistry.reload();
        World world=new World(2L,"test");
        boolean found=false;
        for(int x=-2;x<=2&&!found;x++)for(int z=-2;z<=2&&!found;z++){
            WorldChunk chunk=world.getChunk(x,z);
            for(com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState block:chunk.blocks())if(Blocks.same(block,Blocks.WOOD)){found=true;break;}
        }
        assertTrue(found,"default structure set should generate oak trees");
        StructureRegistry.clear();
    }

    @Test
    void multiPieceVillageIsGuaranteedNearSpawn() throws Exception {
        StructureRegistry.clear();
        StructureRegistry.reload();
        World world = new World(12345L, "village-test");
        int[] spawn = StructureRegistry.spawnPoint(world);
        assertTrue(spawn != null && Math.abs(spawn[0] - 8) <= 72 && Math.abs(spawn[1] - 8) <= 72,
                "configured village spawn must remain in the spawn search area");
        assertEquals(1, StructureRegistry.composites().size(), "village must be a registered composite structure");
        int centerX=spawn[0],centerZ=spawn[1]+8;
        net.vibmc.world.gen.TerrainGenerator terrain=new net.vibmc.world.gen.TerrainGenerator(world.seed());
        int wellY=terrain.getHeight(centerX,centerZ);
        for(int x=centerX-1;x<=centerX+1;x++)for(int z=centerZ-1;z<=centerZ+1;z++)assertTrue(Blocks.same(world.getBlockAt(x,wellY,z),Blocks.WATER),"roads must not cover the well water");
        int[][] doors={{centerX-14,centerZ-11},{centerX+14,centerZ-11},{centerX-14,centerZ+11},{centerX+14,centerZ+11}};
        for(int[]door:doors){boolean connected=false;int surface=terrain.getHeight(door[0],door[1]);for(int y=surface-3;y<=surface+3;y++)if(Blocks.same(world.getBlockAt(door[0],y,door[1]),Blocks.GRAVEL))connected=true;assertTrue(connected,"house doorway at "+door[0]+","+door[1]+" must connect directly to a road stub");}
        assertTrue(Blocks.same(world.getBlockAt(centerX-14,terrain.getHeight(centerX-14,centerZ-14),centerZ-14),Blocks.STONE),"unrotated house anchor must land at its declared node");
        assertTrue(Blocks.same(world.getBlockAt(centerX+14,terrain.getHeight(centerX+14,centerZ-14),centerZ-14),Blocks.STONE),"rotated house anchor must remain at its declared node");
        int farmGravel=0;for(int x=centerX-5;x<=centerX+5;x++)for(int z=centerZ+24;z<=centerZ+32;z++)for(int y=0;y<256;y++)if(Blocks.same(world.getBlockAt(x,y,z),Blocks.GRAVEL))farmGravel++;
        assertTrue(farmGravel<=3,"the farm must not overlap a road; only its entrance threshold may be gravel");

        int planks = 0, gravel = 0, water = 0, workstations = 0, villageLeaves=0;
        for (int chunkX = Math.floorDiv(spawn[0] - 48, 16);
             chunkX <= Math.floorDiv(spawn[0] + 48, 16); chunkX++) {
            for (int chunkZ = Math.floorDiv(spawn[1] - 48, 16);
                 chunkZ <= Math.floorDiv(spawn[1] + 48, 16); chunkZ++) {
                WorldChunk chunk = world.getChunk(chunkX, chunkZ);
                for (com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState block : chunk.blocks()) {
                    if (Blocks.same(block, Blocks.OAK_PLANKS)) planks++;
                    else if (Blocks.same(block, Blocks.GRAVEL)) gravel++;
                    else if (Blocks.same(block, Blocks.WATER)) water++;
                    else if (Blocks.same(block, Blocks.CRAFTING_TABLE) || Blocks.same(block, Blocks.FURNACE)) workstations++;
                }
            }
        }
        assertTrue(planks > 300, "four village houses and well roofs must generate");
        assertTrue(gravel > 100, "village roads must generate");
        assertTrue(water > 10, "village well and farm irrigation must generate");
        assertEquals(8, workstations, "each of four houses gets two workstations");
        for(int x=centerX-34;x<=centerX+34;x++)for(int z=centerZ-34;z<=centerZ+34;z++)for(int y=0;y<256;y++)if(Blocks.same(world.getBlockAt(x,y,z),Blocks.LEAVES))villageLeaves++;
        assertEquals(0,villageLeaves,"spawn village excludes the oak-tree structure across its footprint");
        StructureRegistry.clear();
    }
}
