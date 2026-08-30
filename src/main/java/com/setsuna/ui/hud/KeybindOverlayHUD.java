package com.setsuna.ui.hud;

import com.setsuna.event.events.Render2DEvent;
import com.setsuna.module.Module;
import com.setsuna.module.ModuleManager;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.ui.UiTheme;
import com.setsuna.util.client.InputBind;
import com.setsuna.util.client.KeybindUtils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Movable list of every module that currently has a key or mouse binding. */
public final class KeybindOverlayHUD extends EpsilonHudModule {

    public static final KeybindOverlayHUD INSTANCE = new KeybindOverlayHUD();

    private static final float BASE_WIDTH = 132.0F;
    private static final float HEADER_HEIGHT = 19.0F;
    private static final float ROW_HEIGHT = 13.0F;
    private static final float PADDING = 6.0F;
    private static final float VERTICAL_PADDING = 3.5F;

    public final DoubleSetting scale = add(new DoubleSetting("Scale", 1.0, 0.65, 1.8, 0.05));
    public final BooleanSetting background = add(new BooleanSetting("Background", true));
    public final ColorSetting backgroundColor = add(new ColorSetting("Background Color",
            new Color(UiTheme.withAlpha(UiTheme.SURFACE, 224), true), true)
            .visibleWhen(background::get));
    public final BooleanSetting blur = add(new BooleanSetting("Blur", false));
    public final IntSetting blurStrength = add(new IntSetting("Blur Strength", 6, 1, 16, 1)
            .visibleWhen(blur::get));
    public final BooleanSetting border = add(new BooleanSetting("Border", true));
    public final DoubleSetting borderWidth = add(new DoubleSetting(
            "Border Width", 1.2, 0.5, 4.0, 0.1).visibleWhen(border::get));
    public final DoubleSetting borderRadius = add(new DoubleSetting(
            "Border Radius", 5.0, 0.0, 14.0, 0.5)
            .visibleWhen(() -> background.get() || border.get() || blur.get()));
    public final EnumSetting<HudRenderUtil.BorderMode> borderMode = add(
            new EnumSetting<>("Border Mode", HudRenderUtil.BorderMode.Single)
                    .visibleWhen(border::get));
    public final ColorSetting borderColor = add(new ColorSetting("Border Color",
            new Color(75, 155, 255), false).visibleWhen(() -> border.get()
            && borderMode.is(HudRenderUtil.BorderMode.Single)));
    public final ColorSetting borderStart = add(new ColorSetting("Border Start",
            new Color(66, 225, 255), false).visibleWhen(() -> border.get()
            && borderMode.is(HudRenderUtil.BorderMode.Gradient)));
    public final ColorSetting borderEnd = add(new ColorSetting("Border End",
            new Color(126, 92, 255), false).visibleWhen(() -> border.get()
            && borderMode.is(HudRenderUtil.BorderMode.Gradient)));
    public final BooleanSetting shadow = add(new BooleanSetting("Shadow", false));
    public final IntSetting shadowStrength = add(new IntSetting("Shadow Strength", 10, 2, 24, 1)
            .visibleWhen(shadow::get));

    private final Map<Module, RowAnimation> rowAnimations = new IdentityHashMap<>();
    private long lastFrameNanos = System.nanoTime();

    private KeybindOverlayHUD() {
        super("Keybind Overlay", 18, 330, BASE_WIDTH, HEADER_HEIGHT + ROW_HEIGHT);
    }

    @Override
    protected void renderHud(Render2DEvent event) {
        List<BoundModule> modules = boundModules();
        float delta = frameDelta();
        updateAnimations(modules, delta);
        float s = scale.get().floatValue();
        float rowHeight = ROW_HEIGHT * s;
        float headerHeight = HEADER_HEIGHT * s;
        float verticalPadding = VERTICAL_PADDING * s;
        int maximumRows = Math.max(1, (int) ((event.height() - headerHeight
                - verticalPadding * 2.0F - 12.0F) / rowHeight));
        if (modules.size() > maximumRows) {
            modules = modules.subList(0, maximumRows);
        }

        float nameSize = 7.2F * s;
        float bindSize = 6.8F * s;
        float width = BASE_WIDTH * s;
        for (BoundModule module : modules) {
            width = Math.max(width, PADDING * 3.0F * s
                    + SkijaUi.textWidth(module.name(), nameSize)
                    + SkijaUi.textWidth(module.bind(), bindSize));
        }
        width = Math.min(width, Math.max(4.0F, event.width() - 12.0F));
        float height = verticalPadding * 2.0F + headerHeight
                + Math.max(1, modules.size()) * rowHeight;
        float x = renderX(event, width);
        float y = renderY(event, height);
        float radius = Math.min(borderRadius.get().floatValue() * s, height * 0.5F);
        updateBounds(width, height);
        if (!HudFusionManager.isFused(this, event)) {
            if (blur.get()) {
                HudRenderUtil.blur(event.canvas(), x, y, width, height, radius,
                        blurStrength.get() * 0.55F);
            }
            if (shadow.get()) {
                SkijaUi.dropShadowRounded(event.canvas(), x, y, width, height,
                        radius, 2.0F * s, shadowStrength.get() * s, 0x9C000000);
            }
            if (background.get()) {
                HudRenderUtil.coloredSurface(event.canvas(), x, y, width, height, radius,
                        backgroundColor.argb(), HudFusionManager.Edges.NONE);
            }
            if (border.get()) {
                HudRenderUtil.border(event.canvas(), x, y, width, height, radius,
                        borderWidth.get().floatValue() * s, 1.0F, borderMode.get(),
                        borderColor.argb(), borderStart.argb(), borderEnd.argb(), 255);
            }
        }

        float contentY = y + verticalPadding;
        SkijaUi.boldText(event.canvas(), "KEYBINDS", x + PADDING * s, contentY,
                headerHeight, UiTheme.TEXT, 7.4F * s);
        SkijaUi.fill(event.canvas(), x + PADDING * s,
                contentY + headerHeight - 1.0F * s,
                width - PADDING * 2.0F * s, 1.0F * s, UiTheme.withAlpha(UiTheme.accent(), 150));

        if (modules.isEmpty()) {
            SkijaUi.text(event.canvas(), "No keybinds", x + PADDING * s,
                    contentY + headerHeight,
                    rowHeight, UiTheme.TEXT_FAINT, bindSize);
            return;
        }

        float available = width - PADDING * 2.0F * s;
        for (int index = 0; index < modules.size(); index++) {
            BoundModule module = modules.get(index);
            float enabledProgress = smooth(rowAnimations.get(module.module()).progress);
            float rowY = contentY + headerHeight + index * rowHeight;
            float bindWidth = SkijaUi.textWidth(module.bind(), bindSize);
            String name = HudRenderUtil.fit(module.name(),
                    Math.max(8.0F, available - bindWidth - 8.0F * s), nameSize, false);
            int nameColor = mix(UiTheme.TEXT_MUTED, UiTheme.TEXT, enabledProgress);
            int bindColor = mix(UiTheme.TEXT_FAINT, UiTheme.accent(), enabledProgress);
            float nameX = x + PADDING * s + (1.0F - enabledProgress) * 1.5F * s;
            SkijaUi.text(event.canvas(), name, nameX, rowY,
                    rowHeight, nameColor, nameSize);
            SkijaUi.text(event.canvas(), module.bind(), x + width - PADDING * s - bindWidth,
                    rowY, rowHeight, bindColor, bindSize);
        }
    }

    private static List<BoundModule> boundModules() {
        List<BoundModule> result = new ArrayList<>();
        for (Module module : ModuleManager.INSTANCE.modules()) {
            if (module.bind().isUnbound()) continue;
            result.add(new BoundModule(module, module.displayName(), keyText(module.bind())));
        }
        result.sort(Comparator.comparing(BoundModule::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private void updateAnimations(List<BoundModule> modules, float delta) {
        rowAnimations.keySet().removeIf(module -> module.bind().isUnbound());
        for (BoundModule bound : modules) {
            boolean enabled = bound.module().isEnabled();
            RowAnimation animation = rowAnimations.computeIfAbsent(bound.module(),
                    ignored -> new RowAnimation(enabled ? 1.0F : 0.0F));
            float target = enabled ? 1.0F : 0.0F;
            animation.progress += (target - animation.progress)
                    * (1.0F - (float) Math.exp(-12.0F * delta));
            if (Math.abs(target - animation.progress) < 0.001F) {
                animation.progress = target;
            }
        }
    }

    private float frameDelta() {
        long now = System.nanoTime();
        float delta = Math.min(0.1F, Math.max(0.0F, (now - lastFrameNanos) / 1_000_000_000.0F));
        lastFrameNanos = now;
        return delta;
    }

    private static String keyText(InputBind bind) {
        StringBuilder text = new StringBuilder();
        for (InputBind.Modifier modifier : bind.modifiers()) {
            if (!text.isEmpty()) text.append(" + ");
            text.append(modifier.displayName());
        }
        if (!text.isEmpty()) text.append(" + ");
        return text.append(KeybindUtils.format(bind.keyBind())).toString();
    }

    private static float smooth(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static int mix(int from, int to, float amount) {
        float value = Math.max(0.0F, Math.min(1.0F, amount));
        int alpha = mixChannel(from >>> 24, to >>> 24, value);
        int red = mixChannel(from >>> 16, to >>> 16, value);
        int green = mixChannel(from >>> 8, to >>> 8, value);
        int blue = mixChannel(from, to, value);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int mixChannel(int from, int to, float amount) {
        int first = from & 0xFF;
        int second = to & 0xFF;
        return Math.round(first + (second - first) * amount);
    }

    @Override
    public int editorColor() {
        return UiTheme.accent();
    }

    @Override
    public boolean supportsHudFusion() {
        return true;
    }

    @Override
    public boolean hudFusionBorderEnabled() {
        return border.get();
    }

    @Override
    public boolean hudFusionBackgroundEnabled() {
        return background.get();
    }

    @Override
    public HudFusionManager.FusionStyle hudFusionStyle() {
        float s = scale.get().floatValue();
        return new HudFusionManager.FusionStyle(background.get(),
                backgroundColor.argb(), blur.get(),
                blurStrength.get() * 0.55F, shadow.get(), shadowStrength.get() * s,
                border.get(), borderRadius.get().floatValue() * s,
                borderWidth.get().floatValue() * s, borderMode.get(),
                borderColor.argb(), borderStart.argb(), borderEnd.argb());
    }

    private record BoundModule(Module module, String name, String bind) {
    }

    private static final class RowAnimation {
        private float progress;

        private RowAnimation(float progress) {
            this.progress = progress;
        }
    }
}
