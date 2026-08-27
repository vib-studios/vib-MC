package net.vibmc.world;

import net.vibmc.world.gen.TerrainGenerator;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import java.util.Arrays;
import net.vibmc.world.structure.StructureRegistry;

public class WorldChunk {
    private static final int WORLD_HEIGHT = 256;
    private static final int SEA_LEVEL = 63;

    private final World world;
    private final int chunkX;
    private final int chunkZ;
    private final WrappedBlockState[] blocks = new WrappedBlockState[16 * 16 * WORLD_HEIGHT];

    /** Set whenever blocks change, cleared once the chunk has been written to disk. */
    private volatile boolean dirty;

    private WorldChunk(World world, int chunkX, int chunkZ) {
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        Arrays.fill(blocks, Blocks.AIR);
    }

    /** Rebuilds a chunk from previously saved block data instead of regenerating it. */
    public static WorldChunk fromStored(World world, int chunkX, int chunkZ, WrappedBlockState[] stored) {
        WorldChunk chunk = new WorldChunk(world, chunkX, chunkZ);
        System.arraycopy(stored,0,chunk.blocks,0,chunk.blocks.length);
        chunk.dirty = false;
        return chunk;
    }

    public static WorldChunk generate(World world, int chunkX, int chunkZ) {
        WorldChunk chunk = new WorldChunk(world, chunkX, chunkZ);
        TerrainGenerator terrain = new TerrainGenerator(world.seed());
        switch (world.environment()) {
            case NETHER:
                generateNether(chunk, terrain);
                break;
            case END:
                generateEnd(chunk, terrain);
                break;
            default:
                generateOverworld(chunk, terrain);
        }
        StructureRegistry.decorate(chunk, world);
        return chunk;
    }

    private static void generateOverworld(WorldChunk chunk, TerrainGenerator terrain) {
        final int seaLevel = 63;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunk.chunkX * 16 + x;
                int worldZ = chunk.chunkZ * 16 + z;
                int surface = terrain.getHeight(worldX, worldZ);
                chunk.setBlock(x, 0, z, Blocks.BEDROCK);
                for (int y = 1; y < surface - 3; y++) {
                    if (y > 8 && y < surface - 5 && isCave(terrain, worldX, y, worldZ)) {
                        continue;
                    }
                    chunk.setBlock(x, y, z, oreOrStone(terrain, worldX, y, worldZ));
                }
                boolean beach = surface <= seaLevel + 1
                        || "minecraft:desert".equals(chunk.world.biomeAt(worldX, worldZ));
                for (int y = Math.max(1, surface - 3); y < surface; y++) {
                    chunk.setBlock(x, y, z, beach ? Blocks.SAND : Blocks.DIRT);
                }
                chunk.setBlock(x, surface, z, beach ? Blocks.SAND : Blocks.GRASS);
                for (int y = surface + 1; y <= seaLevel; y++) {
                    chunk.setBlock(x, y, z, Blocks.WATER);
                }
            }
        }
    }

    private static void generateNether(WorldChunk chunk, TerrainGenerator terrain) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunk.chunkX * 16 + x;
                int worldZ = chunk.chunkZ * 16 + z;
                for (int y = 0; y <= 127; y++) {
                    if (y == 0 || y == 127) {
                        chunk.setBlock(x, y, z, Blocks.BEDROCK);
                        continue;
                    }
                    double cavern = terrain.fbm(worldX * 0.035 + y * 0.013,
                            worldZ * 0.035 - y * 0.017, 3);
                    boolean solid = y < 32 || y > 105 || cavern > -0.12;
                    if (solid) {
                        WrappedBlockState block = y < 35 && terrain.hash(worldX + y, worldZ) % 18 == 0
                                ? Blocks.SOUL_SAND : Blocks.NETHERRACK;
                        chunk.setBlock(x, y, z, block);
                    } else if (y < 31) {
                        chunk.setBlock(x, y, z, Blocks.LAVA);
                    }
                }
            }
        }
    }

    private static void generateEnd(WorldChunk chunk, TerrainGenerator terrain) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunk.chunkX * 16 + x;
                int worldZ = chunk.chunkZ * 16 + z;
                double distance = Math.sqrt((double) worldX * worldX + (double) worldZ * worldZ);
                double island = 68.0 - distance * 0.16 + terrain.fbm(worldX * 0.025, worldZ * 0.025, 3) * 8.0;
                int surface = clamp((int) island, 0, 78);
                if (surface < 45) continue;
                for (int y = Math.max(1, surface - 18); y <= surface; y++) {
                    chunk.setBlock(x, y, z, Blocks.END_STONE);
                }
            }
        }
    }

    private static boolean isCave(TerrainGenerator terrain, int x, int y, int z) {
        return caveNoise(terrain, x * 0.075, y * 0.09, z * 0.075) > 0.52;
    }

    private static double caveNoise(TerrainGenerator terrain, double x, double y, double z) {
        int ix=(int)Math.floor(x), iy=(int)Math.floor(y), iz=(int)Math.floor(z);
        double fx=smooth(x-ix), fy=smooth(y-iy), fz=smooth(z-iz);
        double[] values=new double[8]; int n=0;
        for(int dy=0;dy<=1;dy++)for(int dz=0;dz<=1;dz++)for(int dx=0;dx<=1;dx++){
            int hash=terrain.hash(ix+dx+(iy+dy)*19349663,iz+dz+(iy+dy)*83492791);
            values[n++]=(hash/(double)Integer.MAX_VALUE)*2.0-1.0;
        }
        double x00=lerp(values[0],values[1],fx),x01=lerp(values[2],values[3],fx);
        double x10=lerp(values[4],values[5],fx),x11=lerp(values[6],values[7],fx);
        return lerp(lerp(x00,x01,fz),lerp(x10,x11,fz),fy);
    }

    private static double smooth(double value){return value*value*(3.0-2.0*value);}
    private static double lerp(double a,double b,double amount){return a+(b-a)*amount;}

    private static WrappedBlockState oreOrStone(TerrainGenerator terrain, int x, int y, int z) {
        int hash = terrain.hash(x ^ (y * 7349), z + y * 31);
        // Sparse vanilla-scale averages; future vein carvers can cluster these deposits.
        if (y < 128 && hash % 997 < 5) return Blocks.COAL_ORE;
        if (y < 64 && hash % 1301 < 4) return Blocks.IRON_ORE;
        return stoneMix(terrain, x, y, z);
    }

    private static WrappedBlockState stoneMix(TerrainGenerator terrain, int x, int y, int z) {
        int h = terrain.hash(x, z ^ (y * 7919));
        switch (h % 16) {
            case 0:
                return Blocks.ANDESITE;
            case 1:
                return Blocks.DIORITE;
            default:
                return Blocks.STONE;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public void setBlock(int x,int y,int z,WrappedBlockState state){if(inBounds(x,y,z)){int index=index(x,y,z);if(!Blocks.same(blocks[index],state)){blocks[index]=state;dirty=true;}}}

    /** True when this chunk holds changes that are not on disk yet. */
    public boolean isDirty() {
        return dirty;
    }

    /** Marks the chunk as needing a write on the next save. */
    public void markDirty() {
        dirty = true;
    }

    /** Called by the chunk manager once the chunk has been written out. */
    public void markSaved() {
        dirty = false;
    }

    public WrappedBlockState getBlock(int x,int y,int z){if(!inBounds(x,y,z))return Blocks.AIR;return blocks[index(x,y,z)];}
    public WrappedBlockState[] blocks(){return blocks.clone();}

    public World world() { return world; }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    private boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < 16 && y >= 0 && y < WORLD_HEIGHT && z >= 0 && z < 16;
    }

    private int index(int x, int y, int z) {
        return (y * 16 + z) * 16 + x;
    }
}
