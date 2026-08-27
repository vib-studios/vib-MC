package net.vibmc.network.packetevents.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import javax.crypto.Cipher;
import java.util.List;

public final class MinecraftCipherEncoder extends MessageToMessageEncoder<ByteBuf> {
    private final Cipher cipher;
    public MinecraftCipherEncoder(Cipher cipher) { this.cipher = cipher; }
    @Override protected void encode(ChannelHandlerContext context, ByteBuf input, List<Object> output) {
        byte[] plain=new byte[input.readableBytes()];input.getBytes(input.readerIndex(),plain);byte[] encrypted=cipher.update(plain);
        output.add(context.alloc().buffer(encrypted.length).writeBytes(encrypted));
    }
}
