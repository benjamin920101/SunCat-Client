package dev.suncat.mod.modules.impl.combat;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.UpdateEvent;
import dev.suncat.api.utils.player.InventoryUtil;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.EnumSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;

/**
 * MainhandPlus - 从 Zenith 移植的主手管理模块
 * 自动管理主手物品
 */
public class MainhandPlus extends Module {
    public static MainhandPlus INSTANCE;

    public final EnumSetting<Item> mainItem = add(new EnumSetting<>("Item", Item.Crystal));
    private final BooleanSetting onlyCombat = add(new BooleanSetting("OnlyCombat", true));

    public MainhandPlus() {
        super("MainhandPlus", Category.Combat);
        this.setChinese("主手管理Plus");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (Module.nullCheck()) {
            return;
        }
        int slot = -1;
        switch (mainItem.getValue()) {
            case Crystal:
                slot = InventoryUtil.findItem(Items.END_CRYSTAL);
                break;
            case Obsidian:
                slot = InventoryUtil.findBlock(net.minecraft.block.Blocks.OBSIDIAN);
                break;
            case Sword:
                slot = findSword();
                break;
            case Bow:
                slot = InventoryUtil.findItem(Items.BOW);
                break;
            case Totem:
                slot = InventoryUtil.findItem(Items.TOTEM_OF_UNDYING);
                break;
        }

        if (slot != -1 && mc.player.getInventory().selectedSlot != slot) {
            mc.player.getInventory().selectedSlot = slot;
            mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(slot));
        }
    }

    private int findSword() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem().toString().contains("sword")) return i;
        }
        return -1;
    }

    public enum Item {
        Crystal, Obsidian, Sword, Bow, Totem
    }
}
