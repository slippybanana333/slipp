package com.gladiators.arena;

import com.gladiators.config.GladiatorsConfig;
import com.gladiators.config.GladiatorsConfig.ArenaData;
import com.gladiators.config.GladiatorsConfig.SpawnPoint;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure geometry of the colosseum, no world access. Coordinates are relative to the arena center column.
 * Layout from the middle outwards: flat sand floor, tiered stone-brick seating, outer wall. Above the wall an
 * invisible barrier column and a barrier ceiling keep everybody (and elytra flyers, pearls...) inside.
 */
public final class ArenaDesign {
	/** Solid layers placed under the floor/seating so nothing can fall through or collapse. */
	public static final int FOUNDATION = 3;

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState SAND = Blocks.SAND.defaultBlockState();
	private static final BlockState SANDSTONE = Blocks.SANDSTONE.defaultBlockState();
	private static final BlockState BRICKS = Blocks.STONE_BRICKS.defaultBlockState();
	private static final BlockState MOSSY = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
	private static final BlockState CRACKED = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
	private static final BlockState CHISELED = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
	private static final BlockState LANTERN = Blocks.SEA_LANTERN.defaultBlockState();
	private static final BlockState BARRIER = Blocks.BARRIER.defaultBlockState();

	public final int centerX;
	public final int centerZ;
	/** Y of the sand surface. */
	public final int floorY;
	public final int radius;
	public final int ceilingHeight;

	private final int tiers;
	private final int tierWidth;
	private final int wallTop;
	private final int lightSpacing;
	private final double floorEdge;
	private final double seatingEdge;
	private final double outerRadius;
	/** Packed (dx,dz) -> pillar height, for every column a pillar covers. */
	private final Map<Long, Integer> pillarColumns = new HashMap<>();

	public ArenaDesign(GladiatorsConfig cfg, int centerX, int floorY, int centerZ) {
		this.centerX = centerX;
		this.centerZ = centerZ;
		this.floorY = floorY;
		this.radius = cfg.arenaRadius;
		this.tiers = cfg.seatingTiers;
		this.tierWidth = cfg.seatingTierWidth;
		this.ceilingHeight = cfg.ceilingHeight;
		this.lightSpacing = cfg.lightSpacing;
		this.wallTop = Math.max(3, tiers + cfg.wallExtraHeight);
		this.floorEdge = radius + 0.5;
		this.seatingEdge = floorEdge + (double) tiers * tierWidth;
		this.outerRadius = seatingEdge + cfg.wallThickness;
		placePillars(cfg.pillarCount);
	}

	/** Number of block layers per column that this design touches (foundation + air + ceiling). */
	public int columnHeight() {
		return FOUNDATION + ceilingHeight + 1;
	}

	public int outerReach() {
		return (int) Math.ceil(outerRadius);
	}

	public int ceilingY() {
		return floorY + ceilingHeight;
	}

	public int lowestY() {
		return floorY - FOUNDATION;
	}

	/**
	 * Fills {@code out} (length {@link #columnHeight()}, index 0 = lowest layer) with the target block for every
	 * layer of the column at (dx, dz). Returns false if the column is outside the arena footprint.
	 */
	public boolean column(int dx, int dz, BlockState[] out) {
		double d = Math.sqrt((double) dx * dx + (double) dz * dz);
		if (d > outerRadius) {
			return false;
		}
		boolean isFloor = d <= floorEdge;
		boolean isWall = d > seatingEdge;
		int top;
		BlockState topState;
		BlockState body;
		if (isFloor) {
			body = SANDSTONE;
			Integer pillar = pillarColumns.get(pack(dx, dz));
			if (pillar != null) {
				top = pillar;
				topState = top == 0 ? BRICKS : CHISELED;
				body = BRICKS;
			} else {
				top = 0;
				topState = isLantern(dx, dz, d) ? LANTERN : SAND;
			}
		} else if (!isWall) {
			int tier = (int) ((d - floorEdge) / tierWidth);
			top = tier + 1;
			body = BRICKS;
			topState = weathered(dx, dz);
		} else {
			top = wallTop;
			body = BRICKS;
			topState = weathered(dx, dz);
		}
		for (int i = 0; i < out.length; i++) {
			int dy = i - FOUNDATION;
			if (dy == ceilingHeight) {
				out[i] = BARRIER;
			} else if (dy > top) {
				out[i] = isWall ? BARRIER : AIR;
			} else if (dy == top) {
				out[i] = topState;
			} else {
				out[i] = body;
			}
		}
		return true;
	}

	/** Spawn points on an outer and an inner ring, sorted by angle so evenly spaced picks spread players out. */
	public List<SpawnPoint> spawnPoints() {
		int outerCount = Math.max(8, Math.min(32, radius));
		int innerCount = outerCount / 2;
		List<double[]> raw = new ArrayList<>(); // {angle, ringRadius}
		for (int i = 0; i < outerCount; i++) {
			raw.add(new double[] {2 * Math.PI * i / outerCount, radius * 0.8});
		}
		for (int i = 0; i < innerCount; i++) {
			raw.add(new double[] {2 * Math.PI * (i + 0.5) / innerCount, radius * 0.45});
		}
		raw.sort((a, b) -> Double.compare(a[0], b[0]));
		List<SpawnPoint> points = new ArrayList<>();
		for (double[] p : raw) {
			double x = centerX + 0.5 + Math.cos(p[0]) * p[1];
			double z = centerZ + 0.5 + Math.sin(p[0]) * p[1];
			// Face the middle of the arena (yaw 0 = south/+Z).
			float yaw = (float) (Mth.atan2(-(centerX + 0.5 - x), centerZ + 0.5 - z) * 180.0 / Math.PI);
			points.add(new SpawnPoint(x, floorY + 1, z, yaw));
		}
		return points;
	}

	/** The saved form written to config.json. */
	public ArenaData toData(String dimension) {
		ArenaData data = new ArenaData();
		data.dimension = dimension;
		data.centerX = centerX;
		data.centerZ = centerZ;
		data.floorY = floorY;
		data.radius = radius;
		data.outerRadius = outerRadius;
		data.ceilingY = ceilingY();
		data.spawnPoints = spawnPoints();
		return data;
	}

	private void placePillars(int count) {
		for (int k = 0; k < count; k++) {
			double angle = 2 * Math.PI * (k + 0.5) / count;
			int px = (int) Math.round(Math.cos(angle) * radius * 0.62);
			int pz = (int) Math.round(Math.sin(angle) * radius * 0.62);
			int height = 4 + k % 3;
			for (int ox = 0; ox < 2; ox++) {
				for (int oz = 0; oz < 2; oz++) {
					pillarColumns.put(pack(px + ox, pz + oz), height);
				}
			}
		}
	}

	private boolean isLantern(int dx, int dz, double d) {
		return lightSpacing > 0 && d <= radius - 1
			&& Math.floorMod(dx, lightSpacing) == 0 && Math.floorMod(dz, lightSpacing) == 0;
	}

	/** Cheap deterministic variation so the brickwork does not look uniform. */
	private static BlockState weathered(int dx, int dz) {
		int h = Math.floorMod(dx * 31 + dz * 17 + dx * dz, 10);
		return h == 0 ? MOSSY : h == 1 ? CRACKED : BRICKS;
	}

	private static long pack(int dx, int dz) {
		return ((long) dx << 32) ^ (dz & 0xFFFFFFFFL);
	}
}
