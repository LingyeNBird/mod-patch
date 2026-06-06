package com.mskb.nsukbeautifulfarm.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class BeautifulFarmConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue WORK_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue STATUS_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue HARVEST_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue MIN_HARVEST_PLANS_PER_TICK;
    public static final ForgeConfigSpec.IntValue CONTAINER_SEARCH_HORIZONTAL_RADIUS;
    public static final ForgeConfigSpec.IntValue CONTAINER_SEARCH_VERTICAL_RADIUS;
    public static final ForgeConfigSpec.IntValue DROPPED_ITEM_PICKUP_DELAY_TICKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("beautiful_farm");

        WORK_INTERVAL_TICKS = builder
                .comment("农田维护队列的执行间隔，单位 tick。数值越小，修地、铺水、放装饰、补种越及时，但服务器开销越高。默认 5。")
                .defineInRange("workIntervalTicks", 5, 1, 200);

        STATUS_INTERVAL_TICKS = builder
                .comment("缺少材料/种子时给附近玩家提示的间隔，单位 tick。默认 200，即约 10 秒。")
                .defineInRange("statusIntervalTicks", 200, 20, 20 * 60 * 10);

        HARVEST_INTERVAL_TICKS = builder
                .comment("收割队列期望清空的 tick 数。队列会按成熟作物数量自动平摊，目标是在下一次原版收割间隔前处理完。默认 300，即约 15 秒。")
                .defineInRange("harvestIntervalTicks", 300, 20, 20 * 60 * 10);

        MIN_HARVEST_PLANS_PER_TICK = builder
                .comment("收割队列每 tick 至少处理多少个成熟作物计划。少量作物会更快处理完；数值越高，瞬时开销越高。默认 10。")
                .defineInRange("minHarvestPlansPerTick", 10, 1, 1000);

        CONTAINER_SEARCH_HORIZONTAL_RADIUS = builder
                .comment("农田盒查找附近箱子/容器的水平半径，单位方块。原 NSUK 默认是 8。")
                .defineInRange("containerSearchHorizontalRadius", 8, 1, 64);

        CONTAINER_SEARCH_VERTICAL_RADIUS = builder
                .comment("农田盒查找附近箱子/容器的垂直半径，单位方块。原 NSUK 默认是上下 3 格。")
                .defineInRange("containerSearchVerticalRadius", 3, 0, 32);

        DROPPED_ITEM_PICKUP_DELAY_TICKS = builder
                .comment("拆除农田或破坏装饰时，无法放入箱子的掉落物拾取延迟，单位 tick。收割逻辑不会产生掉落实体。默认 10。")
                .defineInRange("droppedItemPickupDelayTicks", 10, 0, 20 * 60);

        builder.pop();

        SPEC = builder.build();
    }

    private BeautifulFarmConfig() {
    }
}
