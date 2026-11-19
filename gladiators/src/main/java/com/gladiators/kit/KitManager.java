package com.gladiators.kit;

import com.gladiators.GladiatorsMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads kits.json (writing the defaults on first run) and answers kit lookups. */
public final class KitManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String DEFAULTS = "/data/gladiators/default_kits.json";

	private final Path file;
	private Map<String, KitDefinition> kits = new LinkedHashMap<>();
	private Set<Item> kitItems = Set.of();
	private Set<Block> kitBlocks = Set.of();
	private boolean usesFluids;

	public KitManager(Path configDir) {
		this.file = configDir.resolve("kits.json");
	}

	/** (Re)reads kits.json. Returns an error text, or null on success. On error the old kits stay active. */
	public String load() {
		try {
			Files.createDirectories(file.getParent());
			if (!Files.exists(file)) {
				try (InputStream in = KitManager.class.getResourceAsStream(DEFAULTS)) {
					if (in == null) {
						return "default kits resource missing from the jar";
					}
					Files.copy(in, file);
				}
			}
			JsonObject root;
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				root = GSON.fromJson(reader, JsonObject.class);
			}
			if (root == null || !root.has("kits")) {
				return "kits.json needs a top-level \"kits\" object";
			}
			Map<String, KitDefinition> parsed = GSON.fromJson(root.get("kits"),
				new TypeToken<LinkedHashMap<String, KitDefinition>>() {}.getType());
			parsed.keySet().removeIf(name -> !name.matches("[a-z0-9_]+"));
			kits = parsed;
			indexItems();
			return null;
		} catch (Exception e) {
			GladiatorsMod.LOGGER.error("Could not read {}: {}", file, e.toString());
			return "kits.json is invalid: " + e.getMessage();
		}
	}

	public List<String> names() {
		return new ArrayList<>(kits.keySet());
	}

	public KitDefinition get(String name) {
		return kits.get(name);
	}

	/** Every item used by any kit. Only these may be used on blocks during an event. */
	public Set<Item> kitItems() {
		return kitItems;
	}

	/** Blocks that kits can place; the only blocks players may place or break during an event. */
	public Set<Block> kitBlocks() {
		return kitBlocks;
	}

	/** True if any kit contains a bucket, so the arena is swept for leftover water/lava after an event. */
	public boolean usesFluids() {
		return usesFluids;
	}

	private void indexItems() {
		boolean fluids = false;
		Set<Item> items = new HashSet<>();
		Set<Block> blocks = new HashSet<>();
		for (KitDefinition kit : kits.values()) {
			for (KitDefinition.Item entry : kit.items) {
				Identifier id = entry.id == null ? null : Identifier.tryParse(entry.id);
				var found = BuiltInRegistries.ITEM.getOptional(id);
				if (found.isPresent()) {
					Item item = found.get();
					items.add(item);
					if (item instanceof BlockItem blockItem) {
						blocks.add(blockItem.getBlock());
					}
					fluids |= item instanceof BucketItem;
				}
			}
		}
		usesFluids = fluids;
		kitItems = Collections.unmodifiableSet(items);
		kitBlocks = Collections.unmodifiableSet(blocks);
	}
}
