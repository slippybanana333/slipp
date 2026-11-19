package com.tuffsmp;

import com.tuffsmp.ability.AbilityExecutor;
import com.tuffsmp.altar.AltarManager;
import com.tuffsmp.boss.BossManager;
import com.tuffsmp.boss.BossRegistry;
import com.tuffsmp.boss.BossRewards;
import com.tuffsmp.command.TuffCommands;
import com.tuffsmp.data.State;
import com.tuffsmp.net.AbilityPayload;
import com.tuffsmp.upgrade.Upgrades;
import com.tuffsmp.util.Scheduler;
import com.tuffsmp.weekly.WeeklyScheduler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TuffSmp implements ModInitializer {
	public static final String MOD_ID = "tuffsmp";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Config.load();
		BossRegistry.init();
		ModBlocks.register();
		ModItems.register();

		// Ability keybinds (client -> server)
		PayloadTypeRegistry.playC2S().register(AbilityPayload.TYPE, AbilityPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(AbilityPayload.TYPE, (payload, context) -> {
			if (payload.action() == AbilityPayload.USE) AbilityExecutor.use(context.player());
			else if (payload.action() == AbilityPayload.CYCLE) AbilityExecutor.cycle(context.player());
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> TuffCommands.register(dispatcher));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			State.load(server);
			Scheduler.clear();
			BossManager.init();
			AltarManager.init(server);
			WeeklyScheduler.init(server);
			LOGGER.info("Tuff SMP season 2 loaded: {} bosses, weekly boss {}", BossRegistry.all().size(),
					Config.INSTANCE.weeklyEnabled ? "enabled" : "disabled");
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			BossManager.syncState();
			State.INSTANCE.save();
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Scheduler.tick();
			BossManager.tick(server);
			int t = server.getTickCount();
			if (t % 20 == 0) {
				AltarManager.tick(server);
				WeeklyScheduler.tick(server);
				Upgrades.tickPerks(server);
			}
			if (t % 600 == 0) {
				BossManager.syncState();
				State.INSTANCE.saveIfDirty();
			}
		});

		ServerEntityEvents.ENTITY_LOAD.register(BossManager::onEntityLoad);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(BossManager::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(BossManager::onAfterDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register(BossManager::allowDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register(BossManager::onDeath);

		// No building or digging inside an arena while its boss fight is on.
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (player.isCreative() || !BossManager.buildingLocked(level, pos)) return true;
			com.tuffsmp.util.Msg.actionbar((net.minecraft.server.level.ServerPlayer) player,
					com.tuffsmp.util.Msg.text("You can't dig during a boss fight.", net.minecraft.ChatFormatting.RED));
			return false;
		});
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			var held = player.getItemInHand(hand).getItem();
			boolean placing = held instanceof net.minecraft.world.item.BlockItem || held instanceof net.minecraft.world.item.BucketItem
					|| held == net.minecraft.world.item.Items.FLINT_AND_STEEL || held == net.minecraft.world.item.Items.FIRE_CHARGE;
			if (!placing || player.isCreative()) return net.minecraft.world.InteractionResult.PASS;
			var target = hit.getBlockPos().relative(hit.getDirection());
			if (BossManager.buildingLocked(level, hit.getBlockPos()) || BossManager.buildingLocked(level, target)) {
				if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
					com.tuffsmp.util.Msg.actionbar(sp, com.tuffsmp.util.Msg.text("You can't build during a boss fight.", net.minecraft.ChatFormatting.RED));
				}
				return net.minecraft.world.InteractionResult.FAIL;
			}
			return net.minecraft.world.InteractionResult.PASS;
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> BossRewards.deliverPending(handler.getPlayer()));
	}
}
