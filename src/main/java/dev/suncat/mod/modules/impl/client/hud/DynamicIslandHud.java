package dev.suncat.mod.modules.impl.client.hud;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.ClientTickEvent;
import dev.suncat.api.utils.combat.CombatUtil;
import dev.suncat.api.utils.render.Render2DUtil;
import dev.suncat.api.utils.render.TextUtil;
import dev.suncat.core.impl.FontManager;
import dev.suncat.mod.modules.HudModule;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.impl.client.ClickGui;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.ColorSetting;
import dev.suncat.mod.modules.settings.impl.EnumSetting;
import dev.suncat.mod.modules.settings.impl.SliderSetting;
import dev.suncat.suncat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DynamicIslandHud extends HudModule {
    public static DynamicIslandHud INSTANCE;

    // Settings
    private final EnumSetting<Page> page = this.add(new EnumSetting<Page>("Page", Page.General));
    private final BooleanSetting showTime = this.add(new BooleanSetting("Time", true, () -> this.page.is(Page.General)));
    private final BooleanSetting showFps = this.add(new BooleanSetting("FPS", true, () -> this.page.is(Page.General)));
    private final BooleanSetting showEnemyHp = this.add(new BooleanSetting("EnemyHP", true, () -> this.page.is(Page.General)));
    private final SliderSetting enemyRange = this.add(new SliderSetting("EnemyRange", 20.0, 1.0, 100.0, 1.0, () -> this.page.is(Page.General) && this.showEnemyHp.getValue()));
    private final BooleanSetting autoWidth = this.add(new BooleanSetting("AutoWidth", true, () -> this.page.is(Page.General)));
    private final SliderSetting hudWidth = this.add(new SliderSetting("Width", 200, 100, 500, 1, () -> this.page.is(Page.General) && !this.autoWidth.getValue()));
    private final SliderSetting hudHeight = this.add(new SliderSetting("Height", 40, 30, 100, 1, () -> this.page.is(Page.General) && !this.autoWidth.getValue()));
    private final SliderSetting padding = this.add(new SliderSetting("Padding", 20, 5, 50, 1, () -> this.page.is(Page.General) && this.autoWidth.getValue()));
    
    // 位置设置 - 激活状态和空闲状态独立位置
    private final EnumSetting<PositionMode> positionMode = this.add(new EnumSetting<PositionMode>("PositionMode", PositionMode.Single, () -> this.page.is(Page.General)));
    // 单位置模式（兼容旧版）
    private final SliderSetting activeX = this.add(new SliderSetting("X", 10, 0, 1500, () -> this.page.is(Page.General) && this.positionMode.is(PositionMode.Single)));
    private final SliderSetting activeY = this.add(new SliderSetting("Y", 10, 0, 1000, () -> this.page.is(Page.General) && this.positionMode.is(PositionMode.Single)));
    // 双位置模式 - 激活状态
    private final SliderSetting activeX2 = this.add(new SliderSetting("ActiveX", 10, 0, 1500, () -> this.page.is(Page.General) && this.positionMode.is(PositionMode.Dual)));
    private final SliderSetting activeY2 = this.add(new SliderSetting("ActiveY", 10, 0, 1000, () -> this.page.is(Page.General) && this.positionMode.is(PositionMode.Dual)));
    // 双位置模式 - 空闲状态
    private final SliderSetting idleX = this.add(new SliderSetting("IdleX", 10, 0, 1500, () -> this.page.is(Page.General) && this.positionMode.is(PositionMode.Dual)));
    private final SliderSetting idleY = this.add(new SliderSetting("IdleY", 50, 0, 1000, () -> this.page.is(Page.General) && this.positionMode.is(PositionMode.Dual)));
    
    // 调试设置 - 强制显示激活状态位置
    private final BooleanSetting debugActive = this.add(new BooleanSetting("DebugActive", false, () -> this.page.is(Page.General) && this.positionMode.is(PositionMode.Dual)));

    private final BooleanSetting blur = this.add(new BooleanSetting("Blur", true, () -> this.page.is(Page.Color)).setParent());
    private final SliderSetting blurRadius = this.add(new SliderSetting("BlurRadius", 15.0, 0.0, 50.0, 1.0, () -> this.page.is(Page.Color) && this.blur.isOpen()));
    private final SliderSetting cornerRadius = this.add(new SliderSetting("CornerRadius", 20.0, 5.0, 50.0, 1.0, () -> this.page.is(Page.Color)));
    
    // 背景设置
    private final BooleanSetting showBackground = this.add(new BooleanSetting("ShowBackground", true, () -> this.page.is(Page.Color)).setParent());
    private final SliderSetting backgroundAlpha = this.add(new SliderSetting("BackgroundAlpha", 180, 0, 255, 1, () -> this.page.is(Page.Color) && this.showBackground.getValue()));
    private final BooleanSetting customColor = this.add(new BooleanSetting("CustomColor", false, () -> this.page.is(Page.Color) && this.showBackground.getValue()));
    private final ColorSetting backgroundColor = this.add(new ColorSetting("BackgroundColor", new Color(40, 40, 40, 180), () -> this.page.is(Page.Color) && this.customColor.getValue() && this.showBackground.getValue()));
    
    // 边框设置
    private final BooleanSetting showBorder = this.add(new BooleanSetting("ShowBorder", false, () -> this.page.is(Page.Color)).setParent());
    private final SliderSetting borderWidth = this.add(new SliderSetting("BorderWidth", 1.5f, 0.5f, 5.0f, 0.5f, () -> this.page.is(Page.Color) && this.showBorder.getValue()));
    private final ColorSetting borderColor = this.add(new ColorSetting("BorderColor", new Color(255, 255, 255, 100), () -> this.page.is(Page.Color) && this.showBorder.getValue()));

    private final List<ModuleState> moduleStates = new CopyOnWriteArrayList<>();
    private String currentDisplayText = "";
    private boolean isModuleActive = false; // 标记是否是模块激活状态
    private long lastModuleChange = 0;
    private static final long MODULE_CHANGE_DELAY = 5000L; // 5秒后切换到默认信息

    public DynamicIslandHud() {
        super("DynamicIsland", "", "灵动岛", 10, 10, Corner.LeftTop);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        // 初始化所有模块状态
        moduleStates.clear();
        for (Module module : suncat.MODULE.getModules()) {
            moduleStates.add(new ModuleState(module.getName(), module.isOn()));
        }
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (DynamicIslandHud.nullCheck()) return;
        if (!event.isPost()) return;

        // 调试模式：强制显示激活状态位置
        if (this.debugActive.getValue()) {
            this.isModuleActive = true;
            this.currentDisplayText = "Active Preview [+]";
            return;
        }

        // 检查最新的模块变化
        ModuleState latestChange = null;
        for (ModuleState state : moduleStates) {
            Module module = getModuleByName(state.name);
            if (module != null) {
                boolean isOn = module.isOn();
                if (state.isOn != isOn) {
                    state.isOn = isOn;
                    state.changeTime = System.currentTimeMillis();
                }
                // 找到最近的变化
                if ((System.currentTimeMillis() - state.changeTime) < MODULE_CHANGE_DELAY) {
                    if (latestChange == null || state.changeTime > latestChange.changeTime) {
                        latestChange = state;
                    }
                }
            }
        }

        if (latestChange != null) {
            lastModuleChange = latestChange.changeTime;
            isModuleActive = true;
            String status = latestChange.isOn ? "[+]" : "[-]";
            currentDisplayText = latestChange.name + " " + status;
        } else {
            // 没有最近的模块变化，显示默认信息
            isModuleActive = false;
            updateDefaultDisplay();
        }
    }

    private void updateDefaultDisplay() {
        StringBuilder sb = new StringBuilder();
        
        // 时间
        if (this.showTime.getValue()) {
            String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).format(new Date());
            sb.append("TIME ").append(timeStr);
        }

        // FPS
        if (this.showFps.getValue()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("FPS: ").append(suncat.FPS.getFps());
        }

        // 敌人血量
        if (this.showEnemyHp.getValue()) {
            PlayerEntity closestEnemy = CombatUtil.getClosestEnemy(this.enemyRange.getValue());
            if (closestEnemy != null && closestEnemy.isAlive()) {
                float hp = closestEnemy.getHealth();
                float abs = ((LivingEntity) closestEnemy).getAbsorptionAmount();
                if (sb.length() > 0) sb.append(" | ");
                sb.append(closestEnemy.getName().getString()).append(" HP: ").append(String.format("%.1f", hp));
                if (abs > 0) {
                    sb.append(" | ABS: ").append(String.format("%.1f", abs));
                }
            } else {
                if (sb.length() > 0) sb.append(" | ");
                sb.append("No Enemy");
            }
        }

        currentDisplayText = sb.toString();
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (DynamicIslandHud.nullCheck() || currentDisplayText.isEmpty()) {
            this.clearHudBounds();
            return;
        }

        // 根据 autoWidth 设置计算总宽度和总高度
        int textWidth = getTextWidth(currentDisplayText);
        int fontHeight = getFontHeight();

        int totalWidth, totalHeight;
        if (this.autoWidth.getValue()) {
            // 自适应模式：根据文字宽度 + 内边距计算
            int pad = this.padding.getValueInt();
            totalWidth = textWidth + pad * 2;
            totalHeight = fontHeight + pad;
        } else {
            // 固定模式：使用设置值
            totalWidth = this.hudWidth.getValueInt();
            totalHeight = this.hudHeight.getValueInt();
        }

        // 根据位置模式和状态获取 X/Y
        int x, y;
        if (this.positionMode.is(PositionMode.Dual)) {
            // 双位置模式：根据状态使用不同的位置
            if (isModuleActive) {
                x = getHudRenderX(totalWidth, this.activeX2.getValueInt());
                y = getHudRenderY(totalHeight, this.activeY2.getValueInt());
            } else {
                x = getHudRenderX(totalWidth, this.idleX.getValueInt());
                y = getHudRenderY(totalHeight, this.idleY.getValueInt());
            }
        } else {
            // 单位置模式：兼容旧版
            x = this.getHudRenderX(totalWidth);
            y = this.getHudRenderY(totalHeight);
        }

        // 获取颜色
        ClickGui gui = ClickGui.getInstance();

        // 绘制背景（如果开启）
        if (this.showBackground.getValue()) {
            int alpha = this.backgroundAlpha.getValueInt();
            Color bgColor;
            if (this.customColor.getValue()) {
                Color picked = this.backgroundColor.getValue();
                bgColor = new Color(picked.getRed(), picked.getGreen(), picked.getBlue(), alpha);
            } else {
                if (gui != null) {
                    Color baseColor = gui.getColor(0);
                    bgColor = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alpha);
                } else {
                    bgColor = new Color(40, 40, 40, alpha);
                }
            }

            // 应用模糊（仅在有背景时）
            if (this.blur.getValue()) {
                suncat.BLUR.applyBlur(this.blurRadius.getValueFloat(), x, y, totalWidth, totalHeight);
            }

            // 绘制圆角背景
            float cornerR = this.cornerRadius.getValueFloat();
            Render2DUtil.drawRoundedRect(context.getMatrices(), x, y, totalWidth, totalHeight, cornerR, bgColor);
        }

        // 绘制边框（如果开启）
        if (this.showBorder.getValue()) {
            float cornerR = this.cornerRadius.getValueFloat();
            float borderW = this.borderWidth.getValueFloat();
            Color borderC = this.borderColor.getValue();
            Render2DUtil.drawRoundedStroke(context.getMatrices(), x, y, totalWidth, totalHeight, cornerR, borderC, 12);
        }

        // 绘制文字（居中）
        int textX = x + (totalWidth - textWidth) / 2;
        int textY = y + (totalHeight - fontHeight) / 2;

        // 获取文字颜色
        int textColor;
        if (isModuleActive) {
            // 模块激活状态：检查是 [+] 还是 [-]
            if (currentDisplayText.contains("[+]")) {
                textColor = Color.GREEN.getRGB(); // [+] 绿色
            } else if (currentDisplayText.contains("[-]")) {
                textColor = Color.RED.getRGB();   // [-] 红色
            } else {
                // 默认颜色
                textColor = getDefaultTextColor(gui);
            }
        } else {
            // 空闲状态：使用默认颜色
            textColor = getDefaultTextColor(gui);
        }

        TextUtil.drawString(context, currentDisplayText, textX, textY, textColor, HudSetting.useFont(), HudSetting.useShadow());

        this.setHudBounds(x, y, totalWidth, totalHeight);
    }

    /**
     * 获取默认文字颜色
     */
    private int getDefaultTextColor(ClickGui gui) {
        if (!this.showBackground.getValue() || (!this.customColor.getValue() && gui != null)) {
            if (gui != null) {
                Color lineColor = gui.getColor(0);
                return lineColor.getRGB();
            }
        }
        return Color.WHITE.getRGB();
    }

    private int getTextWidth(String text) {
        if (HudSetting.useFont()) {
            return (int) FontManager.ui.getWidth(text);
        }
        return mc.textRenderer.getWidth(text);
    }

    private int getFontHeight() {
        if (HudSetting.useFont()) {
            return (int) FontManager.ui.getFontHeight();
        }
        return 9;
    }
    
    /**
     * 自定义边距的 HUD 渲染 X
     */
    private int getHudRenderX(int elementW, int margin) {
        if (DynamicIslandHud.mc.getWindow() == null) {
            return margin;
        }
        int sw = DynamicIslandHud.mc.getWindow().getScaledWidth();
        int w = Math.max(0, elementW);
        int x = this.corner.getValue().isRight() ? (sw - w - margin) : margin;
        return clampInt(x, 0, Math.max(0, sw - w));
    }
    
    /**
     * 自定义边距的 HUD 渲染 Y
     */
    private int getHudRenderY(int elementH, int margin) {
        if (DynamicIslandHud.mc.getWindow() == null) {
            return margin;
        }
        int sh = DynamicIslandHud.mc.getWindow().getScaledHeight();
        int h = Math.max(0, elementH);
        int y = this.corner.getValue().isBottom() ? (sh - h - margin) : margin;
        return clampInt(y, 0, Math.max(0, sh - h));
    }
    
    private static int clampInt(int v, int min, int max) {
        if (v < min) {
            return min;
        }
        return Math.min(v, max);
    }

    private Module getModuleByName(String name) {
        for (Module module : suncat.MODULE.getModules()) {
            if (module.getName().equalsIgnoreCase(name)) {
                return module;
            }
        }
        return null;
    }

    public enum Page {
        General,
        Color
    }
    
    public enum PositionMode {
        Single,  // 单位置模式（兼容旧版）
        Dual     // 双位置模式（激活/空闲独立位置）
    }

    private static class ModuleState {
        public final String name;
        public boolean isOn;
        public long changeTime;

        public ModuleState(String name, boolean isOn) {
            this.name = name;
            this.isOn = isOn;
            this.changeTime = System.currentTimeMillis();
        }
    }
}
