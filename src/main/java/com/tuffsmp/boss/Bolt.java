package com.tuffsmp.boss;

import com.tuffsmp.util.Scheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A scripted projectile: moves a point each tick, draws particles, and damages the first player it
 * touches. Purely server-side (so vanilla clients see it fine) and never sets fires or breaks blocks.
 */
final class Bolt {
	private Bolt() {}

	static void fire(ServerLevel level, Mob owner, Vec3 from, Vec3 direction, double speed, int maxTicks,
					 double damage, ParticleOptions particle, boolean ignite) {
		step(level, owner, from, direction.normalize().scale(speed), maxTicks, damage, particle, ignite);
	}

	private static void step(ServerLevel level, Mob owner, Vec3 pos, Vec3 vel, int remaining, double damage,
							 ParticleOptions particle, boolean ignite) {
		if (remaining <= 0 || owner.isRemoved()) return;
		Vec3 next = pos.add(vel);

		var hit = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
		if (hit.getType() != HitResult.Type.MISS) {
			BossFx.burst(level, hit.getLocation(), ParticleTypes.SMOKE, 6, 0.2);
			return;
		}

		for (ServerPlayer p : level.getPlayers(pl -> pl.isAlive() && !pl.isSpectator() && !pl.isCreative()
				&& pl.getBoundingBox().inflate(0.35).contains(next))) {
			BossFx.hurt(level, owner, p, damage);
			if (ignite) p.igniteForSeconds(3);
			BossFx.burst(level, next, particle, 12, 0.3);
			BossFx.sound(level, next, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.6f);
			return;
		}

		level.sendParticles(particle, next.x, next.y, next.z, 3, 0.05, 0.05, 0.05, 0.0);
		level.sendParticles(ParticleTypes.SMOKE, next.x, next.y, next.z, 1, 0.02, 0.02, 0.02, 0.0);
		Scheduler.later(1, () -> step(level, owner, next, vel, remaining - 1, damage, particle, ignite));
	}
}
