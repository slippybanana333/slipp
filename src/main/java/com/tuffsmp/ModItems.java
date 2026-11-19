package com.tuffsmp;

import com.tuffsmp.boss.BossDef;
import com.tuffsmp.boss.BossRegistry;
import com.tuffsmp.essence.Essence;
import com.tuffsmp.item.EssenceItem;
import com.tuffsmp.item.MagicEssenceItem;
import com.tuffsmp.item.RuneItem;
import com.tuffsmp.item.SigilItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class ModItems {
	private static final Map<Essence, Item> ESSENCES = new HashMap<>();
	private static final Map<String, Item> SIGILS = new HashMap<>();
	private static final Map<String, Item> RUNES = new HashMap<>();
	private static final List<Item> ALL = new ArrayList<>();

	private ModItems() {}

	public static Item essence(Essence e) {
		return ESSENCES.get(e);
	}

	public static Item sigil(String bossId) {
		return SIGILS.get(bossId);
	}

	public static Item rune(String bossId) {
		return RUNES.get(bossId);
	}

	private static ItemLore lore(String... lines) {
		List<Component> out = new ArrayList<>();
		for (String l : lines) {
			out.add(Component.literal(l).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)));
		}
		return new ItemLore(out);
	}

	private static Item register(String path, Function<Item.Properties, Item> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(TuffSmp.MOD_ID, path));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
		ALL.add(item);
		return item;
	}

	public static void register() {
		for (Essence e : Essence.values()) {
			if (e.level == 3) continue;
			String lore2 = e.level == 1
					? "Level 1 Essence — " + describePerk(e)
					: "Level 2 Essence — unlocks the " + e.ability.displayName + " ability";
			Item.Properties p = new Item.Properties()
					.rarity(e.level == 1 ? Rarity.UNCOMMON : Rarity.RARE)
					.component(DataComponents.LORE, lore(lore2,
							"Right-click to invest one. Sneak + right-click to invest the stack."));
			if (e.level == 2) p = p.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			ESSENCES.put(e, register(e.itemId(), props -> new EssenceItem(props, e), p));
		}

		Item magic = register("magic_essence", MagicEssenceItem::new, new Item.Properties()
				.rarity(Rarity.EPIC).stacksTo(16)
				.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
				.component(DataComponents.LORE, lore("Level 3 Essence — dropped by weekly bosses.",
						"Sneak + right-click to channel it, then DM the owner",
						"with exactly what you want made.")));
		ESSENCES.put(Essence.MAGIC, magic);

		for (BossDef d : BossRegistry.TIER1) {
			Item.Properties p = new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(16)
					.component(DataComponents.LORE, lore("Summons " + d.name + ".",
							"Right-click the ground. Silent — no announcement."));
			SIGILS.put(d.id, register(d.id + "_sigil", props -> new SigilItem(props, d), p));
		}
		for (BossDef d : BossRegistry.TIER2) {
			Item.Properties p = new Item.Properties().rarity(Rarity.RARE).stacksTo(16)
					.component(DataComponents.LORE, lore("Offering for " + d.name + ".",
							"Right-click a Boss Altar. The whole server is told,",
							"and the boss rises after a countdown."));
			RUNES.put(d.id, register(d.id + "_rune", props -> new RuneItem(props, d), p));
		}

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(entries -> {
			for (Item i : ALL) entries.accept(i);
			entries.accept(ModBlocks.BOSS_ALTAR_ITEM);
		});
	}

	private static String describePerk(Essence e) {
		String n = BuiltInRegistries.MOB_EFFECT.getKey(e.perkEffect.value()) == null ? "an effect"
				: BuiltInRegistries.MOB_EFFECT.getKey(e.perkEffect.value()).getPath().replace('_', ' ');
		return "permanent " + n;
	}
}
