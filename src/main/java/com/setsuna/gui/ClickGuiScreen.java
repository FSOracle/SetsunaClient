package com.setsuna.gui;

import com.setsuna.config.ConfigManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.ModuleManager;
import com.setsuna.module.modules.ClickGui;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.Setting;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ButtonSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.FontSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.ui.SkijaScreen;
import com.setsuna.util.KeyBindText;
import io.github.humbleui.skija.Canvas;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The Setsuna ClickGUI: one draggable, collapsible panel per {@link Category},
 * each listing its modules with expandable settings. Input and layout stay in
 * this screen while {@link SkijaRenderer} draws the final frame with Skija.
 * <p>
 * Controls:
 * <ul>
 *   <li>Panel header — left-drag to move, right-click to collapse.</li>
 *   <li>Module row — left-click toggles (or expands non-toggleable modules),
 *       right-click expands settings, middle-click starts key-binding.</li>
 *   <li>Setting row — booleans toggle, enums cycle (left +1 / right -1),
 *       numbers drag as a slider (right-click steps down), buttons run.</li>
 *   <li>While binding: any key sets the bind; Esc/Delete clears it.</li>
 * </ul>
 */
public final class ClickGuiScreen extends Screen implements SkijaScreen {

    private static final int PANEL_WIDTH = 110;
    private static final int PANEL_GAP = 6;
    private static final int HEADER_H = 14;
    private static final int MODULE_H = 13;
    private static final int SETTING_H = 12;
    private static final int PAD_X = 4;
    private static final int MARGIN = 6;

    private static final int COLOR_BACKDROP = argb(140, 0, 0, 0);
    private static final int COLOR_PANEL = argb(215, 22, 24, 30);
    private static final int COLOR_MODULE = argb(235, 32, 34, 42);
    private static final int COLOR_MODULE_HOVER = argb(235, 44, 47, 58);
    private static final int COLOR_SETTING = argb(235, 27, 29, 36);
    private static final int COLOR_TRACK = argb(255, 60, 64, 76);
    private static final int COLOR_TEXT = argb(255, 235, 237, 242);
    private static final int COLOR_TEXT_DIM = argb(255, 160, 164, 174);
    private static final int COLOR_TEXT_OFF = argb(255, 120, 124, 134);

    private final List<Panel> panels = new ArrayList<>();

    private Panel draggingPanel;
    private int dragOffsetX;
    private int dragOffsetY;

    private Setting<?> draggingNumber;
    private int numberRowX;
    private int numberRowWidth;

    private Module bindingModule;

    private int renderMouseX;
    private int renderMouseY;

    public ClickGuiScreen() {
        super(Component.literal("Setsuna"));
    }

    @Override
    protected void init() {
        panels.clear();
        int x = MARGIN;
        for (Category category : Category.values()) {
            if (!category.guiVisible()) {
                continue;
            }
            List<Module> inCategory = ModuleManager.INSTANCE.modulesIn(category);
            if (inCategory.isEmpty()) {
                continue;
            }
            panels.add(new Panel(category, inCategory, x, MARGIN));
            x += PANEL_WIDTH + PANEL_GAP;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Skija draws the complete screen after Minecraft presents its main target.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        renderMouseX = mouseX;
        renderMouseY = mouseY;
        if (SkijaRenderer.hasFailed()) {
            graphics.fill(0, 0, width, height, COLOR_BACKDROP);
            graphics.text(font, "Skija renderer failed - check latest.log", MARGIN, MARGIN, COLOR_TEXT, false);
        }
    }

    /** Called on the render thread after the Minecraft framebuffer is presented. */
    public void renderSkija(Canvas canvas) {
        SkijaUi.fill(canvas, 0, 0, width, height, COLOR_BACKDROP);
        for (Panel panel : panels) {
            panel.render(canvas, renderMouseX, renderMouseY);
        }
    }

    // --- mouse ----------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x();
        int my = (int) event.y();
        int button = event.button();

        // Iterate front-to-back; a click brings its panel to the front.
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel panel = panels.get(i);
            if (panel.mouseClicked(mx, my, button)) {
                panels.remove(i);
                panels.add(panel);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingPanel = null;
        draggingNumber = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        int mx = (int) event.x();
        int my = (int) event.y();
        if (draggingPanel != null) {
            draggingPanel.x = mx - dragOffsetX;
            draggingPanel.y = my - dragOffsetY;
            return true;
        }
        if (draggingNumber != null) {
            updateNumber(draggingNumber, mx);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    // --- keyboard -------------------------------------------------------------

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (bindingModule != null) {
            int key = event.key();
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
                bindingModule.setKeyBind(-1);
            } else {
                bindingModule.setKeyBind(key);
            }
            bindingModule = null;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        ConfigManager.INSTANCE.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- helpers --------------------------------------------------------------

    private void updateNumber(Setting<?> setting, int mouseX) {
        float fraction = clamp01((float) (mouseX - numberRowX) / (float) numberRowWidth);
        if (setting instanceof IntSetting intSetting) {
            int range = intSetting.max() - intSetting.min();
            int raw = Math.round(intSetting.min() + range * fraction);
            int step = intSetting.step();
            intSetting.set(Math.round((float) raw / step) * step);
        } else if (setting instanceof DoubleSetting doubleSetting) {
            double range = doubleSetting.max() - doubleSetting.min();
            double raw = doubleSetting.min() + range * fraction;
            double step = doubleSetting.step();
            doubleSetting.set(Math.round(raw / step) * step);
        }
    }

    private static String settingValueText(Setting<?> setting) {
        if (setting instanceof BooleanSetting b) {
            return b.get() ? "ON" : "OFF";
        }
        if (setting instanceof EnumSetting<?> e) {
            return e.displayValue();
        }
        if (setting instanceof FontSetting font) {
            return font.displayValue();
        }
        if (setting instanceof DoubleSetting d) {
            return trimZeros(String.format(java.util.Locale.ROOT, "%.2f", d.get()));
        }
        if (setting instanceof IntSetting i) {
            return Integer.toString(i.get());
        }
        if (setting instanceof ColorSetting c) {
            return c.get().getRed() + "," + c.get().getGreen() + "," + c.get().getBlue();
        }
        Object value = setting.get();
        return value == null ? "" : value.toString();
    }

    private static String trimZeros(String s) {
        if (s.indexOf('.') < 0) {
            return s;
        }
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && s.charAt(end - 1) == '.') {
            end--;
        }
        return s.substring(0, end);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** A category column. Rows are laid out top-down with identical math in
     *  {@link #render} and {@link #mouseClicked}, so hit-testing stays in sync. */
    private final class Panel {

        private final Category category;
        private final List<Entry> entries = new ArrayList<>();
        private int x;
        private int y;
        private boolean collapsed;

        private Panel(Category category, List<Module> modules, int x, int y) {
            this.category = category;
            this.x = x;
            this.y = y;
            for (Module module : modules) {
                // Non-toggleable modules (e.g. the GUI opener) start expanded so
                // their settings are reachable without triggering them.
                entries.add(new Entry(module, !module.isToggleable()));
            }
        }

        private void render(Canvas canvas, int mouseX, int mouseY) {
            SkijaUi.rounded(canvas, x, y, PANEL_WIDTH, HEADER_H, 3.0F, COLOR_PANEL);
            drawCentered(canvas, category.displayName(), y, HEADER_H, COLOR_TEXT, true);

            if (collapsed) {
                return;
            }

            int rowY = y + HEADER_H;
            for (Entry entry : entries) {
                Module module = entry.module;
                boolean hovered = inside(mouseX, mouseY, x, rowY, PANEL_WIDTH, MODULE_H);
                SkijaUi.fill(canvas, x, rowY, PANEL_WIDTH, MODULE_H, hovered ? COLOR_MODULE_HOVER : COLOR_MODULE);

                int nameColor = !module.isToggleable() ? COLOR_TEXT
                        : module.isEnabled() ? ClickGui.INSTANCE.accent.argb() : COLOR_TEXT_OFF;
                SkijaUi.text(canvas, module.displayName(), x + PAD_X, rowY, MODULE_H, nameColor);

                String right = bindingModule == module ? "..." : KeyBindText.of(module.keyBind());
                if (!right.isEmpty()) {
                    float rightWidth = SkijaUi.textWidth(right);
                    SkijaUi.text(canvas, right, x + PANEL_WIDTH - PAD_X - rightWidth, rowY, MODULE_H, COLOR_TEXT_DIM);
                }
                rowY += MODULE_H;

                if (entry.expanded) {
                    for (Setting<?> setting : module.settings()) {
                        if (!setting.visible()) {
                            continue;
                        }
                        renderSetting(canvas, setting, rowY);
                        rowY += SETTING_H;
                    }
                }
            }
        }

        private void renderSetting(Canvas canvas, Setting<?> setting, int rowY) {
            SkijaUi.fill(canvas, x, rowY, PANEL_WIDTH, SETTING_H, COLOR_SETTING);

            if (setting instanceof ButtonSetting) {
                drawCentered(canvas, setting.displayName(), rowY, SETTING_H, ClickGui.INSTANCE.accent.argb(), false);
                return;
            }

            float fraction = numberFraction(setting);
            if (fraction >= 0) {
                // Slider fill along the bottom of the row.
                int fillW = Math.round((PANEL_WIDTH - 2) * fraction);
                SkijaUi.fill(canvas, x + 1, rowY + SETTING_H - 2, PANEL_WIDTH - 2, 1, COLOR_TRACK);
                SkijaUi.fill(canvas, x + 1, rowY + SETTING_H - 2, fillW, 1, ClickGui.INSTANCE.accent.argb());
            }

            SkijaUi.text(canvas, setting.displayName(), x + PAD_X + 2, rowY, SETTING_H, COLOR_TEXT_DIM);
            String value = settingValueText(setting);
            float valueWidth = SkijaUi.textWidth(value);
            int valueColor = setting instanceof BooleanSetting b
                    ? (b.get() ? ClickGui.INSTANCE.accent.argb() : COLOR_TEXT_OFF)
                    : COLOR_TEXT;
            SkijaUi.text(canvas, value, x + PANEL_WIDTH - PAD_X - valueWidth, rowY, SETTING_H, valueColor);

            if (setting instanceof ColorSetting color) {
                // Small swatch just left of the value.
                float swatchX = x + PANEL_WIDTH - PAD_X - valueWidth - 10;
                SkijaUi.rounded(canvas, swatchX, rowY + 3, 6, SETTING_H - 6, 1.5F, color.argb());
            }
        }

        private boolean mouseClicked(int mx, int my, int button) {
            if (inside(mx, my, x, y, PANEL_WIDTH, HEADER_H)) {
                if (button == 0) {
                    draggingPanel = this;
                    dragOffsetX = mx - x;
                    dragOffsetY = my - y;
                } else if (button == 1) {
                    collapsed = !collapsed;
                }
                return true;
            }

            if (collapsed) {
                return false;
            }

            int rowY = y + HEADER_H;
            for (Entry entry : entries) {
                Module module = entry.module;
                if (inside(mx, my, x, rowY, PANEL_WIDTH, MODULE_H)) {
                    if (button == 2) {
                        bindingModule = module;
                    } else if (button == 0) {
                        if (module.isToggleable()) {
                            module.toggle();
                        } else {
                            entry.expanded = !entry.expanded;
                        }
                    } else if (button == 1) {
                        entry.expanded = !entry.expanded;
                    }
                    return true;
                }
                rowY += MODULE_H;

                if (entry.expanded) {
                    for (Setting<?> setting : module.settings()) {
                        if (!setting.visible()) {
                            continue;
                        }
                        if (inside(mx, my, x, rowY, PANEL_WIDTH, SETTING_H)) {
                            clickSetting(setting, button, mx);
                            return true;
                        }
                        rowY += SETTING_H;
                    }
                }
            }
            return false;
        }

        private void clickSetting(Setting<?> setting, int button, int mx) {
            if (setting instanceof BooleanSetting b) {
                b.toggle();
            } else if (setting instanceof FontSetting font) {
                font.cycle(button == 1 ? -1 : 1);
            } else if (setting instanceof EnumSetting<?> e) {
                e.cycle(button == 1 ? -1 : 1);
            } else if (setting instanceof ButtonSetting btn) {
                btn.press();
            } else if (setting instanceof IntSetting i) {
                if (button == 1) {
                    i.set(i.get() - i.step());
                } else {
                    draggingNumber = setting;
                    numberRowX = x + 1;
                    numberRowWidth = PANEL_WIDTH - 2;
                    updateNumber(setting, mx);
                }
            } else if (setting instanceof DoubleSetting d) {
                if (button == 1) {
                    d.set(d.get() - d.step());
                } else {
                    draggingNumber = setting;
                    numberRowX = x + 1;
                    numberRowWidth = PANEL_WIDTH - 2;
                    updateNumber(setting, mx);
                }
            }
            // ColorSetting / StringSetting are display-only in the base GUI.
        }

        private void drawCentered(Canvas canvas, String text, float top, float height, int color, boolean bold) {
            float textWidth = bold ? SkijaUi.boldTextWidth(text) : SkijaUi.textWidth(text);
            float textX = x + (PANEL_WIDTH - textWidth) * 0.5F;
            if (bold) {
                SkijaUi.boldText(canvas, text, textX, top, height, color);
            } else {
                SkijaUi.text(canvas, text, textX, top, height, color);
            }
        }
    }

    private static float numberFraction(Setting<?> setting) {
        if (setting instanceof IntSetting i) {
            return i.fraction();
        }
        if (setting instanceof DoubleSetting d) {
            return d.fraction();
        }
        return -1.0F;
    }

    /** A module and whether its settings are currently expanded in this panel. */
    private static final class Entry {
        private final Module module;
        private boolean expanded;

        private Entry(Module module, boolean expanded) {
            this.module = module;
            this.expanded = expanded;
        }
    }
}
