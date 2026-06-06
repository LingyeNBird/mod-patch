package com.mskb.nsukbeautifulfarm.server;

import net.minecraft.core.BlockPos;

final class WorkState {
    final String dimension;
    final BlockPos boxPos;
    final LenientFarmingServer.PlotBounds bounds;
    final LenientFarmingServer.CropPlan crop;
    boolean topCleared;
    boolean maintenanceMode;
    boolean paused;
    int idleTicks;

    WorkState(String dimension, BlockPos boxPos, LenientFarmingServer.PlotBounds bounds, LenientFarmingServer.CropPlan crop, boolean topCleared) {
        this.dimension = dimension;
        this.boxPos = boxPos;
        this.bounds = bounds;
        this.crop = crop;
        this.topCleared = topCleared;
    }

    WorkKey key() {
        return new WorkKey(dimension, boxPos);
    }
}
