package net.vibmc.world;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Deferred neighbour updates: falling sand, flowing fluids, decaying leaves, and blocks that
 * need something underneath them.
 *
 * Nothing used to react to a block change at all - a wall could be pulled out of a lake and
 * the water would sit there in mid-air. Updates are queued rather than applied inline so a
 * single break cannot recurse through a whole ocean, and each world tick spends a bounded
 * budget on them. World generation writes through {@link WorldChunk} directly and so never
 * enqueues anything, which is what stops a freshly generated ocean from flowing on load.
 */
public final class BlockUpdates {
    /** Updates applied per world tick. Anything past this waits for the next tick. */
    private static final int BUDGET_PER_TICK = 2048;
    /** Hard cap on pending updates, so a pathological cascade cannot exhaust memory. */
    private static final int MAX_PENDING = 32768;
    private static final int WATER_MAX_SPREAD = 7;
    private static final int LAVA_MAX_SPREAD = 3;
    /** 1.12.2 encodes a falling fluid as level 8 and above. */
    private static final int FALLING = 8;
    private static final int LEAF_SUPPORT_RADIUS = 4;

    private static final int[][] NEIGHBOURS = {
            {0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}
    };
    private static final int[][] HORIZONTAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final World world;
    private final Queue<long[]> pending = new ConcurrentLinkedQueue<>();
    private final Set<Long> queued = ConcurrentHashMap.newKeySet();
    private volatile long tick;

    BlockUpdates(World world) {
        this.world = world;
    }

    /** Queues a position for re-evaluation in {@code delay} ticks. */
    public void schedule(int x, int y, int z, int delay) {
        if (y < 0 || y >= 256) return;
        long packed = pack(x, y, z);
        if (queued.size() >= MAX_PENDING || !queued.add(packed)) return;
        pending.add(new long[]{packed, tick + Math.max(1, delay)});
    }

    /** Queues the changed block and everything touching it. */
    public void scheduleNeighbours(int x, int y, int z) {
        schedule(x, y, z, 1);
        for (int[] offset : NEIGHBOURS) schedule(x + offset[0], y + offset[1], z + offset[2], 1);
    }

    /** Applies up to a tick's worth of queued updates. */
    void tick(long currentTick) {
        tick = currentTick;
        int budget = BUDGET_PER_TICK;
        int deferred = 0;
        int size = pending.size();
        while (budget > 0 && deferred < size) {
            long[] entry = pending.poll();
            if (entry == null) break;
            if (entry[1] > currentTick) {
                pending.add(entry);
                deferred++;
                continue;
            }
            queued.remove(entry[0]);
            budget--;
            try {
                apply(unpackX(entry[0]), unpackY(entry[0]), unpackZ(entry[0]));
            } catch (RuntimeException error) {
                net.vibmc.server.VibMC server = net.vibmc.server.VibMC.getInstance();
                if (server != null) server.getLogger().warn("Block update failed: %s", error);
            }
        }
    }

    private void apply(int x, int y, int z) {
        WrappedBlockState state = world.getBlockAt(x, y, z);
        if (Blocks.same(state, Blocks.AIR)) return;
        if (Blocks.isGravityAffected(state)) { applyGravity(state, x, y, z); return; }
        if (Blocks.isFluid(state)) { applyFluid(state, x, y, z); return; }
        if (state.getType() == StateTypes.OAK_LEAVES) { applyLeafDecay(x, y, z); return; }
        if (needsSupport(state) && !isSupported(state, x, y, z)) breakNaturally(state, x, y, z);
    }

    /** Sand and gravel drop until they land on something. */
    private void applyGravity(WrappedBlockState state, int x, int y, int z) {
        if (y <= 0) return;
        WrappedBlockState below = world.getBlockAt(x, y - 1, z);
        if (!Blocks.isReplaceable(below)) return;
        world.setBlockAndUpdate(x, y, z, Blocks.AIR);
        world.setBlockAndUpdate(x, y - 1, z, state);
    }

    /**
     * Fluid spread. A block flows down when it can, sideways when it cannot, and drains when
     * nothing upstream is feeding it. Source blocks (level 0) never drain.
     */
    private void applyFluid(WrappedBlockState fluid, int x, int y, int z) {
        boolean water = Blocks.isWater(fluid);
        int maxSpread = water ? WATER_MAX_SPREAD : LAVA_MAX_SPREAD;
        int delay = water ? 5 : 15;
        int level = Blocks.fluidLevel(fluid);
        boolean source = level == 0;
        int effective = level >= FALLING ? 0 : level;

        if (!source && !isFed(fluid, level, x, y, z)) {
            world.setBlockAndUpdate(x, y, z, Blocks.AIR);
            return;
        }
        WrappedBlockState below = y > 0 ? world.getBlockAt(x, y - 1, z) : Blocks.AIR;
        if (y > 0 && Blocks.isReplaceable(below) && !sameFluid(fluid, below)) {
            world.setBlockAndUpdate(x, y - 1, z, Blocks.fluidAt(fluid, FALLING));
            schedule(x, y - 1, z, delay);
            return;
        }
        // Water pouring onto lava, or lava met by water, produces stone as in vanilla.
        if (y > 0 && Blocks.isFluid(below) && !sameFluid(fluid, below)) {
            world.setBlockAndUpdate(x, y - 1, z, Blocks.COBBLESTONE);
            return;
        }
        if (effective >= maxSpread) return;
        for (int[] offset : HORIZONTAL) {
            int nx = x + offset[0], nz = z + offset[1];
            WrappedBlockState neighbour = world.getBlockAt(nx, y, nz);
            if (sameFluid(fluid, neighbour)) {
                if (Blocks.fluidLevel(neighbour) > effective + 1) {
                    world.setBlockAndUpdate(nx, y, nz, Blocks.fluidAt(fluid, effective + 1));
                    schedule(nx, y, nz, delay);
                }
                continue;
            }
            if (Blocks.isFluid(neighbour)) {
                world.setBlockAndUpdate(nx, y, nz, water ? Blocks.COBBLESTONE : Blocks.STONE);
                continue;
            }
            if (!Blocks.isReplaceable(neighbour)) continue;
            world.setBlockAndUpdate(nx, y, nz, Blocks.fluidAt(fluid, effective + 1));
            schedule(nx, y, nz, delay);
        }
    }

    /** A flowing block survives while fed from above or by a shallower neighbour. */
    private boolean isFed(WrappedBlockState fluid, int level, int x, int y, int z) {
        if (y < 255 && sameFluid(fluid, world.getBlockAt(x, y + 1, z))) return true;
        int effective = level >= FALLING ? 0 : level;
        for (int[] offset : HORIZONTAL) {
            WrappedBlockState neighbour = world.getBlockAt(x + offset[0], y, z + offset[1]);
            if (!sameFluid(fluid, neighbour)) continue;
            int neighbourLevel = Blocks.fluidLevel(neighbour);
            if (neighbourLevel < FALLING && neighbourLevel < effective) return true;
        }
        return false;
    }

    private static boolean sameFluid(WrappedBlockState fluid, WrappedBlockState other) {
        return other != null && other.getType() == fluid.getType();
    }

    /** Leaves cut off from a log decay away. */
    private void applyLeafDecay(int x, int y, int z) {
        for (int dx = -LEAF_SUPPORT_RADIUS; dx <= LEAF_SUPPORT_RADIUS; dx++) {
            for (int dy = -LEAF_SUPPORT_RADIUS; dy <= LEAF_SUPPORT_RADIUS; dy++) {
                for (int dz = -LEAF_SUPPORT_RADIUS; dz <= LEAF_SUPPORT_RADIUS; dz++) {
                    if (Blocks.same(world.getBlockAt(x + dx, y + dy, z + dz), Blocks.WOOD)) return;
                }
            }
        }
        breakNaturally(Blocks.LEAVES, x, y, z);
    }

    private static boolean needsSupport(WrappedBlockState state) {
        return Blocks.same(state, Blocks.CACTUS) || Blocks.same(state, Blocks.DEAD_BUSH)
                || state.getType() == StateTypes.OAK_SAPLING;
    }

    /** Cacti additionally refuse to stand against a solid neighbour, as in vanilla. */
    private boolean isSupported(WrappedBlockState state, int x, int y, int z) {
        if (y <= 0) return false;
        WrappedBlockState below = world.getBlockAt(x, y - 1, z);
        if (Blocks.same(state, Blocks.CACTUS)) {
            if (!Blocks.same(below, Blocks.SAND) && !Blocks.same(below, Blocks.CACTUS)) return false;
            for (int[] offset : HORIZONTAL) {
                if (Blocks.isSolid(world.getBlockAt(x + offset[0], y, z + offset[1]))) return false;
            }
            return true;
        }
        return Blocks.isSolid(below);
    }

    private void breakNaturally(WrappedBlockState state, int x, int y, int z) {
        world.setBlockAndUpdate(x, y, z, Blocks.AIR);
        Effects.blockBreak(world, x, y, z, state);
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3ffffff) << 38) | ((long) (y & 0xfff) << 26) | (z & 0x3ffffff);
    }

    private static int unpackX(long packed) { return signed((int) (packed >> 38), 26); }
    private static int unpackY(long packed) { return (int) ((packed >> 26) & 0xfff); }
    private static int unpackZ(long packed) { return signed((int) (packed & 0x3ffffff), 26); }

    private static int signed(int value, int bits) {
        int mask = 1 << (bits - 1);
        return (value & (mask - 1)) - (value & mask);
    }
}
