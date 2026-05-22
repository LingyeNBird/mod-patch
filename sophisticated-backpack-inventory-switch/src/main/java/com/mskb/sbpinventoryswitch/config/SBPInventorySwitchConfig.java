package com.mskb.sbpinventoryswitch.config;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SBPInventorySwitchConfig {
    private static final String FILE_NAME = "sophisticated-backpack-inventory-switch.toml";
    private static boolean enabled = true;
    private static boolean loaded;

    private SBPInventorySwitchConfig() {
    }

    public static boolean isEnabled() {
        load();
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        loaded = true;
        save();
    }

    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = configPath();
        if (!Files.exists(path)) {
            save();
            return;
        }
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("enabled") && trimmed.contains("=")) {
                    enabled = Boolean.parseBoolean(trimmed.split("=", 2)[1].trim());
                    return;
                }
            }
        } catch (IOException ignored) {
            // Default remains active if the config cannot be read.
        }
    }

    private static void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path,
                    "# 精妙背包快捷切换补丁配置\n"
                            + "# enabled = true：允许在玩家背包和精妙背包界面之间用快捷键切换。\n"
                            + "# enabled = false：完全关闭该补丁的快捷键切换功能。\n"
                            + "enabled = " + enabled + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Runtime setting still applies even if writing fails.
        }
    }

    private static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }
}
