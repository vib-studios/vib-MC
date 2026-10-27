package net.vibmc.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ServerConfigAuthTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultIsAuthenticatedMode() throws IOException {
        Path configPath = temporaryDirectory.resolve("server.properties");
        ServerConfig config = ServerConfig.load(configPath);
        assertTrue(config.onlineMode());
    }

    @Test
    void explicitOfflineModeDetected() throws IOException {
        Path configPath = temporaryDirectory.resolve("server.properties");
        Files.write(configPath, "online-mode=false\n".getBytes(StandardCharsets.ISO_8859_1));
        ServerConfig config = ServerConfig.load(configPath);
        assertFalse(config.onlineMode());
        assertTrue(config.isExplicitOfflineMode());
    }

    @Test
    void proxyModeRequiresOnlineModeValidation() throws IOException {
        Path configPath = temporaryDirectory.resolve("server.properties");
        Files.write(configPath, ("online-mode=false\nproxy-mode=legacy\n").getBytes(StandardCharsets.ISO_8859_1));
        ServerConfig config = ServerConfig.load(configPath);
        // Configuration loads; validation is enforced at startup by VibMC
        assertEquals("legacy", config.proxyMode());
        assertFalse(config.onlineMode());
    }
}
