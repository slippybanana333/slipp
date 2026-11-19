package com.gladiators.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.gladiators.GladiatorsMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Loads and saves config/gladiators/config.json. */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path dir = FabricLoader.getInstance().getConfigDir().resolve("gladiators");
	private final Path file = dir.resolve("config.json");
	private GladiatorsConfig config = new GladiatorsConfig();

	public GladiatorsConfig get() {
		return config;
	}

	public Path dir() {
		return dir;
	}

	/** Reads config.json (creating it with defaults on first run). Returns an error text or null on success. */
	public String load() {
		try {
			Files.createDirectories(dir);
			if (!Files.exists(file)) {
				save();
				return null;
			}
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				GladiatorsConfig loaded = GSON.fromJson(reader, GladiatorsConfig.class);
				if (loaded != null) {
					config = loaded;
					sanitize();
				}
			}
			return null;
		} catch (Exception e) {
			// Keep the previous settings and leave the broken file untouched so the user can fix it.
			GladiatorsMod.LOGGER.error("Could not read {}: {}", file, e.toString());
			return "config.json is invalid: " + e.getMessage();
		}
	}

	public void save() {
		try {
			Files.createDirectories(dir);
			Path tmp = dir.resolve("config.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			GladiatorsMod.LOGGER.error("Could not write {}", file, e);
		}
	}

	/** Clamp values so a typo in the file cannot produce a broken arena. */
	private void sanitize() {
		GladiatorsConfig c = config;
		c.arenaRadius = clamp(c.arenaRadius, 12, 120);
		c.seatingTiers = clamp(c.seatingTiers, 0, 24);
		c.seatingTierWidth = clamp(c.seatingTierWidth, 1, 6);
		c.wallThickness = clamp(c.wallThickness, 1, 6);
		c.wallExtraHeight = clamp(c.wallExtraHeight, 0, 40);
		c.ceilingHeight = clamp(c.ceilingHeight, c.seatingTiers + c.wallExtraHeight + 8, 200);
		c.pillarCount = clamp(c.pillarCount, 0, 64);
		c.lightSpacing = c.lightSpacing <= 0 ? 0 : clamp(c.lightSpacing, 3, 16);
		c.blocksPerTick = clamp(c.blocksPerTick, 100, 50_000);
		c.fallGraceSeconds = clamp(c.fallGraceSeconds, 0, 60);
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
