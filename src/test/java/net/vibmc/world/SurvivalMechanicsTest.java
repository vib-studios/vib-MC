package net.vibmc.world;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import net.vibmc.entity.DamageSource;
import net.vibmc.entity.ServerPlayer;
import net.vibmc.inventory.Armor;
import net.vibmc.inventory.Weapons;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drops, tool tiers, armour, weapons, and the damage pipeline. */
class SurvivalMechanicsTest {
    private static final Random RANDOM = new Random(1234L);

    private static ItemStack stack(com.github.retrooper.packetevents.protocol.item.type.ItemType type) {
        return ItemStack.builder().type(type).amount(1).version(ClientVersion.V_1_12_2).build();
    }

    private static ServerPlayer player() {
        ServerPlayer player = new ServerPlayer(new World(5L, "survival-test"), null, "Tester", UUID.randomUUID());
        // The default position is y=0, which is inside bedrock and would suffocate them.
        player.setPosition(8.5, 200.0, 8.5);
        return player;
    }

    @Test
    void stoneNeedsAPickaxeAndYieldsCobblestone() {
        assertTrue(BlockDrops.drop(Blocks.STONE, ItemStack.EMPTY, RANDOM).isEmpty(),
                "bare hands get nothing out of stone");
        ItemStack dropped = BlockDrops.drop(Blocks.STONE, stack(ItemTypes.WOODEN_PICKAXE), RANDOM);
        assertEquals(ItemTypes.COBBLESTONE, dropped.getType());
    }

    @Test
    void oresRespectTheirToolTier() {
        assertTrue(BlockDrops.drop(Blocks.DIAMOND_ORE, stack(ItemTypes.STONE_PICKAXE), RANDOM).isEmpty(),
                "a stone pickaxe cannot harvest diamond");
        assertEquals(ItemTypes.DIAMOND,
                BlockDrops.drop(Blocks.DIAMOND_ORE, stack(ItemTypes.IRON_PICKAXE), RANDOM).getType());
        assertEquals(ItemTypes.IRON_ORE,
                BlockDrops.drop(Blocks.IRON_ORE, stack(ItemTypes.STONE_PICKAXE), RANDOM).getType(),
                "iron ore drops itself, to be smelted");
        assertEquals(ItemTypes.COAL,
                BlockDrops.drop(Blocks.COAL_ORE, stack(ItemTypes.WOODEN_PICKAXE), RANDOM).getType());
    }

    @Test
    void grassDropsDirtAndDiggingNeedsNoTool() {
        assertEquals(ItemTypes.DIRT, BlockDrops.drop(Blocks.GRASS, ItemStack.EMPTY, RANDOM).getType());
    }

    @Test
    void redstoneYieldsSeveralDust() {
        ItemStack dropped = BlockDrops.drop(Blocks.REDSTONE_ORE, stack(ItemTypes.IRON_PICKAXE), RANDOM);
        assertEquals(ItemTypes.REDSTONE, dropped.getType());
        assertTrue(dropped.getAmount() >= 4 && dropped.getAmount() <= 5,
                "redstone ore drops 4-5 dust, got " + dropped.getAmount());
    }

    @Test
    void blocksThatDropThemselvesResolveTheirItem() {
        // Block-state names are not namespaced but item names are; the generic fallback used
        // to look up the bare name only, so every one of these silently dropped nothing.
        assertEquals(ItemTypes.SAND, BlockDrops.drop(Blocks.SAND, ItemStack.EMPTY, RANDOM).getType());
        assertEquals(ItemTypes.DIRT, BlockDrops.drop(Blocks.DIRT, ItemStack.EMPTY, RANDOM).getType());
        assertEquals(ItemTypes.OAK_LOG, BlockDrops.drop(Blocks.WOOD, ItemStack.EMPTY, RANDOM).getType());
        assertEquals(ItemTypes.OAK_PLANKS, BlockDrops.drop(Blocks.OAK_PLANKS, ItemStack.EMPTY, RANDOM).getType());
        assertEquals(ItemTypes.CHEST, BlockDrops.drop(Blocks.CHEST, ItemStack.EMPTY, RANDOM).getType());
        assertEquals(ItemTypes.CACTUS, BlockDrops.drop(Blocks.CACTUS, ItemStack.EMPTY, RANDOM).getType());
        assertEquals(ItemTypes.OBSIDIAN,
                BlockDrops.drop(Blocks.OBSIDIAN, stack(ItemTypes.DIAMOND_PICKAXE), RANDOM).getType());
    }

    @Test
    void containersAreRecognisedAfterPlacementRotatesThem() {
        // A placed chest carries a facing property, so an exact state comparison misses it
        // and the window never opens.
        com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState facing =
                Blocks.CHEST.clone();
        facing.setFacing(com.github.retrooper.packetevents.protocol.world.BlockFace.EAST);
        assertFalse(Blocks.same(facing, Blocks.CHEST), "a rotated chest is a different state");
        assertTrue(Blocks.isType(facing,
                com.github.retrooper.packetevents.protocol.world.states.type.StateTypes.CHEST),
                "but it is still a chest");
    }

    @Test
    void armorReducesDamageByFourPercentPerPoint() {
        assertEquals(2, Armor.points(stack(ItemTypes.IRON_HELMET)));
        assertEquals(8, Armor.points(stack(ItemTypes.DIAMOND_CHESTPLATE)));
        assertEquals(0, Armor.points(stack(ItemTypes.DIAMOND_SWORD)), "a sword is not armour");
        assertEquals(Armor.HELMET, Armor.slotFor(stack(ItemTypes.LEATHER_HELMET)));
        assertEquals(Armor.BOOTS, Armor.slotFor(stack(ItemTypes.IRON_BOOTS)));

        assertEquals(10.0f, Armor.reduce(10.0f, 0), 0.001f);
        assertEquals(8.0f, Armor.reduce(10.0f, 5), 0.001f);
        assertEquals(2.0f, Armor.reduce(10.0f, 20), 0.001f, "reduction caps at 80%");
    }

    @Test
    void weaponDamageFollowsTheMaterial() {
        assertEquals(1.0f, Weapons.attackDamage(ItemStack.EMPTY), 0.001f, "a fist does one heart");
        assertEquals(4.0f, Weapons.attackDamage(stack(ItemTypes.WOODEN_SWORD)), 0.001f);
        assertEquals(7.0f, Weapons.attackDamage(stack(ItemTypes.DIAMOND_SWORD)), 0.001f);
        assertTrue(Weapons.attackDamage(stack(ItemTypes.DIAMOND_SWORD))
                > Weapons.attackDamage(stack(ItemTypes.IRON_SWORD)));
    }

    @Test
    void armorSoaksDamageThroughTheHurtPipeline() {
        ServerPlayer unarmored = player();
        unarmored.hurt(10.0f, DamageSource.PLAYER, "Attacker");
        float withoutArmor = unarmored.getHealth();

        ServerPlayer armored = player();
        armored.setArmorPiece(Armor.CHESTPLATE, stack(ItemTypes.DIAMOND_CHESTPLATE));
        armored.hurt(10.0f, DamageSource.PLAYER, "Attacker");

        assertTrue(armored.getHealth() > withoutArmor,
                "a diamond chestplate must absorb some of the hit");
    }

    @Test
    void drowningIgnoresArmor() {
        ServerPlayer armored = player();
        armored.setArmorPiece(Armor.CHESTPLATE, stack(ItemTypes.DIAMOND_CHESTPLATE));
        armored.hurt(4.0f, DamageSource.DROWNING);
        assertEquals(16.0f, armored.getHealth(), 0.001f, "armour does not protect against drowning");
    }

    @Test
    void invulnerabilityFramesBlockRapidRepeatHits() {
        ServerPlayer target = player();
        assertTrue(target.hurt(3.0f, DamageSource.PLAYER, "A"), "the first hit lands");
        assertFalse(target.hurt(3.0f, DamageSource.PLAYER, "A"), "an immediate second hit is ignored");
        assertEquals(17.0f, target.getHealth(), 0.001f);

        // Starvation has no invulnerability window, so it always applies.
        assertTrue(target.hurt(1.0f, DamageSource.STARVATION));
        assertEquals(16.0f, target.getHealth(), 0.001f);
    }

    @Test
    void lethalDamageKillsAndDeathMessagesNameTheSource() {
        ServerPlayer victim = player();
        victim.hurt(100.0f, DamageSource.LAVA);
        assertFalse(victim.isAlive(), "damage past the last heart kills");
        assertEquals(0.0f, victim.getHealth(), 0.001f);

        assertEquals("Steve tried to swim in lava", DamageSource.LAVA.deathMessage("Steve", null));
        assertEquals("Steve was slain by Alex", DamageSource.PLAYER.deathMessage("Steve", "Alex"));
        assertEquals("Steve drowned", DamageSource.DROWNING.deathMessage("Steve", null));
    }

    @Test
    void hungerDrainsAndStarvationHurts() {
        ServerPlayer player = player();
        player.setFoodLevel(20);
        player.setFoodSaturation(0.0f);
        player.addExhaustion(4.0f);
        player.tick();
        assertEquals(19, player.getFoodLevel(), "four exhaustion costs one hunger point");

        player.setFoodLevel(0);
        for (int tick = 0; tick < 80; tick++) player.tick();
        assertTrue(player.getHealth() < 20.0f, "an empty hunger bar starves the player");
    }

    @Test
    void aFedPlayerRegeneratesHealth() {
        ServerPlayer player = player();
        player.hurt(6.0f, DamageSource.FALL);
        float hurt = player.getHealth();
        player.setFoodLevel(20);
        for (int tick = 0; tick < 81; tick++) player.tick();
        assertTrue(player.getHealth() > hurt, "a fed player heals over time");
    }

    @Test
    void creativePlayersTakeNoDamage() {
        ServerPlayer player = player();
        player.setInvulnerable(true);
        assertFalse(player.hurt(10.0f, DamageSource.LAVA), "an invulnerable player is not hurt");
        assertEquals(20.0f, player.getHealth(), 0.001f);
    }
}
