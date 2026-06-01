package com.mskb.nsukbeautifulfarm.network;

import com.mskb.nsukbeautifulfarm.NSUKBeautifulFarmPatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class BeautifulFarmNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(NSUKBeautifulFarmPatch.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private BeautifulFarmNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SaveFarmDecorationPacket.class, SaveFarmDecorationPacket::encode, SaveFarmDecorationPacket::decode, SaveFarmDecorationPacket::handle);
    }
}
