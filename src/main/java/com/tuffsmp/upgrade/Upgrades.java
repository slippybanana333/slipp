package com.tuffsmp.upgrade;

import com.tuffsmp.data.State;
import com.tuffsmp.essence.Ability;
import com.tuffsmp.essence.Essence;
import com.tuffsmp.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Essence investment and what it grants.
 *
 * Level 1 essences give permanent potion effects (re-applied quietly every second).
 * Level 2 essences unlock abilities and make them stronger.
 */
public final class Upgrades {
	private Upgrades() {}

	public static int invested(UUID id, Essence e) {
		State.PlayerRecord r = State.INSTANCE.playerIfPresent(id);
		return r == null ? 0 : r.invested.getOrDefault(e.id, 0);
	}

	public static int level(UUID id, Essence e) {
		return e.levelFor(invested(id, e));
	}

	/** Invests up to {@code amount} (capped at what the final tier needs). Returns how many were actually used. */
	public static int invest(ServerPlayer p, Essence e, int amount) {
		if (e.level == 3 || e.thresholds.length == 0) return 0;
		int cap = e.thresholds[e.thresholds.length - 1];
		State.PlayerRecord rec = State.INSTANCE.player(p.getUUID());
		int before = rec.invested.getOrDefault(e.id, 0);
		int accepted = Math.max(0, Math.min(amount, cap - before));
		if (accepted == 0) {
			Msg.actionbar(p, Msg.text(e.name + " is already at its maximum level.", ChatFormatting.YELLOW));
			return 0;
		}
		int after = before + accepted;
		rec.invested.put(e.id, after);
		State.INSTANCE.markDirty();

		int lvlBefore = e.levelFor(before), lvlAfter = e.levelFor(after);
		int next = nextThreshold(e, after);
		MutableComponent msg = e.displayName().copy().append(Msg.text("  " + after + (next > 0 ? "/" + next : " (MAX)"), ChatFormatting.GRAY));
		if (lvlAfter > lvlBefore) {
			if (e.level == 1) {
				msg.append(Msg.text("  — " + perkName(e) + " " + roman(lvlAfter) + " unlocked!", ChatFormatting.GREEN));
			} else {
				msg.append(Msg.text("  — " + e.ability.displayName + (lvlAfter == 1 ? " unlocked!" : " is now level " + lvlAfter + "!"), ChatFormatting.GREEN));
			}
			p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.2f);
			if (e.level == 2 && lvlBefore == 0) {
				State.PlayerRecord r = State.INSTANCE.player(p.getUUID());
				if (r.selectedAbility == null) r.selectedAbility = e.ability.id;
				Msg.tell(p, Msg.text("Press your Use Ability key (default R) to use " + e.ability.displayName
						+ ". Press V to cycle abilities.", ChatFormatting.AQUA));
			}
		} else {
			p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.0f);
		}
		Msg.tell(p, msg);
		return accepted;
	}

	private static int nextThreshold(Essence e, int invested) {
		for (int t : e.thresholds) if (invested < t) return t;
		return -1;
	}

	public static String perkName(Essence e) {
		var key = BuiltInRegistries.MOB_EFFECT.getKey(e.perkEffect.value());
		if (key == null) return "Effect";
		StringBuilder sb = new StringBuilder();
		for (String part : key.getPath().split("_")) {
			if (sb.length() > 0) sb.append(' ');
			sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return sb.toString();
	}

	public static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			default -> String.valueOf(n);
		};
	}

	/** Runs once per second: refreshes each player's permanent potion perks. */
	public static void tickPerks(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			State.PlayerRecord rec = State.INSTANCE.playerIfPresent(p.getUUID());
			if (rec == null || rec.invested.isEmpty() || !p.isAlive()) continue;
			for (Essence e : Essence.values()) {
				if (e.level != 1) continue;
				int lvl = e.levelFor(rec.invested.getOrDefault(e.id, 0));
				if (lvl <= 0) continue;
				int amp = lvl - 1;
				MobEffectInstance cur = p.getEffect(e.perkEffect);
				if (cur != null && (cur.getAmplifier() > amp || (cur.getAmplifier() == amp && cur.getDuration() > 80))) continue;
				// ambient + no particles: it's a permanent perk, not a potion
				p.addEffect(new MobEffectInstance(e.perkEffect, 200, amp, true, false, true));
			}
		}
	}

	public static List<Ability> unlocked(UUID id) {
		List<Ability> out = new ArrayList<>();
		for (Essence e : Essence.values()) {
			if (e.ability != null && level(id, e) > 0) out.add(e.ability);
		}
		return out;
	}

	public static int abilityLevel(UUID id, Ability a) {
		Essence e = Essence.forAbility(a);
		return e == null ? 0 : level(id, e);
	}

	public static Component status(ServerPlayer p) {
		MutableComponent out = Msg.text("=== Your Essence Upgrades ===", ChatFormatting.GOLD, ChatFormatting.BOLD);
		for (Essence e : Essence.values()) {
			if (e.level == 3) continue;
			int inv = invested(p.getUUID(), e);
			int lvl = e.levelFor(inv);
			int next = nextThreshold(e, inv);
			String what = e.level == 1
					? (lvl > 0 ? perkName(e) + " " + roman(lvl) : "no perk yet")
					: (lvl > 0 ? e.ability.displayName + " Lv." + lvl : e.ability.displayName + " locked");
			out.append(Component.literal("\n")).append(e.displayName()).append(
					Msg.text("  " + inv + (next > 0 ? "/" + next : " (MAX)") + "  — " + what,
							lvl > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		}
		return out;
	}
}
