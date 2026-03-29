/*
 * SunCat Client - MiddleClickDrop Module
 * 中键删除物品模块
 *
 * 功能:
 * - 禁用原版中键绑定（丢弃物品）
 * - 开启后中键不再丢弃物品
 */
package dev.suncat.mod.modules.impl.player;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.PacketEvent;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;

public class MiddleClickDrop extends Module {
    public static MiddleClickDrop INSTANCE;

    private final BooleanSetting debug = this.add(new BooleanSetting("Debug", false));

    public MiddleClickDrop() {
        super("MiddleClickDrop", Category.Player);
        this.setChinese("禁用中键丢弃");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (debug.getValue()) {
            sendMessage("§a[MCDrop] §7已禁用原版中键丢弃");
        }
    }

    @Override
    public void onDisable() {
        if (debug.getValue()) {
            sendMessage("§c[MCDrop] §7已恢复原版中键丢弃");
        }
    }

    @EventListener
    public void onPacket(PacketEvent.Send event) {
        if (Module.nullCheck()) {
            return;
        }

        // 拦截丢弃物品的数据包
        if (event.getPacket() instanceof PlayerActionC2SPacket) {
            PlayerActionC2SPacket packet = (PlayerActionC2SPacket) event.getPacket();
            if (packet.getAction() == PlayerActionC2SPacket.Action.DROP_ITEM) {
                event.setCancelled(true);
                if (debug.getValue()) {
                    sendMessage("§a[MCDrop] §7已拦截中键丢弃");
                }
            }
        }
    }
}
