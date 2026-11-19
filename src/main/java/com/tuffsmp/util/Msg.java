package com.tuffsmp.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Chat helpers. All text is literal so vanilla clients (without the mod's lang file) see it fine. */
public final class Msg {
	private Msg() {}

	public static MutableComponent text(String s, ChatFormatting... f) {
		return Component.literal(s).withStyle(f);
	}

	public static MutableComponent colored(String s, int rgb) {
		return Component.literal(s).withColor(rgb);
	}

	public static void broadcast(MinecraftServer server, Component c) {
		server.getPlayerList().broadcastSystemMessage(c, false);
	}

	public static void tell(ServerPlayer p, Component c) {
		p.sendSystemMessage(c);
	}

	public static void actionbar(ServerPlayer p, Component c) {
		p.displayClientMessage(c, true);
	}

	/** Plays a sound only this player hears, at full volume wherever they are (used for server-wide alerts). */
	public static void ping(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
		p.connection.send(new ClientboundSoundPacket(Holder.direct(sound), SoundSource.MASTER,
				p.getX(), p.getY(), p.getZ(), volume, pitch, p.getRandom().nextLong()));
	}

	public static String clock(long totalSeconds) {
		long m = totalSeconds / 60, s = totalSeconds % 60;
		return String.format("%d:%02d", m, s);
	}
}
