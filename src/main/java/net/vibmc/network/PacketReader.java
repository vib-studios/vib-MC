package net.vibmc.network;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Reads a decoded inbound packet payload (the packet id has already been consumed). */
public final class PacketReader {
    private final ByteBuf buf;

    public PacketReader(ByteBuf payload) {
        this.buf = payload;
    }

    public int readVarInt() {
        int value = 0;
        for (int i = 0; i < 5; i++) {
            byte currentByte = buf.readByte();
            value |= (currentByte & 0x7F) << (i * 7);
            if ((currentByte & 0x80) != 128) break;
        }
        return value;
    }

    public String readString() {
        int length = readVarInt();
        if (length < 0 || length > Protocol.MAX_STRING_LENGTH) {
            throw new IllegalStateException("Invalid string length " + length);
        }
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public int readUnsignedByte() {
        return buf.readUnsignedByte();
    }

    public int readByte() {
        return buf.readByte();
    }

    public int readUnsignedShort() {
        return buf.readUnsignedShort();
    }

    public int readShort() {
        return buf.readShort();
    }

    public int readInt() {
        return buf.readInt();
    }

    public long readLong() {
        return buf.readLong();
    }

    public float readFloat() {
        return buf.readFloat();
    }

    public double readDouble() {
        return buf.readDouble();
    }

    public boolean readBoolean() {
        return buf.readBoolean();
    }

    public byte[] readByteArray() {
        int length = readVarInt();
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return bytes;
    }

    public int[] readVarIntArray() {
        int length = readVarInt();
        int[] values = new int[length];
        for (int i = 0; i < length; i++) values[i] = readVarInt();
        return values;
    }

    /** Reads a 64-bit encoded 1.12 position. */
    public long readPosition() {
        return buf.readLong();
    }

    public static int posX(long value) {
        return (int) (value << 38 >> 38);
    }

    public static int posY(long value) {
        return (int) (value << 52 >> 52);
    }

    public static int posZ(long value) {
        return (int) (value << 26 >> 26);
    }

    /** 1.12 login packets carry the UUID as a string. */
    public UUID readUUIDString() {
        return UUID.fromString(readString());
    }

    public UUID readUUIDLongs() {
        return new UUID(buf.readLong(), buf.readLong());
    }

    public boolean isReadable() {
        return buf.isReadable();
    }

    public int readableBytes() {
        return buf.readableBytes();
    }

    public ByteBuf buffer() {
        return buf;
    }
}