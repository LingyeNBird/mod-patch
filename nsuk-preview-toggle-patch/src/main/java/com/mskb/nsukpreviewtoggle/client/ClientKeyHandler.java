package com.mskb.nsukpreviewtoggle.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mskb.nsukpreviewtoggle.NSUKPreviewTogglePatch;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

public final class ClientKeyHandler {
    private static final String KEY_CATEGORY = "key.categories." + NSUKPreviewTogglePatch.MOD_ID;
    private static final KeyMapping TOGGLE_PREVIEW = new KeyMapping(
            "key." + NSUKPreviewTogglePatch.MOD_ID + ".toggle_preview",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            KEY_CATEGORY
    );

    private ClientKeyHandler() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ClientKeyHandler::registerKeyMappings);
        MinecraftForge.EVENT_BUS.addListener(ClientKeyHandler::onClientTick);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_PREVIEW);
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        while (TOGGLE_PREVIEW.consumeClick()) {
            togglePreviewMode();
        }
    }

    private static void togglePreviewMode() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        try {
            if (!SimukraftPreviewBridge.isPreviewActive()) {
                show(player, "message.nsukpreviewtoggle.no_preview");
                return;
            }

            if (SimukraftPreviewBridge.isRangeOnlyPreview()) {
                SimukraftPreviewBridge.loadBlocks();
                show(player, "message.nsukpreviewtoggle.full_preview");
            } else {
                SimukraftPreviewBridge.activateRangeOnlyPreview();
                show(player, "message.nsukpreviewtoggle.range_preview");
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            show(player, "message.nsukpreviewtoggle.error");
        }
    }

    private static void show(LocalPlayer player, String translationKey) {
        player.displayClientMessage(Component.translatable(translationKey), true);
    }
}
