package com.mskb.nsukmanifestplus.network;

import com.mskb.nsukmanifestplus.NSUKManifestPlusPatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ManifestPlusNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(NSUKManifestPlusPatch.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int nextPacketId;

    private ManifestPlusNetwork() {
    }

    public static void register() {
        ManifestStockCounter.register();

        CHANNEL.messageBuilder(ManifestStockRequestPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ManifestStockRequestPacket::encode)
                .decoder(ManifestStockRequestPacket::decode)
                .consumerMainThread(ManifestStockRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(ManifestStockResponsePacket.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ManifestStockResponsePacket::encode)
                .decoder(ManifestStockResponsePacket::decode)
                .consumerMainThread(ManifestStockResponsePacket::handle)
                .add();

        CHANNEL.messageBuilder(ManifestConfigRequestPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ManifestConfigRequestPacket::encode)
                .decoder(ManifestConfigRequestPacket::decode)
                .consumerMainThread(ManifestConfigRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(ManifestConfigUpdatePacket.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ManifestConfigUpdatePacket::encode)
                .decoder(ManifestConfigUpdatePacket::decode)
                .consumerMainThread(ManifestConfigUpdatePacket::handle)
                .add();

        CHANNEL.messageBuilder(ManifestConfigResponsePacket.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ManifestConfigResponsePacket::encode)
                .decoder(ManifestConfigResponsePacket::decode)
                .consumerMainThread(ManifestConfigResponsePacket::handle)
                .add();
    }

    public static void sendToServer(ManifestStockRequestPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToServer(ManifestConfigRequestPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToServer(ManifestConfigUpdatePacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToPlayer(ServerPlayer player, ManifestStockResponsePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToPlayer(ServerPlayer player, ManifestConfigResponsePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
