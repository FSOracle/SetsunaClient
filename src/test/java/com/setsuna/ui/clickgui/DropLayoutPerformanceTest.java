package com.setsuna.ui.clickgui;

import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.Setting;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.ui.hud.HUD;
import com.setsuna.ui.hud.HUDModuel;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropLayoutPerformanceTest {
    @Test
    void collapsedPanelDoesNotMeasureExpandedModuleSettings() throws Exception {
        AtomicInteger visits = new AtomicInteger();
        Object panel = panel(modules(24, 12, visits));
        for (Object entry : entries(panel)) expandEntry(entry);
        render(panel);
        assertEquals(0, visits.get(), "A closed category must not lay out its hidden settings");
    }

    @Test
    void openPanelDoesNotMeasureCollapsedModuleSettings() throws Exception {
        AtomicInteger visits = new AtomicInteger();
        Object panel = panel(modules(24, 12, visits));
        call(panel, "expandImmediately", new Class<?>[0]);
        render(panel);
        assertEquals(0, visits.get(), "A module row must not measure its collapsed details");
    }

    @Test
    void layoutRefreshesWhenVisibleSettingsChangeAndClampsScroll() throws Exception {
        AtomicInteger visits = new AtomicInteger();
        List<Module> modules = modules(1, 24, visits);
        Object panel = panel(modules);
        call(panel, "expandImmediately", new Class<?>[0]);
        expandEntry(entries(panel).getFirst());
        render(panel);
        set(panel, "scroll", 180.0F);
        render(panel);
        assertEquals(180.0F, (float) get(panel, "scroll"));

        for (var setting : modules.getFirst().settings()) setting.visibleWhen(() -> false);
        render(panel);
        assertEquals(0.0F, (float) get(panel, "scroll"));
        assertEquals(40.0F, (float) call(panel, "contentHeight", new Class<?>[0]));
    }

    @Test
    void settingsBelowViewportAreMeasuredAtMostOncePerFrame() throws Exception {
        AtomicInteger visits = new AtomicInteger();
        List<Module> modules = modules(1, 24, visits);
        Object panel = panel(modules);
        call(panel, "expandImmediately", new Class<?>[0]);
        expandEntry(entries(panel).getFirst());
        set(panel, "y", 424.0F);
        render(panel);
        // The viewport ends at the module row, so all settings are clipped out.
        assertTrue(visits.get() <= 24, "Hidden detail traversal count: " + visits.get());
    }

    @Test
    void closingDetailsKeepTheirHeightUntilTheAnimationFinishes() throws Exception {
        AtomicInteger visits = new AtomicInteger();
        Object panel = panel(modules(1, 4, visits));
        call(panel, "expandImmediately", new Class<?>[0]);
        Object entry = entries(panel).getFirst();
        set(entry, "expandProgress", 0.5F);
        set(get(panel, "this$0"), "animationDelta", 0.0F);
        render(panel);
        assertEquals(70.0F, (float) call(panel, "contentHeight", new Class<?>[0]));
        assertTrue(visits.get() > 0);

        set(entry, "expandProgress", 0.0F);
        visits.set(0);
        render(panel);
        assertEquals(20.0F, (float) call(panel, "contentHeight", new Class<?>[0]));
        assertEquals(0, visits.get());
    }

    @Test
    @SuppressWarnings("unchecked")
    void collapsedHudComponentSettingsAreSkippedInsideAnExpandedHudModule() throws Exception {
        AtomicInteger visits = new AtomicInteger();
        HUDModuel component = HUD.INSTANCE.components().getFirst();
        BooleanSetting probe = new BooleanSetting("Layout Probe", false).visibleWhen(() -> {
            visits.incrementAndGet();
            return true;
        });
        component.settings().add(probe);
        try {
            Object panel = panel(List.of(HUD.INSTANCE));
            expandEntry(entries(panel).getFirst());
            call(panel, "contentHeight", new Class<?>[0]);
            assertEquals(0, visits.get());

            Map<HUDModuel, Float> progress = (Map<HUDModuel, Float>) get(panel, "hudExpandProgress");
            progress.put(component, 0.5F);
            call(panel, "contentHeight", new Class<?>[0]);
            assertEquals(1, visits.get());

            progress.put(component, 0.0F);
            call(panel, "contentHeight", new Class<?>[0]);
            assertEquals(1, visits.get());
        } finally {
            component.settings().remove(probe);
        }
    }

    @Test
    void colorEditorUpdatesTheScrollRangeOnTheNextFrame() throws Exception {
        List<Module> modules = modules(1, 12, new AtomicInteger());
        ColorSetting color = new ColorSetting("Color", Color.WHITE, true);
        modules.getFirst().settings().add(color);
        Object panel = panel(modules);
        call(panel, "expandImmediately", new Class<?>[0]);
        expandEntry(entries(panel).getFirst());
        set(panel, "y", 300.0F);
        set(panel, "scroll", 10000.0F);
        render(panel);
        float collapsedScroll = (float) get(panel, "scroll");
        ColorPickerState.toggle(color);
        try {
            set(panel, "scroll", 10000.0F);
            render(panel);
            assertEquals(collapsedScroll + 62.0F, (float) get(panel, "scroll"));
        } finally {
            ColorPickerState.toggle(color);
        }
        render(panel);
        assertEquals(collapsedScroll, (float) get(panel, "scroll"));
    }

    @Test
    void hudGlobalSettingsAreSeparateFromComponentToggles() {
        List<Setting<?>> controls = HUD.INSTANCE.controllerSettings();
        assertTrue(controls.containsAll(List.of(HUD.INSTANCE.language, HUD.INSTANCE.globalBlur,
                HUD.INSTANCE.globalBlurStrength, HUD.INSTANCE.mainMenuStyle)));
        for (HUDModuel component : HUD.INSTANCE.components()) {
            assertFalse(controls.contains(HUD.INSTANCE.componentSetting(component)));
        }
        assertEquals(HUD.INSTANCE.settings().size(), controls.size() + HUD.INSTANCE.components().size());
    }

    @Test
    void hudGlobalSettingsUseTheSameHeightForLayoutAndDrawing() throws Exception {
        boolean originalBlur = HUD.INSTANCE.globalBlur.get();
        Object panel = panel(List.of(HUD.INSTANCE));
        Object entry = entries(panel).getFirst();
        expandEntry(entry);
        try {
            for (boolean blur : new boolean[]{true, false}) {
                HUD.INSTANCE.globalBlur.set(blur);
                float controlsHeight = 0.0F;
                for (Setting<?> setting : HUD.INSTANCE.controllerSettings()) {
                    if (setting.visible()) controlsHeight += settingHeight(setting);
                }
                float detailsHeight = 20.0F + controlsHeight + HUD.INSTANCE.components().size() * 20.0F;
                assertEquals(20.0F + detailsHeight, (float) call(panel, "contentHeight", new Class<?>[0]));
                try (Surface surface = Surface.makeRasterN32Premul(640, 800)) {
                    float rendered = (float) call(panel, "renderEntryDetails",
                            new Class<?>[]{Canvas.class, entry.getClass(), float.class, float.class, float.class},
                            surface.getCanvas(), entry, 0.0F, 0.0F, 800.0F);
                    assertEquals(detailsHeight, rendered);
                }
            }
        } finally {
            HUD.INSTANCE.globalBlur.set(originalBlur);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void hudGlobalControlsAndFirstComponentRemainClickableWhenBlurStrengthHides() throws Exception {
        boolean originalBlur = HUD.INSTANCE.globalBlur.get();
        var originalStyle = HUD.INSTANCE.mainMenuStyle.get();
        int originalStrength = HUD.INSTANCE.globalBlurStrength.get();
        Object panel = panel(List.of(HUD.INSTANCE));
        expandEntry(entries(panel).getFirst());
        call(panel, "expandImmediately", new Class<?>[0]);
        try {
            HUD.INSTANCE.globalBlur.set(true);
            HUD.INSTANCE.globalBlurStrength.set(8);
            render(panel);
            click(panel, controllerY(HUD.INSTANCE.globalBlurStrength) + 5.0F, 1);
            assertEquals(7, HUD.INSTANCE.globalBlurStrength.get());

            click(panel, controllerY(HUD.INSTANCE.globalBlur) + 5.0F, 0);
            assertFalse(HUD.INSTANCE.globalBlur.get());
            render(panel);
            click(panel, controllerY(HUD.INSTANCE.mainMenuStyle) + 5.0F, 0);
            assertEquals(originalStyle, HUD.INSTANCE.mainMenuStyle.get());

            float componentY = controllerY(null);
            click(panel, componentY + 5.0F, 1);
            Set<HUDModuel> expanded = (Set<HUDModuel>) get(panel, "expandedHudComponents");
            assertTrue(expanded.contains(HUD.INSTANCE.components().getFirst()));
            assertEquals(1, expanded.size());
        } finally {
            HUD.INSTANCE.globalBlur.set(originalBlur);
            HUD.INSTANCE.mainMenuStyle.set(originalStyle);
            HUD.INSTANCE.globalBlurStrength.set(originalStrength);
        }
    }

    @Test
    void collapsedHudDetailsStillSkipGlobalSettings() throws Exception {
        AtomicInteger visits = new AtomicInteger();
        HUD.INSTANCE.mainMenuStyle.visibleWhen(() -> {
            visits.incrementAndGet();
            return true;
        });
        try {
            Object panel = panel(List.of(HUD.INSTANCE));
            render(panel);
            call(panel, "expandImmediately", new Class<?>[0]);
            render(panel);
            assertEquals(0, visits.get());
            expandEntry(entries(panel).getFirst());
            render(panel);
            assertTrue(visits.get() > 0);
        } finally {
            HUD.INSTANCE.mainMenuStyle.visibleWhen(() -> true);
        }
    }

    private static float controllerY(Setting<?> target) throws Exception {
        float y = 12.0F + 24.0F + 20.0F + 20.0F;
        for (Setting<?> setting : HUD.INSTANCE.controllerSettings()) {
            if (!setting.visible()) continue;
            if (setting == target) break;
            y += settingHeight(setting);
        }
        return y;
    }

    private static float settingHeight(Setting<?> setting) throws Exception {
        Method method = WindowClickGuiScreen.class.getDeclaredMethod("settingHeight", Setting.class);
        method.setAccessible(true);
        return (float) method.invoke(null, setting);
    }

    private static void click(Object panel, float y, int button) throws Exception {
        assertTrue((boolean) call(panel, "mouseClicked",
                new Class<?>[]{float.class, float.class, int.class, float.class},
                20.0F, y, button, 480.0F));
    }

    private static List<Module> modules(int count, int settings, AtomicInteger visits) {
        List<Module> modules = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Module module = new Module("Sample " + i, Category.COMBAT) {};
            for (int j = 0; j < settings; j++) {
                module.settings().add(new BooleanSetting("Setting " + j, false).visibleWhen(() -> {
                    visits.incrementAndGet();
                    return true;
                }));
            }
            modules.add(module);
        }
        return modules;
    }

    private static Object panel(List<Module> modules) throws Exception {
        // Minecraft owns Screen construction; the painters only need these local UI fields.
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        WindowClickGuiScreen screen = (WindowClickGuiScreen) ((Unsafe) unsafeField.get(null))
                .allocateInstance(WindowClickGuiScreen.class);
        set(screen, "animationDelta", 1.0F);
        set(screen, "toggleAnimations", new HashMap<>());
        set(screen, "activeScale", 1.0F);
        screen.width = 640;
        screen.height = 480;
        Class<?> type = Class.forName(WindowClickGuiScreen.class.getName() + "$ModulePanel");
        Constructor<?> constructor = type.getDeclaredConstructor(WindowClickGuiScreen.class,
                Category.class, List.class, float.class, float.class);
        constructor.setAccessible(true);
        return constructor.newInstance(screen, Category.COMBAT, modules, 12.0F, 12.0F);
    }

    private static void render(Object panel) throws Exception {
        try (Surface surface = Surface.makeRasterN32Premul(640, 480)) {
            call(panel, "clampPosition", new Class<?>[]{float.class, float.class}, 640.0F, 480.0F);
            call(panel, "render", new Class<?>[]{Canvas.class, float.class}, surface.getCanvas(), 480.0F);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> entries(Object panel) throws Exception {
        return (List<Object>) get(panel, "entries");
    }

    private static void expandEntry(Object entry) throws Exception {
        set(entry, "expanded", true);
        set(entry, "expandProgress", 1.0F);
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(name, types);
                method.setAccessible(true);
                return method.invoke(target, args);
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static Field field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object get(Object target, String name) throws Exception {
        return field(target, name).get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        field(target, name).set(target, value);
    }
}
