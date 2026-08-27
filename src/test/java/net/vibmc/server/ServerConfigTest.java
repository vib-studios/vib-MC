package net.vibmc.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerConfigTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsACompleteDefaultConfiguration() throws IOException {
        Path configPath = temporaryDirectory.resolve("nested/server.properties");

        ServerConfig config = ServerConfig.load(configPath);

        assertTrue(Files.isRegularFile(configPath));
        String contents = new String(Files.readAllBytes(configPath), StandardCharsets.ISO_8859_1);
        assertTrue(contents.startsWith("# vib-MC server properties"));
        assertTrue(contents.contains("server-port=25565"));
        assertTrue(contents.contains("level-name=world"));
        assertTrue(contents.contains("debug=false"));
        assertTrue(contents.contains("seed="));
        assertFalse(contents.contains("seed=0"));
        assertEquals(configPath.toAbsolutePath(), config.path());
        assertEquals(25565, config.port());
        assertEquals(4, config.getViewDistance());
        assertEquals(null, config.configuredSeed());
    }

    @Test
    void loadsExistingValuesWithoutRewritingTheFile() throws IOException {
        Path configPath = temporaryDirectory.resolve("server.properties");
        String original = "# keep this comment\nserver-port=25570\nlevel-name=custom world\nonline-mode=true\n";
        Files.write(configPath, original.getBytes(StandardCharsets.ISO_8859_1));

        ServerConfig config = ServerConfig.load(configPath);

        assertEquals(25570, config.port());
        assertEquals("custom world", config.worldName());
        assertTrue(config.onlineMode());
        assertEquals(original, new String(Files.readAllBytes(configPath), StandardCharsets.ISO_8859_1));
    }

    @Test
    void invalidValuesFallBackToSafeDefaults() throws IOException {
        Path configPath = temporaryDirectory.resolve("server.properties");
        Files.write(configPath, ("server-port=70000\n"
                + "max-players=nope\n"
                + "view-distance=0\n"
                + "online-mode=perhaps\n"
                + "seed=not-a-number\n").getBytes(StandardCharsets.ISO_8859_1));

        ServerConfig config = ServerConfig.load(configPath);

        assertEquals(25565, config.port());
        assertEquals(20, config.maxPlayers());
        assertEquals(4, config.getViewDistance());
        assertFalse(config.onlineMode());
        assertEquals((long) "not-a-number".hashCode(), config.seed());
    }

    @Test
    void rejectsAConfigurationPathThatIsADirectory() throws IOException {
        Path configPath = Files.createDirectory(temporaryDirectory.resolve("server.properties"));

        assertThrows(IOException.class, () -> ServerConfig.load(configPath));
    }
}
