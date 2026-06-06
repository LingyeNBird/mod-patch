package com.mskb.nsukbeautifulfarm.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.PumpkinBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.block.state.properties.Half;
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
        addScarecrows(blocks, min, max, config);
        addBorder(blocks, min, max, config);
        return blocks.entrySet().stream().map(e -> new DecorationBlockPlan(e.getKey(), e.getValue())).toList();
    }

    public static List<BlockPos> waterPositions(BlockPos min, BlockPos max, FarmDecorationConfig config) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        addWater(blocks, min, max, config);
        return List.copyOf(blocks.keySet());
    }

    public static List<BlockPos> scarecrowGroundPositions(BlockPos min, BlockPos max, FarmDecorationConfig config) {
        if (config.scarecrowStyle == FarmDecorationConfig.ScarecrowStyle.NONE) {
            return List.of();
        }
        int spacing = FarmDecorationConfig.clampScarecrowSpacing(config.scarecrowSpacing) + 1;
        List<BlockPos> positions = new ArrayList<>();
        for (int x = alignedStart(min.getX(), 0, spacing); x <= max.getX(); x += spacing) {
            for (int z = alignedStart(min.getZ(), 0, spacing); z <= max.getZ(); z += spacing) {
                BlockPos ground = new BlockPos(x, min.getY(), z);
                if (fitsScarecrow(min, max, ground, config)) {
                    positions.add(ground);
                }
            }
        }
        return positions;
    }

    private static boolean fitsScarecrow(BlockPos min, BlockPos max, BlockPos ground, FarmDecorationConfig config) {
        if (config.scarecrowStyle != FarmDecorationConfig.ScarecrowStyle.BLOCK_1) {
            return true;
        }
        Direction arm = config.scarecrowFacing.getClockWise();
        BlockPos left = ground.relative(arm);
        BlockPos right = ground.relative(arm.getOpposite());
        return insideHorizontal(min, max, left) && insideHorizontal(min, max, right);
    }

    private static boolean insideHorizontal(BlockPos min, BlockPos max, BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
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
        connectBorderBlocks(blocks, y);
    }

    private static void connectBorderBlocks(Map<BlockPos, BlockState> blocks, int y) {
        Map<BlockPos, BlockState> connected = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getY() == y) {
                connected.put(pos, connect(entry.getValue(), blocks.containsKey(pos.north()), blocks.containsKey(pos.east()), blocks.containsKey(pos.south()), blocks.containsKey(pos.west())));
            }
        }
        blocks.putAll(connected);
    }

    private static BlockState connect(BlockState state, boolean north, boolean east, boolean south, boolean west) {
        if (state.hasProperty(WallBlock.NORTH_WALL)) state = state.setValue(WallBlock.NORTH_WALL, north ? WallSide.LOW : WallSide.NONE);
        if (state.hasProperty(WallBlock.EAST_WALL)) state = state.setValue(WallBlock.EAST_WALL, east ? WallSide.LOW : WallSide.NONE);
        if (state.hasProperty(WallBlock.SOUTH_WALL)) state = state.setValue(WallBlock.SOUTH_WALL, south ? WallSide.LOW : WallSide.NONE);
        if (state.hasProperty(WallBlock.WEST_WALL)) state = state.setValue(WallBlock.WEST_WALL, west ? WallSide.LOW : WallSide.NONE);
        if (state.hasProperty(IronBarsBlock.NORTH)) state = state.setValue(IronBarsBlock.NORTH, north);
        if (state.hasProperty(IronBarsBlock.EAST)) state = state.setValue(IronBarsBlock.EAST, east);
        if (state.hasProperty(IronBarsBlock.SOUTH)) state = state.setValue(IronBarsBlock.SOUTH, south);
        if (state.hasProperty(IronBarsBlock.WEST)) state = state.setValue(IronBarsBlock.WEST, west);
        return state;
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
        int spacing = FarmDecorationConfig.clampSpacing(config.waterSpacing);
        if (config.waterStyle == FarmDecorationConfig.WaterStyle.CHANNELS) {
            boolean northSouth = config.waterFacing.getAxis() == Direction.Axis.Z;
            if (northSouth) {
                for (int x : waterCoordinates(min.getX(), max.getX(), config.waterOffsetX, spacing, true)) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) blocks.put(new BlockPos(x, y, z), water);
                }
            } else {
                for (int z : waterCoordinates(min.getZ(), max.getZ(), config.waterOffsetZ, spacing, true)) {
                    for (int x = min.getX(); x <= max.getX(); x++) blocks.put(new BlockPos(x, y, z), water);
                }
            }
            return;
        }
        List<Integer> xs = waterCoordinates(min.getX(), max.getX(), config.waterOffsetX, spacing, true);
        List<Integer> zs = waterCoordinates(min.getZ(), max.getZ(), config.waterOffsetZ, spacing, true);
        for (int x : xs) {
            for (int z : zs) {
                blocks.put(new BlockPos(x, y, z), water);
            }
        }
    }

    private static void addScarecrows(Map<BlockPos, BlockState> blocks, BlockPos min, BlockPos max, FarmDecorationConfig config) {
        if (config.scarecrowStyle == FarmDecorationConfig.ScarecrowStyle.NONE) {
            return;
        }
        for (BlockPos ground : scarecrowGroundPositions(min, max, config)) {
            if (config.scarecrowStyle == FarmDecorationConfig.ScarecrowStyle.BLOCK_1) {
                addBlockScarecrow(blocks, ground, config.scarecrowFacing);
            } else if (config.scarecrowStyle == FarmDecorationConfig.ScarecrowStyle.ARMOR_STAND_1) {
                addArmorStandScarecrowBlocks(blocks, ground, config.scarecrowFacing);
            }
        }
    }

    private static void addBlockScarecrow(Map<BlockPos, BlockState> blocks, BlockPos ground, Direction facing) {
        Direction arm = facing.getClockWise();
        BlockPos body = ground.above(2);
        blocks.put(ground.above(), Blocks.OAK_FENCE.defaultBlockState());
        blocks.put(body, Blocks.HAY_BLOCK.defaultBlockState());
        blocks.put(body.relative(arm), Blocks.OAK_FENCE.defaultBlockState());
        blocks.put(body.relative(arm.getOpposite()), Blocks.OAK_FENCE.defaultBlockState());
        BlockState pumpkin = Blocks.CARVED_PUMPKIN.defaultBlockState();
        if (pumpkin.hasProperty(PumpkinBlock.FACING)) {
            pumpkin = pumpkin.setValue(PumpkinBlock.FACING, facing);
        }
        blocks.put(ground.above(3), pumpkin);
    }

    private static void addArmorStandScarecrowBlocks(Map<BlockPos, BlockState> blocks, BlockPos ground, Direction facing) {
        BlockState trapdoor = Blocks.OAK_TRAPDOOR.defaultBlockState();
        if (trapdoor.hasProperty(TrapDoorBlock.FACING)) {
            trapdoor = trapdoor.setValue(TrapDoorBlock.FACING, facing);
        }
        if (trapdoor.hasProperty(TrapDoorBlock.HALF)) {
            trapdoor = trapdoor.setValue(TrapDoorBlock.HALF, Half.TOP);
        }
        blocks.put(ground.above(2), trapdoor);
    }

    private static List<Integer> waterCoordinates(int min, int max, int offset, int spacing, boolean addEdgeWater) {
        List<Integer> values = new ArrayList<>();
        int start = alignedStart(min, offset, spacing);
        for (int coordinate = start; coordinate <= max; coordinate += spacing) {
            addUnique(values, coordinate);
        }
        if (addEdgeWater && values.isEmpty()) {
            addUnique(values, min);
        }
        if (addEdgeWater && !values.isEmpty()) {
            int first = values.get(0);
            int last = values.get(values.size() - 1);
            if (first - min > 4) {
                addUnique(values, min);
            }
            if (max - last > 4) {
                addUnique(values, max);
            }
            values.sort(Integer::compareTo);
        }
        return values;
    }

    private static void addUnique(List<Integer> values, int coordinate) {
        if (!values.contains(coordinate)) {
            values.add(coordinate);
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
            state = state.setValue(TrapDoorBlock.FACING, config.coverFacing);
            if (state.hasProperty(TrapDoorBlock.HALF)) {
                state = state.setValue(TrapDoorBlock.HALF, Half.TOP);
            }
            return state;
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
