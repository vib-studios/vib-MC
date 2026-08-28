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
    /** Version 2 added armour, the offhand, air supply, and exhaustion. */
    private static final int VERSION = 2;
    private static final int INVENTORY_SIZE = 36;
    private static final int ARMOR_SIZE = net.vibmc.inventory.Armor.SLOTS;
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
            if (version < 1 || version > VERSION) {
                throw new IOException("unsupported player-data version " + version);
            }
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
            // Version 1 files predate armour and vitals; they load with vanilla defaults.
            ItemStack[] armor = new ItemStack[ARMOR_SIZE];
            java.util.Arrays.fill(armor, ItemStack.EMPTY);
            ItemStack offhand = ItemStack.EMPTY;
            int air = 300;
            float exhaustion = 0.0f;
            if (version >= 2) {
                int worn = input.readUnsignedByte();
                if (worn != ARMOR_SIZE) throw new IOException("invalid armor size " + worn);
                for (int slot = 0; slot < worn; slot++) armor[slot] = readItem(input);
                offhand = readItem(input);
                air = input.readInt();
                exhaustion = input.readFloat();
                if (air < 0 || air > 300 || !Float.isFinite(exhaustion) || exhaustion < 0.0f) {
                    throw new IOException("invalid player vitals");
                }
            }
            if (input.read() != -1) throw new IOException("trailing bytes in player data");
            return Optional.of(new PlayerData(world, x, y, z, yaw, pitch, health, food,
                    saturation, gameMode, flying, heldSlot, inventory, armor, offhand, air, exhaustion));
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
                output.writeByte(data.armor.length);
                for (ItemStack piece : data.armor) writeItem(output, piece);
                writeItem(output, data.offhand);
                output.writeInt(data.airSupply);
                output.writeFloat(data.exhaustion);
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
        net.vibmc.inventory.ItemCodec.writeItem(output, item);
    }

    private static ItemStack readItem(DataInput input) throws IOException {
        return net.vibmc.inventory.ItemCodec.readItem(input);
    }

    private static String readBoundedString(DataInput input, String label) throws IOException {
        return net.vibmc.inventory.ItemCodec.readBoundedString(input, label);
    }

    private static void writeBoundedString(DataOutput output, String value, String label) throws IOException {
        net.vibmc.inventory.ItemCodec.writeBoundedString(output, value, label);
    }
}
