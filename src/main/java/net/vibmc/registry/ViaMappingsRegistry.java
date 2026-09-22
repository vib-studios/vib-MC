package net.vibmc.registry;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.viaversion.nbt.io.NBTIO;
import com.viaversion.nbt.tag.compound.CompoundTag;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Loads ViaVersion Mappings NBT snapshots from classpath resources using ViaNBT. */
public final class ViaMappingsRegistry {
    private static final String ROOT = "/mappings/";
    private static volatile ViaMappingsRegistry instance;

    private final Map<String, String> releaseByProtocol;
    private final Map<String, CompoundTag> cache = new ConcurrentHashMap<>();

    private ViaMappingsRegistry(Map<String, String> releaseByProtocol) {
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
        instance = new ViaMappingsRegistry(Collections.unmodifiableMap(protocols));
    }

    public static ViaMappingsRegistry get() {
        ViaMappingsRegistry loaded = instance;
        if (loaded == null) {
            try {
                initialize();
                loaded = instance;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to initialize ViaMappingsRegistry", e);
            }
        }
        return loaded;
    }

    public CompoundTag forClient(ClientVersion clientVersion) {
        if (clientVersion == null) throw new IllegalArgumentException("clientVersion");
        String release = selectRelease(clientVersion);
        return cache.computeIfAbsent(release, this::loadMappingsNbt);
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
        return ViaMappingsRegistry.class.getResource(ROOT + "mapping-" + release + ".nbt") != null;
    }

    private CompoundTag loadMappingsNbt(String release) {
        String path = ROOT + "mapping-" + release + ".nbt";
        InputStream stream = ViaMappingsRegistry.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalArgumentException("Missing ViaVersion Mappings NBT resource for " + release + ": " + path);
        }
        try (InputStream in = stream) {
            return NBTIO.read(in);
        } catch (IOException e) {
            throw new RegistryLoadException("Failed to read ViaVersion Mappings NBT for " + release, e);
        }
    }

    public static final class RegistryLoadException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public RegistryLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
