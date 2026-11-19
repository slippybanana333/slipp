package com.gladiators.state;

import com.gladiators.GladiatorsMod;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures everything the event touches on a player (position, dimension, gamemode, inventory incl. armor and
 * offhand, health, hunger, XP, effects) into NBT, and applies it back. The ender chest is never touched.
 */
public final class PlayerSnapshot {
	private PlayerSnapshot() {}

	public static CompoundTag capture(ServerPlayer player, MinecraftServer server) {
		RegistryOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		CompoundTag tag = new CompoundTag();
		tag.putInt("Version", 1);
		tag.putString("Name", player.getGameProfile().name());
		tag.putString("Dimension", player.level().dimension().identifier().toString());
		tag.putDouble("X", player.getX());
		tag.putDouble("Y", player.getY());
		tag.putDouble("Z", player.getZ());
		tag.putFloat("Yaw", player.getYRot());
		tag.putFloat("Pitch", player.getXRot());
		tag.putString("GameMode", player.gameMode().getName());
		tag.putFloat("Health", player.getHealth());
		tag.putInt("Food", player.getFoodData().getFoodLevel());
		tag.putFloat("Saturation", player.getFoodData().getSaturationLevel());
		tag.putInt("XpLevel", player.experienceLevel);
		tag.putFloat("XpProgress", player.experienceProgress);
		tag.putInt("XpTotal", player.totalExperience);

		ListTag inventory = new ListTag();
		Inventory inv = player.getInventory();
		for (int slot = 0; slot < inv.getContainerSize(); slot++) {
			ItemStack stack = inv.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			CompoundTag entry = new CompoundTag();
			entry.putInt("Slot", slot);
			entry.put("Item", ItemStack.CODEC.encodeStart(ops, stack).getOrThrow());
			inventory.add(entry);
		}
		tag.put("Inventory", inventory);

		ListTag effects = new ListTag();
		for (MobEffectInstance effect : player.getActiveEffects()) {
			effects.add(MobEffectInstance.CODEC.encodeStart(ops, effect).getOrThrow());
		}
		tag.put("Effects", effects);
		return tag;
	}

	/**
	 * Restores the saved state. Returns a list of problems (empty = everything restored). The player always
	 * ends up with as much of their state back as could be read.
	 */
	public static List<String> apply(ServerPlayer player, MinecraftServer server, CompoundTag tag) {
		List<String> problems = new ArrayList<>();
		RegistryOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);

		player.closeContainer();
		player.stopRiding();

		// Inventory (+ armor + offhand): the container indexes are the same ones we saved with.
		Inventory inv = player.getInventory();
		inv.clearContent();
		for (Tag raw : tag.getListOrEmpty("Inventory")) {
			if (!(raw instanceof CompoundTag entry)) {
				continue;
			}
			int slot = entry.getIntOr("Slot", -1);
			DataResult<ItemStack> parsed = ItemStack.CODEC.parse(ops, entry.get("Item"));
			if (slot < 0 || slot >= inv.getContainerSize() || parsed.result().isEmpty()) {
				problems.add("could not restore an item in slot " + slot);
				continue;
			}
			inv.setItem(slot, parsed.result().get());
		}

		player.removeAllEffects();
		for (Tag raw : tag.getListOrEmpty("Effects")) {
			MobEffectInstance.CODEC.parse(ops, raw).result().ifPresent(player::addEffect);
		}

		player.clearFire();
		player.setAirSupply(player.getMaxAirSupply());
		player.setHealth(Math.max(1.0F, Math.min(tag.getFloatOr("Health", 20.0F), player.getMaxHealth())));
		player.getFoodData().setFoodLevel(tag.getIntOr("Food", 20));
		player.getFoodData().setSaturation(tag.getFloatOr("Saturation", 5.0F));
		setExperience(player, tag.getIntOr("XpLevel", 0), tag.getFloatOr("XpProgress", 0.0F), tag.getIntOr("XpTotal", 0));

		// Position first, then gamemode (so a saved spectator/creative player is not moved while flying oddly).
		teleportBack(player, server, tag, problems);
		player.setGameMode(GameType.byName(tag.getStringOr("GameMode", "survival"), GameType.SURVIVAL));
		player.inventoryMenu.sendAllDataToRemote();
		return problems;
	}

	/** Puts a player into a clean fighting state (used by /gladiators start). Their real state is already on disk. */
	public static void resetForFight(ServerPlayer player) {
		player.closeContainer();
		player.stopRiding();
		player.getInventory().clearContent();
		heal(player);
		setExperience(player, 0, 0.0F, 0);
		player.setGameMode(GameType.SURVIVAL);
		player.inventoryMenu.sendAllDataToRemote();
	}

	/** Full health, hunger and clean status (used after a kit is given, and when a fighter becomes a spectator). */
	public static void heal(ServerPlayer player) {
		player.removeAllEffects();
		player.clearFire();
		player.setAirSupply(player.getMaxAirSupply());
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(5.0F);
		player.resetFallDistance();
	}

	public static void setExperience(ServerPlayer player, int level, float progress, int total) {
		player.experienceLevel = level;
		player.experienceProgress = progress;
		player.totalExperience = total;
		player.connection.send(new ClientboundSetExperiencePacket(progress, total, level));
	}

	private static void teleportBack(ServerPlayer player, MinecraftServer server, CompoundTag tag, List<String> problems) {
		Identifier dimId = Identifier.tryParse(tag.getStringOr("Dimension", "minecraft:overworld"));
		ServerLevel level = dimId == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
		if (level == null) {
			problems.add("saved dimension '" + tag.getStringOr("Dimension", "?") + "' no longer exists; position left as is");
			return;
		}
		Vec3 target = ReturnSpot.resolve(level, new Vec3(tag.getDoubleOr("X", 0), tag.getDoubleOr("Y", 64), tag.getDoubleOr("Z", 0)));
		Teleports.to(player, level, target.x, target.y, target.z, tag.getFloatOr("Yaw", 0), tag.getFloatOr("Pitch", 0));
	}
}
