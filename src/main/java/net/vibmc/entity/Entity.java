package net.vibmc.entity;

import net.vibmc.world.World;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class Entity {
    private static final AtomicInteger ENTITY_ID_COUNTER = new AtomicInteger(1);

    protected final int entityId;
    protected UUID uuid;
    protected volatile World world;
    protected volatile double x, y, z;
    protected volatile float yaw, pitch;
    protected volatile float health;
    protected volatile float maxHealth;
    protected volatile boolean alive;
    protected volatile boolean onGround;
    protected volatile boolean invulnerable;

    public Entity(World world) {
        this(world, UUID.randomUUID());
    }

    protected Entity(World world, UUID uuid) {
        this.entityId = ENTITY_ID_COUNTER.getAndIncrement();
        this.uuid = uuid;
        this.world = world;
        this.alive = true;
        this.health = 20.0f;
        this.maxHealth = 20.0f;
        this.onGround = false;
        this.invulnerable = false;
    }

    public abstract void tick();

    public int getEntityId() { return entityId; }
    public UUID getUuid() { return uuid; }

    public World getWorld() { return world; }
    public void setWorld(World world) { this.world = world; }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }

    public void setPosition(double x, double y, double z) {
        validatePosition(x, y, z);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void setRotation(float yaw, float pitch) {
        validateRotation(yaw, pitch);
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public void setPositionAndRotation(double x, double y, double z, float yaw, float pitch) {
        validatePosition(x, y, z);
        validateRotation(yaw, pitch);
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    private static void validatePosition(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || Math.abs(y) > 30_000_000) {
            throw new IllegalArgumentException("Invalid entity position");
        }
    }

    private static void validateRotation(float yaw, float pitch) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Invalid entity rotation");
        }
    }

    public float getHealth() { return health; }
    public float getMaxHealth() { return maxHealth; }
    public void setHealth(float health) {
        if (!Float.isFinite(health)) {
            throw new IllegalArgumentException("health must be finite");
        }
        this.health = Math.max(0, Math.min(maxHealth, health));
    }

    public void setMaxHealth(float maxHealth) {
        if (!Float.isFinite(maxHealth) || maxHealth <= 0) {
            throw new IllegalArgumentException("maxHealth must be positive and finite");
        }
        this.maxHealth = maxHealth;
        this.health = Math.min(health, maxHealth);
    }

    public void damage(float amount) {
        if (!Float.isFinite(amount) || amount <= 0 || invulnerable || !alive) return;
        health -= amount;
        if (health <= 0) {
            health = 0;
            die();
        }
    }

    public void heal(float amount) {
        if (!Float.isFinite(amount) || amount <= 0 || !alive) return;
        health = Math.min(maxHealth, health + amount);
    }

    public boolean isAlive() { return alive; }
    public boolean isOnGround() { return onGround; }
    public void setOnGround(boolean onGround) { this.onGround = onGround; }
    public boolean isInvulnerable() { return invulnerable; }
    public void setInvulnerable(boolean inv) { this.invulnerable = inv; }

    public void die() {
        if (!alive) return;
        alive = false;
        onDeath();
    }

    protected void onDeath() {
        // Override in subclasses
    }

    protected void revive() {
        alive = true;
    }

    public void remove() {
        world.removeEntity(this);
    }

    public boolean isPlayer() { return false; }
}
