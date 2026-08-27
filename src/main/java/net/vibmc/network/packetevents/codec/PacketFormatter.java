package net.vibmc.network.packetevents.codec;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;import io.netty.buffer.ByteBuf;import io.netty.channel.ChannelHandlerContext;import io.netty.handler.codec.MessageToByteEncoder;
public final class PacketFormatter extends MessageToByteEncoder<ByteBuf>{protected void encode(ChannelHandlerContext c,ByteBuf msg,ByteBuf out){int length=msg.readableBytes();ByteBufHelper.writeVarInt(out,length);out.writeBytes(msg,msg.readerIndex(),length);}}
