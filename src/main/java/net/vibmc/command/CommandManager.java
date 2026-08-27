package net.vibmc.command;

import net.vibmc.command.commands.*;
import net.vibmc.permission.PermissionManager;
import net.vibmc.server.VibMC;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

public class CommandManager {
    private final Map<String, Command> commands;
    private Thread consoleThread;

    public CommandManager() {
        this.commands = new LinkedHashMap<>();
        registerDefaults();
    }

    private void registerDefaults() {
        register(new HelpCommand());
        register(new TeleportCommand());
        register(new GamemodeCommand());
        register(new TimeCommand());
        register(new WeatherCommand());
        register(new GiveCommand());
        register(new KillCommand());
        register(new SayCommand());
        register(new SeedCommand());
        register(new SaveAllCommand());
        register(new SaveOnCommand());
        register(new SaveOffCommand());
        register(new StopCommand());
        register(new ListCommand());
        register(new OpCommand());
        register(new DeopCommand());
        register(new KickCommand());
        register(new DimensionCommand());
    }

    public void register(Command command) {
        if (command == null) {
            throw new IllegalArgumentException("command cannot be null");
        }
        commands.put(command.getName().toLowerCase(Locale.ROOT), command);
        VibMC.getInstance().getLogger().debug("Registered command: /%s", command.getName());
    }

    public void registerCommand(Command command) {
        register(command);
    }

    public boolean execute(CommandSender sender, String input) {
        if (input == null || input.trim().isEmpty()) return false;

        String[] parts = input.trim().split("\\s+");
        String commandName = parts[0].toLowerCase(Locale.ROOT);
        if (commandName.startsWith("/")) {
            commandName = commandName.substring(1);
        }
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);

        Command command = commands.get(commandName);
        if (command == null) {
            sender.sendMessage("{\"text\":\"§cUnknown command. Use /help for a list of commands.\"}");
            return false;
        }

        if (!canExecute(sender, command)) {
            sender.sendMessage("{\"text\":\"§cYou don't have permission to use this command.\"}");
            return false;
        }

        return command.execute(sender, args);
    }

    public void startConsole() {
        consoleThread = new Thread(() -> {
            try (Scanner scanner = new Scanner(System.in)) {
                while (VibMC.getInstance().isRunning() && scanner.hasNextLine()) {
                    String input = scanner.nextLine();
                    if (input == null) break;
                    CommandSender console = new CommandSender("CONSOLE");
                    String commandLine = input;
                    if (!VibMC.getInstance().executeOnMainThread(
                            () -> execute(console, commandLine))) {
                        VibMC.getInstance().getLogger().warn("Console command rejected: main-thread queue is full");
                    }
                }
            } catch (RuntimeException e) {
                if (VibMC.getInstance().isRunning()) {
                    VibMC.getInstance().getLogger().warn("Console input stopped: %s", e);
                }
            }
        }, "Console");
        consoleThread.setDaemon(true);
        consoleThread.start();
    }

    public boolean canExecute(CommandSender sender, Command command) {
        if (sender == null || command == null) {
            return false;
        }
        if (!sender.isPlayer() || command.getPermission() == null || command.getPermission().isEmpty()) {
            return true;
        }
        if (VibMC.getInstance().getOperatorManager().isOperator(sender.getPlayer().getUuid())) {
            return true;
        }
        PermissionManager permissionManager = VibMC.getInstance().getPluginManager().getPermissionManager();
        return permissionManager.hasPermission(sender.getPlayer(), command.getPermission());
    }

    public Command getCommand(String name) {
        return name == null ? null : commands.get(name.toLowerCase(Locale.ROOT));
    }

    public Map<String, Command> getCommands() {
        return Collections.unmodifiableMap(commands);
    }
}
