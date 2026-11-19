package com.tuffsmp.boss;

import com.tuffsmp.Config;

public enum BossTier {
	/** Self-summoned with a sigil. No announcement. Drops Level 1 essence. */
	ONE(1, "Tier 1", 48, 32, 0.08),
	/** Summoned at an altar. Announced, then spawns after a countdown. Drops Level 2 essence. */
	TWO(2, "Tier 2", 64, 48, 0.04),
	/** Weekly world boss. Drops multiples of every essence plus a Magic Essence. */
	THREE(3, "Tier 3", 96, 64, 0.02);

	public final int number;
	public final String label;
	/** Boss is pulled back to its spawn point if it strays further than this. */
	public final int leashRadius;
	/** How far away the boss will notice players. */
	public final int targetRange;
	/** Minimum share of total damage dealt to qualify for rewards. */
	public final double minDamageShare;

	BossTier(int number, String label, int leashRadius, int targetRange, double minDamageShare) {
		this.number = number;
		this.label = label;
		this.leashRadius = leashRadius;
		this.targetRange = targetRange;
		this.minDamageShare = minDamageShare;
	}

	public double coopBonus() {
		return switch (this) {
			case ONE -> Config.INSTANCE.coopBonusTier1;
			case TWO -> Config.INSTANCE.coopBonusTier2;
			case THREE -> Config.INSTANCE.coopBonusTier3;
		};
	}
}
