package com.gladiators.event;

import com.gladiators.config.GladiatorsConfig.ArenaData;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** In-memory state of the running event. Everything that must survive a crash lives in the snapshot files. */
public final class GladiatorsEvent {
	public final ArenaData arena;
	public final Map<UUID, Participant> participants = new LinkedHashMap<>();
	/** Positions where players placed kit blocks (rails etc.), removed again when the event ends. */
	public final LongOpenHashSet placedBlocks = new LongOpenHashSet();
	public int startingFighters;
	public boolean winnerAnnounced;
	/** Winner is decided on the tick after the last death, so simultaneous deaths are all counted. */
	public boolean winnerCheckPending;

	public GladiatorsEvent(ArenaData arena) {
		this.arena = arena;
	}

	public long aliveCount() {
		return participants.values().stream().filter(Participant::aliveFighter).count();
	}

	/** Where spectators hover: above the middle of the arena. */
	public Vec3 spectatorSpot() {
		return new Vec3(arena.centerX + 0.5, arena.floorY + 12, arena.centerZ + 0.5);
	}
}
