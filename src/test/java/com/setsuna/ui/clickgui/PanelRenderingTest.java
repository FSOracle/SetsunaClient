package com.setsuna.ui.clickgui;

import com.setsuna.i18n.LocalizedText;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.modules.render.ESP;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.Setting;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.StringSetting;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.EncodedImageFormat;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.ArrayList;
import sun.misc.Unsafe;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offscreen checks use the production drawing methods without a game session. */
class PanelRenderingTest {
    @Test
    void rendersModuleAndControlSurfacesAtDesktopAndCompactSizes() throws Exception {
        var previousLanguage = LocalizedText.languageOverride();
        try {
            for (var language : new LocalizedText.LanguageOverride[]{
                    LocalizedText.LanguageOverride.ENGLISH, LocalizedText.LanguageOverride.CHINESE}) {
                LocalizedText.setLanguageOverride(language);
                render(960, 540, language.name().toLowerCase());
                render(427, 240, language.name().toLowerCase());
            }
        } finally {
            LocalizedText.setLanguageOverride(previousLanguage);
        }
    }

    private void render(int width, int height, String language) throws Exception {
        // Screen's constructor requires a live Minecraft window. These painters only use UI state.
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        ClickGuiScreen screen = (ClickGuiScreen) ((Unsafe) unsafeField.get(null)).allocateInstance(ClickGuiScreen.class);
        set(screen, "search", "");
        set(screen, "transitions", new HashMap<String, Float>());
        set(screen, "categoryHighlightY", Float.NaN);
        set(screen, "selectedCategory", Category.COMBAT);
        set(screen, "animationDelta", 1.0F);
        ClickGuiLayout layout = ClickGuiLayout.forViewport(width, height);
        try (Surface surface = Surface.makeRasterN32Premul(width * 2, height * 2)) {
            Canvas canvas = surface.getCanvas();
            canvas.clear(0xFF272D30);
            canvas.scale(2, 2);
            float scale = layout.fittedScale(width, height, 1);
            canvas.translate(width * 0.5F, height * 0.5F);
            canvas.scale(scale, scale);
            canvas.translate(-width * 0.5F, -height * 0.5F);
            SkijaUi.rounded(canvas, layout.x(), layout.y(), layout.width(), layout.height(), 8, UiTheme.SURFACE);
            call(screen, "drawColumnSurfaces", new Class<?>[]{Canvas.class, ClickGuiLayout.class}, canvas, layout);
            call(screen, "renderTopBar", new Class<?>[]{Canvas.class, ClickGuiLayout.class}, canvas, layout);
            call(screen, "renderCategoryRail", new Class<?>[]{Canvas.class, ClickGuiLayout.class}, canvas, layout);
            String[] modules = {"KillAura", "Velocity", "Criticals", "Backtrack", "AntiBot", "AutoHead"};
            for (int i = 0; i < modules.length; i++) {
                Module module = new SampleModule(modules[i], i % 2 == 0);
                call(screen, "renderPanelModuleCard",
                        new Class<?>[]{Canvas.class, Module.class, float.class, float.class, float.class, float.class, float.class},
                        canvas, module, layout.moduleCardX(i), layout.moduleCardY(i, 0), layout.moduleCardWidth(), 50.0F, 1.0F);
            }
            save(surface, "panel-" + language + "-" + width + ".png");

            canvas.clear(0xFF272D30);
            SkijaUi.rounded(canvas, layout.x(), layout.y(), layout.width(), layout.height(), 8, UiTheme.SURFACE);
            call(screen, "drawColumnSurfaces", new Class<?>[]{Canvas.class, ClickGuiLayout.class}, canvas, layout);
            call(screen, "renderTopBar", new Class<?>[]{Canvas.class, ClickGuiLayout.class}, canvas, layout);
            call(screen, "renderCategoryRail", new Class<?>[]{Canvas.class, ClickGuiLayout.class}, canvas, layout);
            SampleModule hud = new SampleModule("HUD", true);
            Setting<?>[] settings = {new BooleanSetting("Global Blur", true),
                    new DoubleSetting("Global Blur Strength", 8, 1, 16, 1),
                    new ColorSetting("Background Color", new Color(30, 36, 38, 172), true),
                    new StringSetting("Name", "Setsuna")};
            for (Setting<?> setting : settings) hud.settings().add(setting);
            hud.bindI18n();
            set(screen, "selectedModule", hud);
            set(screen, "panelEspHits", new ArrayList<>());
            set(screen, "settingsPage", true);
            set(screen, "settingsTransition", 1.0F);
            call(screen, "renderDetailColumn", new Class<?>[]{Canvas.class, ClickGuiLayout.class}, canvas, layout);
            save(surface, "panel-controls-" + language + "-" + width + ".png");
            set(screen, "selectedModule", ESP.INSTANCE);
            assertTrue((Boolean) call(screen, "usesSidePreview", new Class<?>[]{ClickGuiLayout.class}, layout));
            assertTrue((Float) call(screen, "panelSettingsWidth", new Class<?>[]{ClickGuiLayout.class}, layout) >= 180);
            assertTrue((Float) call(screen, "panelSettingsHeight", new Class<?>[]{ClickGuiLayout.class}, layout) >= 80);
        }
    }

    private static void save(Surface surface, String name) throws Exception {
        Path output = Path.of("build", "ui-previews", name);
        Files.createDirectories(output.getParent());
        try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData(EncodedImageFormat.PNG)) {
            assertTrue(data != null && data.getSize() > 2000, "Rendered panel should contain visible content");
            Files.write(output, data.getBytes());
        }
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, arguments);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class SampleModule extends Module {
        private final boolean active;
        SampleModule(String name, boolean active) {
            super(name, Category.COMBAT);
            this.active = active;
        }
        @Override public boolean isEnabled() { return active; }
    }
}
