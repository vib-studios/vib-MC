package net.vibmc.world;

import net.vibmc.world.storage.WorldStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DimensionGenerationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void overworldHasVanillaScaleTerrainWithoutGeneratedPortals() {
        World world = world("overworld", WorldEnvironment.OVERWORLD);
        WorldChunk chunk = world.getChunk(0, 0);

        assertTrue(world.getHighestSolidY(8, 8) > 50);
        assertTrue(contains(chunk, Blocks.WATER) || contains(chunk, Blocks.GRASS));
        assertTrue(contains(chunk, Blocks.COAL_ORE) || contains(chunk, Blocks.IRON_ORE));
        assertTrue(hasUndergroundCave(chunk));
        assertFalse(contains(chunk, Blocks.NETHER_PORTAL));
        assertFalse(contains(chunk, Blocks.END_PORTAL));
    }

    @Test
    void netherHasCeilingAndCavernsWithoutGeneratedPortal() {
        World world = world("nether", WorldEnvironment.NETHER);
        WorldChunk chunk = world.getChunk(0, 0);

        assertEquals(Blocks.BEDROCK, chunk.getBlock(0, 0, 0));
        assertEquals(Blocks.BEDROCK, chunk.getBlock(0, 127, 0));
        assertTrue(contains(chunk, Blocks.NETHERRACK));
        assertFalse(contains(chunk, Blocks.NETHER_PORTAL));
    }

    @Test
    void endHasAnIslandWithoutGeneratedPortalOrDragonContent() {
        World world = world("end", WorldEnvironment.END);
        WorldChunk chunk = world.getChunk(0, 0);

        assertTrue(contains(chunk, Blocks.END_STONE));
        assertFalse(contains(chunk, Blocks.END_PORTAL));
    }

    private World world(String name, WorldEnvironment environment) {
        return new World(12345L, name,
                new WorldStorage(temporaryDirectory.resolve(name).toString()), environment);
    }

    private static boolean hasUndergroundCave(WorldChunk chunk) {
        for (int x=0;x<16;x++) for(int z=0;z<16;z++) for(int y=10;y<50;y++) {
            if (chunk.getBlock(x,y,z)==Blocks.AIR
                    && chunk.getBlock(x,Math.min(60,y+10),z)!=Blocks.AIR) return true;
        }
        return false;
    }

    private static boolean contains(WorldChunk chunk, com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState expected) {
        for (com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState block : chunk.blocks()) if (Blocks.same(block, expected)) return true;
        return false;
    }
}
