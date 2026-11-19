package com.tuffsmp.arena;

import com.tuffsmp.ModBlocks;
import com.tuffsmp.boss.BossTier;
import com.tuffsmp.util.Scheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A coded boss arena: a flat circular stone-brick platform with a low wall, cleared of terrain above it.
 *
 * All the look-and-feel numbers are the constants below, so tweaking an arena is a one-line change.
 * The layout is deterministic from (center, radius), which means "restoring" it is just building it again.
 */
public final class Arena {
	// ---- Tweakables ----
	public static final int RADIUS_TIER2 = 24;
	public static final int RADIUS_TIER3 = 40;
	/** How many blocks above the floor are cleared of terrain. */
	public static final int CLEAR_HEIGHT = 8;
	/** Height of the perimeter wall, in blocks above the floor. */
	public static final int WALL_HEIGHT = 2;
	/** How far down we fill gaps beneath the floor so it doesn't float over cliffs/caves. */
	public static final int SUPPORT_DEPTH = 6;
	/** Blocks changed per tick while building (keeps big arenas from lagging the server). */
	public static final int BLOCKS_PER_TICK = 6000;

	private static final BlockState FLOOR = Blocks.STONE_BRICKS.defaultBlockState();
	private static final BlockState FLOOR_RIM = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
	private static final BlockState WALL = Blocks.STONE_BRICKS.defaultBlockState();
	private static final BlockState WALL_CAP = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
	private static final BlockState SUPPORT = Blocks.STONE.defaultBlockState();
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	/** The block position of the arena's center; the floor sits one block below this. */
	public final BlockPos center;
	public final int radius;

	public Arena(BlockPos center, int radius) {
		this.center = center;
		this.radius = radius;
	}

	public static int radiusFor(BossTier tier) {
		return switch (tier) {
			case TWO -> RADIUS_TIER2;
			case THREE -> RADIUS_TIER3;
			default -> 0;
		};
	}

	/** True if the horizontal point is inside the fighting area (inside the wall). */
	public boolean containsXZ(double x, double z) {
		double dx = x - (center.getX() + 0.5), dz = z - (center.getZ() + 0.5);
		return dx * dx + dz * dz <= (double) radius * radius;
	}

	/** True for blocks that belong to the arena volume (used to block building during a fight). */
	public boolean containsBlock(BlockPos p) {
		if (p.getY() < center.getY() - 1 - SUPPORT_DEPTH || p.getY() > center.getY() + CLEAR_HEIGHT + 2) return false;
		double dx = p.getX() + 0.5 - (center.getX() + 0.5), dz = p.getZ() + 0.5 - (center.getZ() + 0.5);
		double r = radius + 1.5;
		return dx * dx + dz * dz <= r * r;
	}

	public Vec3 centerVec() {
		return Vec3.atBottomCenterOf(center);
	}

	/**
	 * Builds (or rebuilds) the arena over several ticks, then runs {@code onDone}. Safe to call repeatedly:
	 * it just re-places the same blocks. Chests and other block-entity blocks are never touched.
	 */
	public void build(ServerLevel level, Runnable onDone) {
		new Job(level, onDone).step();
	}

	private final class Job {
		final ServerLevel level;
		final Runnable onDone;
		int dx = -radius, dz = -radius;
		final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		Job(ServerLevel level, Runnable onDone) {
			this.level = level;
			this.onDone = onDone;
		}

		void step() {
			int budget = BLOCKS_PER_TICK;
			while (dx <= radius) {
				while (dz <= radius) {
					double d2 = (double) dx * dx + (double) dz * dz;
					if (d2 <= (double) radius * radius) {
						budget -= column(dx, dz, d2);
						if (budget <= 0) {
							dz++;
							Scheduler.later(1, this::step);
							return;
						}
					}
					dz++;
				}
				dz = -radius;
				dx++;
			}
			onDone.run();
		}

		boolean mayReplace(BlockState s) {
			return !s.hasBlockEntity() && !s.is(ModBlocks.BOSS_ALTAR) && !s.is(Blocks.BEDROCK);
		}

		void put(BlockPos p, BlockState state) {
			BlockState cur = level.getBlockState(p);
			if (cur == state || !mayReplace(cur)) return;
			level.setBlock(p, state, 2);
		}

		int column(int dx, int dz, double d2) {
			int x = center.getX() + dx, z = center.getZ() + dz;
			int floorY = center.getY() - 1;
			int work = 0;
			boolean rim = d2 > (radius - 1.5) * (radius - 1.5);
			boolean wall = d2 > (radius - 1.0) * (radius - 1.0);

			level.getChunk(x >> 4, z >> 4); // make sure it's loaded

			put(pos.set(x, floorY, z), rim ? FLOOR_RIM : FLOOR);
			work++;

			// support: fill gaps under the floor so it doesn't float
			for (int i = 1; i <= SUPPORT_DEPTH; i++) {
				pos.set(x, floorY - i, z);
				BlockState s = level.getBlockState(pos);
				if (s.isAir() || s.canBeReplaced() || !s.getFluidState().isEmpty()) {
					put(pos, SUPPORT);
					work++;
				} else {
					break;
				}
			}

			// clear terrain above; the outer ring gets the wall instead
			for (int y = 0; y < CLEAR_HEIGHT; y++) {
				pos.set(x, center.getY() + y, z);
				if (wall && y < WALL_HEIGHT) put(pos, y == WALL_HEIGHT - 1 ? WALL_CAP : WALL);
				else put(pos, AIR);
				work++;
			}
			return work;
		}
	}
}
