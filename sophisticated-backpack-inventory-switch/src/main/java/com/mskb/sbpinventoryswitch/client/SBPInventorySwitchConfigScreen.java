package com.mskb.sbpinventoryswitch.client;

import com.mskb.sbpinventoryswitch.config.SBPInventorySwitchConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

public final class SBPInventorySwitchConfigScreen extends Screen {
    private final Screen parent;
    private boolean enabled;
    private Button toggleButton;

    private SBPInventorySwitchConfigScreen(Screen parent) {
        super(Component.literal("精妙背包切换配置"));
        this.parent = parent;
        this.enabled = SBPInventorySwitchConfig.isEnabled();
    }

    public static void registerFactory() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new SBPInventorySwitchConfigScreen(parent))
        );
    }

    @Override
    protected void init() {
        toggleButton = Button.builder(toggleLabel(), button -> {
            enabled = !enabled;
            button.setMessage(toggleLabel());
        }).bounds(this.width / 2 - 80, this.height / 2 - 10, 160, 20).build();
        addRenderableWidget(toggleButton);

        addRenderableWidget(Button.builder(Component.literal("保存"), button -> save()).bounds(this.width / 2 - 105, this.height / 2 + 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("完成"), button -> onClose()).bounds(this.width / 2 + 5, this.height / 2 + 28, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 54, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal("关闭后，背包界面的快捷键切换不会生效。"), this.width / 2, this.height / 2 - 34, 0xCCCCCC);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private void save() {
        SBPInventorySwitchConfig.setEnabled(enabled);
        onClose();
    }

    private Component toggleLabel() {
        return Component.literal(enabled ? "功能：开启" : "功能：关闭");
    }
}
