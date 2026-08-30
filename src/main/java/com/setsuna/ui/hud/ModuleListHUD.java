package com.setsuna.ui.hud;

import com.setsuna.event.Listen;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.event.events.VanillaHudRenderEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.i18n.LocalizedText;
import com.setsuna.module.ModuleManager;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.FontSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.ui.CategoryGlyphs;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.types.RRect;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Right-aligned animated module list, ported 1:1 from Remix's {@code ModuleList}:
 * optional per-row backgrounds, per-row accent colouring, camelCase-split names
 * with a grey suffix, and a slide-in animation from the anchored edge weighted by
 * each row's fade.
 *
 * <p>The list can draw with the client's Skija typeface or Minecraft's own bitmap
 * font, and colours each row from the shared HUD accent, a static gradient,
 * a self-contained rainbow, or a fixed custom colour.</p>
 */
public final class ModuleListHUD extends EpsilonHudModule {

    private static final int GRAY = 0xFFAAAAAA;

    public static final ModuleListHUD INSTANCE = new ModuleListHUD();

    /** How each row is coloured, independent of the other HUD components. */
    public enum ColorMode { Sync, Gradient, Rainbow, Custom }

    public final DoubleSetting scale = add(new DoubleSetting("Scale", 1.0, 0.65, 1.6, 0.05));
    public final FontSetting font = add(new FontSetting("Font", SkijaUi.CLIENT_FONT,
            ModuleListHUD::fontChoices));
    public final EnumSetting<ColorMode> colorMode = add(new EnumSetting<>("Color Mode", ColorMode.Sync));
    public final ColorSetting gradientStart = add(new ColorSetting("Start Color",
            new Color(114, 189, 244), false).visibleWhen(() -> colorMode.is(ColorMode.Gradient)));
    public final ColorSetting gradientEnd = add(new ColorSetting("End Color",
            new Color(238, 132, 176), false).visibleWhen(() -> colorMode.is(ColorMode.Gradient)));
    public final ColorSetting customColor = add(new ColorSetting("Custom Color",
            new Color(120, 198, 255), false).visibleWhen(() -> colorMode.is(ColorMode.Custom)));
    public final BooleanSetting onlyImportant = add(new BooleanSetting("Only Important", false));
    public final BooleanSetting moduleInfo = add(new BooleanSetting("Module Info", true));
    public final BooleanSetting shadow = add(new BooleanSetting("Shadow", false));
    public final BooleanSetting glow = add(new BooleanSetting("Glow", false));
    public final IntSetting glowStrength = add(new IntSetting("Glow Strength", 8, 1, 10, 1)
            .visibleWhen(glow::get));
    public final BooleanSetting background = add(new BooleanSetting("Background", false));
    public final ColorSetting backgroundColor = add(new ColorSetting("Background Color",
            new Color(5, 9, 14, 128), true).visibleWhen(background::get));
    public final IntSetting backgroundRadius = add(new IntSetting("Background Radius", 3, 0, 8, 1)
            .visibleWhen(background::get));
    public final BooleanSetting backgroundShadow = add(new BooleanSetting("Background Shadow", false)
            .visibleWhen(background::get));
    public final BooleanSetting ico = add(new BooleanSetting("Ico", false)
            .visibleWhen(background::get));
    public final BooleanSetting iconContinuous = add(new BooleanSetting("Icon Continuous", false)
            .visibleWhen(() -> background.get() && ico.get()));
    public final BooleanSetting continuous = add(new BooleanSetting("Continuous", false)
            .visibleWhen(background::get));
    public final BooleanSetting bar = add(new BooleanSetting("Bar", false)
            .visibleWhen(background::get));
    public final IntSetting spacing = add(new IntSetting("Spacing", 0, -3, 3, 1));

    private final Map<Module, Float> visibility = new IdentityHashMap<>();

    private ModuleListHUD() {
        super("Array List", 1000, 16, 132.0F, 82.0F);
        setEnabled(true);
    }

    // --- Skija (client font) path -------------------------------------------

    @Override
    protected void renderHud(Render2DEvent event) {
        if (noPlayer() || isMinecraftFont()) return;
        float s = scale.get().floatValue();
        float fontSize = 10.0F * s;
        float rowHeight = fontSize * 1.35F;
        float sidePadding = 3.0F * s;
        float anchorExtension = 2.0F * s;
        float plainPadding = 4.0F * s;
        float iconDiameter = iconDiameter(rowHeight, s);
        float iconGap = iconGap(s);
        float iconExtra = iconsEnabled() ? iconDiameter + iconGap : 0.0F;

        List<Row> rows = collectRows();
        if (rows.isEmpty()) {
            updateBounds(defaultWidth(), defaultHeight());
            return;
        }

        float maxWidth = 0.0F;
        for (Row row : rows) {
            if (row.alpha() > 0.01F) maxWidth = Math.max(maxWidth, skijaWidth(row, fontSize));
        }
        float blockWidth = maxWidth + (background.get()
                ? sidePadding * 2.0F + anchorExtension
                : plainPadding) + iconExtra;

        float blockX = renderX(event, blockWidth);
        float blockY = renderY(event, estimatedHeight(rows, rowHeight, s));
        boolean right = xPosition.get() > 500;

        Canvas canvas = event.canvas();
        float offsetY = blockY;
        int index = 0;
        List<RowLayout> layouts = new ArrayList<>();
        for (Row row : rows) {
            float alpha = row.alpha();
            if (alpha <= 0.01F) continue;
            float nameWidth = clientTextWidth(row.name(), fontSize);
            float rowWidth = nameWidth + (row.suffix().isBlank()
                    ? 0.0F : clientTextWidth(row.suffix(), fontSize));
            float slide = (1.0F - alpha) * (blockWidth + 10.0F * s);
            float textX;
            float backgroundX = 0.0F;
            float backgroundWidth = 0.0F;
            float iconX = 0.0F;
            if (background.get()) {
                backgroundWidth = rowWidth + sidePadding * 2.0F + anchorExtension;
                backgroundX = right
                        ? blockX + blockWidth - iconExtra - backgroundWidth + slide
                        : blockX - slide;
                textX = backgroundX + sidePadding + (right ? 0.0F : anchorExtension);
                iconX = backgroundX + backgroundWidth + iconGap;
            } else {
                float x = right ? blockX + blockWidth - rowWidth + slide : blockX - slide;
                textX = right ? x - 2.0F * s : x + 2.0F * s;
            }
            layouts.add(new RowLayout(row, textX, offsetY, rowWidth, nameWidth, index++,
                    backgroundX, backgroundWidth, iconX, iconDiameter));
            offsetY += (rowHeight + spacing.get() * s) * alpha;
        }

        if (background.get()) {
            drawBackground(canvas, layouts, rowHeight, s, right,
                    withAlpha(backgroundColor.argb(), maxAlpha(layouts)), false);
            if (bar.get()) {
                drawBar(canvas, layouts, rowHeight, s, right,
                        right ? blockX + blockWidth : blockX, rows.size(), false);
            }
        }
        if (iconsEnabled()) {
            drawCategoryIcons(canvas, layouts, rowHeight, rows.size(), false);
        }
        float renderedHeight = Math.max(rowHeight, offsetY - blockY);
        if (glow.get()) {
            SkijaUi.glowLayer(canvas, blockX, blockY, blockWidth, renderedHeight,
                    4.0F * s, glowStrength.get(),
                    () -> drawClientRows(canvas, layouts, rows.size(), rowHeight, fontSize, false));
        }
        drawClientRows(canvas, layouts, rows.size(), rowHeight, fontSize, true);
        updateBounds(blockWidth, renderedHeight);
    }

    // --- Minecraft (bitmap font) path ---------------------------------------

    @Listen
    private void onVanillaHud(VanillaHudRenderEvent event) {
        if (noPlayer() || !isMinecraftFont()) return;
        float s = scale.get().floatValue();
        int lineHeight = mc.font.lineHeight;
        float rowHeight = lineHeight + spacing.get();
        float sidePadding = 3.0F;
        float anchorExtension = 2.0F;
        float plainPadding = 4.0F;
        float iconDiameter = iconDiameter(rowHeight, 1.0F);
        float iconGap = iconGap(1.0F);
        float iconExtra = iconsEnabled() ? iconDiameter + iconGap : 0.0F;

        List<Row> rows = collectRows();
        if (rows.isEmpty()) {
            updateBounds(defaultWidth(), defaultHeight());
            return;
        }

        float maxWidth = 0.0F;
        for (Row row : rows) {
            if (row.alpha() > 0.01F) maxWidth = Math.max(maxWidth, vanillaWidth(row));
        }
        float blockWidth = maxWidth + (background.get()
                ? sidePadding * 2.0F + anchorExtension
                : plainPadding) + iconExtra;
        float blockHeight = estimatedHeight(rows, rowHeight, 1.0F);

        float screenWidth = blockWidth * s;
        float screenHeight = blockHeight * s;
        float blockX = renderX(event.width(), screenWidth);
        float blockY = renderY(event.height(), screenHeight);
        boolean right = xPosition.get() > 500;
        updateBounds(screenWidth, Math.max(rowHeight * s, screenHeight));

        float offsetY = 0.0F;
        int index = 0;
        List<RowLayout> layouts = new ArrayList<>();
        for (Row row : rows) {
            float alpha = row.alpha();
            if (alpha <= 0.01F) continue;
            int nameWidth = mc.font.width(row.name());
            float rowWidth = nameWidth + (row.suffix().isBlank() ? 0 : mc.font.width(row.suffix()));
            float slide = (1.0F - alpha) * (blockWidth + 10.0F);
            float textX;
            float backgroundX = 0.0F;
            float backgroundWidth = 0.0F;
            float iconX = 0.0F;
            if (background.get()) {
                backgroundWidth = rowWidth + sidePadding * 2.0F + anchorExtension;
                backgroundX = right
                        ? blockWidth - iconExtra - backgroundWidth + slide
                        : -slide;
                textX = backgroundX + sidePadding + (right ? 0.0F : anchorExtension);
                iconX = backgroundX + backgroundWidth + iconGap;
            } else {
                float x = right ? blockWidth - rowWidth + slide : -slide;
                textX = right ? x - 2.0F : x + 2.0F;
            }
            layouts.add(new RowLayout(row, textX, offsetY, rowWidth, nameWidth, index++,
                    backgroundX, backgroundWidth, iconX, iconDiameter));
            offsetY += (rowHeight + spacing.get()) * alpha;
        }

        if (background.get()) {
            int color = applyOpacity(withAlpha(backgroundColor.argb(), maxAlpha(layouts)));
            SkijaRenderer.renderMainTarget(canvas -> {
                int save = canvas.save();
                try {
                    canvas.translate(blockX, blockY);
                    canvas.scale(s, s);
                    drawBackground(canvas, layouts, rowHeight, 1.0F, right, color, true);
                    if (bar.get()) {
                        drawBar(canvas, layouts, rowHeight, 1.0F, right,
                                right ? blockWidth : 0.0F, rows.size(), true);
                    }
                    if (iconsEnabled()) {
                        drawCategoryIcons(canvas, layouts, rowHeight, rows.size(), true);
                    }
                } finally {
                    canvas.restoreToCount(save);
                }
            });
        }

        GuiGraphicsExtractor graphics = event.graphics();
        graphics.pose().pushMatrix();
        graphics.pose().translate(blockX, blockY);
        graphics.pose().scale(s, s);

        for (RowLayout layout : layouts) {
            Row row = layout.row();
            int accent = applyOpacity(withAlpha(rowColor(layout.index(), rows.size()), row.alpha()));
            drawVanillaText(graphics, row.name(), layout.textX(), layout.y(), accent);
            if (!row.suffix().isBlank()) {
                drawVanillaText(graphics, row.suffix(), layout.textX() + layout.nameWidth(), layout.y(),
                        applyOpacity(withAlpha(GRAY, row.alpha())));
            }
        }
        graphics.pose().popMatrix();
    }

    private void drawBackground(Canvas canvas, List<RowLayout> layouts,
                                float rowHeight, float unit, boolean right,
                                int color, boolean bakeOpacity) {
        if (layouts.isEmpty() || ((color >>> 24) & 0xFF) == 0) return;
        float verticalPadding = continuous.get() ? 1.25F * unit : -0.65F * unit;
        float radius = backgroundRadius.get() * unit;

        try (PathBuilder builder = new PathBuilder()) {
            for (int i = 0; i < layouts.size(); i++) {
                RowLayout layout = layouts.get(i);
                float x = layout.backgroundX();
                float width = layout.backgroundWidth();
                float top = layout.y() - verticalPadding;
                float bottom = layout.y() + rowHeight + verticalPadding;
                if (continuous.get() && i + 1 < layouts.size()) {
                    bottom = Math.max(bottom, layouts.get(i + 1).y() + verticalPadding);
                }
                float height = Math.max(1.0F, bottom - top);
                boolean mergedIcon = iconsEnabled() && iconContinuous.get();
                float shapeWidth = width + (mergedIcon ? layout.iconDiameter() : 0.0F);
                float rowRadius = Math.min(radius, Math.min(shapeWidth, height) * 0.5F);
                float mergedRadius = Math.min(height * 0.5F,
                        Math.max(rowRadius, layout.iconDiameter() * 0.5F));
                float[] radii = mergedIcon
                        ? new float[]{rowRadius, rowRadius, mergedRadius, mergedRadius,
                        mergedRadius, mergedRadius, rowRadius, rowRadius}
                        : iconsEnabled()
                        ? new float[]{rowRadius, rowRadius, rowRadius, rowRadius,
                        rowRadius, rowRadius, rowRadius, rowRadius}
                        : right
                        ? new float[]{rowRadius, rowRadius, 0.0F, 0.0F,
                        0.0F, 0.0F, rowRadius, rowRadius}
                        : new float[]{0.0F, 0.0F, rowRadius, rowRadius,
                        rowRadius, rowRadius, 0.0F, 0.0F};
                builder.addRRect(RRect.makeComplexXYWH(x, top, shapeWidth, height, radii));

                if (iconsEnabled() && !mergedIcon) {
                    float iconY = iconY(layout, rowHeight);
                    float diameter = layout.iconDiameter();
                    builder.addRRect(RRect.makeXYWH(layout.iconX(), iconY,
                            diameter, diameter, diameter * 0.5F));
                }
            }
            try (Path path = builder.detach()) {
                if (backgroundShadow.get()) {
                    int shadowColor = withAlpha(0x8C000000, maxAlpha(layouts));
                    if (bakeOpacity) shadowColor = applyOpacity(shadowColor);
                    SkijaUi.dropShadowPath(canvas, path, 7.0F * unit, shadowColor);
                }
                SkijaUi.fillPath(canvas, path, color);
            }
        }
    }

    private void drawCategoryIcons(Canvas canvas, List<RowLayout> layouts,
                                   float rowHeight, int rowCount, boolean bakeOpacity) {
        for (RowLayout layout : layouts) {
            float size = layout.iconDiameter() * 0.64F;
            String glyph = categoryIcon(layout.row().category());
            float glyphWidth = SkijaUi.iconWidth(glyph, size, SkijaUi.IconSet.LUCIDE);
            int color = withAlpha(rowColor(layout.index(), rowCount), layout.row().alpha());
            if (bakeOpacity) color = applyOpacity(color);
            SkijaUi.icon(canvas, glyph,
                    layout.iconX() + (layout.iconDiameter() - glyphWidth) * 0.5F,
                    iconY(layout, rowHeight), layout.iconDiameter(), color, size,
                    SkijaUi.IconSet.LUCIDE);
        }
    }

    private boolean iconsEnabled() {
        return background.get() && ico.get();
    }

    private float iconGap(float unit) {
        return iconContinuous.get() ? 0.0F : 2.5F * unit;
    }

    private static float iconDiameter(float rowHeight, float unit) {
        return Math.max(6.0F * unit, rowHeight - 2.0F * unit);
    }

    private static float iconY(RowLayout layout, float rowHeight) {
        return layout.y() + (rowHeight - layout.iconDiameter()) * 0.5F;
    }

    private static String categoryIcon(Category category) {
        return CategoryGlyphs.forCategory(category);
    }

    private void drawBar(Canvas canvas, List<RowLayout> layouts, float rowHeight,
                         float unit, boolean right, float anchorX,
                         int rowCount, boolean bakeOpacity) {
        if (layouts.isEmpty()) return;
        float verticalPadding = 1.25F * unit;
        float barWidth = Math.max(1.0F, 1.5F * unit);
        float barX = right ? anchorX - barWidth : anchorX;
        for (int index = 0; index < layouts.size(); index++) {
            RowLayout layout = layouts.get(index);
            float top = layout.y() - verticalPadding;
            float bottom = layout.y() + rowHeight + verticalPadding;
            if (index + 1 < layouts.size()) {
                bottom = Math.max(bottom, layouts.get(index + 1).y() + verticalPadding);
            }
            int color = withAlpha(rowColor(layout.index(), rowCount), layout.row().alpha());
            if (bakeOpacity) color = applyOpacity(color);
            SkijaUi.fill(canvas, barX, top, barWidth,
                    Math.max(1.0F, bottom - top), color);
        }
    }

    private static float maxAlpha(List<RowLayout> layouts) {
        float alpha = 0.0F;
        for (RowLayout layout : layouts) alpha = Math.max(alpha, layout.row().alpha());
        return alpha;
    }

    private void drawClientText(Canvas canvas, String text, float x, float y,
                                float height, int color, float fontSize) {
        if (shadow.get()) {
            SkijaUi.textShadowWithFallback(canvas, text, x, y, height, color,
                    fontSize, clientFontOverride());
        } else {
            SkijaUi.textWithFallback(canvas, text, x, y, height, color,
                    fontSize, clientFontOverride());
        }
    }

    private void drawClientRows(Canvas canvas, List<RowLayout> layouts, int rowCount,
                                float rowHeight, float fontSize, boolean includeShadow) {
        for (RowLayout layout : layouts) {
            Row row = layout.row();
            int accent = withAlpha(rowColor(layout.index(), rowCount), row.alpha());
            if (includeShadow) {
                drawClientText(canvas, row.name(), layout.textX(), layout.y(), rowHeight, accent, fontSize);
            } else {
                SkijaUi.textWithFallback(canvas, row.name(), layout.textX(), layout.y(), rowHeight,
                        accent, fontSize, clientFontOverride());
            }
            if (row.suffix().isBlank()) continue;
            int suffix = withAlpha(GRAY, row.alpha());
            if (includeShadow) {
                drawClientText(canvas, row.suffix(), layout.textX() + layout.nameWidth(), layout.y(),
                        rowHeight, suffix, fontSize);
            } else {
                SkijaUi.textWithFallback(canvas, row.suffix(),
                        layout.textX() + layout.nameWidth(), layout.y(),
                        rowHeight, suffix, fontSize, clientFontOverride());
            }
        }
    }

    private void drawVanillaText(GuiGraphicsExtractor graphics, String text,
                                 float x, float y, int color) {
        int drawX = Math.round(x);
        int drawY = Math.round(y);
        if (glow.get()) {
            int glowLimit = 48 + Math.max(1, Math.min(10, glowStrength.get())) * 20;
            int alpha = Math.min(glowLimit, (color >>> 24) & 0xFF);
            int glowColor = (alpha << 24) | (color & 0x00FFFFFF);
            graphics.text(mc.font, text, drawX - 1, drawY, glowColor, false);
            graphics.text(mc.font, text, drawX + 1, drawY, glowColor, false);
            graphics.text(mc.font, text, drawX, drawY - 1, glowColor, false);
            graphics.text(mc.font, text, drawX, drawY + 1, glowColor, false);
            graphics.text(mc.font, text, drawX - 1, drawY - 1, glowColor, false);
            graphics.text(mc.font, text, drawX + 1, drawY - 1, glowColor, false);
            graphics.text(mc.font, text, drawX - 1, drawY + 1, glowColor, false);
            graphics.text(mc.font, text, drawX + 1, drawY + 1, glowColor, false);
        }
        graphics.text(mc.font, text, drawX, drawY, color, shadow.get());
    }

    @Override
    protected boolean usesSharedOpacityLayer() {
        // The Minecraft-font path draws through vanilla GuiGraphics and bakes
        // opacity into its colours; the client-font path uses the shared layer.
        return !isMinecraftFont();
    }

    // --- shared row collection & colouring -----------------------------------

    private List<Row> collectRows() {
        List<Row> rows = new ArrayList<>();
        for (Module module : ModuleManager.INSTANCE.modules()) {
            if (module == this || module == HUD.INSTANCE || module.isHidden() || module instanceof EpsilonHudModule
                    || module.category() == Category.HUD || !module.isToggleable()) {
                continue;
            }
            if (onlyImportant.get() && module.category() == Category.RENDER) {
                continue;
            }
            float target = module.isEnabled() ? 1.0F : 0.0F;
            float current = visibility.getOrDefault(module, target);
            current += (target - current) * 0.16F;
            if (Math.abs(target - current) < 0.004F) current = target;
            visibility.put(module, current);
            if (current <= 0.01F) continue;

            String name = splitName(module.displayName());
            String suffix = moduleInfo.get() && module.getInfo() != null
                    ? " " + LocalizedText.freeform(module.getInfo().trim()) : "";
            rows.add(new Row(name, suffix, module.category(), current));
        }
        sortRowsByWidth(rows);
        return rows;
    }

    /** Row colour (opaque) honouring the list's own colour mode, before per-row fade. */
    private int rowColor(int index, int rowCount) {
        return switch (colorMode.get()) {
            case Sync -> HUD.INSTANCE.whiteMode.get() ? 0xFFFFFFFF : HUD.INSTANCE.getColor(index);
            case Gradient -> gradient(index, rowCount);
            case Rainbow -> rainbow(index);
            case Custom -> customColor.argb();
        };
    }

    private int gradient(int index, int rowCount) {
        float progress = rowCount <= 1 ? 0.0F : index / (float) (rowCount - 1);
        Color start = gradientStart.get();
        Color end = gradientEnd.get();
        int red = Math.round(start.getRed() + (end.getRed() - start.getRed()) * progress);
        int green = Math.round(start.getGreen() + (end.getGreen() - start.getGreen()) * progress);
        int blue = Math.round(start.getBlue() + (end.getBlue() - start.getBlue()) * progress);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int rainbow(int index) {
        double state = Math.ceil(System.currentTimeMillis() - index * 110L) / 11.0 % 360.0;
        int rgb = Color.HSBtoRGB((float) (state / 360.0), 1.0F, 1.0F);
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    private float estimatedHeight(List<Row> rows, float rowHeight, float scale) {
        float total = 0.0F;
        for (Row row : rows) {
            if (row.alpha() > 0.01F) total += (rowHeight + spacing.get() * scale) * row.alpha();
        }
        return Math.max(rowHeight, total);
    }

    private float skijaWidth(Row row, float fontSize) {
        float width = clientTextWidth(row.name(), fontSize);
        return row.suffix().isBlank() ? width : width + clientTextWidth(row.suffix(), fontSize);
    }

    /**
     * Matches LiquidBounce's ArrayList ordering: measure the complete rendered
     * row and keep the widest row at the top. Widths are cached so the bubble
     * sort does not repeatedly measure the same text during a HUD frame.
     */
    private void sortRowsByWidth(List<Row> rows) {
        float[] widths = new float[rows.size()];
        for (int index = 0; index < rows.size(); index++) {
            widths[index] = rowSortWidth(rows.get(index));
        }

        for (int end = rows.size() - 1; end > 0; end--) {
            boolean swapped = false;
            for (int index = 0; index < end; index++) {
                if (widths[index] < widths[index + 1]) {
                    Row current = rows.get(index);
                    rows.set(index, rows.get(index + 1));
                    rows.set(index + 1, current);

                    float currentWidth = widths[index];
                    widths[index] = widths[index + 1];
                    widths[index + 1] = currentWidth;
                    swapped = true;
                }
            }
            if (!swapped) return;
        }
    }

    private float rowSortWidth(Row row) {
        return isMinecraftFont()
                ? vanillaWidth(row)
                : skijaWidth(row, 10.0F);
    }

    private boolean isMinecraftFont() {
        return "Minecraft".equalsIgnoreCase(font.get());
    }

    private String clientFontOverride() {
        return SkijaUi.CLIENT_FONT.equalsIgnoreCase(font.get()) ? null : font.get();
    }

    private float clientTextWidth(String text, float size) {
        return SkijaUi.textWidthWithFallback(text, size, clientFontOverride());
    }

    private static List<String> fontChoices() {
        List<String> choices = new ArrayList<>(SkijaUi.availableFontNames());
        choices.add(1, "Minecraft");
        return choices;
    }

    private float vanillaWidth(Row row) {
        int width = mc.font.width(row.name());
        return row.suffix().isBlank() ? width : width + mc.font.width(row.suffix());
    }

    /** Splits camelCase boundaries with spaces, mirroring Remix's regex. */
    private static String splitName(String value) {
        return value.replaceAll("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])", " ");
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(((color >>> 24) & 0xFF) * alpha)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    @Override
    public int editorColor() {
        return rowColor(0, 2);
    }

    private record Row(String name, String suffix, Category category, float alpha) {
    }

    private record RowLayout(Row row, float textX, float y, float rowWidth,
                             float nameWidth, int index, float backgroundX,
                             float backgroundWidth, float iconX, float iconDiameter) {
    }
}
