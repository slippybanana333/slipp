package com.tuffsmp.item;

import com.tuffsmp.ModBlocks;
import com.tuffsmp.altar.AltarManager;
import com.tuffsmp.boss.BossDef;
import com.tuffsmp.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Tier 2 offering: use it on a Boss Altar to start the announced summoning. */
public class RuneItem extends Item {
	public final BossDef boss;

	public RuneItem(Properties props, BossDef boss) {
		super(props);
		this.boss = boss;
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = ctx.getClickedPos();
		if (!level.getBlockState(pos).is(ModBlocks.BOSS_ALTAR)) {
			Msg.actionbar(player, Msg.text("Use this rune on a Boss Altar.", ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		if (!AltarManager.start(level, pos, player, boss)) return InteractionResult.FAIL;
		if (!player.isCreative()) ctx.getItemInHand().shrink(1);
		return InteractionResult.SUCCESS;
	}
}
