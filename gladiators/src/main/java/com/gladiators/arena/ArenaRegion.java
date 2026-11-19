package com.gladiators.arena;

import com.gladiators.config.GladiatorsConfig.ArenaData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Containment checks against the saved arena (a cylinder: wall radius, from below the floor to the ceiling). */
public final class ArenaRegion {
	private ArenaRegion() {}

	public static boolean sameDimension(ArenaData arena, Level level) {
		return arena.dimension.equals(level.dimension().identifier().toString());
	}

	public static boolean contains(ArenaData arena, Vec3 pos, double margin) {
		return containsXZ(arena, pos.x, pos.z, margin)
			&& pos.y >= arena.floorY - ArenaDesign.FOUNDATION - margin
			&& pos.y <= arena.ceilingY + 1 + margin;
	}

	public static boolean contains(ArenaData arena, BlockPos pos, double margin) {
		return contains(arena, new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), margin);
	}

	public static boolean containsXZ(ArenaData arena, double x, double z, double margin) {
		double dx = x - (arena.centerX + 0.5);
		double dz = z - (arena.centerZ + 0.5);
		double r = arena.outerRadius + margin;
		return dx * dx + dz * dz <= r * r;
	}
}
