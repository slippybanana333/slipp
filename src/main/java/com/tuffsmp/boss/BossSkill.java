package com.tuffsmp.boss;

/** The moves a boss can use. Which ones a boss has, and from which phase, is set in {@link BossRegistry}. */
public enum BossSkill {
	/** Ground pound: telegraphed ring, then damage + knockback around the boss. */
	SLAM,
	/** Volley of fire bolts at the target. */
	VOLLEY,
	/** Summons minions (capped). */
	MINIONS,
	/** Leaps at the target and lands with a small shockwave. */
	LEAP,
	/** Freezing blast: damage, slowness and freezing around the boss. */
	NOVA,
	/** Marks players' positions, then drops lightning on them. */
	LIGHTNING,
	/** Drags nearby players toward the boss. */
	PULL,
	/** Teleports behind its target. */
	TELEPORT,
	/** Darkness + weakness pulse. */
	DARKNESS,
	/** Wither blast around the boss. */
	WITHER_BLAST
}
