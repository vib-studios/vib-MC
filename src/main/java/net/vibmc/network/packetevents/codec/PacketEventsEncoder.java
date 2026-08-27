package net.vibmc.network.packetevents.codec;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.PacketEventsImplHelper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

/** Runs PacketEvents clientbound listeners while preserving strict ByteBuf ownership. */
public final class PacketEventsEncoder extends MessageToMessageEncoder<ByteBuf> {
    private User user;
    private Object player;

    public PacketEventsEncoder(User user, Object player) {
        this.user = user;
        this.player = player;
    }

    public void setUser(User user) { this.user = user; }
    public void setPlayer(Object player) { this.player = player; }

    @Override
    protected void encode(ChannelHandlerContext context, ByteBuf input, List<Object> output)
            throws Exception {
        ByteBuf copy = context.alloc().buffer(input.readableBytes());
        boolean transferred = false;
        try {
            copy.writeBytes(input, input.readerIndex(), input.readableBytes());
            PacketSendEvent event = PacketEventsImplHelper.handleClientBoundPacket(
                    context.channel(), user, player, copy, false);
            if (event == null || event.isCancelled()) {
                return;
            }

            if (event.hasPostTasks()) {
                for (Runnable task : event.getPostTasks()) task.run();
            }

            output.add(copy);
            transferred = true; // MessageToMessageEncoder now owns/releases the output.
        } finally {
            // The old encoder leaked this allocation whenever PacketEvents or a listener threw
            // before copy was added to output (including malformed/unsupported packets).
            if (!transferred && copy.refCnt() > 0) copy.release();
        }
    }
}
