package com.mskb.nsukbeautifulfarm.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.xiaoliang.simukraft.client.gui.FarmlandBoxScreen", remap = false)
public abstract class FarmlandBoxScreenMixin {
    @Inject(
            method = "handleStartFarming",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/network/simple/SimpleChannel;sendToServer(Ljava/lang/Object;)V",
                    shift = At.Shift.AFTER
            ),
            remap = false
    )
    private void nsukbeautifulfarm$closeAfterStartFarming(CallbackInfo ci) {
        Minecraft.getInstance().setScreen(null);
    }
}
