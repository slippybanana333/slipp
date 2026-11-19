package com.tuffsmp.boss;

import com.tuffsmp.ModItems;
import com.tuffsmp.data.State;
import com.tuffsmp.essence.Essence;
import com.tuffsmp.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Hands out boss rewards straight to participants (no loot on the ground to steal).
 * Players who were offline when the boss died get their rewards queued for their next login.
 */
public final class BossRewards {
	private BossRewards() {}

	public static void distribute(MinecraftServer server, ServerLevel level, ActiveBoss b, Vec3 pos) {
		BossDef def = b.def;
		RandomSource rng = level.getRandom();
		float total = b.totalDamage();

		if (def.xp > 0) ExperienceOrb.award(level, pos, def.xp);

		if (total <= 0) {
			announceKill(server, level, b, List.of(), pos);
			return;
		}

		List<Map.Entry<UUID, Float>> ranked = new ArrayList<>(b.damage.entrySet());
		ranked.sort((x, y) -> Float.compare(y.getValue(), x.getValue()));
		UUID top = ranked.get(0).getKey();

		List<Map.Entry<UUID, Float>> eligible = new ArrayList<>();
		for (var e : ranked) {
			if (e.getKey().equals(top) || e.getValue() / total >= def.tier.minDamageShare) eligible.add(e);
		}

		// Tier 3: exactly one Magic Essence, raffled by damage share.
		UUID magicWinner = null;
		if (def.tier == BossTier.THREE) {
			float sum = 0;
			for (var e : eligible) sum += e.getValue();
			float roll = rng.nextFloat() * sum;
			for (var e : eligible) {
				roll -= e.getValue();
				if (roll <= 0) {
					magicWinner = e.getKey();
					break;
				}
			}
			if (magicWinner == null) magicWinner = top;
		}

		for (var e : eligible) {
			UUID id = e.getKey();
			List<ItemStack> stacks = new ArrayList<>();
			rollEssence(def, rng, id.equals(top), stacks);
			for (LootEntry l : def.loot) {
				if (rng.nextDouble() <= l.chance()) {
					int n = l.min() + rng.nextInt(l.max() - l.min() + 1);
					if (n > 0) stacks.add(new ItemStack(l.item().get(), n));
				}
			}
			if (id.equals(magicWinner)) stacks.add(new ItemStack(ModItems.essence(Essence.MAGIC), 1));
			deliver(server, level, id, def, stacks, id.equals(top), id.equals(magicWinner));
		}

		announceKill(server, level, b, ranked, pos);
		if (magicWinner != null) {
			String name = b.names.getOrDefault(magicWinner, "A hero");
			Msg.broadcast(server, Msg.text("✦ " + name + " received the MAGIC ESSENCE!", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
		}
	}

	private static void rollEssence(BossDef def, RandomSource rng, boolean isTop, List<ItemStack> out) {
		if (def.tier == BossTier.THREE) {
			for (Essence e : Essence.values()) {
				if (e.level == 3) continue;
				int n = def.allEssenceMin + rng.nextInt(def.allEssenceMax - def.allEssenceMin + 1);
				out.add(new ItemStack(ModItems.essence(e), n));
			}
			return;
		}
		if (def.essence == null) return;
		int n = def.essenceMin + rng.nextInt(def.essenceMax - def.essenceMin + 1);
		if (isTop) n += def.topBonus;
		out.add(new ItemStack(ModItems.essence(def.essence), n));
		// Tier 2 sometimes also spills a little Level 1 essence.
		if (def.tier == BossTier.TWO && rng.nextDouble() < 0.35) {
			Essence extra = Essence.values()[rng.nextInt(6)];
			out.add(new ItemStack(ModItems.essence(extra), 1 + rng.nextInt(2)));
		}
	}

	private static void deliver(MinecraftServer server, ServerLevel level, UUID id, BossDef def, List<ItemStack> stacks,
								boolean top, boolean magic) {
		ServerPlayer p = server.getPlayerList().getPlayer(id);
		if (p == null) {
			for (ItemStack s : stacks) {
				State.INSTANCE.pendingRewards.computeIfAbsent(id.toString(), k -> new ArrayList<>())
						.add(new State.PendingItem(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount()));
			}
			State.INSTANCE.markDirty();
			return;
		}
		MutableComponent line = Msg.colored(def.name, def.color).copy().append(Msg.text(" defeated! Your rewards:", ChatFormatting.GOLD));
		if (top) line.append(Msg.text("  (top damage bonus)", ChatFormatting.YELLOW));
		Msg.tell(p, line);
		for (ItemStack s : stacks) {
			give(p, s);
			Msg.tell(p, Msg.text("  + " + s.getCount() + "x ", ChatFormatting.GRAY).append(s.getHoverName()));
		}
		p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.0f);
	}

	public static void give(ServerPlayer p, ItemStack stack) {
		ItemStack copy = stack.copy();
		p.getInventory().add(copy);
		if (!copy.isEmpty()) p.drop(copy, false);
	}

	/** Called on login: pays out anything earned while offline. */
	public static void deliverPending(ServerPlayer p) {
		List<State.PendingItem> items = State.INSTANCE.pendingRewards.remove(p.getUUID().toString());
		if (items == null || items.isEmpty()) return;
		Msg.tell(p, Msg.text("You earned boss rewards while you were away:", ChatFormatting.GOLD));
		for (State.PendingItem pi : items) {
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(pi.item));
			ItemStack s = new ItemStack(item, pi.count);
			give(p, s);
			Msg.tell(p, Msg.text("  + " + pi.count + "x ", ChatFormatting.GRAY).append(s.getHoverName()));
		}
		State.INSTANCE.markDirty();
	}

	private static void announceKill(MinecraftServer server, ServerLevel level, ActiveBoss b, List<Map.Entry<UUID, Float>> ranked, Vec3 pos) {
		BossDef def = b.def;
		MutableComponent head = Msg.colored("⚔ " + def.name, def.color).copy()
				.append(Msg.text(" has been slain!", ChatFormatting.GOLD));
		if (def.tier == BossTier.ONE) {
			// Silent tier: only tell people who were nearby.
			for (ServerPlayer p : level.getPlayers(pl -> pl.distanceToSqr(pos) <= 80 * 80)) Msg.tell(p, head);
			return;
		}
		Msg.broadcast(server, head);
		StringBuilder sb = new StringBuilder("Top damage: ");
		int shown = Math.min(3, ranked.size());
		for (int i = 0; i < shown; i++) {
			String name = b.names.getOrDefault(ranked.get(i).getKey(), "Someone");
			sb.append(i == 0 ? "" : ", ").append(i + 1).append(". ").append(name)
					.append(" (").append(Math.round(ranked.get(i).getValue())).append(")");
		}
		if (shown > 0) Msg.broadcast(server, Msg.text(sb.toString(), ChatFormatting.YELLOW));
		if (ranked.size() > shown) {
			Msg.broadcast(server, Msg.text("...and " + (ranked.size() - shown) + " more fought in the battle.", ChatFormatting.GRAY));
		}
	}
}
