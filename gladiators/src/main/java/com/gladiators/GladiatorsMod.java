package com.gladiators;

import com.gladiators.arena.ArenaManager;
import com.gladiators.command.GladiatorsCommand;
import com.gladiators.config.ConfigManager;
import com.gladiators.event.EventListeners;
import com.gladiators.event.EventManager;
import com.gladiators.event.Protection;
import com.gladiators.kit.KitDefinition;
import com.gladiators.kit.KitFactory;
import com.gladiators.kit.KitManager;
import com.gladiators.state.FallGrace;
import com.gladiators.state.SnapshotService;
import com.gladiators.state.SnapshotStore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Server-side Gladiators mod. Wires config, kits, arena and event managers to the Fabric events. */
public class GladiatorsMod implements ModInitializer {
	public static final String ID = "gladiators";
	public static final Logger LOGGER = LoggerFactory.getLogger(ID);

	private static final ConfigManager CONFIG = new ConfigManager();
	private static KitManager kits;
	private static ArenaManager arenas;
	private static EventManager events;

	public static ConfigManager config() {
		return CONFIG;
	}

	public static KitManager kits() {
		return kits;
	}

	/** Null until the server has started. */
	public static ArenaManager arenas() {
		return arenas;
	}

	/** Null until the server has started. */
	public static EventManager events() {
		return events;
	}

	@Override
	public void onInitialize() {
		CONFIG.load();
		kits = new KitManager(CONFIG.dir());
		String kitError = kits.load();
		if (kitError != null) {
			LOGGER.error("Kits not loaded: {}", kitError);
		}

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> GladiatorsCommand.register(dispatcher));
		EventListeners.register();
		Protection.register();

		ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			events = null;
			arenas = null;
			FallGrace.clear();
		});
	}

	private void onServerStarted(MinecraftServer server) {
		arenas = new ArenaManager(server);
		SnapshotService snapshots = new SnapshotService(server, new SnapshotStore(server.getWorldPath(LevelResource.ROOT)));
		events = new EventManager(server, arenas, snapshots);

		// An event never survives a restart, but saved player states do: they are applied when each player joins.
		int pending = snapshots.allPending().size();
		if (pending > 0) {
			LOGGER.warn("{} player(s) have a saved pre-event state; it will be restored when they join.", pending);
		}
		validateKits(server).forEach(w -> LOGGER.warn("Kit problem: {}", w));
		LOGGER.info("Gladiators ready: {} kits, arena status {}.", kits.names().size(), arenas.status());
	}

	/** Builds every kit once and returns the problems found (bad item ids, enchantments, components). */
	public static List<String> validateKits(MinecraftServer server) {
		List<String> warnings = new ArrayList<>();
		for (String name : kits.names()) {
			KitDefinition def = kits.get(name);
			KitFactory.build(name, def, server.registryAccess(), warnings);
		}
		return warnings;
	}
}
