package net.vibmc.world;

import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;
import net.vibmc.entity.ServerPlayer;
import net.vibmc.mappings.Mappings;
import net.vibmc.network.PacketSender;
import net.vibmc.network.PacketWriter;
import net.vibmc.server.VibMC;
import net.vibmc.world.block.BlockState;
import net.vibmc.world.block.BlockType;

/**
 * Sounds, particles, and animations. None of this changes authoritative state - it is what
 * makes the state changes legible to a player, and the server sent none of it before.
 *
 * <p>Sound names resolve through the vendored 1.12 mapping to the numeric sound id the
 * protocol requires, and the packet is written by hand because PacketEvents' sound wrapper
 * cannot encode it for 1.12.
 */
public final class Effects {
    /** Vanilla level-event id for "block broken": plays the break sound and particles. */
    private static final int EFFECT_BLOCK_BREAK = 2001;
    /** Vanilla entity-status ids. */
    public static final int STATUS_HURT = 2;
    public static final int STATUS_DEATH = 3;

    /** The 1.12 sound categories, in wire order. */
    public enum Category {
        MASTER(0), MUSIC(1), RECORDS(2), WEATHER(3), BLOCKS(4), HOSTILE(5), NEUTRAL(6),
        PLAYERS(7), AMBIENT(8), VOICE(9);

        private final int id;

        Category(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }
    }

    private Effects() {}

    /** Break sound plus the block's own particle burst, for everyone who can see it. */
    public static void blockBreak(World world, int x, int y, int z, BlockState broken) {
        VibMC server = VibMC.getInstance();
        if (server == null || broken == null || Blocks.same(broken, Blocks.AIR)) return;
        Vector3i position = new Vector3i(x, y, z);
        server.getPlayerManager().broadcastNear(world, x + 0.5, y + 0.5, z + 0.5, 48.0, player ->
                new WrapperPlayServerEffect(EFFECT_BLOCK_BREAK, position, broken.getGlobalId(), false));
    }

    /** The muted "block placed" sound, taken from the placed block's material. */
    public static void blockPlace(World world, int x, int y, int z, BlockState placed) {
        sound(world, x + 0.5, y + 0.5, z + 0.5, breakSound(placed), Category.BLOCKS, 1.0f, 0.8f);
    }

    /** Entity status, which is how the client is told to play its hurt flash or death fall.
     * Sent to the subject as well: their own client plays the effect for them. */
    public static void status(ServerPlayer subject, int status) {
        VibMC server = VibMC.getInstance();
        if (server == null || subject == null) return;
        server.getPlayerManager().broadcastNear(subject.getWorld(), subject.getX(), subject.getY(),
                subject.getZ(), 48.0, viewer -> new WrapperPlayServerEntityStatus(subject.getEntityId(), status));
    }

    /** Arm swing, hurt flash, and the rest of the entity animations, shown to other players. */
    public static void animation(ServerPlayer subject, WrapperPlayServerEntityAnimation.EntityAnimationType type) {
        VibMC server = VibMC.getInstance();
        if (server == null || subject == null) return;
        server.getPlayerManager().broadcastNear(subject.getWorld(), subject.getX(), subject.getY(),
                subject.getZ(), 48.0, viewer -> viewer == subject ? null
                        : new WrapperPlayServerEntityAnimation(subject.getEntityId(), type));
    }

    /** A sound played in the world for everyone close enough to hear it. */
    public static void sound(World world, double x, double y, double z, String soundName,
                             Category category, float volume, float pitch) {
        VibMC server = VibMC.getInstance();
        if (server == null || category == null) return;
        int soundId = soundId(soundName);
        if (soundId < 0) return;
        final int xi = (int) (x * 8.0), yi = (int) (y * 8.0), zi = (int) (z * 8.0);
        server.getPlayerManager().broadcastNear(world, x, y, z, 32.0, player -> {
            sendSound(player, soundId, category, xi, yi, zi, volume, pitch);
            return null;
        });
    }

    /** A sound only the given player hears, positioned on them. */
    public static void soundTo(ServerPlayer player, String soundName, Category category,
                               float volume, float pitch) {
        if (player == null || player.getUser() == null || category == null) return;
        int soundId = soundId(soundName);
        if (soundId < 0) return;
        sendSound(player, soundId, category, (int) (player.getX() * 8.0), (int) (player.getY() * 8.0),
                (int) (player.getZ() * 8.0), volume, pitch);
    }

    private static void sendSound(ServerPlayer player, int soundId, Category category,
                                  int x, int y, int z, float volume, float pitch) {
        if (player.getUser() == null) return;
        PacketWriter writer = PacketWriter.out(0x49);
        writer.writeVarInt(soundId);
        writer.writeVarInt(category.id());
        writer.writeInt(x);
        writer.writeInt(y);
        writer.writeInt(z);
        writer.writeFloat(volume);
        writer.writeFloat(pitch);
        PacketSender.send(player.getUser(), writer);
    }

    /** The material sound family a block breaks with. */
    public static String breakSound(BlockState block) {
        if (block == null) return "block.stone.break";
        BlockType type = block.getType();
        String name = type.name();
        if (name.equals("grass_block") || name.equals("dirt") || name.equals("oak_leaves")
                || name.equals("cactus") || name.equals("dead_bush")) return "block.grass.break";
        if (name.equals("sand")) return "block.sand.break";
        if (name.equals("gravel") || name.equals("soul_sand")) return "block.gravel.break";
        if (name.equals("glass") || name.equals("glowstone")) return "block.glass.break";
        if (name.equals("oak_log") || name.equals("oak_planks") || name.equals("chest")
                || name.equals("crafting_table") || name.equals("oak_door")
                || name.equals("oak_trapdoor")) return "block.wood.break";
        return "block.stone.break";
    }

    private static int soundId(String soundName) {
        return soundName == null ? -1 : Mappings.soundId(soundName);
    }
}