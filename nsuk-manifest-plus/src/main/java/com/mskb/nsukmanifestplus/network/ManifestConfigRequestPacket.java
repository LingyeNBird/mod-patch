package com.mskb.nsukmanifestplus.network;

import com.mskb.nsukmanifestplus.config.ManifestPlusConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class ManifestConfigRequestPacket {
    public static void encode(ManifestConfigRequestPacket packet, FriendlyByteBuf buf) {
    }

    public static ManifestConfigRequestPacket decode(FriendlyByteBuf buf) {
        return new ManifestConfigRequestPacket();
    }

    public static void handle(ManifestConfigRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            ManifestPlusNetwork.sendToPlayer(player, new ManifestConfigResponsePacket(ManifestPlusConfig.get(), canEdit(player)));
        }
        context.setPacketHandled(true);
    }

    static boolean canEdit(ServerPlayer player) {
        return (player.getServer() != null && player.getServer().isSingleplayerOwner(player.getGameProfile())) || player.hasPermissions(2);
    }
}
