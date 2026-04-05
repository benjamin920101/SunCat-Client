package dev.suncat.mod.modules.impl.render;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.RenderEntityEvent;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.*;
import dev.suncat.suncat;
import java.awt.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;

public class LowEndRender extends Module {
    public static LowEndRender INSTANCE;

    public final BooleanSetting players = this.add(new BooleanSetting("Players", false).setParent());
    public final ColorSetting playersColor = this.add(new ColorSetting("PlayersColor", new Color(200, 60, 60), this.players::isOpen));
    public final BooleanSetting self = this.add(new BooleanSetting("Self", true, this.players::isOpen));
    public final ColorSetting selfColor = this.add(new ColorSetting("SelfColor", new Color(200, 60, 60), this.self::isOpen));
    public final BooleanSetting friends = this.add(new BooleanSetting("FriendsColor", true, this.players::isOpen));

    public final BooleanSetting animals = this.add(new BooleanSetting("Animals", true).setParent());
    public final ColorSetting animalsColor = this.add(new ColorSetting("AnimalsColor", new Color(0, 200, 0), this.animals::isOpen));

    public final BooleanSetting monsters = this.add(new BooleanSetting("Monsters", false).setParent());
    public final ColorSetting monstersColor = this.add(new ColorSetting("MonstersColor", new Color(200, 60, 60), this.monsters::isOpen));

    public final BooleanSetting items = this.add(new BooleanSetting("Items", true).setParent());
    public final ColorSetting itemsColor = this.add(new ColorSetting("ItemsColor", new Color(200, 100, 0), this.items::isOpen));

    public final BooleanSetting crystals = this.add(new BooleanSetting("Crystals", false).setParent());
    public final ColorSetting crystalsColor = this.add(new ColorSetting("CrystalsColor", new Color(200, 100, 200), this.crystals::isOpen));

    public final SliderSetting range = this.add(new SliderSetting("Range", 50.0, 10.0, 200.0, 1.0));

    public LowEndRender() {
        super("LowEndRender", Category.Render);
        this.setChinese("渣机渲染");
        INSTANCE = this;
    }

    public boolean shouldRenderPlayer(PlayerEntity player) {
        if (player == mc.player) {
            return self.getValue() && !mc.options.getPerspective().isFirstPerson();
        }
        if (friends.getValue() && suncat.FRIEND.isFriend(player)) {
            return true;
        }
        return players.getValue();
    }

    @EventListener
    public void onRender(RenderEntityEvent event) {
        Entity entity = event.getEntity();
        if (entity == null || !this.isOn()) {
            return;
        }

        // 检查距离
        if (mc.player != null && mc.player.getPos().distanceTo(entity.getPos()) > range.getValue()) {
            return;
        }

        // 玩家渲染控制
        if (entity instanceof PlayerEntity) {
            if (!shouldRenderPlayer((PlayerEntity) entity)) {
                event.cancel();
            }
        }
        // 动物渲染控制
        else if (entity instanceof net.minecraft.entity.passive.AnimalEntity) {
            if (animals.getValue()) {
                event.cancel();
            }
        }
        // 怪物渲染控制
        else if (entity instanceof MobEntity) {
            if (monsters.getValue()) {
                event.cancel();
            }
        }
        // 物品渲染控制
        else if (entity instanceof ItemEntity) {
            if (items.getValue()) {
                event.cancel();
            }
        }
    }
}
