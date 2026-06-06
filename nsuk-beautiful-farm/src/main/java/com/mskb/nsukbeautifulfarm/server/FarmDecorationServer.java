package com.mskb.nsukbeautifulfarm.server;

import com.mskb.nsukbeautifulfarm.common.DecorationBlockPlan;
import com.mskb.nsukbeautifulfarm.common.DecorationPlanner;
import com.mskb.nsukbeautifulfarm.common.FarmDecorationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public final class FarmDecorationServer {
    private static final Map<BlockPos, Queue<DecorationBlockPlan>> QUEUES = new HashMap<>();
    private static int tickCounter;

    private static Class<?> farmlandDataClass;
    private static Method getSelectedPlot;
    private static Method minPos;
    private static Method maxPos;

    private FarmDecorationServer() {
    }

    public static void reset(BlockPos boxPos) {
        QUEUES.remove(boxPos);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++tickCounter % 5 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            for (Map.Entry<BlockPos, FarmDecorationConfig> entry : FarmDecorationData.all().entrySet()) {
                tickFarm(level, entry.getKey(), entry.getValue());
            }
        }
    }

    private static void tickFarm(ServerLevel level, BlockPos boxPos, FarmDecorationConfig config) {
        if (LenientFarmingServer.isManaged(level, boxPos)) {
            return;
        }
        Queue<DecorationBlockPlan> queue = QUEUES.get(boxPos);
        if (queue == null) {
            PlotBounds bounds = getPlotBounds(boxPos);
            if (bounds == null || !looksStarted(level, bounds)) {
                return;
            }
            List<DecorationBlockPlan> plan = DecorationPlanner.plan(bounds.min, bounds.max, config);
            queue = new ArrayDeque<>(plan);
            QUEUES.put(boxPos, queue);
        }
        DecorationBlockPlan next = queue.poll();
        if (next == null) {
            QUEUES.remove(boxPos);
            return;
        }
        place(level, LenientFarmingServer.findNearbyUsableChest(level, boxPos), next);
    }

    private static boolean looksStarted(ServerLevel level, PlotBounds bounds) {
        BlockState state = level.getBlockState(bounds.min);
        return state.is(Blocks.FARMLAND) || state.is(Blocks.WATER) || state.getBlock() instanceof LiquidBlock;
    }

    private static void place(ServerLevel level, BlockPos chestPos, DecorationBlockPlan plan) {
        BlockState existing = level.getBlockState(plan.pos());
        if (existing.equals(plan.state())) {
            return;
        }
        if (!plan.state().is(Blocks.WATER) && plan.state().getBlock().asItem() != net.minecraft.world.item.Items.AIR) {
            ItemStack cost = new ItemStack(plan.state().getBlock().asItem());
            if (!consume(level, chestPos, cost)) {
                return;
            }
        }
        if (plan.state().is(Blocks.WATER) || plan.state().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
            BlockPos above = plan.pos().above();
            if (!level.getBlockState(above).isAir()) {
                level.destroyBlock(above, true);
            }
        }
        level.setBlock(plan.pos(), plan.state(), 3);
    }

    private static boolean consume(ServerLevel level, BlockPos chestPos, ItemStack cost) {
        if (chestPos == null || cost.isEmpty()) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(chestPos);
        if (be == null) {
            return false;
        }
        if (be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (ItemStack.isSameItemSameTags(stack, cost) && stack.getCount() >= cost.getCount()) {
                    stack.shrink(cost.getCount());
                    container.setChanged();
                    return true;
                }
            }
        }
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER).map(handler -> consume(handler, cost)).orElse(false);
    }

    private static boolean consume(IItemHandler handler, ItemStack cost) {
        int remaining = cost.getCount();
        for (int i = 0; i < handler.getSlots() && remaining > 0; i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!ItemStack.isSameItemSameTags(stack, cost)) {
                continue;
            }
            ItemStack extracted = handler.extractItem(i, remaining, false);
            remaining -= extracted.getCount();
        }
        return remaining == 0;
    }

    private static PlotBounds getPlotBounds(BlockPos boxPos) {
        try {
            Object plot = getSelectedPlotMethod().invoke(null, boxPos);
            if (plot == null) {
                return null;
            }
            if (minPos == null) {
                minPos = plot.getClass().getMethod("minPos");
                maxPos = plot.getClass().getMethod("maxPos");
            }
            return new PlotBounds((BlockPos) minPos.invoke(plot), (BlockPos) maxPos.invoke(plot));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Class<?> farmlandDataClass() throws ClassNotFoundException {
        if (farmlandDataClass == null) {
            farmlandDataClass = Class.forName("com.xiaoliang.simukraft.world.FarmlandHiredData");
        }
        return farmlandDataClass;
    }

    private static Method getSelectedPlotMethod() throws ReflectiveOperationException {
        if (getSelectedPlot == null) {
            getSelectedPlot = farmlandDataClass().getMethod("getSelectedPlot", BlockPos.class);
        }
        return getSelectedPlot;
    }

    private record PlotBounds(BlockPos min, BlockPos max) {
    }
}
