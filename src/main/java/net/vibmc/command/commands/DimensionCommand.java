package net.vibmc.command.commands;

import net.vibmc.command.Command;
import net.vibmc.command.CommandSender;
import net.vibmc.entity.ServerPlayer;
import net.vibmc.network.JsonText;
import net.vibmc.server.VibMC;
import net.vibmc.world.World;

public final class DimensionCommand extends Command {
    public DimensionCommand(){super("dimension","Move to another dimension","/dimension <overworld|nether|end> [player]","vibmc.command.dimension");}
    @Override public boolean execute(CommandSender sender,String[] args){
        if(args.length<1){sender.sendMessage(JsonText.component("§cUsage: /dimension <overworld|nether|end> [player]"));return false;}
        ServerPlayer target=args.length>1?VibMC.getInstance().getPlayerManager().getPlayer(args[1]):sender.getPlayer();
        if(target==null){sender.sendMessage(JsonText.component("§cPlayer must be specified and online."));return false;}
        World world;switch(args[0].toLowerCase(java.util.Locale.ROOT)){case "overworld":world=VibMC.getInstance().getWorldManager().getMainWorld();break;case "nether":world=VibMC.getInstance().getWorldManager().getNetherWorld();break;case "end":world=VibMC.getInstance().getWorldManager().getEndWorld();break;default:sender.sendMessage(JsonText.component("§cUnknown dimension."));return false;}
        if(world==null){sender.sendMessage(JsonText.component("§cThat dimension is disabled."));return false;}
        VibMC.getInstance().getPlayerManager().transferPlayer(target,world);sender.sendMessage(JsonText.component("§aMoved "+target.getUsername()+" to "+args[0]+"."));return true;
    }
}
