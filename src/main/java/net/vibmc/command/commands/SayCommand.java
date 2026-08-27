package net.vibmc.command.commands;

import net.vibmc.command.Command;
import net.vibmc.command.CommandSender;
import net.vibmc.server.VibMC;
import net.vibmc.network.JsonText;

public class SayCommand extends Command {
    public SayCommand() {
        super("say", "Broadcast a message to all players", "/say <message>", "vibmc.command.say");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("{\"text\":\"§cUsage: /say <message>\"}");
            return false;
        }
        StringBuilder message = new StringBuilder();
        for (String arg : args) {
            if (message.length() > 0) message.append(' ');
            message.append(arg);
        }
        VibMC.getInstance().getPlayerManager().broadcastMessage(
                JsonText.component("§7[" + sender.getName() + "] " + message));
        return true;
    }
}
