package net.vibmc.network.handler;

import net.vibmc.entity.ServerPlayer;

/** Lifecycle hook retained while PacketEvents listeners own packet processing. */
public interface PacketHandler {
    default void onConnect(ServerPlayer connection) {}
    default void onDisconnect(ServerPlayer connection, String reason) {}
}
