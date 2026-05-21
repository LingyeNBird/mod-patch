package com.mskb.nsukmanifestplus.config;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ManifestPlusConfig {
    private static final String FILE_NAME = "nsukmanifestplus-server.toml";
    private static final String LOCAL_FILE_NAME = "nsukmanifestplus-local.toml";
    private static final Settings SETTINGS = new Settings(64, 128, 16, 6000, 10, 4);
    private static final Settings LOCAL_SETTINGS = SETTINGS.copy();

    private static MinecraftServer currentServer;
    private static boolean registered;

    private ManifestPlusConfig() {
    }

    public static void register() {
        if (!registered) {
            registered = true;
            MinecraftForge.EVENT_BUS.addListener(ManifestPlusConfig::onServerStarted);
        }
    }

    public static Settings get() {
        return SETTINGS.copy();
    }

    public static Settings getLocal() {
        loadLocal();
        return LOCAL_SETTINGS.copy();
    }

    public static void applyLocalAndSave(Settings settings) {
        LOCAL_SETTINGS.copyFrom(settings.sanitized());
        saveLocal();
    }

    public static void applyAndSave(MinecraftServer server, Settings settings) {
        SETTINGS.copyFrom(settings.sanitized());
        save(server);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        currentServer = event.getServer();
        load(currentServer);
        save(currentServer);
    }

    private static void load(MinecraftServer server) {
        Path path = configPath(server);
        if (!Files.exists(path)) {
            loadLocal();
            SETTINGS.copyFrom(LOCAL_SETTINGS.sanitized());
            return;
        }

        try {
            SETTINGS.copyFrom(readSettings(path, SETTINGS).sanitized());
        } catch (IOException ignored) {
            // Runtime defaults remain active if the file cannot be read.
        }
    }

    private static void save(MinecraftServer server) {
        MinecraftServer targetServer = server != null ? server : currentServer;
        if (targetServer == null) {
            return;
        }

        Path path = configPath(targetServer);
        try {
            Files.createDirectories(path.getParent());
            Settings settings = SETTINGS.sanitized();
            Files.writeString(path, serialize(settings, "当前世界服务端"), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Runtime settings still apply even if writing fails.
        }
    }

    private static void loadLocal() {
        Path path = localConfigPath();
        if (!Files.exists(path)) {
            saveLocal();
            return;
        }

        try {
            LOCAL_SETTINGS.copyFrom(readSettings(path, LOCAL_SETTINGS).sanitized());
        } catch (IOException ignored) {
            // Keep the in-memory local settings if the local file cannot be read.
        }
    }

    private static void saveLocal() {
        Path path = localConfigPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, serialize(LOCAL_SETTINGS.sanitized(), "本地默认"), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Local UI still updates in-memory if writing fails.
        }
    }

    private static Path configPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(FILE_NAME);
    }

    private static Path localConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve(LOCAL_FILE_NAME);
    }

    private static Settings readSettings(Path path, Settings fallback) throws IOException {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                continue;
            }
            String[] parts = trimmed.split("=", 2);
            try {
                values.put(parts[0].trim(), Integer.parseInt(parts[1].trim()));
            } catch (NumberFormatException ignored) {
                // Keep default for invalid values.
            }
        }
        return new Settings(
                values.getOrDefault("maxBlockPositionsPerTick", fallback.maxBlockPositionsPerTick),
                values.getOrDefault("maxContainerSlotsPerTick", fallback.maxContainerSlotsPerTick),
                values.getOrDefault("maxTasksPerTick", fallback.maxTasksPerTick),
                values.getOrDefault("cacheTtlTicks", fallback.cacheTtlTicks),
                values.getOrDefault("progressIntervalTicks", fallback.progressIntervalTicks),
                values.getOrDefault("minProgressTicks", fallback.minProgressTicks)
        );
    }

    private static String serialize(Settings settings, String scopeLabel) {
        return "# NSUK Manifest Plus 清单库存扫描配置（" + scopeLabel + "）\n"
                + "# 当前世界实际扫描只读取 world/serverconfig/nsukmanifestplus-server.toml。\n"
                + "# 客户端本地 config/nsukmanifestplus-local.toml 只作为本地默认值；远程服务器普通玩家保存不会影响服务器。\n"
                + "\n"
                + "# 每 tick 全服最多检查多少个方块位置，用于寻找附近容器。默认 64。\n"
                + "maxBlockPositionsPerTick = " + settings.maxBlockPositionsPerTick + "\n"
                + "\n"
                + "# 每 tick 全服最多读取多少个容器槽位。超大容器会从上次槽位继续扫。默认 128。\n"
                + "maxContainerSlotsPerTick = " + settings.maxContainerSlotsPerTick + "\n"
                + "\n"
                + "# 每 tick 最多轮询多少个清单扫描任务，防止单个任务独占。默认 16。\n"
                + "maxTasksPerTick = " + settings.maxTasksPerTick + "\n"
                + "\n"
                + "# 库存扫描缓存有效 tick 数。20 tick 约等于 1 秒；默认 6000（约 5 分钟）。设为 0 表示不复用缓存。\n"
                + "cacheTtlTicks = " + settings.cacheTtlTicks + "\n"
                + "\n"
                + "# 扫描期间每隔多少 tick 向客户端发送一次进度。默认 10。\n"
                + "progressIntervalTicks = " + settings.progressIntervalTicks + "\n"
                + "\n"
                + "# 预计扫描 tick 数小于该值时不显示进度条，避免小任务闪一下。默认 4。\n"
                + "minProgressTicks = " + settings.minProgressTicks + "\n";
    }

    public static final class Settings {
        public int maxBlockPositionsPerTick;
        public int maxContainerSlotsPerTick;
        public int maxTasksPerTick;
        public int cacheTtlTicks;
        public int progressIntervalTicks;
        public int minProgressTicks;

        public Settings(int maxBlockPositionsPerTick, int maxContainerSlotsPerTick, int maxTasksPerTick,
                        int cacheTtlTicks, int progressIntervalTicks, int minProgressTicks) {
            this.maxBlockPositionsPerTick = maxBlockPositionsPerTick;
            this.maxContainerSlotsPerTick = maxContainerSlotsPerTick;
            this.maxTasksPerTick = maxTasksPerTick;
            this.cacheTtlTicks = cacheTtlTicks;
            this.progressIntervalTicks = progressIntervalTicks;
            this.minProgressTicks = minProgressTicks;
        }

        public Settings copy() {
            return new Settings(maxBlockPositionsPerTick, maxContainerSlotsPerTick, maxTasksPerTick,
                    cacheTtlTicks, progressIntervalTicks, minProgressTicks);
        }

        public void copyFrom(Settings other) {
            this.maxBlockPositionsPerTick = other.maxBlockPositionsPerTick;
            this.maxContainerSlotsPerTick = other.maxContainerSlotsPerTick;
            this.maxTasksPerTick = other.maxTasksPerTick;
            this.cacheTtlTicks = other.cacheTtlTicks;
            this.progressIntervalTicks = other.progressIntervalTicks;
            this.minProgressTicks = other.minProgressTicks;
        }

        public Settings sanitized() {
            return new Settings(
                    clamp(maxBlockPositionsPerTick, 1, 4096),
                    clamp(maxContainerSlotsPerTick, 1, 16384),
                    clamp(maxTasksPerTick, 1, 128),
                    clamp(cacheTtlTicks, 0, 20 * 60 * 60),
                    clamp(progressIntervalTicks, 1, 200),
                    clamp(minProgressTicks, 1, 200)
            );
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
