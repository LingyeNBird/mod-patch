package com.mskb.nsukbeautifulfarm.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DecorationPlanner {
    private DecorationPlanner() {
    }

    public static List<DecorationBlockPlan> plan(BlockPos min, BlockPos max, FarmDecorationConfig config) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        addWater(blocks, min, max, config);
        addCover(blocks, config);
        addBorder(blocks, min, max, config);
        return blocks.entrySet().stream().map(e -> new DecorationBlockPlan(e.getKey(), e.getValue())).toList();
    }

    public static List<BlockPos> waterPositions(BlockPos min, BlockPos max, FarmDecorationConfig config) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        addWater(blocks, min, max, config);
        return List.copyOf(blocks.keySet());
    }

    private static void addBorder(Map<BlockPos, BlockState> blocks, BlockPos min, BlockPos max, FarmDecorationConfig config) {
        if (config.borderStyle == FarmDecorationConfig.BorderStyle.NONE) {
            return;
        }
        BlockState material = block(config.borderMaterial).defaultBlockState();
        boolean fence = config.borderStyle == FarmDecorationConfig.BorderStyle.OPPOSITE_FENCE || config.borderStyle == FarmDecorationConfig.BorderStyle.FULL_FENCE;
        boolean full = config.borderStyle == FarmDecorationConfig.BorderStyle.FULL_FENCE || config.borderStyle == FarmDecorationConfig.BorderStyle.FULL_WALL || config.borderStyle == FarmDecorationConfig.BorderStyle.FULL_PANE;
        boolean zSides = full || config.borderFacing.getAxis() == Direction.Axis.X;
        boolean xSides = full || config.borderFacing.getAxis() == Direction.Axis.Z;
        int y = max.getY() + 1;
        int minX = min.getX() - 1;
        int maxX = max.getX() + 1;
        int minZ = min.getZ() - 1;
        int maxZ = max.getZ() + 1;
        for (int x = minX; x <= maxX; x++) {
            if (zSides) {
                blocks.put(new BlockPos(x, y, minZ), material);
                blocks.put(new BlockPos(x, y, maxZ), material);
            }
        }
        for (int z = minZ; z <= maxZ; z++) {
            if (xSides) {
                blocks.put(new BlockPos(minX, y, z), material);
                blocks.put(new BlockPos(maxX, y, z), material);
            }
        }
        if (!full || fence) {
            addFenceGate(blocks, min, max, config, fence);
        }
    }

    private static void addFenceGate(Map<BlockPos, BlockState> blocks, BlockPos min, BlockPos max, FarmDecorationConfig config, boolean fence) {
        if (!fence || config.borderStyle != FarmDecorationConfig.BorderStyle.FULL_FENCE) {
            return;
        }
        ResourceLocation gateId = matchingFenceGate(config.borderMaterial);
        Block gateBlock = block(gateId);
        BlockState gate = gateBlock.defaultBlockState();
        if (gate.hasProperty(FenceGateBlock.FACING)) {
            gate = gate.setValue(FenceGateBlock.FACING, config.borderFacing);
        }
        int y = max.getY() + 1;
        List<BlockPos> holes = centeredSidePositions(min, max, config.borderFacing, y);
        for (BlockPos pos : holes) {
            blocks.put(pos, gate);
        }
    }

    private static List<BlockPos> centeredSidePositions(BlockPos min, BlockPos max, Direction side, int y) {
        List<BlockPos> result = new ArrayList<>();
        if (side.getAxis() == Direction.Axis.Z) {
            int z = side == Direction.NORTH ? min.getZ() - 1 : max.getZ() + 1;
            addCentered(result, min.getX(), max.getX(), x -> new BlockPos(x, y, z));
        } else {
            int x = side == Direction.WEST ? min.getX() - 1 : max.getX() + 1;
            addCentered(result, min.getZ(), max.getZ(), z -> new BlockPos(x, y, z));
        }
        return result;
    }

    private static void addCentered(List<BlockPos> result, int min, int max, PosFactory factory) {
        int width = max - min + 1;
        if ((width & 1) == 0) {
            result.add(factory.create(min + width / 2 - 1));
            result.add(factory.create(min + width / 2));
        } else {
            result.add(factory.create(min + width / 2));
        }
    }

    private static void addWater(Map<BlockPos, BlockState> blocks, BlockPos min, BlockPos max, FarmDecorationConfig config) {
        if (config.waterStyle == FarmDecorationConfig.WaterStyle.NONE) {
            return;
        }
        BlockState water = Blocks.WATER.defaultBlockState();
        int y = min.getY();
        if (config.waterStyle == FarmDecorationConfig.WaterStyle.CHANNELS) {
            boolean northSouth = config.waterFacing.getAxis() == Direction.Axis.Z;
            if (northSouth) {
                int startX = alignedStart(min.getX(), config.waterOffsetX, 4);
                for (int x = startX; x <= max.getX(); x += 4) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) blocks.put(new BlockPos(x, y, z), water);
                }
            } else {
                int startZ = alignedStart(min.getZ(), config.waterOffsetZ, 4);
                for (int z = startZ; z <= max.getZ(); z += 4) {
                    for (int x = min.getX(); x <= max.getX(); x++) blocks.put(new BlockPos(x, y, z), water);
                }
            }
            return;
        }
        int startX = alignedStart(min.getX(), config.waterOffsetX, 8);
        int startZ = alignedStart(min.getZ(), config.waterOffsetZ, 8);
        for (int x = startX; x <= max.getX(); x += 8) {
            for (int z = startZ; z <= max.getZ(); z += 8) {
                blocks.put(new BlockPos(x, y, z), water);
            }
        }
    }

    private static int alignedStart(int min, int offset, int step) {
        int normalized = Math.floorMod(offset, step);
        return min + normalized;
    }

    private static void addCover(Map<BlockPos, BlockState> blocks, FarmDecorationConfig config) {
        if (config.coverStyle == FarmDecorationConfig.CoverStyle.NONE) {
            return;
        }
        List<BlockPos> water = blocks.entrySet().stream()
                .filter(e -> e.getValue().is(Blocks.WATER))
                .map(Map.Entry::getKey)
                .toList();
        for (BlockPos pos : water) {
            BlockState state = coverState(config);
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                state = state.setValue(BlockStateProperties.WATERLOGGED, true);
            }
            blocks.put(pos, state);
        }
    }

    private static BlockState coverState(FarmDecorationConfig config) {
        Block block = block(config.coverMaterial);
        BlockState state = block.defaultBlockState();
        if (config.coverStyle == FarmDecorationConfig.CoverStyle.TOP_SLAB && state.hasProperty(SlabBlock.TYPE)) {
            return state.setValue(SlabBlock.TYPE, SlabType.TOP);
        }
        if (config.coverStyle == FarmDecorationConfig.CoverStyle.BOTTOM_SLAB && state.hasProperty(SlabBlock.TYPE)) {
            return state.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        }
        if (config.coverStyle == FarmDecorationConfig.CoverStyle.TRAPDOOR && state.hasProperty(TrapDoorBlock.FACING)) {
            return state.setValue(TrapDoorBlock.FACING, config.coverFacing);
        }
        return state;
    }

    private static Block block(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.getOptional(id).orElse(Blocks.AIR);
    }

    private static ResourceLocation matchingFenceGate(ResourceLocation fence) {
        String path = fence.getPath().replace("_fence", "_fence_gate");
        return new ResourceLocation(fence.getNamespace(), path);
    }

    private interface PosFactory {
        BlockPos create(int coordinate);
    }
}
