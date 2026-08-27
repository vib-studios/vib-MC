package net.vibmc.network.handler;

import net.vibmc.entity.ServerPlayer;
import net.vibmc.network.JsonText;
import net.vibmc.server.ServerConfig;
import net.vibmc.server.VibMC;

public final class StatusHandler implements PacketHandler {
    public void processRequest(ServerPlayer connection) {
        connection.getUser().sendPacket(new com.github.retrooper.packetevents.wrapper.status.server.WrapperStatusServerResponse(buildStatusJson(connection)));
    }

    public void processPing(ServerPlayer connection, long payload) {
        connection.getUser().sendPacket(new com.github.retrooper.packetevents.wrapper.status.server.WrapperStatusServerPong(payload));
    }

    private String buildStatusJson(ServerPlayer connection) {
        VibMC server = VibMC.getInstance();
        ServerConfig config = server.getConfig();
        com.github.retrooper.packetevents.protocol.player.ClientVersion wireVersion =
                connection.getUser().getClientVersion();
        return "{\"version\":{\"name\":" + JsonText.quote(wireVersion.getReleaseName())
                + ",\"protocol\":" + wireVersion.getProtocolVersion() + "},"
                + "\"players\":{\"max\":" + config.maxPlayers() + ",\"online\":"
                + server.getPlayerManager().getOnlineCount() + ",\"sample\":[]},"
                + "\"previewsChat\":false,\"enforcesSecureChat\":false,"
                + "\"description\":{\"text\":" + JsonText.quote(config.motd()) + "}}";
    }
}
