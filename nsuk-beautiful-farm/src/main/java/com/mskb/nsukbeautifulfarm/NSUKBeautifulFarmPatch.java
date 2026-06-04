package com.mskb.nsukbeautifulfarm;

import com.mskb.nsukbeautifulfarm.client.BeautifulFarmClient;
import com.mskb.nsukbeautifulfarm.network.BeautifulFarmNetwork;
import com.mskb.nsukbeautifulfarm.server.FarmDecorationData;
import com.mskb.nsukbeautifulfarm.server.FarmDecorationServer;
import com.mskb.nsukbeautifulfarm.server.LenientFarmingServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(NSUKBeautifulFarmPatch.MOD_ID)
public final class NSUKBeautifulFarmPatch {
    public static final String MOD_ID = "nsukbeautifulfarm";

    public NSUKBeautifulFarmPatch() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        BeautifulFarmNetwork.register();
        MinecraftForge.EVENT_BUS.register(FarmDecorationData.class);
        MinecraftForge.EVENT_BUS.register(FarmDecorationServer.class);
        MinecraftForge.EVENT_BUS.register(LenientFarmingServer.class);
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> BeautifulFarmClient.register(modEventBus));
    }
}
