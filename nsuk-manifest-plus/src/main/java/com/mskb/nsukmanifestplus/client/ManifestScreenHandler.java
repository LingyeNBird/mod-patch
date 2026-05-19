package com.mskb.nsukmanifestplus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;

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

    private static final WeakHashMap<Screen, ManifestState> STATES = new WeakHashMap<>();
    private static final Reflection REFLECTION = new Reflection();

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
        int contentX = intField(screen, "contentX", clipboardX + 16);
        int contentWidth = intField(screen, "contentWidth", 188);

        Button refreshButton = Button.builder(Component.literal("刷新"), button -> {
            refresh(screen, state);
            applyFilter(screen, state);
        }).bounds(clipboardX + 12, clipboardY + 8, 38, 18).build();

        Button toggleButton = Button.builder(toggleLabel(state), button -> {
            state.showMissingOnly = !state.showMissingOnly;
            button.setMessage(toggleLabel(state));
            applyFilter(screen, state);
        }).bounds(contentX + contentWidth - 58, clipboardY + 8, 58, 18).build();

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
                int existing = state.counts.getOrDefault(itemId, countPlayerItems(itemId));
                int missing = Math.max(0, required - existing);
                int percent = required <= 0 ? 100 : Math.min(100, existing * 100 / required);
                String text = "-已有" + percent + "%-缺" + missing + "/需" + required;

                int y = contentY + i * 24 + 8;
                int width = Minecraft.getInstance().font.width(text);
                int x = contentX + contentWidth - width;
                graphics.fill(x - 2, y - 1, contentX + contentWidth + 1, y + 10, 0xFFF4E7C5);
                graphics.drawString(Minecraft.getInstance().font, text, x, y, 0xFF333333, false);
            }
        } catch (ReflectiveOperationException ignored) {
            // The patch is intentionally best-effort against the external mod's private UI.
        }
    }

    private static void refresh(Screen screen, ManifestState state) {
        state.counts.clear();
        try {
            for (Object material : REFLECTION.allMaterials(REFLECTION.manifestStack(screen))) {
                String itemId = REFLECTION.materialItemId(material);
                state.counts.put(itemId, countPlayerItems(itemId));
            }
        } catch (ReflectiveOperationException ignored) {
            // Keep the last visible screen usable even if the external mod changes internals.
        }
    }

    private static void applyFilter(Screen screen, ManifestState state) {
        try {
            Object manifestStack = REFLECTION.manifestStack(screen);
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
        int existing = state.counts.getOrDefault(itemId, countPlayerItems(itemId));
        return existing < required;
    }

    private static int countPlayerItems(String itemId) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }

        ResourceLocation location = ResourceLocation.tryParse(itemId);
        if (location == null) {
            return 0;
        }

        Item item = ForgeRegistries.ITEMS.getValue(location);
        if (item == null || item == Items.AIR) {
            return 0;
        }

        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().armor) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
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

        private Object manifestStack(Screen screen) throws ReflectiveOperationException {
            return field(screen.getClass(), "manifestStack", true).get(screen);
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
