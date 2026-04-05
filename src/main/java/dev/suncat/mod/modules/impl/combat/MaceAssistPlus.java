package dev.suncat.mod.modules.impl.combat;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.UpdateEvent;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * MaceAssistPlus - 从 Zenith 移植的重锤助手模块
 * 自动使用重锤进行坠落攻击
 */
public class MaceAssistPlus extends Module {
    public static MaceAssistPlus INSTANCE;

    private final SliderSetting range = add(new SliderSetting("Range", 5, 0, 8));
    private final BooleanSetting onlyFall = add(new BooleanSetting("OnlyFall", true));
    private final SliderSetting minFallDist = add(new SliderSetting("MinFallDist", 3, 0, 10));
    private final BooleanSetting autoJump = add(new BooleanSetting("AutoJump", true));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));
    private final SliderSetting delay = add(new SliderSetting("Delay", 100, 0, 500));

    private long lastAttackTime = 0;

    public MaceAssistPlus() {
        super("MaceAssistPlus", Category.Combat);
        this.setChinese("重锤助手Plus");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (Module.nullCheck()) {
            return;
        }
        if (System.currentTimeMillis() - lastAttackTime < delay.getValueInt()) return;

        // 检查是否持有重锤
        if (!mc.player.getMainHandStack().getItem().toString().contains("mace")) return;

        // 查找目标
        PlayerEntity target = mc.world.getPlayers().stream()
                .filter(p -> p != mc.player && p.isAlive() && p.distanceTo(mc.player) <= range.getValue())
                .min((a, b) -> Float.compare(a.distanceTo(mc.player), b.distanceTo(mc.player)))
                .orElse(null);

        if (target == null) return;

        // 检查坠落距离
        if (onlyFall.getValue()) {
            double fallDist = mc.player.getY() - target.getY();
            if (fallDist < minFallDist.getValue()) {
                if (autoJump.getValue() && mc.player.isOnGround()) {
                    mc.player.jump();
                }
                return;
            }
        }

        // 攻击
        if (rotate.getValue()) {
            mc.player.setYaw((float) Math.toDegrees(Math.atan2(
                    target.getZ() - mc.player.getZ(),
                    target.getX() - mc.player.getX()
            )));
        }

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        lastAttackTime = System.currentTimeMillis();
    }
}
