package com.mskb.nsukbeautifulfarm.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mskb.nsukbeautifulfarm.NSUKBeautifulFarmPatch;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

public final class BeautifulFarmClient {
    public static KeyMapping nextOption;
    public static KeyMapping previousStyle;
    public static KeyMapping nextStyle;
    public static KeyMapping previousMaterial;
    public static KeyMapping nextMaterial;
    public static KeyMapping rotate;

    private BeautifulFarmClient() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(BeautifulFarmClient::registerKeys);
        MinecraftForge.EVENT_BUS.register(FarmSelectionClientHandler.class);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        nextOption = key("next_option", GLFW.GLFW_KEY_TAB);
        previousStyle = key("previous_style", GLFW.GLFW_KEY_LEFT);
        nextStyle = key("next_style", GLFW.GLFW_KEY_RIGHT);
        previousMaterial = key("previous_material", GLFW.GLFW_KEY_UP);
        nextMaterial = key("next_material", GLFW.GLFW_KEY_DOWN);
        rotate = key("rotate", GLFW.GLFW_KEY_R);
        event.register(nextOption);
        event.register(previousStyle);
        event.register(nextStyle);
        event.register(previousMaterial);
        event.register(nextMaterial);
        event.register(rotate);
    }

    private static KeyMapping key(String name, int key) {
        return new KeyMapping("key." + NSUKBeautifulFarmPatch.MOD_ID + "." + name,
                InputConstants.Type.KEYSYM, key, "key.categories." + NSUKBeautifulFarmPatch.MOD_ID);
    }
}
