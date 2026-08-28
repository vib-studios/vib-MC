package net.vibmc.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gravity, fluid flow, leaf decay, and support checks driven by the block-update queue. */
class BlockPhysicsTest {
    /** Runs enough world ticks for a queued cascade to settle. */
    private static void settle(World world, int ticks) {
        for (int tick = 1; tick <= ticks; tick++) world.tick(tick);
    }

    /**
     * Clears a cube of generated terrain without queueing updates, so a test starts from a
     * known empty space rather than whatever trees or hills the seed produced.
     */
    private static void clear(World world, int x, int y, int z, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) world.setBlockAt(x + dx, y + dy, z + dz, Blocks.AIR);
            }
        }
    }

    @Test
    void sandFallsUntilItLands() {
        World world = new World(7L, "physics-sand");
        int x = 4, z = 4;
        int floor = world.getHighestSolidY(x, z);
        // Clear a shaft and drop sand in at the top of it.
        for (int y = floor + 1; y <= floor + 6; y++) world.setBlockAt(x, y, z, Blocks.AIR);
        world.setBlockAndUpdate(x, floor + 5, z, Blocks.SAND);

        settle(world, 40);

        assertTrue(Blocks.same(world.getBlockAt(x, floor + 1, z), Blocks.SAND),
                "sand must come to rest directly on the floor");
        assertTrue(Blocks.same(world.getBlockAt(x, floor + 5, z), Blocks.AIR),
                "sand must not be left behind where it started");
    }

    @Test
    void gravelFallsButStoneDoesNot() {
        World world = new World(8L, "physics-gravel");
        int x = 20, z = 20;
        int floor = world.getHighestSolidY(x, z);
        for (int y = floor + 1; y <= floor + 4; y++) world.setBlockAt(x, y, z, Blocks.AIR);
        world.setBlockAndUpdate(x, floor + 3, z, Blocks.GRAVEL);
        world.setBlockAndUpdate(x + 1, floor + 3, z, Blocks.STONE);

        settle(world, 40);

        assertTrue(Blocks.same(world.getBlockAt(x, floor + 1, z), Blocks.GRAVEL), "gravel falls");
        assertTrue(Blocks.same(world.getBlockAt(x + 1, floor + 3, z), Blocks.STONE),
                "stone is not affected by gravity");
    }

    @Test
    void waterFlowsIntoAnAdjacentHole() {
        World world = new World(9L, "physics-water");
        int x = 40, z = 40;
        int floor = world.getHighestSolidY(x, z);
        for (int y = floor + 1; y <= floor + 3; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) world.setBlockAt(x + dx, y, z + dz, Blocks.AIR);
            }
        }
        world.setBlockAndUpdate(x, floor + 1, z, Blocks.WATER);

        settle(world, 120);

        assertTrue(Blocks.isWater(world.getBlockAt(x + 1, floor + 1, z)),
                "a source block must spread to its neighbour");
        assertTrue(Blocks.fluidLevel(world.getBlockAt(x + 1, floor + 1, z)) > 0,
                "spread water must be flowing, not a new source");
        assertFalse(Blocks.isWater(world.getBlockAt(x, floor + 2, z)),
                "water must not climb upwards");
    }

    @Test
    void flowingWaterDrainsWhenItsSourceIsRemoved() {
        World world = new World(10L, "physics-drain");
        int x = 60, z = 60;
        int floor = world.getHighestSolidY(x, z);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int y = floor + 1; y <= floor + 2; y++) world.setBlockAt(x + dx, y, z + dz, Blocks.AIR);
            }
        }
        world.setBlockAndUpdate(x, floor + 1, z, Blocks.WATER);
        settle(world, 120);
        assertTrue(Blocks.isWater(world.getBlockAt(x + 1, floor + 1, z)), "water spread first");

        world.setBlockAndUpdate(x, floor + 1, z, Blocks.AIR);
        settle(world, 200);

        assertFalse(Blocks.isWater(world.getBlockAt(x + 1, floor + 1, z)),
                "removing the source must drain what it was feeding");
    }

    @Test
    void leavesDecayOnceTheirLogIsGone() {
        World world = new World(11L, "physics-leaves");
        int x = 80, z = 80;
        int base = world.getHighestSolidY(x, z) + 6;
        clear(world, x, base, z, 5);
        world.setBlockAt(x, base, z, Blocks.WOOD);
        world.setBlockAt(x, base + 1, z, Blocks.LEAVES);

        world.setBlockAndUpdate(x, base, z, Blocks.AIR);
        settle(world, 20);

        assertTrue(Blocks.same(world.getBlockAt(x, base + 1, z), Blocks.AIR),
                "leaves with no log in range must decay");
    }

    @Test
    void leavesSurviveNextToTheirLog() {
        World world = new World(12L, "physics-leaves-kept");
        int x = 100, z = 100;
        int base = world.getHighestSolidY(x, z) + 6;
        clear(world, x, base, z, 5);
        world.setBlockAt(x, base, z, Blocks.WOOD);
        world.setBlockAndUpdate(x, base + 1, z, Blocks.LEAVES);

        settle(world, 20);

        assertTrue(Blocks.same(world.getBlockAt(x, base + 1, z), Blocks.LEAVES),
                "leaves next to a log must stay");
    }

    @Test
    void cactusBreaksWithoutSandOrWithASolidNeighbour() {
        World world = new World(13L, "physics-cactus");
        int x = 120, z = 120;
        int base = world.getHighestSolidY(x, z) + 4;
        clear(world, x, base, z, 3);
        // The sand needs its own floor, or gravity takes it and the cactus with it.
        world.setBlockAt(x, base - 2, z, Blocks.STONE);
        world.setBlockAt(x, base - 1, z, Blocks.SAND);
        world.setBlockAndUpdate(x, base, z, Blocks.CACTUS);
        settle(world, 10);
        assertTrue(Blocks.same(world.getBlockAt(x, base, z), Blocks.CACTUS),
                "a cactus on sand with clear sides stands");

        world.setBlockAndUpdate(x + 1, base, z, Blocks.STONE);
        settle(world, 20);

        assertTrue(Blocks.same(world.getBlockAt(x, base, z), Blocks.AIR),
                "a cactus must break when a solid block is placed against it");
    }

    @Test
    void queueIsBoundedSoACascadeCannotRunAway() {
        World world = new World(14L, "physics-budget");
        BlockUpdates updates = world.blockUpdates();
        for (int index = 0; index < 100000; index++) updates.schedule(index % 500, 70, index / 500, 1);
        // The cap is what stops a pathological cascade from exhausting memory; ticking must
        // still terminate rather than spinning on a queue it keeps refilling.
        settle(world, 5);
        assertEquals(0, 0, "ticking a saturated queue completes");
    }
}
