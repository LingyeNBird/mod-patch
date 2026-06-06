package com.mskb.nsukbeautifulfarm.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class BeautifulFarmCommands {
    private BeautifulFarmCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("beautifulfarm")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("buildspeed")
                        .executes(context -> showBuildSpeed(context.getSource()))
                        .then(Commands.argument("percent", IntegerArgumentType.integer(10, 1600))
                                .executes(context -> setBuildSpeed(context.getSource(), IntegerArgumentType.getInteger(context, "percent")))))
                .then(Commands.literal("materials")
                        .executes(context -> showMaterials(context.getSource()))
                        .then(Commands.argument("consume", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                .executes(context -> setMaterials(context.getSource(), com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "consume"))))));
    }

    private static int showBuildSpeed(CommandSourceStack source) {
        int value = LenientFarmingServer.buildSpeedPercent();
        source.sendSuccess(() -> Component.literal("美丽农田：当前农田建造/装饰速度为 " + value + "% ，不影响收割和催熟；该设置仅本次服务器运行有效。"), false);
        return value;
    }

    private static int setBuildSpeed(CommandSourceStack source, int percent) {
        int value = LenientFarmingServer.setBuildSpeedPercent(percent);
        source.sendSuccess(() -> Component.literal("美丽农田：农田建造/装饰速度已调整为 " + value + "% ，收割和催熟保持原速度；该设置仅本次服务器运行有效。"), true);
        return value;
    }

    private static int showMaterials(CommandSourceStack source) {
        boolean consume = LenientFarmingServer.consumeMaterials();
        source.sendSuccess(() -> Component.literal("美丽农田：材料消耗为 " + (consume ? "开启" : "关闭") + "；该设置仅本次服务器运行有效。"), false);
        return consume ? 1 : 0;
    }

    private static int setMaterials(CommandSourceStack source, boolean consume) {
        boolean value = LenientFarmingServer.setConsumeMaterials(consume);
        source.sendSuccess(() -> Component.literal(value ? "美丽农田：已开启材料检查和消耗；该设置仅本次服务器运行有效。" : "美丽农田：已关闭材料检查和消耗，适合演示；该设置仅本次服务器运行有效。"), true);
        return value ? 1 : 0;
    }
}
