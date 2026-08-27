package net.vibmc.registry;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.google.gson.JsonElement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftDataRegistryTest {
    @BeforeAll static void initialize() throws Exception {MinecraftDataRegistry.initialize();}

    @Test
    void resolvesCanonicalAndModernJavaSnapshots(){
        MinecraftDataRegistry.VersionData canonical=MinecraftDataRegistry.get()
                .forClient(ClientVersion.V_1_12_2);
        assertEquals("1.12.2",canonical.release());
        assertTrue(canonical.has("version"));

        MinecraftDataRegistry.VersionData modern=MinecraftDataRegistry.get()
                .forClient(ClientVersion.V_1_19_4);
        assertEquals("1.19.4",modern.release());
        assertTrue(modern.has("loginPacket"));
        assertFalse(modern.has("blocks"));
        assertFalse(modern.has("items"));
        assertFalse(modern.has("biomes"));

        MinecraftDataRegistry.VersionData oneTwentyThree=MinecraftDataRegistry.get()
                .forClient(ClientVersion.V_1_20_3);
        assertEquals("1.20.3",oneTwentyThree.release());
        assertTrue(oneTwentyThree.has("loginPacket"));
    }

    @Test
    void registryJsonIsParsedLazilyAndCached(){
        MinecraftDataRegistry.VersionData data=MinecraftDataRegistry.get()
                .forClient(ClientVersion.V_1_20);
        JsonElement first=data.data("version");
        assertTrue(first.isJsonObject());
        assertSame(first,data.data("version"));
        assertEquals(763,first.getAsJsonObject().get("version").getAsInt());
    }
}
