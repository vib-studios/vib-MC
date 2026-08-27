package net.vibmc.server;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Loads and, when necessary, creates the server configuration. */
public final class ServerConfig {
    private static final Map<String, String> DEFAULTS = createDefaults();

    private final Properties properties;
    private final Path path;

    private ServerConfig(Properties properties, Path path) {
        this.properties = properties;
        this.path = path;
    }

    /**
     * Loads a configuration file. If it does not exist, a complete default file
     * is written before the configuration is returned.
     *
     * @throws IOException if the file cannot be read or its default cannot be created
     */
    public static ServerConfig load(String path) throws IOException {
        return load(Paths.get(path));
    }

    public static ServerConfig load(Path path) throws IOException {
        Path normalizedPath = path.toAbsolutePath().normalize();
        createDefaultFileIfMissing(normalizedPath);

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(normalizedPath)) {
            properties.load(input);
        }
        return new ServerConfig(properties, normalizedPath);
    }

    private static void createDefaultFileIfMissing(Path path) throws IOException {
        if (Files.exists(path)) {
            if (!Files.isRegularFile(path)) {
                throw new IOException("Configuration path is not a regular file: " + path);
            }
            return;
        }

        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temporary = Files.createTempFile(parent, ".server.properties-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.ISO_8859_1)) {
                writer.write("# vib-MC server properties");
                writer.newLine();
                for (Map.Entry<String, String> entry : DEFAULTS.entrySet()) {
                    writer.write(entry.getKey());
                    writer.write('=');
                    writer.write(entry.getValue());
                    writer.newLine();
                }
            }

            try {
                Files.move(temporary, path);
            } catch (FileAlreadyExistsException e) {
                // Another server process won the creation race. Use its file.
                if (!Files.isRegularFile(path)) {
                    throw e;
                }
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Map<String, String> createDefaults() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("allow-end", "true");
        defaults.put("allow-flight", "false");
        defaults.put("allow-nether", "true");
        defaults.put("autosave-interval-ticks", "6000");
        defaults.put("debug", "false");
        defaults.put("difficulty", "easy");
        defaults.put("generate-structures", "true");
        defaults.put("level-name", "world");
        defaults.put("level-type", "default");
        defaults.put("max-build-height", "320");
        defaults.put("max-players", "20");
        defaults.put("max-tick-time", "60000");
        defaults.put("motd", "A vib-MC Server");
        defaults.put("online-mode", "false");
        defaults.put("pvp", "true");
        defaults.put("proxy-mode", "none");
        defaults.put("proxy-trusted-address", "127.0.0.1");
        defaults.put("resource-pack", "");
        defaults.put("resource-pack-sha1", "");
        defaults.put("seed", "");
        defaults.put("server-ip", "0.0.0.0");
        defaults.put("server-port", "25565");
        defaults.put("shutdown-message", "Server closed");
        defaults.put("simulation-distance", "8");
        defaults.put("spawn-animals", "true");
        defaults.put("spawn-monsters", "true");
        defaults.put("view-distance", "4");
        return Collections.unmodifiableMap(defaults);
    }

    public Path path() {
        return path;
    }

    public String address() {
        return getString("server-ip");
    }

    public int port() {
        return getInt("server-port", 1, 65535);
    }

    public String worldName() {
        return getString("level-name");
    }

    /** Returns the configured seed, or null when a new world should choose one randomly. */
    public Long configuredSeed() {
        String value = properties.getProperty("seed", "").trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return (long) value.hashCode();
        }
    }

    /** Compatibility accessor; new-world creation should use configuredSeed(). */
    public long seed() {
        Long configured = configuredSeed();
        return configured == null ? 0L : configured;
    }

    public String motd() {
        return getString("motd");
    }

    public int maxPlayers() {
        return getInt("max-players", 1, Integer.MAX_VALUE);
    }

    public int getMaxPlayers() {
        return maxPlayers();
    }

    public int getViewDistance() {
        return getInt("view-distance", 1, 32);
    }

    public boolean onlineMode() {
        return getBoolean("online-mode");
    }

    public boolean allowFlight() {
        return getBoolean("allow-flight");
    }

    public boolean allowNether() { return getBoolean("allow-nether"); }
    public boolean allowEnd() { return getBoolean("allow-end"); }
    public boolean generateStructures() { return getBoolean("generate-structures"); }

    public String difficulty() {
        String value = getString("difficulty").toLowerCase(java.util.Locale.ROOT);
        return "peaceful".equals(value) || "normal".equals(value) || "hard".equals(value)
                ? value : "easy";
    }

    /** Ticks between automatic world saves. Zero disables autosaving. */
    public int autosaveIntervalTicks() {
        return getInt("autosave-interval-ticks", 0, Integer.MAX_VALUE);
    }

    public boolean debug() {
        return getBoolean("debug");
    }

    public String proxyMode() {
        String mode = getString("proxy-mode").toLowerCase(java.util.Locale.ROOT);
        return "legacy".equals(mode) ? mode : "none";
    }

    public String proxyTrustedAddress() {
        return getString("proxy-trusted-address");
    }

    public String shutdownMessage() {
        return getString("shutdown-message");
    }

    public boolean useLegacyProxyForwarding() {
        return "legacy".equals(proxyMode());
    }

    private String getString(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return DEFAULTS.get(key);
        }
        return value.trim();
    }

    private int getInt(String key, int minimum, int maximum) {
        int defaultValue = Integer.parseInt(DEFAULTS.get(key));
        try {
            int value = Integer.parseInt(getString(key));
            return value >= minimum && value <= maximum ? value : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private boolean getBoolean(String key) {
        String value = getString(key);
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return Boolean.parseBoolean(DEFAULTS.get(key));
    }
}
