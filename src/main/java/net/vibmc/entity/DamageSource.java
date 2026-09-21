package net.vibmc.entity;

/**
 * Where a point of damage came from. Carries the death message and hurt sound name, and
 * whether armour and invulnerability frames apply - drowning and starvation ignore both, as
 * in vanilla. Sound names resolve through the vendored 1.12 mapping to protocol sound ids.
 */
public enum DamageSource {
    FALL("%s fell from a high place", "entity.player.big_fall", true, true),
    VOID("%s fell out of the world", null, false, false),
    DROWNING("%s drowned", "entity.player.hurt_drown", false, false),
    SUFFOCATION("%s suffocated in a wall", "entity.player.hurt", false, false),
    LAVA("%s tried to swim in lava", "entity.player.hurt_on_fire", true, true),
    FIRE("%s went up in flames", "entity.player.hurt_on_fire", true, true),
    BURNING("%s burned to death", "entity.player.hurt_on_fire", true, false),
    CACTUS("%s was pricked to death", "entity.player.hurt", true, true),
    STARVATION("%s starved to death", "entity.player.hurt", false, false),
    PLAYER("%s was slain by %s", "entity.player.hurt", true, true),
    GENERIC("%s died", "entity.player.hurt", true, true);

    private final String deathMessage;
    private final String hurtSound;
    private final boolean reducedByArmor;
    private final boolean usesInvulnerabilityFrames;

    DamageSource(String deathMessage, String hurtSound, boolean reducedByArmor,
                 boolean usesInvulnerabilityFrames) {
        this.deathMessage = deathMessage;
        this.hurtSound = hurtSound;
        this.reducedByArmor = reducedByArmor;
        this.usesInvulnerabilityFrames = usesInvulnerabilityFrames;
    }

    public String hurtSound() { return hurtSound; }
    public boolean reducedByArmor() { return reducedByArmor; }
    public boolean usesInvulnerabilityFrames() { return usesInvulnerabilityFrames; }

    public String deathMessage(String victim, String attacker) {
        return this == PLAYER
                ? String.format(deathMessage, victim, attacker == null ? "a player" : attacker)
                : String.format(deathMessage, victim);
    }
}