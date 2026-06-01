package com.mskb.nsukbeautifulfarm.common;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class FarmDecorationConfig {
    public BorderStyle borderStyle = BorderStyle.NONE;
    public ResourceLocation borderMaterial = new ResourceLocation("minecraft", "oak_fence");
    public Direction borderFacing = Direction.NORTH;

    public WaterStyle waterStyle = WaterStyle.NONE;
    public Direction waterFacing = Direction.NORTH;
    public int waterOffsetX = 0;
    public int waterOffsetZ = 0;

    public CoverStyle coverStyle = CoverStyle.NONE;
    public ResourceLocation coverMaterial = new ResourceLocation("minecraft", "oak_leaves");
    public Direction coverFacing = Direction.NORTH;

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("borderStyle", borderStyle.name());
        tag.putString("borderMaterial", borderMaterial.toString());
        tag.putString("borderFacing", borderFacing.getName());
        tag.putString("waterStyle", waterStyle.name());
        tag.putString("waterFacing", waterFacing.getName());
        tag.putInt("waterOffsetX", waterOffsetX);
        tag.putInt("waterOffsetZ", waterOffsetZ);
        tag.putString("coverStyle", coverStyle.name());
        tag.putString("coverMaterial", coverMaterial.toString());
        tag.putString("coverFacing", coverFacing.getName());
        return tag;
    }

    public static FarmDecorationConfig fromTag(CompoundTag tag) {
        FarmDecorationConfig config = new FarmDecorationConfig();
        config.borderStyle = readEnum(BorderStyle.class, tag.getString("borderStyle"), BorderStyle.NONE);
        config.borderMaterial = readLocation(tag.getString("borderMaterial"), config.borderMaterial);
        config.borderFacing = readDirection(tag.getString("borderFacing"), Direction.NORTH);
        config.waterStyle = readEnum(WaterStyle.class, tag.getString("waterStyle"), WaterStyle.NONE);
        config.waterFacing = readDirection(tag.getString("waterFacing"), Direction.NORTH);
        config.waterOffsetX = tag.getInt("waterOffsetX");
        config.waterOffsetZ = tag.getInt("waterOffsetZ");
        config.coverStyle = readEnum(CoverStyle.class, tag.getString("coverStyle"), CoverStyle.NONE);
        config.coverMaterial = readLocation(tag.getString("coverMaterial"), config.coverMaterial);
        config.coverFacing = readDirection(tag.getString("coverFacing"), Direction.NORTH);
        return config;
    }

    private static ResourceLocation readLocation(String value, ResourceLocation fallback) {
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        return parsed == null ? fallback : parsed;
    }

    private static Direction readDirection(String value, Direction fallback) {
        Direction direction = Direction.byName(value);
        return direction == null || direction.getAxis().isVertical() ? fallback : direction;
    }

    private static <E extends Enum<E>> E readEnum(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    public enum BorderStyle {
        NONE("无边框"),
        OPPOSITE_FENCE("对边栅栏"),
        FULL_FENCE("四边栅栏"),
        OPPOSITE_WALL("对边石墙"),
        FULL_WALL("四边石墙"),
        OPPOSITE_PANE("对边玻璃板"),
        FULL_PANE("四边玻璃板");

        private final String displayName;

        BorderStyle(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public enum WaterStyle {
        NONE("无水"),
        CHANNELS("水渠"),
        SCATTERED("分散的水");

        private final String displayName;

        WaterStyle(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public enum CoverStyle {
        NONE("无"),
        LEAVES("含水树叶"),
        TOP_SLAB("上半砖"),
        BOTTOM_SLAB("下半砖"),
        TRAPDOOR("活板门");

        private final String displayName;

        CoverStyle(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }
}
