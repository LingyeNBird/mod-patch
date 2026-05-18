package com.mskb.nsukpreviewtoggle;

import com.mskb.nsukpreviewtoggle.client.ClientKeyHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(NSUKPreviewTogglePatch.MOD_ID)
public final class NSUKPreviewTogglePatch {
    public static final String MOD_ID = "nsukpreviewtoggle";

    public NSUKPreviewTogglePatch() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> ClientKeyHandler.register(modEventBus));
    }
}
