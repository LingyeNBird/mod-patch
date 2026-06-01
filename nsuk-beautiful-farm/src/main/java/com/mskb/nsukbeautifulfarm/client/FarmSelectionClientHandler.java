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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

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
    private static Method getMode;
    private static boolean physicalUiActive;
    private static PhysicalUi physicalUi;

    private FarmSelectionClientHandler() {
    }

    @SubscribeEvent
    public static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        if (!isFarmSelectionScreen(event.getScreen())) {
            return;
        }
        int key = event.getKeyCode();
        if (BeautifulFarmClient.detachMouse.matches(key, event.getScanCode())) {
            togglePhysicalUi();
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
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!physicalUiActive || mc.screen == null || !isFarmSelectionScreen(mc.screen) || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        PhysicalButton hovered = hoveredPhysicalButton(mc);
        if (hovered != null) {
            activate(hovered.action);
            event.setCanceled(true);
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
            physicalUiActive = false;
            physicalUi = null;
            return;
        }
        if (!physicalUiActive) {
            drawUi(event.getGuiGraphics(), event.getScreen());
        } else {
            Minecraft mc = Minecraft.getInstance();
            event.getGuiGraphics().drawString(mc.font, "Alt: 恢复屏幕UI", 8, 8, 0xFFFF55, true);
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null || mc.level == null || !isFarmSelectionScreen(mc.screen)) {
            return;
        }
        List<DecorationBlockPlan> plans = currentPlans();
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
        if (physicalUiActive) {
            renderPhysicalUi(mc, event, camX, camY, camZ);
        }
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
        gui.drawString(mc.font, "Alt: 脱离鼠标", left, helpY + 74, 0xDDDDDD, true);

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

    private static void togglePhysicalUi() {
        if (physicalUiActive) {
            physicalUiActive = false;
            physicalUi = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Vector3f lookVector = mc.gameRenderer.getMainCamera().getLookVector();
        Vector3f leftVector = mc.gameRenderer.getMainCamera().getLeftVector();
        Vector3f upVector = mc.gameRenderer.getMainCamera().getUpVector();
        Vec3 normal = new Vec3(lookVector.x(), lookVector.y(), lookVector.z()).normalize();
        Vec3 right = new Vec3(-leftVector.x(), -leftVector.y(), -leftVector.z()).normalize();
        Vec3 up = new Vec3(upVector.x(), upVector.y(), upVector.z()).normalize();
        physicalUi = new PhysicalUi(cam.add(normal.scale(3.0D)), normal, right, up);
        physicalUiActive = true;
    }

    private static void renderPreviewBlock(Minecraft mc, RenderLevelStageEvent event, MultiBufferSource.BufferSource buffer,
                                           BlockPos pos, BlockState state, double camX, double camY, double camZ) {
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(pos.getX() - camX, pos.getY() - camY, pos.getZ() - camZ);
        mc.getBlockRenderer().renderSingleBlock(state, event.getPoseStack(), buffer, 15728880, OverlayTexture.NO_OVERLAY);
        event.getPoseStack().popPose();
    }

    private static void renderPhysicalUi(Minecraft mc, RenderLevelStageEvent event, double camX, double camY, double camZ) {
        if (physicalUi == null) {
            return;
        }
        PhysicalButton hovered = hoveredPhysicalButton(mc);
        MultiBufferSource.BufferSource buffer = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        for (PhysicalButton button : physicalButtons()) {
            String label = (hovered == button ? "> " : "[ ") + button.label() + (hovered == button ? " <" : " ]");
            int color = hovered == button ? 0xFFFF55 : 0xFFFFFF;
            renderWorldLabel(mc, event, buffer, physicalUi.point(button.x, button.y), label, color, camX, camY, camZ);
        }
        buffer.endBatch();
    }

    private static void renderWorldLabel(Minecraft mc, RenderLevelStageEvent event, MultiBufferSource.BufferSource buffer,
                                         Vec3 pos, String text, int color, double camX, double camY, double camZ) {
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(pos.x - camX, pos.y - camY, pos.z - camZ);
        event.getPoseStack().mulPose(mc.gameRenderer.getMainCamera().rotation());
        event.getPoseStack().scale(-0.025F, -0.025F, 0.025F);
        Matrix4f matrix = event.getPoseStack().last().pose();
        float x = -mc.font.width(text) / 2.0F;
        mc.font.drawInBatch(text, x, 0.0F, color, false, matrix, buffer, Font.DisplayMode.SEE_THROUGH, 0x66000000, LightTexture.FULL_BRIGHT);
        event.getPoseStack().popPose();
    }

    private static PhysicalButton hoveredPhysicalButton(Minecraft mc) {
        if (physicalUi == null) {
            return null;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Vector3f lookVector = mc.gameRenderer.getMainCamera().getLookVector();
        Vec3 ray = new Vec3(lookVector.x(), lookVector.y(), lookVector.z()).normalize();
        double denominator = ray.dot(physicalUi.normal);
        if (Math.abs(denominator) < 1.0E-5D) {
            return null;
        }
        double t = physicalUi.origin.subtract(cam).dot(physicalUi.normal) / denominator;
        if (t < 0.0D || t > 8.0D) {
            return null;
        }
        Vec3 hit = cam.add(ray.scale(t)).subtract(physicalUi.origin);
        double x = hit.dot(physicalUi.right);
        double y = hit.dot(physicalUi.up);
        for (PhysicalButton button : physicalButtons()) {
            if (x >= button.x - button.w / 2.0D && x <= button.x + button.w / 2.0D && y >= button.y - button.h / 2.0D && y <= button.y + button.h / 2.0D) {
                return button;
            }
        }
        return null;
    }

    private static List<PhysicalButton> physicalButtons() {
        return List.of(
                new PhysicalButton(PhysicalAction.STYLE, -1.05D, 0.55D, 1.5D, 0.28D),
                new PhysicalButton(PhysicalAction.MATERIAL, -1.05D, 0.20D, 1.5D, 0.28D),
                new PhysicalButton(PhysicalAction.FACING, -1.05D, -0.15D, 1.5D, 0.28D),
                new PhysicalButton(PhysicalAction.BORDER, 0.85D, 0.55D, 1.3D, 0.28D),
                new PhysicalButton(PhysicalAction.WATER, 0.85D, 0.20D, 1.3D, 0.28D),
                new PhysicalButton(PhysicalAction.COVER, 0.85D, -0.15D, 1.3D, 0.28D)
        );
    }

    private static void activate(PhysicalAction action) {
        switch (action) {
            case STYLE -> changeStyle(1);
            case MATERIAL -> changeMaterial(1);
            case FACING -> rotate();
            case BORDER -> selectedOption = DecorationOption.BORDER;
            case WATER -> selectedOption = DecorationOption.WATER;
            case COVER -> selectedOption = DecorationOption.WATER_COVER;
        }
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

    private static Method getModeMethod() throws ReflectiveOperationException {
        if (getMode == null) getMode = managerClass().getMethod("getMode");
        return getMode;
    }

    private record PhysicalUi(Vec3 origin, Vec3 normal, Vec3 right, Vec3 up) {
        Vec3 point(double x, double y) {
            return origin.add(right.scale(x)).add(up.scale(y));
        }
    }

    private record PhysicalButton(PhysicalAction action, double x, double y, double w, double h) {
        String label() {
            return switch (action) {
                case STYLE -> "样式: " + styleName();
                case MATERIAL -> "材质: " + materialName();
                case FACING -> "朝向: " + facingName();
                case BORDER -> DecorationOption.BORDER.displayName();
                case WATER -> DecorationOption.WATER.displayName();
                case COVER -> DecorationOption.WATER_COVER.displayName();
            };
        }
    }

    private enum PhysicalAction {
        STYLE,
        MATERIAL,
        FACING,
        BORDER,
        WATER,
        COVER
    }

}
