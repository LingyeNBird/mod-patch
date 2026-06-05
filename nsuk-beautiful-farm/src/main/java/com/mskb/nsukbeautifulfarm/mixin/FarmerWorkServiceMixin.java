package com.mskb.nsukbeautifulfarm.mixin;

import com.mskb.nsukbeautifulfarm.server.LenientFarmingServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.xiaoliang.simukraft.job.jobs.farmer.FarmerWorkService", remap = false)
public abstract class FarmerWorkServiceMixin {
    @Inject(method = "harvestAndReplant", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsukbeautifulfarm$skipHarvestWhenChestFull(@Coerce Object npc, BlockPos farmlandBoxPos, ServerLevel level, CallbackInfo ci) {
        if (LenientFarmingServer.shouldSkipHarvestBecauseOutputFull(level, farmlandBoxPos)) {
            ci.cancel();
        }
    }

    @Redirect(method = "harvestAndReplant", at = @At(value = "INVOKE", target = "Lcom/xiaoliang/simukraft/utils/FarmlandManager;getBoundChestIfValid(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"), remap = false)
    private BlockPos nsukbeautifulfarm$useNearbyChestForHarvest(ServerLevel level, BlockPos farmlandBoxPos) {
        return LenientFarmingServer.findNearbyUsableChest(level, farmlandBoxPos);
    }

    @Redirect(method = "spawnHarvestDrops", at = @At(value = "INVOKE", target = "Lcom/xiaoliang/simukraft/world/FarmlandHiredData;getBoundChest(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"), remap = false)
    private BlockPos nsukbeautifulfarm$useNearbyChestForRightClickHarvest(BlockPos farmlandBoxPos, @Coerce Object npc, BlockPos pos, ServerLevel level, net.minecraft.world.level.block.Block block) {
        return LenientFarmingServer.findNearbyUsableChest(level, farmlandBoxPos);
    }

    @Inject(method = "spawnItemAtPos", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsukbeautifulfarm$storeHarvestDropBeforeSpawning(ServerLevel level, BlockPos pos, ItemStack stack, CallbackInfo ci) {
        if (LenientFarmingServer.storeHarvestDrop(level, pos, stack)) {
            ci.cancel();
        }
    }
}
