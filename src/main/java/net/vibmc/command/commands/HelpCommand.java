package net.vibmc.command.commands;

import net.vibmc.command.Command;
import net.vibmc.command.CommandSender;
import net.vibmc.server.VibMC;

public class HelpCommand extends Command {
    public HelpCommand() {
        super("help", "Shows a list of commands", "/help", null);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        sender.sendMessage("{\"text\":\"§6--- Commands ---\"}");
        for (Command command : VibMC.getInstance().getCommandManager().getCommands().values()) {
            if (!VibMC.getInstance().getCommandManager().canExecute(sender, command)) {
                continue;
            }
            sender.sendMessage("{\"text\":\"§e/" + command.getName() + " §7- " + command.getDescription() + "\"}");
        }
        return true;
    }
}
