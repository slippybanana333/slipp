package com.tuffsmp.boss;

import com.tuffsmp.essence.Essence;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Static description of a boss: what it is, how tough it is, what it can do, what it drops. */
public final class BossDef {
	public record SkillSlot(BossSkill skill, int minPhase) {}

	public final String id;
	public final BossTier tier;
	public final String name;
	public final int color;
	public final Supplier<EntityType<? extends Mob>> type;

	public double health = 200;
	/** Used for ability damage and the melee attribute. */
	public double damage = 8;
	public double armor = 4;
	public float scale = 1.5f;
	public double knockbackResistance = 0.8;
	public double speedMultiplier = 1.0;
	public BossEvent.BossBarColor barColor = BossEvent.BossBarColor.RED;
	public Supplier<Item> mainHand = null;

	public final List<SkillSlot> skills = new ArrayList<>();
	public Supplier<EntityType<? extends Mob>> minion = null;
	/** Ticks between skill casts at phase 1 (shortens in later phases). */
	public int skillInterval = 160;

	/** Level 1/2 bosses: their signature essence. Null for tier 3. */
	public Essence essence = null;
	public int essenceMin = 1, essenceMax = 2;
	/** Extra essence for the top damage dealer. */
	public int topBonus = 1;
	/** Tier 3: every essence type, this many each per participant. */
	public int allEssenceMin = 1, allEssenceMax = 3;
	public int xp = 300;
	public final List<LootEntry> loot = new ArrayList<>();

	public BossDef(String id, BossTier tier, String name, int color, Supplier<EntityType<? extends Mob>> type) {
		this.id = id;
		this.tier = tier;
		this.name = name;
		this.color = color;
		this.type = type;
	}

	public BossDef stats(double health, double damage, double armor, float scale) {
		this.health = health;
		this.damage = damage;
		this.armor = armor;
		this.scale = scale;
		return this;
	}

	public BossDef bar(BossEvent.BossBarColor c) {
		this.barColor = c;
		return this;
	}

	public BossDef weapon(Supplier<Item> item) {
		this.mainHand = item;
		return this;
	}

	public BossDef skill(BossSkill skill, int minPhase) {
		skills.add(new SkillSlot(skill, minPhase));
		return this;
	}

	public BossDef minion(Supplier<EntityType<? extends Mob>> type) {
		this.minion = type;
		return this;
	}

	public BossDef interval(int ticks) {
		this.skillInterval = ticks;
		return this;
	}

	public BossDef essence(Essence e, int min, int max, int topBonus) {
		this.essence = e;
		this.essenceMin = min;
		this.essenceMax = max;
		this.topBonus = topBonus;
		return this;
	}

	public BossDef allEssence(int min, int max) {
		this.allEssenceMin = min;
		this.allEssenceMax = max;
		return this;
	}

	public BossDef xp(int xp) {
		this.xp = xp;
		return this;
	}

	public BossDef loot(Supplier<Item> item, int min, int max, double chance) {
		loot.add(LootEntry.of(item, min, max, chance));
		return this;
	}

	public boolean hasSkill(BossSkill s) {
		for (SkillSlot slot : skills) if (slot.skill == s) return true;
		return false;
	}
}
