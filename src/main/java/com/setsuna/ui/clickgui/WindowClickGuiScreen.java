package com.setsuna.ui.clickgui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.setsuna.Setsuna;
import com.setsuna.config.ConfigManager;
import com.setsuna.i18n.TranslationKey;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.ModuleManager;
import com.setsuna.module.modules.ClickGui;
import com.setsuna.notification.NotificationManager;
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
import com.setsuna.setting.settings.KeybindSetting;
import com.setsuna.setting.settings.StringSetting;
import com.setsuna.ui.CategoryGlyphs;
import com.setsuna.ui.SkijaScreen;
import com.setsuna.ui.hud.EpsilonHudModule;
import com.setsuna.ui.hud.HUD;
import com.setsuna.util.KeyBindText;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.Rect;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Drop-style floating panel ClickGUI drawn over the blurred live game scene. */
public final class WindowClickGuiScreen extends Screen implements SkijaScreen {

    private static final float PANEL_WIDTH = 112.0F;
    private static final float MARGIN = 12.0F;
    private static final float HEADER_HEIGHT = 20.0F;
    private static final float MODULE_HEIGHT = 17.0F;
    private static final float SETTING_HEIGHT = 17.0F;
    private static final float NUMBER_HEIGHT = 30.0F;
    private static final float COLOR_CHANNEL_HEIGHT = 10.0F;
    private static final float MAX_BODY_HEIGHT = 310.0F;
    private static final float SCROLL_STEP = 22.0F;
    private static final float BODY_INSET = 3.0F;
    private static final float SECTION_INSET = 4.0F;
    private static final float SLIDER_FADE_WIDTH = 5.0F;
    private static final float EXPAND_SPEED = 14.0F;
    private static final float MIN_SIX_COLUMN_WIDTH = MARGIN * 2.0F
            + PANEL_WIDTH * 6.0F + 8.0F * 5.0F;

    private static final float FONT = 9.0F;
    private static final float FONT_SMALL = 7.5F;

    private static final int BACKDROP = argb(52, 2, 8, 13);
    private static final int BODY = argb(224, 38, 39, 41);
    private static final int PANEL_EDGE = argb(135, 8, 10, 13);
    private static final int SETTING_BACKGROUND = argb(188, 31, 33, 36);
    private static final int TEXT = argb(255, 255, 255, 255);
    private static final int TEXT_DIM = argb(255, 170, 170, 170);
    private static final int TEXT_OFF = argb(255, 85, 85, 85);
    private static final int TRACK = argb(255, 67, 71, 82);
    private static final int CONFIG_GREEN = argb(255, 85, 255, 85);
    private static final int DELETE_BACKGROUND = argb(230, 145, 38, 38);
    private static final Paint GRADIENT_PAINT = new Paint().setAntiAlias(false);
    private static final Gson STATE_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STATE_FILE_NAME = "drop-clickgui-state.json";

    /** Backend insertion point for the Configs panel. */
    public interface ConfigProvider {
        default String currentName() {
            return "default";
        }

        default List<String> profiles() {
            return List.of();
        }

        default void save(String name) {
        }

        default void load(String name) {
        }

        default void delete(String name) {
        }

        default void persist() {
        }
    }

    private static final ConfigProvider DEFAULT_CONFIG_PROVIDER = new ConfigProvider() {
        @Override
        public String currentName() {
            return ConfigManager.INSTANCE.currentProfile();
        }

        @Override
        public List<String> profiles() {
            return ConfigManager.INSTANCE.listProfiles();
        }

        @Override
        public void save(String name) {
            try {
                ConfigManager.INSTANCE.saveProfile(name);
            } catch (Exception error) {
                Setsuna.LOGGER.error("Failed to save Drop ClickGUI profile {}", name, error);
            }
        }

        @Override
        public void load(String name) {
            try {
                ConfigManager.INSTANCE.loadProfile(name);
            } catch (Exception error) {
                Setsuna.LOGGER.error("Failed to load Drop ClickGUI profile {}", name, error);
            }
        }

        @Override
        public void delete(String name) {
            try {
                ConfigManager.INSTANCE.deleteProfile(name);
            } catch (Exception error) {
                Setsuna.LOGGER.error("Failed to delete Drop ClickGUI profile {}", name, error);
            }
        }

        @Override
        public void persist() {
            ConfigManager.INSTANCE.save();
        }
    };
    private static volatile ConfigProvider configProvider = DEFAULT_CONFIG_PROVIDER;
    private static final Map<String, PanelMemory> PANEL_MEMORY = new HashMap<>();
    private static final List<String> PANEL_ORDER_MEMORY = new ArrayList<>();
    private static boolean panelMemorySaved;
    private static boolean panelMemoryDiskLoaded;

    public static void installConfigProvider(ConfigProvider provider) {
        configProvider = provider == null ? DEFAULT_CONFIG_PROVIDER : provider;
    }

    private final List<Panel> panels = new ArrayList<>();

    private Panel draggingPanel;
    private float dragOffsetX;
    private float dragOffsetY;

    private Setting<?> draggingNumber;
    private float draggingTrackX;
    private float draggingTrackWidth;
    private ColorSetting draggingColor;
    private int draggingColorChannel = -1;

    private Module capturingModule;
    private KeybindSetting capturingKeybind;

    private TextTarget textTarget = TextTarget.NONE;
    private StringSetting editingString;
    private ColorSetting editingColor;
    private Color colorBeforeEdit;
    private String editText = "";
    private int editCursor;
    private boolean selectAll;

    private float activeScale = 1.0F;
    private float animationDelta;
    private float renderMouseX;
    private float renderMouseY;
    private long lastFrame = System.nanoTime();
    private final Screen parent;

    public WindowClickGuiScreen() {
        this(null);
    }

    public WindowClickGuiScreen(Screen parent) {
        super(Component.literal(tr("title", "Setsuna ClickGUI")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        activeScale = configuredScale();
        lastFrame = System.nanoTime();
        if (panels.isEmpty()) {
            loadPanelMemoryFromDisk();
            Category[] categories = {
                    Category.COMBAT, Category.MISC, Category.RENDER,
                    Category.MOVEMENT, Category.PLAYER, Category.CLIENT
            };
            float y = MARGIN;
            for (int index = 0; index < categories.length; index++) {
                Category category = categories[index];
                panels.add(new ModulePanel(category, ModuleManager.INSTANCE.modulesIn(category),
                        MARGIN, y));
                y += HEADER_HEIGHT + 4.0F;
            }
            panels.add(new ConfigPanel(MARGIN, y));
            restorePanelMemory();
        }
        clampPanels();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blurBeforeThisStratum();
        graphics.fill(0, 0, width, height, 0x24010A0F);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        renderMouseX = (float) logical(mouseX);
        renderMouseY = (float) logical(mouseY);
        if (SkijaRenderer.hasFailed()) {
            graphics.fill(0, 0, width, height, BACKDROP);
            graphics.text(font, "Skija renderer failed - check latest.log", 8, 8, TEXT, false);
        }
    }

    @Override
    public void renderSkija(Canvas canvas) {
        long now = System.nanoTime();
        animationDelta = Math.min(0.05F,
                Math.max(0.0F, (now - lastFrame) / 1_000_000_000.0F));
        lastFrame = now;
        float logicalWidth = logicalWidth();
        float logicalHeight = logicalHeight();
        clampPanels();

        canvas.save();
        canvas.scale(activeScale, activeScale);
        SkijaUi.fill(canvas, 0.0F, 0.0F, logicalWidth + 1.0F, logicalHeight + 1.0F, BACKDROP);
        for (Panel panel : panels) {
            panel.render(canvas, logicalHeight);
        }
        canvas.restore();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        float mouseX = (float) logical(event.x());
        float mouseY = (float) logical(event.y());
        int button = event.button();

        finishTextEditing();
        capturingModule = null;
        capturingKeybind = null;

        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel panel = panels.get(i);
            if (panel.mouseClicked(mouseX, mouseY, button, logicalHeight())) {
                panels.remove(i);
                panels.add(panel);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        float mouseX = (float) logical(event.x());
        float mouseY = (float) logical(event.y());
        if (draggingPanel != null) {
            draggingPanel.x = mouseX - dragOffsetX;
            draggingPanel.y = mouseY - dragOffsetY;
            draggingPanel.clampPosition(logicalWidth(), logicalHeight());
            return true;
        }
        if (draggingNumber != null) {
            updateNumber(mouseX);
            return true;
        }
        if (draggingColor != null) {
            updateColor(mouseX);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingPanel = null;
        draggingNumber = null;
        draggingColor = null;
        draggingColorChannel = -1;
        activeScale = configuredScale();
        clampPanels();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float logicalX = (float) logical(mouseX);
        float logicalY = (float) logical(mouseY);
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel panel = panels.get(i);
            if (panel.scrollAt(logicalX, logicalY, (float) verticalAmount, logicalHeight())) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (capturingKeybind != null) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                capturingKeybind = null;
            } else if (event.key() == GLFW.GLFW_KEY_DELETE || event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                capturingKeybind.set(KeybindSetting.NONE);
                capturingKeybind = null;
            } else if (event.key() != GLFW.GLFW_KEY_UNKNOWN) {
                capturingKeybind.set(event.key());
                capturingKeybind = null;
            }
            return true;
        }
        if (capturingModule != null) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                capturingModule = null;
            } else if (event.key() == GLFW.GLFW_KEY_DELETE || event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                capturingModule.setKeyBind(-1);
                capturingModule = null;
            } else if (event.key() != GLFW.GLFW_KEY_UNKNOWN) {
                capturingModule.setKeyBind(event.key());
                capturingModule = null;
            }
            return true;
        }
        if (textTarget != TextTarget.NONE && handleTextKey(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (textTarget == TextTarget.NONE || !event.isAllowedChatCharacter()) {
            return super.charTyped(event);
        }
        insertText(event.codepointAsString());
        return true;
    }

    @Override
    public void onClose() {
        finishTextEditing();
        savePanelMemory();
        configProvider.persist();
        if (parent != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public void removed() {
        savePanelMemory();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private abstract class Panel {
        private final String memoryId;
        private final String title;
        private final String icon;
        protected final int headerColor;
        protected final float panelWidth;
        protected float x;
        protected float y;
        private float scroll;
        private boolean collapsed = true;
        private float expandProgress;

        private Panel(String memoryId, String title, String icon, int headerColor, float x, float y) {
            this.memoryId = memoryId;
            this.title = title;
            this.icon = icon;
            this.headerColor = headerColor;
            this.x = x;
            this.y = y;
            this.panelWidth = PANEL_WIDTH;
        }

        private void render(Canvas canvas, float screenHeight) {
            updateAnimations();
            expandProgress = animateTowards(expandProgress, collapsed ? 0.0F : 1.0F,
                    EXPAND_SPEED, animationDelta);
            float bodyHeight = bodyHeight(screenHeight);
            SkijaUi.fill(canvas, x - 1.0F, y - 1.0F, panelWidth + 2.0F,
                    HEADER_HEIGHT + 2.0F, PANEL_EDGE);
            drawGradientRect(canvas, x, y, panelWidth, HEADER_HEIGHT,
                    mixColor(headerColor, 0xFF172229, 0.24F), headerColor);
            SkijaUi.icon(canvas, icon, x + 5.0F, y, HEADER_HEIGHT,
                    TEXT, 11.5F, SkijaUi.IconSet.LUCIDE);
            drawBoldText(canvas, fit(title, panelWidth - 39.0F, true),
                    x + 23.0F, y, HEADER_HEIGHT, TEXT, FONT);
            String marker = collapsed ? "v" : "^";
            float markerWidth = SkijaUi.textWidth(marker, FONT);
            drawText(canvas, marker, x + panelWidth - 7.0F - markerWidth,
                    y, HEADER_HEIGHT, TEXT, FONT);

            if (bodyHeight <= 0.1F) {
                return;
            }

            float bodyY = y + HEADER_HEIGHT;
            float bodyX = bodyX();
            float bodyWidth = bodyWidth();
            SkijaUi.fill(canvas, bodyX - 1.0F, bodyY, bodyWidth + 2.0F,
                    bodyHeight + 1.0F, PANEL_EDGE);
            SkijaUi.fill(canvas, bodyX, bodyY, bodyWidth, bodyHeight, BODY);
            canvas.save();
            canvas.clipRect(Rect.makeXYWH(bodyX, bodyY, bodyWidth, bodyHeight));
            renderContent(canvas, bodyY - scroll, bodyY, bodyHeight);
            canvas.restore();
            renderScrollbar(canvas, bodyY, bodyHeight);
        }

        private boolean mouseClicked(float mouseX, float mouseY, int button, float screenHeight) {
            if (inside(mouseX, mouseY, x, y, panelWidth, HEADER_HEIGHT)) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                        || (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseX >= x + panelWidth - HEADER_HEIGHT)) {
                    collapsed = !collapsed;
                    clampScroll(screenHeight);
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    draggingPanel = this;
                    dragOffsetX = mouseX - x;
                    dragOffsetY = mouseY - y;
                }
                return true;
            }
            if (collapsed || !inside(mouseX, mouseY, bodyX(), y + HEADER_HEIGHT,
                    bodyWidth(), bodyHeight(screenHeight))) {
                return false;
            }
            return clickContent(mouseX, mouseY + scroll, button);
        }

        private boolean scrollAt(float mouseX, float mouseY, float amount, float screenHeight) {
            float bodyHeight = bodyHeight(screenHeight);
            if (collapsed || bodyHeight <= 0.0F
                    || !inside(mouseX, mouseY, bodyX(), y + HEADER_HEIGHT, bodyWidth(), bodyHeight)) {
                return false;
            }
            scroll -= amount * SCROLL_STEP;
            clampScroll(screenHeight);
            return true;
        }

        private float bodyHeight(float screenHeight) {
            return expandedBodyHeight(screenHeight) * expandProgress;
        }

        private float expandedBodyHeight(float screenHeight) {
            float available = Math.max(0.0F, screenHeight - y - HEADER_HEIGHT - MARGIN);
            return Math.min(contentHeight(), Math.min(MAX_BODY_HEIGHT, available));
        }

        private void clampPosition(float screenWidth, float screenHeight) {
            x = clamp(x, 0.0F, Math.max(0.0F, screenWidth - panelWidth));
            y = clamp(y, 0.0F, Math.max(0.0F, screenHeight - HEADER_HEIGHT));
            clampScroll(screenHeight);
        }

        protected final void clampScroll(float screenHeight) {
            scroll = clamp(scroll, 0.0F,
                    Math.max(0.0F, contentHeight() - expandedBodyHeight(screenHeight)));
        }

        protected void updateAnimations() {
        }

        private void renderScrollbar(Canvas canvas, float bodyY, float bodyHeight) {
            float contentHeight = contentHeight();
            if (contentHeight <= bodyHeight || bodyHeight <= 0.0F) {
                return;
            }
            float thumbHeight = Math.max(10.0F, bodyHeight * bodyHeight / contentHeight);
            float travel = bodyHeight - thumbHeight;
            float maxScroll = contentHeight - bodyHeight;
            float thumbY = bodyY + (maxScroll <= 0.0F ? 0.0F : scroll / maxScroll * travel);
            float scrollbarX = bodyX() + bodyWidth() - 2.0F;
            SkijaUi.fill(canvas, scrollbarX, bodyY, 2.0F, bodyHeight, argb(100, 0, 0, 0));
            SkijaUi.fill(canvas, scrollbarX, thumbY, 2.0F, thumbHeight, headerColor);
        }

        protected final void drawSettingSection(Canvas canvas, float rowY, float height) {
            SkijaUi.fill(canvas, x + SECTION_INSET, rowY + 1.0F,
                    panelWidth - SECTION_INSET * 2.0F,
                    Math.max(1.0F, height - 2.0F), SETTING_BACKGROUND);
        }

        private float bodyX() {
            return x + BODY_INSET;
        }

        private float bodyWidth() {
            return panelWidth - BODY_INSET * 2.0F;
        }

        protected abstract float contentHeight();

        protected abstract void renderContent(Canvas canvas, float top, float bodyY, float bodyHeight);

        protected abstract boolean clickContent(float mouseX, float contentMouseY, int button);
    }

    private final class EmptyPanel extends Panel {
        private EmptyPanel(String title, float x, float y) {
            super(title, title, "N", accent(), x, y);
        }

        @Override
        protected float contentHeight() {
            return 0.0F;
        }

        @Override
        protected void renderContent(Canvas canvas, float top, float bodyY, float bodyHeight) {
        }

        @Override
        protected boolean clickContent(float mouseX, float contentMouseY, int button) {
            return false;
        }
    }

    private final class ModulePanel extends Panel {
        private final List<Entry> entries = new ArrayList<>();
        private final Set<EpsilonHudModule> expandedHudComponents = new HashSet<>();
        private final Map<EpsilonHudModule, Float> hudExpandProgress = new HashMap<>();

        private ModulePanel(Category category, List<Module> modules, float x, float y) {
            super(categoryMemoryId(category), category.displayName(), categoryIcon(category),
                    categoryColor(category), x, y);
            for (Module module : modules) {
                entries.add(new Entry(module, false));
            }
        }

        @Override
        protected float contentHeight() {
            float height = 0.0F;
            for (Entry entry : entries) {
                height += MODULE_HEIGHT;
                height += entryDetailsHeight(entry) * entry.expandProgress;
            }
            return height;
        }

        @Override
        protected void updateAnimations() {
            for (Entry entry : entries) {
                entry.expandProgress = animateTowards(entry.expandProgress,
                        entry.expanded ? 1.0F : 0.0F, EXPAND_SPEED, animationDelta);
            }
            for (EpsilonHudModule component : HUD.INSTANCE.components()) {
                float current = hudExpandProgress.getOrDefault(component, 0.0F);
                float target = expandedHudComponents.contains(component) ? 1.0F : 0.0F;
                hudExpandProgress.put(component,
                        animateTowards(current, target, EXPAND_SPEED, animationDelta));
            }
        }

        @Override
        protected void renderContent(Canvas canvas, float top, float bodyY, float bodyHeight) {
            float rowY = top;
            for (Entry entry : entries) {
                renderModule(canvas, entry, rowY, bodyY, bodyHeight);
                rowY += MODULE_HEIGHT;
                float visibleDetails = entryDetailsHeight(entry) * entry.expandProgress;
                if (visibleDetails <= 0.1F) continue;
                canvas.save();
                canvas.clipRect(Rect.makeXYWH(x + BODY_INSET, rowY,
                        panelWidth - BODY_INSET * 2.0F, visibleDetails));
                renderEntryDetails(canvas, entry, rowY, bodyY, bodyHeight);
                canvas.restore();
                rowY += visibleDetails;
            }
        }

        private float renderEntryDetails(Canvas canvas, Entry entry, float rowY,
                                         float bodyY, float bodyHeight) {
            float startY = rowY;
            renderModuleKeybind(canvas, entry.module, rowY, bodyY, bodyHeight);
            rowY += SETTING_HEIGHT;
            if (entry.module == HUD.INSTANCE) {
                for (EpsilonHudModule component : HUD.INSTANCE.components()) {
                    renderHudComponent(canvas, component, rowY, bodyY, bodyHeight);
                    rowY += SETTING_HEIGHT;
                    float visibleSettings = componentSettingsHeight(component)
                            * hudExpandProgress.getOrDefault(component, 0.0F);
                    if (visibleSettings <= 0.1F) continue;
                    canvas.save();
                    canvas.clipRect(Rect.makeXYWH(x + BODY_INSET, rowY,
                            panelWidth - BODY_INSET * 2.0F, visibleSettings));
                    float settingY = rowY;
                    for (Setting<?> setting : component.settings()) {
                        if (isHudPosition(component, setting) || !setting.visible()) continue;
                        renderSetting(canvas, setting, settingY, bodyY, bodyHeight, true);
                        settingY += settingHeight(setting);
                    }
                    canvas.restore();
                    rowY += visibleSettings;
                }
            } else {
                for (Setting<?> setting : entry.module.settings()) {
                    if (!setting.visible()) continue;
                    renderSetting(canvas, setting, rowY, bodyY, bodyHeight);
                    rowY += settingHeight(setting);
                }
            }
            return rowY - startY;
        }

        private float entryDetailsHeight(Entry entry) {
            float height = SETTING_HEIGHT;
            if (entry.module == HUD.INSTANCE) {
                for (EpsilonHudModule component : HUD.INSTANCE.components()) {
                    height += SETTING_HEIGHT + componentSettingsHeight(component)
                            * hudExpandProgress.getOrDefault(component, 0.0F);
                }
                return height;
            }
            for (Setting<?> setting : entry.module.settings()) {
                if (setting.visible()) height += settingHeight(setting);
            }
            return height;
        }

        private float componentSettingsHeight(EpsilonHudModule component) {
            float height = 0.0F;
            for (Setting<?> setting : component.settings()) {
                if (isHudPosition(component, setting) || !setting.visible()) continue;
                height += settingHeight(setting);
            }
            return height;
        }

        private void renderModule(Canvas canvas, Entry entry, float rowY, float bodyY, float bodyHeight) {
            Module module = entry.module;
            int nameColor = module.isEnabled() ? headerColor : 0xFFE4E7E9;
            String compactName = module.displayName();
            drawText(canvas, fit(compactName, panelWidth - 22.0F, false),
                    x + 7.0F, rowY, MODULE_HEIGHT, nameColor, FONT);

            String right;
            if (capturingModule == module) {
                right = "...";
            } else {
                right = entry.expanded ? "^" : "v";
            }
            if (!right.isEmpty()) {
                float width = SkijaUi.textWidth(right, FONT);
                drawText(canvas, right, x + panelWidth - 6.0F - width,
                        rowY, MODULE_HEIGHT, TEXT, FONT);
            }
        }

        private void renderModuleKeybind(Canvas canvas, Module module, float rowY,
                                         float bodyY, float bodyHeight) {
            drawSettingSection(canvas, rowY, SETTING_HEIGHT);
            drawText(canvas, tr("keybind", "Keybind"), x + 7.0F, rowY,
                    SETTING_HEIGHT, TEXT_DIM, FONT_SMALL);
            String value = capturingModule == module ? "..." : KeyBindText.of(module.keyBind());
            if (value == null || value.isBlank()) value = tr("none", "NONE");
            drawRight(canvas, fit(value, 45.0F, false), rowY, SETTING_HEIGHT,
                    capturingModule == module ? headerColor : TEXT, FONT_SMALL);
        }

        private void renderHudComponent(Canvas canvas, EpsilonHudModule component, float rowY,
                                        float bodyY, float bodyHeight) {
            drawSettingSection(canvas, rowY, SETTING_HEIGHT);
            BooleanSetting toggle = HUD.INSTANCE.componentSetting(component);
            String label = component.displayName();
            drawText(canvas, fit(label, 65.0F, false), x + 7.0F, rowY,
                    SETTING_HEIGHT, toggle.get() ? TEXT : TEXT_DIM, FONT_SMALL);

            String marker = expandedHudComponents.contains(component) ? "-" : "+";
            drawText(canvas, marker, x + panelWidth - 31.0F, rowY,
                    SETTING_HEIGHT, TEXT_DIM, FONT_SMALL);
            SkijaUi.fill(canvas, x + panelWidth - 14.0F, rowY + 4.0F, 8.0F, 8.0F,
                    toggle.get() ? headerColor : TRACK);
        }

        private void renderSetting(Canvas canvas, Setting<?> setting, float rowY,
                                   float bodyY, float bodyHeight) {
            renderSetting(canvas, setting, rowY, bodyY, bodyHeight, false);
        }

        private void renderSetting(Canvas canvas, Setting<?> setting, float rowY,
                                   float bodyY, float bodyHeight, boolean nested) {
            float height = settingHeight(setting);
            drawSettingSection(canvas, rowY, height);

            if (setting instanceof ButtonSetting) {
                String label = fit(setting.displayName(), panelWidth - 10.0F, false);
                float width = SkijaUi.textWidth(label, FONT);
                drawText(canvas, label, x + (panelWidth - width) * 0.5F,
                        rowY, SETTING_HEIGHT, headerColor, FONT);
                return;
            }

            float labelWidth = setting instanceof ColorSetting
                    ? (nested ? 28.0F : 34.0F)
                    : setting instanceof StringSetting && !(setting instanceof FontSetting)
                    ? (nested ? 34.0F : 38.0F) : (nested ? 56.0F : 62.0F);
            drawText(canvas, fit(setting.displayName(), labelWidth, false),
                    x + (nested ? 10.0F : 7.0F), rowY, SETTING_HEIGHT, TEXT_DIM, FONT_SMALL);

            if (setting instanceof BooleanSetting booleanSetting) {
                float boxX = x + panelWidth - 14.0F;
                float boxY = rowY + 4.0F;
                SkijaUi.fill(canvas, boxX, boxY, 8.0F, 8.0F,
                        booleanSetting.get() ? headerColor : TRACK);
            } else if (setting instanceof FontSetting fontSetting) {
                drawRight(canvas, fit(fontSetting.displayValue(), 42.0F, false),
                        rowY, SETTING_HEIGHT, headerColor, FONT_SMALL);
            } else if (setting instanceof EnumSetting<?> enumSetting) {
                drawRight(canvas, fit(enumSetting.displayValue(), 42.0F, false),
                        rowY, SETTING_HEIGHT, headerColor, FONT_SMALL);
                if (MusicPresetPreview.matches(setting)) {
                    MusicPresetPreview.draw(canvas, x + SECTION_INSET, rowY + SETTING_HEIGHT,
                            Math.max(1.0F, panelWidth - SECTION_INSET * 2.0F),
                            height - SETTING_HEIGHT, 1.0F);
                }
            } else if (setting instanceof IntSetting intSetting) {
                renderNumberSlider(canvas, rowY, intSetting.fraction(),
                        Integer.toString(intSetting.get()));
            } else if (setting instanceof DoubleSetting doubleSetting) {
                renderNumberSlider(canvas, rowY, doubleSetting.fraction(),
                        trimZeros(String.format(Locale.ROOT, "%.3f", doubleSetting.get())));
            } else if (setting instanceof ColorSetting colorSetting) {
                renderColor(canvas, colorSetting, rowY);
            } else if (setting instanceof KeybindSetting keybindSetting) {
                String value = capturingKeybind == keybindSetting ? "..." : KeyBindText.of(keybindSetting.get());
                drawRight(canvas, value.isEmpty() ? tr("none", "NONE") : fit(value, 43.0F, false),
                        rowY, SETTING_HEIGHT, headerColor, FONT_SMALL);
            } else if (setting instanceof StringSetting stringSetting) {
                boolean focused = textTarget == TextTarget.STRING && editingString == stringSetting;
                String value = textTarget == TextTarget.STRING && editingString == stringSetting
                        ? textWithCursor() : stringSetting.get();
                float fieldX = x + panelWidth - 60.0F;
                float fieldY = rowY + 2.0F;
                float fieldWidth = 54.0F;
                float fieldHeight = 12.0F;
                SkijaUi.fill(canvas, fieldX, fieldY, fieldWidth, fieldHeight,
                        focused ? headerColor : TRACK);
                SkijaUi.fill(canvas, fieldX + 1.0F, fieldY + 1.0F,
                        fieldWidth - 2.0F, fieldHeight - 2.0F, BODY);
                String display = value.isEmpty() ? tr("empty", "Empty") : value;
                drawText(canvas, fit(display, fieldWidth - 8.0F, false), fieldX + 4.0F,
                        fieldY, fieldHeight, value.isEmpty() ? TEXT_OFF : TEXT, FONT_SMALL);
            }
        }

        private void renderNumberSlider(Canvas canvas, float rowY, float fraction, String value) {
            float trackX = numberTrackX();
            float trackWidth = numberTrackWidth();
            float trackY = rowY + 22.0F;
            renderMovingValueSlider(canvas, trackX, trackY, trackWidth,
                    fraction, value, headerColor, 14.0F);
        }

        private void renderColor(Canvas canvas, ColorSetting setting, float rowY) {
            Color color = setting.get();
            float fieldX = x + panelWidth - 72.0F;
            float fieldY = rowY + 2.0F;
            float fieldWidth = 52.0F;
            float fieldHeight = 12.0F;
            boolean focused = textTarget == TextTarget.COLOR && editingColor == setting;
            String value = focused ? textWithCursor() : setting.hex();
            SkijaUi.fill(canvas, fieldX, fieldY, fieldWidth, fieldHeight,
                    focused ? headerColor : TRACK);
            SkijaUi.fill(canvas, fieldX + 1.0F, fieldY + 1.0F,
                    fieldWidth - 2.0F, fieldHeight - 2.0F, BODY);
            drawText(canvas, fit(value, fieldWidth - 6.0F, false), fieldX + 3.0F,
                    fieldY, fieldHeight, focused ? TEXT : TEXT_DIM, FONT_SMALL);
            SkijaUi.fill(canvas, x + panelWidth - 16.0F, rowY + 4.0F, 10.0F, 8.0F, setting.argb());
            int[] values = {color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()};
            int[] colors = {
                    argb(255, 232, 84, 84),
                    argb(255, 84, 218, 117),
                    argb(255, 84, 151, 235),
                    argb(255, 226, 228, 233)
            };
            String[] labels = {"R", "G", "B", "A"};
            int channels = setting.allowAlpha() ? 4 : 3;
            for (int i = 0; i < channels; i++) {
                float channelY = rowY + SETTING_HEIGHT + i * COLOR_CHANNEL_HEIGHT;
                drawBoldText(canvas, labels[i], x + 6.0F, channelY,
                        COLOR_CHANNEL_HEIGHT, colors[i], FONT_SMALL);
                float trackX = colorTrackX();
                float trackWidth = colorTrackWidth();
                String channelValue = Integer.toString(values[i]);
                renderMovingValueSlider(canvas, trackX, channelY + 5.0F,
                        trackWidth, values[i] / 255.0F, channelValue, colors[i],
                        COLOR_CHANNEL_HEIGHT);
            }
        }

        private void renderMovingValueSlider(Canvas canvas, float trackX, float trackY,
                                             float trackWidth, float fraction, String value,
                                             int color, float textHeight) {
            float clamped = clamp(fraction, 0.0F, 1.0F);
            float trackEnd = trackX + trackWidth;
            float handleX = trackX + trackWidth * clamped;
            float valueWidth = SkijaUi.textWidth(value, FONT_SMALL);
            float valueX = handleX + 5.0F;
            if (valueX + valueWidth > trackEnd) {
                valueX = handleX - 5.0F - valueWidth;
            }
            valueX = clamp(valueX, trackX, Math.max(trackX, trackEnd - valueWidth));
            float gapStart = valueX - 2.5F;
            float gapEnd = valueX + valueWidth + 2.5F;

            drawSliderLayer(canvas, trackX, trackEnd, gapStart, gapEnd,
                    trackY, 2.0F, TRACK);
            drawSliderLayer(canvas, trackX, handleX, gapStart, gapEnd,
                    trackY, 2.0F, color);
            SkijaUi.fill(canvas, handleX - 1.5F, trackY - 3.0F,
                    3.0F, 8.0F, color);
            drawText(canvas, value, valueX, trackY - 7.0F,
                    textHeight, TEXT, FONT_SMALL);
        }

        private void drawRight(Canvas canvas, String value, float rowY, float height, int color, float size) {
            float width = SkijaUi.textWidth(value, size);
            drawText(canvas, value, x + panelWidth - 6.0F - width, rowY, height, color, size);
        }

        private float numberTrackX() {
            return x + 7.0F;
        }

        private float numberTrackWidth() {
            return Math.max(12.0F, panelWidth - 14.0F);
        }

        private float colorTrackX() {
            return x + 16.0F;
        }

        private float colorTrackWidth() {
            return Math.max(12.0F, panelWidth - 23.0F);
        }

        @Override
        protected boolean clickContent(float mouseX, float contentMouseY, int button) {
            float rowY = y + HEADER_HEIGHT;
            for (Entry entry : entries) {
                if (inside(mouseX, contentMouseY, x, rowY, panelWidth, MODULE_HEIGHT)) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                        capturingModule = entry.module;
                    } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                        entry.expanded = !entry.expanded;
                    } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        entry.module.interactFromClickGui();
                    }
                    clampScroll(logicalHeight());
                    return true;
                }
                rowY += MODULE_HEIGHT;

                float visibleDetails = entryDetailsHeight(entry) * entry.expandProgress;
                if (visibleDetails <= 0.1F) continue;
                if (entry.expandProgress < 0.995F) {
                    rowY += visibleDetails;
                    continue;
                }

                if (inside(mouseX, contentMouseY, x, rowY, panelWidth, SETTING_HEIGHT)) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        capturingModule = entry.module;
                    } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                        entry.module.setKeyBind(-1);
                        capturingModule = null;
                    }
                    return true;
                }
                rowY += SETTING_HEIGHT;

                if (entry.module == HUD.INSTANCE) {
                    for (EpsilonHudModule component : HUD.INSTANCE.components()) {
                        if (inside(mouseX, contentMouseY, x, rowY, panelWidth, SETTING_HEIGHT)) {
                            BooleanSetting toggle = HUD.INSTANCE.componentSetting(component);
                            boolean markerClicked = mouseX >= x + panelWidth - 38.0F
                                    && mouseX < x + panelWidth - 17.0F;
                            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                                    || button == GLFW.GLFW_MOUSE_BUTTON_LEFT && markerClicked) {
                                if (!expandedHudComponents.add(component)) {
                                    expandedHudComponents.remove(component);
                                }
                            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                                NotificationManager.INSTANCE.withoutModuleFeedback(toggle::toggle);
                            }
                            clampScroll(logicalHeight());
                            return true;
                        }
                        rowY += SETTING_HEIGHT;
                        float componentProgress = hudExpandProgress.getOrDefault(component, 0.0F);
                        float visibleSettings = componentSettingsHeight(component) * componentProgress;
                        if (visibleSettings <= 0.1F) continue;
                        if (componentProgress < 0.995F) {
                            rowY += visibleSettings;
                            continue;
                        }
                        for (Setting<?> setting : component.settings()) {
                            if (isHudPosition(component, setting) || !setting.visible()) continue;
                            float height = settingHeight(setting);
                            if (inside(mouseX, contentMouseY, x, rowY, panelWidth, height)) {
                                clickSetting(setting, rowY, mouseX, contentMouseY, button);
                                clampScroll(logicalHeight());
                                return true;
                            }
                            rowY += height;
                        }
                    }
                    continue;
                }

                for (Setting<?> setting : entry.module.settings()) {
                    if (!setting.visible()) continue;
                    float height = settingHeight(setting);
                    if (inside(mouseX, contentMouseY, x, rowY, panelWidth, height)) {
                        clickSetting(setting, rowY, mouseX, contentMouseY, button);
                        clampScroll(logicalHeight());
                        return true;
                    }
                    rowY += height;
                }
            }
            return false;
        }

        private void clickSetting(Setting<?> setting, float rowY, float mouseX,
                                  float mouseY, int button) {
            if (setting instanceof BooleanSetting booleanSetting) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    NotificationManager.INSTANCE.withoutModuleFeedback(booleanSetting::toggle);
                }
            } else if (setting instanceof FontSetting fontSetting) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    fontSetting.cycle(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1);
                }
            } else if (setting instanceof EnumSetting<?> enumSetting) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    enumSetting.cycle(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1);
                }
            } else if (setting instanceof IntSetting intSetting) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    intSetting.set(intSetting.get() - intSetting.step());
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    beginNumberDrag(setting, numberTrackX(), numberTrackWidth(), mouseX);
                }
            } else if (setting instanceof DoubleSetting doubleSetting) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    doubleSetting.set(doubleSetting.get() - doubleSetting.step());
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    beginNumberDrag(setting, numberTrackX(), numberTrackWidth(), mouseX);
                }
            } else if (setting instanceof ColorSetting colorSetting) {
                float fieldX = x + panelWidth - 72.0F;
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                        && inside(mouseX, mouseY, fieldX, rowY + 2.0F, 52.0F, 12.0F)) {
                    focusColor(colorSetting);
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    colorSetting.reset();
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseY >= rowY + SETTING_HEIGHT) {
                    int channel = (int) ((mouseY - rowY - SETTING_HEIGHT) / COLOR_CHANNEL_HEIGHT);
                    int channels = colorSetting.allowAlpha() ? 4 : 3;
                    if (channel >= 0 && channel < channels) {
                        draggingColor = colorSetting;
                        draggingColorChannel = channel;
                        draggingTrackX = colorTrackX();
                        draggingTrackWidth = colorTrackWidth();
                        updateColor(mouseX);
                    }
                }
            } else if (setting instanceof KeybindSetting keybindSetting) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    keybindSetting.set(KeybindSetting.NONE);
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    capturingKeybind = keybindSetting;
                }
            } else if (setting instanceof StringSetting stringSetting) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    stringSetting.reset();
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    focusString(stringSetting);
                }
            } else if (setting instanceof ButtonSetting buttonSetting
                    && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                buttonSetting.press();
            }
        }

        private boolean isHudPosition(EpsilonHudModule component, Setting<?> setting) {
            return setting == component.xPosition || setting == component.yPosition;
        }
    }

    private final class ConfigPanel extends Panel {
        private static final float TOP_HEIGHT = 50.0F;
        private String name = "";

        private ConfigPanel(float x, float y) {
            super("Configs", tr("configs", "Configs"), "N", 0xFF48B9DE, x, y);
        }

        @Override
        protected float contentHeight() {
            return TOP_HEIGHT + configProfiles().size() * MODULE_HEIGHT;
        }

        @Override
        protected void renderContent(Canvas canvas, float top, float bodyY, float bodyHeight) {
            drawText(canvas, tr("current_colon", "Current:"), x + 5.0F, top, 16.0F, TEXT_DIM, FONT);
            drawText(canvas, fit(currentConfigName(), 60.0F, false),
                    x + 52.0F, top, 16.0F, CONFIG_GREEN, FONT);

            float inputY = top + 16.0F;
            SkijaUi.fill(canvas, x + 5.0F, inputY + 1.0F,
                    panelWidth - 10.0F, 14.0F, argb(235, 42, 44, 50));
            String input = textTarget == TextTarget.CONFIG ? textWithCursor() : name;
            if (input.isEmpty()) {
                input = tr("enter_name", "Enter name...");
            }
            drawText(canvas, fit(input, panelWidth - 16.0F, false),
                    x + 7.0F, inputY, 16.0F, name.isEmpty() ? TEXT_OFF : TEXT, FONT);

            float buttonY = top + 32.0F;
            float buttonWidth = (panelWidth - 17.0F) * 0.5F;
            SkijaUi.fill(canvas, x + 5.0F, buttonY + 1.0F, buttonWidth, 14.0F,
                    argb(240, 48, 116, 60));
            SkijaUi.fill(canvas, x + 12.0F + buttonWidth, buttonY + 1.0F,
                    buttonWidth, 14.0F, argb(240, 55, 90, 139));
            drawCentered(canvas, tr("save", "Save"), x + 5.0F, buttonY, buttonWidth, 16.0F, TEXT);
            drawCentered(canvas, tr("load", "Load"), x + 12.0F + buttonWidth,
                    buttonY, buttonWidth, 16.0F, TEXT);

            float rowY = top + TOP_HEIGHT;
            for (String profile : configProfiles()) {
                drawSettingSection(canvas, rowY, MODULE_HEIGHT);
                drawText(canvas, fit(profile, panelWidth - 24.0F, false),
                        x + 7.0F, rowY, MODULE_HEIGHT, TEXT, FONT);
                float deleteX = x + panelWidth - 21.0F;
                SkijaUi.fill(canvas, deleteX, rowY + 2.0F, 15.0F,
                        MODULE_HEIGHT - 4.0F, DELETE_BACKGROUND);
                drawCentered(canvas, "x", deleteX, rowY, 15.0F, MODULE_HEIGHT, TEXT);
                rowY += MODULE_HEIGHT;
            }
        }

        @Override
        protected boolean clickContent(float mouseX, float contentMouseY, int button) {
            float top = y + HEADER_HEIGHT;
            if (inside(mouseX, contentMouseY, x + 5.0F, top + 16.0F, panelWidth - 10.0F, 16.0F)
                    && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                focusConfigName(name);
                return true;
            }

            float buttonWidth = (panelWidth - 17.0F) * 0.5F;
            if (inside(mouseX, contentMouseY, x + 5.0F, top + 32.0F, buttonWidth, 16.0F)
                    && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                configProvider.save(configTargetName());
                return true;
            }
            if (inside(mouseX, contentMouseY, x + 12.0F + buttonWidth, top + 32.0F, buttonWidth, 16.0F)
                    && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                configProvider.load(configTargetName());
                return true;
            }

            float rowY = top + TOP_HEIGHT;
            for (String profile : configProfiles()) {
                if (inside(mouseX, contentMouseY, x, rowY, panelWidth, MODULE_HEIGHT)) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || mouseX >= x + panelWidth - 22.0F) {
                        configProvider.delete(profile);
                    } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        name = profile;
                    }
                    return true;
                }
                rowY += MODULE_HEIGHT;
            }
            return false;
        }

        private String configTargetName() {
            return name.isBlank() ? currentConfigName() : name.trim();
        }
    }

    private void beginNumberDrag(Setting<?> setting, float trackX, float trackWidth, float mouseX) {
        draggingNumber = setting;
        draggingTrackX = trackX;
        draggingTrackWidth = trackWidth;
        updateNumber(mouseX);
    }

    private void updateNumber(float mouseX) {
        float fraction = clamp((mouseX - draggingTrackX) / draggingTrackWidth, 0.0F, 1.0F);
        if (draggingNumber instanceof IntSetting setting) {
            double steps = Math.round(((setting.max() - setting.min()) * fraction) / setting.step());
            setting.set((int) Math.round(setting.min() + steps * setting.step()));
        } else if (draggingNumber instanceof DoubleSetting setting) {
            double raw = setting.min() + (setting.max() - setting.min()) * fraction;
            double steps = Math.round((raw - setting.min()) / setting.step());
            setting.set(setting.min() + steps * setting.step());
        }
    }

    private void updateColor(float mouseX) {
        if (draggingColor == null || draggingColorChannel < 0) {
            return;
        }
        int value = Math.round(clamp((mouseX - draggingTrackX) / draggingTrackWidth, 0.0F, 1.0F) * 255.0F);
        Color current = draggingColor.get();
        int red = draggingColorChannel == 0 ? value : current.getRed();
        int green = draggingColorChannel == 1 ? value : current.getGreen();
        int blue = draggingColorChannel == 2 ? value : current.getBlue();
        int alpha = draggingColorChannel == 3 ? value : current.getAlpha();
        draggingColor.set(new Color(red, green, blue, alpha));
    }

    private void focusString(StringSetting setting) {
        textTarget = TextTarget.STRING;
        editingString = setting;
        editingColor = null;
        colorBeforeEdit = null;
        editText = setting.get();
        editCursor = editText.length();
        selectAll = false;
    }

    private void focusConfigName(String current) {
        textTarget = TextTarget.CONFIG;
        editingString = null;
        editingColor = null;
        colorBeforeEdit = null;
        editText = current;
        editCursor = editText.length();
        selectAll = false;
    }

    private void focusColor(ColorSetting setting) {
        textTarget = TextTarget.COLOR;
        editingString = null;
        editingColor = setting;
        colorBeforeEdit = setting.get();
        editText = setting.hex();
        editCursor = editText.length();
        selectAll = true;
    }

    private boolean handleTextKey(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_ENTER
                || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            finishTextEditing();
            return true;
        }
        if (event.isSelectAll()) {
            selectAll = true;
            editCursor = editText.length();
            return true;
        }
        if (event.isCopy()) {
            minecraft.keyboardHandler.setClipboard(editText);
            return true;
        }
        if (event.isCut()) {
            minecraft.keyboardHandler.setClipboard(editText);
            editText = "";
            editCursor = 0;
            selectAll = false;
            applyEditedText();
            return true;
        }
        if (event.isPaste()) {
            insertText(minecraft.keyboardHandler.getClipboard());
            return true;
        }
        switch (event.key()) {
            case GLFW.GLFW_KEY_LEFT -> editCursor = Math.max(0, editCursor - 1);
            case GLFW.GLFW_KEY_RIGHT -> editCursor = Math.min(editText.length(), editCursor + 1);
            case GLFW.GLFW_KEY_HOME -> editCursor = 0;
            case GLFW.GLFW_KEY_END -> editCursor = editText.length();
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (selectAll) {
                    editText = "";
                    editCursor = 0;
                } else if (editCursor > 0) {
                    editText = editText.substring(0, editCursor - 1) + editText.substring(editCursor);
                    editCursor--;
                }
                applyEditedText();
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (selectAll) {
                    editText = "";
                    editCursor = 0;
                } else if (editCursor < editText.length()) {
                    editText = editText.substring(0, editCursor) + editText.substring(editCursor + 1);
                }
                applyEditedText();
            }
            default -> {
                return false;
            }
        }
        selectAll = false;
        return true;
    }

    private void insertText(String inserted) {
        if (inserted == null || inserted.isEmpty()) {
            return;
        }
        StringBuilder clean = new StringBuilder();
        inserted.codePoints()
                .filter(codepoint -> codepoint >= 32 && codepoint != 127)
                .forEach(clean::appendCodePoint);
        if (selectAll) {
            editText = "";
            editCursor = 0;
        }
        int maxLength = textTarget == TextTarget.COLOR ? 9 : 128;
        int remaining = maxLength - editText.length();
        if (remaining <= 0) {
            selectAll = false;
            return;
        }
        String value = clean.length() > remaining ? clean.substring(0, remaining) : clean.toString();
        editText = editText.substring(0, editCursor) + value + editText.substring(editCursor);
        editCursor += value.length();
        selectAll = false;
        applyEditedText();
    }

    private void applyEditedText() {
        if (textTarget == TextTarget.STRING && editingString != null) {
            editingString.set(editText);
        } else if (textTarget == TextTarget.CONFIG) {
            for (Panel panel : panels) {
                if (panel instanceof ConfigPanel configPanel) {
                    configPanel.name = editText;
                    break;
                }
            }
        } else if (textTarget == TextTarget.COLOR && editingColor != null) {
            Color parsed = editingColor.parseHex(editText);
            if (parsed != null) editingColor.set(parsed);
        }
    }

    private String textWithCursor() {
        int cursor = Math.max(0, Math.min(editCursor, editText.length()));
        return editText.substring(0, cursor) + "|" + editText.substring(cursor);
    }

    private void finishTextEditing() {
        if (textTarget == TextTarget.NONE) {
            return;
        }
        if (textTarget == TextTarget.COLOR && editingColor != null) {
            Color parsed = editingColor.parseHex(editText);
            editingColor.set(parsed != null ? parsed : colorBeforeEdit);
        } else {
            applyEditedText();
        }
        textTarget = TextTarget.NONE;
        editingString = null;
        editingColor = null;
        colorBeforeEdit = null;
        editText = "";
        editCursor = 0;
        selectAll = false;
    }

    private void clampPanels() {
        float logicalWidth = logicalWidth();
        float logicalHeight = logicalHeight();
        for (Panel panel : panels) {
            panel.clampPosition(logicalWidth, logicalHeight);
        }
    }

    private static void loadPanelMemoryFromDisk() {
        if (panelMemoryDiskLoaded) return;
        panelMemoryDiskLoaded = true;
        if (panelMemorySaved) return;
        try {
            Path file = ConfigManager.INSTANCE.configDirectory().resolve(STATE_FILE_NAME);
            if (!Files.isRegularFile(file)) return;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                DropMemoryFile state = STATE_GSON.fromJson(reader, DropMemoryFile.class);
                if (state == null || state.panels == null || state.panels.isEmpty()) return;
                PANEL_MEMORY.clear();
                for (Map.Entry<String, PanelMemory> entry : state.panels.entrySet()) {
                    PanelMemory memory = entry.getValue();
                    if (entry.getKey() == null || memory == null) continue;
                    memory.normalize();
                    PANEL_MEMORY.put(entry.getKey(), memory);
                }
                PANEL_ORDER_MEMORY.clear();
                if (state.order != null) PANEL_ORDER_MEMORY.addAll(state.order);
                panelMemorySaved = !PANEL_MEMORY.isEmpty();
            }
        } catch (Exception error) {
            Setsuna.LOGGER.warn("Failed to load Drop ClickGUI state; using defaults", error);
        }
    }

    private static void writePanelMemoryToDisk() {
        if (!panelMemorySaved) return;
        try {
            Path file = ConfigManager.INSTANCE.configDirectory().resolve(STATE_FILE_NAME);
            DropMemoryFile state = new DropMemoryFile();
            state.panels.putAll(PANEL_MEMORY);
            state.order.addAll(PANEL_ORDER_MEMORY);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                STATE_GSON.toJson(state, writer);
            }
        } catch (Exception error) {
            Setsuna.LOGGER.warn("Failed to save Drop ClickGUI state", error);
        }
    }

    private void restorePanelMemory() {
        if (!panelMemorySaved) return;

        Map<String, Panel> byTitle = new HashMap<>();
        for (Panel panel : panels) {
            byTitle.put(panel.memoryId, panel);
            PanelMemory memory = PANEL_MEMORY.get(panel.memoryId);
            if (memory == null) continue;
            panel.x = memory.x;
            panel.y = memory.y;
            panel.scroll = memory.scroll;
            panel.collapsed = memory.collapsed;
            panel.expandProgress = memory.collapsed ? 0.0F : 1.0F;

            if (panel instanceof ModulePanel modulePanel) {
                for (Entry entry : modulePanel.entries) {
                    entry.expanded = memory.expandedModules.contains(entry.module.id());
                    entry.expandProgress = entry.expanded ? 1.0F : 0.0F;
                }
                modulePanel.expandedHudComponents.clear();
                for (EpsilonHudModule component : HUD.INSTANCE.components()) {
                    if (memory.expandedHudComponents.contains(component.id())) {
                        modulePanel.expandedHudComponents.add(component);
                        modulePanel.hudExpandProgress.put(component, 1.0F);
                    }
                }
            } else if (panel instanceof ConfigPanel configPanel) {
                configPanel.name = memory.configName;
            }
        }

        List<Panel> restoredOrder = new ArrayList<>();
        for (String title : PANEL_ORDER_MEMORY) {
            Panel panel = byTitle.remove(title);
            if (panel != null) restoredOrder.add(panel);
        }
        restoredOrder.addAll(byTitle.values());
        panels.clear();
        panels.addAll(restoredOrder);
    }

    private void savePanelMemory() {
        if (panels.isEmpty()) return;
        PANEL_MEMORY.clear();
        PANEL_ORDER_MEMORY.clear();
        for (Panel panel : panels) {
            PanelMemory memory = new PanelMemory();
            memory.x = panel.x;
            memory.y = panel.y;
            memory.scroll = panel.scroll;
            memory.collapsed = panel.collapsed;
            if (panel instanceof ModulePanel modulePanel) {
                for (Entry entry : modulePanel.entries) {
                    if (entry.expanded) memory.expandedModules.add(entry.module.id());
                }
                for (EpsilonHudModule component : modulePanel.expandedHudComponents) {
                    memory.expandedHudComponents.add(component.id());
                }
            } else if (panel instanceof ConfigPanel configPanel) {
                memory.configName = configPanel.name;
            }
            PANEL_MEMORY.put(panel.memoryId, memory);
            PANEL_ORDER_MEMORY.add(panel.memoryId);
        }
        panelMemorySaved = true;
        writePanelMemoryToDisk();
    }

    private float configuredScale() {
        float requested = clamp(ClickGui.INSTANCE.guiScale.get() / 100.0F, 0.65F, 1.25F);
        if (width <= 0) return requested;
        float fit = width / MIN_SIX_COLUMN_WIDTH;
        return clamp(Math.min(requested, fit), 0.5F, 1.25F);
    }

    private double logical(double coordinate) {
        return coordinate / activeScale;
    }

    private float logicalWidth() {
        return Math.max(1.0F, width / activeScale);
    }

    private float logicalHeight() {
        return Math.max(1.0F, height / activeScale);
    }

    private static float settingHeight(Setting<?> setting) {
        if (setting instanceof IntSetting || setting instanceof DoubleSetting) {
            return NUMBER_HEIGHT;
        }
        if (setting instanceof ColorSetting colorSetting) {
            return SETTING_HEIGHT + (colorSetting.allowAlpha() ? 4 : 3) * COLOR_CHANNEL_HEIGHT;
        }
        if (MusicPresetPreview.matches(setting)) {
            return SETTING_HEIGHT + MusicPresetPreview.EXTRA_HEIGHT;
        }
        return SETTING_HEIGHT;
    }

    private static String categoryMemoryId(Category category) {
        return switch (category) {
            case COMBAT -> "Combat";
            case MISC -> "Misc";
            case HUD -> "HUD";
            case RENDER -> "Render";
            case MOVEMENT -> "Movement";
            case PLAYER -> "Player";
            case CLIENT -> "Client";
        };
    }

    private static String categoryIcon(Category category) {
        return CategoryGlyphs.forCategory(category);
    }

    private static int categoryColor(Category category) {
        return switch (category) {
            case COMBAT -> 0xFF45B8EA;
            case MISC -> 0xFF7085F3;
            case HUD -> 0xFF9674EE;
            case RENDER -> 0xFF4FD5C5;
            case MOVEMENT -> 0xFF43C4E2;
            case PLAYER -> 0xFF6978F2;
            case CLIENT -> 0xFF4AD9A6;
        };
    }

    private static String currentConfigName() {
        String current = configProvider.currentName();
        return current == null || current.isBlank() ? "default" : current;
    }

    private static List<String> configProfiles() {
        List<String> profiles = configProvider.profiles();
        return profiles == null ? List.of() : List.copyOf(profiles);
    }

    private static int accent() {
        Color configured = ClickGui.INSTANCE.accent.get();
        return argb(255, configured.getRed(), configured.getGreen(), configured.getBlue());
    }

    private static String fit(String text, float maxWidth, boolean bold) {
        String value = Objects.requireNonNullElse(text, "");
        if (maxWidth <= 0.0F) {
            return "";
        }
        if (textWidth(value, bold) <= maxWidth) {
            return value;
        }
        String suffix = "...";
        while (!value.isEmpty() && textWidth(value + suffix, bold) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isEmpty() ? "" : value + suffix;
    }

    private static float textWidth(String text, boolean bold) {
        return bold ? SkijaUi.boldTextWidth(text, FONT) : SkijaUi.textWidth(text, FONT);
    }

    private static void drawText(Canvas canvas, String text, float x, float y,
                                 float height, int color, float size) {
        SkijaUi.text(canvas, text, x + 1.0F, y + 1.0F, height, shadowColor(color), size);
        SkijaUi.text(canvas, text, x, y, height, color, size);
    }

    private static void drawBoldText(Canvas canvas, String text, float x, float y,
                                     float height, int color, float size) {
        SkijaUi.boldText(canvas, text, x + 1.0F, y + 1.0F, height, shadowColor(color), size);
        SkijaUi.boldText(canvas, text, x, y, height, color, size);
    }

    private static int shadowColor(int color) {
        return (color & 0xFF000000) | ((color & 0x00FCFCFC) >> 2);
    }

    private static void drawCentered(Canvas canvas, String text, float x, float y,
                                     float width, float height, int color) {
        float textWidth = SkijaUi.textWidth(text, FONT);
        drawText(canvas, text, x + (width - textWidth) * 0.5F, y, height, color, FONT);
    }

    private static void drawGradientRect(Canvas canvas, float x, float y,
                                         float width, float height, int start, int end) {
        if (width <= 0.0F || height <= 0.0F) return;
        try (Shader shader = Shader.makeLinearGradient(x, y, x + width, y,
                new int[]{start, end})) {
            GRADIENT_PAINT.setShader(shader).setColor(0xFFFFFFFF).setAlpha(255);
            canvas.drawRect(Rect.makeXYWH(x, y, width, height), GRADIENT_PAINT);
        } finally {
            GRADIENT_PAINT.setShader(null).setAlpha(255);
        }
    }

    private static void drawSliderLayer(Canvas canvas, float start, float end,
                                        float gapStart, float gapEnd,
                                        float y, float height, int color) {
        if (end <= start) return;
        if (gapEnd <= start || gapStart >= end) {
            SkijaUi.fill(canvas, start, y, end - start, height, color);
            return;
        }

        float leftEnd = Math.min(end, gapStart);
        if (leftEnd > start) {
            float fadeStart = Math.max(start, leftEnd - SLIDER_FADE_WIDTH);
            if (fadeStart > start) {
                SkijaUi.fill(canvas, start, y, fadeStart - start, height, color);
            }
            drawGradientRect(canvas, fadeStart, y, leftEnd - fadeStart, height,
                    color, withAlpha(color, 0));
        }

        float rightStart = Math.max(start, gapEnd);
        if (end > rightStart) {
            float fadeEnd = Math.min(end, rightStart + SLIDER_FADE_WIDTH);
            drawGradientRect(canvas, rightStart, y, fadeEnd - rightStart, height,
                    withAlpha(color, 0), color);
            if (end > fadeEnd) {
                SkijaUi.fill(canvas, fadeEnd, y, end - fadeEnd, height, color);
            }
        }
    }

    private static String trimZeros(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && value.charAt(end - 1) == '.') {
            end--;
        }
        return value.substring(0, end);
    }

    private static String tr(String suffix, String fallback) {
        return TranslationKey.of(Setsuna.MOD_ID + ".gui." + suffix, fallback).get();
    }

    private static boolean inside(double mouseX, double mouseY,
                                  float x, float y, float width, float height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float animateTowards(float current, float target, float speed, float delta) {
        if (Math.abs(target - current) < 0.002F) return target;
        float amount = 1.0F - (float) Math.exp(-speed * Math.max(0.0F, delta));
        return current + (target - current) * amount;
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static int mixColor(int first, int second, float amount) {
        float t = clamp(amount, 0.0F, 1.0F);
        int a = Math.round(((first >>> 24) & 255)
                + (((second >>> 24) & 255) - ((first >>> 24) & 255)) * t);
        int r = Math.round(((first >>> 16) & 255)
                + (((second >>> 16) & 255) - ((first >>> 16) & 255)) * t);
        int g = Math.round(((first >>> 8) & 255)
                + (((second >>> 8) & 255) - ((first >>> 8) & 255)) * t);
        int b = Math.round((first & 255) + ((second & 255) - (first & 255)) * t);
        return argb(a, r, g, b);
    }

    private static final class Entry {
        private final Module module;
        private boolean expanded;
        private float expandProgress;

        private Entry(Module module, boolean expanded) {
            this.module = module;
            this.expanded = expanded;
        }
    }

    private static final class PanelMemory {
        private float x;
        private float y;
        private float scroll;
        private boolean collapsed;
        private Set<String> expandedModules = new HashSet<>();
        private Set<String> expandedHudComponents = new HashSet<>();
        private String configName = "";

        private void normalize() {
            if (!Float.isFinite(x)) x = MARGIN;
            if (!Float.isFinite(y)) y = MARGIN;
            if (!Float.isFinite(scroll) || scroll < 0.0F) scroll = 0.0F;
            if (expandedModules == null) expandedModules = new HashSet<>();
            if (expandedHudComponents == null) expandedHudComponents = new HashSet<>();
            if (configName == null) configName = "";
        }
    }

    private static final class DropMemoryFile {
        private Map<String, PanelMemory> panels = new HashMap<>();
        private List<String> order = new ArrayList<>();
    }

    private enum TextTarget {
        NONE,
        STRING,
        CONFIG,
        COLOR
    }
}
