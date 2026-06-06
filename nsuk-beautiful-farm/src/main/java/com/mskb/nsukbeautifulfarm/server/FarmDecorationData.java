package com.mskb.nsukbeautifulfarm.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mskb.nsukbeautifulfarm.common.FarmDecorationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FarmDecorationData {
    private static final Gson GSON = new Gson();
    private static final Map<BlockPos, FarmDecorationConfig> CONFIGS = new ConcurrentHashMap<>();
    private static final String FILE_NAME = "beautiful_farm_decorations.json";

    private FarmDecorationData() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        load(event.getServer());
    }

    public static FarmDecorationConfig get(BlockPos pos) {
        FarmDecorationConfig config = CONFIGS.get(pos);
        return config == null ? null : config;
    }

    public static Map<BlockPos, FarmDecorationConfig> all() {
        return Map.copyOf(CONFIGS);
    }

    public static void set(MinecraftServer server, BlockPos pos, FarmDecorationConfig config) {
        CONFIGS.put(pos.immutable(), config);
        save(server);
        FarmDecorationServer.reset(pos);
    }

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("simukraft").resolve(FILE_NAME);
    }

    public static void load(MinecraftServer server) {
        CONFIGS.clear();
        Path file = file(server);
        if (!Files.exists(file)) {
            return;
        }
        try (var reader = Files.newBufferedReader(file)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                return;
            }
            for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
                BlockPos pos = parsePos(entry.getKey());
                if (pos != null && entry.getValue().isJsonObject()) {
                    CONFIGS.put(pos, fromJson(entry.getValue().getAsJsonObject()));
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static void save(MinecraftServer server) {
        Path file = file(server);
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            for (Map.Entry<BlockPos, FarmDecorationConfig> entry : CONFIGS.entrySet()) {
                root.add(posKey(entry.getKey()), toJson(entry.getValue()));
            }
            try (var writer = Files.newBufferedWriter(file)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private static BlockPos parsePos(String value) {
        try {
            String[] parts = value.split(",");
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (Exception ex) {
            return null;
        }
    }

    private static String posKey(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static JsonObject toJson(FarmDecorationConfig config) {
        JsonObject json = new JsonObject();
        json.addProperty("borderStyle", config.borderStyle.name());
        json.addProperty("borderMaterial", config.borderMaterial.toString());
        json.addProperty("borderFacing", config.borderFacing.getName());
        json.addProperty("waterStyle", config.waterStyle.name());
        json.addProperty("waterFacing", config.waterFacing.getName());
        json.addProperty("waterOffsetX", config.waterOffsetX);
        json.addProperty("waterOffsetZ", config.waterOffsetZ);
        json.addProperty("waterSpacing", config.waterSpacing);
        json.addProperty("coverStyle", config.coverStyle.name());
        json.addProperty("coverMaterial", config.coverMaterial.toString());
        json.addProperty("coverFacing", config.coverFacing.getName());
        json.addProperty("scarecrowStyle", config.scarecrowStyle.name());
        json.addProperty("scarecrowFacing", config.scarecrowFacing.getName());
        json.addProperty("scarecrowSpacing", config.scarecrowSpacing);
        return json;
    }

    private static FarmDecorationConfig fromJson(JsonObject json) {
        FarmDecorationConfig config = new FarmDecorationConfig();
        config.borderStyle = enumValue(FarmDecorationConfig.BorderStyle.class, string(json, "borderStyle"), FarmDecorationConfig.BorderStyle.NONE);
        config.borderMaterial = location(string(json, "borderMaterial"), config.borderMaterial);
        config.borderFacing = direction(string(json, "borderFacing"), Direction.NORTH);
        config.waterStyle = enumValue(FarmDecorationConfig.WaterStyle.class, string(json, "waterStyle"), FarmDecorationConfig.WaterStyle.NONE);
        config.waterFacing = direction(string(json, "waterFacing"), Direction.NORTH);
        config.waterOffsetX = integer(json, "waterOffsetX");
        config.waterOffsetZ = integer(json, "waterOffsetZ");
        config.waterSpacing = json.has("waterSpacing") ? FarmDecorationConfig.clampSpacing(integer(json, "waterSpacing")) : FarmDecorationConfig.defaultSpacing(config.waterStyle);
        config.coverStyle = enumValue(FarmDecorationConfig.CoverStyle.class, string(json, "coverStyle"), FarmDecorationConfig.CoverStyle.NONE);
        config.coverMaterial = location(string(json, "coverMaterial"), config.coverMaterial);
        config.coverFacing = direction(string(json, "coverFacing"), Direction.NORTH);
        config.scarecrowStyle = enumValue(FarmDecorationConfig.ScarecrowStyle.class, string(json, "scarecrowStyle"), FarmDecorationConfig.ScarecrowStyle.NONE);
        config.scarecrowFacing = direction(string(json, "scarecrowFacing"), Direction.NORTH);
        config.scarecrowSpacing = json.has("scarecrowSpacing") ? FarmDecorationConfig.clampScarecrowSpacing(integer(json, "scarecrowSpacing")) : 3;
        return config;
    }

    private static String string(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsString() : "";
    }

    private static int integer(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsInt() : 0;
    }

    private static ResourceLocation location(String value, ResourceLocation fallback) {
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        return parsed == null ? fallback : parsed;
    }

    private static Direction direction(String value, Direction fallback) {
        Direction parsed = Direction.byName(value);
        return parsed == null || parsed.getAxis().isVertical() ? fallback : parsed;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
