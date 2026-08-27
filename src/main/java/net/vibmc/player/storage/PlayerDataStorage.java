package net.vibmc.player.storage;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.nbt.NBT;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTLimiter;
import com.github.retrooper.packetevents.protocol.nbt.serializer.DefaultNBTSerializer;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import net.vibmc.player.GameMode;

import java.io.*;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

/** Binary, atomic player-data storage. Item payloads use PacketEvents' complete NBT codec. */
public final class PlayerDataStorage {
    private static final int MAGIC = 0x56494250; // VIBP
    private static final int VERSION = 1;
    private static final int INVENTORY_SIZE = 36;
    private static final int MAX_ITEM_NBT_BYTES = 1 << 20;
    private static final int MAX_STRING_BYTES = 32767;
    private static final int MAX_FILE_BYTES = 8 << 20;
    private final Path directory;

    public PlayerDataStorage(Path directory) {
        this.directory = directory;
    }

    public Optional<PlayerData> read(UUID uuid) throws IOException {
        Path file = file(uuid);
        if (!Files.exists(file)) return Optional.empty();
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) {
            throw new IOException("invalid or oversized player-data file");
        }
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (input.readInt() != MAGIC) throw new IOException("invalid player-data magic");
            int version = input.readInt();
            if (version != VERSION) throw new IOException("unsupported player-data version " + version);
            String world = readBoundedString(input, "world name");
            double x = input.readDouble(), y = input.readDouble(), z = input.readDouble();
            float yaw = input.readFloat(), pitch = input.readFloat(), health = input.readFloat();
            int food = input.readUnsignedByte();
            float saturation = input.readFloat();
            GameMode gameMode = GameMode.byId(input.readUnsignedByte());
            boolean flying = input.readBoolean();
            int heldSlot = input.readUnsignedByte();
            int slots = input.readUnsignedByte();
            if (slots != INVENTORY_SIZE) throw new IOException("invalid inventory size " + slots);
            ItemStack[] inventory = new ItemStack[slots];
            for (int slot = 0; slot < slots; slot++) inventory[slot] = readItem(input);
            if (input.read() != -1) throw new IOException("trailing bytes in player data");
            return Optional.of(new PlayerData(world, x, y, z, yaw, pitch, health, food,
                    saturation, gameMode, flying, heldSlot, inventory));
        } catch (RuntimeException error) {
            throw new IOException("invalid player data for " + uuid, error);
        }
    }

    public void write(UUID uuid, PlayerData data) throws IOException {
        Files.createDirectories(directory);
        Path target = file(uuid);
        Path temporary = directory.resolve(uuid.toString() + ".dat.tmp");
        try {
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                writeBoundedString(output, data.worldName, "world name");
                output.writeDouble(data.x); output.writeDouble(data.y); output.writeDouble(data.z);
                output.writeFloat(data.yaw); output.writeFloat(data.pitch); output.writeFloat(data.health);
                output.writeByte(data.foodLevel);
                output.writeFloat(data.foodSaturation);
                output.writeByte(data.gameMode.getId());
                output.writeBoolean(data.flying);
                output.writeByte(data.heldItemSlot);
                output.writeByte(data.inventory.length);
                for (ItemStack item : data.inventory) writeItem(output, item);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public Path directory() {
        return directory;
    }

    private Path file(UUID uuid) {
        return directory.resolve(uuid.toString() + ".dat");
    }

    private static void writeItem(DataOutput output, ItemStack item) throws IOException {
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

    private static String readBoundedString(DataInput input, String label) throws IOException {
        int length = input.readUnsignedShort();
        if (length > MAX_STRING_BYTES) throw new IOException(label + " is too long");
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void writeBoundedString(DataOutput output, String value, String label) throws IOException {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) throw new IOException(label + " is too long");
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private static ItemStack readItem(DataInput input) throws IOException {
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
}
