package com.tuffsmp.essence;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

/**
 * Every kind of boss essence.
 *
 * Level 1: invest them to earn a permanent potion effect (thresholds = total essence invested).
 * Level 2: invest them to unlock / level an ability.
 * Level 3: Magic Essence; channel it and DM the owner for a custom legendary item.
 */
public enum Essence {
	// ---- Level 1: potion-effect perks ----
	VERDANT(1, "verdant", "Verdant", 0x55C455, MobEffects.REGENERATION, new int[]{5, 20}, null),
	EMBER(1, "ember", "Ember", 0xFF6A2A, MobEffects.FIRE_RESISTANCE, new int[]{3}, null),
	FROST(1, "frost", "Frost", 0x8FD9FF, MobEffects.RESISTANCE, new int[]{6, 24}, null),
	STORM(1, "storm", "Storm", 0xB89CFF, MobEffects.SPEED, new int[]{4, 16}, null),
	TIDE(1, "tide", "Tide", 0x2FA7C9, MobEffects.WATER_BREATHING, new int[]{3}, null),
	GILDED(1, "gilded", "Gilded", 0xFFD23F, MobEffects.HASTE, new int[]{5, 18}, null),

	// ---- Level 2: abilities (level thresholds: unlock, II, III) ----
	RIFT(2, "rift", "Rift", 0xA020F0, null, ABILITY_THRESHOLDS(), Ability.BLINK),
	WRAITH(2, "wraith", "Wraith", 0x6A6A9A, null, ABILITY_THRESHOLDS(), Ability.DASH),
	TITAN(2, "titan", "Titan", 0xD0D0D0, null, ABILITY_THRESHOLDS(), Ability.SLAM),
	GALE(2, "gale", "Gale", 0x9BF0D0, null, ABILITY_THRESHOLDS(), Ability.LEAP),
	FURY(2, "fury", "Fury", 0xD0141C, null, ABILITY_THRESHOLDS(), Ability.OVERCHARGE),

	// ---- Level 3 ----
	MAGIC(3, "magic", "Magic", 0xFF5CF0, null, new int[0], null);

	public final int level;
	public final String id;
	public final String name;
	public final int color;
	/** Level 1 only. */
	public final Holder<MobEffect> perkEffect;
	public final int[] thresholds;
	/** Level 2 only. */
	public final Ability ability;

	Essence(int level, String id, String name, int color, Holder<MobEffect> perkEffect, int[] thresholds, Ability ability) {
		this.level = level;
		this.id = id;
		this.name = name;
		this.color = color;
		this.perkEffect = perkEffect;
		this.thresholds = thresholds;
		this.ability = ability;
	}

	private static int[] ABILITY_THRESHOLDS() {
		return new int[]{3, 10, 25};
	}

	/** Registry path of the item, e.g. "verdant_essence". */
	public String itemId() {
		return id + "_essence";
	}

	public Component displayName() {
		return Component.literal(name + " Essence").withColor(color);
	}

	/** How many thresholds the given investment has passed (0 = nothing unlocked yet). */
	public int levelFor(int invested) {
		int lvl = 0;
		for (int t : thresholds) if (invested >= t) lvl++;
		return lvl;
	}

	public int maxLevel() {
		return thresholds.length;
	}

	public static Essence byId(String id) {
		if (id == null) return null;
		for (Essence e : values()) if (e.id.equals(id)) return e;
		return null;
	}

	public static Essence forAbility(Ability a) {
		for (Essence e : values()) if (e.ability == a) return e;
		return null;
	}
}
