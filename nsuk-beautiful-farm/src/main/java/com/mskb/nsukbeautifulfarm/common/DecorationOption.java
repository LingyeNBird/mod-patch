package com.mskb.nsukbeautifulfarm.common;

public enum DecorationOption {
    BORDER("边框装饰"),
    WATER("水装饰"),
    WATER_COVER("水遮盖装饰"),
    SCARECROW("稻草人装饰");

    private final String displayName;

    DecorationOption(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
