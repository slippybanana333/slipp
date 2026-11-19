package com.gladiators.state;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Short fall-damage immunity after an event teleport. */
public final class FallGrace {
	private static final Map<UUID, Long> UNTIL = new HashMap<>();

	private FallGrace() {}

	public static void grant(ServerPlayer player, int seconds) {
		if (seconds > 0) {
			UNTIL.put(player.getUUID(), player.level().getServer().getTickCount() + seconds * 20L);
		}
	}

	public static boolean active(ServerPlayer player) {
		Long until = UNTIL.get(player.getUUID());
		if (until == null) {
			return false;
		}
		if (player.level().getServer().getTickCount() > until) {
			UNTIL.remove(player.getUUID());
			return false;
		}
		return true;
	}

	public static void clear() {
		UNTIL.clear();
	}
}
