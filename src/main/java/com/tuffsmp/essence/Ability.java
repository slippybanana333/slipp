package com.tuffsmp.essence;

/** Level 2 upgrades: active abilities, triggered by keybind (or /ability). */
public enum Ability {
	DASH("dash", "Dash", 160),
	LEAP("leap", "Leap", 200),
	SLAM("slam", "Slam", 280),
	BLINK("blink", "Blink", 240),
	OVERCHARGE("overcharge", "Overcharge", 900);

	public final String id;
	public final String displayName;
	public final int baseCooldownTicks;

	Ability(String id, String displayName, int baseCooldownTicks) {
		this.id = id;
		this.displayName = displayName;
		this.baseCooldownTicks = baseCooldownTicks;
	}

	/** Each ability level past the first trims 12% off the cooldown. */
	public int cooldownTicks(int level) {
		return (int) (baseCooldownTicks * (1.0 - 0.12 * (Math.max(1, level) - 1)));
	}

	public static Ability byId(String id) {
		if (id == null) return null;
		for (Ability a : values()) if (a.id.equals(id)) return a;
		return null;
	}
}
