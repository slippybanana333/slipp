package com.gladiators.event;

import com.gladiators.GladiatorsMod;
import com.gladiators.state.FallGrace;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;

/** Hooks the Fabric events to the event manager. */
public final class EventListeners {
	private EventListeners() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (GladiatorsMod.arenas() != null) {
				GladiatorsMod.arenas().tick();
			}
			if (GladiatorsMod.events() != null) {
				GladiatorsMod.events().tick();
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (GladiatorsMod.events() != null) {
				GladiatorsMod.events().onJoin(handler.getPlayer());
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (GladiatorsMod.events() != null) {
				GladiatorsMod.events().onDisconnect(handler.getPlayer());
			}
		});

		// Returning false from ALLOW_DEATH cancels the death: no death screen, no respawn at world spawn.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
			!(entity instanceof ServerPlayer player) || GladiatorsMod.events() == null || GladiatorsMod.events().onAllowDeath(player, source));

		// No fall damage right after an event teleport.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof ServerPlayer player && source.is(DamageTypes.FALL) && FallGrace.active(player)));
	}
}
