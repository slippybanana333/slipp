package com.tuffsmp.item;

import com.tuffsmp.essence.Essence;
import com.tuffsmp.upgrade.Upgrades;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Level 1 / Level 2 essence. Right-click to invest one; sneak + right-click to invest the whole stack. */
public class EssenceItem extends Item {
	public final Essence essence;

	public EssenceItem(Properties props, Essence essence) {
		super(props);
		this.essence = essence;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
		ItemStack stack = player.getItemInHand(hand);
		int amount = player.isShiftKeyDown() ? stack.getCount() : 1;
		int used = Upgrades.invest(sp, essence, amount);
		if (!player.isCreative()) stack.shrink(used);
		return InteractionResult.SUCCESS;
	}
}
