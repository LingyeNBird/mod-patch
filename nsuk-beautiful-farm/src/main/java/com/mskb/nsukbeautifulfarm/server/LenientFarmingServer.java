package com.mskb.nsukbeautifulfarm.server;

import com.mskb.nsukbeautifulfarm.common.DecorationBlockPlan;
import com.mskb.nsukbeautifulfarm.common.DecorationPlanner;
import com.mskb.nsukbeautifulfarm.common.FarmDecorationConfig;
import com.mskb.nsukbeautifulfarm.config.BeautifulFarmConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;

public final class LenientFarmingServer {
    private static final String WORK_DATA_ID = "nsukbeautifulfarm_work";
    private static final Map<WorkKey, WorkState> WORK = new HashMap<>();
    private static final Map<WorkKey, HarvestJob> HARVEST_JOBS = new HashMap<>();
    private static int tickCounter;
    private static MinecraftServer loadedServer;

    private static Class<?> farmlandDataClass;
    private static Method loadAllFarmlandData;
    private static Method saveAllFarmlandData;
    private static Method getSelectedPlot;
    private static Method setSelectedPlot;
    private static Method setSelectedArea;
    private static Method setSelectedCrop;
    private static Method clearSelectedCrop;
    private static Method clearSelectedArea;
    private static Method clearSelectedPlot;
    private static Method getSelectedCrop;
    private static Method getHiredFarmer;
    private static Method findNpcByUuid;
    private static Method canUseItemForBlock;
    private static Method minPos;
    private static Method maxPos;
    private static Constructor<?> farmlandPlotConstructor;
    private static Class<?> farmlandBoxBlockClass;

    private LenientFarmingServer() {
    }

    public static boolean isManaged(BlockPos boxPos) {
        return WORK.keySet().stream().anyMatch(key -> key.boxPos().equals(boxPos));
    }

    public static boolean isManaged(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) {
            return false;
        }
        ensureWorkLoaded(level.getServer());
        return WORK.containsKey(workKey(level, boxPos));
    }

    public static boolean shouldSkipHarvestBecauseOutputFull(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) {
            return false;
        }
        BlockPos chestPos = findNearbyUsableChest(level, boxPos);
        if (!usableChest(level, chestPos)) {
            return true;
        }
        return !hasAnyInsertSpace(level, chestPos);
    }

    public static boolean isPaused(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) {
            return false;
        }
        ensureWorkLoaded(level.getServer());
        WorkState state = WORK.get(workKey(level, boxPos));
        return state != null && state.paused;
    }

    public static void togglePaused(ServerPlayer player, BlockPos boxPos) {
        if (player == null || boxPos == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        WorkKey key = workKey(level, boxPos);
        if (!canControlWork(player, boxPos)) {
            player.displayClientMessage(Component.literal("美丽农田：你离这个农田盒太远，无法操作。"), false);
            return;
        }
        ensureWorkLoaded(level.getServer());
        WorkState state = WORK.get(key);
        if (state == null) {
            player.displayClientMessage(Component.literal("美丽农田：当前没有可暂停的耕种任务。"), false);
            return;
        }
        boolean paused = !state.paused;
        state.paused = paused;
        HARVEST_JOBS.remove(key);
        persistWork(level.getServer(), state);
        player.displayClientMessage(Component.literal(paused ? "美丽农田：已暂停耕种维护，仍会保持湿润。" : "美丽农田：已继续耕种维护。"), false);
    }

    public static void stopWork(ServerPlayer player, BlockPos boxPos) {
        if (player == null || boxPos == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        WorkKey key = workKey(level, boxPos);
        if (!canControlWork(player, boxPos)) {
            player.displayClientMessage(Component.literal("美丽农田：你离这个农田盒太远，无法操作。"), false);
            return;
        }
        ensureWorkLoaded(level.getServer());
        WORK.remove(key);
        removePersistedWork(level.getServer(), key);
        HARVEST_JOBS.remove(key);
        clearOriginalFarmWork(level.getServer(), boxPos);
        player.displayClientMessage(Component.literal("美丽农田：已终止耕种任务，农民雇佣关系保留。"), false);
    }

    public static boolean harvestAndStore(ServerLevel level, BlockPos boxPos, Object npc) {
        if (level == null || boxPos == null) {
            return true;
        }
        ensureWorkLoaded(level.getServer());
        WorkKey key = workKey(level, boxPos);
        WorkState state = WORK.get(key);
        Queue<HarvestPlan> plans;
        String dimension;
        if (state == null) {
            loadData(level.getServer());
            PlotBounds bounds = getPlot(boxPos);
            if (bounds == null) {
                return true;
            }
            plans = collectHarvestPlansWithoutWork(level, bounds, CropPlan.resolveStrict(getCrop(boxPos)), npc);
            dimension = level.dimension().location().toString();
        } else {
            if (state.paused) {
                HARVEST_JOBS.remove(key);
                return true;
            }
            plans = collectHarvestPlans(level, state, npc);
            dimension = state.dimension;
        }
        if (!plans.isEmpty()) {
            HARVEST_JOBS.put(new WorkKey(dimension, boxPos.immutable()), new HarvestJob(dimension, boxPos.immutable(), plans));
        } else {
            HARVEST_JOBS.remove(key);
        }
        return true;
    }

    public static BlockPos findNearbyUsableChest(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) {
            return null;
        }
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (BlockPos pos : nearbyContainers(level, boxPos)) {
            if (!hasAnyInsertSpace(level, pos)) {
                continue;
            }
            double distance = pos.distSqr(boxPos);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = pos;
            }
        }
        return nearest;
    }

    public static boolean demolish(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) {
            return false;
        }
        ensureWorkLoaded(level.getServer());
        WorkKey key = workKey(level, boxPos);
        WorkState state = WORK.remove(key);
        removePersistedWork(level.getServer(), key);
        HARVEST_JOBS.remove(key);
        PlotBounds bounds = state != null ? state.bounds : getPlot(boxPos);
        BlockPos chestPos = findNearbyUsableChest(level, boxPos);
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
        ensureWorkLoaded(server);
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

        WorkState state = new WorkState(level.dimension().location().toString(), boxPos.immutable(), bounds, cropPlan, isPartiallyStarted(level, bounds));
        WORK.put(state.key(), state);
        persistWork(server, state);
        saveData(server);
        sendNeeds(player, level, state, true);
        player.displayClientMessage(Component.literal("美丽农田：开始宽松耕种，坏格不会阻止开工。"), false);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        ensureWorkLoaded(server);
        processHarvestJobs(server);
        if (++tickCounter % workIntervalTicks() != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (WorkState state : List.copyOf(WORK.values())) {
                if (!state.dimension.equals(level.dimension().location().toString())) {
                    continue;
                }
                WorkKey key = state.key();
                if (!isFarmlandBoxBlock(level.getBlockState(state.boxPos).getBlock())) {
                    WORK.remove(key);
                    removePersistedWork(server, key);
                    HARVEST_JOBS.remove(key);
                    continue;
                }
                if (!hasHiredFarmer(state.boxPos)) {
                    HARVEST_JOBS.remove(key);
                    continue;
                }
                keepFarmlandMoist(level, state);
                if (state.paused) {
                    HARVEST_JOBS.remove(key);
                    continue;
                }
                if (!isFarmerAbleToWork(server, state.boxPos)) {
                    continue;
                }
                tickWork(level, state);
            }
        }
    }

    private static void keepFarmlandMoist(ServerLevel level, WorkState state) {
        Set<BlockPos> water = waterPositions(state);
        for (BlockPos base : state.bounds.positions()) {
            if (water.contains(base)) {
                continue;
            }
            BlockState existing = level.getBlockState(base);
            if (existing.getBlock() instanceof FarmBlock && existing.hasProperty(FarmBlock.MOISTURE) && existing.getValue(FarmBlock.MOISTURE) < 7) {
                level.setBlock(base, existing.setValue(FarmBlock.MOISTURE, 7), 3);
                return;
            }
        }
    }

    private static boolean canControlWork(ServerPlayer player, BlockPos boxPos) {
        ServerLevel level = player.serverLevel();
        if (!isFarmlandBoxBlock(level.getBlockState(boxPos).getBlock())) {
            return false;
        }
        return player.distanceToSqr(boxPos.getX() + 0.5D, boxPos.getY() + 0.5D, boxPos.getZ() + 0.5D) <= 64.0D;
    }

    private static void tickWork(ServerLevel level, WorkState state) {
        if (!state.maintenanceMode && !state.topCleared) {
            if (clearOne(level, state)) {
                return;
            }
            state.topCleared = true;
            persistWork(level.getServer(), state);
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
            if (!state.maintenanceMode) {
                state.maintenanceMode = true;
                persistWork(level.getServer(), state);
            }
            return;
        }
        if (++state.idleTicks >= Math.max(1, statusIntervalTicks() / workIntervalTicks())) {
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
                if (!isWaterSatisfied(level.getBlockState(base))) {
                    level.setBlock(base, Blocks.WATER.defaultBlockState(), 3);
                    state.idleTicks = 0;
                    return true;
                }
                continue;
            }
            BlockState existing = level.getBlockState(base);
            if (existing.getBlock() instanceof FarmBlock) {
                continue;
            }
            if (!isFreeDirtEquivalent(existing) && !consumeNearbyItem(level, state.boxPos, new ItemStack(Blocks.DIRT))) {
                continue;
            }
            level.setBlock(base, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 3);
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
            if (isDecorationSatisfied(existing, plan.state())) {
                continue;
            }
            ItemStack cost = costFor(plan.state());
            if (!cost.isEmpty() && !consumeNearbyBuildingMaterial(level, state.boxPos, plan.state())) {
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
            BlockState existingCrop = level.getBlockState(cropPos);
            if (existingCrop.is(state.crop.cropBlock)) {
                continue;
            }
            if (!existingCrop.isAir()) {
                continue;
            }
            if (!consumeNearbyItem(level, state.boxPos, new ItemStack(state.crop.seed))) {
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
        int dirtAvailable = countNearbyItem(level, state.boxPos, new ItemStack(Blocks.DIRT));
        int dirtNeed = Math.max(0, needs.dirt - dirtAvailable);
        if (includeAvailableDirt) {
            player.displayClientMessage(Component.literal("美丽农田：相邻箱子泥土 " + dirtAvailable + " 个，还需要 " + dirtNeed + " 个泥土。"), false);
        } else if (needs.dirt > 0) {
            player.displayClientMessage(Component.literal("美丽农田：还需要 " + dirtNeed + " 个泥土。"), false);
        }
        for (Map.Entry<MaterialNeedKey, Integer> entry : needs.materials.entrySet()) {
            BlockState materialState = entry.getKey().state();
            ItemStack stack = costFor(materialState);
            int missing = Math.max(0, entry.getValue() - countNearbyBuildingMaterial(level, state.boxPos, materialState));
            if (missing > 0) {
                player.displayClientMessage(Component.literal("美丽农田：装饰还需要 " + missing + " 个 " + stack.getHoverName().getString() + "。"), false);
            }
        }
        int seedMissing = Math.max(0, needs.seeds - countNearbyItem(level, state.boxPos, new ItemStack(state.crop.seed)));
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
                if (isDecorationSatisfied(level.getBlockState(plan.pos()), plan.state())) {
                    continue;
                }
                ItemStack cost = costFor(plan.state());
                if (!cost.isEmpty()) {
                    needs.materials.merge(new MaterialNeedKey(plan.state()), cost.getCount(), Integer::sum);
                }
            }
        }
        return needs;
    }

    private static boolean isComplete(ServerLevel level, WorkState state) {
        Needs needs = computeNeeds(level, state);
        return needs.dirt == 0 && needs.seeds == 0 && needs.materials.isEmpty();
    }

    private static boolean isPartiallyStarted(ServerLevel level, PlotBounds bounds) {
        for (BlockPos base : bounds.positions()) {
            if (level.getBlockState(base).getBlock() instanceof FarmBlock || !level.getBlockState(base.above()).isAir()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWaterSatisfied(BlockState existing) {
        return existing.is(Blocks.WATER)
                || existing.hasProperty(BlockStateProperties.WATERLOGGED) && Boolean.TRUE.equals(existing.getValue(BlockStateProperties.WATERLOGGED));
    }

    private static boolean isDecorationSatisfied(BlockState existing, BlockState target) {
        if (existing.equals(target)) {
            return true;
        }
        if (!existing.is(target.getBlock())) {
            return false;
        }
        for (var property : target.getProperties()) {
            if (!existing.hasProperty(property)) {
                continue;
            }
            if (isIgnoredDecorationProperty(property)) {
                continue;
            }
            if (!existing.getValue(property).equals(target.getValue(property))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIgnoredDecorationProperty(Property<?> property) {
        String name = property.getName();
        return property == BlockStateProperties.POWERED
                || property == BlockStateProperties.OPEN
                || name.equals("north")
                || name.equals("east")
                || name.equals("south")
                || name.equals("west")
                || name.equals("up");
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
            itemEntity.setPickUpDelay(droppedItemPickupDelayTicks());
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

    private static void processHarvestJobs(MinecraftServer server) {
        int pendingPlans = 0;
        for (HarvestJob job : HARVEST_JOBS.values()) {
            pendingPlans += job.pending.size();
        }
        int limit = Math.max(minHarvestPlansPerTick(), (int) Math.ceil(pendingPlans / (double) harvestIntervalTicks()));
        int processed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (HarvestJob job : List.copyOf(HARVEST_JOBS.values())) {
                if (processed >= limit) {
                    return;
                }
                if (!job.dimension.equals(level.dimension().location().toString())) {
                    continue;
                }
                if (!isFarmlandBoxBlock(level.getBlockState(job.boxPos).getBlock())) {
                    HARVEST_JOBS.remove(job.key());
                    continue;
                }
                if (!hasHiredFarmer(job.boxPos)) {
                    HARVEST_JOBS.remove(job.key());
                    continue;
                }
                if (!isFarmerAbleToWork(server, job.boxPos)) {
                    continue;
                }
                WorkState state = WORK.get(job.key());
                if (state != null && state.paused) {
                    HARVEST_JOBS.remove(job.key());
                    continue;
                }
                while (processed < limit && !job.pending.isEmpty()) {
                    executeHarvestPlan(level, job.boxPos, job.pending.poll());
                    processed++;
                }
                if (job.pending.isEmpty()) {
                    HARVEST_JOBS.remove(job.key());
                }
                if (processed >= limit) {
                    return;
                }
            }
        }
    }

    private static Queue<HarvestPlan> collectHarvestPlans(ServerLevel level, WorkState state, Object npc) {
        Queue<HarvestPlan> plans = new ArrayDeque<>();
        Set<BlockPos> plannedStemFruit = new HashSet<>();
        for (BlockPos base : state.bounds.positions()) {
            BlockPos cropPos = base.above();
            BlockState cropState = level.getBlockState(cropPos);
            Block block = cropState.getBlock();
            HarvestPlan plan;
            if (isRightClickHarvestCrop(block)) {
                plan = planRightClickCrop(level, cropPos, cropState);
            } else if (state.crop.cropBlock instanceof StemBlock) {
                plan = planStemFruit(level, cropPos, state.crop.cropBlock);
                if (plan != null && !plannedStemFruit.add(plan.pos)) {
                    plan = null;
                }
            } else {
                plan = planRegularCrop(level, cropPos, cropState, state.crop, npc);
            }
            if (plan != null) {
                plans.add(plan);
            }
        }
        return plans;
    }

    private static Queue<HarvestPlan> collectHarvestPlansWithoutWork(ServerLevel level, PlotBounds bounds, CropPlan crop, Object npc) {
        Queue<HarvestPlan> plans = new ArrayDeque<>();
        Set<BlockPos> plannedStemFruit = new HashSet<>();
        for (BlockPos base : bounds.positions()) {
            BlockPos cropPos = base.above();
            BlockState cropState = level.getBlockState(cropPos);
            Block block = cropState.getBlock();
            HarvestPlan plan = null;
            // 没有持久化 WORK 时，复刻原模组 harvestAndReplant 的判断：右键作物按当前方块识别，普通/瓜类作物只按农田盒保存的 selectedCrop 处理。
            if (isRightClickHarvestCrop(block)) {
                plan = planRightClickCrop(level, cropPos, cropState);
            } else if (crop != null && crop.cropBlock instanceof StemBlock) {
                plan = planStemFruit(level, cropPos, crop.cropBlock);
                if (plan != null && !plannedStemFruit.add(plan.pos)) {
                    plan = null;
                }
            } else if (crop != null) {
                plan = planRegularCrop(level, cropPos, cropState, crop, npc);
            }
            if (plan != null) {
                plans.add(plan);
            }
        }
        return plans;
    }

    private static HarvestPlan planRightClickCrop(ServerLevel level, BlockPos pos, BlockState state) {
        if (!isRightClickCropMature(state)) {
            return null;
        }
        List<ItemStack> drops = new java.util.ArrayList<>();
        if (state.is(Blocks.SWEET_BERRY_BUSH)) {
            drops.add(new ItemStack(Items.SWEET_BERRIES, 2 + level.random.nextInt(2)));
        } else {
            drops.addAll(Block.getDrops(state, level, pos, null));
        }
        return new HarvestPlan(HarvestKind.RIGHT_CLICK, pos.immutable(), state, drops, ItemStack.EMPTY, null, false);
    }

    private static HarvestPlan planRegularCrop(ServerLevel level, BlockPos pos, BlockState state, CropPlan crop, Object npc) {
        if (!state.is(crop.cropBlock) || !isStandardCropMature(state)) {
            return null;
        }
        List<ItemStack> drops = new java.util.ArrayList<>(getDrops(level, pos, state, npc));
        boolean replantFromDrops = level.getBlockState(pos.below()).getBlock() instanceof FarmBlock
                && reserveSeedFromDrops(drops, new ItemStack(crop.seed));
        return new HarvestPlan(HarvestKind.REGULAR, pos.immutable(), state, drops, new ItemStack(crop.seed), crop.cropBlock, replantFromDrops);
    }

    private static HarvestPlan planStemFruit(ServerLevel level, BlockPos stemPos, Block cropBlock) {
        BlockState stemState = level.getBlockState(stemPos);
        if (!isStemCropReadyForHarvest(stemState, cropBlock)) {
            return null;
        }
        Block fruitBlock = resolveStemFruitBlock(cropBlock);
        if (fruitBlock == null) {
            return null;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos fruitPos = stemPos.relative(direction);
            BlockState fruitState = level.getBlockState(fruitPos);
            if (!fruitState.is(fruitBlock)) {
                continue;
            }
            List<ItemStack> drops = new java.util.ArrayList<>(Block.getDrops(fruitState, level, fruitPos, null));
            return new HarvestPlan(HarvestKind.STEM_FRUIT, fruitPos.immutable(), fruitState, drops, ItemStack.EMPTY, null, false);
        }
        return null;
    }

    private static void executeHarvestPlan(ServerLevel level, BlockPos boxPos, HarvestPlan plan) {
        if (!isHarvestPlanStillValid(level, plan)) {
            return;
        }
        for (BlockPos chestPos : nearbyContainers(level, boxPos)) {
            HarvestAttempt attempt = tryExecuteHarvestPlan(level, chestPos, plan);
            if (attempt != HarvestAttempt.NOT_ACCEPTED) {
                return;
            }
        }
    }

    private static HarvestAttempt tryExecuteHarvestPlan(ServerLevel level, BlockPos chestPos, HarvestPlan plan) {
        if (plan.kind == HarvestKind.RIGHT_CLICK) {
            SimulatedInventory inventory = simulateActualInventory(level, chestPos);
            if (inventory == null || !inventory.insertAll(plan.drops)) {
                return tryExecuteItemHandlerHarvestPlan(level, chestPos, plan);
            }
            inventory.apply(level, chestPos);
            resetCropAge(level, plan.pos, plan.state);
            return HarvestAttempt.COMPLETED;
        }
        if (plan.kind == HarvestKind.REGULAR) {
            SimulatedInventory inventory = simulateActualInventory(level, chestPos);
            if (inventory == null || !inventory.insertAll(plan.drops)) {
                return tryExecuteItemHandlerHarvestPlan(level, chestPos, plan);
            }
            inventory.apply(level, chestPos);
            level.setBlockAndUpdate(plan.pos, Blocks.AIR.defaultBlockState());
            if (plan.replantBlock != null && level.getBlockState(plan.pos.below()).getBlock() instanceof FarmBlock) {
                if (plan.replantFromDrops || consume(level, chestPos, plan.seedTemplate.copy())) {
                    level.setBlockAndUpdate(plan.pos, plan.replantBlock.defaultBlockState());
                }
            }
            return HarvestAttempt.COMPLETED;
        }
        if (plan.kind == HarvestKind.STEM_FRUIT) {
            SimulatedInventory inventory = simulateActualInventory(level, chestPos);
            if (inventory == null || !inventory.insertAll(plan.drops)) {
                return tryExecuteItemHandlerHarvestPlan(level, chestPos, plan);
            }
            inventory.apply(level, chestPos);
            level.setBlockAndUpdate(plan.pos, Blocks.AIR.defaultBlockState());
            return HarvestAttempt.COMPLETED;
        }
        return HarvestAttempt.NOT_ACCEPTED;
    }

    private static HarvestAttempt tryExecuteItemHandlerHarvestPlan(ServerLevel level, BlockPos chestPos, HarvestPlan plan) {
        BlockEntity be = level.getBlockEntity(chestPos);
        if (be == null || be instanceof Container) {
            return HarvestAttempt.NOT_ACCEPTED;
        }
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER).map(handler -> tryExecuteItemHandlerHarvestPlan(level, handler, plan)).orElse(HarvestAttempt.NOT_ACCEPTED);
    }

    private static HarvestAttempt tryExecuteItemHandlerHarvestPlan(ServerLevel level, IItemHandler handler, HarvestPlan plan) {
        if (!canInsertAllItemHandler(handler, plan.drops)) {
            return HarvestAttempt.NOT_ACCEPTED;
        }
        if (!insertAllItemHandler(handler, plan.drops)) {
            return HarvestAttempt.ABORTED;
        }
        if (plan.kind == HarvestKind.RIGHT_CLICK) {
            resetCropAge(level, plan.pos, plan.state);
            return HarvestAttempt.COMPLETED;
        }
        if (plan.kind == HarvestKind.REGULAR) {
            level.setBlockAndUpdate(plan.pos, Blocks.AIR.defaultBlockState());
            if (plan.replantBlock != null && level.getBlockState(plan.pos.below()).getBlock() instanceof FarmBlock) {
                if (plan.replantFromDrops || consumeItemHandler(handler, plan.seedTemplate.copy())) {
                    level.setBlockAndUpdate(plan.pos, plan.replantBlock.defaultBlockState());
                }
            }
            return HarvestAttempt.COMPLETED;
        }
        if (plan.kind == HarvestKind.STEM_FRUIT) {
            level.setBlockAndUpdate(plan.pos, Blocks.AIR.defaultBlockState());
            return HarvestAttempt.COMPLETED;
        }
        return HarvestAttempt.NOT_ACCEPTED;
    }

    private static boolean isHarvestPlanStillValid(ServerLevel level, HarvestPlan plan) {
        BlockState current = level.getBlockState(plan.pos);
        if (plan.kind == HarvestKind.RIGHT_CLICK) {
            return current.is(plan.state.getBlock()) && isRightClickCropMature(current);
        }
        if (plan.kind == HarvestKind.REGULAR) {
            return plan.replantBlock != null && current.is(plan.replantBlock) && isStandardCropMature(current);
        }
        if (plan.kind == HarvestKind.STEM_FRUIT) {
            return current.is(plan.state.getBlock());
        }
        return false;
    }

    private static List<ItemStack> getDrops(ServerLevel level, BlockPos pos, BlockState state, Object npc) {
        if (npc instanceof net.minecraft.world.entity.Entity entity) {
            return Block.getDrops(state, level, pos, null, entity, ItemStack.EMPTY);
        }
        return Block.getDrops(state, level, pos, null);
    }

    private static boolean reserveSeedFromDrops(List<ItemStack> drops, ItemStack seedTemplate) {
        if (seedTemplate.isEmpty()) {
            return false;
        }
        for (ItemStack drop : drops) {
            if (ItemStack.isSameItemSameTags(drop, seedTemplate) && drop.getCount() > 0) {
                drop.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static boolean canInsertAllItemHandler(IItemHandler handler, List<ItemStack> stacks) {
        List<ItemStack> remainingStacks = new java.util.ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                remainingStacks.add(stack.copy());
            }
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack simulatedSlot = handler.getStackInSlot(slot).copy();
            for (ItemStack stack : remainingStacks) {
                if (stack.isEmpty()) {
                    continue;
                }
                if (!simulatedSlot.isEmpty() && !ItemStack.isSameItemSameTags(simulatedSlot, stack)) {
                    continue;
                }
                ItemStack rejected = handler.insertItem(slot, stack, true);
                int acceptedByHandler = stack.getCount() - rejected.getCount();
                int slotLimit = Math.min(stack.getMaxStackSize(), handler.getSlotLimit(slot));
                int simulatedSpace = simulatedSlot.isEmpty() ? slotLimit : Math.max(0, slotLimit - simulatedSlot.getCount());
                int moved = Math.min(acceptedByHandler, simulatedSpace);
                if (moved <= 0) {
                    continue;
                }
                if (simulatedSlot.isEmpty()) {
                    simulatedSlot = stack.copy();
                    simulatedSlot.setCount(moved);
                } else {
                    simulatedSlot.grow(moved);
                }
                stack.shrink(moved);
            }
        }
        for (ItemStack stack : remainingStacks) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean insertAllItemHandler(IItemHandler handler, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            ItemStack remaining = stack.copy();
            for (int i = 0; i < handler.getSlots() && !remaining.isEmpty(); i++) {
                remaining = handler.insertItem(i, remaining, false);
            }
            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean consumeItemHandler(IItemHandler handler, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        int remaining = stack.getCount();
        for (int i = 0; i < handler.getSlots() && remaining > 0; i++) {
            ItemStack existing = handler.getStackInSlot(i);
            if (ItemStack.isSameItemSameTags(existing, stack)) {
                remaining -= handler.extractItem(i, remaining, false).getCount();
            }
        }
        return remaining <= 0;
    }

    private static boolean isRightClickHarvestCrop(Block block) {
        String blockName = block.getDescriptionId();
        return block == Blocks.SWEET_BERRY_BUSH
                || blockName.contains("berry")
                || blockName.contains("tomato")
                || blockName.contains("pepper")
                || blockName.contains("eggplant")
                || blockName.contains("cucumber")
                || blockName.contains("corn")
                || block instanceof BushBlock && !(block instanceof CropBlock);
    }

    private static boolean isRightClickCropMature(BlockState state) {
        if (state.is(Blocks.SWEET_BERRY_BUSH)) {
            return state.getValue(SweetBerryBushBlock.AGE) >= 2;
        }
        return isAgePropertyAtMax(state);
    }

    private static boolean isStandardCropMature(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock cropBlock) {
            return cropBlock.isMaxAge(state);
        }
        return block instanceof StemBlock && isAgePropertyAtMax(state);
    }

    private static boolean isStemCropReadyForHarvest(BlockState state, Block cropBlock) {
        if (state.getBlock() instanceof StemBlock) {
            return isAgePropertyAtMax(state);
        }
        Block attachedStemBlock = resolveAttachedStemBlock(cropBlock);
        return attachedStemBlock != null && state.is(attachedStemBlock);
    }

    private static boolean isAgePropertyAtMax(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (!property.getName().equals("age")) {
                continue;
            }
            Comparable<?> value = state.getValue(property);
            if (!(value instanceof Integer age)) {
                continue;
            }
            int maxAge = age;
            for (Comparable<?> possibleValue : property.getPossibleValues()) {
                if (possibleValue instanceof Integer intValue && intValue > maxAge) {
                    maxAge = intValue;
                }
            }
            return age >= maxAge;
        }
        return false;
    }

    private static void resetCropAge(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(Blocks.SWEET_BERRY_BUSH)) {
            level.setBlockAndUpdate(pos, state.setValue(SweetBerryBushBlock.AGE, 0));
            return;
        }
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals("age") && property instanceof IntegerProperty ageProperty && property.getPossibleValues().contains(0)) {
                level.setBlockAndUpdate(pos, state.setValue(ageProperty, 0));
                return;
            }
        }
    }

    private static Block resolveStemFruitBlock(Block cropBlock) {
        if (cropBlock == Blocks.MELON_STEM) {
            return Blocks.MELON;
        }
        if (cropBlock == Blocks.PUMPKIN_STEM) {
            return Blocks.PUMPKIN;
        }
        return null;
    }

    private static Block resolveAttachedStemBlock(Block cropBlock) {
        if (cropBlock == Blocks.MELON_STEM) {
            return Blocks.ATTACHED_MELON_STEM;
        }
        if (cropBlock == Blocks.PUMPKIN_STEM) {
            return Blocks.ATTACHED_PUMPKIN_STEM;
        }
        return null;
    }

    private static void simulateInsert(List<SimulatedSlot> slots, ItemStack stack, int defaultSlotLimit) {
        for (SimulatedSlot slot : slots) {
            ItemStack existing = slot.stack;
            if (stack.isEmpty()) {
                return;
            }
            if (!slot.canPlace(stack)) {
                continue;
            }
            int slotLimit = Math.min(existing.getMaxStackSize(), defaultSlotLimit);
            if (ItemStack.isSameItemSameTags(existing, stack) && existing.getCount() < slotLimit) {
                int moved = Math.min(stack.getCount(), slotLimit - existing.getCount());
                existing.grow(moved);
                stack.shrink(moved);
            }
        }
        for (int i = 0; i < slots.size() && !stack.isEmpty(); i++) {
            SimulatedSlot slot = slots.get(i);
            ItemStack existing = slot.stack;
            if (existing.isEmpty()) {
                if (!slot.canPlace(stack)) {
                    continue;
                }
                int moved = Math.min(stack.getCount(), Math.min(stack.getMaxStackSize(), defaultSlotLimit));
                ItemStack copy = stack.copy();
                copy.setCount(moved);
                slot.stack = copy;
                stack.shrink(moved);
            }
        }
    }

    private static SimulatedInventory simulateActualInventory(ServerLevel level, BlockPos chestPos) {
        BlockEntity be = level.getBlockEntity(chestPos);
        if (be == null) {
            return null;
        }
        if (be instanceof Container container) {
            return SimulatedInventory.create(container);
        }
        return null;
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

    private static boolean usableChest(ServerLevel level, BlockPos pos) {
        if (pos == null) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof Container || be != null && be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
    }

    private static boolean hasAnyInsertSpace(ServerLevel level, BlockPos chestPos) {
        BlockEntity be = level.getBlockEntity(chestPos);
        if (be == null) {
            return false;
        }
        if (be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (stack.isEmpty() || stack.getCount() < Math.min(stack.getMaxStackSize(), container.getMaxStackSize())) {
                    return true;
                }
            }
        }
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER).map(LenientFarmingServer::hasAnyInsertSpace).orElse(false);
    }

    private static boolean hasAnyInsertSpace(IItemHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) {
                return true;
            }
            if (stack.getCount() >= Math.min(stack.getMaxStackSize(), handler.getSlotLimit(i))) {
                continue;
            }
            ItemStack probe = stack.copy();
            probe.setCount(1);
            if (handler.insertItem(i, probe, true).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean consumeNearbyItem(ServerLevel level, BlockPos boxPos, ItemStack cost) {
        for (BlockPos chestPos : nearbyContainers(level, boxPos)) {
            if (consume(level, chestPos, cost.copy())) {
                return true;
            }
        }
        return false;
    }

    private static boolean consumeNearbyBuildingMaterial(ServerLevel level, BlockPos boxPos, BlockState targetState) {
        for (BlockPos chestPos : nearbyContainers(level, boxPos)) {
            if (consumeBuildingMaterial(level, chestPos, targetState)) {
                return true;
            }
        }
        return false;
    }

    private static int countNearbyItem(ServerLevel level, BlockPos boxPos, ItemStack template) {
        int count = 0;
        for (BlockPos chestPos : nearbyContainers(level, boxPos)) {
            count += countItem(level, chestPos, template);
        }
        return count;
    }

    private static int countNearbyBuildingMaterial(ServerLevel level, BlockPos boxPos, BlockState targetState) {
        int count = 0;
        for (BlockPos chestPos : nearbyContainers(level, boxPos)) {
            count += countBuildingMaterial(level, chestPos, targetState);
        }
        return count;
    }

    private static List<BlockPos> nearbyContainers(ServerLevel level, BlockPos boxPos) {
        java.util.LinkedHashSet<BlockPos> result = new java.util.LinkedHashSet<>();
        int horizontalRadius = containerSearchHorizontalRadius();
        int verticalRadius = containerSearchVerticalRadius();
        for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
            for (int y = -verticalRadius; y <= verticalRadius; y++) {
                for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                    BlockPos pos = boxPos.offset(x, y, z);
                    if (usableChest(level, pos)) {
                        result.add(pos.immutable());
                        BlockPos otherHalf = otherDoubleChestHalf(level, pos);
                        if (otherHalf != null && usableChest(level, otherHalf)) {
                            result.add(otherHalf.immutable());
                        }
                    }
                }
            }
        }
        return new java.util.ArrayList<>(result);
    }

    private static BlockPos otherDoubleChestHalf(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || !state.hasProperty(ChestBlock.TYPE) || !state.hasProperty(ChestBlock.FACING)) {
            return null;
        }
        ChestType type = state.getValue(ChestBlock.TYPE);
        if (type == ChestType.SINGLE) {
            return null;
        }
        Direction facing = state.getValue(ChestBlock.FACING);
        Direction side = type == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
        BlockPos other = pos.relative(side);
        BlockState otherState = level.getBlockState(other);
        return otherState.getBlock() instanceof ChestBlock ? other : null;
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
            if (countItem(level, chestPos, cost) < cost.getCount()) {
                return false;
            }
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
        if (countItem(handler, cost) < cost.getCount()) {
            return false;
        }
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

    private static boolean consumeBuildingMaterial(ServerLevel level, BlockPos chestPos, BlockState targetState) {
        if (chestPos == null || targetState.isAir()) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(chestPos);
        if (be == null) {
            return false;
        }
        if (be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && canUseItemForBlock(stack, targetState)) {
                    stack.shrink(1);
                    container.setChanged();
                    return true;
                }
            }
        }
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER).map(handler -> consumeBuildingMaterial(handler, targetState)).orElse(false);
    }

    private static boolean consumeBuildingMaterial(IItemHandler handler, BlockState targetState) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty() && canUseItemForBlock(stack, targetState)) {
                ItemStack extracted = handler.extractItem(i, 1, false);
                if (!extracted.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
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

    private static int countBuildingMaterial(ServerLevel level, BlockPos chestPos, BlockState targetState) {
        if (chestPos == null || targetState.isAir()) {
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
                if (!stack.isEmpty() && canUseItemForBlock(stack, targetState)) {
                    count += stack.getCount();
                }
            }
            return count;
        }
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER).map(handler -> countBuildingMaterial(handler, targetState)).orElse(0);
    }

    private static int countBuildingMaterial(IItemHandler handler, BlockState targetState) {
        int count = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty() && canUseItemForBlock(stack, targetState)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean canUseItemForBlock(ItemStack stack, BlockState targetState) {
        try {
            if ((Boolean) canUseItemForBlockMethod().invoke(null, stack, targetState)) {
                return true;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return isSameMaterialFamily(stack, targetState);
    }

    private static boolean isSameMaterialFamily(ItemStack stack, BlockState targetState) {
        Item item = targetState.getBlock().asItem();
        if (item != Items.AIR && ItemStack.isSameItemSameTags(stack, new ItemStack(item))) {
            return true;
        }
        ResourceLocation providedId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ResourceLocation requiredId = BuiltInRegistries.ITEM.getKey(item);
        if (providedId == null || requiredId == null || !providedId.getNamespace().equals(requiredId.getNamespace())) {
            return false;
        }
        String provided = providedId.getPath();
        String required = requiredId.getPath();
        return sameSuffix(provided, required, "_trapdoor")
                || sameSuffix(provided, required, "_fence")
                || sameSuffix(provided, required, "_fence_gate")
                || sameSuffix(provided, required, "_slab")
                || sameSuffix(provided, required, "_stairs")
                || sameSuffix(provided, required, "_planks")
                || sameSuffix(provided, required, "_log")
                || sameSuffix(provided, required, "_wood")
                || sameSuffix(provided, required, "_leaves")
                || sameSuffix(provided, required, "_wall")
                || sameSuffix(provided, required, "_pane");
    }

    private static boolean sameSuffix(String provided, String required, String suffix) {
        return provided.endsWith(suffix) && required.endsWith(suffix);
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

    private static boolean isFarmerAbleToWork(MinecraftServer server, BlockPos boxPos) {
        try {
            Object uuid = getHiredFarmerMethod().invoke(null, boxPos);
            if (uuid == null) {
                return false;
            }
            Object npc = findNpcByUuidMethod().invoke(null, server, uuid);
            if (npc == null || Boolean.TRUE.equals(npc.getClass().getMethod("isSleeping").invoke(npc))) {
                return false;
            }
            Object workStatus = npc.getClass().getMethod("getWorkStatus").invoke(npc);
            Object workSubState = npc.getClass().getMethod("getWorkSubState").invoke(npc);
            return hasEnumName(workStatus, "WORKING") && hasEnumName(workSubState, "WORKING");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return hasHiredFarmer(boxPos);
        }
    }

    private static boolean hasEnumName(Object value, String name) {
        return value instanceof Enum<?> enumValue && enumValue.name().equals(name);
    }

    private static int workIntervalTicks() {
        return BeautifulFarmConfig.WORK_INTERVAL_TICKS.get();
    }

    private static int statusIntervalTicks() {
        return BeautifulFarmConfig.STATUS_INTERVAL_TICKS.get();
    }

    private static int harvestIntervalTicks() {
        return BeautifulFarmConfig.HARVEST_INTERVAL_TICKS.get();
    }

    private static int minHarvestPlansPerTick() {
        return BeautifulFarmConfig.MIN_HARVEST_PLANS_PER_TICK.get();
    }

    private static int containerSearchHorizontalRadius() {
        return BeautifulFarmConfig.CONTAINER_SEARCH_HORIZONTAL_RADIUS.get();
    }

    private static int containerSearchVerticalRadius() {
        return BeautifulFarmConfig.CONTAINER_SEARCH_VERTICAL_RADIUS.get();
    }

    private static int droppedItemPickupDelayTicks() {
        return BeautifulFarmConfig.DROPPED_ITEM_PICKUP_DELAY_TICKS.get();
    }

    private static void ensureWorkLoaded(MinecraftServer server) {
        if (server == null || loadedServer == server) {
            return;
        }
        loadedServer = server;
        WORK.clear();
        HARVEST_JOBS.clear();
        workData(server).copyTo(WORK);
    }

    private static void persistWork(MinecraftServer server, WorkState state) {
        if (server == null || state == null || loadedServer != server) {
            return;
        }
        workData(server).put(state);
    }

    private static void removePersistedWork(MinecraftServer server, WorkKey key) {
        if (server == null || loadedServer != server) {
            return;
        }
        workData(server).remove(key);
    }

    private static WorkSavedData workData(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(WorkSavedData::load, WorkSavedData::new, WORK_DATA_ID);
    }

    private static WorkKey workKey(ServerLevel level, BlockPos boxPos) {
        return new WorkKey(level.dimension().location().toString(), boxPos.immutable());
    }

    private static boolean isFarmlandBoxBlock(Block block) {
        try {
            return farmlandBoxBlockClass().isInstance(block);
        } catch (ClassNotFoundException | LinkageError ignored) {
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

    private static void clearOriginalFarmWork(MinecraftServer server, BlockPos boxPos) {
        clearOriginalFarmField(() -> clearSelectedCropMethod().invoke(null, boxPos));
        clearOriginalFarmField(() -> clearSelectedAreaMethod().invoke(null, boxPos));
        clearOriginalFarmField(() -> clearSelectedPlotMethod().invoke(null, boxPos));
        saveData(server);
    }

    private static void clearOriginalFarmField(ReflectiveAction action) {
        try {
            action.run();
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    @FunctionalInterface
    private interface ReflectiveAction {
        void run() throws ReflectiveOperationException;
    }

    private static String getCrop(BlockPos boxPos) {
        try {
            Object crop = getSelectedCropMethod().invoke(null, boxPos);
            return crop instanceof String value ? value : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static void setArea(BlockPos boxPos, int areaSize) {
        try {
            setSelectedAreaMethod().invoke(null, boxPos, areaSize);
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

    private static Method clearSelectedCropMethod() throws ReflectiveOperationException {
        if (clearSelectedCrop == null) clearSelectedCrop = farmlandDataClass().getMethod("clearSelectedCrop", BlockPos.class);
        return clearSelectedCrop;
    }

    private static Method getSelectedCropMethod() throws ReflectiveOperationException {
        if (getSelectedCrop == null) getSelectedCrop = farmlandDataClass().getMethod("getSelectedCrop", BlockPos.class);
        return getSelectedCrop;
    }

    private static Method clearSelectedAreaMethod() throws ReflectiveOperationException {
        if (clearSelectedArea == null) clearSelectedArea = farmlandDataClass().getMethod("clearSelectedArea", BlockPos.class);
        return clearSelectedArea;
    }

    private static Method clearSelectedPlotMethod() throws ReflectiveOperationException {
        if (clearSelectedPlot == null) clearSelectedPlot = farmlandDataClass().getMethod("clearSelectedPlot", BlockPos.class);
        return clearSelectedPlot;
    }

    private static Method getHiredFarmerMethod() throws ReflectiveOperationException {
        if (getHiredFarmer == null) getHiredFarmer = farmlandDataClass().getMethod("getHiredFarmer", BlockPos.class);
        return getHiredFarmer;
    }

    private static Method findNpcByUuidMethod() throws ReflectiveOperationException {
        if (findNpcByUuid == null) findNpcByUuid = farmlandDataClass().getMethod("findNPCByUuid", MinecraftServer.class, java.util.UUID.class);
        return findNpcByUuid;
    }

    private static Method canUseItemForBlockMethod() throws ReflectiveOperationException {
        if (canUseItemForBlock == null) {
            canUseItemForBlock = Class.forName("com.xiaoliang.simukraft.utils.MaterialManager").getMethod("canUseItemForBlock", ItemStack.class, BlockState.class);
        }
        return canUseItemForBlock;
    }

    private static Constructor<?> farmlandPlotConstructor() throws ReflectiveOperationException {
        if (farmlandPlotConstructor == null) {
            farmlandPlotConstructor = Class.forName("com.xiaoliang.simukraft.farmland.FarmlandPlot").getConstructor(BlockPos.class, BlockPos.class);
        }
        return farmlandPlotConstructor;
    }

    private static Class<?> farmlandBoxBlockClass() throws ClassNotFoundException {
        if (farmlandBoxBlockClass == null) {
            farmlandBoxBlockClass = Class.forName("com.xiaoliang.simukraft.block.NSUKFarmlandBoxBlock");
        }
        return farmlandBoxBlockClass;
    }

    private static final class WorkSavedData extends SavedData {
        private static final String TAG_WORK = "work";
        private static final String TAG_DIMENSION = "dimension";
        private static final String TAG_BOX_X = "boxX";
        private static final String TAG_BOX_Y = "boxY";
        private static final String TAG_BOX_Z = "boxZ";
        private static final String TAG_MIN_X = "minX";
        private static final String TAG_MIN_Y = "minY";
        private static final String TAG_MIN_Z = "minZ";
        private static final String TAG_MAX_X = "maxX";
        private static final String TAG_MAX_Y = "maxY";
        private static final String TAG_MAX_Z = "maxZ";
        private static final String TAG_CROP = "crop";
        private static final String TAG_TOP_CLEARED = "topCleared";
        private static final String TAG_MAINTENANCE_MODE = "maintenanceMode";
        private static final String TAG_PAUSED = "paused";
        private static final String TAG_IDLE_TICKS = "idleTicks";

        private final Map<WorkKey, WorkState> savedWork = new HashMap<>();

        private static WorkSavedData load(CompoundTag tag) {
            WorkSavedData data = new WorkSavedData();
            ListTag list = tag.getList(TAG_WORK, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                WorkState state = readWorkState(list.getCompound(i));
                if (state != null) {
                    data.savedWork.put(state.key(), state);
                }
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (WorkState state : savedWork.values()) {
                list.add(writeWorkState(state));
            }
            tag.put(TAG_WORK, list);
            return tag;
        }

        private void copyTo(Map<WorkKey, WorkState> target) {
            target.clear();
            target.putAll(savedWork);
        }

        private void put(WorkState state) {
            savedWork.put(state.key(), state);
            setDirty();
        }

        private void remove(WorkKey key) {
            if (savedWork.remove(key) != null) {
                setDirty();
            }
        }

        private static CompoundTag writeWorkState(WorkState state) {
            CompoundTag tag = new CompoundTag();
            tag.putString(TAG_DIMENSION, state.dimension);
            tag.putInt(TAG_BOX_X, state.boxPos.getX());
            tag.putInt(TAG_BOX_Y, state.boxPos.getY());
            tag.putInt(TAG_BOX_Z, state.boxPos.getZ());
            tag.putInt(TAG_MIN_X, state.bounds.min.getX());
            tag.putInt(TAG_MIN_Y, state.bounds.min.getY());
            tag.putInt(TAG_MIN_Z, state.bounds.min.getZ());
            tag.putInt(TAG_MAX_X, state.bounds.max.getX());
            tag.putInt(TAG_MAX_Y, state.bounds.max.getY());
            tag.putInt(TAG_MAX_Z, state.bounds.max.getZ());
            tag.putString(TAG_CROP, state.crop.selectionId);
            tag.putBoolean(TAG_TOP_CLEARED, state.topCleared);
            tag.putBoolean(TAG_MAINTENANCE_MODE, state.maintenanceMode);
            tag.putBoolean(TAG_PAUSED, state.paused);
            tag.putInt(TAG_IDLE_TICKS, state.idleTicks);
            return tag;
        }

        private static WorkState readWorkState(CompoundTag tag) {
            CropPlan crop = CropPlan.resolve(tag.getString(TAG_CROP));
            if (crop == null || !tag.contains(TAG_DIMENSION)) {
                return null;
            }
            BlockPos boxPos = new BlockPos(tag.getInt(TAG_BOX_X), tag.getInt(TAG_BOX_Y), tag.getInt(TAG_BOX_Z));
            PlotBounds bounds = new PlotBounds(
                    new BlockPos(tag.getInt(TAG_MIN_X), tag.getInt(TAG_MIN_Y), tag.getInt(TAG_MIN_Z)),
                    new BlockPos(tag.getInt(TAG_MAX_X), tag.getInt(TAG_MAX_Y), tag.getInt(TAG_MAX_Z))
            );
            WorkState state = new WorkState(tag.getString(TAG_DIMENSION), boxPos, bounds, crop, tag.getBoolean(TAG_TOP_CLEARED));
            state.maintenanceMode = tag.getBoolean(TAG_MAINTENANCE_MODE);
            state.paused = tag.getBoolean(TAG_PAUSED);
            state.idleTicks = tag.getInt(TAG_IDLE_TICKS);
            return state;
        }
    }

    private static final class Needs {
        private int dirt;
        private int seeds;
        private final Map<MaterialNeedKey, Integer> materials = new LinkedHashMap<>();
    }

    private enum HarvestKind {
        REGULAR,
        RIGHT_CLICK,
        STEM_FRUIT
    }

    private enum HarvestAttempt {
        NOT_ACCEPTED,
        COMPLETED,
        ABORTED
    }

    private static final class HarvestPlan {
        private final HarvestKind kind;
        private final BlockPos pos;
        private final BlockState state;
        private final List<ItemStack> drops;
        private final ItemStack seedTemplate;
        private final Block replantBlock;
        private final boolean replantFromDrops;

        private HarvestPlan(HarvestKind kind, BlockPos pos, BlockState state, List<ItemStack> drops, ItemStack seedTemplate, Block replantBlock, boolean replantFromDrops) {
            this.kind = kind;
            this.pos = pos;
            this.state = state;
            this.drops = drops;
            this.seedTemplate = seedTemplate == null ? ItemStack.EMPTY : seedTemplate;
            this.replantBlock = replantBlock;
            this.replantFromDrops = replantFromDrops;
        }
    }

    private static final class HarvestJob {
        private final String dimension;
        private final BlockPos boxPos;
        private final Queue<HarvestPlan> pending;

        private HarvestJob(String dimension, BlockPos boxPos, Queue<HarvestPlan> pending) {
            this.dimension = dimension;
            this.boxPos = boxPos;
            this.pending = pending;
        }

        private WorkKey key() {
            return new WorkKey(dimension, boxPos);
        }
    }

    private static final class SimulatedInventory {
        private final java.util.ArrayList<SimulatedSlot> slots;
        private final int defaultSlotLimit;

        private SimulatedInventory(java.util.ArrayList<SimulatedSlot> slots, int defaultSlotLimit) {
            this.slots = slots;
            this.defaultSlotLimit = defaultSlotLimit;
        }

        private static SimulatedInventory create(ServerLevel level, BlockPos chestPos) {
            BlockEntity be = level.getBlockEntity(chestPos);
            if (be == null) {
                return null;
            }
            if (be instanceof Container container) {
                return create(container);
            }
            return null;
        }

        private static SimulatedInventory create(Container container) {
            java.util.ArrayList<SimulatedSlot> slots = new java.util.ArrayList<>();
            for (int i = 0; i < container.getContainerSize(); i++) {
                int slot = i;
                slots.add(new SimulatedSlot(container.getItem(i).copy(), stack -> container.canPlaceItem(slot, stack)));
            }
            return new SimulatedInventory(slots, container.getMaxStackSize());
        }

        private boolean canConsume(ItemStack stack) {
            return stack.isEmpty() || count(stack) >= stack.getCount();
        }

        private void consume(ItemStack stack) {
            if (stack.isEmpty()) {
                return;
            }
            int remaining = stack.getCount();
            for (SimulatedSlot slot : slots) {
                if (remaining <= 0) {
                    return;
                }
                if (ItemStack.isSameItemSameTags(slot.stack, stack)) {
                    int taken = Math.min(remaining, slot.stack.getCount());
                    slot.stack.shrink(taken);
                    remaining -= taken;
                }
            }
        }

        private boolean canInsertAll(List<ItemStack> stacks) {
            SimulatedInventory copy = copy();
            return copy.insertAll(stacks);
        }

        private boolean insertAll(List<ItemStack> stacks) {
            for (ItemStack stack : stacks) {
                if (stack.isEmpty()) {
                    continue;
                }
                ItemStack remaining = stack.copy();
                simulateInsert(slots, remaining, defaultSlotLimit);
                if (!remaining.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        private int count(ItemStack template) {
            int total = 0;
            for (SimulatedSlot slot : slots) {
                if (ItemStack.isSameItemSameTags(slot.stack, template)) {
                    total += slot.stack.getCount();
                }
            }
            return total;
        }

        private SimulatedInventory copy() {
            java.util.ArrayList<SimulatedSlot> copied = new java.util.ArrayList<>();
            for (SimulatedSlot slot : slots) {
                copied.add(slot.copy());
            }
            return new SimulatedInventory(copied, defaultSlotLimit);
        }

        private void apply(ServerLevel level, BlockPos chestPos) {
            BlockEntity be = level.getBlockEntity(chestPos);
            if (!(be instanceof Container container)) {
                return;
            }
            int size = Math.min(container.getContainerSize(), slots.size());
            for (int i = 0; i < size; i++) {
                container.setItem(i, slots.get(i).stack.copy());
            }
            container.setChanged();
        }
    }

    private static final class SimulatedSlot {
        private ItemStack stack;
        private final java.util.function.Predicate<ItemStack> canPlace;

        private SimulatedSlot(ItemStack stack, java.util.function.Predicate<ItemStack> canPlace) {
            this.stack = stack;
            this.canPlace = canPlace;
        }

        private boolean canPlace(ItemStack stack) {
            return canPlace.test(stack);
        }

        private SimulatedSlot copy() {
            return new SimulatedSlot(stack.copy(), canPlace);
        }
    }

    private record MaterialNeedKey(BlockState state) {
    }

    static final class PlotBounds {
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

        private boolean containsHarvestDrop(BlockPos pos) {
            return pos.getX() >= min.getX() - 1
                    && pos.getX() <= max.getX() + 1
                    && pos.getY() >= min.getY()
                    && pos.getY() <= max.getY() + 2
                    && pos.getZ() >= min.getZ() - 1
                    && pos.getZ() <= max.getZ() + 1;
        }
    }

    static final class CropPlan {
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
            String id = normalize(crop, true);
            return resolveNormalized(id);
        }

        private static CropPlan resolveStrict(String crop) {
            String id = normalize(crop, false);
            return resolveNormalized(id);
        }

        private static CropPlan resolveNormalized(String id) {
            if (id == null) {
                return null;
            }
            ResourceLocation location = ResourceLocation.tryParse(id);
            if (location == null) {
                return null;
            }
            // 这里复刻原模组 CropRegistry.resolve 的核心逻辑，避免没 WORK 时把未知模组作物错误回退成小麦。
            Item item = ForgeRegistries.ITEMS.getValue(location);
            if (item == null || item == Items.AIR) {
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

        private static String normalize(String crop, boolean defaultWheat) {
            if (crop == null || crop.isBlank()) {
                return defaultWheat ? "minecraft:wheat_seeds" : null;
            }
            String value = crop.trim().toLowerCase(java.util.Locale.ROOT);
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
