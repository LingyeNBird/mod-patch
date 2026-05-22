package com.mskb.sbpinventoryswitch.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mskb.sbpinventoryswitch.config.SBPInventorySwitchConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class BackpackScreenKeyHandler {
    private static final String BACKPACK_SCREEN = "net.p3pp3rf1y.sophisticatedbackpacks.client.gui.BackpackScreen";
    private static final String KEYBIND_HANDLER = "net.p3pp3rf1y.sophisticatedbackpacks.client.KeybindHandler";
    private static final String BACKPACK_OPEN_MESSAGE = "net.p3pp3rf1y.sophisticatedbackpacks.network.BackpackOpenMessage";
    private static final String PACKET_HANDLER = "net.p3pp3rf1y.sophisticatedbackpacks.network.SBPPacketHandler";

    private static KeyMapping backpackOpenKey;
    private static Constructor<?> backpackOpenMessageConstructor;
    private static Object packetHandler;
    private static Method sendToServer;

    private BackpackScreenKeyHandler() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, BackpackScreenKeyHandler::onScreenKeyPressed);
    }

    private static void onScreenKeyPressed(ScreenEvent.KeyPressed.Post event) {
        Screen screen = event.getScreen();
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (!SBPInventorySwitchConfig.isEnabled()) {
            return;
        }
        if (isTextInputFocused(screen)) {
            return;
        }

        if (screen instanceof InventoryScreen && matchesBackpackOpenKey(event.getKeyCode(), event.getScanCode())) {
            if (sendBackpackOpenMessage()) {
                event.setCanceled(true);
            }
            return;
        }

        if (isBackpackScreen(screen) && minecraft.options.keyInventory.matches(event.getKeyCode(), event.getScanCode())) {
            event.setCanceled(true);
            player.closeContainer();
            minecraft.setScreen(new InventoryScreen(player));
        }
    }

    private static boolean isTextInputFocused(Screen screen) {
        GuiEventListener focused = screen.getFocused();
        if (focused == null) {
            return false;
        }
        if (focused instanceof EditBox editBox) {
            return editBox.isFocused();
        }
        return false;
    }

    private static boolean matchesBackpackOpenKey(int keyCode, int scanCode) {
        try {
            KeyMapping keyMapping = backpackOpenKey();
            InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
            return keyMapping.matches(keyCode, scanCode) || keyMapping.isActiveAndMatches(key);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean sendBackpackOpenMessage() {
        try {
            Object message = backpackOpenMessageConstructor().newInstance();
            sendToServerMethod().invoke(packetHandler(), message);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean isBackpackScreen(Screen screen) {
        Class<?> screenClass = screen.getClass();
        while (screenClass != null) {
            if (BACKPACK_SCREEN.equals(screenClass.getName())) {
                return true;
            }
            screenClass = screenClass.getSuperclass();
        }
        return false;
    }

    private static KeyMapping backpackOpenKey() throws ReflectiveOperationException {
        if (backpackOpenKey == null) {
            Field field = Class.forName(KEYBIND_HANDLER).getField("BACKPACK_OPEN_KEYBIND");
            backpackOpenKey = (KeyMapping) field.get(null);
        }
        return backpackOpenKey;
    }

    private static Constructor<?> backpackOpenMessageConstructor() throws ReflectiveOperationException {
        if (backpackOpenMessageConstructor == null) {
            backpackOpenMessageConstructor = Class.forName(BACKPACK_OPEN_MESSAGE).getConstructor();
        }
        return backpackOpenMessageConstructor;
    }

    private static Object packetHandler() throws ReflectiveOperationException {
        if (packetHandler == null) {
            Field field = Class.forName(PACKET_HANDLER).getField("INSTANCE");
            packetHandler = field.get(null);
        }
        return packetHandler;
    }

    private static Method sendToServerMethod() throws ReflectiveOperationException {
        if (sendToServer == null) {
            sendToServer = packetHandler().getClass().getMethod("sendToServer", Object.class);
        }
        return sendToServer;
    }
}
