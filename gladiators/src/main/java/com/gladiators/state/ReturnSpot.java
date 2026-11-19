package com.gladiators.state;

import com.gladiators.GladiatorsMod;
import com.gladiators.arena.ArenaRegion;
import com.gladiators.config.GladiatorsConfig.ArenaData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Makes sure a restored player does not end up inside solid blocks. If their old spot is now inside the arena
 * (the op built it on top of them), they are placed just outside the arena wall instead.
 */
public final class ReturnSpot {
	private ReturnSpot() {}

	public static Vec3 resolve(ServerLevel level, Vec3 saved) {
		ArenaData arena = GladiatorsMod.config().get().arena;
		if (arena != null && ArenaRegion.sameDimension(arena, level) && ArenaRegion.contains(arena, saved, 1.0)) {
			return outsideArena(level, arena, saved);
		}
		return firstFreeSpotAbove(level, saved);
	}

	private static Vec3 outsideArena(ServerLevel level, ArenaData arena, Vec3 saved) {
		double dx = saved.x - (arena.centerX + 0.5);
		double dz = saved.z - (arena.centerZ + 0.5);
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0E-3) {
			dx = 1.0;
			dz = 0.0;
			len = 1.0;
		}
		double r = arena.outerRadius + 3.0;
		double x = arena.centerX + 0.5 + dx / len * r;
		double z = arena.centerZ + 0.5 + dz / len * r;
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
		return new Vec3(x, y, z);
	}

	private static Vec3 firstFreeSpotAbove(ServerLevel level, Vec3 saved) {
		EntityDimensions size = EntityType.PLAYER.getDimensions();
		int top = level.getMaxY();
		for (int up = 0; up <= 64 && saved.y + up < top; up++) {
			Vec3 candidate = new Vec3(saved.x, saved.y + up, saved.z);
			if (level.noCollision(size.makeBoundingBox(candidate))) {
				return candidate;
			}
		}
		return saved;
	}
}
