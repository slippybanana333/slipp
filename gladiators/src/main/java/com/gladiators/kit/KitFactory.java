package com.gladiators.kit;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Turns kit definitions into item stacks and hands them to players. */
public final class KitFactory {
	private KitFactory() {}

	/** One resolved kit entry: the stack plus a target inventory index (-1 = next free slot). */
	public record Entry(ItemStack stack, int slot) {}

	/** Builds the stacks for a kit. Problems (bad ids, bad components) are appended to {@code warnings}. */
	public static List<Entry> build(String kitName, KitDefinition kit, HolderLookup.Provider registries, List<String> warnings) {
		List<Entry> out = new ArrayList<>();
		for (KitDefinition.Item def : kit.items) {
			ItemStack stack = buildStack(kitName, def, registries, warnings);
			if (stack != null) {
				out.add(new Entry(stack, resolveSlot(kitName, def, stack, warnings)));
			}
		}
		return out;
	}

	/** Clears the inventory and gives the kit. Anything that does not fit is dropped at the player's feet. */
	public static void give(ServerPlayer player, List<Entry> entries) {
		Inventory inv = player.getInventory();
		inv.clearContent();
		for (Entry entry : entries) {
			ItemStack stack = entry.stack().copy();
			if (entry.slot() >= 0 && inv.getItem(entry.slot()).isEmpty()) {
				inv.setItem(entry.slot(), stack);
			} else if (!inv.add(stack)) {
				player.drop(stack, false);
			}
		}
		inv.setSelectedSlot(0);
	}

	private static ItemStack buildStack(String kit, KitDefinition.Item def, HolderLookup.Provider registries, List<String> warnings) {
		Identifier id = def.id == null ? null : Identifier.tryParse(def.id);
		Holder<Item> item = id == null ? null : BuiltInRegistries.ITEM.get(id).orElse(null);
		if (item == null) {
			warnings.add("kit '" + kit + "': unknown item '" + def.id + "' (skipped)");
			return null;
		}
		DataComponentPatch patch = parseComponents(kit, def, registries, warnings);
		int count = Math.max(1, Math.min(def.count, 2304));
		ItemStack stack = new ItemStack(item, count, patch);

		if (def.enchantments != null) {
			for (Map.Entry<String, Integer> e : def.enchantments.entrySet()) {
				Identifier enchId = Identifier.tryParse(e.getKey());
				Holder<Enchantment> ench = enchId == null ? null : registries.lookupOrThrow(Registries.ENCHANTMENT)
					.get(ResourceKey.create(Registries.ENCHANTMENT, enchId)).orElse(null);
				if (ench == null) {
					warnings.add("kit '" + kit + "': unknown enchantment '" + e.getKey() + "' on " + def.id + " (skipped)");
				} else if (e.getValue() == null || e.getValue() < 1) {
					warnings.add("kit '" + kit + "': bad level for " + e.getKey() + " on " + def.id + " (skipped)");
				} else {
					stack.enchant(ench, e.getValue());
				}
			}
		}
		return stack;
	}

	private static DataComponentPatch parseComponents(String kit, KitDefinition.Item def, HolderLookup.Provider registries, List<String> warnings) {
		JsonObject json = def.components;
		if (json == null || json.isEmpty()) {
			return DataComponentPatch.EMPTY;
		}
		var ops = registries.createSerializationContext(JsonOps.INSTANCE);
		return DataComponentPatch.CODEC.parse(ops, json).resultOrPartial(err ->
			warnings.add("kit '" + kit + "': bad components on " + def.id + ": " + err)).orElse(DataComponentPatch.EMPTY);
	}

	private static int resolveSlot(String kit, KitDefinition.Item def, ItemStack stack, List<String> warnings) {
		if (def.slot != null) {
			EquipmentSlot named = switch (def.slot.toLowerCase()) {
				case "head" -> EquipmentSlot.HEAD;
				case "chest" -> EquipmentSlot.CHEST;
				case "legs" -> EquipmentSlot.LEGS;
				case "feet" -> EquipmentSlot.FEET;
				case "offhand" -> EquipmentSlot.OFFHAND;
				default -> null;
			};
			if (named != null) {
				return inventoryIndex(named);
			}
			try {
				int index = Integer.parseInt(def.slot.trim());
				if (index >= 0 && index < Inventory.INVENTORY_SIZE) {
					return index;
				}
			} catch (NumberFormatException ignored) {
				// reported below
			}
			warnings.add("kit '" + kit + "': bad slot '" + def.slot + "' on " + def.id + " (using next free slot)");
			return -1;
		}
		// No slot given: wear armor automatically.
		var equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable != null) {
			EquipmentSlot slot = equippable.slot();
			if (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET) {
				return inventoryIndex(slot);
			}
		}
		return -1;
	}

	private static int inventoryIndex(EquipmentSlot slot) {
		for (var entry : Inventory.EQUIPMENT_SLOT_MAPPING.int2ObjectEntrySet()) {
			if (entry.getValue() == slot) {
				return entry.getIntKey();
			}
		}
		return -1;
	}
}
