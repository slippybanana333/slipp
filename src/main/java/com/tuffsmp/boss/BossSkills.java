package com.tuffsmp.boss;

import com.tuffsmp.util.Scheduler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The implementation of every {@link BossSkill}. Each is telegraphed first so players can react. */
final class BossSkills {
	private BossSkills() {}

	static void cast(ActiveBoss b, Mob mob, ServerLevel level, LivingEntity target, BossSkill skill) {
		switch (skill) {
			case SLAM -> slam(b, mob, level);
			case VOLLEY -> volley(b, mob, level, target);
			case MINIONS -> minions(b, mob, level, target, false);
			case LEAP -> leap(b, mob, level, target);
			case NOVA -> nova(b, mob, level);
			case LIGHTNING -> lightning(b, mob, level);
			case PULL -> pull(b, mob, level);
			case TELEPORT -> teleport(b, mob, level, target);
			case DARKNESS -> darkness(b, mob, level);
			case WITHER_BLAST -> witherBlast(b, mob, level);
		}
	}

	private static boolean live(ActiveBoss b, Mob mob) {
		return mob.isAlive() && !mob.isRemoved() && !b.dead;
	}

	// ---------------------------------------------------------------------------------------

	private static void slam(ActiveBoss b, Mob mob, ServerLevel level) {
		double radius = 5.5 + b.def.tier.number * 0.5 + b.phase * 0.5;
		double dmg = b.damage(1.4);
		BossFx.warn(level, mob, b.def, "raises its fists!");
		BossFx.sound(level, mob.position(), SoundEvents.IRON_GOLEM_ATTACK, 1.5f, 0.6f);
		for (int t = 0; t <= 16; t += 4) {
			Scheduler.later(Math.max(1, t), () -> {
				if (live(b, mob)) BossFx.ring(level, mob.position(), radius, ParticleTypes.CRIT, 36);
			});
		}
		Scheduler.later(20, () -> {
			if (!live(b, mob)) return;
			Vec3 c = mob.position();
			for (ServerPlayer p : BossFx.players(level, c, radius)) {
				BossFx.hurt(level, mob, p, dmg);
				BossFx.knock(p, c, 1.1, 0.55);
			}
			BossFx.burst(level, c, ParticleTypes.EXPLOSION, 6, radius * 0.4);
			BossFx.ring(level, c, radius, ParticleTypes.CLOUD, 40);
			BossFx.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.7f);
		});
	}

	private static void volley(ActiveBoss b, Mob mob, ServerLevel level, LivingEntity target) {
		int count = 3 + b.def.tier.number + b.phase;
		double dmg = b.damage(0.9);
		BossFx.warn(level, mob, b.def, "gathers fire!");
		BossFx.sound(level, mob.position(), SoundEvents.BLAZE_SHOOT, 1.2f, 0.6f);
		for (int i = 0; i < count; i++) {
			Scheduler.later(12 + i * 4, () -> {
				if (!live(b, mob) || !target.isAlive()) return;
				Vec3 from = mob.getEyePosition();
				Vec3 to = target.getEyePosition().add(0, -0.2, 0);
				Vec3 dir = to.subtract(from).normalize();
				var r = mob.getRandom();
				dir = dir.add((r.nextDouble() - 0.5) * 0.22, (r.nextDouble() - 0.5) * 0.12, (r.nextDouble() - 0.5) * 0.22);
				Bolt.fire(level, mob, from, dir, 0.95, 45, dmg, ParticleTypes.FLAME, true);
				BossFx.sound(level, from, SoundEvents.BLAZE_SHOOT, 0.8f, 1.1f);
			});
		}
	}

	static void minions(ActiveBoss b, Mob mob, ServerLevel level, LivingEntity target, boolean burst) {
		if (b.def.minion == null) return;
		int cap = 3 + b.def.tier.number * 3;
		b.pruneMinions(level);
		int room = cap - b.minions.size();
		if (room <= 0) return;
		int want = (burst ? 2 : 1) + b.def.tier.number + b.phase;
		int n = Math.min(room, want);
		BossFx.warn(level, mob, b.def, "calls for aid!");
		BossFx.sound(level, mob.position(), SoundEvents.EVOKER_PREPARE_SUMMON, 1.2f, 0.8f);
		var r = mob.getRandom();
		for (int i = 0; i < n; i++) {
			Vec3 at = mob.position().add((r.nextDouble() - 0.5) * 8, 0, (r.nextDouble() - 0.5) * 8);
			Mob m = BossManager.spawnMinion(b, level, b.def.minion.get(), at);
			if (m != null && target != null) m.setTarget(target);
		}
	}

	private static void leap(ActiveBoss b, Mob mob, ServerLevel level, LivingEntity target) {
		double dmg = b.damage(1.0);
		BossFx.warn(level, mob, b.def, "crouches to pounce!");
		BossFx.sound(level, mob.position(), SoundEvents.RAVAGER_ROAR, 1.2f, 0.9f);
		Scheduler.later(10, () -> {
			if (!live(b, mob) || !target.isAlive()) return;
			Vec3 d = target.position().subtract(mob.position());
			Vec3 h = new Vec3(d.x, 0, d.z);
			double len = h.length();
			h = len < 1.0E-3 ? Vec3.ZERO : h.normalize().scale(Math.min(1.7, 0.35 + len * 0.09));
			mob.setDeltaMovement(h.x, 0.75 + Math.min(0.4, len * 0.02), h.z);
			mob.hurtMarked = true;
			BossFx.burst(level, mob.position(), ParticleTypes.CLOUD, 12, 0.5);
			watchLanding(b, mob, level, dmg, 0);
		});
	}

	private static void watchLanding(ActiveBoss b, Mob mob, ServerLevel level, double dmg, int waited) {
		Scheduler.later(2, () -> {
			if (!live(b, mob)) return;
			int w = waited + 2;
			if ((w > 6 && mob.onGround()) || w > 60) {
				Vec3 c = mob.position();
				for (ServerPlayer p : BossFx.players(level, c, 4.5)) {
					BossFx.hurt(level, mob, p, dmg);
					BossFx.knock(p, c, 0.9, 0.45);
				}
				BossFx.burst(level, c, ParticleTypes.EXPLOSION, 3, 1.5);
				BossFx.ring(level, c, 4.5, ParticleTypes.CLOUD, 28);
				BossFx.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.9f);
			} else {
				watchLanding(b, mob, level, dmg, w);
			}
		});
	}

	private static void nova(ActiveBoss b, Mob mob, ServerLevel level) {
		double radius = 9.0 + b.phase;
		double dmg = b.damage(0.6);
		BossFx.warn(level, mob, b.def, "is gathering frost!");
		BossFx.sound(level, mob.position(), SoundEvents.PLAYER_HURT_FREEZE, 1.4f, 0.6f);
		for (int t = 2; t <= 14; t += 4) {
			final int step = t;
			Scheduler.later(step, () -> {
				if (live(b, mob)) BossFx.ring(level, mob.position(), radius * (0.3 + 0.05 * (14 - step)), ParticleTypes.SNOWFLAKE, 30);
			});
		}
		Scheduler.later(18, () -> {
			if (!live(b, mob)) return;
			Vec3 c = mob.position();
			for (ServerPlayer p : BossFx.players(level, c, radius)) {
				BossFx.hurt(level, mob, p, dmg);
				p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1));
				p.setTicksFrozen(p.getTicksRequiredToFreeze() + 80);
			}
			BossFx.burst(level, c, ParticleTypes.SNOWFLAKE, 80, radius * 0.5);
			BossFx.ring(level, c, radius, ParticleTypes.SNOWFLAKE, 60);
			BossFx.sound(level, c, SoundEvents.GLASS_BREAK, 1.5f, 0.6f);
		});
	}

	private static void lightning(ActiveBoss b, Mob mob, ServerLevel level) {
		List<ServerPlayer> victims = new ArrayList<>(BossFx.players(level, mob.position(), 30));
		if (victims.isEmpty()) return;
		Collections.shuffle(victims);
		int n = Math.min(victims.size(), 2 + b.def.tier.number + b.phase - 1);
		double dmg = b.damage(1.3);
		BossFx.warn(level, mob, b.def, "calls down the storm!");
		BossFx.sound(level, mob.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0f, 1.5f);
		for (int i = 0; i < n; i++) {
			Vec3 strike = victims.get(i).position();
			for (int t = 2; t <= 26; t += 4) {
				Scheduler.later(t, () -> {
					if (live(b, mob)) BossFx.ring(level, strike, 2.6, ParticleTypes.ELECTRIC_SPARK, 18);
				});
			}
			Scheduler.later(30, () -> {
				if (!live(b, mob)) return;
				LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
				if (bolt != null) {
					bolt.snapTo(strike.x, strike.y, strike.z);
					bolt.setVisualOnly(true);
					level.addFreshEntity(bolt);
				}
				for (ServerPlayer p : BossFx.players(level, strike, 2.8)) BossFx.hurt(level, mob, p, dmg);
			});
		}
	}

	private static void pull(ActiveBoss b, Mob mob, ServerLevel level) {
		double radius = 18 + b.phase * 2;
		BossFx.warn(level, mob, b.def, "is drawing you in!");
		BossFx.sound(level, mob.position(), SoundEvents.ENDERMAN_STARE, 1.4f, 0.7f);
		for (int t = 2; t <= 12; t += 2) {
			final int step = t;
			Scheduler.later(step, () -> {
				if (live(b, mob)) BossFx.ring(level, mob.position(), radius * (1.0 - step / 14.0), ParticleTypes.REVERSE_PORTAL, 24);
			});
		}
		Scheduler.later(14, () -> {
			if (!live(b, mob)) return;
			Vec3 c = mob.position();
			for (ServerPlayer p : BossFx.players(level, c, radius)) {
				Vec3 d = c.subtract(p.position());
				Vec3 h = new Vec3(d.x, 0, d.z);
				if (h.lengthSqr() < 4.0) continue;
				h = h.normalize().scale(1.25);
				p.setDeltaMovement(h.x, 0.35, h.z);
				p.hurtMarked = true;
			}
			BossFx.sound(level, c, SoundEvents.ENDERMAN_TELEPORT, 1.4f, 0.5f);
		});
	}

	private static void teleport(ActiveBoss b, Mob mob, ServerLevel level, LivingEntity target) {
		Vec3 look = target.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : flat.normalize();
		Vec3 dest = target.position().subtract(flat.scale(2.5 + mob.getBbWidth() * 0.5));
		if (b.arena != null && !b.arena.containsXZ(dest.x, dest.z)) return;
		var box = mob.getBoundingBox().move(dest.subtract(mob.position()));
		if (!level.noCollision(mob, box)) return;
		BossFx.burst(level, mob.position(), ParticleTypes.PORTAL, 40, 0.8);
		BossFx.sound(level, mob.position(), SoundEvents.ENDERMAN_TELEPORT, 1.2f, 0.8f);
		mob.teleportTo(dest.x, dest.y, dest.z);
		BossFx.burst(level, dest, ParticleTypes.PORTAL, 40, 0.8);
		BossFx.sound(level, dest, SoundEvents.ENDERMAN_TELEPORT, 1.2f, 0.8f);
	}

	private static void darkness(ActiveBoss b, Mob mob, ServerLevel level) {
		double radius = 24;
		double dmg = b.damage(0.4);
		BossFx.warn(level, mob, b.def, "smothers the light!");
		BossFx.sound(level, mob.position(), SoundEvents.WARDEN_ROAR, 1.5f, 0.9f);
		Scheduler.later(14, () -> {
			if (!live(b, mob)) return;
			Vec3 c = mob.position();
			for (ServerPlayer p : BossFx.players(level, c, radius)) {
				BossFx.hurt(level, mob, p, dmg);
				p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0));
				p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0));
			}
			BossFx.ring(level, c, 6, ParticleTypes.SCULK_SOUL, 40);
			BossFx.burst(level, c, ParticleTypes.SCULK_SOUL, 60, 3);
		});
	}

	private static void witherBlast(ActiveBoss b, Mob mob, ServerLevel level) {
		double radius = 10;
		double dmg = b.damage(0.9);
		BossFx.warn(level, mob, b.def, "festers with rot!");
		BossFx.sound(level, mob.position(), SoundEvents.WITHER_AMBIENT, 1.4f, 0.6f);
		for (int t = 2; t <= 14; t += 4) {
			Scheduler.later(t, () -> {
				if (live(b, mob)) BossFx.ring(level, mob.position(), radius, ParticleTypes.SOUL_FIRE_FLAME, 30);
			});
		}
		Scheduler.later(18, () -> {
			if (!live(b, mob)) return;
			Vec3 c = mob.position();
			for (ServerPlayer p : BossFx.players(level, c, radius)) {
				BossFx.hurt(level, mob, p, dmg);
				p.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
				BossFx.knock(p, c, 0.8, 0.35);
			}
			BossFx.burst(level, c, ParticleTypes.LARGE_SMOKE, 50, radius * 0.4);
			BossFx.sound(level, c, SoundEvents.WITHER_SHOOT, 1.4f, 0.7f);
		});
	}
}
