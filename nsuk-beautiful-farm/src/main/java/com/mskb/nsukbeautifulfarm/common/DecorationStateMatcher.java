package com.mskb.nsukbeautifulfarm.common;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

public final class DecorationStateMatcher {
    private DecorationStateMatcher() {
    }

    public static boolean isSatisfied(BlockState existing, BlockState target) {
        if (existing.equals(target)) {
            return true;
        }
        if (!existing.is(target.getBlock())) {
            return false;
        }
        for (Property<?> property : target.getProperties()) {
            if (!existing.hasProperty(property)) {
                continue;
            }
            if (isIgnoredProperty(property)) {
                continue;
            }
            if (!existing.getValue(property).equals(target.getValue(property))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIgnoredProperty(Property<?> property) {
        String name = property.getName();
        return property == BlockStateProperties.POWERED
                || property == BlockStateProperties.OPEN
                || name.equals("north")
                || name.equals("east")
                || name.equals("south")
                || name.equals("west")
                || name.equals("up");
    }
}
