package com.gladiators.state;

import com.gladiators.GladiatorsMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** The one place event teleports happen, so every teleport resets fall distance and grants fall immunity. */
public final class Teleports {
	private Teleports() {}

	public static void to(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
		player.stopRiding();
		player.teleportTo(level, x, y, z, Set.of(), yaw, pitch, false);
		player.resetFallDistance();
		player.setDeltaMovement(Vec3.ZERO);
		player.hurtMarked = true;
		FallGrace.grant(player, GladiatorsMod.config().get().fallGraceSeconds);
	}
}
