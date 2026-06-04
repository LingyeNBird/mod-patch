package com.mskb.nsukbeautifulfarm.server;

import com.mskb.nsukbeautifulfarm.common.DecorationBlockPlan;
import com.mskb.nsukbeautifulfarm.common.DecorationPlanner;
import com.mskb.nsukbeautifulfarm.common.FarmDecorationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LenientFarmingServer {
    private static final int WORK_INTERVAL_TICKS = 5;
    private static final int STATUS_INTERVAL_TICKS = 200;
    private static final Map<BlockPos, WorkState> WORK = new HashMap<>();
    private static int tickCounter;

    private static Class<?> farmlandDataClass;
    private static Method loadAllFarmlandData;
    private static Method saveAllFarmlandData;
    private static Method getSelectedPlot;
    private static Method setSelectedPlot;
    private static Method setSelectedArea;
    private static Method setSelectedCrop;
    private static Method getBoundChest;
    private static Method setBoundChest;
    private static Method getHiredFarmer;
    private static Method findNpcByUuid;
    private static Method minPos;
    private static Method maxPos;
    private static Constructor<?> farmlandPlotConstructor;

    private LenientFarmingServer() {
    }

    public static boolean isManaged(BlockPos boxPos) {
        return WORK.containsKey(boxPos);
    }

    public static boolean demolish(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) {
            return false;
        }
        WorkState state = WORK.remove(boxPos);
        PlotBounds bounds = state != null ? state.bounds : getPlot(boxPos);
        BlockPos chestPos = state != null ? state.chestPos : resolveOrBindChest(level, boxPos);
        FarmDecorationConfig config = FarmDecorationData.get(boxPos);

        if (bounds != null) {
            if (config != null) {
                for (DecorationBlockPlan plan : DecorationPlanner.plan(bounds.min, bounds.max, config)) {
                    breakAndStore(level, chestPos, plan.pos());
                }
            }
            for (BlockPos base : bounds.positions()) {
                breakAndStore(level, chestPos, base.above());
                level.setBlock(base, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        }

        invalidateOriginalWorkflow(level, boxPos);
        level.removeBlock(boxPos, false);
        return true;
    }

    public static void start(ServerPlayer player, BlockPos boxPos, String crop, int areaSize) {
        if (player == null || boxPos == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        MinecraftServer server = level.getServer();
        loadData(server);

        CropPlan cropPlan = CropPlan.resolve(crop);
        if (cropPlan == null) {
            cropPlan = CropPlan.resolve("wheat");
        }
        PlotBounds bounds = getOrCreatePlot(player, boxPos, Math.max(1, areaSize));
        if (bounds == null || cropPlan == null) {
            player.displayClientMessage(Component.literal("美丽农田：已接管耕种，但无法创建有效选区。"), false);
            return;
        }

        setCrop(boxPos, cropPlan.selectionId);
        setArea(boxPos, Math.max(bounds.width(), bounds.depth()));
        setNpcWorking(server, boxPos);
        BlockPos chestPos = resolveOrBindChest(level, boxPos);

        WorkState state = new WorkState(level.dimension().location().toString(), boxPos.immutable(), bounds, cropPlan, chestPos);
        WORK.put(boxPos.immutable(), state);
        saveData(server);
        sendNeeds(player, level, state, true);
        player.displayClientMessage(Component.literal("美丽农田：开始宽松耕种，坏格不会阻止开工。"), false);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++tickCounter % WORK_INTERVAL_TICKS != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            for (WorkState state : List.copyOf(WORK.values())) {
                if (!state.dimension.equals(level.dimension().location().toString())) {
                    continue;
                }
                if (level.getBlockState(state.boxPos).isAir()) {
                    WORK.remove(state.boxPos);
                    continue;
                }
                if (!hasHiredFarmer(state.boxPos)) {
                    WORK.remove(state.boxPos);
                    continue;
                }
                tickWork(level, state);
            }
        }
    }

    private static void tickWork(ServerLevel level, WorkState state) {
        state.chestPos = usableChest(level, state.chestPos) ? state.chestPos : resolveOrBindChest(level, state.boxPos);
        if (!state.topCleared) {
            if (clearOne(level, state)) {
                return;
            }
            state.topCleared = true;
        }
        if (replaceOne(level, state)) {
            return;
        }
        if (decorateOne(level, state)) {
            return;
        }
        if (plantOne(level, state)) {
            return;
        }
        if (isComplete(level, state)) {
            WORK.remove(state.boxPos);
            return;
        }
        if (++state.idleTicks >= STATUS_INTERVAL_TICKS / WORK_INTERVAL_TICKS) {
            state.idleTicks = 0;
            ServerPlayer player = nearestPlayer(level, state.boxPos);
            if (player != null) {
                sendNeeds(player, level, state, false);
            }
        }
    }

    private static boolean clearOne(ServerLevel level, WorkState state) {
        for (BlockPos base : state.bounds.positions()) {
            BlockPos above = base.above();
            BlockState aboveState = level.getBlockState(above);
            if (!aboveState.isAir()) {
                level.destroyBlock(above, true);
                state.idleTicks = 0;
                return true;
            }
        }
        return false;
    }

    private static boolean replaceOne(ServerLevel level, WorkState state) {
        Set<BlockPos> water = waterPositions(state);
        for (BlockPos base : state.bounds.positions()) {
            if (water.contains(base)) {
                if (!level.getBlockState(base).is(Blocks.WATER)) {
                    level.setBlock(base, Blocks.WATER.defaultBlockState(), 3);
                    state.idleTicks = 0;
                    return true;
                }
                continue;
            }
            BlockState existing = level.getBlockState(base);
            if (existing.is(Blocks.DIRT) || existing.getBlock() instanceof FarmBlock) {
                continue;
            }
            if (!isFreeDirtEquivalent(existing) && !consume(level, state.chestPos, new ItemStack(Blocks.DIRT))) {
                continue;
            }
            level.setBlock(base, Blocks.DIRT.defaultBlockState(), 3);
            state.idleTicks = 0;
            return true;
        }
        return false;
    }

    private static boolean decorateOne(ServerLevel level, WorkState state) {
        FarmDecorationConfig config = FarmDecorationData.get(state.boxPos);
        if (config == null) {
            return false;
        }
        List<DecorationBlockPlan> plans = DecorationPlanner.plan(state.bounds.min, state.bounds.max, config);
        for (DecorationBlockPlan plan : plans) {
            BlockState existing = level.getBlockState(plan.pos());
            if (existing.equals(plan.state())) {
                continue;
            }
            ItemStack cost = costFor(plan.state());
            if (!cost.isEmpty() && !consume(level, state.chestPos, cost)) {
                continue;
            }
            if (plan.state().is(Blocks.WATER) || plan.state().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
                BlockPos above = plan.pos().above();
                if (!level.getBlockState(above).isAir()) {
                    level.destroyBlock(above, true);
                }
            }
            level.setBlock(plan.pos(), plan.state(), 3);
            state.idleTicks = 0;
            return true;
        }
        return false;
    }

    private static boolean plantOne(ServerLevel level, WorkState state) {
        Set<BlockPos> water = waterPositions(state);
        for (BlockPos base : state.bounds.positions()) {
            if (water.contains(base) || !shouldPlantAt(state.bounds, base, state.crop.checkerboard)) {
                continue;
            }
            BlockState soil = level.getBlockState(base);
            if (!(soil.getBlock() instanceof FarmBlock)) {
                level.setBlock(base, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 3);
                state.idleTicks = 0;
                return true;
            }
            BlockPos cropPos = base.above();
            if (!level.getBlockState(cropPos).isAir()) {
                continue;
            }
            if (!consume(level, state.chestPos, new ItemStack(state.crop.seed))) {
                continue;
            }
            level.setBlock(cropPos, state.crop.cropBlock.defaultBlockState(), 3);
            state.idleTicks = 0;
            return true;
        }
        return false;
    }

    private static void sendNeeds(ServerPlayer player, ServerLevel level, WorkState state, boolean includeAvailableDirt) {
        Needs needs = computeNeeds(level, state);
        int dirtAvailable = countItem(level, state.chestPos, new ItemStack(Blocks.DIRT));
        int dirtNeed = Math.max(0, needs.dirt - dirtAvailable);
        if (includeAvailableDirt) {
            player.displayClientMessage(Component.literal("美丽农田：相邻箱子泥土 " + dirtAvailable + " 个，还需要 " + dirtNeed + " 个泥土。"), false);
        } else if (needs.dirt > 0) {
            player.displayClientMessage(Component.literal("美丽农田：还需要 " + dirtNeed + " 个泥土。"), false);
        }
        for (Map.Entry<ItemKey, Integer> entry : needs.materials.entrySet()) {
            ItemStack stack = entry.getKey().stack();
            int missing = Math.max(0, entry.getValue() - countItem(level, state.chestPos, stack));
            if (missing > 0) {
                player.displayClientMessage(Component.literal("美丽农田：装饰还需要 " + missing + " 个 " + stack.getHoverName().getString() + "。"), false);
            }
        }
        int seedMissing = Math.max(0, needs.seeds - countItem(level, state.chestPos, new ItemStack(state.crop.seed)));
        if (seedMissing > 0) {
            player.displayClientMessage(Component.literal("美丽农田：种植还需要 " + seedMissing + " 个 " + state.crop.seed.getDescription().getString() + "。"), false);
        }
    }

    private static Needs computeNeeds(ServerLevel level, WorkState state) {
        Needs needs = new Needs();
        Set<BlockPos> water = waterPositions(state);
        for (BlockPos base : state.bounds.positions()) {
            if (!water.contains(base)) {
                BlockState existing = level.getBlockState(base);
                if (!existing.is(Blocks.DIRT) && !isFreeDirtEquivalent(existing)) {
                    needs.dirt++;
                }
            }
            if (!water.contains(base)
                    && level.getBlockState(base).getBlock() instanceof FarmBlock
                    && shouldPlantAt(state.bounds, base, state.crop.checkerboard)
                    && level.getBlockState(base.above()).isAir()) {
                needs.seeds++;
            }
        }
        FarmDecorationConfig config = FarmDecorationData.get(state.boxPos);
        if (config != null) {
            for (DecorationBlockPlan plan : DecorationPlanner.plan(state.bounds.min, state.bounds.max, config)) {
                if (level.getBlockState(plan.pos()).equals(plan.state())) {
                    continue;
                }
                ItemStack cost = costFor(plan.state());
                if (!cost.isEmpty()) {
                    needs.materials.merge(new ItemKey(cost), cost.getCount(), Integer::sum);
                }
            }
        }
        return needs;
    }

    private static boolean isComplete(ServerLevel level, WorkState state) {
        Needs needs = computeNeeds(level, state);
        return needs.dirt == 0 && needs.seeds == 0 && needs.materials.isEmpty();
    }

    private static void breakAndStore(ServerLevel level, BlockPos chestPos, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        List<ItemStack> drops = Block.getDrops(state, level, pos, null);
        level.destroyBlock(pos, false);
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                insertOrDrop(level, chestPos, pos, drop);
            }
        }
    }

    private static void insertOrDrop(ServerLevel level, BlockPos chestPos, BlockPos dropPos, ItemStack stack) {
        ItemStack remaining = stack.copy();
        if (chestPos != null) {
            int inserted = insert(level, chestPos, remaining);
            remaining.shrink(inserted);
        }
        if (!remaining.isEmpty()) {
            net.minecraft.world.entity.item.ItemEntity itemEntity = new net.minecraft.world.entity.item.ItemEntity(
                    level,
                    dropPos.getX() + 0.5D,
                    dropPos.getY() + 0.5D,
                    dropPos.getZ() + 0.5D,
                    remaining
            );
            itemEntity.setPickUpDelay(10);
            level.addFreshEntity(itemEntity);
        }
    }

    private static int insert(ServerLevel level, BlockPos chestPos, ItemStack stack) {
        if (chestPos == null || stack.isEmpty()) {
            return 0;
        }
        BlockEntity be = level.getBlockEntity(chestPos);
        if (be == null) {
            return 0;
        }
        if (be instanceof Container container) {
            int inserted = 0;
            for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
                ItemStack existing = container.getItem(i);
                if (existing.isEmpty()) {
                    container.setItem(i, stack.copy());
                    inserted += stack.getCount();
                    stack.setCount(0);
                    container.setChanged();
                    break;
                }
                if (ItemStack.isSameItemSameTags(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                    int moved = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                    existing.grow(moved);
                    stack.shrink(moved);
                    inserted += moved;
                    container.setChanged();
                }
            }
            return inserted;
        }
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER).map(handler -> insert(handler, stack)).orElse(0);
    }

    private static int insert(IItemHandler handler, ItemStack stack) {
        int before = stack.getCount();
        ItemStack remaining = stack.copy();
        for (int i = 0; i < handler.getSlots() && !remaining.isEmpty(); i++) {
            remaining = handler.insertItem(i, remaining, false);
        }
        return before - remaining.getCount();
    }

    private static Set<BlockPos> waterPositions(WorkState state) {
        FarmDecorationConfig config = FarmDecorationData.get(state.boxPos);
        if (config == null) {
            return Set.of();
        }
        return new HashSet<>(DecorationPlanner.waterPositions(state.bounds.min, state.bounds.max, config));
    }

    private static ItemStack costFor(BlockState state) {
        if (state.is(Blocks.WATER)) {
            return ItemStack.EMPTY;
        }
        Item item = state.getBlock().asItem();
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static boolean isFreeDirtEquivalent(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.getBlock() instanceof FarmBlock;
    }

    private static boolean shouldPlantAt(PlotBounds bounds, BlockPos pos, boolean checkerboard) {
        if (!checkerboard) {
            return true;
        }
        int dx = Math.abs(pos.getX() - bounds.min.getX());
        int dz = Math.abs(pos.getZ() - bounds.min.getZ());
        return (dx + dz) % 2 == 0;
    }

    private static ServerPlayer nearestPlayer(ServerLevel level, BlockPos pos) {
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            double distance = player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private static PlotBounds getOrCreatePlot(ServerPlayer player, BlockPos boxPos, int areaSize) {
        PlotBounds existing = getPlot(boxPos);
        if (existing != null) {
            return existing;
        }
        Direction facing = player.getDirection();
        BlockPos start = boxPos.relative(facing).below();
        BlockPos end = start.relative(facing.getCounterClockWise(), areaSize - 1).relative(facing, areaSize - 1);
        PlotBounds bounds = new PlotBounds(start, end);
        setPlot(boxPos, bounds);
        return bounds;
    }

    private static PlotBounds getPlot(BlockPos boxPos) {
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

    private static void setPlot(BlockPos boxPos, PlotBounds bounds) {
        try {
            Object plot = farmlandPlotConstructor().newInstance(bounds.min, bounds.max);
            setSelectedPlotMethod().invoke(null, boxPos, plot);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private static BlockPos resolveOrBindChest(ServerLevel level, BlockPos boxPos) {
        BlockPos bound = getBoundChest(boxPos);
        if (usableChest(level, bound)) {
            return bound;
        }
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int x = -8; x <= 8; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -8; z <= 8; z++) {
                    BlockPos pos = boxPos.offset(x, y, z);
                    if (!usableChest(level, pos)) {
                        continue;
                    }
                    double distance = pos.distSqr(boxPos);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = pos.immutable();
                    }
                }
            }
        }
        if (nearest != null) {
            setBoundChest(boxPos, nearest);
        }
        return nearest;
    }

    private static boolean usableChest(ServerLevel level, BlockPos pos) {
        if (pos == null) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof Container || be != null && be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
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
            int remaining = cost.getCount();
            for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
                ItemStack stack = container.getItem(i);
                if (!ItemStack.isSameItemSameTags(stack, cost)) {
                    continue;
                }
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
                container.setChanged();
            }
            return remaining == 0;
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
            remaining -= handler.extractItem(i, remaining, false).getCount();
        }
        return remaining == 0;
    }

    private static int countItem(ServerLevel level, BlockPos chestPos, ItemStack template) {
        if (chestPos == null || template.isEmpty()) {
            return 0;
        }
        BlockEntity be = level.getBlockEntity(chestPos);
        if (be == null) {
            return 0;
        }
        if (be instanceof Container container) {
            int count = 0;
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (ItemStack.isSameItemSameTags(stack, template)) {
                    count += stack.getCount();
                }
            }
            return count;
        }
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER).map(handler -> countItem(handler, template)).orElse(0);
    }

    private static int countItem(IItemHandler handler, ItemStack template) {
        int count = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (ItemStack.isSameItemSameTags(stack, template)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void loadData(MinecraftServer server) {
        try {
            loadAllFarmlandDataMethod().invoke(null, server);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private static void saveData(MinecraftServer server) {
        try {
            saveAllFarmlandDataMethod().invoke(null, server);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private static boolean hasHiredFarmer(BlockPos boxPos) {
        try {
            return getHiredFarmerMethod().invoke(null, boxPos) != null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static void invalidateOriginalWorkflow(ServerLevel level, BlockPos boxPos) {
        try {
            Class<?> managerClass = Class.forName("com.xiaoliang.simukraft.utils.FarmlandManager");
            managerClass.getMethod("invalidateWorkflow", ServerLevel.class, BlockPos.class, Class.forName("com.xiaoliang.simukraft.entity.CustomEntity"), boolean.class)
                    .invoke(null, level, boxPos, null, true);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private static void setCrop(BlockPos boxPos, String crop) {
        try {
            setSelectedCropMethod().invoke(null, boxPos, crop);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private static void setArea(BlockPos boxPos, int areaSize) {
        try {
            setSelectedAreaMethod().invoke(null, boxPos, areaSize);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private static BlockPos getBoundChest(BlockPos boxPos) {
        try {
            return (BlockPos) getBoundChestMethod().invoke(null, boxPos);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static void setBoundChest(BlockPos boxPos, BlockPos chestPos) {
        try {
            setBoundChestMethod().invoke(null, boxPos, chestPos);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private static void setNpcWorking(MinecraftServer server, BlockPos boxPos) {
        try {
            Object uuid = getHiredFarmerMethod().invoke(null, boxPos);
            if (uuid == null) {
                return;
            }
            Object npc = findNpcByUuidMethod().invoke(null, server, uuid);
            if (npc == null) {
                return;
            }
            npc.getClass().getMethod("setJob", String.class).invoke(npc, "farmer");
            setEnum(npc, "setWorkStatus", "com.xiaoliang.simukraft.entity.WorkStatus", "WORKING");
            setEnum(npc, "setWorkSubState", "com.xiaoliang.simukraft.entity.WorkSubState", "WORKING");
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private static void setEnum(Object target, String methodName, String enumClassName, String value) throws ReflectiveOperationException {
        Class<?> enumClass = Class.forName(enumClassName);
        Object enumValue = Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), value);
        target.getClass().getMethod(methodName, enumClass).invoke(target, enumValue);
    }

    private static Class<?> farmlandDataClass() throws ClassNotFoundException {
        if (farmlandDataClass == null) {
            farmlandDataClass = Class.forName("com.xiaoliang.simukraft.world.FarmlandHiredData");
        }
        return farmlandDataClass;
    }

    private static Method loadAllFarmlandDataMethod() throws ReflectiveOperationException {
        if (loadAllFarmlandData == null) loadAllFarmlandData = farmlandDataClass().getMethod("loadAllFarmlandData", MinecraftServer.class);
        return loadAllFarmlandData;
    }

    private static Method saveAllFarmlandDataMethod() throws ReflectiveOperationException {
        if (saveAllFarmlandData == null) saveAllFarmlandData = farmlandDataClass().getMethod("saveAllFarmlandData", MinecraftServer.class);
        return saveAllFarmlandData;
    }

    private static Method getSelectedPlotMethod() throws ReflectiveOperationException {
        if (getSelectedPlot == null) getSelectedPlot = farmlandDataClass().getMethod("getSelectedPlot", BlockPos.class);
        return getSelectedPlot;
    }

    private static Method setSelectedPlotMethod() throws ReflectiveOperationException {
        if (setSelectedPlot == null) setSelectedPlot = farmlandDataClass().getMethod("setSelectedPlot", BlockPos.class, Class.forName("com.xiaoliang.simukraft.farmland.FarmlandPlot"));
        return setSelectedPlot;
    }

    private static Method setSelectedAreaMethod() throws ReflectiveOperationException {
        if (setSelectedArea == null) setSelectedArea = farmlandDataClass().getMethod("setSelectedArea", BlockPos.class, int.class);
        return setSelectedArea;
    }

    private static Method setSelectedCropMethod() throws ReflectiveOperationException {
        if (setSelectedCrop == null) setSelectedCrop = farmlandDataClass().getMethod("setSelectedCrop", BlockPos.class, String.class);
        return setSelectedCrop;
    }

    private static Method getBoundChestMethod() throws ReflectiveOperationException {
        if (getBoundChest == null) getBoundChest = farmlandDataClass().getMethod("getBoundChest", BlockPos.class);
        return getBoundChest;
    }

    private static Method setBoundChestMethod() throws ReflectiveOperationException {
        if (setBoundChest == null) setBoundChest = farmlandDataClass().getMethod("setBoundChest", BlockPos.class, BlockPos.class);
        return setBoundChest;
    }

    private static Method getHiredFarmerMethod() throws ReflectiveOperationException {
        if (getHiredFarmer == null) getHiredFarmer = farmlandDataClass().getMethod("getHiredFarmer", BlockPos.class);
        return getHiredFarmer;
    }

    private static Method findNpcByUuidMethod() throws ReflectiveOperationException {
        if (findNpcByUuid == null) findNpcByUuid = farmlandDataClass().getMethod("findNPCByUuid", MinecraftServer.class, java.util.UUID.class);
        return findNpcByUuid;
    }

    private static Constructor<?> farmlandPlotConstructor() throws ReflectiveOperationException {
        if (farmlandPlotConstructor == null) {
            farmlandPlotConstructor = Class.forName("com.xiaoliang.simukraft.farmland.FarmlandPlot").getConstructor(BlockPos.class, BlockPos.class);
        }
        return farmlandPlotConstructor;
    }

    private static final class WorkState {
        private final String dimension;
        private final BlockPos boxPos;
        private final PlotBounds bounds;
        private final CropPlan crop;
        private BlockPos chestPos;
        private boolean topCleared;
        private int idleTicks;

        private WorkState(String dimension, BlockPos boxPos, PlotBounds bounds, CropPlan crop, BlockPos chestPos) {
            this.dimension = dimension;
            this.boxPos = boxPos;
            this.bounds = bounds;
            this.crop = crop;
            this.chestPos = chestPos;
        }
    }

    private static final class Needs {
        private int dirt;
        private int seeds;
        private final Map<ItemKey, Integer> materials = new LinkedHashMap<>();
    }

    private record ItemKey(Item item) {
        private ItemKey(ItemStack stack) {
            this(stack.getItem());
        }

        private ItemStack stack() {
            return new ItemStack(item);
        }
    }

    private static final class PlotBounds {
        private final BlockPos min;
        private final BlockPos max;

        private PlotBounds(BlockPos first, BlockPos second) {
            this.min = new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()));
            this.max = new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
        }

        private int width() {
            return max.getX() - min.getX() + 1;
        }

        private int depth() {
            return max.getZ() - min.getZ() + 1;
        }

        private List<BlockPos> positions() {
            java.util.ArrayList<BlockPos> result = new java.util.ArrayList<>();
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        result.add(new BlockPos(x, y, z));
                    }
                }
            }
            return result;
        }
    }

    private static final class CropPlan {
        private final String selectionId;
        private final Item seed;
        private final Block cropBlock;
        private final boolean checkerboard;

        private CropPlan(String selectionId, Item seed, Block cropBlock, boolean checkerboard) {
            this.selectionId = selectionId;
            this.seed = seed;
            this.cropBlock = cropBlock;
            this.checkerboard = checkerboard;
        }

        private static CropPlan resolve(String crop) {
            String id = normalize(crop);
            ResourceLocation location = ResourceLocation.tryParse(id);
            if (location == null) {
                return null;
            }
            Item item = BuiltInRegistries.ITEM.get(location);
            if (item == Items.AIR) {
                return null;
            }
            Block block = vanillaCropBlock(id);
            if (block == null) {
                if (item instanceof BlockItem blockItem) {
                    block = blockItem.getBlock();
                } else if (item instanceof ItemNameBlockItem itemNameBlockItem) {
                    block = itemNameBlockItem.getBlock();
                }
            }
            if (!(block instanceof CropBlock) && !(block instanceof StemBlock)) {
                return null;
            }
            return new CropPlan(id, item, block, block instanceof StemBlock);
        }

        private static String normalize(String crop) {
            String value = crop == null || crop.isBlank() ? "wheat" : crop.trim().toLowerCase(java.util.Locale.ROOT);
            return switch (value) {
                case "wheat" -> "minecraft:wheat_seeds";
                case "carrot" -> "minecraft:carrot";
                case "potato" -> "minecraft:potato";
                case "beetroot" -> "minecraft:beetroot_seeds";
                case "melon" -> "minecraft:melon_seeds";
                case "pumpkin" -> "minecraft:pumpkin_seeds";
                default -> value;
            };
        }

        private static Block vanillaCropBlock(String id) {
            return switch (id) {
                case "minecraft:wheat_seeds" -> Blocks.WHEAT;
                case "minecraft:carrot" -> Blocks.CARROTS;
                case "minecraft:potato" -> Blocks.POTATOES;
                case "minecraft:beetroot_seeds" -> Blocks.BEETROOTS;
                case "minecraft:melon_seeds" -> Blocks.MELON_STEM;
                case "minecraft:pumpkin_seeds" -> Blocks.PUMPKIN_STEM;
                default -> null;
            };
        }
    }
}
