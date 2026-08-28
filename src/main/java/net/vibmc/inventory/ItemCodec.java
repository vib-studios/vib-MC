package net.vibmc.inventory;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.nbt.NBT;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTLimiter;
import com.github.retrooper.packetevents.protocol.nbt.serializer.DefaultNBTSerializer;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The on-disk encoding for one item stack, shared by player data and world containers so the
 * two cannot drift apart. Item payloads use PacketEvents' complete NBT codec.
 */
public final class ItemCodec {
    private static final int MAX_ITEM_NBT_BYTES = 1 << 20;
    private static final int MAX_STRING_BYTES = 32767;

    private ItemCodec() {}

    public static void writeItem(DataOutput output, ItemStack item) throws IOException {
        if (item == null || item.isEmpty()) {
            output.writeBoolean(false);
            return;
        }
        output.writeBoolean(true);
        writeBoundedString(output, item.getType().getName().toString(), "item type");
        output.writeInt(item.getAmount());
        output.writeInt(item.getDamageValue());
        NBTCompound tag = item.getNBT();
        output.writeBoolean(tag != null);
        if (tag == null) return;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream nbtOutput = new DataOutputStream(bytes)) {
            DefaultNBTSerializer.INSTANCE.serializeTag(nbtOutput, tag, true);
        }
        byte[] payload = bytes.toByteArray();
        if (payload.length > MAX_ITEM_NBT_BYTES) throw new IOException("item NBT exceeds size limit");
        output.writeInt(payload.length);
        output.write(payload);
    }

    public static ItemStack readItem(DataInput input) throws IOException {
        if (!input.readBoolean()) return ItemStack.EMPTY;
        String typeName = readBoundedString(input, "item type");
        ItemType type = ItemTypes.getByName(typeName);
        if (type == null || type == ItemTypes.AIR) throw new IOException("unknown item type " + typeName);
        int amount = input.readInt();
        int damage = input.readInt();
        if (amount <= 0 || amount > 127) throw new IOException("invalid item amount " + amount);
        NBTCompound tag = null;
        if (input.readBoolean()) {
            int length = input.readInt();
            if (length < 0 || length > MAX_ITEM_NBT_BYTES) throw new IOException("invalid item NBT length " + length);
            byte[] payload = new byte[length];
            input.readFully(payload);
            try (DataInputStream nbtInput = new DataInputStream(new ByteArrayInputStream(payload))) {
                NBT encoded = DefaultNBTSerializer.INSTANCE.deserializeTag(NBTLimiter.noop(), nbtInput, true);
                if (!(encoded instanceof NBTCompound)) throw new IOException("item tag is not a compound");
                tag = (NBTCompound) encoded;
            }
        }
        ItemStack item = ItemStack.builder().type(type).amount(amount).nbt(tag)
                .version(ClientVersion.V_1_12_2).build();
        item.setDamageValue(damage);
        return item;
    }

    public static String readBoundedString(DataInput input, String label) throws IOException {
        int length = input.readUnsignedShort();
        if (length > MAX_STRING_BYTES) throw new IOException(label + " is too long");
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void writeBoundedString(DataOutput output, String value, String label) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) throw new IOException(label + " is too long");
        output.writeShort(bytes.length);
        output.write(bytes);
    }
}
