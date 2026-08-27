package net.vibmc.world.gen;

public class TerrainGenerator {
    private static final long DEFAULT_SEED = 0x1B2C3D4E5F6A7B8CL;

    private final long seed;

    public TerrainGenerator(long seed) {
        this.seed = seed != 0 ? seed : DEFAULT_SEED;
    }

    public int getHeight(int x, int z) {
        double continents = fbm(x * 0.004, z * 0.004, 4);
        double hills = fbm(x * 0.02, z * 0.02, 3);
        double height = 62.0 + continents * 18.0 + hills * 5.0;
        return Math.max(8, Math.min(240, (int) height));
    }

    public int hash(int x, int z) {
        long h = seed;
        h = h * 0x9E3779B97F4A7C15L + x;
        h = h * 0x9E3779B97F4A7C15L + z;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);
        return (int) (h & 0x7FFFFFFFL);
    }

    public double noise(double x, double z) {
        int ix = floor(x);
        int iz = floor(z);
        double fx = x - ix;
        double fz = z - iz;
        double sx = smoothstep(fx);
        double sz = smoothstep(fz);

        double a = valueAt(ix, iz);
        double b = valueAt(ix + 1, iz);
        double c = valueAt(ix, iz + 1);
        double d = valueAt(ix + 1, iz + 1);

        double top = lerp(a, b, sx);
        double bottom = lerp(c, d, sx);
        return lerp(top, bottom, sz);
    }

    public double fbm(double x, double z, int octaves) {
        double total = 0;
        double amplitude = 1;
        double frequency = 1;
        double max = 0;
        for (int i = 0; i < octaves; i++) {
            total += noise(x * frequency, z * frequency) * amplitude;
            max += amplitude;
            amplitude *= 0.5;
            frequency *= 2;
        }
        return total / max;
    }

    private double valueAt(int x, int z) {
        return (hash(x, z) / (double) Integer.MAX_VALUE) * 2.0 - 1.0;
    }

    private static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    private static double smoothstep(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
