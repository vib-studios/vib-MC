package net.vibmc.entity;

import net.vibmc.world.World;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityTest {
    @Test
    void respawnRevivesADeadPlayer() {
        ServerPlayer player = new ServerPlayer(new World(42L, "test"), null, "Player", UUID.randomUUID());
        player.die();
        assertFalse(player.isAlive());

        player.respawn();

        assertTrue(player.isAlive());
    }

    @Test
    void killSetsHealthToZeroAndMarksPlayerDead(){
        ServerPlayer player=new ServerPlayer(new World(42L,"test"),null,"Player",UUID.randomUUID());
        player.kill();
        assertFalse(player.isAlive());
        assertEquals(0.0f,player.getHealth());
    }

    @Test
    void spawnPointHasAirForBodyAndDrySolidFloor(){
        World world=new World(91L,"spawn-test");
        ServerPlayer player=new ServerPlayer(world,null,"Player",UUID.randomUUID());
        player.spawnAtSpawn();
        int x=(int)Math.floor(player.getX()),y=(int)Math.floor(player.getY()),z=(int)Math.floor(player.getZ());
        assertTrue(net.vibmc.world.Blocks.same(world.getBlockAt(x,y,z),net.vibmc.world.Blocks.AIR));
        assertTrue(net.vibmc.world.Blocks.same(world.getBlockAt(x,y+1,z),net.vibmc.world.Blocks.AIR));
        assertFalse(net.vibmc.world.Blocks.same(world.getBlockAt(x,y-1,z),net.vibmc.world.Blocks.WATER));
        assertFalse(net.vibmc.world.Blocks.same(world.getBlockAt(x,y-1,z),net.vibmc.world.Blocks.LAVA));
    }

    @Test
    void rejectsNonFiniteAndOutOfBoundsPositions() {
        ServerPlayer player = new ServerPlayer(new World(42L, "test"), null, "Player", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,
                () -> player.setPosition(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> player.setPosition(30_000_001, 0, 0));
    }
}
