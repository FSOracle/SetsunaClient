package com.setsuna.ui.screen;

import com.setsuna.Setsuna;
import com.setsuna.module.modules.ClickGui;
import com.setsuna.module.modules.ColorPresets;
import com.setsuna.render.SkijaUi;
import com.setsuna.ui.clickgui.DropGridRenderer;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Shader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.Locale;

/** Full-screen dual palette editor for the global visual color preset. */
public final class ColorPresetScreen extends AbstractSkijaScreen {

    private static final int[] WHEEL_COLORS = {
            0xFFFF3B3B, 0xFFFFD83B, 0xFF45E06F, 0xFF42E5E9,
            0xFF4583FF, 0xFFB34DFF, 0xFFFF3BBA, 0xFFFF3B3B
    };
    private static final int[] SWATCHES = {
            0xFFFF3B3B, 0xFFFF8A34, 0xFFFFD83B, 0xFF45E06F,
            0xFF42E5E9, 0xFF4583FF, 0xFF884DFF, 0xFFFF3BBA,
            0xFFFFFFFF, 0xFF9AA2B0, 0xFF3B404A, 0xFF050607
    };
    private static final Paint WHEEL_PAINT = new Paint().setAntiAlias(true).setDither(true);
    private static final Paint CIRCLE_PAINT = new Paint().setAntiAlias(true);

    private final Screen parent;
    private final UiControls.TextInput primaryHex = new UiControls.TextInput(
            7, false, ColorPresetScreen::isHexCharacter);
    private final UiControls.TextInput secondaryHex = new UiControls.TextInput(
            7, false, ColorPresetScreen::isHexCharacter);
    private long lastFrame = System.nanoTime();
    private float intro;
    private float toggleMotion;
    private float backgroundToggleMotion;
    private float primaryHue;
    private float primarySaturation;
    private float secondaryHue;
    private float secondarySaturation;
    private boolean initializedSelection;
    private boolean draggingPrimary;
    private boolean draggingSecondary;
    private boolean closing;
    private boolean closeReady;
    private Color primaryBeforeEdit;
    private Color secondaryBeforeEdit;
    private Integer originalMenuBlur;

    public ColorPresetScreen(Screen parent) {
        super(Component.literal("Color Presets"));
        this.parent = parent;
        primaryHex.setPlaceholder("#RRGGBB");
        secondaryHex.setPlaceholder("#RRGGBB");
    }

    @Override
    protected void init() {
        lastFrame = System.nanoTime();
        intro = 0.0F;
        closing = false;
        closeReady = false;
        draggingPrimary = false;
        draggingSecondary = false;
        syncSelectionImmediately();
        syncHexInputs();
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
    protected void drawScreen(Canvas canvas) {
        float delta = frameDelta();
        intro = approach(intro, closing ? 0.0F : 1.0F, delta, closing ? 13.5F : 8.5F);
        toggleMotion = approach(toggleMotion, ColorPresets.INSTANCE.active.get() ? 1.0F : 0.0F,
                delta, 12.0F);
        backgroundToggleMotion = approach(backgroundToggleMotion,
                ColorPresets.INSTANCE.applyBackgroundColors.get() ? 1.0F : 0.0F,
                delta, 12.0F);
        updateAnimatedSelection(delta);

        Layout layout = layout();
        updateInputBounds(layout);
        syncHexInputs();
        drawBackdrop(canvas, intro);
        drawHeader(canvas, layout, intro);
        drawWheelSection(canvas, layout.primary(), true, intro, delta);
        drawWheelSection(canvas, layout.secondary(), false, delayed(intro, 0.10F), delta);
        drawFooter(canvas, layout, intro);

        if (closing && intro <= 0.015F) closeReady = true;
    }

    private void drawBackdrop(Canvas canvas, float progress) {
        SkijaUi.fill(canvas, 0.0F, 0.0F, width, height,
                alpha(0x4602080C, progress));
        DropGridRenderer.drawFull(canvas, width, height, progress,
                ColorPresets.INSTANCE.primary.raw().getRGB());
    }

    private void drawHeader(Canvas canvas, Layout layout, float progress) {
        int text = alpha(UiControls.TEXT, progress);
        int muted = alpha(UiControls.TEXT_MUTED, progress);
        SkijaUi.boldText(canvas, "COLOR PRESETS", 24.0F, 17.0F, 18.0F, text, 11.0F);
        SkijaUi.text(canvas, Setsuna.clientName().toUpperCase(Locale.ROOT) + " VISUAL SYSTEM",
                24.0F, 34.0F, 12.0F, muted, 6.5F);

        UiControls.Box back = layout.back();
        boolean backHover = back.contains(mouseX, mouseY);
        SkijaUi.rounded(canvas, back.x(), back.y(), back.width(), back.height(), 5.0F,
                alpha(backHover ? 0x28FFFFFF : 0x12FFFFFF, progress));
        UiControls.centeredText(canvas, "Back", back, text, true);

        UiControls.Box toggle = layout.toggle();
        int track = mix(0xFF252A34, ColorPresets.INSTANCE.primary.raw().getRGB(), toggleMotion);
        SkijaUi.rounded(canvas, toggle.x(), toggle.y(), toggle.width(), toggle.height(),
                toggle.height() * 0.5F, alpha(track, progress));
        float knobSize = toggle.height() - 6.0F;
        float knobX = toggle.x() + 3.0F + (toggle.width() - knobSize - 6.0F) * toggleMotion;
        SkijaUi.rounded(canvas, knobX, toggle.y() + 3.0F, knobSize, knobSize,
                knobSize * 0.5F, alpha(0xFFFFFFFF, progress));
        String state = ColorPresets.INSTANCE.active.get() ? "ACTIVE" : "INACTIVE";
        float stateWidth = SkijaUi.boldTextWidth(state, 6.5F);
        SkijaUi.boldText(canvas, state, toggle.x() - stateWidth - 10.0F, toggle.y(),
                toggle.height(), ColorPresets.INSTANCE.active.get() ? text : muted, 6.5F);

        UiControls.Box backgroundToggle = layout.backgroundToggle();
        String backgroundLabel = "BACKGROUND COLORS";
        float backgroundLabelWidth = SkijaUi.boldTextWidth(backgroundLabel, 6.5F);
        SkijaUi.boldText(canvas, backgroundLabel,
                backgroundToggle.x() - backgroundLabelWidth - 10.0F,
                backgroundToggle.y(), backgroundToggle.height(),
                ColorPresets.INSTANCE.applyBackgroundColors.get() ? text : muted, 6.5F);
        int backgroundTrack = mix(0xFF252A34,
                ColorPresets.INSTANCE.primary.raw().getRGB(), backgroundToggleMotion);
        SkijaUi.rounded(canvas, backgroundToggle.x(), backgroundToggle.y(),
                backgroundToggle.width(), backgroundToggle.height(),
                backgroundToggle.height() * 0.5F, alpha(backgroundTrack, progress));
        float backgroundKnobSize = backgroundToggle.height() - 6.0F;
        float backgroundKnobX = backgroundToggle.x() + 3.0F
                + (backgroundToggle.width() - backgroundKnobSize - 6.0F)
                * backgroundToggleMotion;
        SkijaUi.rounded(canvas, backgroundKnobX, backgroundToggle.y() + 3.0F,
                backgroundKnobSize, backgroundKnobSize, backgroundKnobSize * 0.5F,
                alpha(0xFFFFFFFF, progress));
    }

    private void drawWheelSection(Canvas canvas, WheelLayout wheel, boolean primary,
                                  float progress, float delta) {
        float eased = easeOutCubic(progress);
        float radius = wheel.radius() * eased;
        float cx = wheel.centerX();
        float cy = wheel.centerY();
        int labelColor = alpha(UiControls.TEXT, progress);
        int muted = alpha(UiControls.TEXT_MUTED, progress);
        String title = primary ? "PRIMARY" : "SECONDARY";
        float titleWidth = SkijaUi.boldTextWidth(title, 8.0F);
        SkijaUi.boldText(canvas, title, cx - titleWidth * 0.5F,
                cy - wheel.radius() - 31.0F, 14.0F, labelColor, 8.0F);

        drawClockTicks(canvas, cx, cy, radius, progress);
        drawColorWheel(canvas, cx, cy, radius, progress);

        float hue = primary ? primaryHue : secondaryHue;
        float saturation = primary ? primarySaturation : secondarySaturation;
        float angle = hue * (float) (Math.PI * 2.0);
        float handleX = cx + (float) Math.cos(angle) * saturation * radius;
        float handleY = cy + (float) Math.sin(angle) * saturation * radius;
        Color selected = primary ? ColorPresets.INSTANCE.primary.raw()
                : ColorPresets.INSTANCE.secondary.raw();
        float handleRadius = 5.2F * eased;
        CIRCLE_PAINT.setColor(alpha(0xFF05070A, progress));
        canvas.drawCircle(handleX, handleY, handleRadius + 2.0F, CIRCLE_PAINT);
        CIRCLE_PAINT.setColor(alpha(selected.getRGB(), progress));
        canvas.drawCircle(handleX, handleY, handleRadius, CIRCLE_PAINT);
        SkijaUi.outline(canvas, handleX - handleRadius, handleY - handleRadius,
                handleRadius * 2.0F, handleRadius * 2.0F, handleRadius, 1.0F,
                alpha(0xFFFFFFFF, progress));

        float infoY = cy + wheel.radius() + 24.0F;
        String rgb = String.format(Locale.ROOT, "RGB  %d  %d  %d",
                selected.getRed(), selected.getGreen(), selected.getBlue());
        drawCentered(canvas, rgb, cx, infoY, 14.0F, muted, 7.0F, false);
        UiControls.TextInput input = primary ? primaryHex : secondaryHex;
        input.draw(canvas, mouseX, mouseY, selected.getRGB(), 0xA80A0C12,
                alpha(0xFFFFFFFF, Math.round(70.0F * progress)));
        drawSwatches(canvas, wheel, primary, progress, delta);
    }

    private static void drawColorWheel(Canvas canvas, float cx, float cy, float radius,
                                       float progress) {
        if (radius <= 0.5F) return;
        try (Shader hue = Shader.makeSweepGradient(cx, cy, WHEEL_COLORS);
             Shader saturation = Shader.makeRadialGradient(cx, cy, radius,
                     new int[]{0xFFFFFFFF, 0x00FFFFFF});
             Shader combined = Shader.makeBlend(io.github.humbleui.skija.BlendMode.SRC_OVER,
                     hue, saturation)) {
            WHEEL_PAINT.setShader(combined).setAlpha(Math.round(255.0F * progress));
            canvas.drawCircle(cx, cy, radius, WHEEL_PAINT);
        } finally {
            WHEEL_PAINT.setShader(null).setAlpha(255);
        }
    }

    private static void drawClockTicks(Canvas canvas, float cx, float cy, float radius,
                                       float progress) {
        for (int index = 0; index < 12; index++) {
            double angle = index / 12.0 * Math.PI * 2.0 - Math.PI * 0.5;
            float inner = radius + (index % 3 == 0 ? 7.0F : 9.0F);
            float outer = radius + 14.0F;
            float x1 = cx + (float) Math.cos(angle) * inner;
            float y1 = cy + (float) Math.sin(angle) * inner;
            float x2 = cx + (float) Math.cos(angle) * outer;
            float y2 = cy + (float) Math.sin(angle) * outer;
            SkijaUi.line(canvas, x1, y1, x2, y2, index % 3 == 0 ? 1.8F : 1.0F,
                    alpha(0xFFFFFFFF, Math.round((index % 3 == 0 ? 170 : 80) * progress)));
        }
    }

    private void drawSwatches(Canvas canvas, WheelLayout wheel, boolean primary,
                              float progress, float delta) {
        float gap = 5.0F;
        float size = Math.max(10.0F, Math.min(15.0F,
                (wheel.swatchWidth() - gap * (SWATCHES.length - 1)) / SWATCHES.length));
        float total = size * SWATCHES.length + gap * (SWATCHES.length - 1);
        float startX = wheel.centerX() - total * 0.5F;
        float y = wheel.swatchY();
        for (int index = 0; index < SWATCHES.length; index++) {
            float x = startX + index * (size + gap);
            boolean hovered = mouseX >= x - 2.0F && mouseX <= x + size + 2.0F
                    && mouseY >= y - 2.0F && mouseY <= y + size + 2.0F;
            float hover = wheel.hover()[index];
            wheel.hover()[index] = approach(hover, hovered ? 1.0F : 0.0F, delta, 14.0F);
            float expansion = wheel.hover()[index] * 2.0F;
            SkijaUi.fill(canvas, x - expansion, y - expansion,
                    size + expansion * 2.0F, size + expansion * 2.0F,
                    alpha(SWATCHES[index], progress));
            SkijaUi.outline(canvas, x - expansion, y - expansion,
                    size + expansion * 2.0F, size + expansion * 2.0F, 0.0F, 1.0F,
                    alpha(0xFFFFFFFF, Math.round((hovered ? 220 : 54) * progress)));
        }
    }

    private void drawFooter(Canvas canvas, Layout layout, float progress) {
        String message;
        if (!ColorPresets.INSTANCE.active.get()) {
            message = "Enable the preset to apply these colors globally";
        } else if (ColorPresets.INSTANCE.applyBackgroundColors.get()) {
            message = "Eligible colors, including backgrounds, follow this palette";
        } else {
            message = "HUD background colors keep their individual settings";
        }
        drawCentered(canvas, message, width * 0.5F, height - 25.0F, 12.0F,
                alpha(UiControls.TEXT_FAINT, progress), 6.5F, false);
        SkijaUi.line(canvas, layout.primary().centerX() + layout.primary().radius() + 42.0F,
                height * 0.5F, layout.secondary().centerX() - layout.secondary().radius() - 42.0F,
                height * 0.5F, 1.0F, alpha(0xFFFFFFFF, Math.round(28.0F * progress)));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || closing) return false;
        Layout layout = layout();
        updateInputBounds(layout);
        boolean primaryWasFocused = primaryHex.isFocused();
        boolean secondaryWasFocused = secondaryHex.isFocused();
        boolean primaryInput = primaryHex.click(event.x(), event.y(), doubleClick);
        boolean secondaryInput = secondaryHex.click(event.x(), event.y(), doubleClick);
        if (primaryWasFocused && !primaryHex.isFocused()) finishHexEdit(true);
        if (secondaryWasFocused && !secondaryHex.isFocused()) finishHexEdit(false);
        if (primaryInput) {
            if (!primaryWasFocused) primaryBeforeEdit = ColorPresets.INSTANCE.primary.raw();
            return true;
        }
        if (secondaryInput) {
            if (!secondaryWasFocused) secondaryBeforeEdit = ColorPresets.INSTANCE.secondary.raw();
            return true;
        }
        if (layout.back().contains(event.x(), event.y())) {
            onClose();
            return true;
        }
        if (layout.toggle().contains(event.x(), event.y())) {
            ColorPresets.INSTANCE.setActive(!ColorPresets.INSTANCE.active.get());
            return true;
        }
        if (layout.backgroundToggle().contains(event.x(), event.y())) {
            ColorPresets.INSTANCE.setApplyBackgroundColors(
                    !ColorPresets.INSTANCE.applyBackgroundColors.get());
            return true;
        }
        if (insideWheel(layout.primary(), event.x(), event.y())) {
            draggingPrimary = true;
            updateFromWheel(layout.primary(), true, event.x(), event.y());
            return true;
        }
        if (insideWheel(layout.secondary(), event.x(), event.y())) {
            draggingSecondary = true;
            updateFromWheel(layout.secondary(), false, event.x(), event.y());
            return true;
        }
        if (applySwatch(layout.primary(), true, event.x(), event.y())
                || applySwatch(layout.secondary(), false, event.x(), event.y())) return true;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        Layout layout = layout();
        if (draggingPrimary) {
            updateFromWheel(layout.primary(), true, event.x(), event.y());
            return true;
        }
        if (draggingSecondary) {
            updateFromWheel(layout.secondary(), false, event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingPrimary || draggingSecondary) {
            draggingPrimary = false;
            draggingSecondary = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (primaryHex.isFocused() || secondaryHex.isFocused()) {
            if (event.isConfirmation() || event.isEscape()) {
                if (primaryHex.isFocused()) finishHexEdit(true);
                if (secondaryHex.isFocused()) finishHexEdit(false);
                return true;
            }
            if (primaryHex.keyPressed(event, minecraft) || secondaryHex.keyPressed(event, minecraft)) {
                return true;
            }
        }
        if (event.isEscape()) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return primaryHex.charTyped(event) || secondaryHex.charTyped(event)
                || super.charTyped(event);
    }

    @Override
    public void onClose() {
        if (!closing) {
            if (primaryHex.isFocused()) finishHexEdit(true);
            if (secondaryHex.isFocused()) finishHexEdit(false);
            closing = true;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (closeReady) {
            closeReady = false;
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void removed() {
        restoreMenuBlur();
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

    private void updateFromWheel(WheelLayout wheel, boolean primary, double mouseX, double mouseY) {
        float dx = (float) mouseX - wheel.centerX();
        float dy = (float) mouseY - wheel.centerY();
        float saturation = clamp((float) Math.hypot(dx, dy) / wheel.radius(), 0.0F, 1.0F);
        float hue = (float) (Math.atan2(dy, dx) / (Math.PI * 2.0));
        if (hue < 0.0F) hue += 1.0F;
        Color color = new Color(Color.HSBtoRGB(hue, saturation, 1.0F));
        if (primary) ColorPresets.INSTANCE.setPrimary(color);
        else ColorPresets.INSTANCE.setSecondary(color);
    }

    private void updateInputBounds(Layout layout) {
        primaryHex.setBounds(hexBounds(layout.primary()));
        secondaryHex.setBounds(hexBounds(layout.secondary()));
    }

    private static UiControls.Box hexBounds(WheelLayout wheel) {
        return new UiControls.Box(wheel.centerX() - 43.0F,
                wheel.centerY() + wheel.radius() + 40.0F, 86.0F, 20.0F);
    }

    private void syncHexInputs() {
        if (!primaryHex.isFocused()) primaryHex.setText(hex(ColorPresets.INSTANCE.primary.raw()));
        if (!secondaryHex.isFocused()) secondaryHex.setText(hex(ColorPresets.INSTANCE.secondary.raw()));
    }

    private void finishHexEdit(boolean primary) {
        UiControls.TextInput input = primary ? primaryHex : secondaryHex;
        Color fallback = primary ? primaryBeforeEdit : secondaryBeforeEdit;
        if (fallback == null) {
            fallback = primary ? ColorPresets.INSTANCE.primary.raw()
                    : ColorPresets.INSTANCE.secondary.raw();
        }
        Color parsed = parseHex(input.text());
        Color value = parsed == null ? fallback : parsed;
        if (primary) {
            ColorPresets.INSTANCE.setPrimary(value);
            primaryBeforeEdit = null;
        } else {
            ColorPresets.INSTANCE.setSecondary(value);
            secondaryBeforeEdit = null;
        }
        input.blur();
        input.setText(hex(value));
    }

    private static Color parseHex(String input) {
        String value = input == null ? "" : input.trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (value.length() != 6) return null;
        try {
            return new Color(Integer.parseInt(value, 16));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String hex(Color color) {
        return String.format(Locale.ROOT, "#%06X", color.getRGB() & 0xFFFFFF);
    }

    private static boolean isHexCharacter(int codePoint) {
        return codePoint == '#' || codePoint >= '0' && codePoint <= '9'
                || codePoint >= 'a' && codePoint <= 'f'
                || codePoint >= 'A' && codePoint <= 'F';
    }

    private boolean applySwatch(WheelLayout wheel, boolean primary, double mouseX, double mouseY) {
        float gap = 5.0F;
        float size = Math.max(10.0F, Math.min(15.0F,
                (wheel.swatchWidth() - gap * (SWATCHES.length - 1)) / SWATCHES.length));
        float total = size * SWATCHES.length + gap * (SWATCHES.length - 1);
        float startX = wheel.centerX() - total * 0.5F;
        for (int index = 0; index < SWATCHES.length; index++) {
            float x = startX + index * (size + gap);
            if (mouseX >= x - 2.0F && mouseX <= x + size + 2.0F
                    && mouseY >= wheel.swatchY() - 2.0F
                    && mouseY <= wheel.swatchY() + size + 2.0F) {
                Color color = new Color(SWATCHES[index], true);
                if (primary) ColorPresets.INSTANCE.setPrimary(color);
                else ColorPresets.INSTANCE.setSecondary(color);
                return true;
            }
        }
        return false;
    }

    private void syncSelectionImmediately() {
        float[] primary = Color.RGBtoHSB(ColorPresets.INSTANCE.primary.raw().getRed(),
                ColorPresets.INSTANCE.primary.raw().getGreen(),
                ColorPresets.INSTANCE.primary.raw().getBlue(), null);
        float[] secondary = Color.RGBtoHSB(ColorPresets.INSTANCE.secondary.raw().getRed(),
                ColorPresets.INSTANCE.secondary.raw().getGreen(),
                ColorPresets.INSTANCE.secondary.raw().getBlue(), null);
        primaryHue = primary[0];
        primarySaturation = primary[1];
        secondaryHue = secondary[0];
        secondarySaturation = secondary[1];
        toggleMotion = ColorPresets.INSTANCE.active.get() ? 1.0F : 0.0F;
        backgroundToggleMotion = ColorPresets.INSTANCE.applyBackgroundColors.get()
                ? 1.0F : 0.0F;
        initializedSelection = true;
    }

    private void updateAnimatedSelection(float delta) {
        if (!initializedSelection) syncSelectionImmediately();
        float[] primary = Color.RGBtoHSB(ColorPresets.INSTANCE.primary.raw().getRed(),
                ColorPresets.INSTANCE.primary.raw().getGreen(),
                ColorPresets.INSTANCE.primary.raw().getBlue(), null);
        float[] secondary = Color.RGBtoHSB(ColorPresets.INSTANCE.secondary.raw().getRed(),
                ColorPresets.INSTANCE.secondary.raw().getGreen(),
                ColorPresets.INSTANCE.secondary.raw().getBlue(), null);
        primaryHue = approachHue(primaryHue, primary[0], delta, draggingPrimary ? 30.0F : 10.0F);
        primarySaturation = approach(primarySaturation, primary[1], delta,
                draggingPrimary ? 30.0F : 10.0F);
        secondaryHue = approachHue(secondaryHue, secondary[0], delta, draggingSecondary ? 30.0F : 10.0F);
        secondarySaturation = approach(secondarySaturation, secondary[1], delta,
                draggingSecondary ? 30.0F : 10.0F);
    }

    private Layout layout() {
        float radius = Math.max(62.0F, Math.min(width * 0.135F, height * 0.245F));
        float centerY = Math.max(128.0F, Math.min(height * 0.44F, height - radius - 105.0F));
        float leftX = Math.max(radius + 30.0F, width * 0.27F);
        float rightX = Math.min(width - radius - 30.0F, width * 0.73F);
        float swatchY = Math.min(height - 50.0F, centerY + radius + 63.0F);
        WheelLayout primary = new WheelLayout(leftX, centerY, radius,
                Math.min(245.0F, width * 0.38F), swatchY, new float[SWATCHES.length]);
        WheelLayout secondary = new WheelLayout(rightX, centerY, radius,
                Math.min(245.0F, width * 0.38F), swatchY, new float[SWATCHES.length]);
        // Preserve hover state despite rebuilding responsive geometry each frame.
        primary = primary.withHover(primaryHover);
        secondary = secondary.withHover(secondaryHover);
        float backgroundLabelWidth = SkijaUi.boldTextWidth("BACKGROUND COLORS", 6.5F);
        float backgroundToggleWidth = 50.0F;
        float backgroundToggleX = width * 0.5F
                + (backgroundLabelWidth + 10.0F - backgroundToggleWidth) * 0.5F;
        return new Layout(primary, secondary,
                new UiControls.Box(width - 76.0F, 18.0F, 52.0F, 22.0F),
                new UiControls.Box(width * 0.5F - 25.0F, 19.0F, 50.0F, 20.0F),
                new UiControls.Box(backgroundToggleX, height - 60.0F,
                        backgroundToggleWidth, 20.0F));
    }

    private final float[] primaryHover = new float[SWATCHES.length];
    private final float[] secondaryHover = new float[SWATCHES.length];

    @Override
    protected int backdropColor() {
        return 0x00000000;
    }

    private static boolean insideWheel(WheelLayout wheel, double x, double y) {
        return Math.hypot(x - wheel.centerX(), y - wheel.centerY()) <= wheel.radius() + 4.0F;
    }

    private float frameDelta() {
        long now = System.nanoTime();
        float delta = clamp((now - lastFrame) / 1_000_000_000.0F, 0.0F, 0.05F);
        lastFrame = now;
        return delta;
    }

    private static float approach(float current, float target, float delta, float speed) {
        return current + (target - current) * Math.min(1.0F, delta * speed);
    }

    private static float approachHue(float current, float target, float delta, float speed) {
        float difference = target - current;
        if (difference > 0.5F) difference -= 1.0F;
        if (difference < -0.5F) difference += 1.0F;
        float result = current + difference * Math.min(1.0F, delta * speed);
        if (result < 0.0F) result += 1.0F;
        if (result >= 1.0F) result -= 1.0F;
        return result;
    }

    private static float delayed(float progress, float delay) {
        return clamp((progress - delay) / (1.0F - delay), 0.0F, 1.0F);
    }

    private static float easeOutCubic(float value) {
        float inverse = 1.0F - clamp(value, 0.0F, 1.0F);
        return 1.0F - inverse * inverse * inverse;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int alpha(int color, float progress) {
        return alpha(color, Math.round(((color >>> 24) & 0xFF) * clamp(progress, 0.0F, 1.0F)));
    }

    private static int alpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static int mix(int from, int to, float progress) {
        float p = clamp(progress, 0.0F, 1.0F);
        int a = Math.round(((from >>> 24) & 0xFF) * (1.0F - p) + ((to >>> 24) & 0xFF) * p);
        int r = Math.round(((from >>> 16) & 0xFF) * (1.0F - p) + ((to >>> 16) & 0xFF) * p);
        int g = Math.round(((from >>> 8) & 0xFF) * (1.0F - p) + ((to >>> 8) & 0xFF) * p);
        int b = Math.round((from & 0xFF) * (1.0F - p) + (to & 0xFF) * p);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static void drawCentered(Canvas canvas, String text, float centerX, float y,
                                     float height, int color, float size, boolean bold) {
        float textWidth = bold ? SkijaUi.boldTextWidth(text, size) : SkijaUi.textWidth(text, size);
        if (bold) SkijaUi.boldText(canvas, text, centerX - textWidth * 0.5F, y, height, color, size);
        else SkijaUi.text(canvas, text, centerX - textWidth * 0.5F, y, height, color, size);
    }

    private record Layout(WheelLayout primary, WheelLayout secondary,
                          UiControls.Box back, UiControls.Box toggle,
                          UiControls.Box backgroundToggle) {
    }

    private record WheelLayout(float centerX, float centerY, float radius, float swatchWidth,
                               float swatchY, float[] hover) {
        WheelLayout withHover(float[] values) {
            return new WheelLayout(centerX, centerY, radius, swatchWidth, swatchY, values);
        }
    }
}
