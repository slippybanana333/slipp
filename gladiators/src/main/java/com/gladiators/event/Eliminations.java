package com.gladiators.event;

import com.gladiators.arena.ArenaRegion;
import com.gladiators.state.PlayerSnapshot;
import com.gladiators.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Deaths inside an event. A dying fighter never really dies (no death screen, no respawn at world spawn):
 * their items drop where they fell and they become a spectator in the arena. Also decides the winner.
 */
final class Eliminations {
	private final EventManager manager;

	Eliminations(EventManager manager) {
		this.manager = manager;
	}

	/** Returns false to cancel the real death. */
	boolean handleDeath(ServerPlayer player, DamageSource source) {
		GladiatorsEvent event = manager.current();
		Participant part = event == null ? null : event.participants.get(player.getUUID());
		if (part == null) {
			return true; // not part of the event: vanilla death
		}
		if (part.aliveFighter()) {
			eliminate(event, part, player);
		} else {
			player.setHealth(player.getMaxHealth()); // spectators cannot die either (e.g. /kill)
		}
		return false;
	}

	private void eliminate(GladiatorsEvent event, Participant part, ServerPlayer player) {
		part.eliminated = true;
		Component deathMessage = player.getCombatTracker().getDeathMessage();
		manager.server().getPlayerList().broadcastSystemMessage(deathMessage, false);

		player.getInventory().dropAll(); // loot stays in the arena for the survivors
		PlayerSnapshot.heal(player);
		player.setGameMode(GameType.SPECTATOR);
		if (!ArenaRegion.contains(event.arena, player.position(), 0.0)) {
			Vec3 spot = event.spectatorSpot();
			player.teleportTo(spot.x, spot.y, spot.z);
		}
		player.sendSystemMessage(Msg.info("You were eliminated. You are now spectating."));
		event.winnerCheckPending = true;
	}

	/** Runs every tick; announces the winner once all deaths of the previous tick have been counted. */
	void tick() {
		GladiatorsEvent event = manager.current();
		if (event == null || !event.winnerCheckPending) {
			return;
		}
		event.winnerCheckPending = false;
		if (event.winnerAnnounced || event.startingFighters < 2) {
			return;
		}
		List<Participant> alive = event.participants.values().stream().filter(Participant::aliveFighter).toList();
		if (alive.size() == 1) {
			event.winnerAnnounced = true;
			manager.server().getPlayerList().broadcastSystemMessage(
				Msg.ok("Winner: " + alive.get(0).name + " is the last gladiator standing!"), false);
		} else if (alive.isEmpty()) {
			event.winnerAnnounced = true;
			manager.server().getPlayerList().broadcastSystemMessage(Msg.info("Nobody survived. There is no winner."), false);
		}
	}
}
