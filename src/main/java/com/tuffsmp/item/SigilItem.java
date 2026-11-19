package com.tuffsmp.item;

import com.tuffsmp.boss.BossDef;
import com.tuffsmp.boss.BossManager;
import com.tuffsmp.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Tier 1 summon item: awakens its boss right where you click. No announcement. */
public class SigilItem extends Item {
	public final BossDef boss;

	public SigilItem(Properties props, BossDef boss) {
		super(props);
		this.boss = boss;
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());
		if (BossManager.spawn(boss, level, pos.getCenter(), player.getGameProfile().name()) == null) {
			Msg.actionbar(player, Msg.text("Something blocks the summoning.", ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		if (!player.isCreative()) ctx.getItemInHand().shrink(1);
		return InteractionResult.SUCCESS;
	}
}
