package com.mskb.nsukmanifestplus.client;

import com.mskb.nsukmanifestplus.config.ManifestPlusConfig;
import com.mskb.nsukmanifestplus.network.ManifestConfigRequestPacket;
import com.mskb.nsukmanifestplus.network.ManifestConfigUpdatePacket;
import com.mskb.nsukmanifestplus.network.ManifestPlusNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

import java.util.ArrayList;
import java.util.List;

public final class ManifestConfigScreen extends Screen {
    private final Screen parent;
    private final List<FieldRow> fields = new ArrayList<>();
    private String status = "已加载本地默认配置";
    private boolean serverEditable;

    private ManifestConfigScreen(Screen parent) {
        super(Component.literal("NSUK 更好的清单配置"));
        this.parent = parent;
    }

    public static void registerFactory() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new ManifestConfigScreen(parent))
        );
    }

    public static void handleServerSettings(ManifestPlusConfig.Settings settings, boolean editable) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ManifestConfigScreen screen) {
            screen.serverEditable = editable;
            screen.status = editable ? "已加载服务器配置；保存会更新当前世界" : "已加载服务器配置（只读）；保存只影响本地文件";
            screen.setFieldValues(settings);
        }
    }

    @Override
    protected void init() {
        populate(ManifestPlusConfig.getLocal());
        requestServerSettings();

        int buttonY = this.height - 32;
        addRenderableWidget(Button.builder(Component.literal("保存"), button -> save()).bounds(this.width / 2 - 105, buttonY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("完成"), button -> onClose()).bounds(this.width / 2 + 5, buttonY, 100, 20).build());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal(status), this.width / 2, this.height - 48, serverEditable ? 0x88FF88 : 0xFFCC66);
        for (FieldRow field : fields) {
            graphics.drawString(this.font, field.label, field.x, field.y, 0xFFFFFF, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void populate(ManifestPlusConfig.Settings settings) {
        fields.clear();

        int left = this.width / 2 - 145;
        int y = 44;
        addField("每tick方块检查上限", settings.maxBlockPositionsPerTick, left, y);
        addField("每tick槽位读取上限", settings.maxContainerSlotsPerTick, left, y + 24);
        addField("每tick任务轮询上限", settings.maxTasksPerTick, left, y + 48);
        addField("缓存有效tick", settings.cacheTtlTicks, left, y + 72);
        addField("进度发送间隔tick", settings.progressIntervalTicks, left, y + 96);
        addField("显示进度最小tick", settings.minProgressTicks, left, y + 120);
    }

    private void addField(String label, int value, int x, int y) {
        EditBox box = new EditBox(this.font, x + 175, y - 4, 80, 18, Component.literal(label));
        box.setValue(Integer.toString(value));
        box.setFilter(text -> text.isEmpty() || text.matches("[0-9]+"));
        fields.add(new FieldRow(label, x, y, box));
        addRenderableWidget(box);
    }

    private void setFieldValues(ManifestPlusConfig.Settings settings) {
        int[] values = new int[]{
                settings.maxBlockPositionsPerTick,
                settings.maxContainerSlotsPerTick,
                settings.maxTasksPerTick,
                settings.cacheTtlTicks,
                settings.progressIntervalTicks,
                settings.minProgressTicks
        };
        for (int i = 0; i < fields.size() && i < values.length; i++) {
            fields.get(i).box.setValue(Integer.toString(values[i]));
        }
    }

    private void save() {
        ManifestPlusConfig.Settings settings = readSettings();
        ManifestPlusConfig.applyLocalAndSave(settings);
        if (Minecraft.getInstance().getConnection() != null) {
            status = "已保存本地配置" + (serverEditable ? "；已发送到服务器" : "；当前服务器不可编辑");
            ManifestPlusNetwork.sendToServer(new ManifestConfigUpdatePacket(settings));
        } else {
            status = "已保存本地配置";
        }
    }

    private ManifestPlusConfig.Settings readSettings() {
        int[] values = new int[6];
        for (int i = 0; i < fields.size(); i++) {
            try {
                values[i] = Integer.parseInt(fields.get(i).box.getValue());
            } catch (NumberFormatException ignored) {
                values[i] = 0;
            }
        }
        return new ManifestPlusConfig.Settings(values[0], values[1], values[2], values[3], values[4], values[5]).sanitized();
    }

    private void requestServerSettings() {
        if (Minecraft.getInstance().getConnection() != null) {
            ManifestPlusNetwork.sendToServer(new ManifestConfigRequestPacket());
        }
    }

    private record FieldRow(String label, int x, int y, EditBox box) {
    }
}
