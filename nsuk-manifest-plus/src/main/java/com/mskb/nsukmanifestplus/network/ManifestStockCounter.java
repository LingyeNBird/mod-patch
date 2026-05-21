package com.mskb.nsukmanifestplus.network;

import com.mskb.nsukmanifestplus.config.ManifestPlusConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ManifestStockCounter {
    private static final int AREA_RADIUS_XZ = 5;
    private static final int AREA_RADIUS_Y = 2;

    private static final Map<CacheKey, CacheEntry> CACHE = new HashMap<>();
    private static final Map<CacheKey, ScanTask> ACTIVE_TASKS = new HashMap<>();
    private static final ArrayDeque<ScanTask> TASK_QUEUE = new ArrayDeque<>();

    private static boolean registered;
    private static Method commercialStockAtPos;
    private static Method stockCurrentStock;
    private static Method materialAcceptedItemIds;
    private static Method alternativeMaterials;

    private ManifestStockCounter() {
    }

    static void register() {
        if (!registered) {
            registered = true;
            MinecraftForge.EVENT_BUS.addListener(ManifestStockCounter::onServerTick);
        }
    }

    static void request(ServerPlayer player, ServerLevel level, BlockPos sourcePos, String sourceType, List<String> itemIds, long requestId) {
        Set<String> wanted = sanitize(itemIds);
        if (level == null || sourcePos == null || wanted.isEmpty()) {
            ManifestPlusNetwork.sendToPlayer(player, new ManifestStockResponsePacket(requestId, Map.of()));
            return;
        }

        String normalizedType = sourceType == null ? "" : sourceType.toLowerCase(Locale.ROOT);
        CacheKey key = new CacheKey(level.dimension().location().toString(), sourcePos.immutable(), normalizedType, wanted);
        CacheEntry cacheEntry = CACHE.get(key);
        long now = level.getGameTime();
        if (cacheEntry != null && now - cacheEntry.updatedAt <= ManifestPlusConfig.get().cacheTtlTicks) {
            ManifestPlusNetwork.sendToPlayer(player, new ManifestStockResponsePacket(requestId, cacheEntry.counts));
        }

        ScanTask task = ACTIVE_TASKS.get(key);
        if (task == null) {
            task = new ScanTask(key, level, sourcePos.immutable(), normalizedType, acceptedByItemId(wanted));
            ACTIVE_TASKS.put(key, task);
            TASK_QUEUE.addLast(task);
        }
        task.addListener(player, requestId);
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TASK_QUEUE.isEmpty()) {
            return;
        }

        ManifestPlusConfig.Settings settings = ManifestPlusConfig.get();
        TickBudget budget = new TickBudget(settings.maxBlockPositionsPerTick, settings.maxContainerSlotsPerTick);
        int taskBudget = settings.maxTasksPerTick;
        while (taskBudget-- > 0 && !TASK_QUEUE.isEmpty() && budget.hasAny()) {
            ScanTask task = TASK_QUEUE.removeFirst();
            if (task.step(settings, budget)) {
                ACTIVE_TASKS.remove(task.key);
                CACHE.put(task.key, new CacheEntry(Map.copyOf(task.counts), task.level.getGameTime()));
                task.sendResult();
            } else {
                TASK_QUEUE.addLast(task);
            }
        }
    }

    private static Map<String, Set<String>> acceptedByItemId(Set<String> itemIds) {
        Map<String, Set<String>> result = new HashMap<>();
        for (String itemId : itemIds) {
            result.put(itemId, acceptedItemIds(itemId));
        }
        return result;
    }

    private static Set<String> acceptedItemIds(String itemId) {
        Set<String> result = new LinkedHashSet<>();
        result.add(itemId);
        addConfiguredAlternatives(itemId, result);

        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null) {
            return result;
        }

        Block block = ForgeRegistries.BLOCKS.getValue(key);
        if (block == null || block == Blocks.AIR) {
            return result;
        }

        try {
            if (materialAcceptedItemIds == null) {
                materialAcceptedItemIds = Class.forName("com.xiaoliang.simukraft.utils.MaterialManager")
                        .getMethod("getAcceptedItemIds", BlockState.class);
            }
            Object accepted = materialAcceptedItemIds.invoke(null, block.defaultBlockState());
            if (accepted instanceof Iterable<?> iterable) {
                for (Object value : iterable) {
                    if (value instanceof String acceptedId && !acceptedId.isBlank()) {
                        result.add(acceptedId);
                        addBlockItemId(acceptedId, result);
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // If NSUK internals change, exact item matching remains available.
        }
        return result;
    }

    private static void addConfiguredAlternatives(String itemId, Set<String> result) {
        try {
            if (alternativeMaterials == null) {
                alternativeMaterials = Class.forName("com.xiaoliang.simukraft.config.ServerConfig")
                        .getMethod("getAlternativeMaterials", String.class);
            }
            Object alternatives = alternativeMaterials.invoke(null, itemId);
            if (alternatives instanceof Iterable<?> iterable) {
                for (Object value : iterable) {
                    if (value instanceof String alternativeId && !alternativeId.isBlank()) {
                        result.add(alternativeId);
                        addBlockItemId(alternativeId, result);
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Exact matching and MaterialManager fallbacks remain available.
        }
    }

    private static void addBlockItemId(String blockId, Set<String> result) {
        ResourceLocation key = ResourceLocation.tryParse(blockId);
        if (key == null) {
            return;
        }
        Block block = ForgeRegistries.BLOCKS.getValue(key);
        if (block == null || block == Blocks.AIR) {
            return;
        }
        ResourceLocation itemKey = ForgeRegistries.ITEMS.getKey(block.asItem());
        if (itemKey != null) {
            result.add(itemKey.toString());
        }
    }

    private static Set<String> sanitize(List<String> itemIds) {
        Set<String> result = new LinkedHashSet<>();
        if (itemIds == null) {
            return result;
        }
        for (String itemId : itemIds) {
            if (itemId != null && !itemId.isBlank()) {
                result.add(itemId);
            }
        }
        return result;
    }

    private static void addOtherChestHalf(ServerLevel level, BlockPos pos, ScanTask task) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) {
            return;
        }
        for (Direction direction : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            BlockPos neighbor = pos.relative(direction);
            if (level.isLoaded(neighbor) && level.getBlockState(neighbor).getBlock() instanceof ChestBlock) {
                task.addContainerIfPresent(neighbor);
            }
        }
    }

    private static void addStack(ItemStack stack, Map<String, Set<String>> acceptedByItemId, Map<String, Integer> counts) {
        if (stack.isEmpty()) {
            return;
        }
        String itemId = itemId(stack);
        for (Map.Entry<String, Set<String>> entry : acceptedByItemId.entrySet()) {
            if (entry.getValue().contains(itemId)) {
                counts.merge(entry.getKey(), stack.getCount(), Integer::sum);
            }
        }
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private static void addCommercialStock(BlockPos sourcePos, Map<String, Set<String>> acceptedByItemId, Map<String, Integer> counts) {
        try {
            if (commercialStockAtPos == null) {
                commercialStockAtPos = Class.forName("com.xiaoliang.simukraft.world.CommercialHiredData")
                        .getMethod("getAllStockAtPos", BlockPos.class);
            }
            Object stockMapObject = commercialStockAtPos.invoke(null, sourcePos);
            if (!(stockMapObject instanceof Map<?, ?> stockMap)) {
                return;
            }
            for (Map.Entry<?, ?> entry : stockMap.entrySet()) {
                if (!(entry.getKey() instanceof String stockItemId) || entry.getValue() == null) {
                    continue;
                }
                if (stockCurrentStock == null) {
                    stockCurrentStock = entry.getValue().getClass().getMethod("getCurrentStock");
                }
                Object currentStock = stockCurrentStock.invoke(entry.getValue());
                if (currentStock instanceof Integer amount && amount > 0) {
                    for (Map.Entry<String, Set<String>> acceptedEntry : acceptedByItemId.entrySet()) {
                        if (acceptedEntry.getValue().contains(stockItemId)) {
                            counts.merge(acceptedEntry.getKey(), amount, Integer::sum);
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Commercial stock is a bonus source; nearby containers remain the stable fallback.
        }
    }

    private record CacheKey(String dimension, BlockPos sourcePos, String sourceType, Set<String> itemIds) {
        private CacheKey {
            itemIds = Set.copyOf(itemIds);
        }
    }

    private record CacheEntry(Map<String, Integer> counts, long updatedAt) {
    }

    private record Listener(ServerPlayer player, long requestId) {
    }

    private static final class TickBudget {
        private int blockPositions;
        private int containerSlots;

        private TickBudget(int blockPositions, int containerSlots) {
            this.blockPositions = blockPositions;
            this.containerSlots = containerSlots;
        }

        private boolean hasAny() {
            return blockPositions > 0 || containerSlots > 0;
        }
    }

    private static final class ScanTask {
        private final CacheKey key;
        private final ServerLevel level;
        private final BlockPos sourcePos;
        private final String sourceType;
        private final Map<String, Set<String>> acceptedByItemId;
        private final Map<String, Integer> counts = new HashMap<>();
        private final List<Listener> listeners = new ArrayList<>();
        private final Set<BlockPos> containerSet = new LinkedHashSet<>();
        private final List<BlockPos> containers = new ArrayList<>();

        private int discoverIndex;
        private int containerIndex;
        private int slotIndex;
        private boolean commercialStockScanned;
        private boolean discoveryComplete;
        private int totalSlotCount;
        private int scannedSlots;
        private int totalWorkUnits;
        private int estimatedTicks;
        private int ticksSinceProgress;
        private boolean progressEnabled;

        private ScanTask(CacheKey key, ServerLevel level, BlockPos sourcePos, String sourceType, Map<String, Set<String>> acceptedByItemId) {
            this.key = key;
            this.level = level;
            this.sourcePos = sourcePos;
            this.sourceType = sourceType;
            this.acceptedByItemId = acceptedByItemId;
        }

        private void addListener(ServerPlayer player, long requestId) {
            Listener listener = new Listener(player, requestId);
            listeners.add(listener);
            if (progressEnabled) {
                sendProgress(listener);
            }
        }

        private boolean step(ManifestPlusConfig.Settings settings, TickBudget budget) {
            if (!commercialStockScanned) {
                commercialStockScanned = true;
                if ("commercial".equals(sourceType)) {
                    addCommercialStock(sourcePos, acceptedByItemId, counts);
                }
            }

            if (!discoveryComplete && !discoverContainers(budget)) {
                return false;
            }

            if (discoveryComplete && totalWorkUnits == 0) {
                initializeProgressEstimate();
            }

            boolean complete = scanContainerSlots(budget);
            if (!complete && progressEnabled && ++ticksSinceProgress >= settings.progressIntervalTicks) {
                ticksSinceProgress = 0;
                sendProgressToAll();
            }
            return complete;
        }

        private boolean discoverContainers(TickBudget budget) {
            if ("build".equals(sourceType)) {
                Direction[] directions = Direction.values();
                while (discoverIndex < directions.length && budget.blockPositions > 0) {
                    budget.blockPositions--;
                    BlockPos checkPos = sourcePos.relative(directions[discoverIndex++]);
                    addContainerIfPresent(checkPos);
                    addOtherChestHalf(level, checkPos, this);
                }
                discoveryComplete = discoverIndex >= directions.length;
                return discoveryComplete;
            }

            int totalPositions = (AREA_RADIUS_XZ * 2 + 1) * (AREA_RADIUS_Y * 2 + 1) * (AREA_RADIUS_XZ * 2 + 1);
            while (discoverIndex < totalPositions && budget.blockPositions > 0) {
                budget.blockPositions--;
                int index = discoverIndex++;
                int xSize = AREA_RADIUS_XZ * 2 + 1;
                int zSize = AREA_RADIUS_XZ * 2 + 1;
                int dx = index % xSize - AREA_RADIUS_XZ;
                int dy = index / (xSize * zSize) - AREA_RADIUS_Y;
                int dz = (index / xSize) % zSize - AREA_RADIUS_XZ;
                addContainerIfPresent(sourcePos.offset(dx, dy, dz));
            }
            discoveryComplete = discoverIndex >= totalPositions;
            return discoveryComplete;
        }

        private void addContainerIfPresent(BlockPos pos) {
            if (!level.isLoaded(pos)) {
                return;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null || blockEntity.isRemoved()) {
                return;
            }
            if (blockEntity instanceof Container || blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()) {
                BlockPos immutablePos = pos.immutable();
                if (containerSet.add(immutablePos)) {
                    containers.add(immutablePos);
                }
            }
        }

        private boolean scanContainerSlots(TickBudget budget) {
            if (containerIndex < containers.size() && budget.containerSlots <= 0) {
                return false;
            }
            while (containerIndex < containers.size() && budget.containerSlots > 0) {
                BlockEntity blockEntity = level.getBlockEntity(containers.get(containerIndex));
                if (blockEntity == null || blockEntity.isRemoved()) {
                    containerIndex++;
                    slotIndex = 0;
                    continue;
                }

                Optional<IItemHandler> handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
                int slotCount = handler.map(IItemHandler::getSlots)
                        .orElseGet(() -> blockEntity instanceof Container container ? container.getContainerSize() : 0);
                if (slotIndex >= slotCount || slotCount <= 0) {
                    containerIndex++;
                    slotIndex = 0;
                    continue;
                }

                while (slotIndex < slotCount && budget.containerSlots > 0) {
                    budget.containerSlots--;
                    ItemStack stack = handler.map(itemHandler -> itemHandler.getStackInSlot(slotIndex))
                            .orElseGet(() -> blockEntity instanceof Container container ? container.getItem(slotIndex) : ItemStack.EMPTY);
                    addStack(stack, acceptedByItemId, counts);
                    slotIndex++;
                    scannedSlots++;
                }

                if (slotIndex >= slotCount) {
                    containerIndex++;
                    slotIndex = 0;
                }
            }
            return containerIndex >= containers.size();
        }

        private void initializeProgressEstimate() {
            for (BlockPos containerPos : containers) {
                BlockEntity blockEntity = level.getBlockEntity(containerPos);
                if (blockEntity == null || blockEntity.isRemoved()) {
                    continue;
                }
                Optional<IItemHandler> handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
                totalSlotCount += handler.map(IItemHandler::getSlots)
                        .orElseGet(() -> blockEntity instanceof Container container ? container.getContainerSize() : 0);
            }

            int totalPositions = "build".equals(sourceType)
                    ? Direction.values().length
                    : (AREA_RADIUS_XZ * 2 + 1) * (AREA_RADIUS_Y * 2 + 1) * (AREA_RADIUS_XZ * 2 + 1);
            totalWorkUnits = totalPositions + totalSlotCount;
            ManifestPlusConfig.Settings settings = ManifestPlusConfig.get();
            int discoveryTicks = Math.max(1, (totalPositions + settings.maxBlockPositionsPerTick - 1) / settings.maxBlockPositionsPerTick);
            int scanTicks = Math.max(1, (totalSlotCount + settings.maxContainerSlotsPerTick - 1) / settings.maxContainerSlotsPerTick);
            estimatedTicks = discoveryTicks + scanTicks;
            progressEnabled = estimatedTicks >= settings.minProgressTicks;
            if (progressEnabled) {
                sendProgressToAll();
            }
        }

        private int progressValue() {
            if (totalWorkUnits <= 0 || estimatedTicks <= 0) {
                return 0;
            }
            int completedWork = Math.min(totalWorkUnits, discoverIndex + scannedSlots);
            return Math.min(estimatedTicks - 1, Math.max(0, completedWork * estimatedTicks / totalWorkUnits));
        }

        private void sendProgressToAll() {
            for (Listener listener : listeners) {
                sendProgress(listener);
            }
        }

        private void sendProgress(Listener listener) {
            if (listener.player.connection != null) {
                ManifestPlusNetwork.sendToPlayer(listener.player, new ManifestStockResponsePacket(
                        listener.requestId,
                        progressValue(),
                        estimatedTicks
                ));
            }
        }

        private void sendResult() {
            for (Listener listener : listeners) {
                if (listener.player.connection != null) {
                    ManifestPlusNetwork.sendToPlayer(listener.player, new ManifestStockResponsePacket(listener.requestId, counts));
                }
            }
        }
    }
}
