package com.tuffsmp.boss;

import com.tuffsmp.Config;
import com.tuffsmp.data.State;
import com.tuffsmp.util.Msg;
import com.tuffsmp.weekly.WeeklyScheduler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Owns every living boss: spawning, ticking, damage tracking, death handling and persistence. */
public final class BossManager {
	public static final String TAG = "tuffsmp_boss";
	public static final String TAG_ID = "tuffsmp_boss_id:";
	public static final String TAG_MINION = "tuffsmp_minion";

	private static final Map<UUID, ActiveBoss> ACTIVE = new HashMap<>();
	private static final ResourceKey<LootTable> EMPTY_LOOT =
			ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("tuffsmp", "empty"));

	private BossManager() {}

	public static void init() {
		ACTIVE.clear();
	}

	public static Collection<ActiveBoss> active() {
		return ACTIVE.values();
	}

	public static ActiveBoss get(UUID id) {
		return ACTIVE.get(id);
	}

	/** True if a tier-3 boss exists, loaded or not. Used so the weekly spawner never doubles up. */
	public static boolean tierThreeAlive() {
		for (State.BossRecord r : State.INSTANCE.bosses) {
			BossDef d = BossRegistry.get(r.bossId);
			if (d != null && d.tier == BossTier.THREE) return true;
		}
		return false;
	}

	// -------------------------------------------------------------------------------------
	// Spawning
	// -------------------------------------------------------------------------------------

	public static Mob spawn(BossDef def, ServerLevel level, Vec3 pos, String summoner) {
		Mob mob = def.type.get().create(level, EntitySpawnReason.COMMAND);
		if (mob == null) return null;
		mob.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.COMMAND, null);
		if (mob instanceof Zombie z) z.setBaby(false);
		mob.ejectPassengers();

		double hp = def.health * Config.INSTANCE.bossHealthMultiplier;
		set(mob.getAttribute(Attributes.MAX_HEALTH), hp);
		set(mob.getAttribute(Attributes.ATTACK_DAMAGE), def.damage * Config.INSTANCE.bossDamageMultiplier);
		set(mob.getAttribute(Attributes.ARMOR), def.armor);
		set(mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE), def.knockbackResistance);
		set(mob.getAttribute(Attributes.SCALE), def.scale);
		set(mob.getAttribute(Attributes.FOLLOW_RANGE), 80);
		set(mob.getAttribute(Attributes.STEP_HEIGHT), Math.max(1.0, 0.5 + def.scale * 0.4));
		mob.setHealth((float) hp);

		if (def.mainHand != null) mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(def.mainHand.get()));
		for (EquipmentSlot slot : EquipmentSlot.values()) mob.setDropChance(slot, 0f);
		mob.setCanPickUpLoot(false);

		mob.setCustomName(Msg.colored(def.name, def.color));
		mob.setCustomNameVisible(true);
		mob.setPersistenceRequired();
		mob.skipDropExperience();
		mob.lootTable = Optional.of(EMPTY_LOOT); // widened by tuffsmp.accesswidener

		mob.addTag(TAG);
		mob.addTag(TAG_ID + def.id);

		ActiveBoss boss = new ActiveBoss(mob.getUUID(), def, level.dimension(), mob.position());
		boss.summoner = summoner;
		ACTIVE.put(mob.getUUID(), boss);

		if (!level.addFreshEntity(mob)) {
			ACTIVE.remove(mob.getUUID());
			return null;
		}
		boss.onSpawn(level, mob);
		syncState();
		State.INSTANCE.markDirty();

		BossFx.burst(level, mob.position(), ParticleTypes.EXPLOSION, 5, 1.5);
		BossFx.burst(level, mob.position(), ParticleTypes.SOUL_FIRE_FLAME, 60, 1.2);
		BossFx.sound(level, mob.position(), SoundEvents.WITHER_SPAWN, 1.0f, def.tier == BossTier.THREE ? 0.6f : 1.0f);
		return mob;
	}

	public static Mob spawnMinion(ActiveBoss boss, ServerLevel level, EntityType<? extends Mob> type, Vec3 pos) {
		Mob m = type.create(level, EntitySpawnReason.COMMAND);
		if (m == null) return null;
		m.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360f, 0f);
		m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()), EntitySpawnReason.COMMAND, null);
		if (m instanceof Zombie z) z.setBaby(false);
		if (m instanceof Vex v) v.setLimitedLife(20 * 90);
		m.ejectPassengers();

		AttributeInstance hp = m.getAttribute(Attributes.MAX_HEALTH);
		if (hp != null) {
			hp.setBaseValue(hp.getBaseValue() + 6 * boss.def.tier.number);
			m.setHealth(m.getMaxHealth());
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) m.setDropChance(slot, 0f);
		m.addTag(TAG_MINION);
		m.skipDropExperience();
		m.lootTable = Optional.of(EMPTY_LOOT);
		boss.minions.add(m.getUUID()); // before adding, so ENTITY_LOAD sees it as owned
		if (!level.addFreshEntity(m)) {
			boss.minions.remove(m.getUUID());
			return null;
		}
		BossFx.burst(level, m.position(), ParticleTypes.SOUL_FIRE_FLAME, 14, 0.4);
		return m;
	}

	private static void set(AttributeInstance inst, double value) {
		if (inst != null) inst.setBaseValue(value);
	}

	// -------------------------------------------------------------------------------------
	// Ticking & events
	// -------------------------------------------------------------------------------------

	public static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) return;
		for (ActiveBoss b : new ArrayList<>(ACTIVE.values())) {
			boolean keep;
			try {
				keep = b.tick(server);
			} catch (RuntimeException e) {
				com.tuffsmp.TuffSmp.LOGGER.error("Boss tick failed for {}", b.def.id, e);
				keep = true;
			}
			if (!keep) {
				ACTIVE.remove(b.uuid);
				State.INSTANCE.bosses.removeIf(r -> b.uuid.toString().equals(r.uuid));
				State.INSTANCE.markDirty();
				if (b.def.tier == BossTier.THREE) WeeklyScheduler.onBossGone(server);
			}
		}
	}

	/** A boss entity just loaded (e.g. after a restart): rebuild its runtime state from the saved record. */
	public static void onEntityLoad(Entity entity, ServerLevel level) {
		if (entity.getTags().contains(TAG_MINION)) {
			// Minions are temporary helpers. One that loads without a living owner (e.g. left over from before a
			// restart, when the boss's minion list was lost) is an orphan: remove it.
			boolean owned = false;
			for (ActiveBoss b : ACTIVE.values()) if (b.minions.contains(entity.getUUID())) owned = true;
			if (!owned) com.tuffsmp.util.Scheduler.later(1, entity::discard);
			return;
		}
		if (!(entity instanceof Mob mob) || !entity.getTags().contains(TAG) || ACTIVE.containsKey(entity.getUUID())) return;
		BossDef def = null;
		for (String t : entity.getTags()) {
			if (t.startsWith(TAG_ID)) def = BossRegistry.get(t.substring(TAG_ID.length()));
		}
		if (def == null) return;

		Vec3 home = mob.position();
		Map<UUID, Float> dmg = new HashMap<>();
		String summoner = null;
		for (State.BossRecord r : State.INSTANCE.bosses) {
			if (entity.getUUID().toString().equals(r.uuid)) {
				home = new Vec3(r.homeX, r.homeY, r.homeZ);
				summoner = r.summonerName;
				for (var e : r.damage.entrySet()) {
					try {
						dmg.put(UUID.fromString(e.getKey()), e.getValue());
					} catch (IllegalArgumentException ignored) {}
				}
			}
		}
		ActiveBoss boss = new ActiveBoss(entity.getUUID(), def, level.dimension(), home);
		boss.damage.putAll(dmg);
		boss.summoner = summoner;
		boss.restorePhase(mob);
		ACTIVE.put(entity.getUUID(), boss);
	}

	/** Bosses ignore environmental damage that big scaled mobs would otherwise die to by accident. */
	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!entity.getTags().contains(TAG)) return true;
		return !(source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.FLY_INTO_WALL)
				|| source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FIRE));
	}

	public static void onAfterDamage(LivingEntity entity, DamageSource source, float base, float taken, boolean blocked) {
		ActiveBoss b = ACTIVE.get(entity.getUUID());
		if (b == null || taken <= 0) return;
		if (source.getEntity() instanceof ServerPlayer sp) b.addDamage(sp, taken);
		// Co-op scaling: the more players are fighting, the more of each hit the boss shrugs off.
		double mult = b.coopMult;
		if (mult > 1.0 && entity.getHealth() > 0) {
			entity.heal((float) (taken * (1.0 - 1.0 / mult)));
		}
	}

	public static void onDeath(LivingEntity entity, DamageSource source) {
		ActiveBoss b = ACTIVE.remove(entity.getUUID());
		if (b == null || !(entity.level() instanceof ServerLevel level)) return;
		b.dead = true;
		b.removeBar();
		b.clearMinions(level);
		if (b.damage.isEmpty() && source.getEntity() instanceof ServerPlayer killer) {
			b.addDamage(killer, 1f); // killed in one blow before any damage was recorded
		}
		State.INSTANCE.bosses.removeIf(r -> entity.getUUID().toString().equals(r.uuid));
		State.INSTANCE.markDirty();

		MinecraftServer server = level.getServer();
		BossRewards.distribute(server, level, b, entity.position());
		if (b.def.tier == BossTier.THREE) WeeklyScheduler.onBossGone(server);
	}

	// -------------------------------------------------------------------------------------
	// Admin helpers & persistence
	// -------------------------------------------------------------------------------------

	/** Copies live runtime state (damage, home) into the persistent records. */
	public static void syncState() {
		for (ActiveBoss b : ACTIVE.values()) {
			State.BossRecord rec = null;
			for (State.BossRecord r : State.INSTANCE.bosses) {
				if (b.uuid.toString().equals(r.uuid)) rec = r;
			}
			if (rec == null) {
				rec = new State.BossRecord();
				rec.uuid = b.uuid.toString();
				State.INSTANCE.bosses.add(rec);
			}
			rec.bossId = b.def.id;
			rec.dimension = b.dimension.identifier().toString();
			rec.homeX = b.home.x;
			rec.homeY = b.home.y;
			rec.homeZ = b.home.z;
			rec.summonerName = b.summoner;
			rec.damage.clear();
			for (var e : b.damage.entrySet()) rec.damage.put(e.getKey().toString(), e.getValue());
		}
	}

	public static int killAll(MinecraftServer server) {
		int n = 0;
		for (ActiveBoss b : new ArrayList<>(ACTIVE.values())) {
			Mob m = b.resolve(server);
			ServerLevel level = server.getLevel(b.dimension);
			b.dead = true;
			b.removeBar();
			if (level != null) b.clearMinions(level);
			if (m != null) m.discard();
			n++;
		}
		ACTIVE.clear();
		State.INSTANCE.bosses.clear();
		State.INSTANCE.markDirty();
		return n;
	}

	public static Component describe(ActiveBoss b, MinecraftServer server) {
		Mob m = b.resolve(server);
		String where = m != null
				? String.format("%d, %d, %d (%s)", (int) m.getX(), (int) m.getY(), (int) m.getZ(), b.dimension.identifier())
				: "unloaded near " + (int) b.home.x + ", " + (int) b.home.y + ", " + (int) b.home.z;
		String hp = m != null ? String.format(" %.0f/%.0f HP, phase %d", m.getHealth(), m.getMaxHealth(), b.phase) : "";
		return Msg.colored("[" + b.def.tier.label + "] " + b.def.name, b.def.color).copy()
				.append(Msg.text(" - " + where + hp, net.minecraft.ChatFormatting.GRAY));
	}
}
