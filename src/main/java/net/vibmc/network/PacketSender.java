package net.vibmc.network;

import com.github.retrooper.packetevents.protocol.player.User;
import io.netty.channel.Channel;

/**
 * Writes an already-encoded packet straight onto a connection's pipeline. PacketEvents
 * encodes netty {@link io.netty.buffer.ByteBuf} writes through its own frame encoder, so a
 * {@link PacketWriter} payload (packet id followed by its data, no length prefix) reaches
 * the client exactly as intended. This is how protocol-340 sends data PacketEvents' wrapper
 * layer cannot encode for 1.12 (window items, set slot, sounds, entity equipment).
 */
public final class PacketSender {
    private PacketSender() {}

    public static void send(User user, PacketWriter writer) {
        if (user == null) return;
        ((Channel) user.getChannel()).writeAndFlush(writer.payload());
    }
}