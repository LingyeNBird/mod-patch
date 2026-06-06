package com.mskb.nsukbeautifulfarm.network;

import com.mskb.nsukbeautifulfarm.server.LenientFarmingServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record FarmWorkControlPacket(BlockPos farmlandBoxPos, Action action) {
    public enum Action {
        TOGGLE_PAUSE,
        STOP
    }

    public static void encode(FarmWorkControlPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.farmlandBoxPos);
        buf.writeEnum(packet.action);
    }

    public static FarmWorkControlPacket decode(FriendlyByteBuf buf) {
        return new FarmWorkControlPacket(buf.readBlockPos(), buf.readEnum(Action.class));
    }

    public static void handle(FarmWorkControlPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() == null) {
                return;
            }
            switch (packet.action) {
                case TOGGLE_PAUSE -> LenientFarmingServer.togglePaused(context.getSender(), packet.farmlandBoxPos);
                case STOP -> LenientFarmingServer.stopWork(context.getSender(), packet.farmlandBoxPos);
            }
        });
        context.setPacketHandled(true);
    }
}
