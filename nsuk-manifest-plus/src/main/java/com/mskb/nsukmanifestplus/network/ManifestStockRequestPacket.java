package com.mskb.nsukmanifestplus.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ManifestStockRequestPacket {
    private static final int MAX_ITEM_IDS = 512;

    private final long requestId;
    private final long sourcePos;
    private final String sourceType;
    private final List<String> itemIds;

    public ManifestStockRequestPacket(long requestId, long sourcePos, String sourceType, List<String> itemIds) {
        this.requestId = requestId;
        this.sourcePos = sourcePos;
        this.sourceType = sourceType == null ? "" : sourceType;
        this.itemIds = List.copyOf(itemIds == null ? List.of() : itemIds);
    }

    public static void encode(ManifestStockRequestPacket packet, FriendlyByteBuf buf) {
        buf.writeLong(packet.requestId);
        buf.writeLong(packet.sourcePos);
        buf.writeUtf(packet.sourceType);
        buf.writeVarInt(Math.min(packet.itemIds.size(), MAX_ITEM_IDS));
        for (int i = 0; i < packet.itemIds.size() && i < MAX_ITEM_IDS; i++) {
            buf.writeUtf(packet.itemIds.get(i));
        }
    }

    public static ManifestStockRequestPacket decode(FriendlyByteBuf buf) {
        long requestId = buf.readLong();
        long sourcePos = buf.readLong();
        String sourceType = buf.readUtf();
        int size = Math.min(buf.readVarInt(), MAX_ITEM_IDS);
        List<String> itemIds = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            itemIds.add(buf.readUtf());
        }
        return new ManifestStockRequestPacket(requestId, sourcePos, sourceType, itemIds);
    }

    public static void handle(ManifestStockRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            ServerLevel level = player.serverLevel();
            BlockPos pos = BlockPos.of(packet.sourcePos);
            ManifestStockCounter.request(player, level, pos, packet.sourceType, packet.itemIds, packet.requestId);
        }
        context.setPacketHandled(true);
    }
}
