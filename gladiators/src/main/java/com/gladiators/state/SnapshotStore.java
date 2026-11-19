package com.gladiators.state;

import com.gladiators.GladiatorsMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * One file per player in {@code <world>/gladiators/snapshots/}. A file exists from the moment a player is
 * pulled into an event until their original state has been restored, so it survives crashes.
 */
public final class SnapshotStore {
	private final Path dir;
	private final Path failedDir;

	public SnapshotStore(Path worldRoot) {
		this.dir = worldRoot.resolve("gladiators").resolve("snapshots");
		this.failedDir = dir.resolve("failed");
	}

	/** Writes atomically: a crash mid-write can never leave a half-written snapshot behind. */
	public void save(UUID id, CompoundTag tag) throws IOException {
		Files.createDirectories(dir);
		Path tmp = dir.resolve(id + ".dat.tmp");
		NbtIo.writeCompressed(tag, tmp);
		try {
			Files.move(tmp, file(id), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (java.nio.file.AtomicMoveNotSupportedException e) {
			Files.move(tmp, file(id), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public boolean exists(UUID id) {
		return Files.exists(file(id));
	}

	public Optional<CompoundTag> load(UUID id) {
		Path file = file(id);
		if (!Files.exists(file)) {
			return Optional.empty();
		}
		try {
			return Optional.of(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()));
		} catch (IOException e) {
			GladiatorsMod.LOGGER.error("Could not read snapshot {}", file, e);
			return Optional.empty();
		}
	}

	public void delete(UUID id) {
		try {
			Files.deleteIfExists(file(id));
		} catch (IOException e) {
			GladiatorsMod.LOGGER.error("Could not delete snapshot for {}", id, e);
		}
	}

	/** Moves a snapshot that could only be partly restored aside so nothing is ever silently destroyed. */
	public void quarantine(UUID id) {
		try {
			Files.createDirectories(failedDir);
			Files.move(file(id), failedDir.resolve(id + "-" + System.currentTimeMillis() + ".dat"));
		} catch (IOException e) {
			GladiatorsMod.LOGGER.error("Could not quarantine snapshot for {}", id, e);
		}
	}

	/** All players that still have a saved state waiting to be restored. */
	public Set<UUID> pending() {
		Set<UUID> ids = new HashSet<>();
		if (!Files.isDirectory(dir)) {
			return ids;
		}
		try (Stream<Path> files = Files.list(dir)) {
			files.map(p -> p.getFileName().toString())
				.filter(n -> n.endsWith(".dat"))
				.forEach(n -> {
					try {
						ids.add(UUID.fromString(n.substring(0, n.length() - 4)));
					} catch (IllegalArgumentException ignored) {
						// not one of ours
					}
				});
		} catch (IOException e) {
			GladiatorsMod.LOGGER.error("Could not list {}", dir, e);
		}
		return ids;
	}

	private Path file(UUID id) {
		return dir.resolve(id + ".dat");
	}
}
