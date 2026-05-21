package com.mskb.nsukmanifestplus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class ManifestStockResponsePacket {
    private static final int MAX_COUNTS = 512;

    private final long requestId;
    private final Map<String, Integer> counts;
    private final boolean progress;
    private final int progressValue;
    private final int progressMax;

    public ManifestStockResponsePacket(long requestId, Map<String, Integer> counts) {
        this(requestId, counts, false, 0, 0);
    }

    public ManifestStockResponsePacket(long requestId, int progressValue, int progressMax) {
        this(requestId, Map.of(), true, progressValue, progressMax);
    }

    private ManifestStockResponsePacket(long requestId, Map<String, Integer> counts, boolean progress, int progressValue, int progressMax) {
        this.requestId = requestId;
        this.counts = Map.copyOf(counts == null ? Map.of() : counts);
        this.progress = progress;
        this.progressValue = progressValue;
        this.progressMax = progressMax;
    }

    public static void encode(ManifestStockResponsePacket packet, FriendlyByteBuf buf) {
        buf.writeLong(packet.requestId);
        buf.writeBoolean(packet.progress);
        if (packet.progress) {
            buf.writeVarInt(Math.max(0, packet.progressValue));
            buf.writeVarInt(Math.max(0, packet.progressMax));
            return;
        }
        buf.writeVarInt(Math.min(packet.counts.size(), MAX_COUNTS));
        int written = 0;
        for (Map.Entry<String, Integer> entry : packet.counts.entrySet()) {
            if (written++ >= MAX_COUNTS) {
                break;
            }
            buf.writeUtf(entry.getKey());
            buf.writeVarInt(Math.max(0, entry.getValue()));
        }
    }

    public static ManifestStockResponsePacket decode(FriendlyByteBuf buf) {
        long requestId = buf.readLong();
        boolean progress = buf.readBoolean();
        if (progress) {
            return new ManifestStockResponsePacket(requestId, Map.of(), true, buf.readVarInt(), buf.readVarInt());
        }
        int size = Math.min(buf.readVarInt(), MAX_COUNTS);
        Map<String, Integer> counts = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            counts.put(buf.readUtf(), buf.readVarInt());
        }
        return new ManifestStockResponsePacket(requestId, counts);
    }

    public static void handle(ManifestStockResponsePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            try {
                Class.forName("com.mskb.nsukmanifestplus.client.ManifestScreenHandler")
                        .getMethod(packet.progress ? "handleStockProgress" : "handleStockResponse",
                                packet.progress ? new Class<?>[]{long.class, int.class, int.class} : new Class<?>[]{long.class, Map.class})
                        .invoke(null, packet.progress
                                ? new Object[]{packet.requestId, packet.progressValue, packet.progressMax}
                                : new Object[]{packet.requestId, packet.counts});
            } catch (ReflectiveOperationException ignored) {
                // Best-effort client UI update.
            }
        });
        contextSupplier.get().setPacketHandled(true);
    }
}
