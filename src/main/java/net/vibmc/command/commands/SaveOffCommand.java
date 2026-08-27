package net.vibmc.command.commands;
import net.vibmc.command.Command;import net.vibmc.command.CommandSender;import net.vibmc.network.JsonText;import net.vibmc.server.VibMC;
public final class SaveOffCommand extends Command{public SaveOffCommand(){super("save-off","Disable automatic and shutdown saving","/save-off","vibmc.command.save");}public boolean execute(CommandSender s,String[]a){VibMC.getInstance().setAutomaticSaving(false);s.sendMessage(JsonText.component("§aAutomatic saving is now disabled. Use /save-all to save manually."));return true;}}
