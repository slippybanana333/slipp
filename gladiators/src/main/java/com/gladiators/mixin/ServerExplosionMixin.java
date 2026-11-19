package com.gladiators.mixin;

import com.gladiators.event.Protection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Server-side only. During an event, explosions near the arena (TNT minecarts!) still hurt players but do not
 * destroy or ignite blocks, so the colosseum survives cart PvP.
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@Shadow
	@Final
	private Vec3 center;

	@Inject(method = "interactsWithBlocks", at = @At("RETURN"), cancellable = true)
	private void gladiators$keepBlocks(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && Protection.protectsExplosion(level, center)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "createFire", at = @At("HEAD"), cancellable = true)
	private void gladiators$noFire(List<BlockPos> positions, CallbackInfo ci) {
		if (Protection.protectsExplosion(level, center)) {
			ci.cancel();
		}
	}
}
