package net.vibmc.plugin.event;

import net.vibmc.entity.ServerPlayer;

public class PlayerJoinEvent extends Event {
    private final ServerPlayer player;
    private String joinMessage;

    public PlayerJoinEvent(ServerPlayer player, String joinMessage) {
        this.player = player;
        this.joinMessage = joinMessage;
    }

    public ServerPlayer getPlayer() { return player; }
    public String getJoinMessage() { return joinMessage; }
    public void setJoinMessage(String joinMessage) { this.joinMessage = joinMessage; }
}
