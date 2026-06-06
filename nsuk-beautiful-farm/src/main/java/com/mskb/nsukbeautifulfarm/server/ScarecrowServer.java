package com.mskb.nsukbeautifulfarm.server;

import com.mskb.nsukbeautifulfarm.common.DecorationPlanner;
import com.mskb.nsukbeautifulfarm.common.FarmDecorationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ScarecrowServer {
    private static final String TAG_PREFIX = "nsukbf_scarecrow_";

    private ScarecrowServer() {
    }

    public static boolean maintainOne(ServerLevel level, BlockPos boxPos, BlockPos min, BlockPos max, FarmDecorationConfig config) {
        for (BlockPos ground : DecorationPlanner.scarecrowGroundPositions(min, max, config)) {
            if (maintainGround(level, boxPos, min, max, config, ground)) {
                return true;
            }
        }
        if (config.scarecrowStyle != FarmDecorationConfig.ScarecrowStyle.ARMOR_STAND_1) {
            return false;
        }
        for (BlockPos ground : DecorationPlanner.scarecrowGroundPositions(min, max, config)) {
            if (maintainArmorStand(level, boxPos, ground, config.scarecrowFacing)) {
                return true;
            }
        }
        return false;
    }

    public static boolean maintainGroundOne(ServerLevel level, BlockPos boxPos, BlockPos min, BlockPos max, FarmDecorationConfig config) {
        for (BlockPos ground : DecorationPlanner.scarecrowGroundPositions(min, max, config)) {
            if (maintainGround(level, boxPos, min, max, config, ground)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasUnsafeGround(ServerLevel level, BlockPos min, BlockPos max, FarmDecorationConfig config) {
        for (BlockPos ground : DecorationPlanner.scarecrowGroundPositions(min, max, config)) {
            if (isUnsafeGround(level, ground, min, max, config)) {
                return true;
            }
        }
        return false;
    }

    public static Map<Item, Integer> itemNeeds(ServerLevel level, BlockPos boxPos, BlockPos min, BlockPos max, FarmDecorationConfig config) {
        Map<Item, Integer> needs = new LinkedHashMap<>();
        if (config.scarecrowStyle != FarmDecorationConfig.ScarecrowStyle.ARMOR_STAND_1) {
            return needs;
        }
        for (BlockPos ground : DecorationPlanner.scarecrowGroundPositions(min, max, config)) {
            ArmorStand stand = findStand(level, boxPos, ground);
            if (stand == null) {
                addNeed(needs, Items.ARMOR_STAND);
                addNeed(needs, Items.LEATHER_CHESTPLATE);
                addNeed(needs, Items.CARVED_PUMPKIN);
                continue;
            }
            if (isMissing(stand.getItemBySlot(EquipmentSlot.CHEST), Items.LEATHER_CHESTPLATE)) {
                addNeed(needs, Items.LEATHER_CHESTPLATE);
            }
            if (isMissing(stand.getItemBySlot(EquipmentSlot.HEAD), Items.CARVED_PUMPKIN)) {
                addNeed(needs, Items.CARVED_PUMPKIN);
            }
        }
        return needs;
    }

    public static boolean cleanupInvalid(ServerLevel level, BlockPos boxPos, BlockPos min, BlockPos max, FarmDecorationConfig config) {
        Set<String> validTags = new HashSet<>();
        if (config.scarecrowStyle == FarmDecorationConfig.ScarecrowStyle.ARMOR_STAND_1) {
            for (BlockPos ground : DecorationPlanner.scarecrowGroundPositions(min, max, config)) {
                validTags.add(tag(boxPos, ground));
            }
        }
        for (ArmorStand stand : managedStands(level, boxPos)) {
            boolean valid = false;
            for (String tag : stand.getTags()) {
                if (validTags.contains(tag)) {
                    valid = true;
                    break;
                }
            }
            if (!valid) {
                stand.discard();
                return true;
            }
        }
        return false;
    }

    public static void removeAll(ServerLevel level, BlockPos boxPos) {
        for (ArmorStand stand : managedStands(level, boxPos)) {
            stand.discard();
        }
    }

    public static boolean isUnsafeGround(ServerLevel level, BlockPos ground, BlockPos min, BlockPos max, FarmDecorationConfig config) {
        if (isPlannedWaterDecorationGround(ground, min, max, config)) {
            return false;
        }
        return isUnsafeGround(level.getBlockState(ground));
    }

    private static boolean isUnsafeGround(BlockState state) {
        if (state.isAir() || state.is(Blocks.LAVA) || state.getBlock() instanceof FireBlock || state.getBlock() instanceof BushBlock) {
            return true;
        }
        if (state.getBlock() instanceof LiquidBlock) {
            return true;
        }
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && Boolean.TRUE.equals(state.getValue(BlockStateProperties.WATERLOGGED))) {
            return true;
        }
        return state.canBeReplaced();
    }

    private static boolean maintainGround(ServerLevel level, BlockPos boxPos, BlockPos min, BlockPos max, FarmDecorationConfig config, BlockPos ground) {
        if (!isUnsafeGround(level, ground, min, max, config)) {
            return false;
        }
        if (!LenientFarmingServer.consumeDecorationItem(level, boxPos, new ItemStack(Items.DIRT))) {
            return false;
        }
        level.setBlock(ground, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        return true;
    }

    private static boolean maintainArmorStand(ServerLevel level, BlockPos boxPos, BlockPos ground, Direction facing) {
        Vec3 target = new Vec3(ground.getX() + 0.5D, ground.getY() + 1.0D, ground.getZ() + 0.5D);
        List<ArmorStand> stands = findStands(level, boxPos, ground);
        if (stands.isEmpty()) {
            ItemStack armorStand = new ItemStack(Items.ARMOR_STAND);
            ItemStack chestplate = new ItemStack(Items.LEATHER_CHESTPLATE);
            ItemStack pumpkin = new ItemStack(Items.CARVED_PUMPKIN);
            if (!LenientFarmingServer.hasDecorationItem(level, boxPos, armorStand)
                    || !LenientFarmingServer.hasDecorationItem(level, boxPos, chestplate)
                    || !LenientFarmingServer.hasDecorationItem(level, boxPos, pumpkin)) {
                return false;
            }
            if (!LenientFarmingServer.consumeDecorationItem(level, boxPos, armorStand)
                    || !LenientFarmingServer.consumeDecorationItem(level, boxPos, chestplate)
                    || !LenientFarmingServer.consumeDecorationItem(level, boxPos, pumpkin)) {
                return false;
            }
            ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, level);
            stand.addTag(tag(boxPos, ground));
            stand.addTag(TAG_PREFIX + "managed");
            configureStand(stand, target, facing);
            stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
            level.addFreshEntity(stand);
            return true;
        }
        ArmorStand stand = stands.get(0);
        if (isMissing(stand.getItemBySlot(EquipmentSlot.CHEST), Items.LEATHER_CHESTPLATE)) {
            if (!LenientFarmingServer.consumeDecorationItem(level, boxPos, new ItemStack(Items.LEATHER_CHESTPLATE))) {
                return false;
            }
            stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            return true;
        }
        if (isMissing(stand.getItemBySlot(EquipmentSlot.HEAD), Items.CARVED_PUMPKIN)) {
            if (!LenientFarmingServer.consumeDecorationItem(level, boxPos, new ItemStack(Items.CARVED_PUMPKIN))) {
                return false;
            }
            stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
            return true;
        }
        boolean changed = configureStand(stand, target, facing);
        for (int i = 1; i < stands.size(); i++) {
            stands.get(i).discard();
            changed = true;
        }
        return changed;
    }

    private static boolean configureStand(ArmorStand stand, Vec3 target, Direction facing) {
        boolean changed = false;
        if (stand.distanceToSqr(target) > 0.01D) {
            stand.setPos(target.x, target.y, target.z);
            changed = true;
        }
        if (!stand.isNoGravity()) {
            stand.setNoGravity(true);
            changed = true;
        }
        if (!stand.noPhysics) {
            stand.noPhysics = true;
            changed = true;
        }
        stand.setDeltaMovement(Vec3.ZERO);
        float yRot = facing.toYRot();
        if (stand.getYRot() != yRot) {
            stand.setYRot(yRot);
            changed = true;
        }
        CompoundTag persistentData = stand.getPersistentData();
        if (!persistentData.getBoolean("nsukbeautifulfarm_scarecrow")) {
            persistentData.putBoolean("nsukbeautifulfarm_scarecrow", true);
            changed = true;
        }
        return changed;
    }

    private static boolean isMissing(ItemStack stack, net.minecraft.world.item.Item item) {
        return stack.isEmpty() || !stack.is(item);
    }

    private static ArmorStand findStand(ServerLevel level, BlockPos boxPos, BlockPos ground) {
        List<ArmorStand> stands = findStands(level, boxPos, ground);
        return stands.isEmpty() ? null : stands.get(0);
    }

    private static List<ArmorStand> findStands(ServerLevel level, BlockPos boxPos, BlockPos ground) {
        String tag = tag(boxPos, ground);
        return level.getEntitiesOfClass(ArmorStand.class, new AABB(ground).inflate(1.5D, 3.0D, 1.5D), stand -> stand.getTags().contains(tag));
    }

    private static List<ArmorStand> managedStands(ServerLevel level, BlockPos boxPos) {
        String prefix = TAG_PREFIX + key(boxPos) + "_";
        return level.getEntitiesOfClass(ArmorStand.class, new AABB(boxPos).inflate(256.0D), stand -> stand.getTags().stream().anyMatch(tag -> tag.startsWith(prefix)));
    }

    private static void addNeed(Map<Item, Integer> needs, Item item) {
        needs.merge(item, 1, Integer::sum);
    }

    private static boolean isPlannedWaterDecorationGround(BlockPos ground, BlockPos min, BlockPos max, FarmDecorationConfig config) {
        if (!DecorationPlanner.waterPositions(min, max, config).contains(ground)) {
            return false;
        }
        return config.waterStyle != FarmDecorationConfig.WaterStyle.NONE;
    }

    private static String tag(BlockPos boxPos, BlockPos ground) {
        return TAG_PREFIX + key(boxPos) + "_" + key(ground);
    }

    private static String key(BlockPos pos) {
        return pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
    }
}
