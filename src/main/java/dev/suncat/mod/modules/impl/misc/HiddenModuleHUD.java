package dev.suncat.mod.modules.impl.misc;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.Render2DEvent;
import dev.suncat.mod.modules.HudModule;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

import java.awt.*;
import java.util.List;

/**
 * HiddenModuleHUD - 隐藏模块 HUD 显示
 * 在屏幕上显示被隐藏的模块，点击可以解锁/关闭
 */
public class HiddenModuleHUD extends HudModule {
    public static HiddenModuleHUD INSTANCE;

    private final BooleanSetting showHidden = this.add(new BooleanSetting("ShowHidden", true));
    private final BooleanSetting clickToUnlock = this.add(new BooleanSetting("ClickToUnlock", true));
    private final BooleanSetting clickToDisable = this.add(new BooleanSetting("ClickToDisable", false));

    private int hoveredModuleIndex = -1;

    public HiddenModuleHUD() {
        super("HiddenModuleHUD", "显示被隐藏的模块并允许操作", "隐藏模块HUD", 10, 100);
        this.setChinese("隐藏模块HUD");
        INSTANCE = this;
    }

    @EventListener
    public void onRender2D(Render2DEvent event) {
        if (Module.nullCheck() || !this.isOn()) return;
        if (!HiddenModule.INSTANCE.isOn()) return;

        List<Module> hiddenModules = HiddenModule.getHiddenModules();
        if (hiddenModules.isEmpty()) return;

        DrawContext context = event.drawContext;
        int x = this.getHudX();
        int y = this.getHudY();
        int lineHeight = 12;

        // 绘制背景
        context.fill(x - 2, y - 2, x + 120 + 2, y + hiddenModules.size() * lineHeight + 2, new Color(0, 0, 0, 150).getRGB());

        // 绘制标题
        context.drawText(mc.textRenderer, "隐藏模块", x, y, Color.YELLOW.getRGB(), true);
        y += lineHeight;

        // 绘制每个隐藏的模块
        hoveredModuleIndex = -1;
        for (int i = 0; i < hiddenModules.size(); i++) {
            Module module = hiddenModules.get(i);
            boolean isHovered = isMouseOverModule(x, y, i, lineHeight);

            if (isHovered) {
                hoveredModuleIndex = i;
                // 高亮悬停的模块
                context.fill(x - 1, y - 1, x + 120 + 1, y + lineHeight - 1, new Color(50, 50, 50, 200).getRGB());
            }

            // 模块状态颜色
            Color textColor = module.isOn() ? Color.GREEN : Color.RED;
            String status = module.isOn() ? "[开]" : "[关]";
            context.drawText(mc.textRenderer, status + " " + module.getDisplayName(), x + 2, y, textColor.getRGB(), true);

            y += lineHeight;
        }
    }

    private boolean isMouseOverModule(int x, int y, int index, int lineHeight) {
        double mouseX = mc.mouse.getX() * mc.getWindow().getScaleFactor();
        double mouseY = mc.mouse.getY() * mc.getWindow().getScaleFactor();
        int moduleY = y + index * 12;

        return mouseX >= x && mouseX <= x + 120 &&
               mouseY >= moduleY && mouseY <= moduleY + 12;
    }

    @Override
    public String getInfo() {
        if (!HiddenModule.INSTANCE.isOn()) return "未开启";
        int count = HiddenModule.getHiddenModules().size();
        return count + " 个隐藏模块";
    }
}
