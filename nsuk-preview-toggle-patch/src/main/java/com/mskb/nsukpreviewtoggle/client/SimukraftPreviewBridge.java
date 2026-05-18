package com.mskb.nsukpreviewtoggle.client;

import java.lang.reflect.Method;

final class SimukraftPreviewBridge {
    private static final String MANAGER_CLASS = "com.xiaoliang.simukraft.client.preview.BuildingPreviewManager";

    private static Class<?> managerClass;
    private static Method isPreviewActive;
    private static Method isRangeOnlyPreview;
    private static Method loadBlocks;
    private static Method activateRangeOnlyPreview;

    private SimukraftPreviewBridge() {
    }

    static boolean isPreviewActive() throws ReflectiveOperationException {
        return (Boolean) isPreviewActiveMethod().invoke(null);
    }

    static boolean isRangeOnlyPreview() throws ReflectiveOperationException {
        return (Boolean) isRangeOnlyPreviewMethod().invoke(null);
    }

    static void loadBlocks() throws ReflectiveOperationException {
        loadBlocksMethod().invoke(null);
    }

    static void activateRangeOnlyPreview() throws ReflectiveOperationException {
        activateRangeOnlyPreviewMethod().invoke(null);
    }

    private static Class<?> managerClass() throws ClassNotFoundException {
        if (managerClass == null) {
            managerClass = Class.forName(MANAGER_CLASS);
        }
        return managerClass;
    }

    private static Method isPreviewActiveMethod() throws ReflectiveOperationException {
        if (isPreviewActive == null) {
            isPreviewActive = managerClass().getMethod("isPreviewActive");
        }
        return isPreviewActive;
    }

    private static Method isRangeOnlyPreviewMethod() throws ReflectiveOperationException {
        if (isRangeOnlyPreview == null) {
            isRangeOnlyPreview = managerClass().getMethod("isRangeOnlyPreview");
        }
        return isRangeOnlyPreview;
    }

    private static Method loadBlocksMethod() throws ReflectiveOperationException {
        if (loadBlocks == null) {
            loadBlocks = managerClass().getMethod("loadBlocks");
        }
        return loadBlocks;
    }

    private static Method activateRangeOnlyPreviewMethod() throws ReflectiveOperationException {
        if (activateRangeOnlyPreview == null) {
            activateRangeOnlyPreview = managerClass().getDeclaredMethod("activateRangeOnlyPreview");
            activateRangeOnlyPreview.setAccessible(true);
        }
        return activateRangeOnlyPreview;
    }
}
