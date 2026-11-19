package com.tuffsmp.boss;

import com.tuffsmp.util.Msg;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Small shared helpers for telegraphs, damage and sound. */
final class BossFx {
	private BossFx() {}

	static void ring(ServerLevel level, Vec3 c, double radius, ParticleOptions particle, int count) {
		for (int i = 0; i < count; i++) {
			double a = (Math.PI * 2 * i) / count;
			level.sendParticles(particle, c.x + Math.cos(a) * radius, c.y + 0.15, c.z + Math.sin(a) * radius,
					1, 0, 0.05, 0, 0);
		}
	}

	static void burst(ServerLevel level, Vec3 c, ParticleOptions particle, int count, double spread) {
		level.sendParticles(particle, c.x, c.y + 0.5, c.z, count, spread, spread * 0.5, spread, 0.02);
	}

	static void sound(ServerLevel level, Vec3 p, SoundEvent s, float vol, float pitch) {
		level.playSound(null, p.x, p.y, p.z, s, SoundSource.HOSTILE, vol, pitch);
	}

	/** Survival-mode players who are alive and within the radius. */
	static List<ServerPlayer> players(ServerLevel level, Vec3 c, double radius) {
		double r2 = radius * radius;
		return level.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.distanceToSqr(c) <= r2);
	}

	static void hurt(ServerLevel level, Mob source, ServerPlayer target, double damage) {
		target.hurtServer(level, level.damageSources().mobAttack(source), (float) damage);
	}

	/** Knockback away from {@code from}, with some upward lift. */
	static void knock(ServerPlayer target, Vec3 from, double strength, double lift) {
		Vec3 d = target.position().subtract(from);
		Vec3 h = new Vec3(d.x, 0, d.z);
		h = h.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 0) : h.normalize();
		target.setDeltaMovement(h.x * strength, lift, h.z * strength);
		target.hurtMarked = true;
	}

	static void warn(ServerLevel level, Mob mob, BossDef def, String text) {
		Component c = Msg.colored("⚠ " + def.name + " " + text, def.color);
		for (ServerPlayer p : level.getPlayers(pl -> pl.distanceToSqr(mob) <= 48 * 48)) {
			Msg.actionbar(p, c);
		}
	}
}
