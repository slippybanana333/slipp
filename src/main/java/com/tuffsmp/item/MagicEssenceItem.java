package com.tuffsmp.item;

import com.tuffsmp.Config;
import com.tuffsmp.data.State;
import com.tuffsmp.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Level 3. Sneak + right-click to channel it (a safeguard against misclicks on something that took a
 * weekly boss to earn). Channeling logs a request the owner can see with /tuff requests.
 */
public class MagicEssenceItem extends Item {
	public MagicEssenceItem(Properties props) {
		super(props);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
		if (!player.isShiftKeyDown()) {
			Msg.actionbar(sp, Msg.text("Sneak + right-click to channel the Magic Essence.", ChatFormatting.LIGHT_PURPLE));
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (!player.isCreative()) stack.shrink(1);

		State.MagicRequest req = new State.MagicRequest();
		req.id = State.INSTANCE.nextRequestId++;
		req.playerName = sp.getGameProfile().name();
		req.playerUuid = sp.getUUID().toString();
		req.timeMillis = System.currentTimeMillis();
		req.source = "magic_essence";
		State.INSTANCE.requests.add(req);
		State.INSTANCE.markDirty();

		var server = sp.level().getServer();
		sp.level().playSound(null, sp.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1f, 1.4f);
		Msg.tell(sp, Msg.text("The Magic Essence dissolves into you...", ChatFormatting.LIGHT_PURPLE));
		Msg.tell(sp, Msg.text("Tell " + Config.INSTANCE.ownerName
				+ " exactly what you want made. Your wish is request #" + req.id + ".", ChatFormatting.GOLD));
		if (server != null) {
			Msg.broadcast(server, Msg.text("✦ " + sp.getGameProfile().name()
					+ " has channeled a Magic Essence!", ChatFormatting.LIGHT_PURPLE));
			for (ServerPlayer op : server.getPlayerList().getPlayers()) {
				if (server.getPlayerList().isOp(op.nameAndId())) {
					Msg.tell(op, Msg.text("[Tuff SMP] Magic request #" + req.id + " from "
							+ sp.getGameProfile().name() + " (see /tuff requests)", ChatFormatting.GRAY));
				}
			}
		}
		return InteractionResult.SUCCESS;
	}
}
