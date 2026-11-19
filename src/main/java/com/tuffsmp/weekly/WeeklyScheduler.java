package com.tuffsmp.weekly;

import com.tuffsmp.Config;
import com.tuffsmp.altar.AltarManager;
import com.tuffsmp.boss.BossDef;
import com.tuffsmp.boss.BossManager;
import com.tuffsmp.boss.BossRegistry;
import com.tuffsmp.data.State;
import com.tuffsmp.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;

/** Tier 3: a world boss on a weekly timer, with advance warnings so everyone can gather. */
public final class WeeklyScheduler {
	/** Minutes before spawn at which a warning is broadcast. */
	private static final int[] WARN_MINUTES = {60, 15, 5, 1};

	private WeeklyScheduler() {}

	public static void init(MinecraftServer server) {
		State.WeeklyRecord w = State.INSTANCE.weekly;
		long now = System.currentTimeMillis();
		long grace = Config.INSTANCE.weeklyGraceHours * 3600_000L;
		if (w.nextSpawnMillis <= 0 || now > w.nextSpawnMillis + grace) reschedule();
	}

	public static void reschedule() {
		State.WeeklyRecord w = State.INSTANCE.weekly;
		w.nextSpawnMillis = computeNext().toInstant().toEpochMilli();
		w.warned.clear();
		State.INSTANCE.markDirty();
	}

	private static ZoneId zone() {
		try {
			return ZoneId.of(Config.INSTANCE.weeklyTimezone);
		} catch (RuntimeException e) {
			return ZoneOffset.UTC;
		}
	}

	public static ZonedDateTime computeNext() {
		ZoneId zone = zone();
		ZonedDateTime now = ZonedDateTime.now(zone);
		DayOfWeek dow;
		try {
			dow = DayOfWeek.valueOf(Config.INSTANCE.weeklyDay.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			dow = DayOfWeek.SATURDAY;
		}
		ZonedDateTime t = now.with(TemporalAdjusters.nextOrSame(dow))
				.withHour(Math.max(0, Math.min(23, Config.INSTANCE.weeklyHour)))
				.withMinute(Math.max(0, Math.min(59, Config.INSTANCE.weeklyMinute)))
				.withSecond(0).withNano(0);
		if (!t.isAfter(now)) t = t.plusWeeks(1);
		return t;
	}

	public static BossDef upcomingBoss() {
		List<BossDef> pool = BossRegistry.TIER3;
		return pool.get(Math.floorMod(State.INSTANCE.weekly.rotationIndex, pool.size()));
	}

	public static void tick(MinecraftServer server) {
		if (!Config.INSTANCE.weeklyEnabled) return;
		State.WeeklyRecord w = State.INSTANCE.weekly;
		long now = System.currentTimeMillis();
		long remainingSec = (w.nextSpawnMillis - now) / 1000;

		for (int minutes : WARN_MINUTES) {
			if (remainingSec <= minutes * 60L && remainingSec > 0 && !w.warned.contains(minutes)) {
				w.warned.add(minutes);
				State.INSTANCE.markDirty();
				BossDef d = upcomingBoss();
				Vec3 loc = locationFor(server);
				Msg.broadcast(server, Msg.text("☠ WEEKLY BOSS: ", ChatFormatting.DARK_RED, ChatFormatting.BOLD)
						.append(Msg.colored(d.name, d.color))
						.append(Msg.text(" rises in " + minutes + " minute" + (minutes == 1 ? "" : "s")
								+ " at " + (int) loc.x + ", " + (int) loc.z + " (" + AltarManager.dimensionName(State.INSTANCE.weekly.dimension) + ")!",
								ChatFormatting.RED)));
				if (minutes >= 15) {
					Msg.broadcast(server, Msg.text("   It is too strong for one player — everyone should gather!", ChatFormatting.YELLOW));
				}
				for (ServerPlayer p : server.getPlayerList().getPlayers()) {
					Msg.ping(p, SoundEvents.ENDER_DRAGON_GROWL, 0.6f, 0.7f);
				}
			}
		}

		if (remainingSec <= 0 && w.nextSpawnMillis > 0) {
			if (BossManager.tierThreeAlive()) {
				Msg.broadcast(server, Msg.text("The weekly boss is still alive — no new one this week.", ChatFormatting.GRAY));
				reschedule();
				return;
			}
			if (spawnNow(server, null)) {
				w.rotationIndex++;
				reschedule();
			} else {
				// Couldn't spawn (e.g. dimension missing). Try again in a minute rather than spamming.
				w.nextSpawnMillis = now + 60_000;
			}
		}
	}

	private static ServerLevel level(MinecraftServer server) {
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(State.INSTANCE.weekly.dimension));
		ServerLevel lvl = server.getLevel(key);
		return lvl != null ? lvl : server.overworld();
	}

	public static Vec3 locationFor(MinecraftServer server) {
		State.WeeklyRecord w = State.INSTANCE.weekly;
		if (w.hasLocation) return new Vec3(w.x + 0.5, w.y, w.z + 0.5);
		ServerLevel ow = server.overworld();
		BlockPos sp = ow.getRespawnData().pos();
		int y = ow.getHeight(Heightmap.Types.MOTION_BLOCKING, sp.getX(), sp.getZ());
		return new Vec3(sp.getX() + 0.5, y, sp.getZ() + 0.5);
	}

	public static void setLocation(ServerLevel level, BlockPos pos) {
		State.WeeklyRecord w = State.INSTANCE.weekly;
		w.hasLocation = true;
		w.dimension = level.dimension().identifier().toString();
		w.x = pos.getX();
		w.y = pos.getY();
		w.z = pos.getZ();
		State.INSTANCE.markDirty();
	}

	/** Spawns the weekly boss immediately (also used by /tuff weekly spawnnow). */
	public static boolean spawnNow(MinecraftServer server, BossDef override) {
		BossDef def = override != null ? override : upcomingBoss();
		ServerLevel level = State.INSTANCE.weekly.hasLocation ? level(server) : server.overworld();
		Vec3 at = locationFor(server);
		BlockPos bp = BlockPos.containing(at);
		level.getChunk(bp.getX() >> 4, bp.getZ() >> 4); // make sure the chunk is loaded
		Mob mob = BossManager.spawn(def, level, at, "the weekly summons");
		if (mob == null) return false;
		State.INSTANCE.weekly.activeBossUuid = mob.getUUID().toString();
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(at.x, at.y, at.z);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		Msg.broadcast(server, Msg.text("☠ THE WEEKLY BOSS HAS RISEN: ", ChatFormatting.DARK_RED, ChatFormatting.BOLD)
				.append(Msg.colored(def.name, def.color))
				.append(Msg.text(" at " + bp.getX() + ", " + bp.getY() + ", " + bp.getZ()
						+ " (" + AltarManager.dimensionName(level.dimension().identifier().toString()) + ")!", ChatFormatting.RED)));
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Msg.ping(p, SoundEvents.WITHER_SPAWN, 1.0f, 0.6f);
		}
		return true;
	}

	public static void onBossGone(MinecraftServer server) {
		State.INSTANCE.weekly.activeBossUuid = null;
		State.INSTANCE.markDirty();
	}

	public static Component status() {
		State.WeeklyRecord w = State.INSTANCE.weekly;
		ZonedDateTime next = Instant.ofEpochMilli(w.nextSpawnMillis).atZone(zone());
		String when = next.format(DateTimeFormatter.ofPattern("EEE yyyy-MM-dd HH:mm z", Locale.ENGLISH));
		long mins = Math.max(0, (w.nextSpawnMillis - System.currentTimeMillis()) / 60000);
		BossDef d = upcomingBoss();
		return Msg.text("Weekly boss: " + (Config.INSTANCE.weeklyEnabled ? "enabled" : "DISABLED")
				+ " | next: " + d.name + " on " + when + " (in " + mins / 60 + "h " + mins % 60 + "m)"
				+ " | location: " + (w.hasLocation ? w.x + ", " + w.y + ", " + w.z + " " + w.dimension : "world spawn (use /tuff weekly setlocation)"),
				ChatFormatting.GRAY);
	}
}
