package com.setsuna.ui.hud;

import com.setsuna.Setsuna;
import com.setsuna.event.Listen;
import com.setsuna.event.Priority;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.event.events.VanillaHudRenderEvent;
import com.setsuna.util.player.HeypixelHealthSource;
import com.setsuna.util.player.HealthDetectionUtils;
import com.setsuna.manager.HealthManager;
import com.setsuna.manager.target.TargetManager;
import com.setsuna.module.Category;
import com.setsuna.module.modules.combat.KillAura;
import com.setsuna.module.modules.combat.KillAuraPlus;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ButtonSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** Target HUD with native textures and antialiased vector/text overlays. */
public final class TargetHud extends HUDModuel {

    public static final TargetHud INSTANCE = new TargetHud();

    public enum Style {
        Setsuna, Flux, Default, ModelPreview, Novoline, Adjust
    }

    /** Selects the server/client source used when resolving a target's health. */
    public enum HealthDetection { Hoplite, Heypixel, Other }

    public enum ColorMode { Sync, Custom, Health, Astolfo }

    public final EnumSetting<HealthDetection> healthDetection = add(
            new EnumSetting<>("Health Detection", HealthDetection.Hoplite));
    public final EnumSetting<Style> style = add(new EnumSetting<>("Style", Style.Setsuna));
    public final DoubleSetting scale = add(new DoubleSetting("Scale", 1.0, 0.5, 2.0, 0.05));
    public final DoubleSetting panelWidth = add(new DoubleSetting("Width", 220.0, 220.0, 700.0, 1.0)
            .visibleWhen(() -> style.is(Style.Setsuna) || style.is(Style.Default)));
    public final DoubleSetting panelHeight = add(new DoubleSetting("Height", 72.0, 60.0, 140.0, 1.0)
            .visibleWhen(() -> style.is(Style.Setsuna) || style.is(Style.Default)));
    public final DoubleSetting radius = add(new DoubleSetting("Radius", 14.0, 0.0, 24.0, 0.5)
            .visibleWhen(() -> style.is(Style.Setsuna)));
    public final ButtonSetting importBackground = add(new ButtonSetting(
            "Import Background", this::importSetsunaBackground)
            .visibleWhen(() -> style.is(Style.Setsuna)));
    public final ButtonSetting resetBackground = add(new ButtonSetting(
            "Reset Background", this::resetSetsunaBackground)
            .visibleWhen(() -> style.is(Style.Setsuna) && hasCustomBackground()));
    public final IntSetting imageOpacity = add(new IntSetting("Image Opacity", 100, 0, 100, 1)
            .visibleWhen(() -> style.is(Style.Setsuna) && hasCustomBackground()));
    public final BooleanSetting drawShadow = add(new BooleanSetting("Drop Shadow", true)
            .visibleWhen(() -> style.is(Style.Setsuna)));
    public final BooleanSetting legacyShadow = add(new BooleanSetting("Shadow", true)
            .visibleWhen(() -> !style.is(Style.Setsuna) && !style.is(Style.Flux)));
    public final DoubleSetting shadowBlur = add(new DoubleSetting("Shadow Blur", 14.0, 0.1, 40.0, 0.5)
            .visibleWhen(() -> !style.is(Style.Flux)
                    && (style.is(Style.Setsuna) ? drawShadow.get() : legacyShadow.get())));
    public final ColorSetting shadowColor = add(new ColorSetting("Shadow Color", new Color(0, 0, 0, 140))
            .visibleWhen(() -> !style.is(Style.Flux)
                    && (style.is(Style.Setsuna) ? drawShadow.get() : legacyShadow.get())));
    public final BooleanSetting blur = add(new BooleanSetting("Blur", false));
    public final IntSetting blurStrength = add(new IntSetting("Blur Strength", 6, 1, 16, 1)
            .visibleWhen(blur::get));
    public final ColorSetting backgroundColor = add(new ColorSetting(
            "Background Color", new Color(18, 14, 30), false)
            .visibleWhen(() -> !style.is(Style.Flux)));
    public final DoubleSetting backgroundAlpha = add(new DoubleSetting(
            "Background Opacity", 180.0, 0.0, 255.0, 1.0)
            .visibleWhen(() -> !style.is(Style.Flux)));
    public final EnumSetting<ColorMode> colorMode = add(new EnumSetting<>("Color Mode", ColorMode.Sync)
            .visibleWhen(() -> !style.is(Style.Setsuna) && !style.is(Style.Novoline)
                    && !style.is(Style.Adjust) && !style.is(Style.Flux)));
    public final ColorSetting customColor = add(new ColorSetting("Custom Color", new Color(255, 50, 50))
            .visibleWhen(() -> !style.is(Style.Setsuna) && !style.is(Style.Novoline)
                    && !style.is(Style.Adjust) && !style.is(Style.Flux)
                    && colorMode.is(ColorMode.Custom)));
    public final ColorSetting novolineAccent = add(new ColorSetting("Novoline Accent",
            new Color(101, 158, 181, 255), true).visibleWhen(() -> style.is(Style.Novoline)));
    public final ColorSetting adjustHealthStart = add(new ColorSetting("Adjust Health Start",
            new Color(151, 92, 255, 255), true).visibleWhen(() -> style.is(Style.Adjust)));
    public final ColorSetting adjustHealthEnd = add(new ColorSetting("Adjust Health End",
            new Color(82, 157, 255, 255), true).visibleWhen(() -> style.is(Style.Adjust)));
    public final BooleanSetting outline = add(new BooleanSetting("Outline", false)
            .visibleWhen(() -> !style.is(Style.Setsuna) && !style.is(Style.Novoline)
                    && !style.is(Style.Adjust) && !style.is(Style.Flux)));
    public final BooleanSetting neonBorder = add(new BooleanSetting("Neon Border", true)
            .visibleWhen(() -> style.is(Style.Setsuna)));
    public final DoubleSetting borderThickness = add(new DoubleSetting("Border Thickness", 1.6, 0.5, 4.0, 0.1)
            .visibleWhen(() -> style.is(Style.Setsuna) && neonBorder.get()));
    public final DoubleSetting healthBarHeight = add(new DoubleSetting("Bar Height", 8.5, 6.0, 24.0, 0.5)
            .visibleWhen(() -> false));
    public final DoubleSetting healthBarRadius = add(new DoubleSetting("Bar Radius", 4.5, 0.0, 12.0, 0.5)
            .visibleWhen(() -> false));
    public final BooleanSetting delayBar = add(new BooleanSetting("Delay Bar", false)
            .visibleWhen(() -> style.is(Style.Setsuna)));
    public final BooleanSetting delayWait = add(new BooleanSetting("Delay Wait", true)
            .visibleWhen(() -> style.is(Style.Setsuna) && delayBar.get()));
    public final DoubleSetting delayTime = add(new DoubleSetting("Delay Time", 250.0, 0.0, 500.0, 20.0)
            .visibleWhen(() -> style.is(Style.Setsuna) && delayBar.get() && delayWait.get()));
    public final DoubleSetting delaySpeed = add(new DoubleSetting("Delay Speed", 2.0, 0.1, 10.0, 0.1)
            .visibleWhen(() -> style.is(Style.Setsuna) && delayBar.get()));
    public final BooleanSetting showHearts = add(new BooleanSetting("Show Hearts", true)
            .visibleWhen(() -> false));
    public final BooleanSetting showEquipment = add(new BooleanSetting("Show Equipment", false)
            .visibleWhen(() -> style.is(Style.Setsuna)));
    public final BooleanSetting showChestBadge = add(new BooleanSetting("Show Chest Badge", false)
            .visibleWhen(() -> style.is(Style.Setsuna)));
    public final BooleanSetting showPing = add(new BooleanSetting("Show Ping", true)
            .visibleWhen(() -> style.is(Style.Setsuna)));
    public final BooleanSetting showDistance = add(new BooleanSetting("Show Distance", true)
            .visibleWhen(() -> style.is(Style.Setsuna) || style.is(Style.ModelPreview)));
    public final ColorSetting borderColor = add(new ColorSetting("Border Color", new Color(160, 100, 255, 220))
            .visibleWhen(() -> style.is(Style.Setsuna) && neonBorder.get()));
    public final ColorSetting accentStart = add(new ColorSetting("Health Start", new Color(196, 130, 255, 255))
            .visibleWhen(() -> style.is(Style.Setsuna)));
    public final ColorSetting accentEnd = add(new ColorSetting("Health End", new Color(96, 165, 250, 255))
            .visibleWhen(() -> style.is(Style.Setsuna)));
    public final ColorSetting textColor = add(new ColorSetting("Text Color", new Color(238, 233, 255, 250))
            .visibleWhen(() -> style.is(Style.Setsuna)
                    || style.is(Style.ModelPreview) || style.is(Style.Novoline)
                    || style.is(Style.Adjust)));
    public final ColorSetting mutedTextColor = add(new ColorSetting("Muted Text Color", new Color(184, 176, 220, 220))
            .visibleWhen(() -> style.is(Style.Setsuna) || style.is(Style.ModelPreview)));
    public final ColorSetting heartColor = add(new ColorSetting("Heart Color", new Color(232, 68, 92, 255))
            .visibleWhen(() -> false));
    public final ColorSetting heartEmptyColor = add(new ColorSetting("Heart Empty Color", new Color(60, 45, 85, 210))
            .visibleWhen(() -> false));
    public final ColorSetting delayBarColor = add(new ColorSetting("Delay Bar Color", new Color(255, 255, 255, 90))
            .visibleWhen(() -> style.is(Style.Setsuna) && delayBar.get()));

    private static final long VISIBILITY_ANIMATION_DURATION_MS = 260L;
    private static final float SKIJA_FONT_UNIT = 12.15F;
    private static final long FRAME_MAX_AGE_NS = 500_000_000L;
    private static final float FLUX_MIN_CONTENT_WIDTH = 75.0F;
    private static final float FLUX_HEIGHT = 28.0F;
    private static final float MODEL_PREVIEW_WIDTH = 150.0F;
    private static final float MODEL_PREVIEW_HEIGHT = 32.0F;
    private static final float NOVOLINE_WIDTH = 184.0F;
    private static final float NOVOLINE_HEIGHT = 69.0F;
    private static final float ADJUST_WIDTH = 200.0F;
    private static final float ADJUST_HEIGHT = 58.0F;
    private static final long NOVOLINE_DAMAGE_HOLD_MS = 180L;
    private static final float NOVOLINE_DELAY_RESPONSE = 3.2F;
    private static final long MAX_BACKGROUND_BYTES = 24L * 1024L * 1024L;
    private static final long MAX_BACKGROUND_PIXELS = 32_000_000L;
    private static final Paint AA_PAINT = new Paint().setAntiAlias(true);
    private final ItemStack[] equipment = new ItemStack[5];
    private LivingEntity renderedTarget;
    private int lastTargetId = Integer.MIN_VALUE;
    private float visibilityProgress;
    private float displayedHealth;
    private float delayedHealth;
    private float lastKnownHealth = -1.0F;
    private float lastKnownMaximum = 1.0F;
    private long lastDamageTimeMs;
    private long lastVisibilityUpdateMs;
    private Canvas skijaCanvas;
    private List<TextureHole> collectingTextureHoles;
    private FrameState frameState;
    private Image customBackgroundImage;
    private boolean customBackgroundLoaded;

    private TargetHud() {
        super("Target HUD", Category.RENDER, 500, 610, 220.0F, 72.0F);
        setEnabled(true);
    }

    @Override
    protected void onDisable() {
        renderedTarget = null;
        visibilityProgress = 0.0F;
        lastVisibilityUpdateMs = 0L;
        frameState = null;
        closeCustomBackground();
        resetAnimatedState();
    }

    @Listen(priority = Priority.HIGH)
    private void onPacket(PacketEvent.Receive event) {
        if (mc != null && mc.player != null
                && healthDetection.is(HealthDetection.Heypixel)
                && event.getPacket() instanceof ClientboundSetHealthPacket healthPacket
                && HeypixelHealthSource.shouldCancelLocalHealth(healthPacket.getHealth())) {
            event.cancel();
        }
    }

    public Float getHeypixelHealth(Player player) {
        return HealthManager.INSTANCE.getHeypixelHealth(player);
    }

    @Listen(priority = Priority.HIGH)
    private void onVanillaHud(VanillaHudRenderEvent event) {
        if (noPlayer()) {
            renderedTarget = null;
            visibilityProgress = 0.0F;
            frameState = null;
            return;
        }

        LivingEntity liveTarget = resolveTarget();
        LivingEntity target = updateRenderedTarget(liveTarget);
        float animation = easeOutSine(visibilityProgress);
        if (target == null || animation <= 0.01F) {
            frameState = null;
            return;
        }

        float maximum;
        if (liveTarget == target) {
            float detectedHealth = getDisplayHealth(target);
            // Let the tab score drive the maximum too so the number never gets
            // clamped below what NameTags shows on servers whose LIST-slot health
            // objective exceeds the vanilla max HP.
            maximum = Math.max(1.0F, Math.max(
                    target.getMaxHealth() + Math.max(0.0F, target.getAbsorptionAmount()), detectedHealth));
            lastKnownMaximum = maximum;
            float frameTime = event.deltaTracker() == null
                    ? 0.05F : event.deltaTracker().getGameTimeDeltaTicks() / 20.0F;
            updateAnimatedHealth(target, detectedHealth, maximum,
                    Mth.clamp(frameTime, 0.0F, 0.1F));
        } else {
            maximum = Math.max(1.0F, lastKnownMaximum);
            displayedHealth = Mth.clamp(displayedHealth, 0.0F, maximum);
            delayedHealth = Mth.clamp(delayedHealth, 0.0F, maximum);
        }

        float renderScale = scale.get().floatValue();
        Bounds bounds = measuredBounds(renderScale, target);
        float width = Math.min(bounds.width(), Math.max(4.0F, event.width() - 12.0F));
        float height = Math.min(bounds.height(), Math.max(4.0F, event.height() - 12.0F));
        float x = renderX(event.width(), width);
        float y = renderY(event.height(), height);
        updateBounds(width, height);

        if (style.is(Style.Novoline)) {
            renderNovolineNative(event.graphics(), target, x, y, width, height,
                    renderScale, animation, maximum);
            frameState = null;
            return;
        }
        if (style.is(Style.Adjust)) {
            renderAdjustNative(event.graphics(), target, x, y, width, height,
                    renderScale, animation, maximum);
            frameState = null;
            return;
        }

        List<TextureHole> holes = new ArrayList<>();
        collectingTextureHoles = holes;
        try {
            if (style.is(Style.Setsuna)) {
                renderSetsuna(event.graphics(), target, x, y, width, height, renderScale, animation, maximum);
            } else {
                renderMyau(event.graphics(), target, x, y, width, height, renderScale, animation, maximum);
            }
        } finally {
            collectingTextureHoles = null;
        }
        frameState = new FrameState(target, x, y, width, height, renderScale,
                animation, maximum, List.copyOf(holes), System.nanoTime());
    }

    @Listen(priority = Priority.HIGH)
    private void onRender2D(Render2DEvent event) {
        FrameState state = frameState;
        if (state == null || System.nanoTime() - state.createdAtNs() > FRAME_MAX_AGE_NS) return;

        Canvas canvas = event.canvas();
        int opacityAlpha = opacityAlpha();
        int save = opacityAlpha >= 255
                ? canvas.save()
                : canvas.saveLayerAlpha(null, Math.max(0, opacityAlpha));
        skijaCanvas = canvas;
        if (style.is(Style.Setsuna) && !state.textureHoles().isEmpty()) {
            drawSetsunaPanelSurface(state.x(), state.y(), state.width(), state.height(),
                    state.scale(), state.animation());
        }
        for (TextureHole hole : state.textureHoles()) {
            canvas.clipRect(Rect.makeXYWH(hole.x(), hole.y(), hole.width(), hole.height()),
                    ClipMode.DIFFERENCE, false);
        }
        try {
            if (style.is(Style.Setsuna)) {
                renderSetsuna(null, state.target(), state.x(), state.y(), state.width(), state.height(),
                        state.scale(), state.animation(), state.maximum(),
                        state.textureHoles().isEmpty());
            } else {
                renderMyau(null, state.target(), state.x(), state.y(), state.width(), state.height(),
                        state.scale(), state.animation(), state.maximum());
            }
        } finally {
            skijaCanvas = null;
            canvas.restoreToCount(save);
        }
    }

    @Override
    protected boolean usesSharedOpacityLayer() {
        return false;
    }

    /** Draws a fitted, read-only sample for ClickGUI inspectors. */
    public void renderPreview(Canvas canvas, float x, float y, float width, float height) {
        if (canvas == null || width <= 1.0F || height <= 1.0F) return;
        LivingEntity target = mc.player;
        Canvas previousCanvas = skijaCanvas;
        float previousDisplayedHealth = displayedHealth;
        float previousDelayedHealth = delayedHealth;
        float previousMaximum = lastKnownMaximum;
        int save = opacityAlpha() >= 255
                ? canvas.save()
                : canvas.saveLayerAlpha(null, Math.max(0, opacityAlpha()));
        try {
            skijaCanvas = canvas;
            if (target == null) {
                float fallbackWidth = Math.min(width, 150.0F);
                float fallbackHeight = Math.min(height, 48.0F);
                float fallbackX = x + (width - fallbackWidth) * 0.5F;
                float fallbackY = y + (height - fallbackHeight) * 0.5F;
                roundedRect(null, fallbackX, fallbackY, fallbackWidth, fallbackHeight,
                        7.0F, 0xE6180F24);
                String label = "Target HUD";
                drawText(null, label,
                        fallbackX + (fallbackWidth - textWidth(label, 0.58F)) * 0.5F,
                        fallbackY + (fallbackHeight - textHeight(0.58F)) * 0.5F,
                        0.58F, textColor.argb(), false);
                return;
            }

            float maximum = Math.max(1.0F,
                    target.getMaxHealth() + Math.max(0.0F, target.getAbsorptionAmount()));
            displayedHealth = maximum * 0.72F;
            delayedHealth = maximum * 0.84F;
            lastKnownMaximum = maximum;

            Bounds base = measuredBounds(1.0F, target);
            float fitScale = Math.min(width / base.width(), height / base.height());
            fitScale = Math.max(0.05F, fitScale);
            float drawWidth = base.width() * fitScale;
            float drawHeight = base.height() * fitScale;
            float drawX = x + (width - drawWidth) * 0.5F;
            float drawY = y + (height - drawHeight) * 0.5F;
            if (style.is(Style.Setsuna)) {
                renderSetsuna(null, target, drawX, drawY, drawWidth, drawHeight,
                        fitScale, 1.0F, maximum);
            } else {
                renderMyau(null, target, drawX, drawY, drawWidth, drawHeight,
                        fitScale, 1.0F, maximum);
                if (style.is(Style.Novoline)) {
                    float contentX = drawX + 69.0F * fitScale;
                    drawText(null, fitText(target.getName().getString(), 0.62F * fitScale,
                                    Math.max(1.0F, drawWidth - 74.0F * fitScale)),
                            contentX, drawY + 4.0F * fitScale, 0.62F * fitScale,
                            textColor.argb(), false);
                    String health = String.format(Locale.ROOT, "%.1f", displayedHealth);
                    drawText(null, health, contentX, drawY + 49.0F * fitScale,
                            0.48F * fitScale, textColor.argb(), false);
                    drawText(null, "\u2665",
                            contentX + textWidth(health, 0.48F * fitScale) + 3.0F * fitScale,
                            drawY + 49.0F * fitScale, 0.48F * fitScale,
                            novolineAccent.argb(), false);
                } else if (style.is(Style.Adjust)) {
                    renderAdjustPreviewDetails(target, drawX, drawY, drawWidth,
                            drawHeight, fitScale, 1.0F);
                }
            }
        } finally {
            skijaCanvas = previousCanvas;
            displayedHealth = previousDisplayedHealth;
            delayedHealth = previousDelayedHealth;
            lastKnownMaximum = previousMaximum;
            canvas.restoreToCount(save);
        }
    }

    private Bounds measuredBounds(float renderScale, LivingEntity target) {
        if (style.is(Style.Flux)) {
            String name = target == null ? "" : target.getName().getString();
            String health = String.format(Locale.ROOT, "%.1f hp", displayedHealth);
            float renderedNameWidth = textWidth(name, 0.52F);
            float measuredNameWidth = textWidth(name, 0.62F);
            float healthWidth = textWidth(health, 0.42F);
            float contentWidth = fluxContentWidth(
                    renderedNameWidth, measuredNameWidth, healthWidth);
            return new Bounds((28.0F + contentWidth) * renderScale,
                    FLUX_HEIGHT * renderScale);
        }
        if (style.is(Style.ModelPreview)) {
            return new Bounds(MODEL_PREVIEW_WIDTH * renderScale,
                    MODEL_PREVIEW_HEIGHT * renderScale);
        }
        if (style.is(Style.Novoline)) {
            return new Bounds(NOVOLINE_WIDTH * renderScale,
                    NOVOLINE_HEIGHT * renderScale);
        }
        if (style.is(Style.Adjust)) {
            return new Bounds(ADJUST_WIDTH * renderScale,
                    ADJUST_HEIGHT * renderScale);
        }
        return new Bounds(panelWidth.get().floatValue() * renderScale,
                panelHeight.get().floatValue() * renderScale);
    }

    private void renderSetsuna(GuiGraphicsExtractor graphics, LivingEntity target,
                             float x, float y, float width, float height,
                             float s, float animation, float maximum) {
        renderSetsuna(graphics, target, x, y, width, height, s, animation, maximum, true);
    }

    private void renderSetsuna(GuiGraphicsExtractor graphics, LivingEntity target,
                               float x, float y, float width, float height,
                               float s, float animation, float maximum,
                               boolean drawPanelSurface) {
        float centerX = x + width * 0.5F;
        float centerY = y + height * 0.5F;
        if (drawPanelSurface) drawSetsunaPanelSurface(x, y, width, height, s, animation);

        float pad = 8.0F * s;
        float headSize = Math.min(44.0F * s, Math.max(4.0F, height - pad * 2.0F));
        float headX = x + pad;
        float headY = y + (height - headSize) * 0.5F;
        float animatedHeadX = Mth.lerp(animation, centerX, headX);
        float animatedHeadY = Mth.lerp(animation, centerY, headY);
        float animatedHeadSize = headSize * animation;
        roundedRect(graphics, animatedHeadX, animatedHeadY, animatedHeadSize, animatedHeadSize,
                8.0F * s * animation, withAlpha(0xDC1C162E, animation));
        drawHead(graphics, target, animatedHeadX, animatedHeadY, animatedHeadSize, animation);

        ItemStack chest = target instanceof Player player
                ? player.getItemBySlot(EquipmentSlot.CHEST) : ItemStack.EMPTY;
        if (showChestBadge.get() && !chest.isEmpty()) {
            float badge = 18.0F * s * animation;
            float badgeX = animatedHeadX - badge * 0.3F;
            float badgeY = animatedHeadY + animatedHeadSize - badge - 2.0F * s * animation;
            drawItem(graphics, chest, badgeX, badgeY, badge / 16.0F, 0x00000000);
        }

        float ringSize = Math.min(46.0F * s, Math.max(8.0F, height - pad * 2.0F));
        float ringX = x + width - pad - ringSize;
        float ringY = y + (height - ringSize) * 0.5F;
        float bodyX = headX + headSize + pad;
        float bodyWidth = Math.max(20.0F, ringX - 8.0F * s - bodyX);
        float nameScale = 0.72F * s;
        float smallScale = 0.46F * s;
        float nameHeight = textHeight(nameScale);
        float rowGap = 5.0F * s;
        float equipmentSize = 14.0F * s;
        float stackHeight = showEquipment.get()
                ? nameHeight + rowGap + equipmentSize : nameHeight;
        float stackY = y + (height - stackHeight) * 0.5F;
        float nameY = stackY;
        float equipmentY = nameY + nameHeight + rowGap;

        float animatedBodyX = Mth.lerp(animation, centerX, bodyX);
        float animatedNameY = Mth.lerp(animation, centerY, nameY);
        float animatedBodyWidth = bodyWidth * animation;
        float animatedNameScale = nameScale * animation;
        float animatedSmallScale = smallScale * animation;

        StringBuilder meta = new StringBuilder();
        if (showDistance.get()) {
            meta.append(String.format(Locale.ROOT, "%.1fm", mc.player.distanceTo(target)));
        }
        if (showPing.get()) {
            int ping = ping(target);
            if (!meta.isEmpty()) meta.append(" / ");
            meta.append(ping < 0 ? "-ms" : ping + "ms");
        }
        String metaText = meta.toString();
        float metaWidth = metaText.isEmpty() ? 0.0F : textWidth(metaText, animatedSmallScale);
        float metaGap = metaText.isEmpty() ? 0.0F : 5.0F * s * animation;
        String name = fitText(target.getName().getString(), animatedNameScale,
                Math.max(1.0F, animatedBodyWidth - metaWidth - metaGap));
        drawText(graphics, name, animatedBodyX, animatedNameY, animatedNameScale,
                withAlpha(textColor.argb(), animation), false);
        if (!metaText.isEmpty()) {
            drawText(graphics, metaText,
                    animatedBodyX + animatedBodyWidth - metaWidth,
                    animatedNameY + (textHeight(animatedNameScale)
                            - textHeight(animatedSmallScale)) * 0.5F,
                    animatedSmallScale, withAlpha(mutedTextColor.argb(), animation), false);
        }

        float ratio = healthRatio(maximum);
        float delayedRatio = Mth.clamp(delayedHealth / Math.max(1.0F, maximum), 0.0F, 1.0F);

        float animatedRingX = Mth.lerp(animation, centerX, ringX);
        float animatedRingY = Mth.lerp(animation, centerY, ringY);
        float animatedRingSize = ringSize * animation;
        drawSetsunaHealthRing(animatedRingX, animatedRingY, animatedRingSize,
                ratio, delayedRatio, animation);

        if (showEquipment.get()) {
            drawEquipment(graphics, target, animatedBodyX,
                    Mth.lerp(animation, centerY, equipmentY),
                    animatedBodyWidth, equipmentSize * animation);
        }

        if (neonBorder.get()) {
            float panelX = Mth.lerp(animation, centerX, x);
            float panelY = Mth.lerp(animation, centerY, y);
            float panelW = width * animation;
            float panelH = height * animation;
            float corner = radius.get().floatValue() * s * animation;
            roundedOutline(graphics, panelX, panelY, panelW, panelH, corner,
                    borderThickness.get().floatValue() * s * animation,
                    withAlpha(borderColor.argb(), animation));
        }
    }

    private void drawSetsunaPanelSurface(float x, float y, float width, float height,
                                         float s, float animation) {
        float centerX = x + width * 0.5F;
        float centerY = y + height * 0.5F;
        float panelX = Mth.lerp(animation, centerX, x);
        float panelY = Mth.lerp(animation, centerY, y);
        float panelW = width * animation;
        float panelH = height * animation;
        float corner = radius.get().floatValue() * s * animation;
        if (activeBlurStrength() > 0.01F) {
            blurBackdrop(panelX, panelY, panelW, panelH, corner,
                    activeBlurStrength());
        }
        if (drawShadow.get()) {
            drawShadow(null, panelX, panelY, panelW, panelH, corner,
                    shadowBlur.get().floatValue() * animation,
                    withAlpha(shadowColor.argb(), animation));
        }
        if (!drawCustomBackground(panelX, panelY, panelW, panelH, corner, animation)) {
            roundedRect(null, panelX, panelY, panelW, panelH, corner,
                    legacyBackground(backgroundColor.argb(), animation,
                            activeBlurStrength() > 0.01F ? 0.28F : 1.0F));
        }
    }

    private void importSetsunaBackground() {
        String selected = TinyFileDialogs.tinyfd_openFileDialog(
                "Import Target HUD background",
                mc.gameDirectory.getAbsolutePath(),
                null,
                "PNG, JPG, BMP, or GIF image",
                false);
        if (selected == null || selected.isBlank()) return;

        Path source = Path.of(selected);
        Path target = customBackgroundPath();
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            if (!Files.isRegularFile(source)) {
                throw new IOException("The selected image does not exist.");
            }
            long bytes = Files.size(source);
            if (bytes <= 0L || bytes > MAX_BACKGROUND_BYTES) {
                throw new IOException("The image must be smaller than 24 MB.");
            }
            BufferedImage decoded = decodeBackground(source);
            Files.createDirectories(target.getParent());
            if (!ImageIO.write(decoded, "png", temporary.toFile())) {
                throw new IOException("The image could not be converted to PNG.");
            }
            Image replacement = Image.makeFromEncoded(Files.readAllBytes(temporary));
            if (replacement == null) {
                throw new IOException("The converted image could not be loaded.");
            }
            try {
                moveReplacing(temporary, target);
            } catch (IOException exception) {
                replacement.close();
                throw exception;
            }
            replaceCustomBackground(replacement);
            customBackgroundLoaded = true;
        } catch (IOException | RuntimeException exception) {
            Setsuna.LOGGER.warn("Unable to import Target HUD background {}", source, exception);
            showBackgroundError(exception.getMessage());
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }

    private void resetSetsunaBackground() {
        try {
            Files.deleteIfExists(customBackgroundPath());
            closeCustomBackground();
        } catch (IOException exception) {
            Setsuna.LOGGER.warn("Unable to reset Target HUD background", exception);
            showBackgroundError(exception.getMessage());
        }
    }

    private boolean drawCustomBackground(float x, float y, float width, float height,
                                         float radius, float animation) {
        if (skijaCanvas == null || width <= 0.0F || height <= 0.0F) return false;
        Image image = customBackground();
        if (image == null) return false;

        float imageWidth = image.getWidth();
        float imageHeight = image.getHeight();
        float viewportAspect = width / height;
        float imageAspect = imageWidth / imageHeight;
        float sourceWidth = imageWidth;
        float sourceHeight = imageHeight;
        if (viewportAspect > imageAspect) sourceHeight = imageWidth / viewportAspect;
        else sourceWidth = imageHeight * viewportAspect;
        float sourceX = (imageWidth - sourceWidth) * 0.5F;
        float sourceY = (imageHeight - sourceHeight) * 0.5F;
        float safeRadius = Math.max(0.0F, Math.min(radius, Math.min(width, height) * 0.5F));

        skijaCanvas.save();
        try {
            skijaCanvas.clipRRect(RRect.makeXYWH(x, y, width, height, safeRadius),
                    ClipMode.INTERSECT, true);
            AA_PAINT.setMode(PaintMode.FILL).setShader(null).setImageFilter(null)
                    .setColor(0xFFFFFFFF).setAlpha(Math.round(backgroundAlpha.get().floatValue()
                            * animation * imageOpacity.get() / 100.0F));
            skijaCanvas.drawImageRect(image,
                    Rect.makeXYWH(sourceX, sourceY, sourceWidth, sourceHeight),
                    Rect.makeXYWH(x, y, width, height),
                    SamplingMode.MITCHELL, AA_PAINT, true);
        } finally {
            AA_PAINT.setAlpha(255);
            skijaCanvas.restore();
        }
        return true;
    }

    private Image customBackground() {
        if (!customBackgroundLoaded) {
            customBackgroundLoaded = true;
            Path file = customBackgroundPath();
            if (Files.isRegularFile(file)) {
                try {
                    customBackgroundImage = Image.makeFromEncoded(Files.readAllBytes(file));
                } catch (IOException | RuntimeException exception) {
                    Setsuna.LOGGER.warn("Unable to load Target HUD background {}", file, exception);
                }
            }
        }
        return customBackgroundImage;
    }

    private boolean hasCustomBackground() {
        return customBackgroundImage != null || Files.isRegularFile(customBackgroundPath());
    }

    private void replaceCustomBackground(Image replacement) {
        Image previous = customBackgroundImage;
        customBackgroundImage = replacement;
        if (previous != null && previous != replacement) previous.close();
    }

    private void closeCustomBackground() {
        if (customBackgroundImage != null) {
            customBackgroundImage.close();
            customBackgroundImage = null;
        }
        customBackgroundLoaded = false;
    }

    private static BufferedImage decodeBackground(Path source) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(source.toFile())) {
            if (stream == null) throw new IOException("The selected file is not a supported image.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IOException("Use a PNG, JPG, BMP, or GIF image.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_BACKGROUND_PIXELS) {
                    throw new IOException("The image resolution is too large.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw new IOException("The selected image could not be decoded.");
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    private static Path customBackgroundPath() {
        return Setsuna.mc().gameDirectory.toPath()
                .resolve(Setsuna.MOD_ID)
                .resolve("assets")
                .resolve("target-hud-background.png");
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void showBackgroundError(String detail) {
        TinyFileDialogs.tinyfd_messageBox(
                "Target HUD background",
                detail == null || detail.isBlank()
                        ? "The selected image could not be imported." : detail,
                "ok", "error", 1);
    }

    private void renderMyau(GuiGraphicsExtractor graphics, LivingEntity target,
                             float x, float y, float width, float height,
                             float s, float animation, float maximum) {
        int accent = myauAccent(0L);
        if (activeBlurStrength() > 0.01F) {
            if (style.is(Style.Flux)) {
                float inset = 0.5F * s;
                blurBackdrop(x + inset, y + inset,
                        Math.max(1.0F, width - 2.0F * inset),
                        Math.max(1.0F, height - 2.0F * inset),
                        2.0F * s, activeBlurStrength());
            } else {
                float blurRadius = style.is(Style.Novoline) ? 0.0F : 8.0F * s;
                blurBackdrop(x, y, width, height, blurRadius,
                        activeBlurStrength());
            }
        }
        if (legacyShadow.get() && !style.is(Style.Flux)) {
            float shadowRadius = style.is(Style.Novoline) ? 0.0F : 8.0F * s;
            drawShadow(graphics, x, y, width, height, shadowRadius,
                    Math.max(2.0F, shadowBlur.get().floatValue() * 0.55F),
                    withAlpha(shadowColor.argb(), animation));
        }
        switch (style.get()) {
            case Flux -> renderFlux(graphics, target, x, y, width, height, s, animation, maximum);
            case Default -> renderDefault(graphics, target, x, y, width, height, s, animation, maximum, accent);
            case ModelPreview -> renderModelPreview(graphics, target, x, y, width, height, s, animation, maximum);
            case Novoline -> renderNovoline(graphics, target, x, y, width, height, s, animation, maximum);
            case Adjust -> renderAdjustPanel(graphics, target, x, y, width, height, s, animation, maximum);
            default -> {
            }
        }
    }

    /** Flux 1.8.9 layout: avatar, inline health, and compact health/armor tracks. */
    private void renderFlux(GuiGraphicsExtractor graphics, LivingEntity target,
                            float x, float y, float w, float h, float s,
                            float animation, float maximum) {
        float corner = 2.0F * s;
        float surfaceAlpha = activeBlurStrength() > 0.01F ? 0.4F : 0.6F;
        float borderAlpha = activeBlurStrength() > 0.01F ? 0.3F : 0.5F;
        roundedRect(graphics, x, y, w, h, corner,
                withAlpha(0xFF000000, animation * surfaceAlpha));
        roundedOutline(graphics, x, y, w, h, corner, Math.max(0.5F, s),
                withAlpha(0xFF000000, animation * borderAlpha));

        float headSize = Math.min(24.0F * s, Math.max(2.0F, h - 4.0F * s));
        float headX = x + 2.0F * s;
        float headY = y + (h - headSize) * 0.5F;
        drawHead(graphics, target, headX, headY, headSize, animation, 0.0F);
        roundedOutline(graphics, headX - 0.5F * s, headY - 0.5F * s,
                headSize + s, headSize + s, 0.0F, Math.max(0.5F, 0.5F * s),
                withAlpha(0xFF00FF00, animation));

        float nameScale = 0.52F * s;
        float smallScale = 0.42F * s;
        float nameX = x + 30.0F * s;
        String healthText = String.format(Locale.ROOT, "%.1f hp", displayedHealth);
        float healthWidth = textWidth(healthText, smallScale);
        float healthX = x + w - 4.0F * s - healthWidth;
        String name = fitText(target.getName().getString(), nameScale,
                Math.max(1.0F, healthX - nameX - 4.0F * s));
        drawText(graphics, name, nameX, y + 3.0F * s, nameScale,
                withAlpha(0xFFFFFFFF, animation), false);
        drawText(graphics, healthText, healthX, y + 4.0F * s, smallScale,
                withAlpha(0xFFCCCCCC, animation), false);

        float barX = x + 37.0F * s;
        float barRight = Math.max(barX + s, x + w - 4.0F * s);
        float barWidth = Math.max(s, barRight - barX);
        float healthY = y + 14.5F * s;
        float armorY = y + 21.5F * s;
        float barHeight = Math.max(1.0F, 3.0F * s);
        int trackColor = withAlpha(0xFF000000, animation * 0.35F);
        fillRect(graphics, barX, healthY, barWidth, barHeight, trackColor);
        fillRect(graphics, barX, armorY, barWidth, barHeight, trackColor);

        float ratio = healthRatio(maximum);
        float delayedRatio = Mth.clamp(delayedHealth / Math.max(1.0F, maximum), 0.0F, 1.0F);
        int healthColor = fluxHealthColor(ratio);
        if (delayedRatio > ratio) {
            fillRect(graphics, barX, healthY, barWidth * delayedRatio, barHeight,
                    withAlpha(mixColor(healthColor, 0xFF000000, 0.30F), animation));
        }
        fillRect(graphics, barX, healthY, barWidth * ratio, barHeight,
                withAlpha(healthColor, animation));

        float armorRatio = Mth.clamp(target.getArmorValue() / 20.0F, 0.0F, 1.0F);
        fillRect(graphics, barX, armorY, barWidth * armorRatio, barHeight,
                withAlpha(0xFF4286F5, animation));
        drawHeart(graphics, x + 29.5F * s, y + 13.5F * s,
                5.0F * s, 5.0F * s, withAlpha(0xFFFFFFFF, animation));
        drawShield(x + 29.5F * s, y + 20.5F * s,
                5.0F * s, 5.0F * s, withAlpha(0xFFFFFFFF, animation));
    }

    private void renderDefault(GuiGraphicsExtractor g, LivingEntity target,
                               float x, float y, float w, float h, float s,
                               float animation, float maximum, int accent) {
        float radius = 6.0F * s;
        roundedRect(g, x, y, w, h, radius,
                legacyBackground(backgroundColor.argb(), animation));
        float pad = 6.0F * s;
        float head = h - pad * 2.0F;
        drawHead(g, target, x + pad, y + pad, head, animation);
        float textX = x + pad + head + pad;
        float nameScale = 0.62F * s;
        drawText(g, target.getName().getString(), textX, y + pad, nameScale,
                withAlpha(0xFFFFFFFF, animation), false);
        float ratio = healthRatio(maximum);
        float barY = y + h - pad - 6.0F * s;
        float barW = Math.max(2.0F, w - (textX - x) - pad);
        roundedRect(g, textX, barY, barW, 6.0F * s, 3.0F * s,
                withAlpha(0x78000000, animation));
        roundedRect(g, textX, barY, Math.max(2.0F, barW * ratio), 6.0F * s, 3.0F * s,
                withAlpha(healthColor(ratio), animation));
        drawText(g, String.format(Locale.ROOT, "%.1f\u2764", displayedHealth),
                textX, y + pad + textHeight(nameScale) + 2.0F * s,
                0.5F * s, withAlpha(accent, animation), false);
        if (outline.get()) roundedOutline(g, x, y, w, h, radius, 1.5F * s, withAlpha(accent, animation));
    }

    /** Compact card with a target-specific player head, adapted from the pasted PVP HUD style. */
    private void renderModelPreview(GuiGraphicsExtractor g, LivingEntity target,
                                    float x, float y, float w, float h, float s,
                                    float animation, float maximum) {
        float corner = 4.0F * s;
        roundedRect(g, x, y, w, h, corner,
                legacyBackground(backgroundColor.argb(), animation));
        fillRect(g, x, y, Math.min(2.0F * s, w), h,
                withAlpha(myauAccent(0L), animation));

        float pad = 3.0F * s;
        float headSize = Math.min(24.0F * s, Math.max(4.0F, h - pad * 2.0F));
        float headX = x + 2.0F * s + pad;
        float headY = y + (h - headSize) * 0.5F;
        boolean playerTarget = target instanceof Player;
        float contentX = playerTarget ? headX + headSize + 4.0F * s : headX;
        float rightX = x + w - pad;

        if (playerTarget) {
            drawHead(g, target, headX, headY, headSize, animation);
        }

        float smallScale = 0.45F * s;
        String distance = String.format(Locale.ROOT, "%.1fm", mc.player.distanceTo(target));
        float distanceWidth = showDistance.get() ? textWidth(distance, smallScale) : 0.0F;
        float nameMaxWidth = Math.max(1.0F,
                rightX - contentX - distanceWidth - (showDistance.get() ? 3.0F * s : 0.0F));
        String name = fitText(target.getName().getString(), 0.55F * s, nameMaxWidth);
        drawText(g, name, contentX, y + 3.0F * s, 0.55F * s,
                withAlpha(textColor.argb(), animation), false);
        if (showDistance.get()) {
            drawText(g, distance, rightX - distanceWidth, y + 3.0F * s,
                    smallScale, withAlpha(mutedTextColor.argb(), animation), false);
        }

        String health = String.format(Locale.ROOT, "HP: %.1f/%.1f", displayedHealth, maximum);
        drawText(g, health, contentX, y + 14.0F * s, 0.42F * s,
                withAlpha(healthColor(healthRatio(maximum)), animation), false);

        float barX = contentX;
        float barY = y + h - 7.0F * s;
        float barWidth = Math.max(2.0F, rightX - barX);
        float ratio = healthRatio(maximum);
        roundedRect(g, barX, barY, barWidth, 4.0F * s, 2.0F * s,
                withAlpha(0x80444444, animation));
        roundedRect(g, barX, barY, Math.max(2.0F, barWidth * ratio), 4.0F * s,
                2.0F * s, withAlpha(healthColor(ratio), animation));

        if (outline.get()) {
            roundedOutline(g, x, y, w, h, corner, 1.0F * s,
                    withAlpha(myauAccent(0L), animation));
        }
    }

    private void renderNovolineNative(GuiGraphicsExtractor graphics, LivingEntity target,
                                      float x, float y, float w, float h, float s,
                                      float animation, float maximum) {
        java.util.function.Consumer<Canvas> nativePainter = canvas -> {
            int save = opacityAlpha() >= 255
                    ? canvas.save()
                    : canvas.saveLayerAlpha(null, Math.max(0, opacityAlpha()));
            skijaCanvas = canvas;
            try {
                if (activeBlurStrength() > 0.01F) {
                    blurBackdrop(x, y, w, h, 0.0F,
                            activeBlurStrength());
                }
                if (legacyShadow.get()) {
                    drawShadow(null, x, y, w, h, 0.0F,
                            Math.max(2.0F, shadowBlur.get().floatValue() * 0.55F),
                            withAlpha(shadowColor.argb(), animation));
                }
                renderNovoline(null, target, x, y, w, h, s, animation, maximum);
            } finally {
                skijaCanvas = null;
                canvas.restoreToCount(save);
            }
        };
        if (activeBlurStrength() > 0.01F) {
            SkijaRenderer.renderMainTargetWithBackdrop(nativePainter);
        } else {
            SkijaRenderer.renderMainTarget(nativePainter);
        }

    }

    /** Pixel-tight Novoline layout matching the 184x69 reference card. */
    private void renderNovoline(GuiGraphicsExtractor g, LivingEntity target,
                                float x, float y, float w, float h, float s,
        float animation, float maximum) {
        int accent = withAlpha(novolineAccent.argb(), animation);
        fillRect(g, x, y, w, h, legacyBackground(backgroundColor.argb(), animation));
        fillRect(g, x + s, y + s, Math.max(1.0F, w - 2.0F * s), s,
                withAlpha(0xFF34393B, animation * 0.72F));

        float headFrameX = x + s;
        float headFrameY = y + 3.0F * s;
        float headFrameSize = 64.0F * s;
        fillRect(g, headFrameX, headFrameY, headFrameSize, headFrameSize,
                withAlpha(0xFF080909, animation));
        drawHead(g, target, headFrameX + s, headFrameY + s,
                62.0F * s, animation, 0.0F);
        fillRect(g, x + 65.0F * s, y + 2.0F * s, 2.0F * s,
                Math.max(1.0F, h - 4.0F * s), withAlpha(0xFF121415, animation));

        float contentX = x + 69.0F * s;
        float contentRight = x + w - 4.0F * s;
        float contentWidth = Math.max(2.0F, contentRight - contentX);

        String name = fitText(target.getName().getString(), 0.62F * s,
                Math.max(1.0F, contentWidth));
        drawText(g, name, contentX, y + 3.0F * s, 0.62F * s,
                withAlpha(textColor.argb(), animation), true);
        String healthText = String.format(Locale.ROOT, "%.1f", displayedHealth);
        drawText(g, healthText, contentX, y + 49.0F * s, 0.48F * s,
                withAlpha(textColor.argb(), animation), true);
        drawHeart(g, contentX + textWidth(healthText, 0.48F * s) + 3.0F * s,
                y + 49.0F * s, 7.0F * s, 7.0F * s,
                withAlpha(novolineAccent.argb(), animation));

        float barY = y + 30.0F * s;
        float barHeight = 13.0F * s;
        fillRect(g, contentX, barY, contentWidth, barHeight,
                withAlpha(0xFF41484B, animation));
        float innerY = barY + s;
        float innerHeight = Math.max(1.0F, barHeight - 2.0F * s);
        float delayedRatio = Mth.clamp(delayedHealth / Math.max(1.0F, maximum), 0.0F, 1.0F);
        float ratio = healthRatio(maximum);
        if (delayedRatio > ratio) {
            fillRect(g, contentX, innerY, contentWidth * delayedRatio, innerHeight,
                    withAlpha(mixColor(novolineAccent.argb(), 0xFFFFFFFF, 0.45F), animation * 0.7F));
        }
        fillRect(g, contentX, innerY, contentWidth * ratio, innerHeight, accent);
    }

    private void renderAdjustNative(GuiGraphicsExtractor graphics, LivingEntity target,
                                    float x, float y, float w, float h, float s,
                                    float animation, float maximum) {
        java.util.function.Consumer<Canvas> nativePainter = canvas -> {
            int save = opacityAlpha() >= 255
                    ? canvas.save()
                    : canvas.saveLayerAlpha(null, Math.max(0, opacityAlpha()));
            skijaCanvas = canvas;
            try {
                if (activeBlurStrength() > 0.01F) {
                    blurBackdrop(x, y, w, h, 0.0F,
                            activeBlurStrength());
                }
                if (legacyShadow.get()) {
                    drawShadow(null, x, y, w, h, 0.0F,
                            Math.max(2.0F, shadowBlur.get().floatValue() * 0.55F),
                            withAlpha(shadowColor.argb(), animation));
                }
                renderAdjustPanel(null, target, x, y, w, h, s, animation, maximum);
            } finally {
                skijaCanvas = null;
                canvas.restoreToCount(save);
            }
        };
        if (activeBlurStrength() > 0.01F) {
            SkijaRenderer.renderMainTargetWithBackdrop(nativePainter);
        } else {
            SkijaRenderer.renderMainTarget(nativePainter);
        }

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(s, s);

        equipment[0] = target.getMainHandItem();
        equipment[1] = target.getItemBySlot(EquipmentSlot.HEAD);
        equipment[2] = target.getItemBySlot(EquipmentSlot.CHEST);
        equipment[3] = target.getItemBySlot(EquipmentSlot.LEGS);
        equipment[4] = target.getItemBySlot(EquipmentSlot.FEET);
        float itemX = 49.0F;
        for (ItemStack stack : equipment) {
            if (stack == null || stack.isEmpty()) continue;
            graphics.pose().pushMatrix();
            graphics.pose().translate(itemX, 19.0F);
            graphics.pose().scale(0.95F, 0.95F);
            graphics.item(stack, 0, 0);
            graphics.pose().popMatrix();
            itemX += 17.0F;
            if (itemX > 137.0F) break;
        }
        graphics.pose().popMatrix();
    }

    private void renderAdjustPanel(GuiGraphicsExtractor graphics, LivingEntity target,
                                   float x, float y, float w, float h, float s,
                                   float animation, float maximum) {
        roundedRect(graphics, x, y, w, h, 0.0F,
                legacyBackground(backgroundColor.argb(), animation));

        float headSize = 40.0F * s;
        drawHead(graphics, target, x + 5.0F * s, y + 5.0F * s,
                headSize, animation, 0.0F);

        float contentX = x + 49.0F * s;
        String name = fitText(target.getName().getString(), 0.66F * s,
                Math.max(1.0F, w - 54.0F * s));
        drawText(graphics, name, contentX, y + 4.0F * s, 0.66F * s,
                withAlpha(textColor.argb(), animation), true);
        float localHealth = mc.player == null ? displayedHealth
                : mc.player.getHealth() + Math.max(0.0F, mc.player.getAbsorptionAmount());
        String difference = String.format(Locale.ROOT, "%.1f", localHealth - displayedHealth);
        float differenceX = x + w - 5.0F * s - textWidth(difference, 0.44F * s);
        drawText(graphics, difference, differenceX, y + 25.0F * s,
                0.44F * s,
                withAlpha(0xFFC9C9DB, animation), true);
        drawHeart(graphics, differenceX - 3.0F * s - 7.0F * s,
                y + 24.0F * s, 7.0F * s, 7.0F * s,
                withAlpha(0xFFC9C9DB, animation));

        float barX = x + 4.0F * s;
        float barY = y + h - 6.0F * s;
        float barWidth = Math.max(2.0F, w - 8.0F * s);
        float barHeight = 3.0F * s;
        roundedRect(graphics, barX, barY, barWidth, barHeight, 0.0F,
                withAlpha(0xFF3B3C50, animation));

        float ratio = healthRatio(maximum);
        float delayedRatio = Mth.clamp(delayedHealth / Math.max(1.0F, maximum), 0.0F, 1.0F);
        if (delayedRatio > ratio) {
            horizontalGradient(graphics, barX, barY, barWidth * delayedRatio, barHeight,
                    0.0F,
                    withAlpha(mixColor(adjustHealthStart.argb(), 0xFFFFFFFF, 0.35F), animation * 0.65F),
                    withAlpha(mixColor(adjustHealthEnd.argb(), 0xFFFFFFFF, 0.35F), animation * 0.65F));
        }
        horizontalGradient(graphics, barX, barY, barWidth * ratio, barHeight,
                0.0F,
                withAlpha(adjustHealthStart.argb(), animation),
                withAlpha(adjustHealthEnd.argb(), animation));
    }

    private void renderAdjustPreviewDetails(LivingEntity target,
                                            float x, float y, float w, float h,
                                            float s, float animation) {
        float contentX = x + 49.0F * s;
        drawText(null, fitText(target.getName().getString(), 0.66F * s,
                        Math.max(1.0F, w - 54.0F * s)),
                contentX, y + 2.0F * s, 0.66F * s,
                withAlpha(textColor.argb(), animation), true);
        String difference = "\u26657.6";
        drawText(null, difference,
                x + w - 5.0F * s - textWidth(difference, 0.44F * s),
                y + 25.0F * s, 0.44F * s,
                withAlpha(0xFFC9C9DB, animation), true);
        for (int index = 0; index < 5; index++) {
            float iconX = contentX + index * 17.0F * s;
            int iconColor = withAlpha(index == 0
                    ? adjustHealthStart.argb() : adjustHealthEnd.argb(), animation * 0.92F);
            roundedRect(null, iconX, y + 19.0F * s,
                    13.0F * s, 13.0F * s, 0.0F, iconColor);
        }
    }

    private void drawHead(GuiGraphicsExtractor graphics, LivingEntity target,
                          float x, float y, float size, float animation) {
        drawHead(graphics, target, x, y, size, animation,
                Math.min(8.0F, size * 0.18F));
    }

    private void drawHead(GuiGraphicsExtractor graphics, LivingEntity target,
                          float x, float y, float size, float animation,
                          float cornerRadius) {
        if (skijaCanvas == null) return;
        Identifier skin = resolveSkinTexture(target);
        if (skin != null) {
            try (SkijaRenderer.BorrowedImage borrowed = SkijaRenderer.borrowTexture(skin)) {
                if (borrowed != null) {
                    float textureWidth = borrowed.image().getWidth();
                    float textureHeight = borrowed.image().getHeight();
                    Rect destination = Rect.makeXYWH(x, y, size, size);
                    float corner = Math.max(0.0F, Math.min(cornerRadius, size * 0.5F));
                    skijaCanvas.save();
                    try {
                        skijaCanvas.clipRRect(RRect.makeXYWH(x, y, size, size, corner),
                                ClipMode.INTERSECT, true);
                        AA_PAINT.setMode(PaintMode.FILL).setShader(null)
                                .setColor(0xFFFFFFFF).setAlpha(Math.round(animation * 255.0F));
                        drawSkinLayer(borrowed, destination, textureWidth, textureHeight, 8.0F, 8.0F);
                        drawSkinLayer(borrowed, destination, textureWidth, textureHeight, 40.0F, 8.0F);
                    } finally {
                        AA_PAINT.setAlpha(255);
                        skijaCanvas.restore();
                    }
                    return;
                }
            } catch (Throwable ignored) {
                // Fall through to the initial avatar if the live skin cannot be borrowed.
            }
        }
        int fallbackAccent = style.is(Style.Novoline) ? novolineAccent.argb()
                : style.is(Style.Adjust) ? adjustHealthStart.argb()
                : style.is(Style.Flux) ? fluxHealthColor(healthRatio(lastKnownMaximum))
                : myauAccent(0L);
        roundedRect(null, x, y, size, size,
                Math.max(0.0F, Math.min(cornerRadius, size * 0.5F)),
                withAlpha(fallbackAccent, animation * 0.72F));
        String name = target.getName().getString();
        String initial = name.isBlank() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
        float textScale = Math.max(0.5F, size / SKIJA_FONT_UNIT * 0.46F);
        drawText(null, initial, x + (size - textWidth(initial, textScale)) * 0.5F,
                y + (size - textHeight(textScale)) * 0.5F, textScale,
                withAlpha(0xFFFFFFFF, animation), true);
    }

    /** Resolves the skin through the target player's UUID before local fallbacks. */
    private Identifier resolveSkinTexture(LivingEntity target) {
        if (!(target instanceof Player player)) return null;

        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(player.getUUID());
            if (info != null && info.getSkin() != null && info.getSkin().body() != null) {
                Identifier texture = info.getSkin().body().texturePath();
                if (texture != null) return texture;
            }
        }

        if (target instanceof AbstractClientPlayer clientPlayer
                && clientPlayer.getSkin() != null
                && clientPlayer.getSkin().body() != null) {
            return clientPlayer.getSkin().body().texturePath();
        }
        return null;
    }

    private void drawSkinLayer(SkijaRenderer.BorrowedImage borrowed, Rect destination,
                               float textureWidth, float textureHeight, float sourceX, float sourceY) {
        Rect source = Rect.makeLTRB(
                sourceX / 64.0F * textureWidth,
                sourceY / 64.0F * textureHeight,
                (sourceX + 8.0F) / 64.0F * textureWidth,
                (sourceY + 8.0F) / 64.0F * textureHeight);
        skijaCanvas.drawImageRect(borrowed.image(), source, destination,
                SamplingMode.DEFAULT, AA_PAINT, true);
    }

    private void drawEquipment(GuiGraphicsExtractor graphics, LivingEntity target,
                               float x, float y, float width, float itemSize) {
        if (graphics == null || itemSize < 4.0F) return;
        equipment[0] = target.getMainHandItem();
        equipment[1] = target.getItemBySlot(EquipmentSlot.HEAD);
        equipment[2] = target.getItemBySlot(EquipmentSlot.CHEST);
        equipment[3] = target.getItemBySlot(EquipmentSlot.LEGS);
        equipment[4] = target.getItemBySlot(EquipmentSlot.FEET);
        // Keep the item row deterministic: empty slots do not collapse the
        // visible icons into one another, and every icon shares one baseline.
        float gap = Math.max(1.0F, itemSize * 0.14F);
        float itemScale = itemSize / 16.0F;
        int visibleCount = 0;
        for (ItemStack stack : equipment) {
            if (stack != null && !stack.isEmpty()) visibleCount++;
        }
        if (visibleCount == 0) return;

        float rowWidth = visibleCount * itemSize + (visibleCount - 1) * gap;
        float itemX = x;
        if (rowWidth < width) {
            itemX += 0.0F;
        }
        float right = x + Math.max(itemSize, width);
        for (ItemStack stack : equipment) {
            if (stack == null || stack.isEmpty()) continue;
            if (itemX + itemSize > right + 0.01F) break;
            drawItem(graphics, stack, itemX, y, itemScale, 0x00000000);
            itemX += itemSize + gap;
        }
    }

    private void drawItem(GuiGraphicsExtractor graphics, ItemStack stack,
                          float x, float y, float itemScale, int backdropColor) {
        if (graphics == null || stack == null || stack.isEmpty() || itemScale <= 0.0F) return;
        float itemSize = 16.0F * itemScale;
        if (collectingTextureHoles != null) {
            collectingTextureHoles.add(new TextureHole(x, y, itemSize, itemSize));
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(itemScale, itemScale);
        if (((backdropColor >>> 24) & 0xFF) != 0) {
            graphics.fill(0, 0, 16, 16, backdropColor);
        }
        graphics.item(stack, 0, 0);
        graphics.itemDecorations(mc.font, stack, 0, 0);
        graphics.pose().popMatrix();
    }

    private void drawSetsunaHealthRing(float x, float y, float size,
                                       float healthRatio, float delayedRatio,
                                       float animation) {
        if (skijaCanvas == null || size <= 1.0F || animation <= 0.01F) return;
        float stroke = Math.max(1.5F, size * 0.13F);
        float inset = stroke * 0.5F + 0.5F;
        float left = x + inset;
        float top = y + inset;
        float right = x + size - inset;
        float bottom = y + size - inset;
        float startAngle = -90.0F;
        float totalSweep = 360.0F;
        float ratio = Mth.clamp(healthRatio, 0.0F, 1.0F);
        float trail = Mth.clamp(delayedRatio, ratio, 1.0F);
        float gapAngle = 8.0F;
        float healthSweep = Math.max(0.0F, totalSweep * ratio - gapAngle);
        float missingStart = startAngle + totalSweep * ratio + gapAngle * 0.5F;
        float missingSweep = Math.max(0.0F, totalSweep * (1.0F - ratio) - gapAngle);

        try {
            AA_PAINT.setAntiAlias(true).setMode(PaintMode.STROKE)
                    .setStrokeWidth(stroke).setStrokeCap(PaintStrokeCap.ROUND)
                    .setShader(null).setAlpha(255)
                    .setColor(withAlpha(0xFF625D73, animation * 0.42F));
            if (ratio <= 0.001F) {
                skijaCanvas.drawCircle(x + size * 0.5F, y + size * 0.5F,
                        Math.max(0.5F, (right - left) * 0.5F), AA_PAINT);
            } else if (ratio < 0.999F && missingSweep > 0.0F) {
                skijaCanvas.drawArc(left, top, right, bottom,
                        missingStart, missingSweep, false, AA_PAINT);
            }

            if (delayBar.get() && trail > ratio + 0.002F) {
                AA_PAINT.setColor(withAlpha(delayBarColor.argb(), animation * 0.8F));
                float trailStart = startAngle + totalSweep * ratio + gapAngle * 0.5F;
                float trailSweep = Math.max(0.0F,
                        totalSweep * (trail - ratio) - gapAngle);
                if (trailSweep > 0.0F) {
                    skijaCanvas.drawArc(left, top, right, bottom,
                            trailStart, trailSweep, false, AA_PAINT);
                }
            }

            if (ratio > 0.002F) {
                float centerX = x + size * 0.5F;
                float centerY = y + size * 0.5F;
                try (Shader shader = Shader.makeSweepGradient(centerX, centerY,
                        new int[]{withAlpha(accentStart.argb(), animation),
                                withAlpha(accentEnd.argb(), animation),
                                withAlpha(accentStart.argb(), animation)})) {
                    AA_PAINT.setShader(shader).setColor(0xFFFFFFFF);
                    if (ratio >= 0.999F) {
                        skijaCanvas.drawCircle(centerX, centerY,
                                Math.max(0.5F, (right - left) * 0.5F), AA_PAINT);
                    } else if (healthSweep > 0.0F) {
                        skijaCanvas.drawArc(left, top, right, bottom,
                                startAngle + gapAngle * 0.5F,
                                healthSweep, false, AA_PAINT);
                    }
                }
            }
        } finally {
            AA_PAINT.setShader(null).setMode(PaintMode.FILL).setStrokeWidth(1.0F)
                    .setStrokeCap(PaintStrokeCap.BUTT).setAlpha(255);
        }

        float actualHealth = Math.max(0.0F, displayedHealth);
        String health = Math.abs(actualHealth - Math.round(actualHealth)) < 0.05F
                ? Integer.toString(Math.round(actualHealth))
                : String.format(Locale.ROOT, "%.1f", actualHealth);
        float healthScale = 0.70F * size / 46.0F;
        float healthX = x + (size - textWidth(health, healthScale)) * 0.5F;
        float healthY = y + (size - textHeight(healthScale)) * 0.5F;
        drawText(null, health, healthX, healthY, healthScale,
                withAlpha(textColor.argb(), animation), false);
    }

    private void drawHearts(GuiGraphicsExtractor graphics, float x, float y,
                            float availableWidth, float heartHeight, float animation) {
        if (skijaCanvas == null) return;
        int heartCount = 10;
        float size = Math.max(4.0F, heartHeight);
        float gap = Math.max(1.0F, size * 0.18F);
        float total = heartCount * size + (heartCount - 1) * gap;
        if (total > availableWidth && total > 0.0F) {
            float shrink = availableWidth / total;
            size = Math.max(2.0F, size * shrink);
            gap = Math.max(0.5F, gap * shrink);
        }
        int shownHealth = Math.round(Math.min(displayedHealth, 20.0F));
        int emptyColor = withAlpha(heartEmptyColor.argb(), animation);
        int filledColor = withAlpha(heartColor.argb(), animation);
        for (int index = 0; index < heartCount; index++) {
            float heartX = x + index * (size + gap);
            int slotHealth = index * 2;
            SkijaUi.boldText(skijaCanvas, "\u2665", heartX, y, size, emptyColor, size);
            int remaining = shownHealth - slotHealth;
            if (remaining >= 2) {
                SkijaUi.boldText(skijaCanvas, "\u2665", heartX, y, size, filledColor, size);
            } else if (remaining == 1) {
                skijaCanvas.save();
                skijaCanvas.clipRect(Rect.makeXYWH(heartX, y, size * 0.5F, size), ClipMode.INTERSECT, true);
                SkijaUi.boldText(skijaCanvas, "\u2665", heartX, y, size, filledColor, size);
                skijaCanvas.restore();
            }
        }
    }

    private void drawSignal(GuiGraphicsExtractor graphics, float x, float y,
                            float size, int ping, int color, float animation) {
        float barWidth = size / 4.5F;
        float gap = size / 12.0F;
        int activeBars = ping < 0 ? 0 : ping < 60 ? 4 : ping < 120 ? 3 : ping < 250 ? 2 : 1;
        for (int index = 0; index < 4; index++) {
            float barHeight = size * (0.35F + index * 0.22F);
            int barColor = index < activeBars ? color : withAlpha(color, 0.25F);
            roundedRect(graphics, x + index * (barWidth + gap), y + size - barHeight,
                    barWidth, barHeight, barWidth * 0.35F, withAlpha(barColor, animation));
        }
    }

    private LivingEntity resolveTarget() {
        LivingEntity target = KillAura.INSTANCE.isEnabled() ? KillAura.INSTANCE.target : null;
        if (isRenderableTarget(target)) return target;
        target = KillAuraPlus.INSTANCE.target();
        if (isRenderableTarget(target)) return target;
        target = TargetManager.INSTANCE.getSharedTarget();
        if (isRenderableTarget(target)) return target;

        // Keep an editor preview when no combat target exists, but never let
        // the editor override a live target (which would always show our skin).
        return mc.screen instanceof HudEditorScreen && mc.player != null ? mc.player : null;
    }

    private boolean isRenderableTarget(LivingEntity target) {
        return target != null && target != mc.player && target.level() == mc.level
                && target.isAlive() && !target.isDeadOrDying();
    }

    private LivingEntity updateRenderedTarget(LivingEntity liveTarget) {
        long now = System.currentTimeMillis();
        if (lastVisibilityUpdateMs == 0L) lastVisibilityUpdateMs = now;
        float delta = Mth.clamp((now - lastVisibilityUpdateMs)
                / (float) VISIBILITY_ANIMATION_DURATION_MS, 0.0F, 1.0F);
        lastVisibilityUpdateMs = now;
        if (liveTarget != null) {
            renderedTarget = liveTarget;
            visibilityProgress = Math.min(1.0F, visibilityProgress + delta);
            return renderedTarget;
        }
        if (renderedTarget == null) {
            visibilityProgress = 0.0F;
            return null;
        }
        visibilityProgress = Math.max(0.0F, visibilityProgress - delta);
        if (visibilityProgress <= 0.01F) {
            renderedTarget = null;
            resetAnimatedState();
            return null;
        }
        return renderedTarget;
    }

    private void updateAnimatedHealth(LivingEntity target, float currentHealth,
                                      float maximum, float frameTime) {
        currentHealth = Mth.clamp(currentHealth, 0.0F, maximum);
        int targetId = target.getId();
        if (targetId != lastTargetId) {
            lastTargetId = targetId;
            displayedHealth = currentHealth;
            delayedHealth = currentHealth;
            lastKnownHealth = currentHealth;
            lastKnownMaximum = maximum;
            lastDamageTimeMs = 0L;
            return;
        }
        if (lastKnownHealth >= 0.0F && currentHealth < lastKnownHealth) {
            lastDamageTimeMs = System.currentTimeMillis();
        }
        float response = Mth.clamp(frameTime * 10.0F, 0.0F, 1.0F);
        displayedHealth = Mth.lerp(response, displayedHealth, currentHealth);
        delayedHealth = updateDelayedHealth(currentHealth, frameTime);
        lastKnownHealth = currentHealth;
        lastKnownMaximum = maximum;
        displayedHealth = Mth.clamp(displayedHealth, 0.0F, maximum);
        delayedHealth = Mth.clamp(delayedHealth, 0.0F, maximum);
    }

    private float updateDelayedHealth(float currentHealth, float frameTime) {
        boolean compactStyle = style.is(Style.Novoline)
                || style.is(Style.Adjust) || style.is(Style.Flux);
        if ((!delayBar.get() && !compactStyle) || currentHealth >= delayedHealth) return currentHealth;
        long holdTime = compactStyle ? NOVOLINE_DAMAGE_HOLD_MS : delayTime.get().longValue();
        boolean shouldWait = compactStyle || delayWait.get();
        if (shouldWait && System.currentTimeMillis() - lastDamageTimeMs < holdTime) {
            return delayedHealth;
        }
        float responseSpeed = compactStyle
                ? NOVOLINE_DELAY_RESPONSE
                : delaySpeed.get().floatValue() * 2.0F;
        float response = Mth.clamp(frameTime * responseSpeed, 0.0F, 1.0F);
        return Mth.lerp(response, delayedHealth, currentHealth);
    }

    private void resetAnimatedState() {
        lastTargetId = Integer.MIN_VALUE;
        displayedHealth = 0.0F;
        delayedHealth = 0.0F;
        lastKnownHealth = -1.0F;
        lastKnownMaximum = 1.0F;
        lastDamageTimeMs = 0L;
    }

    private float healthRatio(float maximum) {
        return Mth.clamp(displayedHealth / Math.max(1.0F, maximum), 0.0F, 1.0F);
    }

    private float getDisplayHealth(LivingEntity target) {
        return HealthDetectionUtils.getHealth(target);
    }

    private int myauAccent(long offsetMs) {
        return switch (colorMode.get()) {
            case Custom -> customColor.argb();
            case Health -> healthColor(healthRatio(lastKnownMaximum));
            case Astolfo -> {
                float hue = ((System.currentTimeMillis() + offsetMs) % 3000L) / 3000.0F;
                float wrapped = hue > 0.5F ? 1.0F - hue : hue + 0.5F;
                yield 0xFF000000 | (Color.HSBtoRGB(wrapped, 0.5F, 1.0F) & 0x00FFFFFF);
            }
            case Sync -> UiTheme.accent();
        };
    }

    private int legacyBackground(int rgb, float animation) {
        return legacyBackground(rgb, animation, 1.0F);
    }

    private int legacyBackground(int rgb, float animation, float alphaFactor) {
        int configured = Mth.clamp(Math.round(backgroundAlpha.get().floatValue()), 0, 255);
        int glassAlpha = hudSurfaceColor((configured << 24) | (rgb & 0x00FFFFFF)) >>> 24;
        int alpha = Mth.clamp(Math.round(glassAlpha * animation * alphaFactor), 0, 255);
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    private float activeBlurStrength() {
        int alpha = style.is(Style.Flux) ? 255
                : Mth.clamp(Math.round(backgroundAlpha.get().floatValue()), 0, 255);
        return hudBlurStrength(blur.get(), blurStrength.get(), true, alpha << 24);
    }

    private static int healthColor(float ratio) {
        float value = Mth.clamp(ratio, 0.0F, 1.0F);
        int red;
        int green;
        if (value < 0.5F) {
            red = 255;
            green = Math.round(value * 2.0F * 255.0F);
        } else {
            red = Math.round((1.0F - (value - 0.5F) * 2.0F) * 255.0F);
            green = 255;
        }
        return 0xFF000000 | (red << 16) | (green << 8);
    }

    static float fluxContentWidth(
            float renderedNameWidth,
            float measuredNameWidth,
            float healthWidth) {
        return Math.max(FLUX_MIN_CONTENT_WIDTH,
                Math.max(measuredNameWidth + 25.0F,
                        renderedNameWidth + healthWidth + 10.0F));
    }

    static int fluxHealthColor(float ratio) {
        int hue = (int) (Mth.clamp(ratio, 0.0F, 1.0F) * 120.0F);
        return 0xFF000000 | (Color.HSBtoRGB(hue / 360.0F, 0.7F, 1.0F) & 0x00FFFFFF);
    }

    private int ping(LivingEntity target) {
        if (!(target instanceof Player player) || mc.getConnection() == null) return -1;
        PlayerInfo info = mc.getConnection().getPlayerInfo(player.getUUID());
        return info == null ? -1 : info.getLatency();
    }

    private int pingColor(int ping) {
        if (ping < 0) return mutedTextColor.argb();
        if (ping < 80) return 0xE678DC8C;
        if (ping < 200) return 0xE6FBBF24;
        return 0xE6F87171;
    }

    private void drawText(GuiGraphicsExtractor graphics, String text,
                          float x, float y, float textScale, int color, boolean shadow) {
        if (text == null || text.isEmpty() || textScale <= 0.01F || ((color >>> 24) & 255) == 0) return;
        if (skijaCanvas == null) return;
        float fontSize = textScale * SKIJA_FONT_UNIT;
        float lineHeight = textHeight(textScale);
        if (shadow) {
            int shadowAlpha = Math.max(1, ((color >>> 24) & 255) / 3);
            SkijaUi.text(skijaCanvas, text, x + 0.55F, y + 0.55F, lineHeight,
                    shadowAlpha << 24, fontSize);
        }
        SkijaUi.text(skijaCanvas, text, x, y, lineHeight, color, fontSize);
    }

    private float textWidth(String text, float textScale) {
        return SkijaUi.textWidth(text == null ? "" : text, textScale * SKIJA_FONT_UNIT);
    }

    private float textHeight(float textScale) {
        return textScale * SKIJA_FONT_UNIT;
    }

    private String fitText(String text, float textScale, float maxWidth) {
        String value = text == null ? "" : text;
        if (textWidth(value, textScale) <= maxWidth) return value;
        String suffix = "...";
        while (!value.isEmpty() && textWidth(value + suffix, textScale) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isEmpty() ? "" : value + suffix;
    }

    private String fitMinecraftText(String text, int maxWidth) {
        String value = text == null ? "" : text;
        if (mc.font.width(value) <= maxWidth) return value;
        String suffix = "...";
        while (!value.isEmpty() && mc.font.width(value + suffix) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isEmpty() ? "" : value + suffix;
    }

    private void fillRect(GuiGraphicsExtractor graphics, float x, float y,
                          float width, float height, int color) {
        if (skijaCanvas == null || width <= 0.0F || height <= 0.0F || ((color >>> 24) & 255) == 0) return;
        SkijaUi.fill(skijaCanvas, x, y, width, height, color);
    }

    private void roundedRect(GuiGraphicsExtractor graphics, float x, float y,
                             float width, float height, float radius, int color) {
        if (skijaCanvas == null || width <= 0.0F || height <= 0.0F || ((color >>> 24) & 255) == 0) return;
        SkijaUi.rounded(skijaCanvas, x, y, width, height,
                Math.max(0.0F, Math.min(radius, Math.min(width, height) * 0.5F)), color);
    }

    private void horizontalGradient(GuiGraphicsExtractor graphics, float x, float y,
                                    float width, float height, float radius,
                                    int startColor, int endColor) {
        if (skijaCanvas == null || width <= 0.0F || height <= 0.0F) return;
        float safeRadius = Math.max(0.0F, Math.min(radius, Math.min(width, height) * 0.5F));
        try (Shader shader = Shader.makeLinearGradient(x, y, x + width, y,
                new int[]{startColor, endColor})) {
            AA_PAINT.setMode(PaintMode.FILL).setShader(shader).setColor(0xFFFFFFFF).setAlpha(255);
            skijaCanvas.drawRRect(RRect.makeXYWH(x, y, width, height, safeRadius), AA_PAINT);
        } finally {
            AA_PAINT.setShader(null);
        }
    }

    private void roundedOutline(GuiGraphicsExtractor graphics, float x, float y,
                                float width, float height, float radius,
                                float thickness, int color) {
        if (skijaCanvas == null || width <= 0.0F || height <= 0.0F || thickness <= 0.0F) return;
        float inset = thickness * 0.5F;
        float drawWidth = Math.max(0.0F, width - thickness);
        float drawHeight = Math.max(0.0F, height - thickness);
        if (drawWidth <= 0.0F || drawHeight <= 0.0F) return;
        float safeRadius = Math.max(0.0F,
                Math.min(radius - inset, Math.min(drawWidth, drawHeight) * 0.5F));
        AA_PAINT.setShader(null).setMode(PaintMode.STROKE).setStrokeWidth(thickness)
                .setColor(color).setAlpha(255);
        skijaCanvas.drawRRect(RRect.makeXYWH(x + inset, y + inset,
                drawWidth, drawHeight, safeRadius), AA_PAINT);
        AA_PAINT.setMode(PaintMode.FILL);
    }

    private void drawShadow(GuiGraphicsExtractor graphics, float x, float y,
                            float width, float height, float radius,
                            float blur, int color) {
        if (skijaCanvas == null || width <= 0.0F || height <= 0.0F) return;
        float sigma = Math.max(0.1F, blur * 0.5F);
        float safeRadius = Math.max(0.0F, Math.min(radius, Math.min(width, height) * 0.5F));
        try (ImageFilter filter = ImageFilter.makeDropShadowOnly(
                0.0F, 0.0F, sigma, sigma, color)) {
            AA_PAINT.setMode(PaintMode.FILL).setShader(null).setMaskFilter(null)
                    .setImageFilter(filter).setColor(0xFFFFFFFF).setAlpha(255);
            skijaCanvas.drawRRect(RRect.makeXYWH(x, y, width, height, safeRadius), AA_PAINT);
        } finally {
            AA_PAINT.setImageFilter(null).setAlpha(255);
        }
    }

    private void blurBackdrop(float x, float y, float width, float height,
                              float radius, float strength) {
        if (skijaCanvas == null || width <= 1.0F || height <= 1.0F || strength <= 0.01F) return;
        SkijaRenderer.drawGaussianBackdrop(skijaCanvas,
                RRect.makeXYWH(x, y, width, height,
                        Math.max(0.0F, Math.min(radius, Math.min(width, height) * 0.5F))),
                x, y, width, height, strength);
    }

    private void drawHeart(GuiGraphicsExtractor graphics, float x, float y,
                           float width, float height, int color) {
        if (skijaCanvas == null || width <= 0.0F || height <= 0.0F
                || ((color >>> 24) & 255) == 0) return;
        float center = x + width * 0.5F;
        try (PathBuilder builder = new PathBuilder()) {
            builder.moveTo(center, y + height)
                    .cubicTo(x + width * 0.32F, y + height * 0.78F,
                            x, y + height * 0.58F, x, y + height * 0.30F)
                    .cubicTo(x, y + height * 0.10F,
                            x + width * 0.22F, y, center, y + height * 0.22F)
                    .cubicTo(x + width * 0.78F, y,
                            x + width, y + height * 0.10F,
                            x + width, y + height * 0.30F)
                    .cubicTo(x + width, y + height * 0.58F,
                            x + width * 0.68F, y + height * 0.78F,
                            center, y + height)
                    .closePath();
            try (io.github.humbleui.skija.Path path = builder.detach()) {
                AA_PAINT.setMode(PaintMode.FILL).setShader(null).setImageFilter(null)
                        .setColor(color).setAlpha(255);
                skijaCanvas.drawPath(path, AA_PAINT);
            } finally {
                AA_PAINT.setAlpha(255);
            }
        }
    }

    private void drawShield(float x, float y, float width, float height, int color) {
        if (skijaCanvas == null || width <= 0.0F || height <= 0.0F
                || ((color >>> 24) & 255) == 0) return;
        try (PathBuilder builder = new PathBuilder()) {
            builder.moveTo(x + width * 0.5F, y)
                    .lineTo(x + width, y + height * 0.18F)
                    .lineTo(x + width * 0.88F, y + height * 0.68F)
                    .lineTo(x + width * 0.5F, y + height)
                    .lineTo(x + width * 0.12F, y + height * 0.68F)
                    .lineTo(x, y + height * 0.18F)
                    .closePath();
            try (io.github.humbleui.skija.Path path = builder.detach()) {
                AA_PAINT.setMode(PaintMode.FILL).setShader(null).setImageFilter(null)
                        .setColor(color).setAlpha(255);
                skijaCanvas.drawPath(path, AA_PAINT);
            }
        }
    }

    private static int withAlpha(int color, float alphaScale) {
        int alpha = Mth.clamp(Math.round(((color >>> 24) & 255)
                * Mth.clamp(alphaScale, 0.0F, 1.0F)), 0, 255);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int mixColor(int first, int second, float amount) {
        float t = Mth.clamp(amount, 0.0F, 1.0F);
        int alpha = Math.round(((first >>> 24) & 255)
                + (((second >>> 24) & 255) - ((first >>> 24) & 255)) * t);
        int red = Math.round(((first >>> 16) & 255)
                + (((second >>> 16) & 255) - ((first >>> 16) & 255)) * t);
        int green = Math.round(((first >>> 8) & 255)
                + (((second >>> 8) & 255) - ((first >>> 8) & 255)) * t);
        int blue = Math.round((first & 255) + ((second & 255) - (first & 255)) * t);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static float easeOutSine(float value) {
        return (float) Math.sin(Mth.clamp(value, 0.0F, 1.0F) * Math.PI * 0.5D);
    }

    @Override
    public int editorColor() {
        if (style.is(Style.Setsuna)) return borderColor.argb();
        if (style.is(Style.Flux)) return fluxHealthColor(healthRatio(lastKnownMaximum));
        if (style.is(Style.Novoline)) return novolineAccent.argb();
        if (style.is(Style.Adjust)) return adjustHealthStart.argb();
        return myauAccent(0L);
    }

    @Override
    public String getInfo() {
        return style.get().name();
    }

    private record Bounds(float width, float height) {
    }

    private record TextureHole(float x, float y, float width, float height) {
    }

    private record FrameState(LivingEntity target, float x, float y, float width, float height,
                              float scale, float animation, float maximum,
                              List<TextureHole> textureHoles, long createdAtNs) {
    }
}
