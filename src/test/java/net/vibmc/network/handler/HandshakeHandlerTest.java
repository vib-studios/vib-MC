package net.vibmc.network.handler;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HandshakeHandlerTest {
    @Test
    void onlyTheVanilla1122ProtocolIsSupported() {
        assertTrue(HandshakeHandler.isSupportedProtocol(340));
        assertTrue(HandshakeHandler.isSupportedProtocol(ClientVersion.V_1_12_2.getProtocolVersion()));
        assertFalse(HandshakeHandler.isSupportedProtocol(ClientVersion.V_1_16.getProtocolVersion()));
        assertFalse(HandshakeHandler.isSupportedProtocol(ClientVersion.V_1_16_1.getProtocolVersion()));
        assertFalse(HandshakeHandler.isSupportedProtocol(ClientVersion.V_1_19_3.getProtocolVersion()));
        assertFalse(HandshakeHandler.isSupportedProtocol(ClientVersion.V_1_20_2.getProtocolVersion()));
        assertFalse(HandshakeHandler.isSupportedProtocol(ClientVersion.V_26_2.getProtocolVersion()));
    }
}