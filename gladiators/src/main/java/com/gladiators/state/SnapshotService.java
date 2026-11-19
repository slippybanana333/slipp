package com.gladiators.state;

import com.gladiators.GladiatorsMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Saves and restores player states using the on-disk store. */
public final class SnapshotService {
	private final MinecraftServer server;
	private final SnapshotStore store;

	public SnapshotService(MinecraftServer server, SnapshotStore store) {
		this.server = server;
		this.store = store;
	}

	public void save(ServerPlayer player) throws IOException {
		store.save(player.getUUID(), PlayerSnapshot.capture(player, server));
	}

	public void discard(UUID id) {
		store.delete(id);
	}

	public boolean hasPending(UUID id) {
		return store.exists(id);
	}

	public Set<UUID> allPending() {
		return store.pending();
	}

	/**
	 * Restores the player's saved state if one is on disk. Returns true if a snapshot was applied. The file is
	 * only deleted after a clean restore; a partly failed one is moved to snapshots/failed/ instead of lost.
	 */
	public boolean restore(ServerPlayer player) {
		UUID id = player.getUUID();
		Optional<CompoundTag> tag = store.load(id);
		if (tag.isEmpty()) {
			return false;
		}
		try {
			List<String> problems = PlayerSnapshot.apply(player, server, tag.get());
			if (problems.isEmpty()) {
				store.delete(id);
			} else {
				GladiatorsMod.LOGGER.error("Restoring {} had problems: {}. Original kept in snapshots/failed/.", player.getGameProfile().name(), problems);
				store.quarantine(id);
			}
			return true;
		} catch (RuntimeException e) {
			GladiatorsMod.LOGGER.error("Restoring {} failed; the snapshot is kept and will be retried on next join.", player.getGameProfile().name(), e);
			return false;
		}
	}
}
