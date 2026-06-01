package com.mskb.nsukbeautifulfarm.common;

import net.minecraft.resources.ResourceLocation;

public final class DecorationMaterials {
    public static final ResourceLocation[] FENCES = ids(
            "oak_fence", "birch_fence", "spruce_fence", "jungle_fence", "acacia_fence", "dark_oak_fence");
    public static final ResourceLocation[] FENCE_GATES = ids(
            "oak_fence_gate", "birch_fence_gate", "spruce_fence_gate", "jungle_fence_gate", "acacia_fence_gate", "dark_oak_fence_gate");
    public static final ResourceLocation[] WALLS = ids(
            "stone_brick_wall", "mossy_stone_brick_wall", "granite_wall", "blackstone_wall", "andesite_wall", "sandstone_wall");
    public static final ResourceLocation[] PANES = ids(
            "glass_pane", "white_stained_glass_pane", "orange_stained_glass_pane", "magenta_stained_glass_pane",
            "light_blue_stained_glass_pane", "yellow_stained_glass_pane", "lime_stained_glass_pane", "pink_stained_glass_pane",
            "gray_stained_glass_pane", "light_gray_stained_glass_pane", "cyan_stained_glass_pane", "purple_stained_glass_pane",
            "blue_stained_glass_pane", "brown_stained_glass_pane", "green_stained_glass_pane", "red_stained_glass_pane", "black_stained_glass_pane");
    public static final ResourceLocation[] LEAVES = ids(
            "oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves");
    public static final ResourceLocation[] SLABS = ids(
            "oak_slab", "birch_slab", "spruce_slab", "jungle_slab", "acacia_slab", "dark_oak_slab",
            "stone_slab", "smooth_stone_slab", "stone_brick_slab", "mossy_stone_brick_slab", "granite_slab", "blackstone_slab", "andesite_slab", "sandstone_slab");
    public static final ResourceLocation[] TRAPDOORS = ids(
            "oak_trapdoor", "birch_trapdoor", "spruce_trapdoor", "jungle_trapdoor", "acacia_trapdoor", "dark_oak_trapdoor");

    private DecorationMaterials() {
    }

    private static ResourceLocation[] ids(String... paths) {
        ResourceLocation[] ids = new ResourceLocation[paths.length];
        for (int i = 0; i < paths.length; i++) {
            ids[i] = new ResourceLocation("minecraft", paths[i]);
        }
        return ids;
    }
}
