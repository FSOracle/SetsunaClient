package com.setsuna.ui.hud;

import com.setsuna.config.ConfigManager;
import com.setsuna.event.EventBus;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.module.Module;
import com.setsuna.module.ModuleManager;
import com.setsuna.notification.NotificationManager;
import com.setsuna.notification.NotificationType;
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
import com.setsuna.setting.settings.StringSetting;
import com.setsuna.ui.SkijaScreen;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.types.Rect;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Direct-manipulation HUD editor with contextual settings beside the selected element. */
public final class HudEditorScreen extends Screen implements SkijaScreen {

    private static final float EDGE_MARGIN = 6.0F;
    private static final float SIDEBAR_WIDTH = 150.0F;
    private static final float SIDEBAR_HEADER = 22.0F;
    private static final float SIDEBAR_FOOTER = 5.0F;
    private static final float MAX_SIDEBAR_BODY = 280.0F;
    private static final float SETTING_HEIGHT = 13.0F;
    private static final float NUMBER_HEIGHT = 18.0F;
    private static final float COLOR_CHANNEL_HEIGHT = 9.0F;
    private static final float BUTTON_WIDTH = 54.0F;
    private static final float BUTTON_HEIGHT = 20.0F;
    private static final float ITEM_LABEL_HEIGHT = 11.0F;
    private static final float SETTING_FONT = 7.0F;
    private static final float SCROLL_STEP = 20.0F;
    private static final float DETACH_GAP = 10.0F;

    private static final int TRACK = UiTheme.argb(255, 67, 71, 82);
    private static final int[] CHANNEL_COLORS = {
            UiTheme.argb(255, 232, 84, 84),
            UiTheme.argb(255, 84, 218, 117),
            UiTheme.argb(255, 84, 151, 235),
            UiTheme.argb(255, 226, 228, 233)
    };
    private static final String[] CHANNEL_LABELS = {"R", "G", "B", "A"};

    private float sidebarX = Float.NaN;
    private float sidebarY = Float.NaN;
    private float sidebarScroll;
    private float inspectorProgress;
    private long lastFrameNanos = System.nanoTime();
    private EpsilonHudModule selectedModule;

    private int mouseX;
    private int mouseY;
    private EditorItem dragging;
    private List<GroupDragOffset> draggingMembers = List.of();
    private float dragOffsetX;
    private float dragOffsetY;

    private Setting<?> draggingNumber;
    private ColorSetting draggingColor;
    private int draggingColorChannel = -1;
    private float draggingTrackX;
    private float draggingTrackWidth;
    private StringSetting editingString;
    private ColorSetting editingColor;
    private Color colorBeforeEdit;
    private String editText = "";
    private int editCursor;
    private boolean selectAll;

    public HudEditorScreen() {
        super(Component.literal("Setsuna HUD Editor"));
    }

    @Override
    protected void init() {
        lastFrameNanos = System.nanoTime();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Preserve the current game frame beneath the editor.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        if (SkijaRenderer.hasFailed()) {
            graphics.fill(6, 6, 244, 26, 0xD6080A0B);
            graphics.text(font, "Skija renderer failed - check latest.log", 11, 14,
                    UiTheme.TEXT, false);
        }
    }

    @Override
    public void renderSkija(Canvas canvas) {
        EventBus.INSTANCE.postTo(new Render2DEvent(canvas, width, height,
                        minecraft.getWindow().getGuiScale()),
                subscriber -> subscriber instanceof EpsilonHudModule
                        || subscriber == HudFusionManager.INSTANCE);
        List<EditorItem> items = items();
        updateInspector(items);
        for (EditorItem item : items) {
            drawItem(canvas, item, selectedModule != null
                    && item.members().contains(selectedModule));
        }
        if (selectedModule != null) drawSidebar(canvas);
        drawActions(canvas);
    }

    private void updateInspector(List<EditorItem> items) {
        long now = System.nanoTime();
        float delta = Math.min(0.08F, Math.max(0.0F,
                (now - lastFrameNanos) / 1_000_000_000.0F));
        lastFrameNanos = now;
        if (selectedModule == null) {
            inspectorProgress = approach(inspectorProgress, 0.0F, delta, 14.0F);
            return;
        }

        EditorItem selected = null;
        for (EditorItem item : items) {
            if (item.module() == selectedModule) {
                selected = item;
                break;
            }
        }
        if (selected == null) {
            selectedModule = null;
            inspectorProgress = 0.0F;
            return;
        }

        List<Slot> slots = buildSlots();
        float panelHeight = SIDEBAR_HEADER + sidebarBodyHeight(slots) + SIDEBAR_FOOTER;
        float gap = 8.0F;
        float rightX = selected.x() + selected.width() + gap;
        float leftX = selected.x() - SIDEBAR_WIDTH - gap;
        float targetX;
        if (rightX + SIDEBAR_WIDTH <= width - EDGE_MARGIN) {
            targetX = rightX;
        } else if (leftX >= EDGE_MARGIN) {
            targetX = leftX;
        } else {
            targetX = HudRenderUtil.clamp(rightX, EDGE_MARGIN,
                    Math.max(EDGE_MARGIN, width - SIDEBAR_WIDTH - EDGE_MARGIN));
        }
        float targetY = HudRenderUtil.clamp(selected.y(), EDGE_MARGIN,
                Math.max(EDGE_MARGIN, height - panelHeight - EDGE_MARGIN));
        if (Float.isNaN(sidebarX) || Float.isNaN(sidebarY)) {
            sidebarX = targetX;
            sidebarY = targetY;
        } else {
            sidebarX = approach(sidebarX, targetX, delta, 13.0F);
            sidebarY = approach(sidebarY, targetY, delta, 13.0F);
        }
        inspectorProgress = approach(inspectorProgress, 1.0F, delta, 14.0F);
        clampScroll(slots);
    }

    private static float approach(float current, float target, float delta, float speed) {
        float result = current + (target - current)
                * (1.0F - (float) Math.exp(-speed * delta));
        return Math.abs(target - result) < 0.001F ? target : result;
    }

    // --- sidebar -------------------------------------------------------------

    private void drawSidebar(Canvas canvas) {
        List<Slot> slots = buildSlots();
        float bodyHeight = sidebarBodyHeight(slots);
        float panelHeight = SIDEBAR_HEADER + bodyHeight + SIDEBAR_FOOTER;
        int layer = canvas.saveLayerAlpha(null,
                Math.round(255.0F * HudRenderUtil.clamp(inspectorProgress, 0.0F, 1.0F)));
        HudRenderUtil.panel(canvas, sidebarX, sidebarY, SIDEBAR_WIDTH, panelHeight, 184);

        SkijaUi.fill(canvas, sidebarX + 1.0F, sidebarY + SIDEBAR_HEADER - 1.0F,
                SIDEBAR_WIDTH - 2.0F, 1.0F, UiTheme.withAlpha(UiTheme.BORDER, 170));
        SkijaUi.boldText(canvas, HudRenderUtil.fit(compactLabel(selectedModule),
                        SIDEBAR_WIDTH - 14.0F, 7.2F, true),
                sidebarX + 7.0F, sidebarY, SIDEBAR_HEADER, UiTheme.TEXT, 7.2F);

        float bodyY = sidebarY + SIDEBAR_HEADER;
        canvas.save();
        canvas.clipRect(Rect.makeXYWH(sidebarX, bodyY, SIDEBAR_WIDTH, bodyHeight));
        float base = bodyY - sidebarScroll;
        for (Slot slot : slots) {
            float rowY = base + slot.y();
            if (rowY + slot.height() < bodyY || rowY > bodyY + bodyHeight) {
                continue;
            }
            drawSettingRow(canvas, slot.setting(), rowY, slot.height());
        }
        canvas.restore();
        drawScrollbar(canvas, bodyY, bodyHeight, contentHeight(slots));
        canvas.restoreToCount(layer);
    }

    private void drawSettingRow(Canvas canvas, Setting<?> setting, float rowY, float rowHeight) {
        boolean hovered = contains(mouseX, mouseY, sidebarX, rowY, SIDEBAR_WIDTH, rowHeight);
        if (hovered) {
            SkijaUi.rounded(canvas, sidebarX + 4.0F, rowY + 1.0F,
                    SIDEBAR_WIDTH - 8.0F, Math.max(1.0F, rowHeight - 2.0F), 3.0F,
                    UiTheme.withAlpha(UiTheme.SURFACE_HOVER, 120));
        }
        float labelWidth = setting instanceof ColorSetting ? 52.0F
                : setting instanceof StringSetting && !(setting instanceof FontSetting) ? 64.0F : 74.0F;
        SkijaUi.text(canvas, HudRenderUtil.fit(setting.displayName(), labelWidth, SETTING_FONT, false),
                sidebarX + 8.0F, rowY, SETTING_HEIGHT, UiTheme.TEXT_MUTED, SETTING_FONT);

        if (setting instanceof BooleanSetting booleanSetting) {
            float boxX = sidebarX + SIDEBAR_WIDTH - 16.0F;
            SkijaUi.rounded(canvas, boxX, rowY + 3.5F, 9.0F, 9.0F, 2.0F,
                    booleanSetting.get() ? UiTheme.accent() : TRACK);
        } else if (setting instanceof FontSetting fontSetting) {
            drawRight(canvas, HudRenderUtil.fit(fontSetting.displayValue(), 60.0F, SETTING_FONT, false),
                    rowY, SETTING_HEIGHT, UiTheme.accent(), SETTING_FONT);
        } else if (setting instanceof EnumSetting<?> enumSetting) {
            drawRight(canvas, HudRenderUtil.fit(enumSetting.displayValue(), 60.0F, SETTING_FONT, false),
                    rowY, SETTING_HEIGHT, UiTheme.accent(), SETTING_FONT);
        } else if (setting instanceof IntSetting intSetting) {
            drawRight(canvas, Integer.toString(intSetting.get()), rowY, 12.0F, UiTheme.TEXT, SETTING_FONT);
            drawNumberTrack(canvas, rowY, intSetting.fraction());
        } else if (setting instanceof DoubleSetting doubleSetting) {
            drawRight(canvas, trimZeros(String.format(Locale.ROOT, "%.2f", doubleSetting.get())),
                    rowY, 12.0F, UiTheme.TEXT, SETTING_FONT);
            drawNumberTrack(canvas, rowY, doubleSetting.fraction());
        } else if (setting instanceof ColorSetting colorSetting) {
            drawColor(canvas, colorSetting, rowY);
        } else if (setting instanceof StringSetting stringSetting) {
            drawStringField(canvas, stringSetting, rowY);
        } else if (setting instanceof ButtonSetting) {
            drawRight(canvas, "RUN", rowY, SETTING_HEIGHT, UiTheme.accent(), SETTING_FONT);
        }
    }

    private void drawStringField(Canvas canvas, StringSetting setting, float rowY) {
        boolean focused = editingString == setting;
        float fieldX = sidebarX + SIDEBAR_WIDTH - 68.0F;
        float fieldY = rowY + 1.5F;
        float fieldWidth = 61.0F;
        float fieldHeight = 11.0F;
        SkijaUi.rounded(canvas, fieldX, fieldY, fieldWidth, fieldHeight, 2.0F,
                focused ? UiTheme.accent() : TRACK);
        SkijaUi.rounded(canvas, fieldX + 1.0F, fieldY + 1.0F,
                fieldWidth - 2.0F, fieldHeight - 2.0F, 1.5F,
                UiTheme.withAlpha(UiTheme.SURFACE, 238));

        String value = focused ? editText : setting.get();
        String shown = value.isEmpty() && !focused ? "Empty" : value;
        int color = value.isEmpty() && !focused ? UiTheme.TEXT_FAINT : UiTheme.TEXT;
        float innerX = fieldX + 4.0F;
        float innerWidth = fieldWidth - 8.0F;
        float offset = 0.0F;
        if (focused) {
            int safeCursor = Math.max(0, Math.min(editCursor, value.length()));
            offset = Math.max(0.0F,
                    SkijaUi.textWidth(value.substring(0, safeCursor), SETTING_FONT) - innerWidth + 1.0F);
        }

        canvas.save();
        canvas.clipRect(Rect.makeXYWH(innerX, fieldY, innerWidth, fieldHeight));
        if (focused && selectAll && !value.isEmpty()) {
            SkijaUi.fill(canvas, innerX, fieldY + 2.0F,
                    Math.min(innerWidth, SkijaUi.textWidth(value, SETTING_FONT)),
                    fieldHeight - 4.0F, UiTheme.withAlpha(UiTheme.accent(), 52));
        }
        SkijaUi.text(canvas, shown, innerX - offset, fieldY, fieldHeight, color, SETTING_FONT);
        if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float caretX = innerX - offset
                    + SkijaUi.textWidth(value.substring(0, editCursor), SETTING_FONT);
            SkijaUi.fill(canvas, caretX, fieldY + 2.0F, 1.0F,
                    fieldHeight - 4.0F, UiTheme.accent());
        }
        canvas.restore();
    }

    private void drawNumberTrack(Canvas canvas, float rowY, float fraction) {
        float trackX = sidebarX + 13.0F;
        float trackWidth = SIDEBAR_WIDTH - 20.0F;
        float trackY = rowY + NUMBER_HEIGHT - 4.0F;
        SkijaUi.fill(canvas, trackX, trackY, trackWidth, 2.0F, TRACK);
        SkijaUi.fill(canvas, trackX, trackY,
                trackWidth * HudRenderUtil.clamp(fraction, 0.0F, 1.0F), 2.0F, UiTheme.accent());
    }

    private void drawColor(Canvas canvas, ColorSetting setting, float rowY) {
        Color color = setting.get();
        boolean focused = editingColor == setting;
        float fieldX = sidebarX + SIDEBAR_WIDTH - 79.0F;
        float fieldY = rowY + 1.5F;
        float fieldWidth = 58.0F;
        float fieldHeight = 11.0F;
        SkijaUi.rounded(canvas, fieldX, fieldY, fieldWidth, fieldHeight, 2.0F,
                focused ? UiTheme.accent() : TRACK);
        SkijaUi.rounded(canvas, fieldX + 1.0F, fieldY + 1.0F,
                fieldWidth - 2.0F, fieldHeight - 2.0F, 1.5F,
                UiTheme.withAlpha(UiTheme.SURFACE, 238));

        String value = focused ? editText : setting.hex();
        float innerX = fieldX + 3.0F;
        float innerWidth = fieldWidth - 6.0F;
        float offset = 0.0F;
        if (focused) {
            int safeCursor = Math.max(0, Math.min(editCursor, value.length()));
            offset = Math.max(0.0F,
                    SkijaUi.textWidth(value.substring(0, safeCursor), SETTING_FONT) - innerWidth + 1.0F);
        }
        canvas.save();
        canvas.clipRect(Rect.makeXYWH(innerX, fieldY, innerWidth, fieldHeight));
        if (focused && selectAll && !value.isEmpty()) {
            SkijaUi.fill(canvas, innerX, fieldY + 2.0F,
                    Math.min(innerWidth, SkijaUi.textWidth(value, SETTING_FONT)),
                    fieldHeight - 4.0F, UiTheme.withAlpha(UiTheme.accent(), 52));
        }
        SkijaUi.text(canvas, value, innerX - offset, fieldY, fieldHeight,
                focused ? UiTheme.TEXT : UiTheme.TEXT_MUTED, SETTING_FONT);
        if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float caretX = innerX - offset
                    + SkijaUi.textWidth(value.substring(0, editCursor), SETTING_FONT);
            SkijaUi.fill(canvas, caretX, fieldY + 2.0F, 1.0F,
                    fieldHeight - 4.0F, UiTheme.accent());
        }
        canvas.restore();

        SkijaUi.rounded(canvas, sidebarX + SIDEBAR_WIDTH - 18.0F, rowY + 3.0F,
                11.0F, 8.0F, 2.0F, setting.argb());
        int[] values = {color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()};
        int channels = setting.allowAlpha() ? 4 : 3;
        for (int i = 0; i < channels; i++) {
            float channelY = rowY + SETTING_HEIGHT + i * COLOR_CHANNEL_HEIGHT;
            SkijaUi.boldText(canvas, CHANNEL_LABELS[i], sidebarX + 13.0F, channelY,
                    COLOR_CHANNEL_HEIGHT, CHANNEL_COLORS[i], SETTING_FONT);
            float trackX = sidebarX + 22.0F;
            float trackWidth = SIDEBAR_WIDTH - 52.0F;
            SkijaUi.fill(canvas, trackX, channelY + 3.5F, trackWidth, 2.0F, TRACK);
            SkijaUi.fill(canvas, trackX, channelY + 3.5F,
                    trackWidth * values[i] / 255.0F, 2.0F, CHANNEL_COLORS[i]);
            String channelValue = Integer.toString(values[i]);
            float valueWidth = SkijaUi.textWidth(channelValue, SETTING_FONT);
            SkijaUi.text(canvas, channelValue, sidebarX + SIDEBAR_WIDTH - 7.0F - valueWidth,
                    channelY, COLOR_CHANNEL_HEIGHT, UiTheme.TEXT_MUTED, SETTING_FONT);
        }
    }

    private void drawRight(Canvas canvas, String value, float rowY, float height, int color, float size) {
        float valueWidth = SkijaUi.textWidth(value, size);
        SkijaUi.text(canvas, value, sidebarX + SIDEBAR_WIDTH - 8.0F - valueWidth,
                rowY, height, color, size);
    }

    private void drawScrollbar(Canvas canvas, float bodyY, float bodyHeight, float contentHeight) {
        if (contentHeight <= bodyHeight || bodyHeight <= 0.0F) {
            return;
        }
        float thumbHeight = Math.max(12.0F, bodyHeight * bodyHeight / contentHeight);
        float travel = bodyHeight - thumbHeight;
        float maxScroll = contentHeight - bodyHeight;
        float thumbY = bodyY + (maxScroll <= 0.0F ? 0.0F : sidebarScroll / maxScroll * travel);
        SkijaUi.fill(canvas, sidebarX + SIDEBAR_WIDTH - 2.5F, bodyY, 2.0F, bodyHeight,
                UiTheme.withAlpha(UiTheme.BORDER, 120));
        SkijaUi.fill(canvas, sidebarX + SIDEBAR_WIDTH - 2.5F, thumbY, 2.0F, thumbHeight,
                UiTheme.accent());
    }

    // --- HUD preview items ---------------------------------------------------

    private void drawItem(Canvas canvas, EditorItem item, boolean active) {
        boolean hovered = item.contains(mouseX, mouseY);
        int outline = active || hovered ? item.color() : UiTheme.withAlpha(item.color(), 176);
        drawOutline(canvas, item.x() - 1.0F, item.y() - 1.0F,
                item.width() + 2.0F, item.height() + 2.0F, outline);
        drawItemLabel(canvas, item, outline, active);
    }

    private void drawItemLabel(Canvas canvas, EditorItem item, int outline, boolean active) {
        String label = HudRenderUtil.fit(item.label(), 92.0F, 6.4F, true);
        float labelWidth = Math.max(18.0F, SkijaUi.boldTextWidth(label, 6.4F) + 7.0F);
        float labelX = HudRenderUtil.clamp(item.x(), 0.0F,
                Math.max(0.0F, width - labelWidth));
        float labelY;
        if (item.y() >= ITEM_LABEL_HEIGHT + 2.0F) {
            labelY = item.y() - ITEM_LABEL_HEIGHT - 2.0F;
        } else if (item.y() + item.height() + ITEM_LABEL_HEIGHT + 2.0F <= height) {
            labelY = item.y() + item.height() + 2.0F;
        } else if (item.x() + item.width() + labelWidth + 2.0F <= width) {
            labelX = item.x() + item.width() + 2.0F;
            labelY = HudRenderUtil.clamp(item.y(), 0.0F,
                    Math.max(0.0F, height - ITEM_LABEL_HEIGHT));
        } else if (item.x() >= labelWidth + 2.0F) {
            labelX = item.x() - labelWidth - 2.0F;
            labelY = HudRenderUtil.clamp(item.y(), 0.0F,
                    Math.max(0.0F, height - ITEM_LABEL_HEIGHT));
        } else {
            labelY = HudRenderUtil.clamp(item.y(), 0.0F,
                    Math.max(0.0F, height - ITEM_LABEL_HEIGHT));
        }

        SkijaUi.fill(canvas, labelX, labelY, labelWidth, ITEM_LABEL_HEIGHT,
                UiTheme.withAlpha(UiTheme.SURFACE, active ? 218 : 184));
        drawOutline(canvas, labelX, labelY, labelWidth, ITEM_LABEL_HEIGHT, outline);
        SkijaUi.boldText(canvas, label, labelX + 3.5F, labelY,
                ITEM_LABEL_HEIGHT, UiTheme.TEXT, 6.4F);
    }

    private static void drawOutline(Canvas canvas, float x, float y,
                                    float outlineWidth, float outlineHeight, int color) {
        if (outlineWidth <= 0.0F || outlineHeight <= 0.0F) return;
        SkijaUi.fill(canvas, x, y, outlineWidth, 1.0F, color);
        SkijaUi.fill(canvas, x, y + Math.max(0.0F, outlineHeight - 1.0F),
                outlineWidth, 1.0F, color);
        if (outlineHeight > 2.0F) {
            SkijaUi.fill(canvas, x, y + 1.0F, 1.0F, outlineHeight - 2.0F, color);
            SkijaUi.fill(canvas, x + Math.max(0.0F, outlineWidth - 1.0F), y + 1.0F,
                    1.0F, outlineHeight - 2.0F, color);
        }
    }

    private void drawActions(Canvas canvas) {
        float doneX = width - 8.0F - BUTTON_WIDTH;
        float resetX = doneX - 5.0F - BUTTON_WIDTH;
        float y = height - 8.0F - BUTTON_HEIGHT;
        drawButton(canvas, resetX, y, "RESET", false,
                contains(mouseX, mouseY, resetX, y, BUTTON_WIDTH, BUTTON_HEIGHT));
        drawButton(canvas, doneX, y, "DONE", true,
                contains(mouseX, mouseY, doneX, y, BUTTON_WIDTH, BUTTON_HEIGHT));
    }

    private void drawButton(Canvas canvas, float x, float y, String label,
                            boolean primary, boolean hovered) {
        int border = primary ? UiTheme.accent() : UiTheme.withAlpha(UiTheme.BORDER, 210);
        int background = hovered
                ? UiTheme.withAlpha(UiTheme.CONTROL_HOVER, 218)
                : UiTheme.withAlpha(UiTheme.SURFACE, 184);
        SkijaUi.rounded(canvas, x, y, BUTTON_WIDTH, BUTTON_HEIGHT, 3.0F, border);
        SkijaUi.rounded(canvas, x + 1.0F, y + 1.0F, BUTTON_WIDTH - 2.0F,
                BUTTON_HEIGHT - 2.0F, 2.0F, background);
        float textWidth = SkijaUi.boldTextWidth(label, 6.8F);
        SkijaUi.boldText(canvas, label, x + (BUTTON_WIDTH - textWidth) * 0.5F, y,
                BUTTON_HEIGHT, primary ? UiTheme.TEXT : UiTheme.TEXT_MUTED, 6.8F);
    }

    // --- input ---------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        float x = (float) event.x();
        float y = (float) event.y();
        finishTextEditing();
        if (clickSidebar(x, y, event.button())) return true;

        float doneX = width - 8.0F - BUTTON_WIDTH;
        float resetX = doneX - 5.0F - BUTTON_WIDTH;
        float actionY = height - 8.0F - BUTTON_HEIGHT;
        if (contains(x, y, doneX, actionY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) onClose();
            return true;
        }
        if (contains(x, y, resetX, actionY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) resetPositions();
            return true;
        }

        List<EditorItem> items = items();
        for (int index = items.size() - 1; index >= 0; index--) {
            EditorItem item = items.get(index);
            if (!item.contains(x, y)) continue;
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                selectModule(item.module());
                if (item.members().size() > 1) {
                    detachGroup(item);
                    return true;
                }
                for (EpsilonHudModule member : item.members()) member.resetPosition();
                if (item.module() instanceof Notifications notifications) notifications.position.reset();
                return true;
            }
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                selectModule(item.module());
                dragging = item;
                dragOffsetX = x - item.x();
                dragOffsetY = y - item.y();
                draggingMembers = new ArrayList<>();
                for (EpsilonHudModule member : item.members()) {
                    draggingMembers.add(new GroupDragOffset(member,
                            member.hudX(width) - item.x(), member.hudY(height) - item.y()));
                }
                if (item.module() instanceof Notifications notifications) notifications.useCustomPosition();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void selectModule(EpsilonHudModule module) {
        if (selectedModule == module) return;
        finishTextEditing();
        boolean firstSelection = selectedModule == null;
        selectedModule = module;
        sidebarScroll = 0.0F;
        draggingNumber = null;
        draggingColor = null;
        draggingColorChannel = -1;
        inspectorProgress = firstSelection ? 0.0F : Math.min(inspectorProgress, 0.72F);
    }

    private boolean clickSidebar(float mouseX, float mouseY, int button) {
        List<Slot> slots = buildSlots();
        float bodyHeight = sidebarBodyHeight(slots);
        float panelHeight = SIDEBAR_HEADER + bodyHeight + SIDEBAR_FOOTER;
        if (!contains(mouseX, mouseY, sidebarX, sidebarY, SIDEBAR_WIDTH, panelHeight)) {
            return false;
        }
        float bodyY = sidebarY + SIDEBAR_HEADER;
        if (mouseY < bodyY) {
            return true;
        }
        if (mouseY >= bodyY + bodyHeight) {
            return true;
        }

        float contentY = mouseY - bodyY + sidebarScroll;
        for (Slot slot : slots) {
            if (contentY < slot.y() || contentY >= slot.y() + slot.height()) {
                continue;
            }
            float localY = contentY - slot.y();
            clickSetting(slot.setting(), mouseX, localY, button);
            clampScroll(slots);
            return true;
        }
        return true;
    }

    private void clickSetting(Setting<?> setting, float mouseX, float localY, int button) {
        if (setting instanceof BooleanSetting booleanSetting) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                booleanSetting.toggle();
            }
        } else if (setting instanceof FontSetting fontSetting) {
            fontSetting.cycle(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1);
        } else if (setting instanceof EnumSetting<?> enumSetting) {
            enumSetting.cycle(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1);
        } else if (setting instanceof IntSetting intSetting) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                intSetting.set(intSetting.get() - intSetting.step());
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                beginNumberDrag(setting, sidebarX + 13.0F, SIDEBAR_WIDTH - 20.0F, mouseX);
            }
        } else if (setting instanceof DoubleSetting doubleSetting) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                doubleSetting.set(doubleSetting.get() - doubleSetting.step());
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                beginNumberDrag(setting, sidebarX + 13.0F, SIDEBAR_WIDTH - 20.0F, mouseX);
            }
        } else if (setting instanceof ColorSetting colorSetting) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                colorSetting.reset();
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && localY < SETTING_HEIGHT
                    && mouseX >= sidebarX + SIDEBAR_WIDTH - 79.0F
                    && mouseX < sidebarX + SIDEBAR_WIDTH - 21.0F) {
                focusColor(colorSetting);
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && localY >= SETTING_HEIGHT) {
                int channel = (int) ((localY - SETTING_HEIGHT) / COLOR_CHANNEL_HEIGHT);
                int channels = colorSetting.allowAlpha() ? 4 : 3;
                if (channel >= 0 && channel < channels) {
                    draggingColor = colorSetting;
                    draggingColorChannel = channel;
                    draggingTrackX = sidebarX + 22.0F;
                    draggingTrackWidth = SIDEBAR_WIDTH - 52.0F;
                    updateColor(mouseX);
                }
            }
        } else if (setting instanceof StringSetting stringSetting) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                stringSetting.reset();
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                editingString = stringSetting;
                editingColor = null;
                colorBeforeEdit = null;
                editText = stringSetting.get();
                editCursor = editText.length();
                selectAll = false;
            }
        } else if (setting instanceof ButtonSetting buttonSetting
                && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            buttonSetting.press();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (editingString == null && editingColor == null) return super.keyPressed(event);
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
            applyStringEditing();
            return true;
        }
        if (event.isPaste()) {
            insertStringText(minecraft.keyboardHandler.getClipboard());
            return true;
        }
        switch (event.key()) {
            case GLFW.GLFW_KEY_LEFT -> editCursor = previousIndex(editText, editCursor);
            case GLFW.GLFW_KEY_RIGHT -> editCursor = nextIndex(editText, editCursor);
            case GLFW.GLFW_KEY_HOME -> editCursor = 0;
            case GLFW.GLFW_KEY_END -> editCursor = editText.length();
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (selectAll) {
                    editText = "";
                    editCursor = 0;
                } else if (editCursor > 0) {
                    int previous = previousIndex(editText, editCursor);
                    editText = editText.substring(0, previous) + editText.substring(editCursor);
                    editCursor = previous;
                }
                applyStringEditing();
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (selectAll) {
                    editText = "";
                    editCursor = 0;
                } else if (editCursor < editText.length()) {
                    int next = nextIndex(editText, editCursor);
                    editText = editText.substring(0, editCursor) + editText.substring(next);
                }
                applyStringEditing();
            }
            default -> {
                return super.keyPressed(event);
            }
        }
        selectAll = false;
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if ((editingString == null && editingColor == null) || !event.isAllowedChatCharacter()) {
            return super.charTyped(event);
        }
        insertStringText(event.codepointAsString());
        return true;
    }

    private void insertStringText(String inserted) {
        if (inserted == null || inserted.isEmpty()) return;
        String clean = inserted.replace('\n', ' ').replace("\r", "");
        if (selectAll) {
            editText = "";
            editCursor = 0;
        }
        int maxLength = editingColor != null ? 9 : 64;
        int remaining = maxLength - editText.length();
        if (remaining <= 0) return;
        if (clean.length() > remaining) clean = clean.substring(0, remaining);
        editText = editText.substring(0, editCursor) + clean + editText.substring(editCursor);
        editCursor += clean.length();
        selectAll = false;
        applyStringEditing();
    }

    private void applyStringEditing() {
        if (editingString != null) {
            editingString.set(editText);
        } else if (editingColor != null) {
            Color parsed = editingColor.parseHex(editText);
            if (parsed != null) editingColor.set(parsed);
        }
    }

    private void focusColor(ColorSetting setting) {
        editingString = null;
        editingColor = setting;
        colorBeforeEdit = setting.get();
        editText = setting.hex();
        editCursor = editText.length();
        selectAll = true;
    }

    private void finishTextEditing() {
        if (editingColor != null) {
            Color parsed = editingColor.parseHex(editText);
            editingColor.set(parsed != null ? parsed : colorBeforeEdit);
        } else {
            applyStringEditing();
        }
        editingString = null;
        editingColor = null;
        colorBeforeEdit = null;
        editText = "";
        editCursor = 0;
        selectAll = false;
    }

    private static int previousIndex(String value, int index) {
        return index <= 0 ? 0 : value.offsetByCodePoints(index, -1);
    }

    private static int nextIndex(String value, int index) {
        return index >= value.length() ? value.length() : value.offsetByCodePoints(index, 1);
    }

    private void beginNumberDrag(Setting<?> setting, float trackX, float trackWidth, float mouseX) {
        draggingNumber = setting;
        draggingTrackX = trackX;
        draggingTrackWidth = trackWidth;
        updateNumber(mouseX);
    }

    private void updateNumber(float mouseX) {
        float fraction = HudRenderUtil.clamp((mouseX - draggingTrackX) / draggingTrackWidth, 0.0F, 1.0F);
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
        int value = Math.round(HudRenderUtil.clamp(
                (mouseX - draggingTrackX) / draggingTrackWidth, 0.0F, 1.0F) * 255.0F);
        Color current = draggingColor.get();
        int red = draggingColorChannel == 0 ? value : current.getRed();
        int green = draggingColorChannel == 1 ? value : current.getGreen();
        int blue = draggingColorChannel == 2 ? value : current.getBlue();
        int alpha = draggingColorChannel == 3 ? value : current.getAlpha();
        draggingColor.set(new Color(red, green, blue, alpha));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingNumber != null) {
            updateNumber((float) event.x());
            return true;
        }
        if (draggingColor != null) {
            updateColor((float) event.x());
            return true;
        }
        if (dragging == null) return super.mouseDragged(event, dragX, dragY);
        float x = (float) event.x() - dragOffsetX;
        float y = (float) event.y() - dragOffsetY;
        SnapPoint snapped = snapPosition(dragging, x, y);
        for (GroupDragOffset member : draggingMembers) {
            member.module().moveTo(snapped.x() + member.offsetX(),
                    snapped.y() + member.offsetY(), width, height);
        }
        if (dragging.module() instanceof Notifications notifications) notifications.useCustomPosition();
        return true;
    }

    private SnapPoint snapPosition(EditorItem moving, float rawX, float rawY) {
        float snapDistance = 7.0F;
        float snappedX = rawX;
        float snappedY = rawY;
        float bestXDistance = snapDistance + 1.0F;
        float bestYDistance = snapDistance + 1.0F;

        for (EditorItem other : items()) {
            if (moving.members().contains(other.module())) continue;

            if (rangesOverlap(rawY, moving.height(), other.y(), other.height())) {
                float[] xTargets = {
                        other.x() + other.width(),
                        other.x() - moving.width()
                };
                for (int index = 0; index < xTargets.length; index++) {
                    float distance = Math.abs(rawX - xTargets[index]);
                    if (distance <= snapDistance && distance < bestXDistance) {
                        CompactAttachment attachment = compactAttachment(
                                moving, other, index == 0);
                        snappedX = attachment == null ? xTargets[index] : attachment.x();
                        bestXDistance = distance;
                        if (attachment != null) {
                            snappedY = attachment.y();
                            bestYDistance = 0.0F;
                        }
                    }
                }
            }
            float[] alignedX = {
                    other.x(),
                    other.x() + (other.width() - moving.width()) * 0.5F,
                    other.x() + other.width() - moving.width()
            };
            for (float target : alignedX) {
                float distance = Math.abs(rawX - target);
                if (distance <= snapDistance && distance < bestXDistance) {
                    snappedX = target;
                    bestXDistance = distance;
                }
            }

            if (rangesOverlap(rawX, moving.width(), other.x(), other.width())) {
                float[] yTargets = {
                        other.y() + other.height(),
                        other.y() - moving.height()
                };
                for (float target : yTargets) {
                    float distance = Math.abs(rawY - target);
                    if (distance <= snapDistance && distance < bestYDistance) {
                        snappedY = target;
                        bestYDistance = distance;
                    }
                }
            }
            float[] alignedY = {
                    other.y(),
                    other.y() + (other.height() - moving.height()) * 0.5F,
                    other.y() + other.height() - moving.height()
            };
            for (float target : alignedY) {
                float distance = Math.abs(rawY - target);
                if (distance <= snapDistance && distance < bestYDistance) {
                    snappedY = target;
                    bestYDistance = distance;
                }
            }
        }
        return new SnapPoint(snappedX, snappedY);
    }

    private CompactAttachment compactAttachment(EditorItem moving, EditorItem other,
                                                boolean attachToRight) {
        if (moving.members().size() != 1
                || !HudFusionManager.compactReadout(moving.module())
                || other.members().stream().anyMatch(
                member -> !HudFusionManager.compactReadout(member))) {
            return null;
        }

        EpsilonHudModule anchor = edgeMember(other, attachToRight);
        float x = attachToRight
                ? anchor.hudX(width) + anchor.hudWidth(width)
                : anchor.hudX(width) - moving.module().hudWidth(width);
        return new CompactAttachment(x, anchor.hudY(height));
    }

    private EpsilonHudModule edgeMember(EditorItem item, boolean rightEdge) {
        if (item.members().size() == 1) return item.module();
        HudFusionManager.FusionSelection selection =
                HudFusionManager.selection(item.module(), width, height);
        EpsilonHudModule edge = item.members().getFirst();
        float edgePosition = rightEdge
                ? selection.cellBounds(edge).getRight()
                : selection.cellBounds(edge).getLeft();
        for (int index = 1; index < item.members().size(); index++) {
            EpsilonHudModule candidate = item.members().get(index);
            Rect cell = selection.cellBounds(candidate);
            float position = rightEdge ? cell.getRight() : cell.getLeft();
            if ((rightEdge && position > edgePosition)
                    || (!rightEdge && position < edgePosition)) {
                edge = candidate;
                edgePosition = position;
            }
        }
        return edge;
    }

    private void detachGroup(EditorItem item) {
        List<EpsilonHudModule> ordered = new ArrayList<>(item.members());
        HudFusionManager.FusionSelection selection =
                HudFusionManager.selection(item.module(), width, height);
        ordered.sort((first, second) -> Float.compare(
                selection.cellBounds(first).getLeft(),
                selection.cellBounds(second).getLeft()));

        float totalWidth = DETACH_GAP * Math.max(0, ordered.size() - 1);
        for (EpsilonHudModule member : ordered) totalWidth += member.hudWidth(width);
        float startX = HudRenderUtil.clamp(item.x(), EDGE_MARGIN,
                Math.max(EDGE_MARGIN, width - totalWidth - EDGE_MARGIN));
        float y = HudRenderUtil.clamp(item.y(), EDGE_MARGIN,
                Math.max(EDGE_MARGIN, height - item.height() - EDGE_MARGIN));
        float cursor = startX;
        for (EpsilonHudModule member : ordered) {
            member.moveTo(cursor, y, width, height);
            cursor += member.hudWidth(width) + DETACH_GAP;
        }
    }

    private static boolean rangesOverlap(float firstStart, float firstSize,
                                         float secondStart, float secondSize) {
        return firstStart < secondStart + secondSize
                && firstStart + firstSize > secondStart;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean wasInteracting = dragging != null
                || draggingNumber != null || draggingColor != null;
        dragging = null;
        draggingMembers = List.of();
        draggingNumber = null;
        draggingColor = null;
        draggingColorChannel = -1;
        return wasInteracting || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        List<Slot> slots = buildSlots();
        float bodyHeight = sidebarBodyHeight(slots);
        if (contains(mouseX, mouseY, sidebarX, sidebarY + SIDEBAR_HEADER, SIDEBAR_WIDTH, bodyHeight)) {
            sidebarScroll -= (float) verticalAmount * SCROLL_STEP;
            clampScroll(slots);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // --- layout --------------------------------------------------------------

    private List<Slot> buildSlots() {
        List<Slot> slots = new ArrayList<>();
        if (selectedModule == null) return slots;
        float y = 0.0F;
        for (Setting<?> setting : selectedModule.settings()) {
            if (isHudPosition(selectedModule, setting) || !setting.visible()) {
                continue;
            }
            float settingHeight = settingHeight(setting);
            slots.add(new Slot(setting, y, settingHeight));
            y += settingHeight;
        }
        return slots;
    }

    private static float contentHeight(List<Slot> slots) {
        if (slots.isEmpty()) {
            return 0.0F;
        }
        Slot last = slots.get(slots.size() - 1);
        return last.y() + last.height();
    }

    private float sidebarBodyHeight(List<Slot> slots) {
        float content = contentHeight(slots);
        float available = Math.max(0.0F,
                height - SIDEBAR_HEADER - SIDEBAR_FOOTER - EDGE_MARGIN * 2.0F);
        return Math.min(content, Math.min(MAX_SIDEBAR_BODY, available));
    }

    private static float settingHeight(Setting<?> setting) {
        if (setting instanceof IntSetting || setting instanceof DoubleSetting) {
            return NUMBER_HEIGHT;
        }
        if (setting instanceof ColorSetting colorSetting) {
            return SETTING_HEIGHT + (colorSetting.allowAlpha() ? 4 : 3) * COLOR_CHANNEL_HEIGHT;
        }
        return SETTING_HEIGHT;
    }

    private void clampScroll(List<Slot> slots) {
        float maxScroll = Math.max(0.0F, contentHeight(slots) - sidebarBodyHeight(slots));
        sidebarScroll = HudRenderUtil.clamp(sidebarScroll, 0.0F, maxScroll);
    }

    private boolean isHudPosition(EpsilonHudModule module, Setting<?> setting) {
        return setting == module.xPosition || setting == module.yPosition;
    }

    // --- items / helpers -----------------------------------------------------

    private List<EditorItem> items() {
        List<EditorItem> result = new ArrayList<>();
        Set<EpsilonHudModule> consumed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (EpsilonHudModule hud : enabledModules()) {
            if (consumed.contains(hud)) continue;
            HudFusionManager.FusionSelection fusion =
                    HudFusionManager.selection(hud, width, height);
            consumed.addAll(fusion.members());
            if (fusion.fused()) {
                Rect bounds = fusion.bounds();
                String label = fusion.members().stream()
                        .map(HudEditorScreen::compactLabel)
                        .reduce((first, second) -> first + " + " + second)
                        .orElse(compactLabel(fusion.owner()));
                result.add(new EditorItem(fusion.owner(), List.copyOf(fusion.members()), label,
                        bounds.getLeft(), bounds.getTop(), bounds.getWidth(), bounds.getHeight(),
                        fusion.owner().editorColor()));
                continue;
            }
            float itemWidth = hud.hudWidth(width);
            float itemHeight = hud.hudHeight(height);
            float itemX = hud.hudX(width);
            float itemY = hud.hudY(height);
            if (hud instanceof Notifications notifications && !notifications.position.is(Notifications.Position.CUSTOM)) {
                itemX = switch (notifications.position.get()) {
                    case TOP_CENTER -> (width - itemWidth) * 0.5F;
                    case TOP_LEFT, BOTTOM_LEFT -> 6.0F;
                    case TOP_RIGHT, BOTTOM_RIGHT -> width - itemWidth - 6.0F;
                    case CUSTOM -> itemX;
                };
                itemY = switch (notifications.position.get()) {
                    case TOP_CENTER, TOP_LEFT, TOP_RIGHT -> 6.0F;
                    case BOTTOM_LEFT, BOTTOM_RIGHT -> height - itemHeight - 6.0F;
                    case CUSTOM -> itemY;
                };
            }
            result.add(new EditorItem(hud, List.of(hud), compactLabel(hud), itemX, itemY,
                    itemWidth, itemHeight, hud.editorColor()));
        }
        return result;
    }

    private List<EpsilonHudModule> enabledModules() {
        List<EpsilonHudModule> result = new ArrayList<>();
        for (EpsilonHudModule module : hudModules()) if (module.isEnabled()) result.add(module);
        return result;
    }

    private List<EpsilonHudModule> hudModules() {
        List<EpsilonHudModule> result = new ArrayList<>();
        for (Module module : ModuleManager.INSTANCE.modules()) {
            if (module instanceof EpsilonHudModule hud) {
                result.add(hud);
            }
        }
        return result;
    }

    private void resetPositions() {
        for (EpsilonHudModule hud : hudModules()) hud.resetPosition();
        Notifications.INSTANCE.position.reset();
    }

    private static String compactLabel(EpsilonHudModule module) {
        return module.editorLabel().replace(" HUD", "");
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

    private static boolean contains(double mouseX, double mouseY,
                                    float x, float y, float width, float height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void onClose() {
        finishTextEditing();
        ConfigManager.INSTANCE.save();
        NotificationManager.INSTANCE.post(NotificationType.SUCCESS, "HUD Editor", "Layout saved");
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Slot(Setting<?> setting, float y, float height) {
    }

    private record SnapPoint(float x, float y) {
    }

    private record CompactAttachment(float x, float y) {
    }

    private record GroupDragOffset(EpsilonHudModule module, float offsetX, float offsetY) {
    }

    private record EditorItem(EpsilonHudModule module, List<EpsilonHudModule> members, String label,
                              float x, float y, float width, float height, int color) {
        boolean contains(double mouseX, double mouseY) {
            return HudEditorScreen.contains(mouseX, mouseY, x, y, width, height);
        }
    }
}
