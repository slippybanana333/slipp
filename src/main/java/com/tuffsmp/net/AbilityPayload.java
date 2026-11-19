package com.tuffsmp.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: the player pressed an ability key. action 0 = use, 1 = cycle. */
public record AbilityPayload(byte action) implements CustomPacketPayload {
	public static final byte USE = 0;
	public static final byte CYCLE = 1;

	public static final Type<AbilityPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("tuffsmp", "ability"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AbilityPayload> CODEC =
			CustomPacketPayload.codec(AbilityPayload::write, buf -> new AbilityPayload(buf.readByte()));

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeByte(action);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
