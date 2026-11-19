package com.gladiators.arena;

import com.gladiators.GladiatorsMod;
import com.gladiators.config.GladiatorsConfig;
import com.gladiators.util.Msg;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Owns the arena lifecycle: build, remove, the on-disk manifest, and the one running {@link ArenaJob}. */
public final class ArenaManager {
	public enum Status {
		NONE, BUILDING, INCOMPLETE, BUILT, REMOVING
	}

	/** What is persisted in world/gladiators/arena/manifest.json. */
	private static final class Manifest {
		String status = Status.NONE.name();
		String dimension = "minecraft:overworld";
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final MinecraftServer server;
	private final Path dir;
	private final Path manifestFile;
	private final ArenaBackup backup;
	private Manifest manifest = new Manifest();
	private ArenaJob job;
	private CommandSourceStack reportTo;
	private ArenaDesign pendingDesign;
	private int lastReported = -1;

	public ArenaManager(MinecraftServer server) {
		this.server = server;
		this.dir = server.getWorldPath(LevelResource.ROOT).resolve("gladiators").resolve("arena");
		this.manifestFile = dir.resolve("manifest.json");
		this.backup = new ArenaBackup(dir);
		readManifest();
	}

	public Status status() {
		return Status.valueOf(manifest.status);
	}

	public boolean busy() {
		return job != null;
	}

	/** Starts a build centered on the command source. Returns an error message, or null if started. */
	public String build(CommandSourceStack source) {
		if (busy()) {
			return "Another arena job is still running (" + job.verb() + ").";
		}
		if (status() != Status.NONE) {
			return "An arena already exists (" + status() + "). Run /gladiators arena remove first.";
		}
		if (source.getEntity() == null) {
			return "Run this as a player: the arena is built where you stand.";
		}
		ServerLevel level = source.getLevel();
		BlockPos origin = BlockPos.containing(source.getPosition());
		GladiatorsConfig cfg = GladiatorsMod.config().get();
		ArenaDesign design = new ArenaDesign(cfg, origin.getX(), origin.getY() - 1, origin.getZ());
		if (design.lowestY() < level.getMinY() || design.ceilingY() > level.getMaxY()) {
			return "Not enough vertical room here: the arena needs Y " + design.lowestY() + " to " + design.ceilingY()
				+ " but this dimension allows " + level.getMinY() + " to " + level.getMaxY() + ".";
		}
		manifest.dimension = level.dimension().identifier().toString();
		setStatus(Status.BUILDING);
		pendingDesign = design;
		start(new ArenaBuilder(level, design, backup, cfg.blocksPerTick), source);
		return null;
	}

	/** Starts restoring the original blocks. Returns an error message, or null if started. */
	public String remove(CommandSourceStack source) {
		if (busy()) {
			return "Another arena job is still running (" + job.verb() + ").";
		}
		if (status() == Status.NONE) {
			return "There is no arena to remove.";
		}
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(manifest.dimension));
		ServerLevel level = server.getLevel(key);
		if (level == null) {
			return "The arena's dimension (" + manifest.dimension + ") is not loaded.";
		}
		setStatus(Status.REMOVING);
		start(new ArenaRemover(level, backup, GladiatorsMod.config().get().blocksPerTick), source);
		return null;
	}

	/** Called every server tick. */
	public void tick() {
		if (job == null) {
			return;
		}
		try {
			if (job.tick()) {
				finish();
				return;
			}
		} catch (IOException | RuntimeException e) {
			GladiatorsMod.LOGGER.error("Arena job failed", e);
			report(Msg.warn(job.verb() + " failed: " + e.getMessage() + ". Run /gladiators arena remove to restore the terrain."));
			job = null;
			pendingDesign = null;
			setStatus(Status.INCOMPLETE);
			return;
		}
		int percent = job.percent();
		if (percent / 10 != lastReported / 10) {
			lastReported = percent;
			report(Msg.info(job.verb() + "... " + percent + "%"));
		}
	}

	private void start(ArenaJob newJob, CommandSourceStack source) {
		job = newJob;
		reportTo = source;
		lastReported = -1;
		report(Msg.info(newJob.verb() + " (" + newJob.percent() + "%). Work is spread over many ticks; the server stays responsive."));
	}

	private void finish() {
		ArenaJob done = job;
		job = null;
		if (done instanceof ArenaBuilder) {
			GladiatorsConfig cfg = GladiatorsMod.config().get();
			cfg.arena = pendingDesign.toData(manifest.dimension);
			GladiatorsMod.config().save();
			setStatus(Status.BUILT);
			report(Msg.ok("Arena built at " + cfg.arena.centerX + ", " + cfg.arena.floorY + ", " + cfg.arena.centerZ
				+ " with " + cfg.arena.spawnPoints.size() + " spawn points. Use /gladiators start."));
		} else {
			backup.deleteAll();
			GladiatorsMod.config().get().arena = null;
			GladiatorsMod.config().save();
			setStatus(Status.NONE);
			report(Msg.ok("Arena removed; the original blocks were restored."));
		}
		pendingDesign = null;
	}

	/** Tells the command sender (the console also logs it); falls back to the log if there is no sender. */
	private void report(Component text) {
		if (reportTo != null) {
			reportTo.sendSuccess(() -> text, false);
		} else {
			GladiatorsMod.LOGGER.info(text.getString());
		}
	}

	private void setStatus(Status status) {
		manifest.status = status.name();
		try {
			Files.createDirectories(dir);
			Path tmp = dir.resolve("manifest.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(manifest, writer);
			}
			Files.move(tmp, manifestFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			GladiatorsMod.LOGGER.error("Could not write arena manifest", e);
		}
	}

	private void readManifest() {
		if (!Files.exists(manifestFile)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(manifestFile, StandardCharsets.UTF_8)) {
			Manifest read = GSON.fromJson(reader, Manifest.class);
			if (read != null) {
				manifest = read;
			}
		} catch (Exception e) {
			GladiatorsMod.LOGGER.error("Could not read arena manifest", e);
			return;
		}
		// A job cannot survive a restart: whatever it was doing is now an incomplete arena.
		if (status() == Status.BUILDING || status() == Status.REMOVING) {
			GladiatorsMod.LOGGER.warn("Arena job was interrupted by a restart. Run /gladiators arena remove to restore the terrain.");
			manifest.status = Status.INCOMPLETE.name();
		}
	}
}
