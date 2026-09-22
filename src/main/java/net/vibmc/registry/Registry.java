package net.vibmc.registry;

import com.github.retrooper.packetevents.protocol.nbt.*;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Registry loader reading ViaVersion Mappings NBT snapshots directly into PacketEvents NBT. */
public final class Registry {
    private static final String ROOT = "/mappings/";
    private static volatile Registry instance;

    private final Map<String, String> releaseByProtocol;
    private final Map<String, NBTCompound> cache = new ConcurrentHashMap<>();
    private final Map<String, NBTCompound> extraCache = new ConcurrentHashMap<>();

    private Registry(Map<String, String> releaseByProtocol) {
        this.releaseByProtocol = releaseByProtocol;
    }

    public static synchronized void initialize() throws IOException {
        if (instance != null) return;
        Map<String, String> protocols = new LinkedHashMap<>();
        protocols.put("754", "1.16.2");
        protocols.put("757", "1.17");
        protocols.put("758", "1.18.2");
        protocols.put("759", "1.19");
        protocols.put("762", "1.19.4");
        protocols.put("763", "1.20");
        protocols.put("764", "1.20.2");
        protocols.put("765", "1.20.3");
        protocols.put("766", "1.20.5");
        protocols.put("767", "1.21");
        protocols.put("768", "1.21.2");
        protocols.put("769", "1.21.5");
        protocols.put("770", "1.21.9");
        protocols.put("771", "1.21.11");
        protocols.put("772", "26.1");
        instance = new Registry(Collections.unmodifiableMap(protocols));
    }

    public static Registry get() {
        Registry loaded = instance;
        if (loaded == null) {
            try {
                initialize();
                loaded = instance;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to initialize Registry", e);
            }
        }
        return loaded;
    }

    public NBTCompound forClient(ClientVersion clientVersion) {
        if (clientVersion == null) throw new IllegalArgumentException("clientVersion cannot be null");
        String release = selectRelease(clientVersion);
        return cache.computeIfAbsent(release, this::loadMappingsNbt);
    }

    public NBTCompound extraResource(String name) {
        return extraCache.computeIfAbsent(name, key -> {
            String path = ROOT + "extra/" + key;
            InputStream stream = Registry.class.getResourceAsStream(path);
            if (stream == null) return new NBTCompound();
            try (InputStream in = stream) {
                return readNbtCompound(in);
            } catch (IOException e) {
                throw new RegistryLoadException("Failed to read extra mapping resource " + key, e);
            }
        });
    }

    public String selectRelease(ClientVersion clientVersion) {
        String releaseName = clientVersion.getReleaseName();
        if (releaseName != null) {
            for (String candidate : releaseName.split("/")) {
                String trimmed = candidate.trim();
                if (hasResource(trimmed)) return trimmed;
            }
        }
        String protocolRelease = releaseByProtocol.get(String.valueOf(clientVersion.getProtocolVersion()));
        if (protocolRelease != null && hasResource(protocolRelease)) return protocolRelease;
        return "1.20.5";
    }

    private boolean hasResource(String release) {
        return Registry.class.getResource(ROOT + "mapping-" + release + ".nbt") != null;
    }

    private NBTCompound loadMappingsNbt(String release) {
        String path = ROOT + "mapping-" + release + ".nbt";
        InputStream stream = Registry.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalArgumentException("Missing ViaVersion Mappings NBT resource for " + release + ": " + path);
        }
        try (InputStream in = stream) {
            return readNbtCompound(in);
        } catch (IOException e) {
            throw new RegistryLoadException("Failed to read ViaVersion Mappings NBT for " + release, e);
        }
    }

    public static NBTCompound readNbtCompound(InputStream in) throws IOException {
        DataInputStream din = (in instanceof DataInputStream) ? (DataInputStream) in : new DataInputStream(in);
        byte type = din.readByte();
        if (type != 10) throw new IOException("Expected root compound tag (10), got " + type);
        readString(din);
        return readCompoundBody(din);
    }

    private static NBTCompound readCompoundBody(DataInputStream din) throws IOException {
        NBTCompound compound = new NBTCompound();
        while (true) {
            byte type = din.readByte();
            if (type == 0) break;
            String name = readString(din);
            compound.setTag(name, readTagPayload(din, type));
        }
        return compound;
    }

    private static NBT readTagPayload(DataInputStream din, byte type) throws IOException {
        switch (type) {
            case 1: return new NBTByte(din.readByte());
            case 2: return new NBTShort(din.readShort());
            case 3: return new NBTInt(din.readInt());
            case 4: return new NBTLong(din.readLong());
            case 5: return new NBTFloat(din.readFloat());
            case 6: return new NBTDouble(din.readDouble());
            case 7: {
                int len = din.readInt();
                byte[] bytes = new byte[len];
                din.readFully(bytes);
                return new NBTByteArray(bytes);
            }
            case 8: return new NBTString(readString(din));
            case 9: {
                byte elemTypeByte = din.readByte();
                int count = din.readInt();
                NBTType<?> peType = toPacketEventsType(elemTypeByte);
                @SuppressWarnings({"rawtypes", "unchecked"})
                NBTList peList = new NBTList(peType);
                for (int i = 0; i < count; i++) {
                    peList.addTag(readTagPayload(din, elemTypeByte));
                }
                return peList;
            }
            case 10: return readCompoundBody(din);
            case 11: {
                int count = din.readInt();
                int[] ints = new int[count];
                for (int i = 0; i < count; i++) ints[i] = din.readInt();
                return new NBTIntArray(ints);
            }
            case 12: {
                int count = din.readInt();
                long[] longs = new long[count];
                for (int i = 0; i < count; i++) longs[i] = din.readLong();
                return new NBTLongArray(longs);
            }
            default: throw new IOException("Unknown NBT tag type: " + type);
        }
    }

    private static String readString(DataInputStream din) throws IOException {
        int len = din.readUnsignedShort();
        byte[] bytes = new byte[len];
        din.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static NBTType<?> toPacketEventsType(byte type) {
        switch (type) {
            case 1: return NBTType.BYTE;
            case 2: return NBTType.SHORT;
            case 3: return NBTType.INT;
            case 4: return NBTType.LONG;
            case 5: return NBTType.FLOAT;
            case 6: return NBTType.DOUBLE;
            case 7: return NBTType.BYTE_ARRAY;
            case 8: return NBTType.STRING;
            case 9: return NBTType.LIST;
            case 10: return NBTType.COMPOUND;
            case 11: return NBTType.INT_ARRAY;
            case 12: return NBTType.LONG_ARRAY;
            default: return NBTType.END;
        }
    }

    public static final class RegistryLoadException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public RegistryLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
