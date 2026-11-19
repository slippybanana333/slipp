package com.tuffsmp.altar;

import com.tuffsmp.Config;
import com.tuffsmp.ModBlocks;
import com.tuffsmp.ModItems;
import com.tuffsmp.boss.BossDef;
import com.tuffsmp.boss.BossManager;
import com.tuffsmp.boss.BossRegistry;
import com.tuffsmp.data.State;
import com.tuffsmp.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Tier 2 flow: a player offers a rune at a Boss Altar, the whole server is told, and the boss rises
 * once the countdown (default 15 minutes) ends. Countdowns use wall-clock time so they survive restarts.
 */
public final class AltarManager {
	private static final int[] REMINDERS = {600, 300, 120, 60, 30, 10};
	private static final Map<String, Pending> PENDING = new HashMap<>();

	private static final class Pending {
		final State.AltarRecord rec;
		final BossDef def;
		final ServerBossEvent bar;
		final Set<Integer> reminded = new HashSet<>();

		Pending(State.AltarRecord rec, BossDef def) {
			this.rec = rec;
			this.def = def;
			this.bar = new ServerBossEvent(Msg.colored(def.name, def.color), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
			this.bar.setDarkenScreen(false);
			this.bar.setPlayBossMusic(false);
		}
	}

	private AltarManager() {}

	private static String key(String dim, int x, int y, int z) {
		return dim + "|" + x + "|" + y + "|" + z;
	}

	/** Rebuilds countdowns from disk after a restart. */
	public static void init(MinecraftServer server) {
		PENDING.clear();
		long now = System.currentTimeMillis();
		for (State.AltarRecord r : State.INSTANCE.altars) {
			BossDef def = BossRegistry.get(r.bossId);
			if (def == null) continue;
			Pending p = new Pending(r, def);
			long remaining = (r.spawnMillis - now) / 1000;
			for (int t : REMINDERS) if (remaining < t) p.reminded.add(t);
			PENDING.put(key(r.dimension, r.x, r.y, r.z), p);
		}
	}

	public static List<String> describeAll() {
		List<String> out = new ArrayList<>();
		long now = System.currentTimeMillis();
		for (Pending p : PENDING.values()) {
			out.add(p.def.name + " at " + p.rec.x + ", " + p.rec.y + ", " + p.rec.z + " (" + p.rec.dimension + ") in "
					+ Msg.clock(Math.max(0, (p.rec.spawnMillis - now) / 1000)) + ", summoned by " + p.rec.summonerName);
		}
		return out;
	}

	public static int cancelAll(MinecraftServer server) {
		int n = PENDING.size();
		for (Pending p : PENDING.values()) p.bar.removeAllPlayers();
		PENDING.clear();
		State.INSTANCE.altars.clear();
		State.INSTANCE.markDirty();
		return n;
	}

	public static boolean start(ServerLevel level, BlockPos pos, ServerPlayer player, BossDef def) {
		boolean ok = start(level, pos, player.getGameProfile().name(), player.getUUID().toString(), def, Config.INSTANCE.altarCountdownSeconds);
		if (!ok) Msg.actionbar(player, Msg.text("This altar is already awakening something.", ChatFormatting.RED));
		return ok;
	}

	/** Core of {@link #start}: also used by /tuff altar start, with a custom countdown. */
	public static boolean start(ServerLevel level, BlockPos pos, String summonerName, String summonerUuid, BossDef def, int countdownSeconds) {
		String dim = level.dimension().identifier().toString();
		String k = key(dim, pos.getX(), pos.getY(), pos.getZ());
		if (PENDING.containsKey(k)) return false;
		State.AltarRecord r = new State.AltarRecord();
		r.bossId = def.id;
		r.dimension = dim;
		r.x = pos.getX();
		r.y = pos.getY();
		r.z = pos.getZ();
		r.summonerName = summonerName;
		r.summonerUuid = summonerUuid;
		r.startMillis = System.currentTimeMillis();
		r.spawnMillis = r.startMillis + countdownSeconds * 1000L;
		State.INSTANCE.altars.add(r);
		State.INSTANCE.markDirty();
		Pending pending = new Pending(r, def);
		// Short countdowns (e.g. admin tests) shouldn't instantly spam reminders for times that already passed.
		for (int t : REMINDERS) if (t >= countdownSeconds) pending.reminded.add(t);
		PENDING.put(k, pending);

		MinecraftServer server = level.getServer();
		Msg.broadcast(server, Msg.text("⚔ " + r.summonerName + " has begun summoning ", ChatFormatting.GOLD)
				.append(Msg.colored(def.name, def.color))
				.append(Msg.text(" at a Boss Altar!", ChatFormatting.GOLD)));
		Msg.broadcast(server, Msg.text("   Location: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
				+ " (" + dimensionName(dim) + ")", ChatFormatting.YELLOW));
		Msg.broadcast(server, Msg.text("   It awakens in " + Msg.clock(countdownSeconds)
				+ ". Gather your allies — or contest the kill!", ChatFormatting.YELLOW));
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Msg.ping(p, SoundEvents.ENDER_DRAGON_GROWL, 0.7f, 0.8f);
		}
		level.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.5f, 0.7f);
		return true;
	}

	public static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) return;
		long now = System.currentTimeMillis();
		for (Map.Entry<String, Pending> en : new ArrayList<>(PENDING.entrySet())) {
			Pending p = en.getValue();
			State.AltarRecord r = p.rec;
			ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(r.dimension));
			ServerLevel level = server.getLevel(dimKey);
			if (level == null) continue;
			BlockPos pos = new BlockPos(r.x, r.y, r.z);
			boolean loaded = level.isLoaded(pos);

			if (loaded && !level.getBlockState(pos).is(ModBlocks.BOSS_ALTAR)) {
				disrupt(server, level, en.getKey(), p);
				continue;
			}

			long remaining = Math.max(0, (r.spawnMillis - now) / 1000);
			if (loaded) effects(level, pos, remaining);

			for (int t : REMINDERS) {
				if (remaining <= t && p.reminded.add(t)) {
					Msg.broadcast(server, Msg.colored(p.def.name, p.def.color).copy()
							.append(Msg.text(" awakens in " + Msg.clock(t) + " at " + r.x + ", " + r.y + ", " + r.z
									+ " (" + dimensionName(r.dimension) + ")", ChatFormatting.YELLOW)));
				}
			}

			double total = Math.max(1, (r.spawnMillis - r.startMillis) / 1000.0);
			p.bar.setProgress((float) Math.max(0, Math.min(1, 1.0 - remaining / total)));
			p.bar.setName(Msg.colored(p.def.name, p.def.color).copy()
					.append(Msg.text(" awakens in " + Msg.clock(remaining), ChatFormatting.GRAY)));
			Vec3 c = Vec3.atCenterOf(pos);
			Set<ServerPlayer> near = new HashSet<>(level.getPlayers(pl -> pl.distanceToSqr(c) <= 128 * 128));
			for (ServerPlayer pl : new ArrayList<>(p.bar.getPlayers())) if (!near.contains(pl)) p.bar.removePlayer(pl);
			for (ServerPlayer pl : near) if (!p.bar.getPlayers().contains(pl)) p.bar.addPlayer(pl);

			if (remaining <= 0 && loaded) {
				Vec3 at = Vec3.atBottomCenterOf(pos.above());
				var mob = BossManager.spawn(p.def, level, at, r.summonerName);
				if (mob == null) continue; // try again next second
				LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
				if (bolt != null) {
					bolt.snapTo(at.x, at.y, at.z);
					bolt.setVisualOnly(true);
					level.addFreshEntity(bolt);
				}
				Msg.broadcast(server, Msg.colored("⚔ " + p.def.name, p.def.color).copy()
						.append(Msg.text(" has risen at " + r.x + ", " + r.y + ", " + r.z + " (" + dimensionName(r.dimension) + ")!", ChatFormatting.GOLD)));
				p.bar.removeAllPlayers();
				PENDING.remove(en.getKey());
				State.INSTANCE.altars.remove(r);
				State.INSTANCE.markDirty();
			}
		}
	}

	private static void effects(ServerLevel level, BlockPos pos, long remaining) {
		double cx = pos.getX() + 0.5, cy = pos.getY() + 1.0, cz = pos.getZ() + 0.5;
		int count = remaining < 30 ? 14 : 5;
		double radius = remaining < 30 ? 1.4 : 1.0;
		for (int i = 0; i < count; i++) {
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, cx + Math.cos(a) * radius, cy, cz + Math.sin(a) * radius,
					1, 0, 0.1, 0, 0.02);
		}
		level.sendParticles(ParticleTypes.ENCHANT, cx, cy + 0.6, cz, 6, 0.5, 0.5, 0.5, 0.5);
		if (remaining % 5 == 0) level.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1.0f, 0.6f);
		if (remaining <= 10 && remaining > 0) {
			level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 2.0f, 0.5f + (10 - remaining) * 0.1f);
		}
	}

	private static void disrupt(MinecraftServer server, ServerLevel level, String key, Pending p) {
		State.AltarRecord r = p.rec;
		p.bar.removeAllPlayers();
		PENDING.remove(key);
		State.INSTANCE.altars.remove(r);
		State.INSTANCE.markDirty();
		ItemStack rune = new ItemStack(ModItems.rune(p.def.id));
		level.addFreshEntity(new ItemEntity(level, r.x + 0.5, r.y + 0.5, r.z + 0.5, rune));
		Msg.broadcast(server, Msg.text("The summoning of ", ChatFormatting.GRAY).append(Msg.colored(p.def.name, p.def.color))
				.append(Msg.text(" at " + r.x + ", " + r.y + ", " + r.z + " was disrupted — the altar was destroyed!", ChatFormatting.GRAY)));
	}

	public static String dimensionName(String dim) {
		return switch (dim) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "End";
			default -> dim;
		};
	}
}
