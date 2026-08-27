package net.vibmc.permission;

import net.vibmc.entity.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** UUID-based vanilla-shaped ops.json storage. */
public final class OperatorManager {
    private static final Pattern OBJECT = Pattern.compile("\\{([^}]*)}");
    private static final Pattern UUID_FIELD = Pattern.compile("\\\"uuid\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern NAME_FIELD = Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern LEVEL_FIELD = Pattern.compile("\\\"level\\\"\\s*:\\s*(\\d+)");
    private static final Pattern BYPASS_FIELD = Pattern.compile("\\\"bypassesPlayerLimit\\\"\\s*:\\s*(true|false)");
    private final Path path;
    private final Map<UUID, Entry> operators = new ConcurrentHashMap<>();

    public OperatorManager(Path path) throws IOException {
        this.path = path.toAbsolutePath().normalize();
        load();
    }

    public boolean isOperator(UUID uuid) { return uuid != null && operators.containsKey(uuid); }

    public synchronized void add(ServerPlayer player) throws IOException {
        operators.put(player.getUuid(), new Entry(player.getUuid(), player.getUsername(), 4, false));
        save();
    }

    public synchronized void remove(ServerPlayer player) throws IOException {
        operators.remove(player.getUuid());
        save();
    }

    private void load() throws IOException {
        if (!Files.exists(path)) {
            save();
            return;
        }
        String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        Matcher objects = OBJECT.matcher(json);
        while (objects.find()) {
            String object = objects.group(1);
            Matcher uuidField = UUID_FIELD.matcher(object);
            Matcher nameField = NAME_FIELD.matcher(object);
            Matcher levelField = LEVEL_FIELD.matcher(object);
            Matcher bypassField = BYPASS_FIELD.matcher(object);
            if (!uuidField.find() || !nameField.find()) continue;
            try {
                UUID uuid = UUID.fromString(uuidField.group(1));
                int level = levelField.find() ? Integer.parseInt(levelField.group(1)) : 4;
                boolean bypass = bypassField.find() && Boolean.parseBoolean(bypassField.group(1));
                operators.put(uuid, new Entry(uuid, nameField.group(1), level, bypass));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed individual entries instead of discarding valid operators.
            }
        }
    }

    private void save() throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        StringBuilder json = new StringBuilder("[\n");
        int index = 0;
        for (Entry entry : operators.values()) {
            if (index++ > 0) json.append(",\n");
            json.append("  {\"uuid\":\"").append(entry.uuid).append("\",\"name\":\"")
                    .append(entry.name).append("\",\"level\":").append(entry.level)
                    .append(",\"bypassesPlayerLimit\":").append(entry.bypassesPlayerLimit).append('}');
        }
        json.append("\n]\n");
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(temporary, json.toString().getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static final class Entry {
        final UUID uuid; final String name; final int level; final boolean bypassesPlayerLimit;
        Entry(UUID uuid, String name, int level, boolean bypassesPlayerLimit) {
            this.uuid=uuid; this.name=name; this.level=level; this.bypassesPlayerLimit=bypassesPlayerLimit;
        }
    }
}
