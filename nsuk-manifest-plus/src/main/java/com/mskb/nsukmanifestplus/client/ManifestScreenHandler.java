package com.mskb.nsukmanifestplus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import com.mskb.nsukmanifestplus.network.ManifestPlusNetwork;
import com.mskb.nsukmanifestplus.network.ManifestStockRequestPacket;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class ManifestScreenHandler {
    private static final String MANIFEST_SCREEN = "com.xiaoliang.simukraft.client.gui.ManifestScreen";
    private static final String MANIFEST_ITEM = "com.xiaoliang.simukraft.item.ManifestItem";
    private static final String TABLE_ROW = "com.xiaoliang.simukraft.client.gui.ManifestScreen$TableRow";
    private static final String PAGE_SLICE = "com.xiaoliang.simukraft.client.gui.ManifestScreen$PageSlice";
    private static final int CLIPBOARD_WIDTH = 200;
    private static final int CLIPBOARD_HEIGHT = 240;
    private static final int CLIP_TOP_HEIGHT = 24;
    private static final int CLIP_BOTTOM_HEIGHT = 16;
    private static final int CLIP_SIDE_WIDTH = 12;
    private static final int PAPER_COLOR = 0xFFF5F0E1;

    private static final WeakHashMap<Screen, ManifestState> STATES = new WeakHashMap<>();
    private static final Reflection REFLECTION = new Reflection();
    private static long nextRequestId = 1L;

    private ManifestScreenHandler() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ManifestScreenHandler::onScreenInit);
        MinecraftForge.EVENT_BUS.addListener(ManifestScreenHandler::onScreenRenderPost);
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!isManifestScreen(screen)) {
            return;
        }

        ManifestState state = state(screen);
        refresh(screen, state);

        int clipboardX = intField(screen, "clipboardX", screen.width / 2 - 110);
        int clipboardY = intField(screen, "clipboardY", screen.height / 2 - 100);
        int paperX = clipboardX + CLIP_SIDE_WIDTH;
        int paperW = CLIPBOARD_WIDTH - CLIP_SIDE_WIDTH * 2;
        int nextPageRight = paperX + paperW - 4;

        Button refreshButton = Button.builder(Component.literal("刷新"), button -> {
            refresh(screen, state);
            applyFilter(screen, state);
        }).bounds(clipboardX + 12, clipboardY + 8, 38, 18).build();

        Button toggleButton = Button.builder(toggleLabel(state), button -> {
            state.showMissingOnly = !state.showMissingOnly;
            button.setMessage(toggleLabel(state));
            applyFilter(screen, state);
        }).bounds(nextPageRight - 58, clipboardY + 8, 58, 18).build();

        event.addListener(refreshButton);
        event.addListener(toggleButton);
    }

    private static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!isManifestScreen(screen)) {
            return;
        }

        ManifestState state = state(screen);
        drawQuantityOverlay(screen, event.getGuiGraphics(), state);
        drawProgressOverlay(screen, event.getGuiGraphics(), state);
    }

    private static void drawQuantityOverlay(Screen screen, GuiGraphics graphics, ManifestState state) {
        try {
            List<?> pages = REFLECTION.pages(screen);
            int currentPage = intField(screen, "currentPage", 0);
            if (currentPage < 0 || currentPage >= pages.size()) {
                return;
            }

            List<?> rows = REFLECTION.pageRows(pages.get(currentPage));
            int contentX = intField(screen, "contentX", 0);
            int contentY = intField(screen, "contentY", 0);
            int contentWidth = intField(screen, "contentWidth", 0);

            for (int i = 0; i < rows.size(); i++) {
                Object row = rows.get(i);
                Object material = REFLECTION.rowMaterial(row);
                if (material == null) {
                    continue;
                }

                String itemId = REFLECTION.materialItemId(material);
                int required = REFLECTION.materialCount(material);
                int available = state.counts.getOrDefault(itemId, 0);
                int missing = Math.max(0, required - available);
                int existing = Math.max(0, required - missing);
                String text = existing + "/" + required;

                int y = contentY + i * 24 + 8;
                int width = Minecraft.getInstance().font.width(text);
                int x = contentX + contentWidth - width;
                int originalWidth = Minecraft.getInstance().font.width("x" + required);
                int clearX = contentX + contentWidth - Math.max(width, originalWidth) - 2;
                graphics.fill(clearX, y - 1, contentX + contentWidth + 1, y + 10, PAPER_COLOR);
                graphics.drawString(Minecraft.getInstance().font, text, x, y, existing >= required ? 0xFF2E7D32 : 0xFF333333, false);
            }
        } catch (ReflectiveOperationException ignored) {
            // The patch is intentionally best-effort against the external mod's private UI.
        }
    }

    private static void drawProgressOverlay(Screen screen, GuiGraphics graphics, ManifestState state) {
        if (state.progressMax <= 0 || state.progressValue >= state.progressMax) {
            return;
        }

        int clipboardX = intField(screen, "clipboardX", screen.width / 2 - 110);
        int clipboardY = intField(screen, "clipboardY", screen.height / 2 - 100);
        int x = clipboardX + CLIP_SIDE_WIDTH;
        int y = clipboardY + CLIPBOARD_HEIGHT - 8;
        int width = CLIPBOARD_WIDTH - CLIP_SIDE_WIDTH * 2;
        int filled = Math.max(0, Math.min(width, width * state.progressValue / state.progressMax));
        graphics.fill(x, y, x + width, y + 3, 0x55333333);
        graphics.fill(x, y, x + filled, y + 3, 0xFF5D8C3A);
    }

    private static void refresh(Screen screen, ManifestState state) {
        try {
            ItemStack manifestStack = REFLECTION.manifestStack(screen);
            if (!manifestStack.hasTag() || manifestStack.getTag() == null || !manifestStack.getTag().contains("BuildBoxPos")) {
                return;
            }

            List<String> itemIds = new ArrayList<>();
            for (Object material : REFLECTION.allMaterials(REFLECTION.manifestStack(screen))) {
                itemIds.add(REFLECTION.materialItemId(material));
            }

            state.requestId = nextRequestId++;
            long sourcePos = manifestStack.getTag().getLong("BuildBoxPos");
            String sourceType = manifestStack.getTag().getString("SourceType");
            ManifestPlusNetwork.sendToServer(new ManifestStockRequestPacket(state.requestId, sourcePos, sourceType, itemIds));
        } catch (ReflectiveOperationException ignored) {
            // Keep the last visible screen usable even if the external mod changes internals.
        }
    }

    public static void handleStockResponse(long requestId, Map<String, Integer> counts) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (!isManifestScreen(screen)) {
            return;
        }

        ManifestState state = state(screen);
        if (state.requestId != requestId) {
            return;
        }

        state.counts.clear();
        state.counts.putAll(counts);
        state.progressValue = 0;
        state.progressMax = 0;
        applyFilter(screen, state);
    }

    public static void handleStockProgress(long requestId, int progressValue, int progressMax) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (!isManifestScreen(screen)) {
            return;
        }

        ManifestState state = state(screen);
        if (state.requestId != requestId) {
            return;
        }

        state.progressValue = Math.max(0, progressValue);
        state.progressMax = Math.max(0, progressMax);
    }

    private static void applyFilter(Screen screen, ManifestState state) {
        try {
            ItemStack manifestStack = REFLECTION.manifestStack(screen);
            List<?> allMaterials = REFLECTION.allMaterials(manifestStack);
            if (!state.showMissingOnly) {
                REFLECTION.setMaterials(screen, allMaterials);
                REFLECTION.setPages(screen, REFLECTION.buildPages(screen));
            } else {
                List<?> filteredMaterials = filterMissing(allMaterials, state);
                REFLECTION.setMaterials(screen, filteredMaterials);
                REFLECTION.setPages(screen, buildFilteredPages(screen, manifestStack, filteredMaterials, state));
            }
            REFLECTION.setCurrentPage(screen, 0);
            REFLECTION.updatePageButtons(screen);
        } catch (ReflectiveOperationException ignored) {
            // Best-effort UI patch.
        }
    }

    private static List<?> buildFilteredPages(Screen screen, Object manifestStack, List<?> fallbackMaterials, ManifestState state) throws ReflectiveOperationException {
        List<Object> rows = new ArrayList<>();
        List<?> groups = REFLECTION.productGroups(manifestStack);
        if (!groups.isEmpty()) {
            for (Object group : groups) {
                List<Object> groupRows = new ArrayList<>();
                for (Object material : REFLECTION.groupMaterials(group)) {
                    if (isMissing(material, state)) {
                        groupRows.add(REFLECTION.materialRow(material));
                    }
                }
                if (!groupRows.isEmpty()) {
                    rows.add(REFLECTION.headerRow(REFLECTION.groupProductItemId(group)));
                    rows.addAll(groupRows);
                }
            }
        } else {
            for (Object material : fallbackMaterials) {
                rows.add(REFLECTION.materialRow(material));
            }
        }
        return REFLECTION.splitRows(screen, rows);
    }

    private static List<?> filterMissing(List<?> materials, ManifestState state) throws ReflectiveOperationException {
        List<Object> filtered = new ArrayList<>();
        for (Object material : materials) {
            if (isMissing(material, state)) {
                filtered.add(material);
            }
        }
        return filtered;
    }

    private static boolean isMissing(Object material, ManifestState state) throws ReflectiveOperationException {
        String itemId = REFLECTION.materialItemId(material);
        int required = REFLECTION.materialCount(material);
        int existing = state.counts.getOrDefault(itemId, 0);
        return existing < required;
    }

    private static Component toggleLabel(ManifestState state) {
        return Component.literal(state.showMissingOnly ? "显示全部" : "显示缺少");
    }

    private static ManifestState state(Screen screen) {
        return STATES.computeIfAbsent(screen, ignored -> new ManifestState());
    }

    private static boolean isManifestScreen(Screen screen) {
        return screen != null && MANIFEST_SCREEN.equals(screen.getClass().getName());
    }

    private static int intField(Screen screen, String fieldName, int fallback) {
        try {
            return REFLECTION.intField(screen, fieldName);
        } catch (ReflectiveOperationException ignored) {
            return fallback;
        }
    }

    private static final class ManifestState {
        private final Map<String, Integer> counts = new HashMap<>();
        private boolean showMissingOnly;
        private long requestId;
        private int progressValue;
        private int progressMax;
    }

    private static final class Reflection {
        private Field manifestStack;
        private Field materials;
        private Field pages;
        private Field currentPage;
        private Field itemsPerPage;
        private Method buildPages;
        private Method updatePageButtons;
        private Method getMaterials;
        private Method getProductGroups;
        private Method materialItemId;
        private Method materialCount;
        private Method groupProductItemId;
        private Method groupMaterials;
        private Method rowMaterial;
        private Method headerRow;
        private Method materialRow;
        private Constructor<?> pageSliceConstructor;
        private Method pageRows;

        private ItemStack manifestStack(Screen screen) throws ReflectiveOperationException {
            return (ItemStack) field(screen.getClass(), "manifestStack", true).get(screen);
        }

        private List<?> allMaterials(Object stack) throws ReflectiveOperationException {
            if (getMaterials == null) {
                getMaterials = Class.forName(MANIFEST_ITEM).getMethod("getMaterials", ItemStack.class);
            }
            return (List<?>) getMaterials.invoke(null, stack);
        }

        private List<?> productGroups(Object stack) throws ReflectiveOperationException {
            if (getProductGroups == null) {
                getProductGroups = Class.forName(MANIFEST_ITEM).getMethod("getProductGroups", ItemStack.class);
            }
            return (List<?>) getProductGroups.invoke(null, stack);
        }

        private void setMaterials(Screen screen, List<?> value) throws ReflectiveOperationException {
            if (materials == null) {
                materials = field(screen.getClass(), "materials", true);
            }
            materials.set(screen, value);
        }

        private List<?> pages(Screen screen) throws ReflectiveOperationException {
            if (pages == null) {
                pages = field(screen.getClass(), "pages", true);
            }
            return (List<?>) pages.get(screen);
        }

        private void setPages(Screen screen, List<?> value) throws ReflectiveOperationException {
            if (pages == null) {
                pages = field(screen.getClass(), "pages", true);
            }
            pages.set(screen, value);
        }

        private List<?> buildPages(Screen screen) throws ReflectiveOperationException {
            if (buildPages == null) {
                buildPages = screen.getClass().getDeclaredMethod("buildPages");
                buildPages.setAccessible(true);
            }
            return (List<?>) buildPages.invoke(screen);
        }

        private void setCurrentPage(Screen screen, int value) throws ReflectiveOperationException {
            if (currentPage == null) {
                currentPage = field(screen.getClass(), "currentPage", true);
            }
            currentPage.setInt(screen, value);
        }

        private void updatePageButtons(Screen screen) throws ReflectiveOperationException {
            if (updatePageButtons == null) {
                updatePageButtons = screen.getClass().getDeclaredMethod("updatePageButtons");
                updatePageButtons.setAccessible(true);
            }
            updatePageButtons.invoke(screen);
        }

        private int intField(Screen screen, String name) throws ReflectiveOperationException {
            return field(screen.getClass(), name, true).getInt(screen);
        }

        private String materialItemId(Object material) throws ReflectiveOperationException {
            if (materialItemId == null) {
                materialItemId = material.getClass().getMethod("itemId");
            }
            return (String) materialItemId.invoke(material);
        }

        private int materialCount(Object material) throws ReflectiveOperationException {
            if (materialCount == null) {
                materialCount = material.getClass().getMethod("count");
            }
            return (Integer) materialCount.invoke(material);
        }

        private String groupProductItemId(Object group) throws ReflectiveOperationException {
            if (groupProductItemId == null) {
                groupProductItemId = group.getClass().getMethod("productItemId");
            }
            return (String) groupProductItemId.invoke(group);
        }

        private List<?> groupMaterials(Object group) throws ReflectiveOperationException {
            if (groupMaterials == null) {
                groupMaterials = group.getClass().getMethod("materials");
            }
            return (List<?>) groupMaterials.invoke(group);
        }

        private Object rowMaterial(Object row) throws ReflectiveOperationException {
            if (rowMaterial == null) {
                rowMaterial = row.getClass().getDeclaredMethod("material");
                rowMaterial.setAccessible(true);
            }
            return rowMaterial.invoke(row);
        }

        private Object headerRow(String productItemId) throws ReflectiveOperationException {
            if (headerRow == null) {
                headerRow = Class.forName(TABLE_ROW).getDeclaredMethod("header", String.class);
                headerRow.setAccessible(true);
            }
            return headerRow.invoke(null, productItemId);
        }

        private Object materialRow(Object material) throws ReflectiveOperationException {
            if (materialRow == null) {
                materialRow = Class.forName(TABLE_ROW).getDeclaredMethod("material", Class.forName("com.xiaoliang.simukraft.item.ManifestItem$MaterialEntry"));
                materialRow.setAccessible(true);
            }
            return materialRow.invoke(null, material);
        }

        private List<?> splitRows(Screen screen, List<Object> rows) throws ReflectiveOperationException {
            int perPage = itemsPerPage(screen);
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < rows.size(); i += perPage) {
                int end = Math.min(i + perPage, rows.size());
                result.add(pageSlice(new ArrayList<>(rows.subList(i, end))));
            }
            return result;
        }

        private int itemsPerPage(Screen screen) throws ReflectiveOperationException {
            if (itemsPerPage == null) {
                itemsPerPage = field(screen.getClass(), "itemsPerPage", true);
            }
            return Math.max(1, itemsPerPage.getInt(screen));
        }

        private Object pageSlice(List<Object> rows) throws ReflectiveOperationException {
            if (pageSliceConstructor == null) {
                pageSliceConstructor = Class.forName(PAGE_SLICE).getDeclaredConstructor(List.class);
                pageSliceConstructor.setAccessible(true);
            }
            return pageSliceConstructor.newInstance(rows);
        }

        private List<?> pageRows(Object page) throws ReflectiveOperationException {
            if (pageRows == null) {
                pageRows = page.getClass().getDeclaredMethod("rows");
                pageRows.setAccessible(true);
            }
            return (List<?>) pageRows.invoke(page);
        }

        private Field field(Class<?> owner, String name, boolean accessible) throws NoSuchFieldException {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(accessible);
            return field;
        }
    }
}
