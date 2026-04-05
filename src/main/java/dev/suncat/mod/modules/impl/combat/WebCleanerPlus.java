package dev.suncat.mod.modules.impl.combat;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.UpdateEvent;
import dev.suncat.api.utils.math.Timer;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * WebCleanerPlus - 修复版：使用发包瞬间破坏蜘蛛网
 */
public class WebCleanerPlus extends Module {
    public static WebCleanerPlus INSTANCE;

    private final SliderSetting range = add(new SliderSetting("Range", 4.0, 0.0, 6.0));
    private final SliderSetting delay = add(new SliderSetting("Delay", 50.0, 0.0, 200.0));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));
    private final BooleanSetting onlyNearPlayer = add(new BooleanSetting("OnlyNearPlayer", false));

    private final Timer timer = new Timer();

    public WebCleanerPlus() {
        super("WebCleanerPlus", Category.Combat);
        this.setChinese("蜘蛛网清除Plus");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (Module.nullCheck()) {
            return;
        }
        if (!timer.passedMs((long)delay.getValue())) return;

        BlockPos closestWeb = null;
        double closestDist = Double.MAX_VALUE;

        // 遍历范围寻找蜘蛛网
        int radius = (int) Math.ceil(range.getValue());
        BlockPos playerPos = mc.player.getBlockPos();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = playerPos.add(x, y, z);

                    // 检测蜘蛛网
                    if (mc.world.getBlockState(checkPos).getBlock() == Blocks.COBWEB) {
                        double dist = checkPos.toCenterPos().distanceTo(mc.player.getEyePos());
                        if (dist <= range.getValue() && dist < closestDist) {

                            // 如果开启了只清除玩家附近的网，检查是否有敌人在附近
                            if (onlyNearPlayer.getValue()) {
                                boolean hasEnemy = false;
                                for (var p : mc.world.getPlayers()) {
                                    if (p != mc.player && p.isAlive() && p.distanceTo(mc.player) <= range.getValue()) {
                                        hasEnemy = true;
                                        break;
                                    }
                                }
                                if (!hasEnemy) continue;
                            }

                            closestDist = dist;
                            closestWeb = checkPos;
                        }
                    }
                }
            }
        }

        if (closestWeb != null) {
            breakWeb(closestWeb);
            timer.reset();
        }
    }

    private void breakWeb(BlockPos pos) {
        // 1. 自动旋转（让服务端认为玩家正在看这个方块）
        if (rotate.getValue()) {
            Vec3d hitVec = pos.toCenterPos();
            double diffX = hitVec.x - mc.player.getX();
            double diffY = hitVec.y - mc.player.getEyeY();
            double diffZ = hitVec.z - mc.player.getZ();

            double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

            float newYaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
            float newPitch = (float) (-Math.toDegrees(Math.atan2(diffY, diffXZ)));

            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                newYaw, newPitch, mc.player.isOnGround()
            ));
        }

        // 2. 发包瞬间破坏 (START 和 STOP)
        // 这样不需要持有工具也能瞬间清除
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP
        ));
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP
        ));

        // 3. 挥动手臂
        mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
    }
}
