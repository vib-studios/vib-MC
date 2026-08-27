package net.vibmc.command;

import net.vibmc.entity.ServerPlayer;
import net.vibmc.network.JsonText;
import net.vibmc.server.VibMC;

public class CommandSender {
    private final String name;
    private final ServerPlayer player;

    public CommandSender(String name) {
        this.name = name;
        this.player = null;
    }

    public CommandSender(ServerPlayer player) {
        this.player = player;
        this.name = player.getUsername();
    }

    public boolean isPlayer() {
        return player != null;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public String getName() {
        return name;
    }

    public void sendMessage(String message) {
        if (player != null) {
            player.sendMessage(message);
        } else {
            String plain = JsonText.toConsoleText(message).replaceAll("§[0-9A-FK-ORa-fk-or]", "");
            VibMC.getInstance().getLogger().info("%s", plain);
        }
    }
}
