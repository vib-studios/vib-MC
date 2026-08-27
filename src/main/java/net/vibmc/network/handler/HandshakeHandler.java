package net.vibmc.network.handler;

import net.vibmc.entity.ServerPlayer;
import net.vibmc.network.HandshakeRequest;
import net.vibmc.network.ProtocolState;
import net.vibmc.server.ServerConfig;
import net.vibmc.server.VibMC;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;

import java.net.InetSocketAddress;
import java.util.UUID;

public final class HandshakeHandler implements PacketHandler {
    public void process(ServerPlayer connection, HandshakeRequest request) {
        int protocol = request.protocolVersion();
        String address = request.address();
        int nextState = request.nextState();
        // PacketEvents' InternalPacketListener owns User client-version and protocol-state
        // transitions. vib-MC only mirrors the handshake values needed by its core handlers.

        if (nextState == 1) {
            connection.setVirtualHost(address);
            connection.setProtocolState(ProtocolState.STATUS);
            connection.setHandler(new StatusHandler());
            return;
        }
        if (nextState != 2) {
            connection.forceClose();
            return;
        }

        connection.setProtocolState(ProtocolState.LOGIN);
        ClientVersion activeVersion = connection.getUser().getClientVersion();
        if (isKnownBrokenVersion(activeVersion)) {
            VibMC.getInstance().getLogger().warn(
                    "Rejecting known-broken protocol %d (%s)",protocol,activeVersion.getReleaseName());
            if(activeVersion==ClientVersion.V_1_19_3)
                connection.disconnect("Minecraft 1.19.3 is unsupported due to a PacketEvents login parsing issue.");
            else
                connection.disconnect("Minecraft 1.16 and 1.16.1 are unsupported due to PacketEvents packet ID issues.");
            return;
        }
        VibMC.getInstance().getLogger().info(
                "Accepting login protocol %d; PacketEvents client=%s/%d, native server=1.12.2/340",
                protocol, activeVersion.getReleaseName(), activeVersion.getProtocolVersion());

        ServerConfig config = VibMC.getInstance().getConfig();
        if (config.useLegacyProxyForwarding()) {
            if (!isTrustedProxy(connection, config.proxyTrustedAddress())) {
                connection.disconnect("This server requires connections through its configured proxy.");
                return;
            }
            String[] forwarded = address.split("\\0", -1);
            if (forwarded.length < 3) {
                connection.disconnect("Invalid proxy forwarding data.");
                return;
            }
            try {
                connection.setVirtualHost(forwarded[0]);
                connection.setForwardedAddress(forwarded[1]);
                connection.setProfileUuid(parseUuid(forwarded[2]));
                if (forwarded.length >= 4) {
                    connection.setProfileProperties(SessionAuthentication.parseProperties(forwarded[3]));
                }
            } catch (IllegalArgumentException e) {
                connection.disconnect("Invalid proxy forwarding identity.");
                return;
            }
        } else {
            connection.setVirtualHost(address);
        }
        connection.setHandler(new LoginHandler());
    }

    static boolean isKnownBrokenVersion(ClientVersion version){
        return version==ClientVersion.V_1_16||version==ClientVersion.V_1_16_1
                ||version==ClientVersion.V_1_19_3;
    }

    private static boolean isTrustedProxy(ServerPlayer connection, String expectedAddress) {
        try {
            InetSocketAddress remote = (InetSocketAddress) connection.channel().remoteAddress();
            return remote.getAddress().getHostAddress().equals(expectedAddress)
                    || remote.getHostString().equalsIgnoreCase(expectedAddress);
        } catch (Exception e) {
            return false;
        }
    }

    private static UUID parseUuid(String value) {
        String normalized = value.replace("-", "");
        if (!normalized.matches("[0-9a-fA-F]{32}")) {
            throw new IllegalArgumentException("bad UUID");
        }
        return UUID.fromString(normalized.substring(0, 8) + "-" + normalized.substring(8, 12)
                + "-" + normalized.substring(12, 16) + "-" + normalized.substring(16, 20)
                + "-" + normalized.substring(20));
    }
}
