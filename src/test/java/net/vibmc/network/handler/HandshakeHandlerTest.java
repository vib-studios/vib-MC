package net.vibmc.network.handler;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HandshakeHandlerTest {
    @Test
    void onlyKnownPacketEventsBreakagesAreRejected(){
        assertTrue(HandshakeHandler.isKnownBrokenVersion(ClientVersion.V_1_16));
        assertTrue(HandshakeHandler.isKnownBrokenVersion(ClientVersion.V_1_16_1));
        assertTrue(HandshakeHandler.isKnownBrokenVersion(ClientVersion.V_1_19_3));
        assertFalse(HandshakeHandler.isKnownBrokenVersion(ClientVersion.V_1_20_2));
        assertFalse(HandshakeHandler.isKnownBrokenVersion(ClientVersion.V_26_2));
    }
}
