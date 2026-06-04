package com.mskb.nsukbeautifulfarm.mixin;

import com.mskb.nsukbeautifulfarm.server.LenientFarmingServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.xiaoliang.simukraft.utils.FarmlandManager", remap = false)
public abstract class FarmlandManagerMixin {
    @Inject(method = "startFarming", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsukbeautifulfarm$startLenientFarming(ServerPlayer player, BlockPos boxPos, String crop, int areaSize, CallbackInfoReturnable<Boolean> cir) {
        LenientFarmingServer.start(player, boxPos, crop, areaSize);
        cir.setReturnValue(true);
    }
}
