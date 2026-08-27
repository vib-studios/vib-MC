package net.vibmc.world;

import net.vibmc.server.ServerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RandomSeedPersistenceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void blankSeedIsChosenOnceAndRestoredFromLevelData() throws Exception {
        Path world = temporaryDirectory.resolve("random-world");
        Path properties = temporaryDirectory.resolve("server.properties");
        Files.write(properties, ("level-name=" + world + "\nseed=\nallow-nether=false\nallow-end=false\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        ServerConfig config = ServerConfig.load(properties);
        assertNull(config.configuredSeed());

        long firstSeed = new WorldManager(config).getMainWorld().seed();
        long restoredSeed = new WorldManager(config).getMainWorld().seed();

        assertEquals(firstSeed, restoredSeed);
    }
}
