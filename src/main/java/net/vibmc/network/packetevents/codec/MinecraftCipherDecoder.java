package net.vibmc.network.packetevents.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import javax.crypto.Cipher;
import java.util.List;

public final class MinecraftCipherDecoder extends MessageToMessageDecoder<ByteBuf> {
    private final Cipher cipher;
    public MinecraftCipherDecoder(Cipher cipher) { this.cipher = cipher; }
    @Override protected void decode(ChannelHandlerContext context, ByteBuf input, List<Object> output) {
        byte[] encrypted=new byte[input.readableBytes()];input.readBytes(encrypted);byte[] plain=cipher.update(encrypted);
        output.add(context.alloc().buffer(plain.length).writeBytes(plain));
    }
}
