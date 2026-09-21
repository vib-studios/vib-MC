package net.vibmc.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.vibmc.inventory.ItemStack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Builds a framed outbound packet. The packet id is written on construction; {@link #frame()}
 * returns a single {@link ByteBuf} prefixed by its varint length, ready for the channel.
 */
public final class PacketWriter {
    private final ByteBuf buf;

    private PacketWriter(int packetId) {
        this.buf = Unpooled.buffer();
        writeVarInt(buf, packetId);
    }

    public static PacketWriter out(int packetId) {
        return new PacketWriter(packetId);
    }

    public PacketWriter writeVarInt(int value) {
        writeVarInt(buf, value);
        return this;
    }

    public PacketWriter writeByte(int value) {
        buf.writeByte(value);
        return this;
    }

    public PacketWriter writeUnsignedByte(int value) {
        buf.writeByte(value & 0xFF);
        return this;
    }

    public PacketWriter writeShort(int value) {
        buf.writeShort(value);
        return this;
    }

    public PacketWriter writeUnsignedShort(int value) {
        buf.writeShort(value & 0xFFFF);
        return this;
    }

    public PacketWriter writeInt(int value) {
        buf.writeInt(value);
        return this;
    }

    public PacketWriter writeLong(long value) {
        buf.writeLong(value);
        return this;
    }

    public PacketWriter writeFloat(float value) {
        buf.writeFloat(value);
        return this;
    }

    public PacketWriter writeDouble(double value) {
        buf.writeDouble(value);
        return this;
    }

    public PacketWriter writeBoolean(boolean value) {
        buf.writeBoolean(value);
        return this;
    }

    public PacketWriter writeString(String value) {
        writeVarInt(buf, value.length());
        buf.writeCharSequence(value, StandardCharsets.UTF_8);
        return this;
    }

    public PacketWriter writeBytes(byte[] bytes) {
        buf.writeBytes(bytes);
        return this;
    }

    public PacketWriter writeByteArray(byte[] bytes) {
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
        return this;
    }

    public PacketWriter writeVarIntArray(int[] values) {
        writeVarInt(buf, values.length);
        for (int value : values) writeVarInt(buf, value);
        return this;
    }

    public PacketWriter writePosition(int x, int y, int z) {
        buf.writeLong(((long) (x & 0x3FFFFFF) << 38)
                | ((long) (y & 0xFFF) << 26)
                | (z & 0x3FFFFFFL));
        return this;
    }

    public PacketWriter writeUUIDLongs(UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
        return this;
    }

    /** 1.12 login packets carry the UUID as a string. */
    public PacketWriter writeUUIDString(UUID uuid) {
        return writeString(uuid.toString());
    }

    /** Slot / item: {@code short id, byte count, short damage, optional nbt}. */
    public PacketWriter writeSlot(ItemStack item) {
        if (item == null || item.isEmpty()) {
            buf.writeShort(-1);
            return this;
        }
        buf.writeShort(item.getId());
        buf.writeByte(item.getAmount());
        buf.writeShort(item.getDamageValue());
        if (item.getNBT() == null || item.getNBT().isEmpty()) {
            buf.writeByte(0);
        } else {
            writeCompound(item.getNBT());
        }
        return this;
    }

    public PacketWriter writeNbt(CompoundBinaryTag tag) {
        if (tag == null || tag.isEmpty()) {
            buf.writeByte(0);
            return this;
        }
        writeCompound(tag);
        return this;
    }

    private void writeCompound(CompoundBinaryTag tag) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutput output = new DataOutputStream(bytes);
        try {
            BinaryTagIO.writer().writeNameless(tag, output);
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        buf.writeBytes(bytes.toByteArray());
    }

    /** The id plus payload, wrapped in the varint length prefix for direct channel writes. */
    public ByteBuf frame() {
        ByteBuf frame = Unpooled.buffer(5 + buf.readableBytes());
        writeVarInt(frame, buf.readableBytes());
        frame.writeBytes(buf);
        buf.release();
        return frame;
    }

    /** The id plus payload (no length prefix), for use with a framing encoder. */
    public ByteBuf payload() {
        return buf;
    }

    public static int varIntSize(int value) {
        int size = 1;
        while ((value & 0xFFFFFF80) != 0) {
            value >>>= 7;
            size++;
        }
        return size;
    }

    public static void writeVarInt(ByteBuf buf, int value) {
        while ((value & 0xFFFFFF80) != 0) {
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }
}