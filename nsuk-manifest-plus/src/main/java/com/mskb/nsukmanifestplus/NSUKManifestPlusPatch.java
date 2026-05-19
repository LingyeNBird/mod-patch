package com.mskb.nsukmanifestplus;

import com.mskb.nsukmanifestplus.client.ManifestScreenHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod(NSUKManifestPlusPatch.MOD_ID)
public final class NSUKManifestPlusPatch {
    public static final String MOD_ID = "nsukmanifestplus";

    public NSUKManifestPlusPatch() {
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ManifestScreenHandler::register);
    }
}
