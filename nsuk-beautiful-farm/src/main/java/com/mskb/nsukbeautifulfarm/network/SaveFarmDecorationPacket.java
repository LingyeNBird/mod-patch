package com.mskb.nsukbeautifulfarm.network;

import com.mskb.nsukbeautifulfarm.common.FarmDecorationConfig;
import com.mskb.nsukbeautifulfarm.server.FarmDecorationData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SaveFarmDecorationPacket(BlockPos farmlandBoxPos, FarmDecorationConfig config) {
    public static void encode(SaveFarmDecorationPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.farmlandBoxPos);
        buf.writeNbt(packet.config.toTag());
    }

    public static SaveFarmDecorationPacket decode(FriendlyByteBuf buf) {
        return new SaveFarmDecorationPacket(buf.readBlockPos(), FarmDecorationConfig.fromTag(buf.readNbt()));
    }

    public static void handle(SaveFarmDecorationPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() == null) {
                return;
            }
            FarmDecorationData.set(context.getSender().server, packet.farmlandBoxPos, packet.config);
            context.getSender().displayClientMessage(Component.translatable("message.nsukbeautifulfarm.saved"), false);
        });
        context.setPacketHandled(true);
    }
}
