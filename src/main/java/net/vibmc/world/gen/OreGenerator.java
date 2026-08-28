package net.vibmc.world.gen;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import net.vibmc.world.Blocks;

/**
 * Clustered ore placement.
 *
 * Ore used to be an independent hash roll per block, which scattered single blocks and never
 * produced a vein worth mining. Each ore now owns a cell grid; a cell either seeds one vein or
 * none, and the seed is kept a vein-radius away from its cell walls so a block only ever has
 * to test its own cell. That keeps generation a pure function of the seed and O(ore types)
 * per block, with no neighbour-chunk lookups.
 */
public final class OreGenerator {
    /** One ore's distribution: grid size, how often a cell seeds a vein, height band, size. */
    private static final class Vein {
        final WrappedBlockState block;
        final int cell, minY, maxY, salt;
        final double chance, radius;
        Vein(WrappedBlockState block, int cell, double chance, int minY, int maxY, double radius, int salt) {
            this.block = block; this.cell = cell; this.chance = chance;
            this.minY = minY; this.maxY = maxY; this.radius = radius; this.salt = salt;
        }
    }

    private static final Vein[] VEINS = {
            new Vein(Blocks.COAL_ORE, 12, 0.55, 5, 128, 2.1, 0x1F3A),
            new Vein(Blocks.IRON_ORE, 14, 0.45, 5, 64, 1.8, 0x2B71),
            new Vein(Blocks.GOLD_ORE, 20, 0.28, 5, 32, 1.6, 0x3C05),
            new Vein(Blocks.REDSTONE_ORE, 18, 0.32, 5, 16, 1.8, 0x4D92),
            new Vein(Blocks.DIAMOND_ORE, 24, 0.18, 5, 16, 1.5, 0x5EA7),
            new Vein(Blocks.LAPIS_ORE, 22, 0.22, 5, 32, 1.5, 0x6F13),
            new Vein(Blocks.EMERALD_ORE, 30, 0.06, 5, 32, 1.0, 0x70C8),
    };

    private OreGenerator() {}

    /** The ore belonging at this position, or null for ordinary stone. */
    public static WrappedBlockState oreAt(TerrainGenerator terrain, int x, int y, int z) {
        for (Vein vein : VEINS) {
            if (y < vein.minY || y > vein.maxY) continue;
            int cellX = Math.floorDiv(x, vein.cell);
            int cellY = Math.floorDiv(y, vein.cell);
            int cellZ = Math.floorDiv(z, vein.cell);
            int seed = terrain.hash(cellX * 31 + vein.salt, cellZ * 17 + cellY * 7919);
            if ((seed & 0xffff) / 65535.0 > vein.chance) continue;
            // Keep the seed a full radius inside the cell so the vein cannot cross a wall.
            int margin = (int) Math.ceil(vein.radius);
            int span = Math.max(1, vein.cell - margin * 2);
            double centerX = cellX * vein.cell + margin + Math.floorMod(seed >>> 4, span);
            double centerY = cellY * vein.cell + margin + Math.floorMod(seed >>> 11, span);
            double centerZ = cellZ * vein.cell + margin + Math.floorMod(seed >>> 19, span);
            double dx = x - centerX, dy = y - centerY, dz = z - centerZ;
            double distance = dx * dx + dy * dy + dz * dz;
            // A little per-block jitter on the edge so veins are lumpy rather than spherical.
            double jitter = (terrain.hash(x ^ (y * 31), z ^ (y * 17)) & 0xff) / 255.0 * 0.6;
            if (distance <= (vein.radius + jitter) * (vein.radius + jitter)) return vein.block;
        }
        return null;
    }
}
