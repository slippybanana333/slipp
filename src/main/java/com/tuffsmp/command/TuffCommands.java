package com.tuffsmp.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.tuffsmp.ability.AbilityExecutor;
import com.tuffsmp.altar.AltarManager;
import com.tuffsmp.boss.ActiveBoss;
import com.tuffsmp.boss.BossDef;
import com.tuffsmp.boss.BossManager;
import com.tuffsmp.boss.BossRegistry;
import com.tuffsmp.data.State;
import com.tuffsmp.essence.Essence;
import com.tuffsmp.upgrade.Upgrades;
import com.tuffsmp.util.Msg;
import com.tuffsmp.weekly.WeeklyScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class TuffCommands {
	private static final SimpleCommandExceptionType NOT_FOUND = new SimpleCommandExceptionType(Component.literal("Unknown id."));

	private TuffCommands() {}

	public static void register(CommandDispatcher<CommandSourceStack> d) {
		// ---- Admin: /tuff ----
		LiteralArgumentBuilder<CommandSourceStack> tuff = Commands.literal("tuff")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

		tuff.then(Commands.literal("boss")
				.then(Commands.literal("spawn")
						.then(Commands.argument("id", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(BossRegistry.all().stream().map(x -> x.id), b))
								.executes(c -> spawnBoss(c.getSource(), StringArgumentType.getString(c, "id"), false))
								.then(Commands.literal("arena")
										.executes(c -> spawnBoss(c.getSource(), StringArgumentType.getString(c, "id"), true)))))
				.then(Commands.literal("list").executes(c -> listBosses(c.getSource())))
				.then(Commands.literal("killall").executes(c -> {
					int n = BossManager.killAll(c.getSource().getServer());
					c.getSource().sendSuccess(() -> Msg.text("Removed " + n + " loaded boss(es) without rewards (bosses in unloaded chunks reappear when their chunk loads).", ChatFormatting.YELLOW), true);
					return n;
				})));

		tuff.then(Commands.literal("altar")
				.then(Commands.literal("start")
						.then(Commands.argument("id", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(BossRegistry.TIER2.stream().map(x -> x.id), b))
								.executes(c -> startAltar(c.getSource(), StringArgumentType.getString(c, "id"), com.tuffsmp.Config.INSTANCE.altarCountdownSeconds))
								.then(Commands.argument("seconds", IntegerArgumentType.integer(1, 86400))
										.executes(c -> startAltar(c.getSource(), StringArgumentType.getString(c, "id"), IntegerArgumentType.getInteger(c, "seconds"))))))
				.then(Commands.literal("list").executes(c -> {
					List<String> l = AltarManager.describeAll();
					if (l.isEmpty()) c.getSource().sendSuccess(() -> Msg.text("No pending altar summonings.", ChatFormatting.GRAY), false);
					for (String s : l) c.getSource().sendSuccess(() -> Msg.text(s, ChatFormatting.GRAY), false);
					return l.size();
				}))
				.then(Commands.literal("cancelall").executes(c -> {
					int n = AltarManager.cancelAll(c.getSource().getServer());
					c.getSource().sendSuccess(() -> Msg.text("Cancelled " + n + " pending summoning(s) (runes not refunded).", ChatFormatting.YELLOW), true);
					return n;
				})));

		tuff.then(Commands.literal("weekly")
				.then(Commands.literal("status").executes(c -> {
					c.getSource().sendSuccess(WeeklyScheduler::status, false);
					return 1;
				}))
				.then(Commands.literal("setlocation").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					WeeklyScheduler.setLocation(p.level(), p.blockPosition());
					c.getSource().sendSuccess(() -> Msg.text("Weekly boss location set to your position.", ChatFormatting.GREEN), true);
					return 1;
				}))
				.then(Commands.literal("reschedule").executes(c -> {
					WeeklyScheduler.reschedule();
					c.getSource().sendSuccess(WeeklyScheduler::status, true);
					return 1;
				}))
				.then(Commands.literal("spawnnow")
						.executes(c -> weeklyNow(c.getSource(), null))
						.then(Commands.argument("id", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(BossRegistry.TIER3.stream().map(x -> x.id), b))
								.executes(c -> weeklyNow(c.getSource(), StringArgumentType.getString(c, "id"))))));

		tuff.then(Commands.literal("requests")
				.then(Commands.literal("list").executes(c -> {
					var list = State.INSTANCE.requests;
					if (list.isEmpty()) c.getSource().sendSuccess(() -> Msg.text("No Magic Essence requests.", ChatFormatting.GRAY), false);
					SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm");
					for (State.MagicRequest r : list) {
						c.getSource().sendSuccess(() -> Msg.text("#" + r.id + "  " + r.playerName + "  " + f.format(new Date(r.timeMillis)), ChatFormatting.LIGHT_PURPLE), false);
					}
					return list.size();
				}))
				.then(Commands.literal("clear")
						.then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(c -> {
							int id = IntegerArgumentType.getInteger(c, "id");
							boolean removed = State.INSTANCE.requests.removeIf(r -> r.id == id);
							State.INSTANCE.markDirty();
							c.getSource().sendSuccess(() -> Msg.text(removed ? "Request #" + id + " cleared." : "No such request.", ChatFormatting.YELLOW), true);
							return removed ? 1 : 0;
						}))));

		tuff.then(Commands.literal("invest")
				.then(Commands.argument("player", EntityArgument.player())
						.then(Commands.argument("essence", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Essence.values()).filter(e -> e.level != 3).map(e -> e.id), b))
								.then(Commands.argument("amount", IntegerArgumentType.integer(1, 1000)).executes(c -> {
									ServerPlayer p = EntityArgument.getPlayer(c, "player");
									Essence e = Essence.byId(StringArgumentType.getString(c, "essence"));
									if (e == null || e.level == 3) throw NOT_FOUND.create();
									int used = Upgrades.invest(p, e, IntegerArgumentType.getInteger(c, "amount"));
									c.getSource().sendSuccess(() -> Msg.text("Invested " + used + " " + e.name + " essence for " + p.getGameProfile().name() + ".", ChatFormatting.GREEN), true);
									return used;
								})))));

		tuff.then(Commands.literal("reset")
				.then(Commands.argument("player", EntityArgument.player()).executes(c -> {
					ServerPlayer p = EntityArgument.getPlayer(c, "player");
					State.INSTANCE.players.remove(p.getUUID().toString());
					State.INSTANCE.markDirty();
					c.getSource().sendSuccess(() -> Msg.text("Reset all essence upgrades for " + p.getGameProfile().name() + ".", ChatFormatting.YELLOW), true);
					return 1;
				})));

		d.register(tuff);

		// ---- Players: /essence, /ability ----
		d.register(Commands.literal("essence").executes(c -> {
			ServerPlayer p = c.getSource().getPlayerOrException();
			p.sendSystemMessage(Upgrades.status(p));
			return 1;
		}));

		d.register(Commands.literal("ability")
				.executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					AbilityExecutor.use(p);
					return 1;
				})
				.then(Commands.literal("use").executes(c -> {
					AbilityExecutor.use(c.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("next").executes(c -> {
					AbilityExecutor.cycle(c.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("list").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					var have = Upgrades.unlocked(p.getUUID());
					if (have.isEmpty()) {
						p.sendSystemMessage(Msg.text("No abilities unlocked. Level 2 essence unlocks them.", ChatFormatting.GRAY));
					}
					var sel = AbilityExecutor.selected(p);
					for (var a : have) {
						p.sendSystemMessage(Msg.text((a == sel ? "> " : "  ") + a.displayName + " Lv." + Upgrades.abilityLevel(p.getUUID(), a),
								a == sel ? ChatFormatting.AQUA : ChatFormatting.GRAY));
					}
					return have.size();
				})));
	}

	private static int spawnBoss(CommandSourceStack src, String id, boolean withArena) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		BossDef def = BossRegistry.get(id);
		if (def == null) throw NOT_FOUND.create();
		var level = src.getLevel();
		int radius = com.tuffsmp.arena.Arena.radiusFor(def.tier);
		if (withArena && radius > 0) {
			// Admin: build the arena here first, then spawn in it (destructive: replaces terrain in the circle).
			var arena = new com.tuffsmp.arena.Arena(net.minecraft.core.BlockPos.containing(src.getPosition()), radius);
			src.sendSuccess(() -> Msg.text("Building a radius-" + radius + " arena, then spawning " + def.name + "...", ChatFormatting.YELLOW), true);
			arena.build(level, () -> BossManager.spawn(def, level, src.getPosition(), src.getTextName(), arena));
			return 1;
		}
		Mob m = BossManager.spawn(def, level, src.getPosition(), src.getTextName());
		if (m == null) {
			src.sendFailure(Component.literal("Could not spawn " + def.name + "."));
			return 0;
		}
		src.sendSuccess(() -> Msg.text("Spawned " + def.name + " (" + def.tier.label + ").", ChatFormatting.GREEN), true);
		return 1;
	}

	/** Admin: places an altar at your feet and starts its countdown without needing a rune. */
	private static int startAltar(CommandSourceStack src, String id, int seconds) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		BossDef def = BossRegistry.get(id);
		if (def == null || def.tier != com.tuffsmp.boss.BossTier.TWO) throw NOT_FOUND.create();
		var level = src.getLevel();
		var pos = net.minecraft.core.BlockPos.containing(src.getPosition());
		level.setBlockAndUpdate(pos, com.tuffsmp.ModBlocks.BOSS_ALTAR.defaultBlockState());
		String uuid = src.getEntity() != null ? src.getEntity().getUUID().toString() : "console";
		if (!AltarManager.start(level, pos, src.getTextName(), uuid, def, seconds)) {
			src.sendFailure(Component.literal("An altar there is already awakening something."));
			return 0;
		}
		return 1;
	}

	private static int weeklyNow(CommandSourceStack src, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		BossDef def = null;
		if (id != null) {
			def = BossRegistry.get(id);
			if (def == null || def.tier != com.tuffsmp.boss.BossTier.THREE) throw NOT_FOUND.create();
		}
		boolean ok = WeeklyScheduler.spawnNow(src.getServer(), def);
		if (!ok) src.sendFailure(Component.literal("Failed to spawn the weekly boss."));
		return ok ? 1 : 0;
	}

	private static int listBosses(CommandSourceStack src) {
		List<ActiveBoss> all = new ArrayList<>(BossManager.active());
		if (all.isEmpty()) src.sendSuccess(() -> Msg.text("No active bosses.", ChatFormatting.GRAY), false);
		for (ActiveBoss b : all) src.sendSuccess(() -> BossManager.describe(b, src.getServer()), false);
		return all.size();
	}
}
