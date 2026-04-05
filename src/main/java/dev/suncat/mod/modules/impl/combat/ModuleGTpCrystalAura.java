package dev.suncat.mod.modules.impl.combat;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.UpdateEvent;
import dev.suncat.api.utils.combat.ExplosionUtilPlus;
import dev.suncat.api.utils.player.InventoryUtil;
import dev.suncat.api.utils.world.BlockUtil;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.impl.client.ClientSetting;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ModuleGTpCrystalAura - TP 水晶光环
 * 放置和破坏水晶时瞬移到目标位置，操作后返回
 */
public class ModuleGTpCrystalAura extends Module {
    public static ModuleGTpCrystalAura INSTANCE;

    private final SliderSetting range = add(new SliderSetting("Range", 5.0, 0.0, 6.0));
    private final SliderSetting wallRange = add(new SliderSetting("WallRange", 5.0, 0.0, 6.0));
    private final SliderSetting placeRange = add(new SliderSetting("PlaceRange", 5.0, 0.0, 6.0));
    private final SliderSetting breakRange = add(new SliderSetting("BreakRange", 5.0, 0.0, 6.0));
    
    private final BooleanSetting place = add(new BooleanSetting("Place", true));
    private final BooleanSetting breakCrystal = add(new BooleanSetting("Break", true));
    private final BooleanSetting tp = add(new BooleanSetting("TP", true));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));
    private final BooleanSetting packet = add(new BooleanSetting("PacketPlace", true));
    private final BooleanSetting swap = add(new BooleanSetting("AutoSwap", true));
    
    private final SliderSetting minDamage = add(new SliderSetting("MinDamage", 5.0, 0.0, 36.0));
    private final SliderSetting maxSelfDamage = add(new SliderSetting("MaxSelf", 12.0, 0.0, 36.0));
    private final SliderSetting placeDelay = add(new SliderSetting("PlaceDelay", 0, 0, 1000));
    private final SliderSetting breakDelay = add(new SliderSetting("BreakDelay", 0, 0, 1000));

    private long lastPlaceTime = 0;
    private long lastBreakTime = 0;
    private Vec3d originalPos = null;

    public ModuleGTpCrystalAura() {
        super("ModuleGTpCrystalAura", Category.Combat);
        this.setChinese("GTp 水晶光环");
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        if (originalPos != null && mc.player != null) {
             mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                originalPos.x, originalPos.y, originalPos.z, mc.player.isOnGround()));
        }
        originalPos = null;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (Module.nullCheck()) return;

        // 记录原始位置
        if (originalPos == null) {
            originalPos = mc.player.getPos();
        }

        // 1. 寻找最佳水晶进行破坏
        if (breakCrystal.getValue()) {
            EndCrystalEntity targetCrystal = findBestCrystalToBreak();
            if (targetCrystal != null && canBreak(targetCrystal)) {
                if (tp.getValue()) {
                    // 瞬移到水晶旁边
                    tpToPos(targetCrystal.getPos());
                }
                if (rotate.getValue()) {
                    lookAt(targetCrystal.getPos());
                }
                mc.interactionManager.attackEntity(mc.player, targetCrystal);
                mc.player.swingHand(Hand.MAIN_HAND);
                lastBreakTime = System.currentTimeMillis();
                
                if (tp.getValue()) {
                    tpBack();
                }
                return; // 优先破坏
            }
        }

        // 2. 寻找最佳位置放置水晶
        if (place.getValue()) {
            if (System.currentTimeMillis() - lastPlaceTime < placeDelay.getValue()) return;
            
            PlaceResult result = findBestPlacePos();
            if (result != null) {
                if (tp.getValue()) {
                    // 瞬移到放置点旁边
                    tpToPos(result.pos.toCenterPos());
                }
                if (rotate.getValue()) {
                    lookAt(result.pos.toCenterPos());
                }
                
                placeCrystal(result.pos, result.side);
                lastPlaceTime = System.currentTimeMillis();
                
                if (tp.getValue()) {
                    tpBack();
                }
            }
        }
    }

    private void tpToPos(Vec3d pos) {
        // 瞬移到目标位置旁边一点，避免卡块
        Vec3d target = pos.add(0, 0.5, 0); 
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            target.x, target.y, target.z, mc.player.isOnGround()));
    }

    private void tpBack() {
        if (originalPos != null) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                originalPos.x, originalPos.y, originalPos.z, mc.player.isOnGround()));
        }
    }

    private void lookAt(Vec3d pos) {
        double diffX = pos.x - mc.player.getX();
        double diffY = pos.y - mc.player.getEyeY();
        double diffZ = pos.z - mc.player.getZ();
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(diffY, diffXZ)));
        mc.player.setYaw(MathHelper.wrapDegrees(yaw));
        mc.player.setPitch(MathHelper.clamp(pitch, -90, 90));
    }

    private EndCrystalEntity findBestCrystalToBreak() {
        EndCrystalEntity bestCrystal = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof EndCrystalEntity crystal) {
                if (crystal.distanceTo(mc.player) <= breakRange.getValue()) {
                    if (!canSee(crystal.getPos()) && crystal.distanceTo(mc.player) > wallRange.getValue()) continue;
                    
                    // 简单逻辑：优先破坏最近的水晶
                    double dist = crystal.distanceTo(mc.player);
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestCrystal = crystal;
                    }
                }
            }
        }
        return bestCrystal;
    }

    private boolean canBreak(EndCrystalEntity crystal) {
        return System.currentTimeMillis() - lastBreakTime >= breakDelay.getValue();
    }

    private PlaceResult findBestPlacePos() {
        PlaceResult bestResult = null;
        float maxDamage = 0;

        BlockPos playerPos = mc.player.getBlockPos();
        int r = (int) Math.ceil(range.getValue());

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (canPlaceCrystal(pos)) {
                        float damage = calculateDamage(pos);
                        if (damage > maxDamage && damage >= minDamage.getValue()) {
                            maxDamage = damage;
                            bestResult = new PlaceResult(pos, getPlaceSide(pos));
                        }
                    }
                }
            }
        }
        return bestResult;
    }

    private boolean canPlaceCrystal(BlockPos pos) {
        BlockPos obsPos = pos.down();
        BlockPos boost = obsPos.up();
        BlockPos boost2 = boost.up();

        // 检查基岩或黑曜石
        boolean isSafeBase = mc.world.getBlockState(obsPos).getBlock() == Blocks.BEDROCK || 
                             mc.world.getBlockState(obsPos).getBlock() == Blocks.OBSIDIAN;
        
        // 检查空间是否足够
        boolean space1 = mc.world.isAir(boost) || (mc.world.getBlockState(boost).getBlock() == Blocks.FIRE);
        boolean space2 = mc.world.isAir(boost2) || (!ClientSetting.INSTANCE.lowVersion.getValue()); // 1.12 需要两格空气

        // 检查是否有实体阻挡
        boolean noEntity = mc.world.getOtherEntities(mc.player, new Box(boost)).isEmpty() &&
                           mc.world.getOtherEntities(mc.player, new Box(boost2)).isEmpty();

        return isSafeBase && space1 && space2 && noEntity;
    }

    private float calculateDamage(BlockPos pos) {
        // 寻找范围内最近的敌人
        PlayerEntity target = mc.world.getPlayers().stream()
                .filter(p -> p != mc.player && p.isAlive() && p.distanceTo(mc.player) <= range.getValue())
                .min(Comparator.comparingDouble(e -> e.distanceTo(mc.player)))
                .orElse(null);
        
        if (target == null) return 0;

        float damage = ExplosionUtilPlus.calculateDamage(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, target, 6.0f);
        float selfDamage = ExplosionUtilPlus.calculateDamage(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, mc.player, 6.0f);

        if (selfDamage > maxSelfDamage.getValue()) return 0;
        if (selfDamage >= target.getHealth() + target.getAbsorptionAmount()) return 0; // 防止自杀

        return damage;
    }

    private void placeCrystal(BlockPos pos, Direction side) {
        if (side == null) return;

        // 切换水晶
        int slot = -1;
        if (swap.getValue()) {
            slot = InventoryUtil.findItem(Items.END_CRYSTAL);
            if (slot == -1) slot = InventoryUtil.findItemInventorySlot(Items.END_CRYSTAL);
        } else {
            slot = InventoryUtil.findItem(Items.END_CRYSTAL);
        }

        if (slot == -1) return;

        int oldSlot = mc.player.getInventory().selectedSlot;
        if (swap.getValue() && slot >= 0) {
             if (slot < 9) {
                 mc.player.getInventory().selectedSlot = slot;
                 mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(slot));
             } else {
                 InventoryUtil.inventorySwap(slot, mc.player.getInventory().selectedSlot);
             }
        }

        Vec3d hitVec = pos.toCenterPos().add(new Vec3d(side.getVector().getX() * 0.5, side.getVector().getY() * 0.5, side.getVector().getZ() * 0.5));
        BlockHitResult hitResult = new BlockHitResult(hitVec, side.getOpposite(), pos, false);

        if (packet.getValue()) {
            mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, 0));
        } else {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        }

        if (swap.getValue() && slot >= 0) {
             if (slot < 9) {
                 mc.player.getInventory().selectedSlot = oldSlot;
                 mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(oldSlot));
             } else {
                 InventoryUtil.inventorySwap(slot, mc.player.getInventory().selectedSlot);
             }
        }
    }

    private Direction getPlaceSide(BlockPos pos) {
        for (Direction side : Direction.values()) {
            if (side == Direction.UP || side == Direction.DOWN) continue;
            BlockPos neighbor = pos.offset(side);
            if (mc.world.getBlockState(neighbor).isSideSolidFullSquare(mc.world, neighbor, side.getOpposite())) {
                return side;
            }
        }
        return null;
    }

    private boolean canSee(Vec3d vec) {
        return mc.world.raycast(new RaycastContext(mc.player.getEyePos(), vec, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player)).getType() == net.minecraft.util.hit.HitResult.Type.MISS;
    }

    private static class PlaceResult {
        BlockPos pos;
        Direction side;
        public PlaceResult(BlockPos pos, Direction side) {
            this.pos = pos;
            this.side = side;
        }
    }
}
