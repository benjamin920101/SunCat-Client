package dev.suncat.mod.modules.impl.client;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.UpdateEvent;
import dev.suncat.api.utils.math.Easing;
import dev.suncat.mod.gui.clickgui.ClickGuiScreen;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.ColorSetting;
import dev.suncat.mod.modules.settings.impl.EnumSetting;
import dev.suncat.mod.modules.settings.impl.SliderSetting;

import java.awt.Color;

public class UI extends Module {
    public static UI INSTANCE;

    private final EnumSetting<Pages> page = this.add(new EnumSetting<>("Page", Pages.General));

    // General 页面设置
    public final BooleanSetting chinese = this.add(new BooleanSetting("Chinese", false, () -> this.page.getValue() == Pages.General));
    public final BooleanSetting font = this.add(new BooleanSetting("Font", true, () -> this.page.getValue() == Pages.General));
    public final BooleanSetting guiSound = this.add(new BooleanSetting("Sound", true, () -> this.page.getValue() == Pages.General));
    public final SliderSetting height = this.add(new SliderSetting("Height", 15, 10, 20, 1, () -> this.page.getValue() == Pages.General));
    public final EnumSetting<Easing> easeAnim = this.add(new EnumSetting<>("EaseAnim", Easing.QuadInOut, () -> this.page.getValue() == Pages.General));
    public final SliderSetting animationTime = this.add(new SliderSetting("AnimationTime", 200, 0, 1000, 1, () -> this.page.getValue() == Pages.General));
    public final EnumSetting<Easing> ease = this.add(new EnumSetting<>("Ease", Easing.QuadInOut, () -> this.page.getValue() == Pages.General));

    // Element 页面设置
    public final BooleanSetting activeBox = this.add(new BooleanSetting("ActiveBox", true, () -> this.page.getValue() == Pages.Element));
    public final BooleanSetting center = this.add(new BooleanSetting("Center", false, () -> this.page.getValue() == Pages.Element));
    public final BooleanSetting showDrawn = this.add(new BooleanSetting("ShowDrawn", true, () -> this.page.is(Pages.Element)));
    public final ColorSetting bindColor = this.add(new ColorSetting("Bind", new Color(255, 255, 255), () -> this.page.getValue() == Pages.Element).injectBoolean(false));
    public final ColorSetting gearColor = this.add(new ColorSetting("Gear", new Color(255, 255, 255), () -> this.page.getValue() == Pages.Element).injectBoolean(true));

    // Color 页面设置
    public final ColorSetting mainColor = this.add(new ColorSetting("Main", new Color(0, 0, 193, 77), () -> this.page.getValue() == Pages.Color));
    public final ColorSetting mainEnd = this.add(new ColorSetting("MainEnd", new Color(0, 0, 255, 130), () -> this.page.getValue() == Pages.Color).injectBoolean(false));
    public final ColorSetting mainHover = this.add(new ColorSetting("Hover", new Color(0, 0, 191, 124), () -> this.page.getValue() == Pages.Color));
    public final ColorSetting barColor = this.add(new ColorSetting("Bar", new Color(0, 0, 191, 106), () -> this.page.getValue() == Pages.Color));
    public final ColorSetting barEnd = this.add(new ColorSetting("BarEnd", new Color(0, 0, 255, 130), () -> this.page.getValue() == Pages.Color).injectBoolean(false));
    public final ColorSetting disableText = this.add(new ColorSetting("DisableText", new Color(0, 0, 255, 255), () -> this.page.getValue() == Pages.Color));
    public final ColorSetting enableText = this.add(new ColorSetting("EnableText", new Color(0, 0, 255, 255), () -> this.page.getValue() == Pages.Color));
    public final ColorSetting enableTextS = this.add(new ColorSetting("EnableText2", new Color(0, 0, 63, 255), () -> this.page.getValue() == Pages.Color));
    public final ColorSetting moduleColor = this.add(new ColorSetting("Module", new Color(0, 0, 50, 112), () -> this.page.getValue() == Pages.Color));
    public final ColorSetting moduleHover = this.add(new ColorSetting("ModuleHover", new Color(0, 0, 191, 122), () -> this.page.getValue() == Pages.Color));
    public final ColorSetting settingColor = this.add(new ColorSetting("Setting", new Color(0, 0, 50, 112), () -> this.page.getValue() == Pages.Color));
    public final ColorSetting settingHover = this.add(new ColorSetting("SettingHover", new Color(0, 0, 191, 112), () -> this.page.getValue() == Pages.Color));
    public final ColorSetting background = this.add(new ColorSetting("Background", new Color(0, 0, 10, 112), () -> this.page.getValue() == Pages.Color));

    public UI() {
        super("UI", Category.Client);
        this.setChinese("菜单");
        INSTANCE = this;
    }

    int lastHeight;

    @Override
    public void onEnable() {
        if (nullCheck()) {
            return;
        }

        if (this.lastHeight != this.height.getValueInt()) {
            this.applyHeight();
            this.lastHeight = this.height.getValueInt();
        }

        mc.setScreen(ClickGuiScreen.getInstance());
    }

    @Override
    public void onDisable() {
        if (mc.currentScreen instanceof ClickGuiScreen) {
            mc.currentScreen.close();
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (!(mc.currentScreen instanceof ClickGuiScreen)) {
            this.disable();
        }
    }

    private void applyHeight() {
        // 应用按钮高度设置到 GUI 组件
        int newHeight = this.height.getValueInt();
        ClickGuiScreen gui = ClickGuiScreen.getInstance();
        if (gui != null && gui.getComponents() != null) {
            gui.getComponents().forEach(component -> {
                component.setHeight(newHeight);
                component.getItems().forEach(item -> {
                    if (item instanceof dev.suncat.mod.gui.items.buttons.Button) {
                        // 更新按钮高度
                    }
                });
            });
        }
    }

    public enum Pages {
        General,
        Color,
        Element
    }
}
