package net.vibmc.command.commands;

import net.vibmc.command.Command;
import net.vibmc.command.CommandSender;
import net.vibmc.server.VibMC;

public class ListCommand extends Command {
    public ListCommand() {
        super("list", "List online players", "/list", null);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        java.util.Collection<net.vibmc.entity.ServerPlayer> players =
                VibMC.getInstance().getPlayerManager().getOnlinePlayers();
        if (players.isEmpty()) {
            sender.sendMessage("{\"text\":\"§7There are no players online.\"}");
            return true;
        }
        StringBuilder names = new StringBuilder();
        for (net.vibmc.entity.ServerPlayer player : players) {
            if (names.length() > 0) names.append("§7, ");
            names.append("§a").append(player.getUsername());
        }
        sender.sendMessage("{\"text\":\"§7Players online (" + players.size() + "): " + names + "\"}");
        return true;
    }
}
