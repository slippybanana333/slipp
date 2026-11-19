package com.tuffsmp;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
	public static Block BOSS_ALTAR;
	public static Item BOSS_ALTAR_ITEM;

	private ModBlocks() {}

	public static void register() {
		Identifier id = Identifier.fromNamespaceAndPath(TuffSmp.MOD_ID, "boss_altar");
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
		BOSS_ALTAR = Registry.register(BuiltInRegistries.BLOCK, blockKey,
				new Block(BlockBehaviour.Properties.of().setId(blockKey)
						.strength(30.0f, 1200.0f).requiresCorrectToolForDrops()
						.lightLevel(state -> 9).sound(SoundType.AMETHYST)));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		BOSS_ALTAR_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey,
				new BlockItem(BOSS_ALTAR, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()
						.rarity(Rarity.EPIC)));
	}
}
