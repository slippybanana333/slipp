package com.gladiators.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Chat message helpers with a consistent prefix. */
public final class Msg {
	private Msg() {}

	private static MutableComponent prefix() {
		return Component.literal("[Gladiators] ").withStyle(ChatFormatting.GOLD);
	}

	public static Component info(String text) {
		return prefix().append(Component.literal(text).withStyle(ChatFormatting.YELLOW));
	}

	public static Component ok(String text) {
		return prefix().append(Component.literal(text).withStyle(ChatFormatting.GREEN));
	}

	public static Component warn(String text) {
		return prefix().append(Component.literal(text).withStyle(ChatFormatting.RED));
	}

	/** Same as {@link #info} but without the prefix, for action bar / secondary lines. */
	public static Component plain(String text) {
		return Component.literal(text).withStyle(ChatFormatting.YELLOW);
	}
}
