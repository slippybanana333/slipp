package com.gladiators.event;

import com.gladiators.GladiatorsMod;
import com.gladiators.arena.ArenaRegion;
import com.gladiators.config.GladiatorsConfig.ArenaData;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Arena protection while an event runs. The only blocks anyone may place or break are the block items that
 * appear in kits.json (rails, redstone blocks...). Explosions do no block damage (see ServerExplosionMixin).
 */
public final class Protection {
	private Protection() {}

	public static void register() {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
			!guarded(level, pos) || GladiatorsMod.kits().kitBlocks().contains(state.getBlock()));

		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
			guarded(level, pos) && !GladiatorsMod.kits().kitBlocks().contains(level.getBlockState(pos).getBlock())
				? InteractionResult.FAIL : InteractionResult.PASS);

		UseBlockCallback.EVENT.register(Protection::onUseBlock);
	}

	/** Called from the explosion mixin: true if this explosion must not touch blocks. */
	public static boolean protectsExplosion(ServerLevel level, Vec3 center) {
		EventManager events = GladiatorsMod.events();
		if (events == null || !events.isActive()) {
			return false;
		}
		ArenaData arena = events.current().arena;
		return ArenaRegion.sameDimension(arena, level) && ArenaRegion.contains(arena, center, 10.0);
	}

	private static boolean guarded(Level level, BlockPos pos) {
		EventManager events = GladiatorsMod.events();
		if (events == null || !events.isActive() || level.isClientSide()) {
			return false;
		}
		ArenaData arena = events.current().arena;
		return ArenaRegion.sameDimension(arena, level) && ArenaRegion.contains(arena, pos, 2.0);
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (!guarded(level, hit.getBlockPos())) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (stack.isEmpty()) {
			return InteractionResult.PASS;
		}
		if (!GladiatorsMod.kits().kitItems().contains(stack.getItem())) {
			return InteractionResult.FAIL; // anything that is not part of a kit may not be used on blocks
		}
		if (stack.getItem() instanceof BlockItem && player instanceof ServerPlayer) {
			// remember where kit blocks go so they can be removed when the event ends
			BlockPos placedAt = new BlockPlaceContext(player, hand, stack, hit).getClickedPos();
			GladiatorsMod.events().current().placedBlocks.add(placedAt.asLong());
		}
		return InteractionResult.PASS;
	}
}
