package net.vibmc.network.packetevents;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.handshaking.client.WrapperHandshakingClientHandshake;
import com.github.retrooper.packetevents.wrapper.login.client.WrapperLoginClientEncryptionResponse;
import com.github.retrooper.packetevents.wrapper.login.client.WrapperLoginClientLoginStart;
import com.github.retrooper.packetevents.wrapper.status.client.WrapperStatusClientPing;
import net.vibmc.entity.ServerPlayer;
import net.vibmc.network.HandshakeRequest;
import net.vibmc.network.handler.ConfigurationHandler;
import net.vibmc.network.handler.HandshakeHandler;
import net.vibmc.network.handler.LoginHandler;
import net.vibmc.network.handler.PacketHandler;
import net.vibmc.network.handler.StatusHandler;

/** Native PacketEvents handling for handshake, status, and login packets. */
public final class VibLifecyclePacketListener implements PacketListener {
    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer)) return;
        ServerPlayer connection = (ServerPlayer) event.getPlayer();
        PacketHandler handler = connection.getHandler();

        if (event.getPacketType() == PacketType.Handshaking.Client.HANDSHAKE) {
            event.setCancelled(true);
            if (!(handler instanceof HandshakeHandler)) {
                rejectInvalidSequence(connection);
                return;
            }
            WrapperHandshakingClientHandshake wrapper = new WrapperHandshakingClientHandshake(event);
            ((HandshakeHandler) handler).process(connection, new HandshakeRequest(
                    wrapper.getProtocolVersion(), wrapper.getServerAddress(),
                    wrapper.getServerPort(), wrapper.getIntention().getId()));
        } else if (event.getPacketType() == PacketType.Status.Client.REQUEST) {
            event.setCancelled(true);
            if (!(handler instanceof StatusHandler)) {
                rejectInvalidSequence(connection);
                return;
            }
            ((StatusHandler) handler).processRequest(connection);
        } else if (event.getPacketType() == PacketType.Status.Client.PING) {
            event.setCancelled(true);
            if (!(handler instanceof StatusHandler)) {
                rejectInvalidSequence(connection);
                return;
            }
            ((StatusHandler) handler).processPing(connection,
                    new WrapperStatusClientPing(event).getTime());
        } else if (event.getPacketType() == PacketType.Login.Client.LOGIN_START) {
            event.setCancelled(true);
            if (!(handler instanceof LoginHandler)) {
                rejectInvalidSequence(connection);
                return;
            }
            ((LoginHandler) handler).beginLogin(connection,
                    new WrapperLoginClientLoginStart(event).getUsername());
        } else if (event.getPacketType() == PacketType.Login.Client.ENCRYPTION_RESPONSE) {
            event.setCancelled(true);
            if (!(handler instanceof LoginHandler)) {
                rejectInvalidSequence(connection);
                return;
            }
            WrapperLoginClientEncryptionResponse wrapper =
                    new WrapperLoginClientEncryptionResponse(event);
            ((LoginHandler) handler).receiveEncryptionResponse(connection,
                    wrapper.getEncryptedSharedSecret(),
                    wrapper.getEncryptedVerifyToken().orElse(new byte[0]));
        } else if (event.getPacketType() == PacketType.Login.Client.LOGIN_SUCCESS_ACK) {
            event.setCancelled(true);
            if (!(handler instanceof ConfigurationHandler)) {
                rejectInvalidSequence(connection);return;
            }
            ((ConfigurationHandler)handler).begin(connection);
        } else if (event.getPacketType() == PacketType.Configuration.Client.SELECT_KNOWN_PACKS) {
            event.setCancelled(true);
            if (!(handler instanceof ConfigurationHandler)) {
                rejectInvalidSequence(connection);return;
            }
            // vib-MC sends the complete registry set regardless of the client's selection.
            ((ConfigurationHandler)handler).knownPacksSelected(connection);
        } else if (event.getPacketType() == PacketType.Configuration.Client.CONFIGURATION_END_ACK) {
            event.setCancelled(true);
            if (!(handler instanceof ConfigurationHandler)) {
                rejectInvalidSequence(connection);return;
            }
            ((ConfigurationHandler)handler).complete(connection);
        } else if (event.getPacketType() == PacketType.Configuration.Client.CLIENT_SETTINGS
                || event.getPacketType() == PacketType.Configuration.Client.PLUGIN_MESSAGE) {
            event.setCancelled(true);
            if (!(handler instanceof ConfigurationHandler)) rejectInvalidSequence(connection);
        }
    }

    private static void rejectInvalidSequence(ServerPlayer connection) {
        // PacketEvents owns the wire-state machine. The vib-MC handler mirror should agree;
        // disagreement means a duplicate/out-of-order packet, never a valid cast opportunity.
        connection.forceClose();
    }
}
