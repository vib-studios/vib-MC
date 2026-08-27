package net.vibmc.network.handler;

import net.vibmc.entity.ServerPlayer;
import net.vibmc.server.VibMC;

/** Play packets are handled by PacketEvents listeners; this object owns disconnect cleanup. */
public final class PlayHandler implements PacketHandler {
    private final ServerPlayer player;

    public PlayHandler(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public void onDisconnect(ServerPlayer connection, String reason) {
        VibMC server = VibMC.getInstance();
        server.executeOnMainThread(() -> {
            server.getPlayerManager().removePlayer(player);
            if (player.isInWorld()) player.getWorld().removeEntity(player);
            // Keep the PacketEvents User registered until authoritative gameplay removal has
            // completed; tick streaming may still send during the close-to-main-thread handoff.
            com.github.retrooper.packetevents.PacketEvents.getAPI().getProtocolManager()
                    .removeUser(player.channel());
        });
    }
}
