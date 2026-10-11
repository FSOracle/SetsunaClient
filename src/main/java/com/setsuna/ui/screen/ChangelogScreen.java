package com.setsuna.ui.screen;

import com.setsuna.Setsuna;
import com.setsuna.render.SkijaUi;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.types.RRect;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Post-startup changelog dialog: a frosted panel over the main-menu backdrop with a
 * scrollable body, an "already read" checkbox, and a confirm button.
 */
public final class ChangelogScreen extends AbstractSkijaScreen {

    private static final String CURRENT_VERSION = "B15";
    private static final String PREVIOUS_VERSION = "B14";

    private static final float CORNER = 12.0F;
    private static final float HEADER_HEIGHT = 74.0F;
    private static final float FOOTER_HEIGHT = 62.0F;
    private static final float PAD = 26.0F;
    private static final float SCROLLBAR_INSET = 10.0F;
    private static final float SCROLLBAR_WIDTH = 4.0F;
    private static final float WHEEL_STEP = 34.0F;

    private static final float LINE_HEIGHT = 16.0F;
    private static final float TITLE_HEIGHT = 19.0F;
    private static final float GAP_HEIGHT = 9.0F;
    private static final float SECTION_GAP = 18.0F;

    private static final float CHECKBOX_SIZE = 15.0F;
    private static final float BUTTON_WIDTH = 132.0F;
    private static final float BUTTON_HEIGHT = 32.0F;

    private static final List<Row> ROWS = List.of(
        Row.bullet("B15 开源版：客户端无需 Setsuna 账户，启动后即可进入主菜单并使用本地功能。"),
        Row.gap(),
        Row.bullet("移除 Setsuna 云端配置：不再提供配置发布、分享码、云端浏览与下载；本地配置仍可保存、加载、删除和管理。"),
        Row.gap(),
        Row.bullet("移除 IRC 聊天、在线称号和外观同步；不再上传或下载 Setsuna 账户头像、皮肤与披风。"),
        Row.gap(),
        Row.bullet("保留 ClickGUI、HUD、本地模块设置和按键绑定；加载配置时仍可选择是否应用界面、HUD 与按键设置。"),
        Row.gap(),
        Row.bullet("篮球、足球和台球保留本地玩法，移除通过 Setsuna 云服务邀请玩家和同步对局的功能。"),
        Row.gap(),
        Row.bullet("保留网易云音乐、QQ 音乐等在线音乐功能，以及微软 Minecraft 账户登录与游戏账户管理。")
    );

    private final long openedAt = System.nanoTime();

    private long lastFrame = openedAt;
    private float scrollOffset;
    private float scrollTarget;
    private float contentHeight;
    private boolean dontShowAgain;
    private boolean draggingScrollbar;

    private float checkboxX, checkboxY;
    private float buttonX, buttonY;
    private float contentTop, contentBottom, contentLeft, contentRight;
    private float scrollbarX, scrollbarTop, scrollbarHeight;
    private float panelX, panelY, panelWidth, panelHeight;
    private ChangelogLayout viewport;
    private float renderedSlide;

    public ChangelogScreen() {
        super(Component.literal(Setsuna.clientName() + " - 版本更新公告"));
    }

    /** Marker file lives next to the mod config so it survives across launcher profiles. */
    private static Path markerPath() {
        return Setsuna.mc().gameDirectory.toPath()
                .resolve(Setsuna.MOD_ID)
                .resolve("changelog_" + CURRENT_VERSION + ".read");
    }

    public static boolean shouldShow() {
        return !Files.exists(markerPath());
    }

    public static void markAsRead() {
        try {
            Path marker = markerPath();
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, CURRENT_VERSION);
        } catch (IOException e) {
            Setsuna.LOGGER.warn("Failed to persist changelog read marker", e);
        }
    }

    @Override
    protected int backdropColor() {
        return 0xFF000000;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void drawScreen(Canvas canvas) {
        long now = System.nanoTime();
        float delta = frameDelta(now);
        float intro = easeOut(clamp((now - openedAt) / 1_000_000_000.0F / 0.42F, 0.0F, 1.0F));

        // Background: the animated main-menu backdrop so the dialog sits on a real image.
        ScreenBackdrop.drawMainMenu(canvas, width, height,
                now / 1_000_000_000.0F, mouseX, mouseY, 46);
        SkijaUi.fill(canvas, 0.0F, 0.0F, width, height, 0x7A000000);

        layout();

        renderedSlide = (1.0F - intro) * 18.0F;
        int saved = canvas.save();
        try {
            canvas.scale(viewport.scale(), viewport.scale());
            canvas.translate(0.0F, renderedSlide);
            drawPanel(canvas);
            drawHeader(canvas);
            drawBody(canvas, delta);
            drawFooter(canvas);
            drawScrollbar(canvas);
        } finally {
            canvas.restoreToCount(saved);
        }
    }

    // ---------------------------------------------------------------- layout

    private void layout() {
        viewport = ChangelogLayout.forViewport(width, height);
        panelWidth = viewport.panel().width();
        panelHeight = viewport.panel().height();
        panelX = viewport.panel().x();
        panelY = viewport.panel().y();

        contentLeft = panelX + PAD;
        contentRight = panelX + panelWidth - PAD - SCROLLBAR_INSET - SCROLLBAR_WIDTH;
        contentTop = panelY + HEADER_HEIGHT + 12.0F;
        contentBottom = panelY + panelHeight - FOOTER_HEIGHT - 4.0F;

        scrollbarX = panelX + panelWidth - PAD - SCROLLBAR_WIDTH;
        scrollbarTop = contentTop;
        scrollbarHeight = Math.max(1.0F, contentBottom - contentTop);

        float footerCenterY = panelY + panelHeight - FOOTER_HEIGHT * 0.5F;
        checkboxX = panelX + PAD;
        checkboxY = footerCenterY - CHECKBOX_SIZE * 0.5F;
        buttonX = panelX + panelWidth - PAD - BUTTON_WIDTH;
        buttonY = footerCenterY - BUTTON_HEIGHT * 0.5F;

        // Needed before the first draw so hit-testing and the wheel clamp are already valid.
        contentHeight = measureContent();
        scrollTarget = clamp(scrollTarget, 0, maxScroll());
        scrollOffset = clamp(scrollOffset, 0, maxScroll());
    }

    private float viewportHeight() {
        return Math.max(1.0F, contentBottom - contentTop);
    }

    private float maxScroll() {
        return Math.max(0.0F, contentHeight - viewportHeight());
    }

    private float textWidth() {
        return Math.max(40.0F, contentRight - contentLeft);
    }

    // ------------------------------------------------------------ panel parts

    private void drawPanel(Canvas canvas) {
        RRect shape = RRect.makeXYWH(panelX, panelY, panelWidth, panelHeight, CORNER);

        SkijaUi.dropShadowRounded(canvas, panelX, panelY, panelWidth, panelHeight,
                CORNER, 6.0F, 26.0F, 0x8C000000);

        // Frosted core: blurred copy of the backdrop, then dark and tinted glass on top.
        com.setsuna.render.SkijaRenderer.drawGaussianBackdrop(canvas, shape,
                panelX, panelY, panelWidth, panelHeight, 20.0F);

        SkijaUi.rounded(canvas, panelX, panelY, panelWidth, panelHeight, CORNER,
                UiTheme.argb(196, 8, 11, 13));
        SkijaUi.rounded(canvas, panelX, panelY, panelWidth, panelHeight, CORNER,
                UiTheme.argb(84, 30, 38, 42));

        // Header/footer tint bands keep the glass reading as one surface.
        canvas.save();
        canvas.clipRRect(shape, ClipMode.INTERSECT, true);
        SkijaUi.gradient(canvas, panelX, panelY, panelWidth, HEADER_HEIGHT,
                UiTheme.themedHeader(150), UiTheme.themedHeader(0), true, 0.0F);
        SkijaUi.gradient(canvas, panelX, panelY + panelHeight - FOOTER_HEIGHT,
                panelWidth, FOOTER_HEIGHT, UiTheme.themedSurface(0), UiTheme.themedSurface(120),
                true, 0.0F);
        canvas.restore();

        SkijaUi.outline(canvas, panelX, panelY, panelWidth, panelHeight, CORNER, 1.0F,
                UiTheme.withAlpha(UiTheme.accent(), 96));
        SkijaUi.outline(canvas, panelX + 1.0F, panelY + 1.0F, panelWidth - 2.0F, panelHeight - 2.0F,
                CORNER - 1.0F, 1.0F, 0x14FFFFFF);
    }

    private void drawHeader(Canvas canvas) {
        float textLeft = panelX + PAD;
        String badge = PREVIOUS_VERSION + "  →  " + CURRENT_VERSION;
        float badgeWidth = SkijaUi.textWidth(badge, 11.5F) + 20.0F;
        float badgeX = panelX + panelWidth - PAD - badgeWidth;
        float titleSize = Math.min(19, 19 * Math.max(1, badgeX - textLeft - 14)
                / SkijaUi.boldTextWidth("版本更新公告", 19));
        SkijaUi.boldText(canvas, "版本更新公告", textLeft, panelY + 20.0F, TITLE_HEIGHT,
                UiTheme.TEXT, titleSize);
        SkijaUi.rounded(canvas, badgeX, panelY + 20.0F, badgeWidth, 20.0F, 10.0F,
                UiTheme.withAlpha(UiTheme.accent(), 34));
        SkijaUi.outline(canvas, badgeX, panelY + 20.0F, badgeWidth, 20.0F, 10.0F, 1.0F,
                UiTheme.withAlpha(UiTheme.accent(), 110));
        SkijaUi.text(canvas, badge, badgeX + 10.0F, panelY + 20.0F, 20.0F,
                UiTheme.withAlpha(UiTheme.accent(), 235), 11.5F);

        SkijaUi.line(canvas, panelX + PAD, panelY + HEADER_HEIGHT - 1.0F,
                panelX + panelWidth - PAD, panelY + HEADER_HEIGHT - 1.0F, 1.0F, 0x1FFFFFFF);
    }

    private void drawBody(Canvas canvas, float delta) {
        // Latch onto the wheel target so scrolling feels smooth rather than steppy.
        float factor = (float) (1.0 - Math.exp(-18.0 * delta));
        scrollOffset += (scrollTarget - scrollOffset) * factor;
        scrollTarget = clamp(scrollTarget, 0.0F, maxScroll());
        if (Math.abs(scrollTarget - scrollOffset) < 0.05F) scrollOffset = scrollTarget;

        canvas.save();
        canvas.clipRRect(RRect.makeXYWH(panelX + 1.0F, contentTop, panelWidth - 2.0F,
                viewportHeight(), 6.0F), ClipMode.INTERSECT, true);

        float y = contentTop - scrollOffset;
        for (Row row : ROWS) {
            float height = rowHeight(row);
            if (y + height >= contentTop - 24.0F && y <= contentBottom + 24.0F) {
                drawRow(canvas, row, y, height);
            }
            y += height;
        }

        canvas.restore();
    }

    private void drawRow(Canvas canvas, Row row, float y, float height) {
        switch (row.kind) {
            case GAP -> {
                // Spacing only.
            }
            case SECTION -> {
                SkijaUi.rounded(canvas, contentLeft, y + 5.0F, 3.0F, height - 10.0F, 1.5F,
                        UiTheme.accent());
                SkijaUi.boldText(canvas, row.text, contentLeft + 12.0F, y, height,
                        UiTheme.TEXT, 15.0F);
            }
            case LABEL -> {
                SkijaUi.boldText(canvas, row.text, contentLeft + 14.0F, y, height,
                        UiTheme.withAlpha(UiTheme.accent(), 240), 13.5F);
            }
            default -> {
                float bulletX = contentLeft + 18.0F;
                float textX = bulletX + 12.0F;
                SkijaUi.rounded(canvas, bulletX, y + height * 0.5F - 1.5F, 3.0F, 3.0F, 1.5F,
                        UiTheme.TEXT_FAINT);
                float lineY = y;
                for (String line : wrap(row.text, textWidth() - 30.0F)) {
                    SkijaUi.text(canvas, line, textX, lineY, LINE_HEIGHT, UiTheme.TEXT_MUTED, 13.0F);
                    lineY += LINE_HEIGHT;
                }
            }
        }
    }

    private void drawFooter(Canvas canvas) {
        SkijaUi.line(canvas, panelX + PAD, panelY + panelHeight - FOOTER_HEIGHT,
                panelX + panelWidth - PAD, panelY + panelHeight - FOOTER_HEIGHT, 1.0F, 0x1FFFFFFF);

        double logicalX = viewport.logicalX(mouseX);
        double logicalY = viewport.logicalY(mouseY, renderedSlide);
        boolean hoveredBox = isInsideCheckbox(logicalX, logicalY);
        int boxFill = hoveredBox ? UiTheme.themedHover(200) : UiTheme.themedRaised(180);
        SkijaUi.rounded(canvas, checkboxX, checkboxY, CHECKBOX_SIZE, CHECKBOX_SIZE, 4.0F,
                dontShowAgain ? UiTheme.withAlpha(UiTheme.accent(), 210) : boxFill);
        SkijaUi.outline(canvas, checkboxX, checkboxY, CHECKBOX_SIZE, CHECKBOX_SIZE, 4.0F, 1.0F,
                dontShowAgain ? UiTheme.withAlpha(UiTheme.accent(), 255) : UiTheme.BORDER_STRONG);

        if (dontShowAgain) {
            int mark = UiTheme.rgb(10, 12, 13);
            float cx = checkboxX + 3.5F;
            float cy = checkboxY + 7.5F;
            SkijaUi.line(canvas, cx, cy, cx + 3.0F, cy + 3.2F, 2.0F, mark);
            SkijaUi.line(canvas, cx + 3.0F, cy + 3.2F, cx + 8.0F, cy - 3.6F, 2.0F, mark);
        }

        float checkboxLabelSize = Math.min(12.5F, 12.5F * Math.max(1,
                buttonX - 12 - (checkboxX + CHECKBOX_SIZE + 9)) / SkijaUi.textWidth("已阅读，以后不再显示", 12.5F));
        SkijaUi.text(canvas, "已阅读，以后不再显示",
                checkboxX + CHECKBOX_SIZE + 9.0F, checkboxY, CHECKBOX_SIZE,
                hoveredBox ? UiTheme.TEXT : UiTheme.TEXT_MUTED, checkboxLabelSize);

        boolean hoveredButton = isInsideButton(logicalX, logicalY);
        int fill = hoveredButton ? UiTheme.themedHover(235) : UiTheme.themedRaised(220);
        SkijaUi.rounded(canvas, buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT, 7.0F, fill);
        SkijaUi.outline(canvas, buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT, 7.0F, 1.0F,
                hoveredButton ? UiTheme.withAlpha(UiTheme.accent(), 220)
                        : UiTheme.withAlpha(UiTheme.accent(), 110));

        String label = "确认";
        float labelWidth = SkijaUi.boldTextWidth(label, 13.5F);
        SkijaUi.boldText(canvas, label, buttonX + (BUTTON_WIDTH - labelWidth) * 0.5F,
                buttonY, BUTTON_HEIGHT, hoveredButton ? UiTheme.TEXT : UiTheme.TEXT_MUTED, 13.5F);
    }

    private void drawScrollbar(Canvas canvas) {
        float scrollable = maxScroll();
        if (scrollable <= 0.5F) return;

        float ratio = viewportHeight() / (contentHeight <= 0.0F ? 1.0F : contentHeight);
        float thumbHeight = Math.max(28.0F, scrollbarHeight * clamp(ratio, 0.0F, 1.0F));
        float travel = scrollbarHeight - thumbHeight;
        float thumbY = scrollbarTop + travel * clamp(scrollOffset / scrollable, 0.0F, 1.0F);

        SkijaUi.rounded(canvas, scrollbarX, scrollbarTop, SCROLLBAR_WIDTH, scrollbarHeight,
                SCROLLBAR_WIDTH * 0.5F, 0x1AFFFFFF);
        SkijaUi.rounded(canvas, scrollbarX, thumbY, SCROLLBAR_WIDTH, thumbHeight,
                SCROLLBAR_WIDTH * 0.5F, UiTheme.withAlpha(UiTheme.accent(), 190));
    }

    // ----------------------------------------------------------- measurement

    private float measureContent() {
        float total = 0.0F;
        for (Row row : ROWS) {
            total += rowHeight(row);
        }
        return total;
    }

    private float rowHeight(Row row) {
        return switch (row.kind) {
            case GAP -> GAP_HEIGHT;
            case SECTION -> TITLE_HEIGHT + SECTION_GAP;
            case LABEL -> LINE_HEIGHT + 2.0F;
            default -> wrap(row.text, textWidth() - 30.0F).size() * LINE_HEIGHT + 2.0F;
        };
    }

    /** Greedy wrap that handles CJK (no spaces) by falling back to per-character breaks. */
    private static List<String> wrap(String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String candidate = current.toString() + c;
            if (SkijaUi.textWidth(candidate, 13.0F) > maxWidth && current.length() > 0) {
                lines.add(current.toString());
                current.setLength(0);
            }
            current.append(c);
        }
        if (current.length() > 0 || lines.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    // ---------------------------------------------------------------- input

    private boolean isInsideCheckbox(double mx, double my) {
        return mx >= checkboxX - 4.0F && mx <= buttonX - 12.0F
                && my >= checkboxY - 4.0F && my <= checkboxY + CHECKBOX_SIZE + 4.0F;
    }

    private boolean isInsideButton(double mx, double my) {
        return mx >= buttonX && mx <= buttonX + BUTTON_WIDTH
                && my >= buttonY && my <= buttonY + BUTTON_HEIGHT;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        layout();
        double x = viewport.logicalX(mouseX);
        double y = viewport.logicalY(mouseY, renderedSlide);
        if (x < panelX || x > panelX + panelWidth || y < contentTop || y > contentBottom) return false;
        if (maxScroll() <= 0.5F) return false;
        scrollTarget = clamp(scrollTarget - (float) scrollY * WHEEL_STEP, 0.0F, maxScroll());
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        if (handlePrimaryClick(event.x(), event.y(), this::confirm)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    boolean handlePrimaryClick(double mouseX, double mouseY, Runnable confirmed) {
        layout();
        double x = viewport.logicalX(mouseX);
        double y = viewport.logicalY(mouseY, renderedSlide);
        if (isInsideCheckbox(x, y)) {
            dontShowAgain = !dontShowAgain;
            return true;
        }
        if (isInsideButton(x, y)) {
            confirmed.run();
            return true;
        }
        if (maxScroll() > 0.5F && x >= scrollbarX - 6.0F && x <= scrollbarX + SCROLLBAR_WIDTH + 6.0F
                && y >= scrollbarTop && y <= scrollbarTop + scrollbarHeight) {
            draggingScrollbar = true;
            return true;
        }
        return false;
    }

    private void confirm() {
        if (dontShowAgain) markAsRead();
        minecraft.setScreen(new MainMenuScreen());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingScrollbar = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        layout();
        if (draggingScrollbar && maxScroll() > 0.5F) {
            float ratio = viewportHeight() / (contentHeight <= 0.0F ? 1.0F : contentHeight);
            float thumbHeight = Math.max(28.0F, scrollbarHeight * clamp(ratio, 0.0F, 1.0F));
            float travel = Math.max(1.0F, scrollbarHeight - thumbHeight);
            float progress = clamp((float) (viewport.logicalY(event.y(), renderedSlide) - scrollbarTop - thumbHeight * 0.5F) / travel,
                    0.0F, 1.0F);
            scrollTarget = progress * maxScroll();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    // ------------------------------------------------------------- utilities

    private float frameDelta(long now) {
        float delta = Math.min(0.05F, Math.max(0.0F, (now - lastFrame) / 1_000_000_000.0F));
        lastFrame = now;
        return delta;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float easeOut(float value) {
        float remaining = 1.0F - clamp(value, 0.0F, 1.0F);
        return 1.0F - remaining * remaining * remaining;
    }

    // ------------------------------------------------------------ data model

    private enum Kind { SECTION, LABEL, BULLET, GAP }

    private record Row(Kind kind, String text) {
        static Row section(String text) { return new Row(Kind.SECTION, text); }
        static Row label(String text) { return new Row(Kind.LABEL, text); }
        static Row bullet(String text) { return new Row(Kind.BULLET, text); }
        static Row gap() { return new Row(Kind.GAP, ""); }
    }
}
