package com.tuffsmp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Server-owner tunables. Written to config/tuffsmp.json on first launch; edit and restart to change. */
public class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static Config INSTANCE = new Config();

	/** Name players are told to DM after channeling a Magic Essence. */
	public String ownerName = "the server owner";

	/** Tier 2: how long after the altar is activated the boss appears (seconds). */
	public int altarCountdownSeconds = 15 * 60;

	/** Tier 3 weekly boss. */
	public boolean weeklyEnabled = true;
	public String weeklyDay = "SATURDAY";
	public int weeklyHour = 18;
	public int weeklyMinute = 0;
	public String weeklyTimezone = "UTC";
	/** If the server was offline at spawn time, still spawn it if we're back within this many hours. */
	public int weeklyGraceHours = 6;

	/** Extra effective boss health per additional nearby player (0.5 = +50% each). */
	public double coopBonusTier1 = 0.35;
	public double coopBonusTier2 = 0.50;
	public double coopBonusTier3 = 0.70;

	/** Multiplies every boss's max health. */
	public double bossHealthMultiplier = 1.0;
	/** Multiplies every boss's damage (melee attribute and abilities). */
	public double bossDamageMultiplier = 1.0;

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("tuffsmp.json");
		try {
			if (Files.exists(path)) {
				Config c = GSON.fromJson(Files.readString(path), Config.class);
				if (c != null) INSTANCE = c;
			}
			// Always rewrite so new options appear in the file after updates.
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(INSTANCE));
		} catch (IOException | RuntimeException e) {
			TuffSmp.LOGGER.error("Could not read/write config, using defaults", e);
		}
	}
}
