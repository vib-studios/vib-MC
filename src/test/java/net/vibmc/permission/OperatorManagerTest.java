package net.vibmc.permission;

import net.vibmc.entity.ServerPlayer;
import net.vibmc.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorManagerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void operatorsRoundTripThroughVanillaShapedJson() throws Exception {
        Path path=temporaryDirectory.resolve("ops.json");
        ServerPlayer player=new ServerPlayer(new World(1L,"test"),null,"Operator",UUID.randomUUID());
        OperatorManager manager=new OperatorManager(path);
        manager.add(player);
        assertTrue(manager.isOperator(player.getUuid()));
        assertTrue(new OperatorManager(path).isOperator(player.getUuid()));
        manager.remove(player);
        assertFalse(manager.isOperator(player.getUuid()));
    }
}
