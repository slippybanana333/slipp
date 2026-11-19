package com.tuffsmp.boss;

import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/** One possible extra drop (on top of essence). Each qualifying participant rolls every entry. */
public record LootEntry(Supplier<Item> item, int min, int max, double chance) {
	public static LootEntry of(Supplier<Item> item, int min, int max, double chance) {
		return new LootEntry(item, min, max, chance);
	}
}
