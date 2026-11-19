package com.tuffsmp.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tuffsmp.net.AbilityPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Client side: just two keybinds that tell the server to use / cycle the player's ability. */
public class TuffSmpClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("tuffsmp", "main"));
		KeyMapping use = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.tuffsmp.use_ability", InputConstants.Type.KEYSYM, InputConstants.KEY_R, category));
		KeyMapping cycle = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.tuffsmp.cycle_ability", InputConstants.Type.KEYSYM, InputConstants.KEY_V, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) return;
			while (use.consumeClick()) ClientPlayNetworking.send(new AbilityPayload(AbilityPayload.USE));
			while (cycle.consumeClick()) ClientPlayNetworking.send(new AbilityPayload(AbilityPayload.CYCLE));
		});
	}
}
