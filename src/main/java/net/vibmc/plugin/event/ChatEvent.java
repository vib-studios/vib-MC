package net.vibmc.plugin.event;

import net.vibmc.entity.ServerPlayer;

public class ChatEvent extends Event implements Cancellable {
    private final ServerPlayer player;
    private String message;
    private boolean cancelled;

    public ChatEvent(ServerPlayer player, String message) {
        this.player = player;
        this.message = message;
        this.cancelled = false;
    }

    public ServerPlayer getPlayer() { return player; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    @Override
    public boolean isCancelled() { return cancelled; }
    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
