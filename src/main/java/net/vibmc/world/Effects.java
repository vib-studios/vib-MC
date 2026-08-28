package net.vibmc.world;

import com.github.retrooper.packetevents.protocol.sound.Sound;
import com.github.retrooper.packetevents.protocol.sound.SoundCategory;
import com.github.retrooper.packetevents.protocol.sound.Sounds;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import net.vibmc.entity.ServerPlayer;
import net.vibmc.server.VibMC;

/**
 * Sounds, particles, and animations. None of this changes authoritative state - it is what
 * makes the state changes legible to a player, and the server sent none of it before.
 */
public final class Effects {
    /** Vanilla level-event id for "block broken": plays the break sound and particles. */
    private static final int EFFECT_BLOCK_BREAK = 2001;
    /** Vanilla entity-status ids. */
    public static final int STATUS_HURT = 2;
    public static final int STATUS_DEATH = 3;

    private Effects() {}

    /** Break sound plus the block's own particle burst, for everyone who can see it. */
    public static void blockBreak(World world, int x, int y, int z, WrappedBlockState broken) {
        VibMC server = VibMC.getInstance();
        if (server == null || broken == null || Blocks.same(broken, Blocks.AIR)) return;
        Vector3i position = new Vector3i(x, y, z);
        server.getPlayerManager().broadcastNear(world, x + 0.5, y + 0.5, z + 0.5, 48.0, player ->
                new WrapperPlayServerEffect(EFFECT_BLOCK_BREAK, position,
                        net.vibmc.network.packetevents.PacketEventsStateMappings.id(
                                broken, player.getUser().getClientVersion()), false));
    }

    /** The muted "block placed" sound, taken from the placed block's material. */
    public static void blockPlace(World world, int x, int y, int z, WrappedBlockState placed) {
        sound(world, x + 0.5, y + 0.5, z + 0.5, breakSound(placed), SoundCategory.BLOCK, 1.0f, 0.8f);
    }

    public static void sound(World world, double x, double y, double z, Sound sound,
                             SoundCategory category, float volume, float pitch) {
        VibMC server = VibMC.getInstance();
        if (server == null || sound == null) return;
        Vector3d position = new Vector3d(x, y, z);
        server.getPlayerManager().broadcastNear(world, x, y, z, 32.0, player ->
                new WrapperPlayServerSoundEffect(sound, category, position, volume, pitch));
    }

    /** A sound only the given player hears, positioned on them. */
    public static void soundTo(ServerPlayer player, Sound sound, SoundCategory category,
                               float volume, float pitch) {
        if (player == null || player.getUser() == null || sound == null) return;
        player.getUser().sendPacket(new WrapperPlayServerSoundEffect(sound, category,
                new Vector3d(player.getX(), player.getY(), player.getZ()), volume, pitch));
    }

    /** Arm swing, hurt flash, and the rest of the entity animations, shown to other players. */
    public static void animation(ServerPlayer subject,
                                 WrapperPlayServerEntityAnimation.EntityAnimationType type) {
        VibMC server = VibMC.getInstance();
        if (server == null || subject == null) return;
        server.getPlayerManager().broadcastNear(subject.getWorld(), subject.getX(), subject.getY(),
                subject.getZ(), 48.0, viewer -> viewer == subject ? null
                        : new WrapperPlayServerEntityAnimation(subject.getEntityId(), type));
    }

    /**
     * Entity status, which is how the client is told to play its hurt flash or death fall.
     * Sent to the subject as well: their own client plays the effect for them.
     */
    public static void status(ServerPlayer subject, int status) {
        VibMC server = VibMC.getInstance();
        if (server == null || subject == null) return;
        server.getPlayerManager().broadcastNear(subject.getWorld(), subject.getX(), subject.getY(),
                subject.getZ(), 48.0, viewer -> new WrapperPlayServerEntityStatus(subject.getEntityId(), status));
    }

    /** The material sound family a block breaks with. */
    public static Sound breakSound(WrappedBlockState block) {
        if (block == null) return Sounds.BLOCK_STONE_BREAK;
        StateType type = block.getType();
        if (type == StateTypes.GRASS_BLOCK || type == StateTypes.DIRT || type == StateTypes.OAK_LEAVES
                || type == StateTypes.CACTUS || type == StateTypes.DEAD_BUSH) return Sounds.BLOCK_GRASS_BREAK;
        if (type == StateTypes.SAND) return Sounds.BLOCK_SAND_BREAK;
        if (type == StateTypes.GRAVEL || type == StateTypes.SOUL_SAND) return Sounds.BLOCK_GRAVEL_BREAK;
        if (type == StateTypes.GLASS || type == StateTypes.GLOWSTONE) return Sounds.BLOCK_GLASS_BREAK;
        if (type == StateTypes.OAK_LOG || type == StateTypes.OAK_PLANKS || type == StateTypes.CHEST
                || type == StateTypes.CRAFTING_TABLE || type == StateTypes.OAK_DOOR
                || type == StateTypes.OAK_TRAPDOOR) return Sounds.BLOCK_WOOD_BREAK;
        return Sounds.BLOCK_STONE_BREAK;
    }
}
