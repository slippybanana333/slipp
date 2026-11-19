package com.gladiators.event;

import com.gladiators.GladiatorsMod;
import com.gladiators.arena.ArenaRegion;
import com.gladiators.config.GladiatorsConfig.ArenaData;
import com.gladiators.config.GladiatorsConfig.SpawnPoint;
import com.gladiators.state.Teleports;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Periodic checks during an event: keep everyone inside the arena and keep hostile mobs out. */
final class EventPatrol {
	private EventPatrol() {}

	static void tick(EventManager manager) {
		GladiatorsEvent event = manager.current();
		ServerLevel level = manager.arenaLevel();
		if (event == null || level == null) {
			return;
		}
		int tick = manager.server().getTickCount();
		if (tick % 10 == 0) {
			keepInside(manager, event, level);
		}
		if (tick % 20 == 0 && GladiatorsMod.config().get().removeHostileMobs) {
			removeHostiles(event.arena, level);
		}
	}

	/** Backup for the physical barrier: spectators can clip through blocks, and glitches happen. */
	private static void keepInside(EventManager manager, GladiatorsEvent event, ServerLevel level) {
		ArenaData arena = event.arena;
		for (Participant part : event.participants.values()) {
			ServerPlayer player = manager.server().getPlayerList().getPlayer(part.id);
			if (player == null) {
				continue;
			}
			boolean inArena = player.level() == level && ArenaRegion.contains(arena, player.position(), 0.5);
			if (inArena && player.getY() >= arena.floorY - 6) {
				continue;
			}
			Vec3 target;
			float yaw = player.getYRot();
			if (part.aliveFighter()) {
				SpawnPoint sp = arena.spawnPoints.get(player.getRandom().nextInt(arena.spawnPoints.size()));
				target = new Vec3(sp.x, sp.y, sp.z);
				yaw = sp.yaw;
			} else {
				target = event.spectatorSpot();
			}
			Teleports.to(player, level, target.x, target.y, target.z, yaw, 0.0F);
		}
	}

	private static void removeHostiles(ArenaData arena, ServerLevel level) {
		double r = arena.outerRadius;
		AABB box = new AABB(arena.centerX - r, arena.floorY - 3, arena.centerZ - r, arena.centerX + r + 1, arena.ceilingY, arena.centerZ + r + 1);
		for (var entity : level.getEntities((net.minecraft.world.entity.Entity) null, box, e -> e instanceof Enemy && !(e instanceof Player))) {
			entity.discard();
		}
	}
}
