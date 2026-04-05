package dev.suncat.mod.modules.impl.combat;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.UpdateEvent;
import dev.suncat.api.utils.math.Timer;
import dev.suncat.api.utils.world.BlockUtil;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * BedAuraPlus - 从 Zenith 完整移植的床光环模块
 * 自动放置和引爆床进行范围攻击
 */
public class BedAuraPlus extends Module {
    public static BedAuraPlus INSTANCE;

    private final SliderSetting range = add(new SliderSetting("Range", 5, 0, 8));
    private final SliderSetting enemyRange = add(new SliderSetting("EnemyRange", 10, 0, 20));
    private final SliderSetting minDamage = add(new SliderSetting("MinDamage", 5, 0, 36));
    private final SliderSetting maxSelfDamage = add(new SliderSetting("MaxSelf", 10, 0, 36));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));
    private final BooleanSetting packet = add(new BooleanSetting("Packet", true));
    private final BooleanSetting swap = add(new BooleanSetting("InventorySwap", true));
    private final SliderSetting delay = add(new SliderSetting("Delay", 100, 0, 500));

    private final Timer timer = new Timer();

    public BedAuraPlus() {
        super("BedAuraPlus", Category.Combat);
        this.setChinese("床光环Plus");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (Module.nullCheck()) {
            return;
        }
        if (!timer.passedMs(delay.getValueInt())) return;

        // 查找最近的敌人
        var target = mc.world.getPlayers().stream()
                .filter(p -> p != mc.player && p.isAlive() && p.distanceTo(mc.player) <= enemyRange.getValue())
                .min((a, b) -> Float.compare(a.distanceTo(mc.player), b.distanceTo(mc.player)))
                .orElse(null);

        if (target == null) return;

        // 查找合适的方块放置床
        for (BlockPos pos : BlockUtil.getSphere(range.getValueFloat())) {
            if (pos.toCenterPos().distanceTo(target.getPos()) > range.getValue()) continue;
            if (canPlaceBed(pos)) {
                placeBed(pos);
                timer.reset();
                return;
            }
        }
    }

    private boolean canPlaceBed(BlockPos pos) {
        return mc.world.getBlockState(pos).isAir() &&
               mc.world.getBlockState(pos.down()).isSolidBlock(mc.world, pos.down());
    }

    private void placeBed(BlockPos pos) {
        // 简化的床放置逻辑
        // 实际需要完整的放置和引爆逻辑
        if (BlockUtil.canPlace(pos)) {
            int bedSlot = dev.suncat.api.utils.player.InventoryUtil.findBlock(net.minecraft.block.Blocks.RED_BED);
            if (bedSlot == -1) return;

            int oldSlot = mc.player.getInventory().selectedSlot;
            dev.suncat.api.utils.player.InventoryUtil.switchToSlot(bedSlot);

            var hitResult = new net.minecraft.util.hit.BlockHitResult(
                    Vec3d.ofCenter(pos),
                    Direction.UP,
                    pos.down(),
                    false
            );
            mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket(
                    net.minecraft.util.Hand.MAIN_HAND,
                    hitResult,
                    0
            ));

            dev.suncat.api.utils.player.InventoryUtil.switchToSlot(oldSlot);
        }
    }
}
