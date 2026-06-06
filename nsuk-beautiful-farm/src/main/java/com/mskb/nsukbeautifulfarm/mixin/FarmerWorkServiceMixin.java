package com.mskb.nsukbeautifulfarm.mixin;

import com.mskb.nsukbeautifulfarm.server.LenientFarmingServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.xiaoliang.simukraft.job.jobs.farmer.FarmerWorkService", remap = false)
public abstract class FarmerWorkServiceMixin {
    @Inject(method = "harvestAndReplant", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsukbeautifulfarm$harvestAndStore(@Coerce Object npc, BlockPos farmlandBoxPos, ServerLevel level, CallbackInfo ci) {
        if (LenientFarmingServer.harvestAndStore(level, farmlandBoxPos, npc)) {
            ci.cancel();
        }
    }

    @Inject(method = "boostCropGrowth", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsukbeautifulfarm$skipGrowthWhilePaused(@Coerce Object npc, BlockPos farmlandBoxPos, ServerLevel level, CallbackInfo ci) {
        if (LenientFarmingServer.isPaused(level, farmlandBoxPos)) {
            ci.cancel();
        }
    }
}
