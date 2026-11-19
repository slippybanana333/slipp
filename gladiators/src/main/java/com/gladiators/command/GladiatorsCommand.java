package com.gladiators.command;

import com.gladiators.GladiatorsMod;
import com.gladiators.arena.ArenaManager;
import com.gladiators.event.EventManager;
import com.gladiators.util.Msg;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** /gladiators ... (op level 2). */
public final class GladiatorsCommand {
	private GladiatorsCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("gladiators")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("arena")
				.then(Commands.literal("build").executes(ctx -> buildArena(ctx.getSource())))
				.then(Commands.literal("remove").executes(ctx -> removeArena(ctx.getSource()))))
			.then(Commands.literal("start").executes(ctx -> start(ctx.getSource())))
			.then(Commands.literal("end").executes(ctx -> end(ctx.getSource())))
			.then(Commands.literal("kit")
				.then(Commands.argument("kit", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(GladiatorsMod.kits().names(), builder))
					.executes(ctx -> kit(ctx, null))
					.then(Commands.argument("players", EntityArgument.players())
						.executes(ctx -> kit(ctx, EntityArgument.getPlayers(ctx, "players"))))))
			.then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource())))
			.then(Commands.literal("status").executes(ctx -> status(ctx.getSource()))));
	}

	private static int buildArena(CommandSourceStack source) {
		if (GladiatorsMod.events().isActive()) {
			return fail(source, "End the running event before changing the arena.");
		}
		String error = GladiatorsMod.arenas().build(source);
		return error == null ? 1 : fail(source, error);
	}

	private static int removeArena(CommandSourceStack source) {
		if (GladiatorsMod.events().isActive()) {
			return fail(source, "End the running event before changing the arena.");
		}
		String error = GladiatorsMod.arenas().remove(source);
		return error == null ? 1 : fail(source, error);
	}

	private static int start(CommandSourceStack source) {
		String error = GladiatorsMod.events().start();
		return error == null ? 1 : fail(source, error);
	}

	private static int end(CommandSourceStack source) {
		String error = GladiatorsMod.events().end();
		return error == null ? 1 : fail(source, error);
	}

	private static int kit(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
		CommandSourceStack source = ctx.getSource();
		List<String> warnings = new ArrayList<>();
		String result = GladiatorsMod.events().giveKit(StringArgumentType.getString(ctx, "kit"), targets, warnings);
		warnings.forEach(w -> source.sendFailure(Msg.warn(w)));
		if (result.startsWith("Gave")) {
			source.sendSuccess(() -> Msg.ok(result), true);
			return 1;
		}
		return fail(source, result);
	}

	private static int reload(CommandSourceStack source) {
		String configError = GladiatorsMod.config().load();
		String kitError = GladiatorsMod.kits().load();
		if (configError != null || kitError != null) {
			return fail(source, configError != null ? configError : kitError);
		}
		List<String> warnings = GladiatorsMod.validateKits(source.getServer());
		warnings.forEach(w -> source.sendFailure(Msg.warn(w)));
		source.sendSuccess(() -> Msg.ok("Reloaded config.json and kits.json (" + GladiatorsMod.kits().names().size()
			+ " kits: " + String.join(", ", GladiatorsMod.kits().names()) + ")."), true);
		return 1;
	}

	private static int status(CommandSourceStack source) {
		ArenaManager arenas = GladiatorsMod.arenas();
		EventManager events = GladiatorsMod.events();
		String arena = "Arena: " + arenas.status() + (arenas.busy() ? " (job running)" : "");
		String event = events.isActive()
			? "Event: running, " + events.current().aliveCount() + " fighter(s) alive, " + events.current().participants.size() + " participant(s)"
			: "Event: not running";
		String pending = "Saved player states waiting to be restored: " + events.snapshots().allPending().size();
		source.sendSuccess(() -> Component.empty().append(Msg.info(arena)).append("\n").append(Msg.info(event)).append("\n").append(Msg.info(pending)), false);
		return 1;
	}

	private static int fail(CommandSourceStack source, String message) {
		source.sendFailure(Msg.warn(message));
		return 0;
	}
}
