package net.vibmc.plugin.event;

import net.vibmc.entity.ServerPlayer;

public class PlayerQuitEvent extends Event {
    private final ServerPlayer player;
    private String quitMessage;

    public PlayerQuitEvent(ServerPlayer player, String quitMessage) {
        this.player = player;
        this.quitMessage = quitMessage;
    }

    public ServerPlayer getPlayer() { return player; }
    public String getQuitMessage() { return quitMessage; }
    public void setQuitMessage(String quitMessage) { this.quitMessage = quitMessage; }
}
