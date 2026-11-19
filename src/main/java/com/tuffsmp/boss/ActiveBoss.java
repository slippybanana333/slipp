package com.tuffsmp.boss;

import com.tuffsmp.Config;
import com.tuffsmp.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Runtime state of one living boss. Ticked every server tick by {@link BossManager}. */
public final class ActiveBoss {
	private static final Identifier PHASE_MOD = Identifier.fromNamespaceAndPath("tuffsmp", "boss_phase");

	public final UUID uuid;
	public final BossDef def;
	public final ResourceKey<Level> dimension;
	public Vec3 home;
	public final Map<UUID, Float> damage = new HashMap<>();
	public final Map<UUID, String> names = new HashMap<>();
	public final Set<UUID> minions = new HashSet<>();
	public final ServerBossEvent bar;
	public String summoner;
	/** Tier 2/3 fight area; null for Tier 1. */
	public com.tuffsmp.arena.Arena arena;

	public int phase = 1;
	public boolean dead = false;
	/** Effective-health multiplier from nearby players (damage taken is partly healed back). */
	public volatile double coopMult = 1.0;
	/** Health as of the last tick/hit, so a killing blow can be credited for the health it actually removed. */
	public float lastHealth;

	private int age = 0;
	private int skillCooldown;
	private BossSkill lastSkill;
	private int idleTicks = 0;
	private Vec3 lastPos;

	public ActiveBoss(UUID uuid, BossDef def, ResourceKey<Level> dimension, Vec3 home) {
		this.uuid = uuid;
		this.def = def;
		this.dimension = dimension;
		this.home = home;
		this.lastPos = home;
		this.skillCooldown = 60 + def.skillInterval / 2;
		BossEvent.BossBarOverlay overlay = switch (def.tier) {
			case ONE -> BossEvent.BossBarOverlay.PROGRESS;
			case TWO -> BossEvent.BossBarOverlay.NOTCHED_10;
			case THREE -> BossEvent.BossBarOverlay.NOTCHED_20;
		};
		this.bar = new ServerBossEvent(title(), def.barColor, overlay);
		this.bar.setDarkenScreen(false);
		this.bar.setPlayBossMusic(false);
		this.bar.setCreateWorldFog(false);
	}

	private Component title() {
		return Msg.colored(def.name, def.color).copy().append(Msg.colored("  [" + def.tier.label + "]", 0xAAAAAA));
	}

	/** Ability damage: boss base damage x phase scaling x global multiplier x the skill's own factor. */
	public double damage(double factor) {
		double phaseMult = 1.0 + 0.2 * (phase - 1);
		return def.damage * factor * phaseMult * Config.INSTANCE.bossDamageMultiplier;
	}

	public void addDamage(ServerPlayer p, float amount) {
		damage.merge(p.getUUID(), amount, Float::sum);
		names.put(p.getUUID(), p.getGameProfile().name());
	}

	public float totalDamage() {
		float t = 0;
		for (float f : damage.values()) t += f;
		return t;
	}

	public Mob resolve(MinecraftServer server) {
		ServerLevel level = server.getLevel(dimension);
		if (level == null) return null;
		Entity e = level.getEntityInAnyDimension(uuid);
		return e instanceof Mob m && m.level() == level ? m : null;
	}

	public void pruneMinions(ServerLevel level) {
		minions.removeIf(id -> {
			Entity e = level.getEntityInAnyDimension(id);
			return e == null || !e.isAlive();
		});
	}

	/** Discards any minions that are still alive (boss died / was removed). */
	public void clearMinions(ServerLevel level) {
		for (UUID id : minions) {
			Entity e = level.getEntityInAnyDimension(id);
			if (e != null && e.isAlive()) {
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
				e.discard();
			}
		}
		minions.clear();
		// Also sweep up orphans (minions summoned before a restart aren't tracked any more).
		double r = def.tier.leashRadius + 32;
		var area = new net.minecraft.world.phys.AABB(home, home).inflate(r);
		for (Mob m : level.getEntitiesOfClass(Mob.class, area, e -> e.getTags().contains(BossManager.TAG_MINION))) {
			m.discard();
		}
	}

	// ---------------------------------------------------------------------------------------

	/** Returns false if the boss entity is gone for good and this record should be dropped. */
	public boolean tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(dimension);
		if (level == null) return true;
		Mob mob = resolve(server);
		if (mob == null) {
			bar.setVisible(false);
			// Entity not found: either its chunk is unloaded (keep waiting) or it was removed.
			return !level.isLoaded(net.minecraft.core.BlockPos.containing(lastPos)) ? true : tryRecover(level);
		}
		if (!mob.isAlive()) return true; // death handled by the death event
		bar.setVisible(true);
		lastPos = mob.position();
		lastHealth = mob.getHealth();
		age++;

		if (mob.isOnFire()) mob.clearFire();

		if (age % 10 == 0) acquireTarget(level, mob);

		if (age % 20 == 0) {
			updateCoop(level, mob);
			syncBar(level, mob);
			checkPhase(level, mob);
			leash(level, mob);
		}

		if (--skillCooldown <= 0) {
			LivingTarget t = LivingTarget.of(mob);
			if (t != null && mob.distanceToSqr(t.entity) < 40 * 40) {
				castSkill(level, mob, t);
			} else {
				skillCooldown = 20;
			}
		}
		return true;
	}

	private boolean tryRecover(ServerLevel level) {
		// Chunk is loaded but the entity isn't there: it was discarded without dying.
		bar.removeAllPlayers();
		return false;
	}

	private record LivingTarget(net.minecraft.world.entity.LivingEntity entity) {
		static LivingTarget of(Mob m) {
			var t = m.getTarget();
			return t != null && t.isAlive() ? new LivingTarget(t) : null;
		}
	}

	private void castSkill(ServerLevel level, Mob mob, LivingTarget t) {
		List<BossDef.SkillSlot> available = new ArrayList<>();
		for (BossDef.SkillSlot s : def.skills) {
			if (s.minPhase() <= phase) available.add(s);
		}
		if (available.isEmpty()) {
			skillCooldown = def.skillInterval;
			return;
		}
		BossSkill pick;
		int guard = 0;
		do {
			pick = available.get(mob.getRandom().nextInt(available.size())).skill();
		} while (pick == lastSkill && available.size() > 1 && ++guard < 6);
		lastSkill = pick;
		BossSkills.cast(this, mob, level, t.entity, pick);

		double phaseSpeed = switch (phase) {
			case 1 -> 1.0;
			case 2 -> 0.78;
			default -> 0.58;
		};
		int jitter = mob.getRandom().nextInt(Math.max(1, def.skillInterval / 4));
		skillCooldown = (int) (def.skillInterval * phaseSpeed) + jitter;
	}

	private void acquireTarget(ServerLevel level, Mob mob) {
		var current = mob.getTarget();
		if (current != null && current.isAlive() && !(current instanceof ServerPlayer sp && (sp.isCreative() || sp.isSpectator()))
				&& mob.distanceToSqr(current) < def.tier.targetRange * def.tier.targetRange) {
			// keep the current target unless someone is much closer
			var nearest = level.getNearestPlayer(mob.getX(), mob.getY(), mob.getZ(), def.tier.targetRange, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
			if (nearest == null || mob.distanceToSqr(current) <= mob.distanceToSqr(nearest) + 36) {
				pokeWarden(mob, current);
				return;
			}
		}
		var p = level.getNearestPlayer(mob.getX(), mob.getY(), mob.getZ(), def.tier.targetRange, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
		if (p != null) {
			mob.setTarget(p);
			pokeWarden(mob, p);
		} else {
			mob.setTarget(null);
		}
	}

	/** Wardens ignore plain setTarget; they hunt whoever they're angry at. */
	private void pokeWarden(Mob mob, net.minecraft.world.entity.LivingEntity target) {
		if (mob instanceof Warden w) w.increaseAngerAt(target, 150, false);
	}

	private void updateCoop(ServerLevel level, Mob mob) {
		double range = def.tier == BossTier.THREE ? 72 : 48;
		int n = BossFx.players(level, mob.position(), range).size();
		coopMult = Math.min(12.0, 1.0 + def.tier.coopBonus() * Math.max(0, n - 1));
	}

	private void syncBar(ServerLevel level, Mob mob) {
		bar.setProgress(Math.max(0f, Math.min(1f, mob.getHealth() / mob.getMaxHealth())));
		double range = def.tier == BossTier.THREE ? 140 : 90;
		double r2 = range * range;
		Set<ServerPlayer> near = new HashSet<>(level.getPlayers(p -> p.distanceToSqr(mob) <= r2));
		for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) {
			if (!near.contains(p)) bar.removePlayer(p);
		}
		for (ServerPlayer p : near) {
			if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
		}
	}

	private void checkPhase(ServerLevel level, Mob mob) {
		float ratio = mob.getHealth() / mob.getMaxHealth();
		int target = ratio <= 0.33f ? 3 : ratio <= 0.66f ? 2 : 1;
		if (target <= phase) return;
		phase = target;
		applyPhaseModifiers(mob);
		for (ServerPlayer p : level.getPlayers(pl -> pl.distanceToSqr(mob) <= 64 * 64)) {
			Msg.tell(p, Msg.colored(def.name + (phase == 2 ? " grows enraged!" : " is DESPERATE!"), def.color));
		}
		BossFx.sound(level, mob.position(), SoundEvents.ENDER_DRAGON_GROWL, 2.0f, phase == 2 ? 0.9f : 0.7f);
		BossFx.burst(level, mob.position(), net.minecraft.core.particles.ParticleTypes.EXPLOSION, 4, 1.5);
		if (def.hasSkill(BossSkill.MINIONS)) {
			LivingTarget t = LivingTarget.of(mob);
			BossSkills.minions(this, mob, level, t == null ? null : t.entity, true);
		}
		skillCooldown = Math.min(skillCooldown, 30);
	}

	/** Re-applies phase scaling (also used after a restart reloads a boss mid-fight). */
	public void applyPhaseModifiers(Mob mob) {
		double dmgBonus = 0.25 * (phase - 1);
		double speedBonus = 0.10 * (phase - 1);
		mod(mob.getAttribute(Attributes.ATTACK_DAMAGE), dmgBonus);
		mod(mob.getAttribute(Attributes.MOVEMENT_SPEED), speedBonus);
	}

	private static void mod(AttributeInstance inst, double amount) {
		if (inst == null) return;
		inst.removeModifier(PHASE_MOD);
		if (amount != 0) {
			inst.addTransientModifier(new AttributeModifier(PHASE_MOD, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private void leash(ServerLevel level, Mob mob) {
		boolean anyone = !BossFx.players(level, mob.position(), 64).isEmpty();
		idleTicks = anyone ? 0 : idleTicks + 20;
		boolean outside;
		if (arena != null) {
			// The boss never leaves its arena.
			double dx = mob.getX() - (arena.center.getX() + 0.5), dz = mob.getZ() - (arena.center.getZ() + 0.5);
			double max = arena.radius - 1.5;
			outside = dx * dx + dz * dz > max * max;
		} else {
			double max = def.tier.leashRadius;
			outside = mob.position().distanceToSqr(home) > max * max;
		}
		if (outside) {
			mob.teleportTo(home.x, home.y, home.z);
			mob.setTarget(null);
			mob.heal(mob.getMaxHealth() * 0.1f);
			BossFx.burst(level, home, net.minecraft.core.particles.ParticleTypes.PORTAL, 40, 0.8);
		}
		// Everyone left or died: reset the fight so it can't be chipped away for free.
		if (idleTicks >= 20 * 120 && mob.getHealth() < mob.getMaxHealth()) {
			mob.setHealth(mob.getMaxHealth());
			mob.teleportTo(home.x, home.y, home.z);
			damage.clear();
			phase = 1;
			applyPhaseModifiers(mob);
			idleTicks = 0;
			// Abandoned fight: put the arena back to how it was built, and the boss stays.
			if (arena != null) arena.build(level, () -> {});
		}
	}

	public void onSpawn(ServerLevel level, Mob mob) {
		lastPos = mob.position();
		updateCoop(level, mob);
		applyPhaseModifiers(mob);
		syncBar(level, mob);
	}

	public void removeBar() {
		bar.removeAllPlayers();
		bar.setVisible(false);
	}

	/** Used after a restart: derive phase from current health. */
	public void restorePhase(Mob mob) {
		float ratio = mob.getHealth() / mob.getMaxHealth();
		phase = ratio <= 0.33f ? 3 : ratio <= 0.66f ? 2 : 1;
		applyPhaseModifiers(mob);
	}
}
