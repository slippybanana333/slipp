package com.gladiators.kit;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** One kit as written in kits.json. Plain data; turned into item stacks by {@link KitFactory}. */
public final class KitDefinition {
	public String description = "";
	public List<Item> items = new ArrayList<>();

	public static final class Item {
		/** Item id, e.g. "minecraft:iron_sword". */
		public String id;
		public int count = 1;
		/** "head", "chest", "legs", "feet", "offhand" or 0-35. Optional. */
		public String slot;
		/** Enchantment id -> level. Optional. */
		public Map<String, Integer> enchantments;
		/** Raw item components (same JSON as in /give). Optional. */
		public JsonObject components;
	}
}
