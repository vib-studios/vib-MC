package net.vibmc.entity;

import com.github.retrooper.packetevents.protocol.sound.Sound;
import com.github.retrooper.packetevents.protocol.sound.Sounds;

/**
 * Where a point of damage came from. Carries the death message and hurt sound, and whether
 * armour and invulnerability frames apply - drowning and starvation ignore both, as in vanilla.
 */
public enum DamageSource {
    FALL("%s fell from a high place", Sounds.ENTITY_PLAYER_BIG_FALL, true, true),
    VOID("%s fell out of the world", null, false, false),
    DROWNING("%s drowned", Sounds.ENTITY_PLAYER_HURT_DROWN, false, false),
    SUFFOCATION("%s suffocated in a wall", Sounds.ENTITY_PLAYER_HURT, false, false),
    LAVA("%s tried to swim in lava", Sounds.ENTITY_PLAYER_HURT_ON_FIRE, true, true),
    FIRE("%s went up in flames", Sounds.ENTITY_PLAYER_HURT_ON_FIRE, true, true),
    BURNING("%s burned to death", Sounds.ENTITY_PLAYER_HURT_ON_FIRE, true, false),
    CACTUS("%s was pricked to death", Sounds.ENTITY_PLAYER_HURT, true, true),
    STARVATION("%s starved to death", Sounds.ENTITY_PLAYER_HURT, false, false),
    PLAYER("%s was slain by %s", Sounds.ENTITY_PLAYER_HURT, true, true),
    GENERIC("%s died", Sounds.ENTITY_PLAYER_HURT, true, true);

    private final String deathMessage;
    private final Sound hurtSound;
    private final boolean reducedByArmor;
    private final boolean usesInvulnerabilityFrames;

    DamageSource(String deathMessage, Sound hurtSound, boolean reducedByArmor,
                 boolean usesInvulnerabilityFrames) {
        this.deathMessage = deathMessage;
        this.hurtSound = hurtSound;
        this.reducedByArmor = reducedByArmor;
        this.usesInvulnerabilityFrames = usesInvulnerabilityFrames;
    }

    public Sound hurtSound() { return hurtSound; }
    public boolean reducedByArmor() { return reducedByArmor; }
    public boolean usesInvulnerabilityFrames() { return usesInvulnerabilityFrames; }

    public String deathMessage(String victim, String attacker) {
        return this == PLAYER
                ? String.format(deathMessage, victim, attacker == null ? "a player" : attacker)
                : String.format(deathMessage, victim);
    }
}
