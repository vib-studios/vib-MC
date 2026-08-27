package net.vibmc.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Detects and activates standard 4x5 obsidian Nether portal frames. */
public final class PortalDetector {
    private PortalDetector() {}

    public static List<int[]> activateNear(World world, int x, int y, int z) {
        for (int base = y - 4; base <= y; base++) {
            for (int start = x - 3; start <= x; start++) {
                List<int[]> result = activate(world, start, base, z, true);
                if (!result.isEmpty()) return result;
            }
            for (int start = z - 3; start <= z; start++) {
                List<int[]> result = activate(world, x, base, start, false);
                if (!result.isEmpty()) return result;
            }
        }
        return Collections.emptyList();
    }

    public static List<int[]> insertEyeAndActivate(World world, int x, int y, int z) {
        if (!Blocks.same(world.getBlockAt(x,y,z),Blocks.END_PORTAL_FRAME)) return Collections.emptyList();
        world.setBlockAt(x,y,z,Blocks.END_PORTAL_FRAME_FILLED);
        for(int minX=x-4;minX<=x;minX++) for(int minZ=z-4;minZ<=z;minZ++) {
            boolean valid=true;
            for(int i=1;i<=3;i++) valid &= filled(world,minX+i,y,minZ) && filled(world,minX+i,y,minZ+4)
                    && filled(world,minX,y,minZ+i) && filled(world,minX+4,y,minZ+i);
            if(!valid) continue;
            List<int[]> changed=new ArrayList<>();
            for(int ix=1;ix<=3;ix++)for(int iz=1;iz<=3;iz++)if(world.setBlockAt(minX+ix,y,minZ+iz,Blocks.END_PORTAL))changed.add(new int[]{minX+ix,y,minZ+iz});
            return changed;
        }
        return Collections.emptyList();
    }

    private static boolean filled(World world,int x,int y,int z){return Blocks.same(world.getBlockAt(x,y,z),Blocks.END_PORTAL_FRAME_FILLED);}

    private static List<int[]> activate(World world, int startA, int baseY, int fixed, boolean alongX) {
        for (int a = 0; a < 4; a++) {
            for (int dy = 0; dy < 5; dy++) {
                int x = alongX ? startA + a : fixed;
                int z = alongX ? fixed : startA + a;
                com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState block = world.getBlockAt(x, baseY + dy, z);
                boolean frame = a == 0 || a == 3 || dy == 0 || dy == 4;
                if (frame ? !Blocks.same(block,Blocks.OBSIDIAN)
                        : !Blocks.same(block,Blocks.AIR) && !Blocks.same(block,Blocks.NETHER_PORTAL)) {
                    return Collections.emptyList();
                }
            }
        }
        List<int[]> changed = new ArrayList<>();
        for (int a = 1; a <= 2; a++) {
            for (int dy = 1; dy <= 3; dy++) {
                int x = alongX ? startA + a : fixed;
                int z = alongX ? fixed : startA + a;
                if (world.setBlockAt(x, baseY + dy, z, Blocks.NETHER_PORTAL)) {
                    changed.add(new int[]{x, baseY + dy, z});
                }
            }
        }
        return changed;
    }
}
