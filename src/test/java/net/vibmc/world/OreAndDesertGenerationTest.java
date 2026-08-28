package net.vibmc.world;

import net.vibmc.world.gen.OreGenerator;
import net.vibmc.world.gen.TerrainGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ore veins and desert cacti. */
class OreAndDesertGenerationTest {
    @Test
    void everyOreTypeGeneratesSomewhere() {
        TerrainGenerator terrain = new TerrainGenerator(4242L);
        Set<String> found = new HashSet<>();
        for (int x = 0; x < 160; x++) {
            for (int z = 0; z < 160; z++) {
                for (int y = 6; y < 90; y += 3) {
                    com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState ore =
                            OreGenerator.oreAt(terrain, x, y, z);
                    if (ore != null) found.add(ore.getType().getName().toString());
                }
            }
        }
        for (String expected : new String[]{"coal_ore", "iron_ore",
                "gold_ore", "redstone_ore", "diamond_ore",
                "lapis_ore"}) {
            assertTrue(found.contains(expected), "expected " + expected + " to generate, found " + found);
        }
    }

    @Test
    void oreGeneratesInVeinsRatherThanSingleBlocks() {
        TerrainGenerator terrain = new TerrainGenerator(99L);
        int veins = 0;
        int clustered = 0;
        for (int x = 0; x < 120; x++) {
            for (int z = 0; z < 120; z++) {
                for (int y = 6; y < 60; y++) {
                    if (OreGenerator.oreAt(terrain, x, y, z) == null) continue;
                    veins++;
                    // A vein block should touch at least one more block of the same ore.
                    boolean touching =
                            sameOre(terrain, x, y, z, x + 1, y, z) || sameOre(terrain, x, y, z, x - 1, y, z)
                            || sameOre(terrain, x, y, z, x, y + 1, z) || sameOre(terrain, x, y, z, x, y - 1, z)
                            || sameOre(terrain, x, y, z, x, y, z + 1) || sameOre(terrain, x, y, z, x, y, z - 1);
                    if (touching) clustered++;
                }
            }
        }
        assertTrue(veins > 100, "ore should be reasonably common, found " + veins);
        assertTrue(clustered * 100L / veins > 80,
                "most ore blocks should belong to a vein, only " + (clustered * 100L / veins) + "% did");
    }

    private static boolean sameOre(TerrainGenerator terrain, int x, int y, int z, int nx, int ny, int nz) {
        com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState first =
                OreGenerator.oreAt(terrain, x, y, z);
        com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState second =
                OreGenerator.oreAt(terrain, nx, ny, nz);
        return first != null && second != null && Blocks.same(first, second);
    }

    @Test
    void deepOresStayDeepAndCoalDoesNot() {
        TerrainGenerator terrain = new TerrainGenerator(555L);
        for (int x = 0; x < 200; x++) {
            for (int z = 0; z < 200; z++) {
                for (int y = 40; y < 120; y += 2) {
                    com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState ore =
                            OreGenerator.oreAt(terrain, x, y, z);
                    if (ore == null) continue;
                    assertTrue(!Blocks.same(ore, Blocks.DIAMOND_ORE) && !Blocks.same(ore, Blocks.REDSTONE_ORE),
                            "diamond and redstone must stay below y=16, found one at y=" + y);
                }
            }
        }
    }

    @Test
    void cactiGenerateOnDesertSand() {
        World world = new World(31337L, "desert-test");
        int cacti = 0;
        int checkedChunks = 0;
        for (int chunkX = 0; chunkX < 24 && cacti == 0; chunkX++) {
            for (int chunkZ = 0; chunkZ < 24 && cacti == 0; chunkZ++) {
                // Only generate chunks whose centre is desert, so the test stays quick.
                if (!"minecraft:desert".equals(world.biomeAt(chunkX * 16 + 8, chunkZ * 16 + 8))) continue;
                checkedChunks++;
                WorldChunk chunk = world.getChunk(chunkX, chunkZ);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 60; y < 120; y++) {
                            if (!Blocks.same(chunk.getBlock(x, y, z), Blocks.CACTUS)) continue;
                            cacti++;
                            boolean supported = Blocks.same(chunk.getBlock(x, y - 1, z), Blocks.SAND)
                                    || Blocks.same(chunk.getBlock(x, y - 1, z), Blocks.CACTUS);
                            assertTrue(supported, "a cactus must sit on sand or another cactus");
                        }
                    }
                }
            }
        }
        assertTrue(checkedChunks > 0, "the test seed must contain desert");
        assertTrue(cacti > 0, "deserts should generate cacti");
    }

    @Test
    void cactiDoNotGenerateOutsideDeserts() {
        World world = new World(2024L, "no-cactus-test");
        for (int chunkX = 0; chunkX < 6; chunkX++) {
            for (int chunkZ = 0; chunkZ < 6; chunkZ++) {
                if ("minecraft:desert".equals(world.biomeAt(chunkX * 16 + 8, chunkZ * 16 + 8))) continue;
                WorldChunk chunk = world.getChunk(chunkX, chunkZ);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        if (!"minecraft:desert".equals(world.biomeAt(chunkX * 16 + x, chunkZ * 16 + z))) {
                            for (int y = 0; y < 256; y++) {
                                assertTrue(!Blocks.same(chunk.getBlock(x, y, z), Blocks.CACTUS),
                                        "cacti must not generate outside desert biomes");
                            }
                        }
                    }
                }
            }
        }
    }
}
