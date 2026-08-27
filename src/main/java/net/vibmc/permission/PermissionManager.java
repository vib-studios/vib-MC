package net.vibmc.permission;

import net.vibmc.entity.ServerPlayer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory, deny-by-default player permissions. Console checks are handled by CommandManager. */
public final class PermissionManager {
    private final ConcurrentHashMap<UUID, Set<String>> permissions = new ConcurrentHashMap<>();

    public boolean hasPermission(ServerPlayer player, String permission) {
        if (permission == null || permission.trim().isEmpty()) {
            return true;
        }
        if (player == null) {
            return false;
        }
        Set<String> grants = permissions.get(player.getUuid());
        if (grants == null || grants.isEmpty()) {
            return false;
        }

        String normalized = normalize(permission);
        if (grants.contains("*") || grants.contains(normalized)) {
            return true;
        }
        int separator = normalized.length();
        while ((separator = normalized.lastIndexOf('.', separator - 1)) >= 0) {
            if (grants.contains(normalized.substring(0, separator + 1) + "*")) {
                return true;
            }
        }
        return false;
    }

    public void grantPermission(ServerPlayer player, String permission) {
        String normalized = requirePermission(permission);
        permissions.computeIfAbsent(player.getUuid(), ignored -> ConcurrentHashMap.newKeySet())
                .add(normalized);
    }

    public void revokePermission(ServerPlayer player, String permission) {
        if (permission == null) {
            return;
        }
        Set<String> grants = permissions.get(player.getUuid());
        if (grants != null) {
            grants.remove(normalize(permission));
            if (grants.isEmpty()) {
                permissions.remove(player.getUuid(), grants);
            }
        }
    }

    public void setPermissions(ServerPlayer player, Set<String> granted) {
        if (granted == null || granted.isEmpty()) {
            permissions.remove(player.getUuid());
            return;
        }
        Set<String> normalized = ConcurrentHashMap.newKeySet();
        for (String permission : granted) {
            normalized.add(requirePermission(permission));
        }
        permissions.put(player.getUuid(), normalized);
    }

    public Set<String> getPermissions(ServerPlayer player) {
        Set<String> grants = permissions.get(player.getUuid());
        if (grants == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new HashSet<>(grants));
    }

    private static String requirePermission(String permission) {
        if (permission == null || permission.trim().isEmpty()) {
            throw new IllegalArgumentException("permission cannot be blank");
        }
        return normalize(permission);
    }

    private static String normalize(String permission) {
        return permission.trim().toLowerCase(Locale.ROOT);
    }
}
