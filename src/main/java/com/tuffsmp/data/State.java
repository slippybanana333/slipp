package com.tuffsmp.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tuffsmp.TuffSmp;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * All persistent mod data, stored as JSON inside the world folder (world/tuffsmp/state.json), so a
 * world reset for a new season automatically resets everything.
 */
public class State {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static State INSTANCE = new State();

	public Map<String, PlayerRecord> players = new HashMap<>();
	public Map<String, List<PendingItem>> pendingRewards = new HashMap<>();
	public List<MagicRequest> requests = new ArrayList<>();
	public List<AltarRecord> altars = new ArrayList<>();
	public List<BossRecord> bosses = new ArrayList<>();
	public WeeklyRecord weekly = new WeeklyRecord();
	public int nextRequestId = 1;

	private transient boolean dirty;
	private transient Path file;

	public static class PlayerRecord {
		/** essence id -> number of that essence the player has invested. */
		public Map<String, Integer> invested = new HashMap<>();
		/** Currently selected ability id. */
		public String selectedAbility;
	}

	public static class PendingItem {
		public String item;
		public int count;

		public PendingItem() {}

		public PendingItem(String item, int count) {
			this.item = item;
			this.count = count;
		}
	}

	public static class MagicRequest {
		public int id;
		public String playerName;
		public String playerUuid;
		public long timeMillis;
		public String source;
	}

	public static class AltarRecord {
		public String bossId;
		public String dimension;
		public int x, y, z;
		public String summonerName;
		public String summonerUuid;
		public long startMillis;
		public long spawnMillis;
	}

	public static class BossRecord {
		public String uuid;
		public String bossId;
		public String dimension;
		public double homeX, homeY, homeZ;
		public Map<String, Float> damage = new HashMap<>();
		public String summonerName;
	}

	public static class WeeklyRecord {
		public long nextSpawnMillis = 0;
		public int rotationIndex = 0;
		public String dimension = "minecraft:overworld";
		public boolean hasLocation = false;
		public int x, y, z;
		public String activeBossUuid;
		/** Warnings already sent for the pending spawn (so restarts don't repeat them). */
		public List<Integer> warned = new ArrayList<>();
	}

	public static void load(MinecraftServer server) {
		Path dir = server.getWorldPath(LevelResource.ROOT).resolve("tuffsmp");
		Path file = dir.resolve("state.json");
		State loaded = null;
		try {
			Files.createDirectories(dir);
			if (Files.exists(file)) loaded = GSON.fromJson(Files.readString(file), State.class);
		} catch (IOException | RuntimeException e) {
			TuffSmp.LOGGER.error("Failed to load state.json; starting fresh (old file kept as state.json.broken)", e);
			try {
				Files.copy(file, dir.resolve("state.json.broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {}
		}
		INSTANCE = loaded != null ? loaded : new State();
		INSTANCE.file = file;
		INSTANCE.sanitize();
	}

	private void sanitize() {
		if (players == null) players = new HashMap<>();
		if (pendingRewards == null) pendingRewards = new HashMap<>();
		if (requests == null) requests = new ArrayList<>();
		if (altars == null) altars = new ArrayList<>();
		if (bosses == null) bosses = new ArrayList<>();
		if (weekly == null) weekly = new WeeklyRecord();
		if (weekly.warned == null) weekly.warned = new ArrayList<>();
	}

	public PlayerRecord player(UUID id) {
		dirty = true;
		return players.computeIfAbsent(id.toString(), k -> new PlayerRecord());
	}

	public PlayerRecord playerIfPresent(UUID id) {
		return players.get(id.toString());
	}

	public void markDirty() {
		dirty = true;
	}

	public void saveIfDirty() {
		if (dirty) save();
	}

	public void save() {
		if (file == null) return;
		try {
			Path tmp = file.resolveSibling("state.json.tmp");
			Files.writeString(tmp, GSON.toJson(this));
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			dirty = false;
		} catch (IOException e) {
			TuffSmp.LOGGER.error("Failed to save state.json", e);
		}
	}
}
