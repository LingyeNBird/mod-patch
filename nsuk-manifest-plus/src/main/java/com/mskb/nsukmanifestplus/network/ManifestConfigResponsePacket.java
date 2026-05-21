package com.mskb.nsukmanifestplus.network;

import com.mskb.nsukmanifestplus.config.ManifestPlusConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class ManifestConfigResponsePacket {
    private final ManifestPlusConfig.Settings settings;
    private final boolean editable;

    public ManifestConfigResponsePacket(ManifestPlusConfig.Settings settings, boolean editable) {
        this.settings = settings.sanitized();
        this.editable = editable;
    }

    public static void encode(ManifestConfigResponsePacket packet, FriendlyByteBuf buf) {
        writeSettings(buf, packet.settings);
        buf.writeBoolean(packet.editable);
    }

    public static ManifestConfigResponsePacket decode(FriendlyByteBuf buf) {
        ManifestPlusConfig.Settings settings = readSettings(buf);
        return new ManifestConfigResponsePacket(settings, buf.readBoolean());
    }

    public static void handle(ManifestConfigResponsePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            try {
                Class.forName("com.mskb.nsukmanifestplus.client.ManifestConfigScreen")
                        .getMethod("handleServerSettings", ManifestPlusConfig.Settings.class, boolean.class)
                        .invoke(null, packet.settings, packet.editable);
            } catch (ReflectiveOperationException ignored) {
                // Config screen may not be open.
            }
        });
        contextSupplier.get().setPacketHandled(true);
    }

    static void writeSettings(FriendlyByteBuf buf, ManifestPlusConfig.Settings settings) {
        ManifestPlusConfig.Settings value = settings.sanitized();
        buf.writeVarInt(value.maxBlockPositionsPerTick);
        buf.writeVarInt(value.maxContainerSlotsPerTick);
        buf.writeVarInt(value.maxTasksPerTick);
        buf.writeVarInt(value.cacheTtlTicks);
        buf.writeVarInt(value.progressIntervalTicks);
        buf.writeVarInt(value.minProgressTicks);
    }

    static ManifestPlusConfig.Settings readSettings(FriendlyByteBuf buf) {
        return new ManifestPlusConfig.Settings(
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt()
        ).sanitized();
    }
}
