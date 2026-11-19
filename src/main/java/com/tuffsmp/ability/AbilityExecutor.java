package com.tuffsmp.ability;

import com.tuffsmp.boss.BossManager;
import com.tuffsmp.data.State;
import com.tuffsmp.essence.Ability;
import com.tuffsmp.upgrade.Upgrades;
import com.tuffsmp.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Level 2 upgrades: what each ability actually does. */
public final class AbilityExecutor {
	/** player -> ability -> server tick at which it's ready again. */
	private static final Map<UUID, Map<Ability, Long>> COOLDOWNS = new HashMap<>();

	private AbilityExecutor() {}

	public static Ability selected(ServerPlayer p) {
		State.PlayerRecord rec = State.INSTANCE.playerIfPresent(p.getUUID());
		List<Ability> have = Upgrades.unlocked(p.getUUID());
		if (have.isEmpty()) return null;
		Ability sel = rec == null ? null : Ability.byId(rec.selectedAbility);
		return sel != null && have.contains(sel) ? sel : have.get(0);
	}

	public static void cycle(ServerPlayer p) {
		List<Ability> have = Upgrades.unlocked(p.getUUID());
		if (have.isEmpty()) {
			Msg.actionbar(p, Msg.text("You haven't unlocked any abilities yet — invest Level 2 essence.", ChatFormatting.GRAY));
			return;
		}
		Ability cur = selected(p);
		Ability next = have.get((have.indexOf(cur) + 1) % have.size());
		State.INSTANCE.player(p.getUUID()).selectedAbility = next.id;
		State.INSTANCE.markDirty();
		p.level().playSound(null, p.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6f, 1.4f);
		Msg.actionbar(p, Msg.text("Ability: ", ChatFormatting.GRAY).append(Msg.text(next.displayName, ChatFormatting.AQUA))
				.append(Msg.text(" (Lv." + Upgrades.abilityLevel(p.getUUID(), next) + ")", ChatFormatting.GRAY)));
	}

	public static void use(ServerPlayer p) {
		Ability a = selected(p);
		if (a == null) {
			Msg.actionbar(p, Msg.text("You haven't unlocked any abilities yet — invest Level 2 essence.", ChatFormatting.GRAY));
			return;
		}
		if (!(p.level() instanceof ServerLevel level) || !p.isAlive() || p.isSpectator()) return;
		long now = level.getServer().getTickCount();
		Map<Ability, Long> cds = COOLDOWNS.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
		long ready = cds.getOrDefault(a, 0L);
		if (now < ready) {
			Msg.actionbar(p, Msg.text(a.displayName + " ready in " + String.format("%.1f", (ready - now) / 20.0) + "s", ChatFormatting.RED));
			return;
		}
		int lvl = Math.max(1, Upgrades.abilityLevel(p.getUUID(), a));
		boolean ok = switch (a) {
			case DASH -> dash(p, level, lvl);
			case LEAP -> leap(p, level, lvl);
			case SLAM -> slam(p, level, lvl);
			case BLINK -> blink(p, level, lvl);
			case OVERCHARGE -> overcharge(p, level, lvl);
		};
		if (ok && !p.isCreative()) cds.put(a, now + a.cooldownTicks(lvl));
		if (ok) Msg.actionbar(p, Msg.text(a.displayName + "!", ChatFormatting.AQUA));
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		return flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
	}

	private static boolean dash(ServerPlayer p, ServerLevel level, int lvl) {
		Vec3 dir = flatLook(p).scale(1.7 + 0.3 * (lvl - 1));
		p.setDeltaMovement(dir.x, 0.18, dir.z);
		p.hurtMarked = true;
		p.resetFallDistance();
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 14, 0.3, 0.1, 0.3, 0.05);
		level.playSound(null, p.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.0f, 1.6f);
		return true;
	}

	private static boolean leap(ServerPlayer p, ServerLevel level, int lvl) {
		Vec3 f = flatLook(p).scale(0.55 + 0.1 * lvl);
		p.setDeltaMovement(f.x, 1.05 + 0.15 * lvl, f.z);
		p.hurtMarked = true;
		p.resetFallDistance();
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, true, false, true));
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY(), p.getZ(), 20, 0.4, 0.1, 0.4, 0.05);
		level.playSound(null, p.blockPosition(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.0f, 1.0f);
		return true;
	}

	private static boolean slam(ServerPlayer p, ServerLevel level, int lvl) {
		double radius = 4.5 + lvl;
		float dmg = 8f + 3f * lvl;
		Vec3 c = p.position();
		var targets = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(radius, 2.0, radius),
				e -> e != p && e.isAlive() && !(e instanceof Player) && isHostile(e) && e.distanceToSqr(c) <= radius * radius);
		for (LivingEntity e : targets) {
			e.hurtServer(level, level.damageSources().playerAttack(p), dmg);
			Vec3 d = e.position().subtract(c);
			Vec3 h = new Vec3(d.x, 0, d.z);
			h = h.lengthSqr() < 1.0E-4 ? Vec3.ZERO : h.normalize().scale(0.9);
			e.setDeltaMovement(h.x, 0.5, h.z);
			e.hurtMarked = true;
		}
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 3, radius * 0.3, 0.1, radius * 0.3, 0.0);
		for (int i = 0; i < 40; i++) {
			double a = Math.PI * 2 * i / 40;
			level.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(a) * radius, c.y + 0.2, c.z + Math.sin(a) * radius, 1, 0, 0.05, 0, 0);
		}
		level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2f, 0.8f);
		return true;
	}

	private static boolean isHostile(LivingEntity e) {
		return e instanceof Enemy || e.getTags().contains(BossManager.TAG) || e.getTags().contains(BossManager.TAG_MINION);
	}

	private static boolean blink(ServerPlayer p, ServerLevel level, int lvl) {
		double max = 9 + 2.5 * lvl;
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		var hit = level.clip(new ClipContext(eye, eye.add(look.scale(max)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		double dist = hit.getType() == HitResult.Type.MISS ? max : Math.max(0, eye.distanceTo(hit.getLocation()) - 0.6);
		// walk back from the far end until the player's body fits
		for (double d = dist; d >= 1.0; d -= 0.5) {
			Vec3 target = p.position().add(look.scale(d));
			var box = p.getBoundingBox().move(target.subtract(p.position()));
			if (level.noCollision(p, box)) {
				level.sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 30, 0.3, 0.6, 0.3, 0.3);
				p.teleportTo(target.x, target.y, target.z);
				p.resetFallDistance();
				level.sendParticles(ParticleTypes.PORTAL, target.x, target.y + 1, target.z, 30, 0.3, 0.6, 0.3, 0.3);
				level.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.2f);
				return true;
			}
		}
		Msg.actionbar(p, Msg.text("No room to blink.", ChatFormatting.RED));
		return false;
	}

	private static boolean overcharge(ServerPlayer p, ServerLevel level, int lvl) {
		int ticks = (8 + 2 * lvl) * 20;
		p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, ticks, lvl >= 3 ? 1 : 0));
		p.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 0));
		p.addEffect(new MobEffectInstance(MobEffects.HASTE, ticks, 1));
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.8, 0.5, 0.2);
		level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.5f);
		return true;
	}
}
