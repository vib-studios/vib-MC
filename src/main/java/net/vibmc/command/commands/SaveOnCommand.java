package net.vibmc.command.commands;
import net.vibmc.command.Command;import net.vibmc.command.CommandSender;import net.vibmc.network.JsonText;import net.vibmc.server.VibMC;
public final class SaveOnCommand extends Command{public SaveOnCommand(){super("save-on","Enable automatic saving","/save-on","vibmc.command.save");}public boolean execute(CommandSender s,String[]a){VibMC.getInstance().setAutomaticSaving(true);s.sendMessage(JsonText.component("§aAutomatic saving is now enabled."));return true;}}
