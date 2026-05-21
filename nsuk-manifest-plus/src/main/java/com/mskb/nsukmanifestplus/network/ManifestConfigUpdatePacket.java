package com.mskb.nsukmanifestplus.network;

import com.mskb.nsukmanifestplus.config.ManifestPlusConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class ManifestConfigUpdatePacket {
    private final ManifestPlusConfig.Settings settings;

    public ManifestConfigUpdatePacket(ManifestPlusConfig.Settings settings) {
        this.settings = settings;
    }

    public static void encode(ManifestConfigUpdatePacket packet, FriendlyByteBuf buf) {
        ManifestConfigResponsePacket.writeSettings(buf, packet.settings);
    }

    public static ManifestConfigUpdatePacket decode(FriendlyByteBuf buf) {
        return new ManifestConfigUpdatePacket(ManifestConfigResponsePacket.readSettings(buf));
    }

    public static void handle(ManifestConfigUpdatePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null && ManifestConfigRequestPacket.canEdit(player)) {
            MinecraftServer server = player.getServer();
            ManifestPlusConfig.applyAndSave(server, packet.settings);
            ManifestPlusNetwork.sendToPlayer(player, new ManifestConfigResponsePacket(ManifestPlusConfig.get(), true));
        }
        context.setPacketHandled(true);
    }
}
