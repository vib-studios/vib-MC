package net.vibmc.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnSafetyTest {
    /** Ocean columns with no dry land for well over a hundred blocks in any direction. */
    private static final long OCEAN_SEED = 42L;
    private static final int OCEAN_X = -560, OCEAN_Z = -520;

    @Test
    void drySpawnSearchLeavesAnAllOceanNeighbourhood() {
        World world = new World(OCEAN_SEED, "ocean-spawn");
        assertFalse(world.isDryStandingColumn(OCEAN_X, OCEAN_Z), "test fixture must start in open water");

        int[] spawn = world.findDrySpawn(OCEAN_X, OCEAN_Z, 8);

        assertFalse(spawn[0] == OCEAN_X && spawn[1] == OCEAN_Z,
                "an all-ocean search radius must not fall back to the starting column");
        assertTrue(world.isDryStandingColumn(spawn[0], spawn[1]),
                "the widened search must return dry land, not water at " + spawn[0] + "," + spawn[1]);
    }

    @Test
    void drySpawnSearchIsDryForEveryTestedSeed() {
        for (long seed : new long[]{3L, 42L, 12345L}) {
            World world = new World(seed, "dry-spawn-" + seed);
            int[] spawn = world.findDrySpawn(8, 8, 24);
            assertTrue(world.isDryStandingColumn(spawn[0], spawn[1]),
                    "seed " + seed + " spawned on a wet column at " + spawn[0] + "," + spawn[1]);
        }
    }

    @Test
    void dryPlatformReplacesTheWaterAboveALandlessColumn() {
        World world = new World(OCEAN_SEED, "ocean-platform");

        int standY = world.createDryPlatform(OCEAN_X, OCEAN_Z);

        assertTrue(standY > world.getSeaLevel(), "the platform must stand above the water line");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                assertFalse(Blocks.same(world.getBlockAt(OCEAN_X + dx, standY, OCEAN_Z + dz), Blocks.WATER),
                        "the body of the platform must be clear of water");
                assertFalse(Blocks.same(world.getBlockAt(OCEAN_X + dx, standY + 1, OCEAN_Z + dz), Blocks.WATER),
                        "head height must be clear of water");
                assertFalse(Blocks.same(world.getBlockAt(OCEAN_X + dx, standY - 1, OCEAN_Z + dz), Blocks.AIR),
                        "the platform must be solid underfoot");
                assertFalse(Blocks.same(world.getBlockAt(OCEAN_X + dx, standY - 1, OCEAN_Z + dz), Blocks.WATER),
                        "the platform must not be floating on water");
            }
        }
    }
}
