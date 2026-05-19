package com.mskb.sbpinventoryswitch;

import com.mskb.sbpinventoryswitch.client.BackpackScreenKeyHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod(SBPInventorySwitchPatch.MOD_ID)
public final class SBPInventorySwitchPatch {
    public static final String MOD_ID = "sbpinventoryswitch";

    public SBPInventorySwitchPatch() {
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> BackpackScreenKeyHandler::register);
    }
}
