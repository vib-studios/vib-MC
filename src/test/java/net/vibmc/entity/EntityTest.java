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
    void spawnIsNeverInWaterForAnyTestedSeed(){
        for(long seed:new long[]{3L,42L,91L,12345L}){
            World world=new World(seed,"wet-spawn-"+seed);
            ServerPlayer player=new ServerPlayer(world,null,"Player",UUID.randomUUID());
            player.spawnAtSpawn();
            int x=(int)Math.floor(player.getX()),y=(int)Math.floor(player.getY()),z=(int)Math.floor(player.getZ());
            assertFalse(player.isSubmerged(),"seed "+seed+" spawned the player in water");
            assertFalse(net.vibmc.world.Blocks.same(world.getBlockAt(x,y-1,z),net.vibmc.world.Blocks.WATER),
                    "seed "+seed+" spawned the player standing on water");
            assertTrue(y>world.getSeaLevel(),"seed "+seed+" spawned the player at or below the water line");
        }
    }

    @Test
    void aPlayerStrandedInWaterIsMovedOntoDryLand(){
        World world=new World(42L,"stranded");
        ServerPlayer player=new ServerPlayer(world,null,"Player",UUID.randomUUID());
        player.setPosition(-560.5,world.getSeaLevel(),-520.5);
        assertTrue(player.isSubmerged(),"test fixture must start the player under water");

        player.ensureSafePosition();

        assertFalse(player.isSubmerged(),"a submerged player must be relocated onto dry land");
    }

    @Test
    void creativeAndFlightCapablePlayersAreExemptFromTheFloatingCheck(){
        World world=new World(42L,"floating-check");
        ServerPlayer player=new ServerPlayer(world,null,"Player",UUID.randomUUID());
        player.spawnAtSpawn();
        for(int tick=0;tick<120;tick++)player.tick();
        assertFalse(player.isFloatingCheckExempt(),"a survival player without flight stays checked");

        player.setGameMode(net.vibmc.player.GameMode.CREATIVE);
        assertTrue(player.isFloatingCheckExempt(),"creative players are allowed to fly");

        player.setGameMode(net.vibmc.player.GameMode.SPECTATOR);
        assertTrue(player.isFloatingCheckExempt(),"spectators are allowed to fly");

        player.setGameMode(net.vibmc.player.GameMode.SURVIVAL);
        assertFalse(player.isFloatingCheckExempt(),"survival is checked again after leaving creative");
        player.setAllowFlight(true);
        assertTrue(player.isFloatingCheckExempt(),"a server-granted flight permission is honoured");
    }

    @Test
    void switchingToCreativeClearsAPendingFloatingFlag(){
        World world=new World(42L,"floating-flag");
        ServerPlayer player=new ServerPlayer(world,null,"Player",UUID.randomUUID());
        player.spawnAtSpawn();
        for(int tick=0;tick<120;tick++)player.tick();
        // Flag the player the way a survival client hovering above the ground would.
        player.setPosition(player.getX(),player.getY()+16,player.getZ());
        player.handleClientMovement(false);
        player.handleClientMovement(false);

        player.setGameMode(net.vibmc.player.GameMode.CREATIVE);
        for(int tick=0;tick<200;tick++)player.tick();

        assertTrue(player.isAlive(),"a creative player must survive the floating check");
        assertTrue(player.isFloatingCheckExempt(),"the creative exemption must persist across ticks");
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
