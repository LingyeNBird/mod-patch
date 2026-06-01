package com.mskb.nsukbeautifulfarm.client;

import com.mojang.blaze3d.vertex.Tesselator;
import com.mskb.nsukbeautifulfarm.common.DecorationBlockPlan;
import com.mskb.nsukbeautifulfarm.common.DecorationMaterials;
import com.mskb.nsukbeautifulfarm.common.DecorationOption;
import com.mskb.nsukbeautifulfarm.common.DecorationPlanner;
import com.mskb.nsukbeautifulfarm.common.FarmDecorationConfig;
import com.mskb.nsukbeautifulfarm.network.BeautifulFarmNetwork;
import com.mskb.nsukbeautifulfarm.network.SaveFarmDecorationPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.List;

public final class FarmSelectionClientHandler {
    private static final String AREA_SELECTION_SCREEN = "com.xiaoliang.simukraft.client.gui.AreaSelectionScreen";
    private static final String FARMLAND_MODE = "FARMLAND";
    private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");
    private static final BlockState WATER_MASK = Blocks.BLUE_STAINED_GLASS.defaultBlockState();

    private static DecorationOption selectedOption = DecorationOption.BORDER;
    private static final FarmDecorationConfig config = new FarmDecorationConfig();

    private static Class<?> areaSelectionManagerClass;
    private static Method getPoint1;
    private static Method getPoint2;
    private static Method setPoint1;
    private static Method setPoint2;
    private static Method getMode;

    private FarmSelectionClientHandler() {
    }

    @SubscribeEvent
    public static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        if (!isFarmSelectionScreen(event.getScreen())) {
            return;
        }
        int key = event.getKeyCode();
        if (BeautifulFarmClient.detachMouse.matches(key, event.getScanCode())) {
            openMouseOverlay(event.getScreen());
            event.setCanceled(true);
        } else if (BeautifulFarmClient.nextOption.matches(key, event.getScanCode())) {
            nextOption();
            event.setCanceled(true);
        } else if (BeautifulFarmClient.nextStyle.matches(key, event.getScanCode())) {
            changeStyle(1);
            event.setCanceled(true);
        } else if (BeautifulFarmClient.previousStyle.matches(key, event.getScanCode())) {
            changeStyle(-1);
            event.setCanceled(true);
        } else if (BeautifulFarmClient.nextMaterial.matches(key, event.getScanCode())) {
            changeMaterial(1);
            event.setCanceled(true);
        } else if (BeautifulFarmClient.previousMaterial.matches(key, event.getScanCode())) {
            changeMaterial(-1);
            event.setCanceled(true);
        } else if (BeautifulFarmClient.rotate.matches(key, event.getScanCode())) {
            rotate();
            event.setCanceled(true);
        } else if (key == 257 && hasBothPoints()) {
            BlockPos boxPos = screenBoxPos(event.getScreen());
            if (boxPos != null) {
                BeautifulFarmNetwork.CHANNEL.sendToServer(new SaveFarmDecorationPacket(boxPos, config));
            }
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!isFarmSelectionScreen(event.getScreen())) {
            return;
        }
        if (selectedOption != DecorationOption.WATER || config.waterStyle == FarmDecorationConfig.WaterStyle.NONE) {
            return;
        }
        Direction facing = Minecraft.getInstance().player == null ? Direction.NORTH : Minecraft.getInstance().player.getDirection();
        int step = event.getScrollDelta() > 0 ? 1 : -1;
        config.waterOffsetX += facing.getStepX() * step;
        config.waterOffsetZ += facing.getStepZ() * step;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!isFarmSelectionScreen(event.getScreen())) {
            return;
        }
        drawUi(event.getGuiGraphics(), event.getScreen());
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null || mc.level == null || !isFarmContextScreen(mc.screen)) {
            return;
        }
        List<DecorationBlockPlan> plans = currentPlans();
        if (plans.isEmpty()) {
            return;
        }
        double camX = mc.gameRenderer.getMainCamera().getPosition().x();
        double camY = mc.gameRenderer.getMainCamera().getPosition().y();
        double camZ = mc.gameRenderer.getMainCamera().getPosition().z();
        MultiBufferSource.BufferSource buffer = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        for (DecorationBlockPlan plan : plans) {
            if (!plan.state().is(Blocks.WATER)) {
                renderPreviewBlock(mc, event, buffer, plan.pos(), plan.state(), camX, camY, camZ);
            }
        }
        for (BlockPos pos : currentWaterPositions()) {
            renderPreviewBlock(mc, event, buffer, pos, WATER_MASK, camX, camY, camZ);
        }
        buffer.endBatch();
    }

    private static void drawUi(GuiGraphics gui, Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        int left = 8;
        drawButton(gui, left, 8, 126, "样式: " + styleName(), true);
        drawButton(gui, left, 32, 126, "材质: " + materialName(), true);
        drawButton(gui, left, 56, 126, "朝向: " + facingName(), true);

        int helpY = 92;
        gui.drawString(mc.font, "美丽农田操作", left, helpY, 0x55FF55, true);
        gui.drawString(mc.font, "Tab: 切换装饰", left, helpY + 14, 0xDDDDDD, true);
        gui.drawString(mc.font, "← / →: 切换具体样式", left, helpY + 26, 0xDDDDDD, true);
        gui.drawString(mc.font, "↑ / ↓: 切换材质", left, helpY + 38, 0xDDDDDD, true);
        gui.drawString(mc.font, "R: 旋转朝向", left, helpY + 50, 0xDDDDDD, true);
        gui.drawString(mc.font, "滚轮: 移动水装饰", left, helpY + 62, 0xDDDDDD, true);
        gui.drawString(mc.font, "Alt: " + (screen instanceof FarmControlsOverlay ? "恢复视角" : "脱离鼠标"), left, helpY + 74, screen instanceof FarmControlsOverlay ? 0xFFFF55 : 0xDDDDDD, true);

        int right = screen.width - 132;
        int y = 38;
        for (DecorationOption option : DecorationOption.values()) {
            drawButton(gui, right, y, 120, option.displayName(), option == selectedOption);
            y += 24;
        }
    }

    private static void drawButton(GuiGraphics gui, int x, int y, int width, String text, boolean selected) {
        Minecraft mc = Minecraft.getInstance();
        int half = width / 2;
        int textureY = selected ? 86 : 46;
        gui.blit(WIDGETS, x, y, 0, textureY, half, 20);
        gui.blit(WIDGETS, x + half, y, 200 - (width - half), textureY, width - half, 20);
        gui.drawCenteredString(mc.font, text, x + width / 2, y + 6, selected ? 0xFFFF55 : 0xFFFFFF);
    }

    private static boolean clickUi(double mouseX, double mouseY, Screen screen) {
        int left = 8;
        if (inside(mouseX, mouseY, left, 8, 126, 20)) {
            changeStyle(1);
            return true;
        }
        if (inside(mouseX, mouseY, left, 32, 126, 20)) {
            changeMaterial(1);
            return true;
        }
        if (inside(mouseX, mouseY, left, 56, 126, 20)) {
            rotate();
            return true;
        }
        int right = screen.width - 132;
        int y = 38;
        for (DecorationOption option : DecorationOption.values()) {
            if (inside(mouseX, mouseY, right, y, 120, 20)) {
                selectedOption = option;
                return true;
            }
            y += 24;
        }
        return false;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static void openMouseOverlay(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new FarmControlsOverlay(parent, point(1), point(2)));
    }

    private static void renderPreviewBlock(Minecraft mc, RenderLevelStageEvent event, MultiBufferSource.BufferSource buffer,
                                           BlockPos pos, BlockState state, double camX, double camY, double camZ) {
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(pos.getX() - camX, pos.getY() - camY, pos.getZ() - camZ);
        mc.getBlockRenderer().renderSingleBlock(state, event.getPoseStack(), buffer, 15728880, OverlayTexture.NO_OVERLAY);
        event.getPoseStack().popPose();
    }

    private static List<DecorationBlockPlan> currentPlans() {
        BlockPos p1 = point(1);
        BlockPos p2 = point(2);
        if (p1 == null || p2 == null) {
            return List.of();
        }
        BlockPos min = new BlockPos(Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()), Math.min(p1.getZ(), p2.getZ()));
        BlockPos max = new BlockPos(Math.max(p1.getX(), p2.getX()), Math.max(p1.getY(), p2.getY()), Math.max(p1.getZ(), p2.getZ()));
        return DecorationPlanner.plan(min, max, config);
    }

    private static List<BlockPos> currentWaterPositions() {
        BlockPos p1 = point(1);
        BlockPos p2 = point(2);
        if (p1 == null || p2 == null) {
            return List.of();
        }
        BlockPos min = new BlockPos(Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()), Math.min(p1.getZ(), p2.getZ()));
        BlockPos max = new BlockPos(Math.max(p1.getX(), p2.getX()), Math.max(p1.getY(), p2.getY()), Math.max(p1.getZ(), p2.getZ()));
        return DecorationPlanner.waterPositions(min, max, config);
    }

    private static boolean isFarmSelectionScreen(Screen screen) {
        if (screen == null) {
            return false;
        }
        if (!AREA_SELECTION_SCREEN.equals(screen.getClass().getName())) {
            return false;
        }
        try {
            Object mode = getModeMethod().invoke(null);
            return mode != null && FARMLAND_MODE.equals(mode.toString());
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean isFarmContextScreen(Screen screen) {
        return isFarmSelectionScreen(screen) || screen instanceof FarmControlsOverlay;
    }

    private static boolean hasBothPoints() {
        return point(1) != null && point(2) != null;
    }

    private static BlockPos point(int point) {
        try {
            return (BlockPos) (point == 1 ? getPoint1Method() : getPoint2Method()).invoke(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static BlockPos screenBoxPos(Screen screen) {
        try {
            var field = screen.getClass().getDeclaredField("buildBoxPos");
            field.setAccessible(true);
            return (BlockPos) field.get(screen);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static void nextOption() {
        DecorationOption[] values = DecorationOption.values();
        selectedOption = values[(selectedOption.ordinal() + 1) % values.length];
    }

    private static void changeStyle(int delta) {
        switch (selectedOption) {
            case BORDER -> {
                config.borderStyle = cycle(FarmDecorationConfig.BorderStyle.values(), config.borderStyle, delta);
                config.borderMaterial = materialsForBorder()[0];
            }
            case WATER -> config.waterStyle = cycle(FarmDecorationConfig.WaterStyle.values(), config.waterStyle, delta);
            case WATER_COVER -> {
                config.coverStyle = cycle(FarmDecorationConfig.CoverStyle.values(), config.coverStyle, delta);
                config.coverMaterial = materialsForCover()[0];
            }
        }
    }

    private static void changeMaterial(int delta) {
        switch (selectedOption) {
            case BORDER -> config.borderMaterial = cycle(materialsForBorder(), config.borderMaterial, delta);
            case WATER_COVER -> config.coverMaterial = cycle(materialsForCover(), config.coverMaterial, delta);
            default -> {
            }
        }
    }

    private static void rotate() {
        switch (selectedOption) {
            case BORDER -> config.borderFacing = config.borderFacing.getClockWise();
            case WATER -> config.waterFacing = config.waterFacing.getClockWise();
            case WATER_COVER -> config.coverFacing = config.coverFacing.getClockWise();
        }
    }

    private static ResourceLocation[] materialsForBorder() {
        return switch (config.borderStyle) {
            case OPPOSITE_FENCE, FULL_FENCE -> DecorationMaterials.FENCES;
            case OPPOSITE_WALL, FULL_WALL -> DecorationMaterials.WALLS;
            case OPPOSITE_PANE, FULL_PANE -> DecorationMaterials.PANES;
            default -> DecorationMaterials.FENCES;
        };
    }

    private static ResourceLocation[] materialsForCover() {
        return switch (config.coverStyle) {
            case LEAVES -> DecorationMaterials.LEAVES;
            case TOP_SLAB, BOTTOM_SLAB -> DecorationMaterials.SLABS;
            case TRAPDOOR -> DecorationMaterials.TRAPDOORS;
            default -> DecorationMaterials.LEAVES;
        };
    }

    private static <T> T cycle(T[] values, T current, int delta) {
        int index = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) index = i;
        return values[Math.floorMod(index + delta, values.length)];
    }

    private static String styleName() {
        return switch (selectedOption) {
            case BORDER -> config.borderStyle.displayName();
            case WATER -> config.waterStyle.displayName();
            case WATER_COVER -> config.coverStyle.displayName();
        };
    }

    private static String materialName() {
        return switch (selectedOption) {
            case BORDER -> materialDisplayName(config.borderMaterial);
            case WATER -> "无材质";
            case WATER_COVER -> materialDisplayName(config.coverMaterial);
        };
    }

    private static String facingName() {
        Direction direction = switch (selectedOption) {
            case BORDER -> config.borderFacing;
            case WATER -> config.waterFacing;
            case WATER_COVER -> config.coverFacing;
        };
        return directionDisplayName(direction);
    }

    private static String materialDisplayName(ResourceLocation id) {
        String path = id.getPath();
        String color = switch (path) {
            case "white_stained_glass_pane" -> "白色";
            case "orange_stained_glass_pane" -> "橙色";
            case "magenta_stained_glass_pane" -> "品红色";
            case "light_blue_stained_glass_pane" -> "淡蓝色";
            case "yellow_stained_glass_pane" -> "黄色";
            case "lime_stained_glass_pane" -> "黄绿色";
            case "pink_stained_glass_pane" -> "粉红色";
            case "gray_stained_glass_pane" -> "灰色";
            case "light_gray_stained_glass_pane" -> "淡灰色";
            case "cyan_stained_glass_pane" -> "青色";
            case "purple_stained_glass_pane" -> "紫色";
            case "blue_stained_glass_pane" -> "蓝色";
            case "brown_stained_glass_pane" -> "棕色";
            case "green_stained_glass_pane" -> "绿色";
            case "red_stained_glass_pane" -> "红色";
            case "black_stained_glass_pane" -> "黑色";
            default -> null;
        };
        if (color != null) return color + "染色玻璃板";
        String base = path;
        String suffix = "";
        if (base.endsWith("_fence_gate")) { suffix = "栅栏门"; base = base.substring(0, base.length() - 11); }
        else if (base.endsWith("_fence")) { suffix = "栅栏"; base = base.substring(0, base.length() - 6); }
        else if (base.endsWith("_wall")) { suffix = "墙"; base = base.substring(0, base.length() - 5); }
        else if (base.endsWith("_pane")) { suffix = "玻璃板"; base = base.substring(0, base.length() - 5); }
        else if (base.endsWith("_leaves")) { suffix = "树叶"; base = base.substring(0, base.length() - 7); }
        else if (base.endsWith("_slab")) { suffix = "台阶"; base = base.substring(0, base.length() - 5); }
        else if (base.endsWith("_trapdoor")) { suffix = "活板门"; base = base.substring(0, base.length() - 9); }
        return materialBaseName(base) + suffix;
    }

    private static String materialBaseName(String base) {
        return switch (base) {
            case "oak" -> "橡木";
            case "birch" -> "白桦木";
            case "spruce" -> "云杉木";
            case "jungle" -> "丛林木";
            case "acacia" -> "金合欢木";
            case "dark_oak" -> "深色橡木";
            case "stone_brick" -> "石砖";
            case "mossy_stone_brick" -> "苔石砖";
            case "granite" -> "花岗岩";
            case "blackstone" -> "黑石";
            case "andesite" -> "安山岩";
            case "sandstone" -> "砂岩";
            case "glass" -> "玻璃";
            case "stone" -> "石头";
            case "smooth_stone" -> "平滑石头";
            default -> base.replace('_', ' ');
        };
    }

    private static String directionDisplayName(Direction direction) {
        return switch (direction) {
            case NORTH -> "北";
            case SOUTH -> "南";
            case WEST -> "西";
            case EAST -> "东";
            default -> direction.getName();
        };
    }

    private static Class<?> managerClass() throws ClassNotFoundException {
        if (areaSelectionManagerClass == null) areaSelectionManagerClass = Class.forName("com.xiaoliang.simukraft.client.preview.AreaSelectionManager");
        return areaSelectionManagerClass;
    }

    private static Method getPoint1Method() throws ReflectiveOperationException {
        if (getPoint1 == null) getPoint1 = managerClass().getMethod("getPoint1");
        return getPoint1;
    }

    private static Method getPoint2Method() throws ReflectiveOperationException {
        if (getPoint2 == null) getPoint2 = managerClass().getMethod("getPoint2");
        return getPoint2;
    }

    private static Method setPoint1Method() throws ReflectiveOperationException {
        if (setPoint1 == null) setPoint1 = managerClass().getMethod("setPoint1", BlockPos.class);
        return setPoint1;
    }

    private static Method setPoint2Method() throws ReflectiveOperationException {
        if (setPoint2 == null) setPoint2 = managerClass().getMethod("setPoint2", BlockPos.class);
        return setPoint2;
    }

    private static Method getModeMethod() throws ReflectiveOperationException {
        if (getMode == null) getMode = managerClass().getMethod("getMode");
        return getMode;
    }

    private static final class FarmControlsOverlay extends Screen {
        private final Screen parent;
        private final BlockPos point1;
        private final BlockPos point2;

        private FarmControlsOverlay(Screen parent, BlockPos point1, BlockPos point2) {
            super(Component.empty());
            this.parent = parent;
            this.point1 = point1;
            this.point2 = point2;
        }

        @Override
        public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
            drawUi(gui, this);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (BeautifulFarmClient.detachMouse.matches(keyCode, scanCode)) {
                restoreParent();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return clickUi(mouseX, mouseY, this) || super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (selectedOption == DecorationOption.WATER && config.waterStyle != FarmDecorationConfig.WaterStyle.NONE) {
                Direction facing = Minecraft.getInstance().player == null ? Direction.NORTH : Minecraft.getInstance().player.getDirection();
                int step = delta > 0 ? 1 : -1;
                config.waterOffsetX += facing.getStepX() * step;
                config.waterOffsetZ += facing.getStepZ() * step;
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, delta);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        private void restoreParent() {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(parent);
            try {
                if (point1 != null) setPoint1Method().invoke(null, point1);
                if (point2 != null) setPoint2Method().invoke(null, point2);
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
        }
    }

}
