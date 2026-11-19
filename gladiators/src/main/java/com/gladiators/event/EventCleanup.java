package com.gladiators.event;

import com.gladiators.GladiatorsMod;
import com.gladiators.arena.ArenaBuilder;
import com.gladiators.config.GladiatorsConfig.ArenaData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Tidies the arena when an event ends: kit blocks players placed, dropped loot, carts, arrows... */
final class EventCleanup {
	void run(ServerLevel level, GladiatorsEvent event) {
		removePlacedBlocks(level, event);
		removeEntities(level, event.arena);
	}

	private void removePlacedBlocks(ServerLevel level, GladiatorsEvent event) {
		var kitBlocks = GladiatorsMod.kits().kitBlocks();
		for (long packed : event.placedBlocks) {
			BlockPos pos = BlockPos.of(packed);
			if (kitBlocks.contains(level.getBlockState(pos).getBlock())) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), ArenaBuilder.FLAGS);
			}
		}
		event.placedBlocks.clear();
	}

	private void removeEntities(ServerLevel level, ArenaData arena) {
		double r = arena.outerRadius;
		AABB box = new AABB(arena.centerX - r, arena.floorY - 3, arena.centerZ - r, arena.centerX + r + 1, arena.ceilingY, arena.centerZ + r + 1);
		for (Entity entity : level.getEntities((Entity) null, box, EventCleanup::isArenaDebris)) {
			entity.discard();
		}
	}

	private static boolean isArenaDebris(Entity e) {
		return e instanceof ItemEntity || e instanceof AbstractMinecart || e instanceof Projectile
			|| e instanceof PrimedTnt || e instanceof ExperienceOrb || e instanceof Enemy;
	}
}
