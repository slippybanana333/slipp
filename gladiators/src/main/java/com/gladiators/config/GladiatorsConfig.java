package com.gladiators.config;

import java.util.ArrayList;
import java.util.List;

/** Mod settings plus the saved arena (center and spawn points). Serialized with Gson as config.json. */
public final class GladiatorsConfig {
	// ---- arena shape (used the next time you run /gladiators arena build) ----
	/** Radius of the flat sand floor. */
	public int arenaRadius = 40;
	/** Number of stone-brick seating steps around the floor. */
	public int seatingTiers = 6;
	/** Horizontal width of each seating step. */
	public int seatingTierWidth = 2;
	/** Thickness of the outer wall. */
	public int wallThickness = 2;
	/** How far the stone wall rises above the top seating step (barrier blocks continue above it). */
	public int wallExtraHeight = 4;
	/** Height of the invisible barrier ceiling above the floor. */
	public int ceilingHeight = 40;
	/** Cover pillars on the floor. 0 disables them. */
	public int pillarCount = 10;
	/** Spacing of sea-lantern floor lights (stops mob spawns at night). 0 disables them. */
	public int lightSpacing = 6;

	// ---- build performance ----
	/** Max blocks placed (or restored) per server tick while building/removing. */
	public int blocksPerTick = 2500;

	// ---- event behaviour ----
	/** Heal, feed and clear effects when a kit is given. */
	public boolean healOnKit = true;
	/** Discard hostile mobs that wander or spawn into the arena during an event. */
	public boolean removeHostileMobs = true;
	/** Seconds of fall-damage immunity after any event teleport. */
	public int fallGraceSeconds = 6;

	/** Written by /gladiators arena build; null when no arena is built. */
	public ArenaData arena = null;

	/** The built arena: center, geometry and spawn points. Spawn points may be hand-edited. */
	public static final class ArenaData {
		public String dimension = "minecraft:overworld";
		public int centerX;
		/** Y of the sand floor surface block. */
		public int floorY;
		public int centerZ;
		public int radius;
		/** Outer edge of the wall; also the "do not leave" radius. */
		public double outerRadius;
		/** Absolute Y of the barrier ceiling. */
		public int ceilingY;
		public List<SpawnPoint> spawnPoints = new ArrayList<>();
	}

	public static final class SpawnPoint {
		public double x;
		public double y;
		public double z;
		public float yaw;

		public SpawnPoint() {}

		public SpawnPoint(double x, double y, double z, float yaw) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.yaw = yaw;
		}
	}
}
