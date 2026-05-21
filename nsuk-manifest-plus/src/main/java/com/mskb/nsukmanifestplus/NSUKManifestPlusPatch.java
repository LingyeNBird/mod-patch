package com.mskb.nsukmanifestplus;

import com.mskb.nsukmanifestplus.config.ManifestPlusConfig;
import com.mskb.nsukmanifestplus.network.ManifestPlusNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod(NSUKManifestPlusPatch.MOD_ID)
public final class NSUKManifestPlusPatch {
    public static final String MOD_ID = "nsukmanifestplus";

    public NSUKManifestPlusPatch() {
        ManifestPlusConfig.register();
        ManifestPlusNetwork.register();
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> {
            try {
                Class.forName("com.mskb.nsukmanifestplus.client.ManifestConfigScreen")
                        .getMethod("registerFactory")
                        .invoke(null);
                Class.forName("com.mskb.nsukmanifestplus.client.ManifestScreenHandler")
                        .getMethod("register")
                        .invoke(null);
            } catch (ReflectiveOperationException ignored) {
                // Keep the server-side packet handler loadable without client classes.
            }
        });
    }
}
