package com.tuffsmp.boss;

import com.tuffsmp.essence.Essence;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;

import java.util.*;

/**
 * Every boss in the mod. To add a boss: add an entry here, add its summon item (sigil/rune) to
 * tools/gen_assets.py and ModItems, then re-run the asset script.
 */
public final class BossRegistry {
	private static final Map<String, BossDef> ALL = new LinkedHashMap<>();
	public static final List<BossDef> TIER1 = new ArrayList<>();
	public static final List<BossDef> TIER2 = new ArrayList<>();
	public static final List<BossDef> TIER3 = new ArrayList<>();

	private BossRegistry() {}

	public static BossDef get(String id) {
		return ALL.get(id);
	}

	public static Collection<BossDef> all() {
		return ALL.values();
	}

	private static BossDef add(BossDef d) {
		ALL.put(d.id, d);
		switch (d.tier) {
			case ONE -> TIER1.add(d);
			case TWO -> TIER2.add(d);
			case THREE -> TIER3.add(d);
		}
		return d;
	}

	public static void init() {
		if (!ALL.isEmpty()) return;

		// =================================================================================
		// TIER 1 - self-summoned with a sigil, silent. ~solo-able. Drops Level 1 essence.
		// =================================================================================
		add(new BossDef("verdant", BossTier.ONE, "Mossback Brute", 0x55C455, () -> EntityType.ZOMBIE)
				.stats(170, 7, 4, 1.8f).bar(BossBarColor.GREEN).weapon(() -> Items.IRON_SWORD)
				.skill(BossSkill.SLAM, 1).skill(BossSkill.MINIONS, 1).minion(() -> EntityType.ZOMBIE)
				.interval(180).essence(Essence.VERDANT, 1, 2, 1).xp(250)
				.loot(() -> Items.IRON_INGOT, 4, 9, 1.0).loot(() -> Items.GOLD_INGOT, 2, 5, 0.5)
				.loot(() -> Items.DIAMOND, 1, 2, 0.25).loot(() -> Items.MOSS_BLOCK, 4, 8, 0.6));

		add(new BossDef("ember", BossTier.ONE, "Cinderfang", 0xFF6A2A, () -> EntityType.WITHER_SKELETON)
				.stats(160, 8, 6, 1.5f).bar(BossBarColor.RED).weapon(() -> Items.STONE_SWORD)
				.skill(BossSkill.VOLLEY, 1).skill(BossSkill.LEAP, 2).skill(BossSkill.SLAM, 3)
				.interval(150).essence(Essence.EMBER, 1, 2, 1).xp(250)
				.loot(() -> Items.IRON_INGOT, 4, 9, 1.0).loot(() -> Items.GOLD_INGOT, 2, 5, 0.5)
				.loot(() -> Items.DIAMOND, 1, 2, 0.25).loot(() -> Items.BLAZE_ROD, 2, 4, 0.6));

		add(new BossDef("frost", BossTier.ONE, "Rimebound Hulk", 0x8FD9FF, () -> EntityType.STRAY)
				.stats(150, 6, 4, 1.6f).bar(BossBarColor.BLUE)
				.skill(BossSkill.NOVA, 1).skill(BossSkill.MINIONS, 2).minion(() -> EntityType.STRAY)
				.interval(170).essence(Essence.FROST, 1, 2, 1).xp(250)
				.loot(() -> Items.IRON_INGOT, 4, 9, 1.0).loot(() -> Items.GOLD_INGOT, 2, 5, 0.5)
				.loot(() -> Items.DIAMOND, 1, 2, 0.25).loot(() -> Items.PACKED_ICE, 6, 12, 0.6));

		add(new BossDef("storm", BossTier.ONE, "Stormcaller", 0xB89CFF, () -> EntityType.VINDICATOR)
				.stats(175, 9, 4, 1.5f).bar(BossBarColor.PURPLE)
				.skill(BossSkill.LIGHTNING, 1).skill(BossSkill.LEAP, 2).skill(BossSkill.TELEPORT, 3)
				.interval(170).essence(Essence.STORM, 1, 2, 1).xp(250)
				.loot(() -> Items.IRON_INGOT, 4, 9, 1.0).loot(() -> Items.GOLD_INGOT, 2, 5, 0.5)
				.loot(() -> Items.DIAMOND, 1, 2, 0.25).loot(() -> Items.COPPER_INGOT, 8, 16, 0.6));

		add(new BossDef("tide", BossTier.ONE, "Tidewrecked Marauder", 0x2FA7C9, () -> EntityType.DROWNED)
				.stats(175, 7, 5, 1.7f).bar(BossBarColor.BLUE).weapon(() -> Items.IRON_AXE)
				.skill(BossSkill.PULL, 1).skill(BossSkill.SLAM, 2).skill(BossSkill.MINIONS, 3).minion(() -> EntityType.DROWNED)
				.interval(170).essence(Essence.TIDE, 1, 2, 1).xp(250)
				.loot(() -> Items.IRON_INGOT, 4, 9, 1.0).loot(() -> Items.GOLD_INGOT, 2, 5, 0.5)
				.loot(() -> Items.DIAMOND, 1, 2, 0.25).loot(() -> Items.PRISMARINE_SHARD, 6, 12, 0.6));

		add(new BossDef("gilded", BossTier.ONE, "Gilded Warlord", 0xFFD23F, () -> EntityType.PIGLIN_BRUTE)
				.stats(185, 10, 8, 1.6f).bar(BossBarColor.YELLOW)
				.skill(BossSkill.LEAP, 1).skill(BossSkill.SLAM, 1).skill(BossSkill.MINIONS, 2).minion(() -> EntityType.ZOMBIFIED_PIGLIN)
				.interval(160).essence(Essence.GILDED, 1, 2, 1).xp(250)
				.loot(() -> Items.IRON_INGOT, 4, 9, 1.0).loot(() -> Items.GOLD_INGOT, 6, 12, 0.8)
				.loot(() -> Items.DIAMOND, 1, 2, 0.25).loot(() -> Items.GOLDEN_APPLE, 1, 1, 0.3));

		// =================================================================================
		// TIER 2 - altar bosses, announced with a 15 minute head start. Drop Level 2 essence.
		// =================================================================================
		add(new BossDef("rift", BossTier.TWO, "Rift Stalker", 0xA020F0, () -> EntityType.ENDERMAN)
				.stats(380, 12, 8, 1.5f).bar(BossBarColor.PURPLE)
				.skill(BossSkill.TELEPORT, 1).skill(BossSkill.PULL, 1).skill(BossSkill.DARKNESS, 2)
				.skill(BossSkill.MINIONS, 2).minion(() -> EntityType.ENDERMITE).skill(BossSkill.SLAM, 3)
				.interval(130).essence(Essence.RIFT, 1, 3, 1).xp(900)
				.loot(() -> Items.DIAMOND, 3, 6, 1.0).loot(() -> Items.NETHERITE_SCRAP, 1, 2, 0.6)
				.loot(() -> Items.GOLDEN_APPLE, 1, 2, 0.5).loot(() -> Items.ENDER_PEARL, 4, 8, 0.8)
				.loot(() -> Items.ECHO_SHARD, 1, 1, 0.2));

		add(new BossDef("wraith", BossTier.TWO, "Wraith Knight", 0x6A6A9A, () -> EntityType.WITHER_SKELETON)
				.stats(420, 13, 10, 2.0f).bar(BossBarColor.WHITE).weapon(() -> Items.NETHERITE_SWORD)
				.skill(BossSkill.WITHER_BLAST, 1).skill(BossSkill.LEAP, 1).skill(BossSkill.VOLLEY, 2)
				.skill(BossSkill.MINIONS, 3).minion(() -> EntityType.SKELETON)
				.interval(130).essence(Essence.WRAITH, 1, 3, 1).xp(900)
				.loot(() -> Items.DIAMOND, 3, 6, 1.0).loot(() -> Items.NETHERITE_SCRAP, 1, 2, 0.6)
				.loot(() -> Items.GOLDEN_APPLE, 1, 2, 0.5).loot(() -> Items.BONE_BLOCK, 4, 8, 0.7)
				.loot(() -> Items.WITHER_ROSE, 1, 1, 0.3));

		add(new BossDef("titan", BossTier.TWO, "Ironclad Colossus", 0xD0D0D0, () -> EntityType.IRON_GOLEM)
				.stats(520, 16, 14, 2.2f).bar(BossBarColor.WHITE)
				.skill(BossSkill.SLAM, 1).skill(BossSkill.LEAP, 2).skill(BossSkill.PULL, 2)
				.interval(120).essence(Essence.TITAN, 1, 3, 1).xp(900)
				.loot(() -> Items.DIAMOND, 3, 6, 1.0).loot(() -> Items.NETHERITE_SCRAP, 1, 2, 0.6)
				.loot(() -> Items.GOLDEN_APPLE, 1, 2, 0.5).loot(() -> Items.IRON_BLOCK, 2, 5, 0.9)
				.loot(() -> Items.NETHERITE_INGOT, 1, 1, 0.15));

		add(new BossDef("gale", BossTier.TWO, "Skybreaker", 0x9BF0D0, () -> EntityType.EVOKER)
				.stats(360, 10, 6, 1.6f).bar(BossBarColor.GREEN)
				.skill(BossSkill.LIGHTNING, 1).skill(BossSkill.TELEPORT, 1).skill(BossSkill.MINIONS, 2)
				.minion(() -> EntityType.VEX).skill(BossSkill.NOVA, 3)
				.interval(125).essence(Essence.GALE, 1, 3, 1).xp(900)
				.loot(() -> Items.DIAMOND, 3, 6, 1.0).loot(() -> Items.NETHERITE_SCRAP, 1, 2, 0.6)
				.loot(() -> Items.GOLDEN_APPLE, 1, 2, 0.5).loot(() -> Items.BREEZE_ROD, 2, 5, 0.7)
				.loot(() -> Items.TOTEM_OF_UNDYING, 1, 1, 0.25));

		add(new BossDef("fury", BossTier.TWO, "Bloodhide Ravager", 0xD0141C, () -> EntityType.RAVAGER)
				.stats(450, 14, 10, 1.7f).bar(BossBarColor.RED)
				.skill(BossSkill.LEAP, 1).skill(BossSkill.SLAM, 1).skill(BossSkill.MINIONS, 2)
				.minion(() -> EntityType.VINDICATOR).skill(BossSkill.VOLLEY, 3)
				.interval(120).essence(Essence.FURY, 1, 3, 1).xp(900)
				.loot(() -> Items.DIAMOND, 3, 6, 1.0).loot(() -> Items.NETHERITE_SCRAP, 1, 2, 0.6)
				.loot(() -> Items.GOLDEN_APPLE, 1, 2, 0.5).loot(() -> Items.EMERALD, 8, 16, 0.8)
				.loot(() -> Items.TOTEM_OF_UNDYING, 1, 1, 0.25));

		// =================================================================================
		// TIER 3 - weekly world bosses. Drop multiples of every essence + one Magic Essence.
		// =================================================================================
		add(new BossDef("sculk_sovereign", BossTier.THREE, "The Sculk Sovereign", 0x1FB6C9, () -> EntityType.WARDEN)
				.stats(900, 20, 14, 1.4f).bar(BossBarColor.BLUE)
				.skill(BossSkill.SLAM, 1).skill(BossSkill.PULL, 1).skill(BossSkill.DARKNESS, 1)
				.skill(BossSkill.MINIONS, 2).minion(() -> EntityType.CAVE_SPIDER).skill(BossSkill.LIGHTNING, 3)
				.interval(110).allEssence(2, 4).xp(4000)
				.loot(() -> Items.NETHERITE_INGOT, 1, 3, 1.0).loot(() -> Items.DIAMOND_BLOCK, 1, 2, 1.0)
				.loot(() -> Items.ENCHANTED_GOLDEN_APPLE, 1, 2, 0.7).loot(() -> Items.TOTEM_OF_UNDYING, 1, 1, 0.5)
				.loot(() -> Items.ECHO_SHARD, 2, 4, 1.0).loot(() -> Items.NETHER_STAR, 1, 1, 0.5));

		add(new BossDef("ashen_overlord", BossTier.THREE, "Ashen Overlord", 0xFF4B1F, () -> EntityType.WITHER_SKELETON)
				.stats(1000, 18, 16, 3.2f).bar(BossBarColor.RED).weapon(() -> Items.NETHERITE_SWORD)
				.skill(BossSkill.VOLLEY, 1).skill(BossSkill.SLAM, 1).skill(BossSkill.WITHER_BLAST, 2)
				.skill(BossSkill.MINIONS, 2).minion(() -> EntityType.WITHER_SKELETON).skill(BossSkill.LEAP, 3)
				.interval(100).allEssence(2, 4).xp(4000)
				.loot(() -> Items.NETHERITE_INGOT, 1, 3, 1.0).loot(() -> Items.DIAMOND_BLOCK, 1, 2, 1.0)
				.loot(() -> Items.ENCHANTED_GOLDEN_APPLE, 1, 2, 0.7).loot(() -> Items.TOTEM_OF_UNDYING, 1, 1, 0.5)
				.loot(() -> Items.WITHER_SKELETON_SKULL, 1, 2, 0.6).loot(() -> Items.NETHER_STAR, 1, 1, 0.5));

		add(new BossDef("tempest_archmage", BossTier.THREE, "Tempest Archmage", 0x7B61FF, () -> EntityType.EVOKER)
				.stats(850, 14, 10, 2.4f).bar(BossBarColor.PURPLE)
				.skill(BossSkill.LIGHTNING, 1).skill(BossSkill.TELEPORT, 1).skill(BossSkill.NOVA, 2)
				.skill(BossSkill.MINIONS, 2).minion(() -> EntityType.VEX).skill(BossSkill.DARKNESS, 3)
				.interval(100).allEssence(2, 4).xp(4000)
				.loot(() -> Items.NETHERITE_INGOT, 1, 3, 1.0).loot(() -> Items.DIAMOND_BLOCK, 1, 2, 1.0)
				.loot(() -> Items.ENCHANTED_GOLDEN_APPLE, 1, 2, 0.7).loot(() -> Items.TOTEM_OF_UNDYING, 1, 2, 0.6)
				.loot(() -> Items.BREEZE_ROD, 4, 8, 1.0).loot(() -> Items.NETHER_STAR, 1, 1, 0.5));

		add(new BossDef("primordial_titan", BossTier.THREE, "Primordial Titan", 0xE8E8E8, () -> EntityType.IRON_GOLEM)
				.stats(1000, 22, 20, 3.4f).bar(BossBarColor.WHITE)
				.skill(BossSkill.SLAM, 1).skill(BossSkill.LEAP, 1).skill(BossSkill.PULL, 2)
				.skill(BossSkill.VOLLEY, 2).skill(BossSkill.NOVA, 3)
				.interval(100).allEssence(2, 4).xp(4000)
				.loot(() -> Items.NETHERITE_INGOT, 2, 4, 1.0).loot(() -> Items.DIAMOND_BLOCK, 1, 3, 1.0)
				.loot(() -> Items.ENCHANTED_GOLDEN_APPLE, 1, 2, 0.7).loot(() -> Items.TOTEM_OF_UNDYING, 1, 1, 0.5)
				.loot(() -> Items.IRON_BLOCK, 4, 8, 1.0).loot(() -> Items.NETHER_STAR, 1, 1, 0.5));
	}
}
