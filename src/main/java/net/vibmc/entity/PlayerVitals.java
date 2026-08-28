package net.vibmc.entity;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import net.vibmc.player.GameMode;
import net.vibmc.world.Blocks;
import net.vibmc.world.World;

/**
 * Everything that quietly wears a player down: air, fire, contact damage, hunger, and natural
 * regeneration. Before this existed the only ways to lose health were falling and the void, so
 * a player could stand in lava indefinitely.
 *
 * All periods are in ticks and follow 1.12.2: 300 ticks of air, damage every second while
 * drowning or burning, a hunger point per 4 exhaustion, and a heal per 4 seconds while fed.
 */
final class PlayerVitals {
    static final int MAX_AIR = 300;
    private static final int DROWN_PERIOD = 20;
    private static final int SUFFOCATE_PERIOD = 10;
    private static final int CONTACT_PERIOD = 10;
    private static final int BURN_PERIOD = 20;
    private static final int STARVE_PERIOD = 80;
    private static final int REGEN_PERIOD = 80;
    private static final float EXHAUSTION_PER_POINT = 4.0f;
    /** Half the player's width, for deciding which blocks they are touching. */
    private static final double HALF_WIDTH = 0.3;

    private final ServerPlayer player;
    private int airSupply = MAX_AIR;
    private int sentAirSupply = MAX_AIR;
    private int fireTicks;
    private float exhaustion;
    private int drownTimer, suffocateTimer, contactTimer, burnTimer, starveTimer, regenTimer;

    PlayerVitals(ServerPlayer player) {
        this.player = player;
    }

    int airSupply() { return Math.max(0, airSupply); }
    void setAirSupply(int value) { airSupply = Math.max(0, Math.min(MAX_AIR, value)); }
    float exhaustion() { return exhaustion; }
    void setExhaustion(float value) { exhaustion = Math.max(0.0f, value); }
    boolean isBurning() { return fireTicks > 0; }

    void reset() {
        airSupply = MAX_AIR;
        fireTicks = 0;
        exhaustion = 0.0f;
        drownTimer = suffocateTimer = contactTimer = burnTimer = starveTimer = regenTimer = 0;
    }

    void tick() {
        if (!player.isInWorld() || !player.isAlive()) return;
        GameMode mode = player.getGameModeEnum();
        boolean vulnerable = mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR;
        tickAir(vulnerable);
        if (!vulnerable) {
            if (fireTicks > 0) setOnFire(0);
            return;
        }
        tickSuffocation();
        tickContact();
        tickBurning();
        tickHunger();
        tickRegeneration();
    }

    /** Air drains while the head is submerged and refills the moment it is not. */
    private void tickAir(boolean vulnerable) {
        World world = player.getWorld();
        boolean submerged = Blocks.isWater(blockAt(world, player.getX(), player.getY() + 1.62, player.getZ()));
        if (submerged && vulnerable) {
            if (fireTicks > 0) setOnFire(0);
            if (airSupply > 0) {
                airSupply--;
            } else if (++drownTimer >= DROWN_PERIOD) {
                drownTimer = 0;
                player.hurt(2.0f, DamageSource.DROWNING);
            }
        } else {
            airSupply = MAX_AIR;
            drownTimer = 0;
        }
        if (airSupply != sentAirSupply) {
            sentAirSupply = airSupply;
            player.sendAirSupply(Math.max(0, airSupply));
        }
    }

    /** A solid block occupying the head is a wall the player is standing inside. */
    private void tickSuffocation() {
        World world = player.getWorld();
        if (!Blocks.isSolid(blockAt(world, player.getX(), player.getY() + 1.62, player.getZ()))) {
            suffocateTimer = 0;
            return;
        }
        if (++suffocateTimer >= SUFFOCATE_PERIOD) {
            suffocateTimer = 0;
            player.hurt(1.0f, DamageSource.SUFFOCATION);
        }
    }

    /** Lava, fire, and cactus all hurt on contact rather than over time. */
    private void tickContact() {
        World world = player.getWorld();
        DamageSource source = null;
        float amount = 0.0f;
        for (double dx = -HALF_WIDTH; dx <= HALF_WIDTH; dx += HALF_WIDTH * 2) {
            for (double dz = -HALF_WIDTH; dz <= HALF_WIDTH; dz += HALF_WIDTH * 2) {
                for (double dy = 0.0; dy <= 1.6; dy += 0.8) {
                    WrappedBlockState touched = blockAt(world, player.getX() + dx,
                            player.getY() + dy, player.getZ() + dz);
                    if (Blocks.isLava(touched)) { source = DamageSource.LAVA; amount = 4.0f; }
                    else if (Blocks.same(touched, Blocks.FIRE) && source != DamageSource.LAVA) {
                        source = DamageSource.FIRE; amount = 1.0f;
                    } else if (Blocks.same(touched, Blocks.CACTUS) && source == null) {
                        source = DamageSource.CACTUS; amount = 1.0f;
                    }
                }
            }
        }
        if (source == null) { contactTimer = 0; return; }
        if (source == DamageSource.LAVA) setOnFire(300);
        else if (source == DamageSource.FIRE) setOnFire(160);
        if (++contactTimer >= CONTACT_PERIOD || contactTimer == 1) {
            if (contactTimer >= CONTACT_PERIOD) contactTimer = 0;
            player.hurt(amount, source);
        }
    }

    private void tickBurning() {
        if (fireTicks <= 0) return;
        fireTicks--;
        if (fireTicks == 0) { setOnFire(0); burnTimer = 0; return; }
        if (++burnTimer >= BURN_PERIOD) {
            burnTimer = 0;
            player.hurt(1.0f, DamageSource.BURNING);
        }
    }

    void setOnFire(int ticks) {
        boolean wasBurning = fireTicks > 0;
        fireTicks = Math.max(0, ticks);
        if (wasBurning != (fireTicks > 0)) player.sendEntityFlags();
    }

    /** Exhaustion converts to saturation, then to food, then to starvation damage. */
    private void tickHunger() {
        while (exhaustion >= EXHAUSTION_PER_POINT) {
            exhaustion -= EXHAUSTION_PER_POINT;
            if (player.getFoodSaturation() > 0.0f) {
                player.setFoodSaturation(player.getFoodSaturation() - 1.0f);
            } else if (player.getFoodLevel() > 0) {
                player.setFoodLevel(player.getFoodLevel() - 1);
            }
            player.sendHealth();
        }
        if (player.getFoodLevel() > 0) { starveTimer = 0; return; }
        if (++starveTimer >= STARVE_PERIOD) {
            starveTimer = 0;
            player.hurt(1.0f, DamageSource.STARVATION);
        }
    }

    private void tickRegeneration() {
        if (player.getFoodLevel() < 18 || player.getHealth() >= player.getMaxHealth()) {
            regenTimer = 0;
            return;
        }
        if (++regenTimer < REGEN_PERIOD) return;
        regenTimer = 0;
        player.heal(1.0f);
        addExhaustion(3.0f);
        player.sendHealth();
    }

    void addExhaustion(float amount) {
        if (amount <= 0.0f) return;
        GameMode mode = player.getGameModeEnum();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return;
        exhaustion += amount;
    }

    private static WrappedBlockState blockAt(World world, double x, double y, double z) {
        return world.getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }
}
