package net.vibmc.command.commands;

import net.vibmc.command.Command;
import net.vibmc.command.CommandSender;
import net.vibmc.server.VibMC;

import java.util.Locale;

public class WeatherCommand extends Command {
    public WeatherCommand() {
        super("weather", "Set the weather", "/weather <clear|rain|thunder>", "vibmc.command.weather");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("{\"text\":\"§cUsage: /weather <clear|rain|thunder>\"}");
            return false;
        }
        String weather = args[0].toLowerCase(Locale.ROOT);
        switch (weather) {
            case "clear":
            case "rain":
            case "thunder":
                VibMC.getInstance().getWorldManager().getMainWorld().weatherSystem().setWeather(weather);
                VibMC.getInstance().getPlayerManager().broadcastWeather(weather);
                sender.sendMessage("{\"text\":\"§aWeather set to " + weather + ".\"}");
                return true;
            default:
                sender.sendMessage("{\"text\":\"§cInvalid weather. Use clear, rain or thunder.\"}");
                return false;
        }
    }
}
