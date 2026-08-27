package net.vibmc.player;

import java.util.Locale;

public enum GameMode {
    SURVIVAL(0),
    CREATIVE(1),
    ADVENTURE(2),
    SPECTATOR(3);

    private final int id;

    GameMode(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static GameMode byId(int id) {
        for (GameMode mode : values()) {
            if (mode.id == id) return mode;
        }
        return SURVIVAL;
    }

    public static GameMode fromName(String name) {
        if (name == null) return null;
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "0":
            case "survival":
            case "s":
                return SURVIVAL;
            case "1":
            case "creative":
            case "c":
                return CREATIVE;
            case "2":
            case "adventure":
            case "a":
                return ADVENTURE;
            case "3":
            case "spectator":
            case "sp":
                return SPECTATOR;
            default:
                return null;
        }
    }
}
