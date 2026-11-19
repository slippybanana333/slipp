package com.gladiators.event;

import com.gladiators.GladiatorsMod;
import com.gladiators.arena.ArenaManager;
import com.gladiators.config.GladiatorsConfig;
import com.gladiators.config.GladiatorsConfig.ArenaData;
import com.gladiators.config.GladiatorsConfig.SpawnPoint;
import com.gladiators.kit.KitDefinition;
import com.gladiators.kit.KitFactory;
import com.gladiators.state.PlayerSnapshot;
import com.gladiators.state.SnapshotService;
import com.gladiators.state.Teleports;
import com.gladiators.util.Msg;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Event lifecycle: start, end, kits, joins and disconnects. Deaths live in {@link Eliminations}. */
public final class EventManager {
	private final MinecraftServer server;
	private final ArenaManager arenas;
	private final SnapshotService snapshots;
	private final Eliminations eliminations = new Eliminations(this);
	private final EventCleanup cleanup = new EventCleanup();
	private GladiatorsEvent event;

	public EventManager(MinecraftServer server, ArenaManager arenas, SnapshotService snapshots) {
		this.server = server;
		this.arenas = arenas;
		this.snapshots = snapshots;
	}

	public MinecraftServer server() {
		return server;
	}

	public boolean isActive() {
		return event != null;
	}

	public GladiatorsEvent current() {
		return event;
	}

	public SnapshotService snapshots() {
		return snapshots;
	}

	public ServerLevel arenaLevel() {
		if (event == null) {
			return null;
		}
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(event.arena.dimension)));
	}

	// ---------------------------------------------------------------- start

	/** Returns an error message, or null on success. */
	public String start() {
		if (event != null) {
			return "An event is already running. Use /gladiators end first.";
		}
		GladiatorsConfig cfg = GladiatorsMod.config().get();
		ArenaData arena = cfg.arena;
		if (arenas.status() != ArenaManager.Status.BUILT || arena == null || arena.spawnPoints.isEmpty()) {
			return "No finished arena. Run /gladiators arena build first.";
		}
		ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(arena.dimension)));
		if (level == null) {
			return "The arena dimension " + arena.dimension + " is not loaded.";
		}
		List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
		if (players.isEmpty()) {
			return "Nobody is online.";
		}

		// Never overwrite a real saved state with a kit state: apply any leftover snapshot first.
		for (ServerPlayer player : players) {
			snapshots.restore(player);
		}
		// Everything is written to disk BEFORE anyone is touched, so a crash can never lose items.
		List<ServerPlayer> saved = new ArrayList<>();
		for (ServerPlayer player : players) {
			try {
				snapshots.save(player);
				saved.add(player);
			} catch (IOException | RuntimeException e) {
				GladiatorsMod.LOGGER.error("Could not save state of {}", player.getGameProfile().name(), e);
				saved.forEach(p -> snapshots.discard(p.getUUID()));
				return "Could not save " + player.getGameProfile().name() + "'s state to disk (" + e.getMessage() + "). Nothing was changed.";
			}
		}

		event = new GladiatorsEvent(arena);
		List<ServerPlayer> fighters = new ArrayList<>();
		for (ServerPlayer player : players) {
			boolean spectator = player.gameMode() == GameType.SPECTATOR;
			Participant.Role role = spectator ? Participant.Role.SPECTATOR : Participant.Role.FIGHTER;
			event.participants.put(player.getUUID(), new Participant(player.getUUID(), player.getGameProfile().name(), role));
			if (!spectator) {
				fighters.add(player);
			}
		}
		event.startingFighters = fighters.size();

		Collections.shuffle(fighters);
		for (int i = 0; i < fighters.size(); i++) {
			ServerPlayer player = fighters.get(i);
			SpawnPoint sp = pickSpawn(arena.spawnPoints, i, fighters.size());
			PlayerSnapshot.resetForFight(player);
			Teleports.to(player, level, sp.x, sp.y, sp.z, sp.yaw, 0.0F);
		}
		for (Participant p : event.participants.values()) {
			if (p.role == Participant.Role.SPECTATOR) {
				sendToSpectate(server.getPlayerList().getPlayer(p.id));
			}
		}

		server.getPlayerList().broadcastSystemMessage(Msg.ok("The Gladiators event has begun! " + fighters.size()
			+ " fighters are in the arena. Wait for your kit."), false);
		if (fighters.size() < 2) {
			server.getPlayerList().broadcastSystemMessage(Msg.warn("Fewer than 2 fighters: no winner will be announced."), false);
		}
		return null;
	}

	/** Evenly spaced picks around the angle-sorted spawn list, so players start far apart. */
	private static SpawnPoint pickSpawn(List<SpawnPoint> points, int index, int count) {
		int n = points.size();
		int slot = count <= n ? index * n / count : index % n;
		return points.get(slot);
	}

	// ---------------------------------------------------------------- end

	/** Returns an error message, or null on success. */
	public String end() {
		if (event == null) {
			return "No event is running.";
		}
		GladiatorsEvent ending = event;
		ServerLevel level = arenaLevel();
		if (level != null) {
			cleanup.run(level, ending);
		}
		int restored = 0;
		int offline = 0;
		for (Participant p : ending.participants.values()) {
			ServerPlayer player = server.getPlayerList().getPlayer(p.id);
			if (player != null && snapshots.restore(player)) {
				restored++;
			} else if (snapshots.hasPending(p.id)) {
				offline++; // restored automatically the next time they join
			}
		}
		event = null;
		server.getPlayerList().broadcastSystemMessage(Msg.ok("The Gladiators event is over. " + restored + " players were returned."), false);
		if (offline > 0) {
			server.getPlayerList().broadcastSystemMessage(Msg.info(offline + " offline participant(s) will be restored when they next join."), false);
		}
		return null;
	}

	// ---------------------------------------------------------------- kits

	/** Gives {@code kitName} to {@code targets} (null = every fighter still alive). Returns a summary or error. */
	public String giveKit(String kitName, Collection<ServerPlayer> targets, List<String> warnings) {
		if (event == null) {
			return "No event is running. Use /gladiators start first.";
		}
		KitDefinition kit = GladiatorsMod.kits().get(kitName);
		if (kit == null) {
			return "Unknown kit '" + kitName + "'. Available: " + String.join(", ", GladiatorsMod.kits().names());
		}
		var entries = KitFactory.build(kitName, kit, server.registryAccess(), warnings);
		Collection<ServerPlayer> recipients = targets;
		if (recipients == null) {
			recipients = new ArrayList<>();
			for (Participant p : event.participants.values()) {
				ServerPlayer player = p.aliveFighter() ? server.getPlayerList().getPlayer(p.id) : null;
				if (player != null) {
					recipients.add(player);
				}
			}
		}
		int given = 0;
		List<String> skipped = new ArrayList<>();
		for (ServerPlayer player : recipients) {
			Participant part = event.participants.get(player.getUUID());
			if (part == null || !part.aliveFighter()) {
				skipped.add(player.getGameProfile().name());
				continue;
			}
			KitFactory.give(player, entries);
			if (GladiatorsMod.config().get().healOnKit) {
				PlayerSnapshot.heal(player);
			}
			player.sendSystemMessage(Msg.ok("You received the '" + kitName + "' kit."));
			given++;
		}
		String summary = "Gave the '" + kitName + "' kit to " + given + " player(s).";
		if (!skipped.isEmpty()) {
			summary += " Skipped (not fighting): " + String.join(", ", skipped) + ".";
		}
		return summary;
	}

	// ---------------------------------------------------------------- connection events

	public void onJoin(ServerPlayer player) {
		// Anyone with a saved state gets it back first - after a crash, a mid-event disconnect, or an ended event.
		boolean restored = snapshots.restore(player);
		if (restored) {
			player.sendSystemMessage(Msg.info("Your inventory and location from before the Gladiators event were restored."));
		}
		if (event != null) {
			addLateSpectator(player);
		}
	}

	public void onDisconnect(ServerPlayer player) {
		if (event == null) {
			return;
		}
		Participant p = event.participants.get(player.getUUID());
		if (p != null && p.aliveFighter()) {
			p.eliminated = true;
			event.winnerCheckPending = true;
		}
	}

	private void addLateSpectator(ServerPlayer player) {
		try {
			snapshots.save(player); // so a crash cannot strand them in the arena
		} catch (IOException | RuntimeException e) {
			GladiatorsMod.LOGGER.error("Could not save late joiner {}", player.getGameProfile().name(), e);
			return;
		}
		Participant p = new Participant(player.getUUID(), player.getGameProfile().name(), Participant.Role.SPECTATOR);
		event.participants.put(player.getUUID(), p);
		PlayerSnapshot.resetForFight(player);
		sendToSpectate(player);
		player.sendSystemMessage(Msg.info("A Gladiators event is running. You are spectating."));
	}

	void sendToSpectate(ServerPlayer player) {
		if (player == null) {
			return;
		}
		ServerLevel level = arenaLevel();
		Vec3 spot = event.spectatorSpot();
		player.setGameMode(GameType.SPECTATOR);
		Teleports.to(player, level, spot.x, spot.y, spot.z, player.getYRot(), 0.0F);
	}

	// ---------------------------------------------------------------- deaths and ticks (delegated)

	public boolean onAllowDeath(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source) {
		return eliminations.handleDeath(player, source);
	}

	public void tick() {
		if (event == null) {
			return;
		}
		eliminations.tick();
		EventPatrol.tick(this);
	}
}
