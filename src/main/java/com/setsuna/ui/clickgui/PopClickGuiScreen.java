package com.setsuna.ui.clickgui;

import com.setsuna.config.ConfigManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.ModuleManager;
import com.setsuna.module.modules.ClickGui;
import com.setsuna.notification.NotificationManager;
import com.setsuna.module.modules.render.ESP;
import com.setsuna.module.modules.render.KillEffect;
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
import com.setsuna.ui.hud.TargetHud;
import com.setsuna.ui.hud.WatermarkHUD;
import com.setsuna.util.KeyBindText;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Rect;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Radial bubble ClickGUI whose selected category morphs into two inspector panels. */
public final class PopClickGuiScreen extends Screen implements SkijaScreen {

    private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath(
            "setsuna", "textures/hud/watermark_logo.png");

    private static final Category[] CATEGORIES = {
            Category.COMBAT, Category.MISC, Category.RENDER,
            Category.MOVEMENT, Category.PLAYER, Category.CLIENT
    };

    private static final float BUBBLE_RADIUS = 27.0F;
    private static final float HEADER_HEIGHT = 44.0F;
    private static final float MODULE_ROW_HEIGHT = 25.0F;
    private static final float SETTING_ROW_HEIGHT = 31.0F;
    private static final float NUMBER_ROW_HEIGHT = 42.0F;
    private static final float COLOR_CHANNEL_HEIGHT = 18.0F;
    private static final float PANEL_GAP = 10.0F;
    private static final float SCROLL_STEP = 24.0F;

    private static final int BACKDROP = argb(70, 2, 8, 12);
    private static final int PANEL = argb(224, 17, 23, 28);
    private static final int PANEL_INNER = argb(205, 25, 32, 38);
    private static final int PANEL_EDGE = argb(155, 102, 210, 238);
    private static final int ROW = argb(92, 255, 255, 255);
    private static final int ROW_HOVER = argb(24, 122, 214, 238);
    private static final int TEXT = 0xFFF3F7F8;
    private static final int TEXT_DIM = 0xFFA3AFB4;
    private static final int TEXT_FAINT = 0xFF68767C;
    private static final int TRACK = 0xFF3A464D;
    private static final int LIGHT_PANEL = argb(240, 248, 250, 252);
    private static final int LIGHT_PANEL_INNER = argb(224, 235, 240, 243);
    private static final int LIGHT_ROW_HOVER = argb(58, 82, 174, 214);
    private static final int LIGHT_TEXT = 0xFF1D282E;
    private static final int LIGHT_TEXT_DIM = 0xFF59686F;
    private static final int LIGHT_TEXT_FAINT = 0xFF929DA2;
    private static final int LIGHT_TRACK = 0xFFC9D3D8;

    private static final Paint RING_PAINT = new Paint()
            .setAntiAlias(true)
            .setMode(PaintMode.STROKE);
    private static final Paint SKIN_PAINT = new Paint().setAntiAlias(true);
    private static final Paint LOGO_PAINT = new Paint().setAntiAlias(true);
    private static final Paint PREVIEW_OUTLINE_PAINT = new Paint()
            .setAntiAlias(true)
            .setMode(PaintMode.STROKE);

    private final Screen parent;
    private final Map<Category, Float> bubbleHover = new EnumMap<>(Category.class);
    private final Map<Module, Float> moduleEnabled = new IdentityHashMap<>();
    private final Map<Setting<?>, Float> toggleProgress = new IdentityHashMap<>();

    private Category selectedCategory;
    private Module selectedModule;
    private boolean categoryRequested;
    private boolean settingsRequested;
    private float introProgress;
    private float categoryProgress;
    private float settingsProgress;
    private float daylightProgress;
    private float moduleScroll;
    private float settingScroll;

    private Setting<?> draggingNumber;
    private ColorSetting draggingColor;
    private int draggingColorChannel = -1;
    private float draggingTrackX;
    private float draggingTrackWidth;
    private final List<EspPreviewHit> espPreviewHits = new ArrayList<>();
    private EspPreviewPart draggingEspPreview;
    private float espDragStartX;
    private float espDragStartY;
    private double espOffsetStartX;
    private double espOffsetStartY;
    private float espViewportX;
    private float espViewportY;
    private float espViewportWidth;
    private float espViewportHeight;

    private Module capturingModule;
    private KeybindSetting capturingKeybind;
    private StringSetting editingString;
    private ColorSetting editingColor;
    private Color colorBeforeEdit;
    private String editText = "";
    private int editCursor;
    private boolean selectAll;

    private float activeScale = 1.0F;
    private float pointerX;
    private float pointerY;
    private float delta;
    private long lastFrame = System.nanoTime();
    private Integer originalMenuBlur;
    private boolean closing;
    private boolean closeFinished;

    public PopClickGuiScreen() {
        this(null);
    }

    public PopClickGuiScreen(Screen parent) {
        super(Component.literal("Setsuna ClickGUI / Pop"));
        this.parent = parent;
        for (Category category : CATEGORIES) {
            bubbleHover.put(category, 0.0F);
        }
    }

    @Override
    protected void init() {
        activeScale = configuredScale();
        introProgress = 0.0F;
        categoryProgress = 0.0F;
        settingsProgress = 0.0F;
        daylightProgress = ClickGui.INSTANCE.daylightMode.get() ? 1.0F : 0.0F;
        categoryRequested = false;
        settingsRequested = false;
        selectedCategory = null;
        selectedModule = null;
        moduleScroll = 0.0F;
        settingScroll = 0.0F;
        draggingEspPreview = null;
        espPreviewHits.clear();
        closing = false;
        closeFinished = false;
        lastFrame = System.nanoTime();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                  float partialTick) {
        int blur = ClickGui.INSTANCE.popBackgroundBlur.get();
        if (blur > 0) {
            applyMenuBlur(blur);
            graphics.blurBeforeThisStratum();
        } else {
            restoreMenuBlur();
        }
        graphics.fill(0, 0, width, height, 0x2601080D);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float partialTick) {
        pointerX = (float) logical(mouseX);
        pointerY = (float) logical(mouseY);
        if (SkijaRenderer.hasFailed()) {
            graphics.fill(0, 0, width, height, 0xD9080D11);
            graphics.text(font, "Skija renderer failed - check latest.log", 8, 8,
                    themeText(), false);
        }
    }

    @Override
    public void renderSkija(Canvas canvas) {
        updateAnimations();
        if (completeCloseWhenReady()) return;
        float logicalWidth = logicalWidth();
        float logicalHeight = logicalHeight();

        canvas.save();
        canvas.scale(activeScale, activeScale);
        SkijaUi.fill(canvas, 0.0F, 0.0F, logicalWidth + 1.0F, logicalHeight + 1.0F,
                multiplyAlpha(BACKDROP, smooth(introProgress)));

        canvas.save();
        if (closing) {
            float closeScale = lerp(0.16F, 1.0F, smooth(introProgress));
            canvas.translate(logicalWidth * 0.5F, logicalHeight * 0.5F);
            canvas.scale(closeScale, closeScale);
            canvas.translate(-logicalWidth * 0.5F, -logicalHeight * 0.5F);
        }
        renderRing(canvas, logicalWidth, logicalHeight);
        if (selectedCategory != null) {
            renderModulePanel(canvas, layout(logicalWidth, logicalHeight));
            if (settingsProgress > 0.01F && selectedModule != null) {
                renderSettingsPanel(canvas, layout(logicalWidth, logicalHeight));
                renderEspPreview(canvas, layout(logicalWidth, logicalHeight));
            }
        }
        canvas.restore();
        if (watermarkLogoTransferActive() && (closing || selectedCategory == null)) {
            drawPopLogo(canvas, logicalWidth * 0.5F, logicalHeight * 0.5F,
                    smooth(introProgress));
        }
        canvas.restore();
    }

    private void updateAnimations() {
        long now = System.nanoTime();
        delta = Math.min(0.05F, Math.max(0.0F,
                (now - lastFrame) / 1_000_000_000.0F));
        lastFrame = now;
        introProgress = animate(introProgress, closing ? 0.0F : 1.0F,
                closing ? 13.5F : 7.5F);
        categoryProgress = animate(categoryProgress, categoryRequested ? 1.0F : 0.0F,
                closing ? 16.5F : 9.5F);
        settingsProgress = animate(settingsProgress, settingsRequested ? 1.0F : 0.0F,
                closing ? 18.0F : 10.5F);
        daylightProgress = animate(daylightProgress,
                ClickGui.INSTANCE.daylightMode.get() ? 1.0F : 0.0F, 7.0F);

        if (!categoryRequested && categoryProgress < 0.01F && selectedCategory != null) {
            selectedCategory = null;
            selectedModule = null;
        }
        if (!settingsRequested && settingsProgress < 0.01F) {
            selectedModule = null;
            settingScroll = 0.0F;
        }

        for (Category category : CATEGORIES) {
            Bubble bubble = bubble(category, logicalWidth(), logicalHeight());
            boolean hovered = selectedCategory == null
                    && distance(pointerX, pointerY, bubble.x(), bubble.y()) <= BUBBLE_RADIUS + 4.0F;
            bubbleHover.put(category, animate(bubbleHover.getOrDefault(category, 0.0F),
                    hovered ? 1.0F : 0.0F, 12.0F));
        }
        for (Module module : ModuleManager.INSTANCE.modules()) {
            moduleEnabled.put(module, animate(moduleEnabled.getOrDefault(module,
                    module.isEnabled() ? 1.0F : 0.0F), module.isEnabled() ? 1.0F : 0.0F, 13.0F));
            for (Setting<?> setting : module.settings()) {
                if (setting instanceof BooleanSetting booleanSetting) {
                    toggleProgress.put(setting, animate(toggleProgress.getOrDefault(setting,
                                    booleanSetting.get() ? 1.0F : 0.0F),
                            booleanSetting.get() ? 1.0F : 0.0F, 14.0F));
                }
            }
        }
        clampScrolls(layout(logicalWidth(), logicalHeight()));
    }

    private void renderRing(Canvas canvas, float screenWidth, float screenHeight) {
        float centerX = screenWidth * 0.5F;
        float centerY = screenHeight * 0.5F;
        float ringRadius = ringRadius(screenWidth, screenHeight);
        float intro = smooth(introProgress);
        float collapse = smooth(categoryProgress);

        RING_PAINT.setStrokeWidth(1.0F)
                .setColor(withAlpha(accent(), Math.round(70.0F * intro * (1.0F - collapse))));
        canvas.drawCircle(centerX, centerY, ringRadius * intro, RING_PAINT);

        for (Category category : CATEGORIES) {
            Bubble bubble = bubble(category, screenWidth, screenHeight);
            boolean selected = category == selectedCategory;
            float disappear = selected ? 1.0F : 1.0F - collapse;
            if (disappear <= 0.01F) continue;

            float hover = bubbleHover.getOrDefault(category, 0.0F);
            float radius = BUBBLE_RADIUS + hover * 4.0F;
            float x = lerp(centerX, bubble.x(), intro);
            float y = lerp(centerY, bubble.y(), intro);
            if (selected) {
                PopLayout layout = layout(screenWidth, screenHeight);
                x = lerp(x, layout.listX() + layout.panelWidth() * 0.5F, collapse);
                y = lerp(y, layout.listY() + HEADER_HEIGHT * 0.5F, collapse);
                radius = lerp(radius, 15.0F, collapse);
            }

            int color = categoryColor(category);
            SkijaUi.rounded(canvas, x - radius - 1.0F, y - radius - 1.0F,
                    radius * 2.0F + 2.0F, radius * 2.0F + 2.0F, radius + 1.0F,
                    withAlpha(color, Math.round(120.0F * disappear)));
            SkijaUi.rounded(canvas, x - radius, y - radius, radius * 2.0F,
                    radius * 2.0F, radius, withAlpha(theme(0xFF11191E, 0xFFF7F9FA),
                            Math.round(238.0F * disappear)));
            float iconAlpha = disappear * (selected ? 1.0F - smooth(collapse) : 1.0F);
            drawCenteredIcon(canvas, categoryIcon(category), x, y - 6.0F,
                    withAlpha(color, Math.round(255.0F * iconAlpha)), 14.0F);
            if (!selected || collapse < 0.35F) {
                drawCenteredText(canvas, categoryTitle(category), x, y + 8.0F,
                        withAlpha(themeText(), Math.round(220.0F * iconAlpha)), 6.5F, true);
            }
        }

        if (selectedCategory == null && !watermarkLogoTransferActive()) {
            drawPopLogo(canvas, centerX, centerY, intro);
        }
    }

    private static boolean watermarkLogoTransferActive() {
        return WatermarkHUD.INSTANCE.isEnabled() && WatermarkHUD.INSTANCE.logo.get();
    }

    private void drawPopLogo(Canvas canvas, float centerX, float centerY, float alpha) {
        boolean transfer = watermarkLogoTransferActive();
        if (!transfer && alpha <= 0.001F) return;
        try (SkijaRenderer.BorrowedImage borrowed = SkijaRenderer.borrowTexture(LOGO_TEXTURE)) {
            if (borrowed == null) return;
            Image image = borrowed.image();
            float progress = smooth(alpha);
            float logoX = centerX;
            float logoY = centerY;
            float size = 38.0F * lerp(0.78F, 1.0F, progress);
            int logoAlpha = Math.round(255.0F * alpha);
            if (transfer) {
                float watermarkScale = WatermarkHUD.INSTANCE.scale.get().floatValue();
                float sourceSize = 11.0F * watermarkScale * 1.28F / activeScale;
                float sourceX = (WatermarkHUD.INSTANCE.hudX(width)
                        + 5.0F * watermarkScale + sourceSize * activeScale * 0.5F) / activeScale;
                float sourceY = (WatermarkHUD.INSTANCE.hudY(height)
                        + 2.0F * watermarkScale + sourceSize * activeScale * 0.5F) / activeScale;
                logoX = lerp(sourceX, centerX, progress);
                logoY = lerp(sourceY, centerY, progress);
                size = lerp(sourceSize, 38.0F, progress);
                logoAlpha = 255;
            }
            Rect source = Rect.makeXYWH(0.0F, 0.0F, image.getWidth(), image.getHeight());
            Rect destination = Rect.makeXYWH(logoX - size * 0.5F,
                    logoY - size * 0.5F, size, size);
            LOGO_PAINT.setColor(0xFFFFFFFF).setAlpha(logoAlpha);
            float glowProgress = transfer ? progress : alpha;
            if (glowProgress > 0.01F) {
                SkijaUi.glowLayer(canvas, destination.getLeft(), destination.getTop(),
                        destination.getWidth(), destination.getHeight(),
                        8.0F * glowProgress, Math.max(1, Math.round(6.0F * glowProgress)),
                        () -> canvas.drawImageRect(image, source, destination,
                                SamplingMode.MITCHELL, LOGO_PAINT, true));
            }
            canvas.drawImageRect(image, source, destination,
                    SamplingMode.MITCHELL, LOGO_PAINT, true);
        } catch (Throwable ignored) {
            // The category bubbles remain usable if the packaged logo is unavailable.
        } finally {
            LOGO_PAINT.setAlpha(255);
        }
    }

    private void renderModulePanel(Canvas canvas, PopLayout layout) {
        float progress = smooth(categoryProgress);
        Bubble origin = bubble(selectedCategory, layout.screenWidth(), layout.screenHeight());
        float startX = origin.x() - BUBBLE_RADIUS;
        float startY = origin.y() - BUBBLE_RADIUS;
        float startSize = BUBBLE_RADIUS * 2.0F;
        float panelX = lerp(startX, layout.listX(), progress);
        float panelY = lerp(startY, layout.listY(), progress);
        float panelWidth = lerp(startSize, layout.panelWidth(), progress);
        float panelHeight = lerp(startSize, layout.listHeight(), progress);
        float radius = lerp(BUBBLE_RADIUS, 9.0F, progress);
        int categoryColor = categoryColor(selectedCategory);

        SkijaUi.rounded(canvas, panelX - 1.0F, panelY - 1.0F,
                panelWidth + 2.0F, panelHeight + 2.0F, radius + 1.0F,
                withAlpha(categoryColor, Math.round(150.0F * progress)));
        SkijaUi.rounded(canvas, panelX, panelY, panelWidth, panelHeight, radius,
                withAlpha(themePanel(), Math.round(255.0F * progress)));
        if (progress < 0.36F) return;

        float contentAlpha = smooth((progress - 0.36F) / 0.64F);
        renderPanelHeader(canvas, panelX, panelY, panelWidth, selectedCategory,
                categoryTitle(selectedCategory), contentAlpha, true);

        float bodyY = panelY + HEADER_HEIGHT;
        float bodyHeight = panelHeight - HEADER_HEIGHT;
        canvas.save();
        canvas.clipRect(Rect.makeXYWH(panelX, bodyY, panelWidth, bodyHeight));
        float rowY = bodyY - moduleScroll;
        List<Module> modules = ModuleManager.INSTANCE.modulesIn(selectedCategory);
        for (Module module : modules) {
            renderModuleRow(canvas, module, panelX, rowY, panelWidth, contentAlpha);
            rowY += MODULE_ROW_HEIGHT;
        }
        canvas.restore();
        renderScrollbar(canvas, panelX, bodyY, panelWidth, bodyHeight,
                modules.size() * MODULE_ROW_HEIGHT, moduleScroll, categoryColor);
    }

    private void renderPanelHeader(Canvas canvas, float x, float y, float width,
                                   Category category, String title, float alpha,
                                   boolean categoryButton) {
        int color = categoryColor(category);
        SkijaUi.rounded(canvas, x + 8.0F, y + 8.0F, 28.0F, 28.0F, 14.0F,
                withAlpha(color, Math.round(205.0F * alpha)));
        if (categoryButton) {
            drawCenteredIcon(canvas, categoryIcon(category), x + 22.0F, y + 22.0F,
                    withAlpha(themeText(), Math.round(255.0F * alpha)), 11.0F);
        } else {
            renderModuleKeybindBadge(canvas, x + 22.0F, y + 22.0F, alpha);
        }
        SkijaUi.boldText(canvas, fit(title, width - 58.0F, 8.6F, true),
                x + 44.0F, y, HEADER_HEIGHT, withAlpha(themeText(), Math.round(255.0F * alpha)), 8.6F);
        SkijaUi.fill(canvas, x + 12.0F, y + HEADER_HEIGHT - 1.0F,
                Math.max(0.0F, width - 24.0F), 1.0F,
                withAlpha(color, Math.round(105.0F * alpha)));
    }

    private void renderModuleRow(Canvas canvas, Module module, float x, float y,
                                 float width, float alpha) {
        boolean hovered = inside(pointerX, pointerY, x + 7.0F, y + 2.0F,
                width - 14.0F, MODULE_ROW_HEIGHT - 4.0F);
        float enabled = moduleEnabled.getOrDefault(module, module.isEnabled() ? 1.0F : 0.0F);
        if (hovered || enabled > 0.01F) {
            SkijaUi.rounded(canvas, x + 7.0F, y + 2.0F, width - 14.0F,
                    MODULE_ROW_HEIGHT - 4.0F, 5.0F,
                    withAlpha(hovered ? theme(ROW_HOVER, LIGHT_ROW_HOVER) : categoryColor(selectedCategory),
                            Math.round((hovered ? 180.0F : 28.0F + enabled * 36.0F) * alpha)));
        }
        SkijaUi.text(canvas, fit(module.name(), width - 54.0F, 7.6F, false),
                x + 14.0F, y, MODULE_ROW_HEIGHT,
                withAlpha(mix(themeTextDim(), themeText(), enabled), Math.round(255.0F * alpha)), 7.6F);
        float dotRadius = 2.5F + enabled;
        SkijaUi.rounded(canvas, x + width - 18.0F - dotRadius,
                y + MODULE_ROW_HEIGHT * 0.5F - dotRadius,
                dotRadius * 2.0F, dotRadius * 2.0F, dotRadius,
                withAlpha(enabled > 0.01F ? categoryColor(selectedCategory) : themeTextFaint(),
                        Math.round(255.0F * alpha)));
    }

    private void renderSettingsPanel(Canvas canvas, PopLayout layout) {
        float progress = smooth(settingsProgress);
        float x = currentSettingsX(layout);
        float y = currentSettingsY(layout);
        float height = currentSettingsHeight(layout);
        int color = categoryColor(selectedCategory);

        SkijaUi.rounded(canvas, x - 1.0F, y - 1.0F,
                layout.panelWidth() + 2.0F, height + 2.0F, 10.0F,
                withAlpha(color, Math.round(145.0F * progress)));
        SkijaUi.rounded(canvas, x, y, layout.panelWidth(), height, 9.0F,
                withAlpha(themePanel(), Math.round(255.0F * progress)));
        if (progress < 0.18F) return;

        float alpha = smooth((progress - 0.18F) / 0.82F);
        renderPanelHeader(canvas, x, y, layout.panelWidth(), selectedCategory,
                selectedModule.name(), alpha, false);
        float bodyY = y + HEADER_HEIGHT;
        float bodyHeight = height - HEADER_HEIGHT;
        canvas.save();
        canvas.clipRect(Rect.makeXYWH(x, bodyY, layout.panelWidth(), bodyHeight));
        float rowY = bodyY - settingScroll;
        for (Setting<?> setting : visibleSettings()) {
            renderSetting(canvas, setting, x, rowY, layout.panelWidth(), alpha);
            rowY += settingHeight(setting);
        }
        canvas.restore();
        renderScrollbar(canvas, x, bodyY, layout.panelWidth(), bodyHeight,
                settingsContentHeight(), settingScroll, color);
    }

    private void renderEspPreview(Canvas canvas, PopLayout layout) {
        espPreviewHits.clear();
        boolean espPreview = selectedModule == ESP.INSTANCE;
        boolean targetHudPreview = selectedModule == TargetHud.INSTANCE;
        if (!espPreview && !targetHudPreview && selectedModule != KillEffect.INSTANCE) return;
        if (layout.previewWidth() < 80.0F) return;
        float progress = smooth(settingsProgress);
        if (progress <= 0.01F) return;

        float x = lerp(currentSettingsX(layout) + layout.panelWidth() - layout.previewWidth(),
                layout.previewX(), progress);
        float y = layout.previewY();
        float width = layout.previewWidth();
        float height = layout.previewHeight();
        int color = categoryColor(Category.RENDER);

        SkijaUi.rounded(canvas, x - 1.0F, y - 1.0F,
                width + 2.0F, height + 2.0F, 10.0F,
                withAlpha(color, Math.round(145.0F * progress)));
        SkijaUi.rounded(canvas, x, y, width, height, 9.0F,
                withAlpha(themePanel(), Math.round(255.0F * progress)));

        float contentAlpha = smooth((progress - 0.16F) / 0.84F);
        String previewTitle = espPreview ? "ESP PREVIEW"
                : targetHudPreview ? "TARGET HUD PREVIEW" : "KILL EFFECT PREVIEW";
        SkijaUi.boldText(canvas, previewTitle, x + 12.0F, y,
                36.0F, withAlpha(themeText(), Math.round(240.0F * contentAlpha)), 7.4F);
        SkijaUi.fill(canvas, x + 12.0F, y + 35.0F, width - 24.0F, 1.0F,
                withAlpha(color, Math.round(95.0F * contentAlpha)));

        float viewportX = x + 10.0F;
        float viewportY = y + 45.0F;
        float viewportWidth = width - 20.0F;
        float viewportHeight = height - 55.0F;
        espViewportX = viewportX;
        espViewportY = viewportY;
        espViewportWidth = viewportWidth;
        espViewportHeight = viewportHeight;
        SkijaUi.rounded(canvas, viewportX, viewportY, viewportWidth, viewportHeight,
                6.0F, withAlpha(themePanelInner(), Math.round(215.0F * contentAlpha)));
        canvas.save();
        canvas.clipRect(Rect.makeXYWH(viewportX, viewportY, viewportWidth, viewportHeight));
        if (espPreview) {
            drawEspPlayer(canvas, viewportX, viewportY, viewportWidth, viewportHeight, contentAlpha);
            drawPreviewChest(canvas, viewportX, viewportY, viewportWidth, viewportHeight, contentAlpha);
            drawEspPreviewHover(canvas, contentAlpha);
        } else if (targetHudPreview) {
            int previewLayer = canvas.saveLayerAlpha(null, Math.round(255.0F * contentAlpha));
            TargetHud.INSTANCE.renderPreview(canvas, viewportX, viewportY,
                    viewportWidth, viewportHeight);
            canvas.restoreToCount(previewLayer);
        } else {
            int previewLayer = canvas.saveLayerAlpha(null, Math.round(255.0F * contentAlpha));
            KillEffect.INSTANCE.renderPreview(canvas, viewportX, viewportY,
                    viewportWidth, viewportHeight);
            canvas.restoreToCount(previewLayer);
        }
        canvas.restore();
    }

    private void drawEspPlayer(Canvas canvas, float x, float y, float width,
                               float height, float alpha) {
        float scale = Math.max(1.0F, Math.min((width - 42.0F) / 16.0F,
                (height - 34.0F) / 32.0F));
        float figureWidth = 16.0F * scale;
        float figureHeight = 32.0F * scale;
        float figureX = x + (width - figureWidth) * 0.5F;
        float figureY = y + (height - figureHeight) * 0.5F;
        float boxInset = Math.max(3.0F, scale * 0.8F);

        boolean renderedSkin = false;
        if (minecraft.player != null && minecraft.player.getSkin() != null
                && minecraft.player.getSkin().body() != null) {
            var skin = minecraft.player.getSkin().body().texturePath();
            try (SkijaRenderer.BorrowedImage borrowed = SkijaRenderer.borrowTexture(skin)) {
                if (borrowed != null) {
                    drawSkinFigure(canvas, borrowed.image(), figureX, figureY, scale, alpha);
                    renderedSkin = true;
                }
            } catch (Throwable ignored) {
                // The geometric fallback keeps the preview useful while a skin is unavailable.
            }
        }
        if (!renderedSkin) {
            drawFallbackFigure(canvas, figureX, figureY, scale, alpha);
        }

        if (!ESP.INSTANCE.players.get()) return;

        int playerColor = multiplyAlpha(ESP.INSTANCE.playerColor.argb(), alpha);
        float left = figureX - boxInset;
        float top = figureY - boxInset;
        float boxWidth = figureWidth + boxInset * 2.0F;
        float boxHeight = figureHeight + boxInset * 2.0F;
        switch (ESP.INSTANCE.playerMode.get()) {
            case CSGO -> drawPreviewCsgo(canvas, left, top, boxWidth, boxHeight,
                    playerColor, alpha);
            case Outline -> drawPreviewPlayerOutline(canvas, figureX, figureY,
                    scale, playerColor);
            case Box2D -> {
                float boxX = left + ESP.INSTANCE.playerOffsetX.get().floatValue();
                float boxY = top + ESP.INSTANCE.playerOffsetY.get().floatValue();
                drawPreviewBox(canvas, boxX, boxY, boxWidth, boxHeight, playerColor);
                addEspPreviewHit(EspPreviewPart.PLAYER, boxX - 2.0F, boxY - 2.0F,
                        boxWidth + 4.0F, boxHeight + 4.0F);
            }
            case Box3D -> drawPreview3DBox(canvas, left, top, boxWidth, boxHeight,
                    playerColor);
            case Cylinder -> drawPreviewCylinder(canvas, left, top, boxWidth,
                    boxHeight, playerColor);
        }
        drawPreviewEquipment(canvas, left, top, boxWidth, alpha);
        drawPreviewEffects(canvas, left, top, boxWidth, alpha);
    }

    private void drawPreviewEquipment(Canvas canvas, float boxX, float boxY,
                                      float boxWidth, float alpha) {
        if (!ESP.INSTANCE.showEquipment.get()) return;

        int itemCount = ESP.INSTANCE.showHands.get() ? 6 : 4;
        float itemSize = 8.0F;
        float gap = 1.5F;
        float rowWidth = itemCount * itemSize + (itemCount - 1) * gap;
        float x = boxX + boxWidth * 0.5F - rowWidth * 0.5F
                + ESP.INSTANCE.equipmentOffsetX.get().floatValue();
        float y = boxY - itemSize - 4.0F
                + ESP.INSTANCE.equipmentOffsetY.get().floatValue();
        int[] colors = {0xFFC9D5DD, 0xFF8EC7DB, 0xFF708BA5, 0xFF65758B, 0xFF9C7FE3, 0xFFD7E1E7};
        for (int i = 0; i < itemCount; i++) {
            float itemX = x + i * (itemSize + gap);
            SkijaUi.rounded(canvas, itemX, y, itemSize, itemSize, 1.5F,
                    multiplyAlpha(0xB012181D, alpha));
            float inset = i == 0 || i == itemCount - 1 ? 2.2F : 1.7F;
            SkijaUi.rounded(canvas, itemX + inset, y + inset,
                    itemSize - inset * 2.0F, itemSize - inset * 2.0F, 0.8F,
                    multiplyAlpha(colors[i], alpha));
        }
        addEspPreviewHit(EspPreviewPart.EQUIPMENT, x - 2.0F, y - 2.0F,
                rowWidth + 4.0F, itemSize + 4.0F);
    }

    private void drawPreviewEffects(Canvas canvas, float boxX, float boxY,
                                    float boxWidth, float alpha) {
        if (!ESP.INSTANCE.showEffects.get()) return;

        String[] effects = {"Speed II 1:24", "Strength I 0:48"};
        int[] colors = {0xFF7CC6F2, 0xFFF07474};
        float textSize = 5.8F;
        float rowHeight = 8.0F;
        float gap = 1.0F;
        float centerX = boxX + boxWidth * 0.5F
                + ESP.INSTANCE.effectsOffsetX.get().floatValue();
        float y = boxY + 2.0F + ESP.INSTANCE.effectsOffsetY.get().floatValue();
        float unionLeft = Float.MAX_VALUE;
        float unionRight = -Float.MAX_VALUE;
        for (int i = 0; i < effects.length; i++) {
            float rowWidth = SkijaUi.textWidth(effects[i], textSize) + 5.0F;
            float left = centerX - rowWidth * 0.5F;
            float rowY = y + i * (rowHeight + gap);
            SkijaUi.fill(canvas, left, rowY, rowWidth, rowHeight,
                    multiplyAlpha(ESP.INSTANCE.infoBackgroundColor.argb(), alpha));
            SkijaUi.text(canvas, effects[i], left + 2.5F, rowY,
                    rowHeight, multiplyAlpha(colors[i], alpha), textSize);
            unionLeft = Math.min(unionLeft, left);
            unionRight = Math.max(unionRight, left + rowWidth);
        }
        addEspPreviewHit(EspPreviewPart.EFFECTS, unionLeft - 2.0F, y - 2.0F,
                unionRight - unionLeft + 4.0F,
                effects.length * rowHeight + (effects.length - 1) * gap + 4.0F);
    }

    private void drawPreviewChest(Canvas canvas, float x, float y, float width,
                                  float height, float alpha) {
        if (!ESP.INSTANCE.chests.get()) return;

        float chestWidth = 23.0F;
        float chestHeight = 15.0F;
        float chestX = x + width - chestWidth - 8.0F;
        float chestY = y + height - chestHeight - 7.0F;
        SkijaUi.fill(canvas, chestX, chestY, chestWidth, chestHeight,
                multiplyAlpha(ESP.INSTANCE.color.argb(), alpha));
        SkijaUi.fill(canvas, chestX, chestY + 4.0F, chestWidth, 1.0F,
                multiplyAlpha(ESP.INSTANCE.outlineColor.argb(), alpha));
        SkijaUi.fill(canvas, chestX + chestWidth * 0.5F - 1.5F,
                chestY + 5.0F, 3.0F, 4.0F,
                multiplyAlpha(ESP.INSTANCE.outlineColor.argb(), alpha));
        if (ESP.INSTANCE.outline.get()) {
            PREVIEW_OUTLINE_PAINT.setStrokeWidth(
                    ESP.INSTANCE.outlineThickness.get().floatValue())
                    .setColor(multiplyAlpha(ESP.INSTANCE.outlineColor.argb(), alpha));
            canvas.drawRect(Rect.makeXYWH(chestX, chestY, chestWidth, chestHeight),
                    PREVIEW_OUTLINE_PAINT);
        }
        if (ESP.INSTANCE.minecartChests.get()) {
            SkijaUi.fill(canvas, chestX - 2.0F, chestY + chestHeight + 2.0F,
                    chestWidth + 4.0F, 1.0F,
                    multiplyAlpha(ESP.INSTANCE.outlineColor.argb(), alpha));
            SkijaUi.rounded(canvas, chestX + 2.0F, chestY + chestHeight,
                    4.0F, 4.0F, 2.0F, multiplyAlpha(0xFF71828B, alpha));
            SkijaUi.rounded(canvas, chestX + chestWidth - 6.0F, chestY + chestHeight,
                    4.0F, 4.0F, 2.0F, multiplyAlpha(0xFF71828B, alpha));
        }
    }

    private void addEspPreviewHit(EspPreviewPart part, float x, float y,
                                  float width, float height) {
        espPreviewHits.add(new EspPreviewHit(part, x, y, width, height));
    }

    private void drawEspPreviewHover(Canvas canvas, float alpha) {
        for (int i = espPreviewHits.size() - 1; i >= 0; i--) {
            EspPreviewHit hit = espPreviewHits.get(i);
            if (hit.part() != draggingEspPreview
                    && !inside(pointerX, pointerY, hit.x(), hit.y(), hit.width(), hit.height())) {
                continue;
            }
            drawPreviewOutline(canvas, hit.x(), hit.y(), hit.width(), hit.height(),
                    multiplyAlpha(accent(), alpha * 0.7F));
            return;
        }
    }

    private void drawPreviewCsgo(Canvas canvas, float x, float y, float width,
                                 float height, int color, float alpha) {
        if (ESP.INSTANCE.playerBox.get()) {
            float boxX = x + ESP.INSTANCE.playerOffsetX.get().floatValue();
            float boxY = y + ESP.INSTANCE.playerOffsetY.get().floatValue();
            drawPreviewBox(canvas, boxX, boxY, width, height, color);
            addEspPreviewHit(EspPreviewPart.PLAYER, boxX - 2.0F, boxY - 2.0F,
                    width + 4.0F, height + 4.0F);
        }
        if (ESP.INSTANCE.healthBar.get()) {
            float barX = x - 7.0F + ESP.INSTANCE.healthOffsetX.get().floatValue();
            float barY = y + ESP.INSTANCE.healthOffsetY.get().floatValue();
            SkijaUi.fill(canvas, barX, barY, 4.0F, height, multiplyAlpha(0xFF000000, alpha));
            SkijaUi.fill(canvas, barX + 1.0F, barY + 1.0F, 2.0F, height - 2.0F,
                    multiplyAlpha(0xFF242B2E, alpha));
            float health = 0.72F;
            float fillHeight = (height - 2.0F) * health;
            SkijaUi.fill(canvas, barX + 1.0F, barY + height - 1.0F - fillHeight,
                    2.0F, fillHeight, multiplyAlpha(0xFF7DE35F, alpha));
            addEspPreviewHit(EspPreviewPart.HEALTH, barX - 3.0F, barY,
                    10.0F, height);
        }
        if (ESP.INSTANCE.armorBar.get()) {
            float barX = x + width + 3.0F + ESP.INSTANCE.armorOffsetX.get().floatValue();
            float barY = y + ESP.INSTANCE.armorOffsetY.get().floatValue();
            float gap = 1.0F;
            float segmentHeight = (height - gap * 3.0F) / 4.0F;
            for (int i = 0; i < 4; i++) {
                float segmentY = barY + i * (segmentHeight + gap);
                SkijaUi.fill(canvas, barX, segmentY, 4.0F, segmentHeight,
                        multiplyAlpha(0xFF000000, alpha));
                float durability = 1.0F - i * 0.12F;
                float fillHeight = Math.max(1.0F, (segmentHeight - 2.0F) * durability);
                SkijaUi.fill(canvas, barX + 1.0F,
                        segmentY + segmentHeight - 1.0F - fillHeight,
                        2.0F, fillHeight, multiplyAlpha(0xFF4ECEE5, alpha));
            }
            addEspPreviewHit(EspPreviewPart.ARMOR, barX - 2.0F, barY,
                    8.0F, height);
        }
    }

    private static void drawPreviewBox(Canvas canvas, float x, float y, float width,
                                       float height, int color) {
        drawPreviewOutline(canvas, x - 1.0F, y - 1.0F, width + 2.0F,
                height + 2.0F, multiplyAlpha(0xFF000000, ((color >>> 24) & 0xFF) / 255.0F));
        drawPreviewOutline(canvas, x, y, width, height, color);
        drawPreviewOutline(canvas, x + 1.0F, y + 1.0F, width - 2.0F,
                height - 2.0F, multiplyAlpha(0xFF000000, ((color >>> 24) & 0xFF) / 255.0F));
    }

    private static void drawPreviewPlayerOutline(Canvas canvas, float x, float y,
                                                 float scale, int color) {
        PREVIEW_OUTLINE_PAINT.setStrokeWidth(Math.max(1.0F, scale * 0.38F)).setColor(color);
        canvas.drawRect(Rect.makeXYWH(x + 4.0F * scale, y, 8.0F * scale,
                8.0F * scale), PREVIEW_OUTLINE_PAINT);
        canvas.drawRect(Rect.makeXYWH(x + 4.0F * scale, y + 8.0F * scale,
                8.0F * scale, 12.0F * scale), PREVIEW_OUTLINE_PAINT);
        canvas.drawRect(Rect.makeXYWH(x, y + 8.0F * scale, 4.0F * scale,
                12.0F * scale), PREVIEW_OUTLINE_PAINT);
        canvas.drawRect(Rect.makeXYWH(x + 12.0F * scale, y + 8.0F * scale,
                4.0F * scale, 12.0F * scale), PREVIEW_OUTLINE_PAINT);
        canvas.drawRect(Rect.makeXYWH(x + 4.0F * scale, y + 20.0F * scale,
                4.0F * scale, 12.0F * scale), PREVIEW_OUTLINE_PAINT);
        canvas.drawRect(Rect.makeXYWH(x + 8.0F * scale, y + 20.0F * scale,
                4.0F * scale, 12.0F * scale), PREVIEW_OUTLINE_PAINT);
    }

    private static void drawPreview3DBox(Canvas canvas, float x, float y, float width,
                                         float height, int color) {
        float offset = Math.max(4.0F, width * 0.16F);
        PREVIEW_OUTLINE_PAINT.setStrokeWidth(1.2F).setColor(color);
        Rect front = Rect.makeXYWH(x, y + offset, width - offset, height - offset);
        Rect back = Rect.makeXYWH(x + offset, y, width - offset, height - offset);
        canvas.drawRect(front, PREVIEW_OUTLINE_PAINT);
        canvas.drawRect(back, PREVIEW_OUTLINE_PAINT);
        canvas.drawLine(front.getLeft(), front.getTop(), back.getLeft(), back.getTop(), PREVIEW_OUTLINE_PAINT);
        canvas.drawLine(front.getRight(), front.getTop(), back.getRight(), back.getTop(), PREVIEW_OUTLINE_PAINT);
        canvas.drawLine(front.getLeft(), front.getBottom(), back.getLeft(), back.getBottom(), PREVIEW_OUTLINE_PAINT);
        canvas.drawLine(front.getRight(), front.getBottom(), back.getRight(), back.getBottom(), PREVIEW_OUTLINE_PAINT);
    }

    private static void drawPreviewCylinder(Canvas canvas, float x, float y, float width,
                                            float height, int color) {
        float ellipseHeight = Math.max(5.0F, width * 0.28F);
        PREVIEW_OUTLINE_PAINT.setStrokeWidth(1.2F).setColor(color);
        canvas.drawOval(Rect.makeXYWH(x, y, width, ellipseHeight), PREVIEW_OUTLINE_PAINT);
        canvas.drawOval(Rect.makeXYWH(x, y + height - ellipseHeight, width, ellipseHeight),
                PREVIEW_OUTLINE_PAINT);
        canvas.drawLine(x, y + ellipseHeight * 0.5F, x,
                y + height - ellipseHeight * 0.5F, PREVIEW_OUTLINE_PAINT);
        canvas.drawLine(x + width, y + ellipseHeight * 0.5F, x + width,
                y + height - ellipseHeight * 0.5F, PREVIEW_OUTLINE_PAINT);
    }

    private static void drawPreviewOutline(Canvas canvas, float x, float y, float width,
                                           float height, int color) {
        SkijaUi.fill(canvas, x, y, width, 1.0F, color);
        SkijaUi.fill(canvas, x, y + height - 1.0F, width, 1.0F, color);
        SkijaUi.fill(canvas, x, y + 1.0F, 1.0F, height - 2.0F, color);
        SkijaUi.fill(canvas, x + width - 1.0F, y + 1.0F, 1.0F, height - 2.0F, color);
    }

    private static void drawSkinFigure(Canvas canvas, Image image, float x, float y,
                                       float scale, float alpha) {
        SKIN_PAINT.setColor(0xFFFFFFFF).setAlpha(Math.round(255.0F * alpha));
        drawSkinPart(canvas, image, 8, 8, 8, 8,
                x + 4.0F * scale, y, 8.0F * scale, 8.0F * scale);
        drawSkinPart(canvas, image, 20, 20, 8, 12,
                x + 4.0F * scale, y + 8.0F * scale, 8.0F * scale, 12.0F * scale);
        drawSkinPart(canvas, image, 44, 20, 4, 12,
                x, y + 8.0F * scale, 4.0F * scale, 12.0F * scale);
        drawSkinPart(canvas, image, 36, 52, 4, 12,
                x + 12.0F * scale, y + 8.0F * scale, 4.0F * scale, 12.0F * scale);
        drawSkinPart(canvas, image, 4, 20, 4, 12,
                x + 4.0F * scale, y + 20.0F * scale, 4.0F * scale, 12.0F * scale);
        drawSkinPart(canvas, image, 20, 52, 4, 12,
                x + 8.0F * scale, y + 20.0F * scale, 4.0F * scale, 12.0F * scale);

        drawSkinPart(canvas, image, 40, 8, 8, 8,
                x + 4.0F * scale, y, 8.0F * scale, 8.0F * scale);
        drawSkinPart(canvas, image, 20, 36, 8, 12,
                x + 4.0F * scale, y + 8.0F * scale, 8.0F * scale, 12.0F * scale);
        drawSkinPart(canvas, image, 44, 36, 4, 12,
                x, y + 8.0F * scale, 4.0F * scale, 12.0F * scale);
        drawSkinPart(canvas, image, 52, 52, 4, 12,
                x + 12.0F * scale, y + 8.0F * scale, 4.0F * scale, 12.0F * scale);
        drawSkinPart(canvas, image, 4, 36, 4, 12,
                x + 4.0F * scale, y + 20.0F * scale, 4.0F * scale, 12.0F * scale);
        drawSkinPart(canvas, image, 4, 52, 4, 12,
                x + 8.0F * scale, y + 20.0F * scale, 4.0F * scale, 12.0F * scale);
        SKIN_PAINT.setAlpha(255);
    }

    private static void drawSkinPart(Canvas canvas, Image image,
                                     float sourceX, float sourceY, float sourceWidth, float sourceHeight,
                                     float x, float y, float width, float height) {
        float textureWidth = image.getWidth();
        float textureHeight = image.getHeight();
        Rect source = Rect.makeLTRB(sourceX / 64.0F * textureWidth,
                sourceY / 64.0F * textureHeight,
                (sourceX + sourceWidth) / 64.0F * textureWidth,
                (sourceY + sourceHeight) / 64.0F * textureHeight);
        canvas.drawImageRect(image, source, Rect.makeXYWH(x, y, width, height),
                SamplingMode.DEFAULT, SKIN_PAINT, true);
    }

    private void drawFallbackFigure(Canvas canvas, float x, float y, float scale, float alpha) {
        int color = withAlpha(themeTextDim(), Math.round(170.0F * alpha));
        SkijaUi.rounded(canvas, x + 4.0F * scale, y, 8.0F * scale,
                8.0F * scale, scale, color);
        SkijaUi.rounded(canvas, x, y + 8.0F * scale, 16.0F * scale,
                12.0F * scale, scale, color);
        SkijaUi.rounded(canvas, x + 4.0F * scale, y + 20.0F * scale,
                8.0F * scale, 12.0F * scale, scale, color);
    }

    private void renderModuleKeybindBadge(Canvas canvas, float centerX, float centerY, float alpha) {
        int color = withAlpha(themeText(), Math.round(255.0F * alpha));
        if (capturingModule == selectedModule) {
            drawCenteredText(canvas, "...", centerX, centerY, color, 7.4F, true);
            return;
        }
        String key = KeyBindText.of(selectedModule.keyBind());
        if (key == null || key.isBlank()) {
            drawKeyboardIcon(canvas, centerX, centerY, color);
            return;
        }
        String label = key.toUpperCase(Locale.ROOT);
        float size = label.length() <= 2 ? 8.0F : label.length() <= 4 ? 6.5F : 5.1F;
        drawCenteredText(canvas, fit(label, 20.0F, size, true), centerX, centerY,
                color, size, true);
    }

    private void drawKeyboardIcon(Canvas canvas, float centerX, float centerY, int color) {
        SkijaUi.rounded(canvas, centerX - 7.5F, centerY - 5.0F,
                15.0F, 10.0F, 2.0F, color);
        SkijaUi.rounded(canvas, centerX - 6.3F, centerY - 3.8F,
                12.6F, 7.6F, 1.2F, theme(0xFF1A242A, 0xFFF7F9FA));
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 4; column++) {
                SkijaUi.rounded(canvas, centerX - 5.2F + column * 2.8F,
                        centerY - 2.8F + row * 2.8F, 1.7F, 1.5F, 0.4F, color);
            }
        }
        SkijaUi.rounded(canvas, centerX - 3.7F, centerY + 2.4F,
                7.4F, 1.0F, 0.4F, color);
    }

    private void renderSetting(Canvas canvas, Setting<?> setting, float x, float y,
                               float width, float alpha) {
        float height = settingHeight(setting);
        renderSettingSurface(canvas, x, y, width, height, alpha);
        int labelColor = withAlpha(themeTextDim(), Math.round(255.0F * alpha));
        int valueColor = withAlpha(themeValue(), Math.round(255.0F * alpha));

        if (setting instanceof ButtonSetting) {
            drawCenteredText(canvas, setting.name(), x + width * 0.5F,
                    y + height * 0.5F, valueColor, 7.4F, true);
            return;
        }

        float labelReserve = setting instanceof ColorSetting ? 112.0F : 68.0F;
        SkijaUi.text(canvas, fit(setting.name(), width - labelReserve, 7.2F, false),
                x + 13.0F, y, SETTING_ROW_HEIGHT, labelColor, 7.2F);
        if (setting instanceof BooleanSetting booleanSetting) {
            float progress = toggleProgress.getOrDefault(setting, booleanSetting.get() ? 1.0F : 0.0F);
            float toggleX = x + width - 40.0F;
            float toggleY = y + 10.0F;
            SkijaUi.rounded(canvas, toggleX, toggleY, 27.0F, 12.0F, 6.0F,
                    mix(themeTrack(), accent(), progress));
            SkijaUi.rounded(canvas, toggleX + 2.0F + progress * 15.0F, toggleY + 2.0F,
                    8.0F, 8.0F, 4.0F, themeText());
        } else if (setting instanceof FontSetting fontSetting) {
            drawRight(canvas, fit(fontSetting.displayValue(), 66.0F, 7.0F, false),
                    x + width - 13.0F, y, SETTING_ROW_HEIGHT, valueColor, 7.0F);
        } else if (setting instanceof EnumSetting<?> enumSetting) {
            drawRight(canvas, fit(enumSetting.get().name(), 66.0F, 7.0F, false),
                    x + width - 13.0F, y, SETTING_ROW_HEIGHT, valueColor, 7.0F);
            if (MusicPresetPreview.matches(setting)) {
                MusicPresetPreview.draw(canvas, x + 13.0F, y + SETTING_ROW_HEIGHT,
                        Math.max(1.0F, width - 26.0F), height - SETTING_ROW_HEIGHT, alpha);
            }
        } else if (setting instanceof IntSetting intSetting) {
            String value = Integer.toString(intSetting.get());
            drawRight(canvas, value, x + width - 13.0F, y,
                    SETTING_ROW_HEIGHT, valueColor, 7.0F);
            renderSlider(canvas, x + 13.0F, y + 30.0F, width - 26.0F,
                    intSetting.fraction(), valueColor);
        } else if (setting instanceof DoubleSetting doubleSetting) {
            String value = trim(doubleSetting.get());
            drawRight(canvas, value, x + width - 13.0F, y,
                    SETTING_ROW_HEIGHT, valueColor, 7.0F);
            renderSlider(canvas, x + 13.0F, y + 30.0F, width - 26.0F,
                    doubleSetting.fraction(), valueColor);
        } else if (setting instanceof ColorSetting colorSetting) {
            renderColor(canvas, colorSetting, x, y, width, alpha);
        } else if (setting instanceof KeybindSetting keybindSetting) {
            String value = capturingKeybind == keybindSetting ? "..." : KeyBindText.of(keybindSetting.get());
            drawRight(canvas, value == null || value.isBlank() ? "NONE" : value,
                    x + width - 13.0F, y, SETTING_ROW_HEIGHT, valueColor, 7.0F);
        } else if (setting instanceof StringSetting stringSetting) {
            String value = editingString == stringSetting ? textWithCursor() : stringSetting.get();
            float fieldX = x + width - 78.0F;
            SkijaUi.rounded(canvas, fieldX, y + 7.0F, 65.0F, 17.0F, 4.0F,
                    editingString == stringSetting ? withAlpha(accent(), 92) : themeTrack());
            SkijaUi.text(canvas, fit(value.isEmpty() ? "Empty" : value, 57.0F, 6.8F, false),
                    fieldX + 4.0F, y + 7.0F, 17.0F,
                    value.isEmpty() ? themeTextFaint() : themeText(), 6.8F);
        }
    }

    private void renderSettingSurface(Canvas canvas, float x, float y, float width,
                                      float height, float alpha) {
        boolean hovered = inside(pointerX, pointerY, x + 7.0F, y + 2.0F,
                width - 14.0F, height - 4.0F);
        SkijaUi.rounded(canvas, x + 7.0F, y + 2.0F, width - 14.0F,
                Math.max(1.0F, height - 4.0F), 5.0F,
                withAlpha(hovered ? theme(ROW_HOVER, LIGHT_ROW_HOVER) : themePanelInner(),
                        Math.round((hovered ? 155.0F : 205.0F) * alpha)));
    }

    private void renderSlider(Canvas canvas, float x, float y, float width,
                              float fraction, int color) {
        float progress = clamp(fraction, 0.0F, 1.0F);
        float handleX = x + width * progress;
        SkijaUi.rounded(canvas, x, y, width, 2.0F, 1.0F, themeTrack());
        SkijaUi.rounded(canvas, x, y, width * progress, 2.0F, 1.0F, color);
        SkijaUi.rounded(canvas, handleX - 3.0F, y - 3.0F, 6.0F, 8.0F, 3.0F, color);
    }

    private void renderColor(Canvas canvas, ColorSetting setting, float x, float y,
                             float width, float alpha) {
        Color color = setting.get();
        float fieldX = x + width - 105.0F;
        float fieldY = y + 7.0F;
        float fieldWidth = 68.0F;
        float fieldHeight = 17.0F;
        boolean focused = editingColor == setting;
        String hex = focused ? textWithCursor() : setting.hex();
        SkijaUi.rounded(canvas, fieldX, fieldY, fieldWidth, fieldHeight, 4.0F,
                focused ? withAlpha(accent(), 92) : themeTrack());
        SkijaUi.text(canvas, fit(hex, fieldWidth - 8.0F, 6.6F, false),
                fieldX + 4.0F, fieldY, fieldHeight,
                focused ? themeText() : themeTextDim(), 6.6F);
        SkijaUi.rounded(canvas, x + width - 31.0F, y + 8.0F, 18.0F, 15.0F,
                4.0F, setting.argb());
        int[] values = {color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()};
        int[] colors = {0xFFE85B5B, 0xFF55D780, 0xFF5A9FEB, 0xFFE6E8EA};
        String[] labels = {"R", "G", "B", "A"};
        int channels = setting.allowAlpha() ? 4 : 3;
        for (int channel = 0; channel < channels; channel++) {
            float channelY = y + SETTING_ROW_HEIGHT + channel * COLOR_CHANNEL_HEIGHT;
            SkijaUi.boldText(canvas, labels[channel], x + 13.0F, channelY,
                    COLOR_CHANNEL_HEIGHT, withAlpha(colors[channel], Math.round(255.0F * alpha)), 6.6F);
            drawRight(canvas, Integer.toString(values[channel]), x + width - 13.0F,
                    channelY, COLOR_CHANNEL_HEIGHT, themeText(), 6.4F);
            renderSlider(canvas, x + 28.0F, channelY + 10.0F, width - 76.0F,
                    values[channel] / 255.0F,
                    withAlpha(colors[channel], Math.round(255.0F * alpha)));
        }
    }

    private void renderScrollbar(Canvas canvas, float x, float y, float width, float height,
                                 float contentHeight, float scroll, int color) {
        if (contentHeight <= height || height <= 0.0F) return;
        float thumbHeight = Math.max(18.0F, height * height / contentHeight);
        float maxScroll = contentHeight - height;
        float thumbY = y + scroll / maxScroll * (height - thumbHeight);
        SkijaUi.rounded(canvas, x + width - 3.0F, y + 4.0F, 2.0F,
                height - 8.0F, 1.0F, withAlpha(themeTrack(), 150));
        SkijaUi.rounded(canvas, x + width - 3.0F, thumbY, 2.0F,
                thumbHeight, 1.0F, color);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closing) return true;
        float mouseX = (float) logical(event.x());
        float mouseY = (float) logical(event.y());
        int button = event.button();
        finishTextEditing();
        capturingModule = null;
        capturingKeybind = null;

        if (selectedCategory == null) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
            for (Category category : CATEGORIES) {
                Bubble bubble = bubble(category, logicalWidth(), logicalHeight());
                if (distance(mouseX, mouseY, bubble.x(), bubble.y()) <= BUBBLE_RADIUS + 6.0F) {
                    selectedCategory = category;
                    categoryRequested = true;
                    moduleScroll = 0.0F;
                    return true;
                }
            }
            return false;
        }

        PopLayout layout = layout(logicalWidth(), logicalHeight());
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && settingsProgress > 0.7F
                && beginEspPreviewDrag(mouseX, mouseY)) {
            return true;
        }
        if (categoryProgress > 0.75F && inside(mouseX, mouseY,
                layout.listX() + 8.0F, layout.listY() + 8.0F, 28.0F, 28.0F)) {
            settingsRequested = false;
            categoryRequested = false;
            return true;
        }

        if (settingsProgress > 0.7F && selectedModule != null) {
            float settingsX = currentSettingsX(layout);
            float settingsY = currentSettingsY(layout);
            if (inside(mouseX, mouseY, settingsX + 8.0F,
                    settingsY + 8.0F, 28.0F, 28.0F)) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    selectedModule.setKeyBind(-1);
                    capturingModule = null;
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    capturingModule = selectedModule;
                }
                return true;
            }
            if (handleSettingClick(layout, mouseX, mouseY, button)) return true;
        }

        if (categoryProgress > 0.75F && handleModuleClick(layout, mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean beginEspPreviewDrag(float mouseX, float mouseY) {
        if (selectedModule != ESP.INSTANCE
                || !inside(mouseX, mouseY, espViewportX, espViewportY,
                espViewportWidth, espViewportHeight)) {
            return false;
        }
        for (int i = espPreviewHits.size() - 1; i >= 0; i--) {
            EspPreviewHit hit = espPreviewHits.get(i);
            if (!inside(mouseX, mouseY, hit.x(), hit.y(), hit.width(), hit.height())) continue;
            draggingEspPreview = hit.part();
            espDragStartX = mouseX;
            espDragStartY = mouseY;
            espOffsetStartX = espOffsetX(hit.part()).get();
            espOffsetStartY = espOffsetY(hit.part()).get();
            return true;
        }
        return false;
    }

    private void updateEspPreviewDrag(float mouseX, float mouseY) {
        if (draggingEspPreview == null) return;
        float boundedX = clamp(mouseX, espViewportX + 1.0F,
                espViewportX + espViewportWidth - 1.0F);
        float boundedY = clamp(mouseY, espViewportY + 1.0F,
                espViewportY + espViewportHeight - 1.0F);
        espOffsetX(draggingEspPreview).set(espOffsetStartX + boundedX - espDragStartX);
        espOffsetY(draggingEspPreview).set(espOffsetStartY + boundedY - espDragStartY);
    }

    private static DoubleSetting espOffsetX(EspPreviewPart part) {
        return switch (part) {
            case PLAYER -> ESP.INSTANCE.playerOffsetX;
            case HEALTH -> ESP.INSTANCE.healthOffsetX;
            case ARMOR -> ESP.INSTANCE.armorOffsetX;
            case EQUIPMENT -> ESP.INSTANCE.equipmentOffsetX;
            case EFFECTS -> ESP.INSTANCE.effectsOffsetX;
        };
    }

    private static DoubleSetting espOffsetY(EspPreviewPart part) {
        return switch (part) {
            case PLAYER -> ESP.INSTANCE.playerOffsetY;
            case HEALTH -> ESP.INSTANCE.healthOffsetY;
            case ARMOR -> ESP.INSTANCE.armorOffsetY;
            case EQUIPMENT -> ESP.INSTANCE.equipmentOffsetY;
            case EFFECTS -> ESP.INSTANCE.effectsOffsetY;
        };
    }

    private boolean handleModuleClick(PopLayout layout, float mouseX, float mouseY, int button) {
        if (!inside(mouseX, mouseY, layout.listX(), layout.listY() + HEADER_HEIGHT,
                layout.panelWidth(), layout.listHeight() - HEADER_HEIGHT)) return false;
        float rowY = layout.listY() + HEADER_HEIGHT - moduleScroll;
        for (Module module : ModuleManager.INSTANCE.modulesIn(selectedCategory)) {
            if (inside(mouseX, mouseY, layout.listX(), rowY,
                    layout.panelWidth(), MODULE_ROW_HEIGHT)) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    module.interactFromClickGui();
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    if (selectedModule == module && settingsRequested) {
                        settingsRequested = false;
                    } else {
                        selectedModule = module;
                        settingsRequested = true;
                        settingScroll = 0.0F;
                    }
                }
                return true;
            }
            rowY += MODULE_ROW_HEIGHT;
        }
        return false;
    }

    private boolean handleSettingClick(PopLayout layout, float mouseX, float mouseY, int button) {
        float settingsX = currentSettingsX(layout);
        float settingsY = currentSettingsY(layout);
        float settingsHeight = currentSettingsHeight(layout);
        float bodyY = settingsY + HEADER_HEIGHT;
        if (!inside(mouseX, mouseY, settingsX, bodyY,
                layout.panelWidth(), settingsHeight - HEADER_HEIGHT)) return false;
        float rowY = bodyY - settingScroll;
        for (Setting<?> setting : visibleSettings()) {
            float height = settingHeight(setting);
            if (inside(mouseX, mouseY, settingsX, rowY,
                    layout.panelWidth(), height)) {
                clickSetting(setting, layout, rowY, mouseX, mouseY, button);
                return true;
            }
            rowY += height;
        }
        return false;
    }

    private void clickSetting(Setting<?> setting, PopLayout layout, float rowY,
                              float mouseX, float mouseY, int button) {
        if (setting instanceof BooleanSetting booleanSetting) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                NotificationManager.INSTANCE.withoutModuleFeedback(booleanSetting::toggle);
            }
        } else if (setting instanceof FontSetting fontSetting) {
            fontSetting.cycle(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1);
        } else if (setting instanceof EnumSetting<?> enumSetting) {
            enumSetting.cycle(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1);
        } else if (setting instanceof IntSetting intSetting) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) intSetting.set(intSetting.get() - intSetting.step());
            else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) beginNumberDrag(setting,
                    currentSettingsX(layout) + 13.0F, layout.panelWidth() - 26.0F, mouseX);
        } else if (setting instanceof DoubleSetting doubleSetting) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) doubleSetting.set(doubleSetting.get() - doubleSetting.step());
            else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) beginNumberDrag(setting,
                    currentSettingsX(layout) + 13.0F, layout.panelWidth() - 26.0F, mouseX);
        } else if (setting instanceof ColorSetting colorSetting) {
            float fieldX = currentSettingsX(layout) + layout.panelWidth() - 105.0F;
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    && inside(mouseX, mouseY, fieldX, rowY + 7.0F, 68.0F, 17.0F)) {
                focusColor(colorSetting);
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                colorSetting.reset();
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    && mouseY >= rowY + SETTING_ROW_HEIGHT) {
                int channel = (int) ((mouseY - rowY - SETTING_ROW_HEIGHT) / COLOR_CHANNEL_HEIGHT);
                int channels = colorSetting.allowAlpha() ? 4 : 3;
                if (channel >= 0 && channel < channels) {
                    draggingColor = colorSetting;
                    draggingColorChannel = channel;
                    draggingTrackX = currentSettingsX(layout) + 28.0F;
                    draggingTrackWidth = layout.panelWidth() - 76.0F;
                    updateColor(mouseX);
                }
            }
        } else if (setting instanceof KeybindSetting keybindSetting) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) keybindSetting.set(KeybindSetting.NONE);
            else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) capturingKeybind = keybindSetting;
        } else if (setting instanceof StringSetting stringSetting) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) stringSetting.reset();
            else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) focusString(stringSetting);
        } else if (setting instanceof ButtonSetting buttonSetting
                && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            buttonSetting.press();
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (closing) return true;
        float mouseX = (float) logical(event.x());
        float mouseY = (float) logical(event.y());
        if (draggingEspPreview != null) {
            updateEspPreviewDrag(mouseX, mouseY);
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
        if (closing) return true;
        draggingNumber = null;
        draggingColor = null;
        draggingColorChannel = -1;
        draggingEspPreview = null;
        activeScale = configuredScale();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        if (closing) return true;
        float x = (float) logical(mouseX);
        float y = (float) logical(mouseY);
        PopLayout layout = layout(logicalWidth(), logicalHeight());
        if (settingsProgress > 0.65F && inside(x, y,
                currentSettingsX(layout), currentSettingsY(layout),
                layout.panelWidth(), currentSettingsHeight(layout))) {
            settingScroll -= (float) verticalAmount * SCROLL_STEP;
            clampScrolls(layout);
            return true;
        }
        if (selectedCategory != null && categoryProgress > 0.65F
                && inside(x, y, layout.listX(), layout.listY(),
                layout.panelWidth(), layout.listHeight())) {
            moduleScroll -= (float) verticalAmount * SCROLL_STEP;
            clampScrolls(layout);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (closing) return true;
        if (capturingKeybind != null) {
            captureKey(event, capturingKeybind);
            return true;
        }
        if (capturingModule != null) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) capturingModule = null;
            else if (event.key() == GLFW.GLFW_KEY_DELETE || event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                capturingModule.setKeyBind(-1);
                capturingModule = null;
            } else if (event.key() != GLFW.GLFW_KEY_UNKNOWN) {
                capturingModule.setKeyBind(event.key());
                capturingModule = null;
            }
            return true;
        }
        if (hasTextInput() && handleTextKey(event)) return true;
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (settingsRequested) settingsRequested = false;
            else if (categoryRequested) categoryRequested = false;
            else onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private void captureKey(KeyEvent event, KeybindSetting setting) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            capturingKeybind = null;
        } else if (event.key() == GLFW.GLFW_KEY_DELETE || event.key() == GLFW.GLFW_KEY_BACKSPACE) {
            setting.set(KeybindSetting.NONE);
            capturingKeybind = null;
        } else if (event.key() != GLFW.GLFW_KEY_UNKNOWN) {
            setting.set(event.key());
            capturingKeybind = null;
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!hasTextInput() || !event.isAllowedChatCharacter()) {
            return super.charTyped(event);
        }
        insertText(event.codepointAsString());
        return true;
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

    private void focusString(StringSetting setting) {
        editingString = setting;
        editingColor = null;
        colorBeforeEdit = null;
        editText = setting.get();
        editCursor = editText.length();
        selectAll = false;
    }

    private void focusColor(ColorSetting setting) {
        editingString = null;
        editingColor = setting;
        colorBeforeEdit = setting.get();
        editText = setting.hex();
        editCursor = editText.length();
        selectAll = true;
    }

    private boolean hasTextInput() {
        return editingString != null || editingColor != null;
    }

    private void insertText(String inserted) {
        if (inserted == null || inserted.isEmpty()) return;
        StringBuilder clean = new StringBuilder();
        inserted.codePoints().filter(codepoint -> codepoint >= 32 && codepoint != 127)
                .forEach(clean::appendCodePoint);
        if (selectAll) {
            editText = "";
            editCursor = 0;
        }
        int maxLength = editingColor != null ? 9 : 128;
        int remaining = maxLength - editText.length();
        if (remaining <= 0) return;
        String value = clean.length() > remaining ? clean.substring(0, remaining) : clean.toString();
        editText = editText.substring(0, editCursor) + value + editText.substring(editCursor);
        editCursor += value.length();
        selectAll = false;
        applyEditedText();
    }

    private void applyEditedText() {
        if (editingString != null) {
            editingString.set(editText);
        } else if (editingColor != null) {
            Color parsed = editingColor.parseHex(editText);
            if (parsed != null) editingColor.set(parsed);
        }
    }

    private void finishTextEditing() {
        if (editingColor != null) {
            Color parsed = editingColor.parseHex(editText);
            editingColor.set(parsed != null ? parsed : colorBeforeEdit);
        } else {
            applyEditedText();
        }
        editingString = null;
        editingColor = null;
        colorBeforeEdit = null;
        editText = "";
        editCursor = 0;
        selectAll = false;
    }

    private String textWithCursor() {
        int cursor = Math.max(0, Math.min(editCursor, editText.length()));
        return editText.substring(0, cursor) + "|" + editText.substring(cursor);
    }

    private void beginNumberDrag(Setting<?> setting, float x, float width, float mouseX) {
        draggingNumber = setting;
        draggingTrackX = x;
        draggingTrackWidth = width;
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
        if (draggingColor == null || draggingColorChannel < 0) return;
        int value = Math.round(clamp((mouseX - draggingTrackX) / draggingTrackWidth,
                0.0F, 1.0F) * 255.0F);
        Color current = draggingColor.get();
        draggingColor.set(new Color(
                draggingColorChannel == 0 ? value : current.getRed(),
                draggingColorChannel == 1 ? value : current.getGreen(),
                draggingColorChannel == 2 ? value : current.getBlue(),
                draggingColorChannel == 3 ? value : current.getAlpha()));
    }

    @Override
    public void onClose() {
        if (closing || closeFinished) return;
        finishTextEditing();
        capturingModule = null;
        capturingKeybind = null;
        draggingNumber = null;
        draggingColor = null;
        draggingColorChannel = -1;
        settingsRequested = false;
        categoryRequested = false;
        closing = true;
    }

    private boolean completeCloseWhenReady() {
        if (!closing || introProgress > 0.055F
                || categoryProgress > 0.055F || settingsProgress > 0.055F) {
            return false;
        }
        closeFinished = true;
        restoreMenuBlur();
        ConfigManager.INSTANCE.save();
        if (parent != null) minecraft.setScreen(parent);
        else super.onClose();
        return true;
    }

    @Override
    public void removed() {
        restoreMenuBlur();
        ConfigManager.INSTANCE.save();
        super.removed();
    }

    private void applyMenuBlur(int strength) {
        if (originalMenuBlur == null) {
            originalMenuBlur = minecraft.options.getMenuBackgroundBlurriness();
        }
        if (minecraft.options.getMenuBackgroundBlurriness() != strength) {
            minecraft.options.menuBackgroundBlurriness().set(strength);
        }
    }

    private void restoreMenuBlur() {
        if (originalMenuBlur == null) return;
        if (minecraft.options.getMenuBackgroundBlurriness() != originalMenuBlur) {
            minecraft.options.menuBackgroundBlurriness().set(originalMenuBlur);
        }
        originalMenuBlur = null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<Setting<?>> visibleSettings() {
        if (selectedModule == null) return List.of();
        return selectedModule.settings().stream().filter(Setting::visible).toList();
    }

    private float settingsContentHeight() {
        float height = 0.0F;
        for (Setting<?> setting : visibleSettings()) height += settingHeight(setting);
        return height;
    }

    private static float settingHeight(Setting<?> setting) {
        if (setting instanceof IntSetting || setting instanceof DoubleSetting) return NUMBER_ROW_HEIGHT;
        if (setting instanceof ColorSetting colorSetting) {
            return SETTING_ROW_HEIGHT
                    + (colorSetting.allowAlpha() ? 4.0F : 3.0F) * COLOR_CHANNEL_HEIGHT;
        }
        if (MusicPresetPreview.matches(setting)) {
            return SETTING_ROW_HEIGHT + MusicPresetPreview.EXTRA_HEIGHT;
        }
        return SETTING_ROW_HEIGHT;
    }

    private void clampScrolls(PopLayout layout) {
        float moduleContent = selectedCategory == null ? 0.0F
                : ModuleManager.INSTANCE.modulesIn(selectedCategory).size() * MODULE_ROW_HEIGHT;
        moduleScroll = clamp(moduleScroll, 0.0F,
                Math.max(0.0F, moduleContent - (layout.listHeight() - HEADER_HEIGHT)));
        settingScroll = clamp(settingScroll, 0.0F,
                Math.max(0.0F, settingsContentHeight() - (layout.settingsHeight() - HEADER_HEIGHT)));
    }

    private PopLayout layout(float screenWidth, float screenHeight) {
        float maxPairWidth = Math.max(220.0F, screenWidth - 28.0F);
        float panelWidth = clamp(screenWidth * 0.23F, 142.0F, 196.0F);
        panelWidth = Math.min(panelWidth, (maxPairWidth - PANEL_GAP) * 0.5F);
        float listHeight = clamp(screenHeight * 0.61F, 218.0F, 322.0F);
        listHeight = Math.min(listHeight, screenHeight - 24.0F);
        float settingsHeight = clamp(screenHeight * 0.75F, 260.0F, 405.0F);
        settingsHeight = Math.min(settingsHeight, screenHeight - 16.0F);
        float closedListX = (screenWidth - panelWidth) * 0.5F;
        float previewWidth = 0.0F;
        if (selectedModule == ESP.INSTANCE || selectedModule == TargetHud.INSTANCE
                || selectedModule == KillEffect.INSTANCE) {
            float available = screenWidth - 16.0F - panelWidth * 2.0F - PANEL_GAP * 2.0F;
            float desired = selectedModule == TargetHud.INSTANCE
                    ? clamp(panelWidth * 0.95F, 128.0F, 176.0F)
                    : selectedModule == KillEffect.INSTANCE
                    ? clamp(panelWidth * 0.90F, 128.0F, 170.0F)
                    : clamp(panelWidth * 0.76F, 112.0F, 148.0F);
            previewWidth = Math.min(desired,
                    Math.max(0.0F, available));
            if (previewWidth < 92.0F) previewWidth = 0.0F;
        }
        float expandedWidth = panelWidth * 2.0F + PANEL_GAP;
        if (previewWidth > 0.0F) expandedWidth += PANEL_GAP + previewWidth;
        float pairStart = (screenWidth - expandedWidth) * 0.5F;
        float listX = lerp(closedListX, pairStart, smooth(settingsProgress));
        float listY = (screenHeight - listHeight) * 0.5F;
        float settingsX = pairStart + panelWidth + PANEL_GAP;
        float settingsY = (screenHeight - settingsHeight) * 0.5F;
        float previewHeight = previewWidth > 0.0F
                ? Math.min(clamp(settingsHeight * 0.62F, 190.0F, 250.0F), screenHeight - 16.0F)
                : 0.0F;
        float previewX = settingsX + panelWidth + PANEL_GAP;
        float previewY = (screenHeight - previewHeight) * 0.5F;
        return new PopLayout(screenWidth, screenHeight, panelWidth, listHeight,
                settingsHeight, closedListX, listX, listY, settingsX, settingsY,
                previewX, previewY, previewWidth, previewHeight);
    }

    private float currentSettingsX(PopLayout layout) {
        return lerp(layout.closedListX(), layout.settingsX(), smooth(settingsProgress));
    }

    private float currentSettingsY(PopLayout layout) {
        return lerp(layout.listY(), layout.settingsY(), smooth(settingsProgress));
    }

    private float currentSettingsHeight(PopLayout layout) {
        return lerp(layout.listHeight(), layout.settingsHeight(), smooth(settingsProgress));
    }

    private Bubble bubble(Category category, float screenWidth, float screenHeight) {
        int index = 0;
        for (; index < CATEGORIES.length && CATEGORIES[index] != category; index++) {
        }
        float angle = -90.0F + index * (360.0F / CATEGORIES.length);
        double radians = Math.toRadians(angle);
        float radius = ringRadius(screenWidth, screenHeight);
        return new Bubble(screenWidth * 0.5F + (float) Math.cos(radians) * radius,
                screenHeight * 0.5F + (float) Math.sin(radians) * radius);
    }

    private static float ringRadius(float width, float height) {
        return clamp(Math.min(width, height) * 0.27F, 78.0F, 135.0F);
    }

    private float animate(float current, float target, float speed) {
        return current + (target - current) * (1.0F - (float) Math.exp(-speed * delta));
    }

    private float configuredScale() {
        return clamp(ClickGui.INSTANCE.guiScale.get() / 100.0F, 0.65F, 1.25F);
    }

    private float logical(double value) {
        return (float) (value / activeScale);
    }

    private float logicalWidth() {
        return width / activeScale;
    }

    private float logicalHeight() {
        return height / activeScale;
    }

    private static void drawCenteredIcon(Canvas canvas, String icon, float centerX, float centerY,
                                         int color, float size) {
        float width = SkijaUi.iconWidth(icon, size, SkijaUi.IconSet.LUCIDE);
        SkijaUi.icon(canvas, icon, centerX - width * 0.5F, centerY - size * 0.5F,
                size, color, size, SkijaUi.IconSet.LUCIDE);
    }

    private static void drawCenteredText(Canvas canvas, String text, float centerX, float centerY,
                                         int color, float size, boolean bold) {
        float width = bold ? SkijaUi.boldTextWidth(text, size) : SkijaUi.textWidth(text, size);
        if (bold) SkijaUi.boldText(canvas, text, centerX - width * 0.5F,
                centerY - size * 0.5F, size, color, size);
        else SkijaUi.text(canvas, text, centerX - width * 0.5F,
                centerY - size * 0.5F, size, color, size);
    }

    private static void drawRight(Canvas canvas, String text, float right, float y,
                                  float height, int color, float size) {
        SkijaUi.text(canvas, text, right - SkijaUi.textWidth(text, size),
                y, height, color, size);
    }

    private static String fit(String text, float maxWidth, float size, boolean bold) {
        if (text == null || maxWidth <= 0.0F) return "";
        if ((bold ? SkijaUi.boldTextWidth(text, size) : SkijaUi.textWidth(text, size)) <= maxWidth) {
            return text;
        }
        String value = text;
        while (!value.isEmpty()) {
            String result = value + "...";
            float width = bold ? SkijaUi.boldTextWidth(result, size) : SkijaUi.textWidth(result, size);
            if (width <= maxWidth) return result;
            value = value.substring(0, value.length() - 1);
        }
        return "";
    }

    private static String trim(double value) {
        String text = String.format(Locale.ROOT, "%.3f", value);
        while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private static String categoryTitle(Category category) {
        return switch (category) {
            case COMBAT -> "Combat";
            case MISC -> "Misc";
            case RENDER -> "Render";
            case MOVEMENT -> "Movement";
            case PLAYER -> "Player";
            case CLIENT -> "Client";
            case HUD -> "HUD";
        };
    }

    private static String categoryIcon(Category category) {
        return CategoryGlyphs.forCategory(category);
    }

    private static int categoryColor(Category category) {
        return switch (category) {
            case COMBAT -> 0xFF45B8EA;
            case MISC -> 0xFF718AF4;
            case RENDER -> 0xFF51D4C6;
            case MOVEMENT -> 0xFF43C4E2;
            case PLAYER -> 0xFF7781F2;
            case CLIENT -> 0xFF4AD9A6;
            case HUD -> 0xFF9674EE;
        };
    }

    private static int accent() {
        Color color = ClickGui.INSTANCE.accent.get();
        return argb(255, color.getRed(), color.getGreen(), color.getBlue());
    }

    private int theme(int dark, int light) {
        return mix(dark, light, smooth(daylightProgress));
    }

    private int themePanel() {
        return theme(PANEL, LIGHT_PANEL);
    }

    private int themePanelInner() {
        return theme(PANEL_INNER, LIGHT_PANEL_INNER);
    }

    private int themeText() {
        return theme(TEXT, LIGHT_TEXT);
    }

    private int themeTextDim() {
        return theme(TEXT_DIM, LIGHT_TEXT_DIM);
    }

    private int themeTextFaint() {
        return theme(TEXT_FAINT, LIGHT_TEXT_FAINT);
    }

    private int themeTrack() {
        return theme(TRACK, LIGHT_TRACK);
    }

    private int themeValue() {
        return theme(accent(), LIGHT_TEXT);
    }

    private static float distance(float firstX, float firstY, float secondX, float secondY) {
        return (float) Math.hypot(firstX - secondX, firstY - secondY);
    }

    private static boolean inside(float mouseX, float mouseY, float x, float y,
                                  float width, float height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static float smooth(float value) {
        float clamped = clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int withAlpha(int color, int alpha) {
        return (clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private static int multiplyAlpha(int color, float factor) {
        return withAlpha(color, Math.round(((color >>> 24) & 0xFF)
                * clamp(factor, 0.0F, 1.0F)));
    }

    private static int mix(int first, int second, float amount) {
        float value = clamp(amount, 0.0F, 1.0F);
        int a = Math.round(((first >>> 24) & 0xFF)
                + (((second >>> 24) & 0xFF) - ((first >>> 24) & 0xFF)) * value);
        int r = Math.round(((first >>> 16) & 0xFF)
                + (((second >>> 16) & 0xFF) - ((first >>> 16) & 0xFF)) * value);
        int g = Math.round(((first >>> 8) & 0xFF)
                + (((second >>> 8) & 0xFF) - ((first >>> 8) & 0xFF)) * value);
        int b = Math.round((first & 0xFF) + ((second & 0xFF) - (first & 0xFF)) * value);
        return argb(a, r, g, b);
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (clamp(alpha, 0, 255) << 24)
                | (clamp(red, 0, 255) << 16)
                | (clamp(green, 0, 255) << 8)
                | clamp(blue, 0, 255);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Bubble(float x, float y) {
    }

    private enum EspPreviewPart {
        PLAYER,
        HEALTH,
        ARMOR,
        EQUIPMENT,
        EFFECTS
    }

    private record EspPreviewHit(EspPreviewPart part, float x, float y,
                                 float width, float height) {
    }

    private record PopLayout(float screenWidth, float screenHeight, float panelWidth,
                             float listHeight, float settingsHeight, float closedListX,
                             float listX, float listY, float settingsX, float settingsY,
                             float previewX, float previewY,
                             float previewWidth, float previewHeight) {
    }
}
