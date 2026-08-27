package net.vibmc.permission;

import net.vibmc.entity.ServerPlayer;
import net.vibmc.world.World;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionManagerTest {
    private final ServerPlayer player = new ServerPlayer(
            new World(1L, "test"), null, "Player", UUID.randomUUID());

    @Test
    void deniesAdministrativePermissionsByDefault() {
        PermissionManager permissions = new PermissionManager();

        assertFalse(permissions.hasPermission(player, "vibmc.command.stop"));
        assertTrue(permissions.hasPermission(player, null));
    }

    @Test
    void supportsExactAndHierarchicalWildcardGrants() {
        PermissionManager permissions = new PermissionManager();
        permissions.grantPermission(player, "vibmc.command.*");

        assertTrue(permissions.hasPermission(player, "vibmc.command.gamemode"));
        assertFalse(permissions.hasPermission(player, "other.command"));

        permissions.revokePermission(player, "vibmc.command.*");
        assertFalse(permissions.hasPermission(player, "vibmc.command.gamemode"));
    }
}
