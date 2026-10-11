package com.setsuna.ui.screen;

import com.setsuna.i18n.TranslationKey;
import com.setsuna.render.SkijaUi;
import io.github.humbleui.skija.Canvas;

/** Responsive home layout. Rendering and input share the same logical bounds. */
final class AuroraMenuView {
    enum Action { SINGLEPLAYER, MULTIPLAYER, ACCOUNTS, OPTIONS, CHANGELOG, VANILLA, QUIT }

    record Layout(float width, float height, float scale, float x, float y,
                  float contentWidth, float contentHeight, boolean wide) {
        UiControls.Box box(Action action) {
            float gap = 10;
            float cardsY = y + contentHeight - 164;
            float half = (contentWidth - gap) * 0.5F;
            float third = (contentWidth - gap * 2) / 3;
            return switch (action) {
                case SINGLEPLAYER -> new UiControls.Box(x, cardsY, half, 82);
                case MULTIPLAYER -> new UiControls.Box(x + half + gap, cardsY, half, 82);
                case ACCOUNTS -> new UiControls.Box(x, cardsY + 92, third, 34);
                case OPTIONS -> new UiControls.Box(x + third + gap, cardsY + 92, third, 34);
                case CHANGELOG -> new UiControls.Box(x + (third + gap) * 2, cardsY + 92, third, 34);
                case VANILLA -> new UiControls.Box(x, y + contentHeight - 24, 100, 24);
                case QUIT -> new UiControls.Box(x + contentWidth - 68, y + contentHeight - 24, 68, 24);
            };
        }

        Action hit(double screenX, double screenY) {
            for (Action action : Action.values()) {
                if (box(action).contains(screenX / scale, screenY / scale)) return action;
            }
            return null;
        }
    }

    private AuroraMenuView() {}

    static Layout layout(float viewportWidth, float viewportHeight) {
        float scale = Math.min(1, Math.min(Math.max(1, viewportWidth) / 360,
                Math.max(1, viewportHeight) / 360));
        float width = Math.max(1, viewportWidth) / scale;
        float height = Math.max(1, viewportHeight) / scale;
        float contentWidth = Math.min(850, width - 40);
        float contentHeight = Math.min(450, height - 40);
        return new Layout(width, height, scale, (width - contentWidth) * 0.5F,
                (height - contentHeight) * 0.5F, contentWidth, contentHeight, contentWidth >= 630);
    }

    static void draw(Canvas canvas, Layout layout, float time, float[] hovers, Action focused,
                     String version) {
        int saved = canvas.save();
        try {
            canvas.scale(layout.scale, layout.scale);
            ScreenGlass.Frame glass = ScreenGlass.drawBackground(canvas, layout.width, layout.height);
            float x = layout.x;
            float y = layout.y;
            AuroraBackdrop.mark(canvas, x, y + 1, 20);
            UiControls.brand(canvas, "SETSUNA", x + 30, y, 22, AuroraBackdrop.TEXT, 10, 2.2F);
            String edition = "AURORA / " + version;
            float editionWidth = SkijaUi.textWidth(edition, 7);
            SkijaUi.text(canvas, edition, x + layout.contentWidth - editionWidth, y, 22,
                    AuroraBackdrop.FAINT, 7);

            float heroY = y + (layout.wide ? 62 : 48);
            SkijaUi.boldText(canvas, tr("eyebrow", "YOUR NEXT CHAPTER"), x, heroY, 14,
                    AuroraBackdrop.ACCENT, 7.5F);
            SkijaUi.boldText(canvas, tr("title", "Make it yours."), x, heroY + 17,
                    layout.wide ? 51 : 34, AuroraBackdrop.TEXT, layout.wide ? 37 : 27);
            SkijaUi.text(canvas, tr("subtitle", "A familiar world. A fresh perspective."), x,
                    heroY + (layout.wide ? 74 : 57), 16, AuroraBackdrop.MUTED, 9);
            for (Action action : Action.values()) {
                float hover = hovers == null ? 0 : hovers[action.ordinal()];
                drawAction(canvas, glass, layout.box(action), action, hover, focused == action);
            }
        } finally {
            canvas.restoreToCount(saved);
        }
    }

    private static void drawAction(Canvas canvas, ScreenGlass.Frame glass, UiControls.Box box,
                                   Action action, float hover, boolean focused) {
        boolean primary = action == Action.SINGLEPLAYER || action == Action.MULTIPLAYER;
        boolean footer = action == Action.VANILLA || action == Action.QUIT;
        glass.surface(canvas, box, primary ? 10 : 6, footer ? 0x3010151B : 0x5C10151B, 0x45DBE3E7);
        if (!footer) {
            if (hover > 0.001F) {
                SkijaUi.rounded(canvas, box.x(), box.y(), box.width(), box.height(), primary ? 12 : 8,
                        (Math.round(hover * 20) << 24) | 0x99E5D5);
            }
        }
        if (focused) SkijaUi.outline(canvas, box.x(), box.y(), box.width(), box.height(),
                primary ? 12 : 8, 1.2F, AuroraBackdrop.ACCENT);
        String label = label(action);
        if (primary) {
            String index = action == Action.SINGLEPLAYER ? "01" : "02";
            SkijaUi.text(canvas, index, box.x() + 14, box.y() + 9, 13, AuroraBackdrop.ACCENT, 7);
            SkijaUi.boldText(canvas, label, box.x() + 14, box.y() + 28, 20, AuroraBackdrop.TEXT, 12);
            SkijaUi.text(canvas, action == Action.SINGLEPLAYER
                            ? tr("single_hint", "Explore your worlds") : tr("multi_hint", "Find your community"),
                    box.x() + 14, box.y() + 53, 14, AuroraBackdrop.MUTED, 7.5F);
            float arrowX = box.x() + box.width() - 18;
            float arrowY = box.y() + 18;
            SkijaUi.line(canvas, arrowX - 5, arrowY + 5, arrowX + 2, arrowY - 2, 1,
                    AuroraBackdrop.ACCENT);
            SkijaUi.line(canvas, arrowX - 4, arrowY - 2, arrowX + 2, arrowY - 2, 1,
                    AuroraBackdrop.ACCENT);
            SkijaUi.line(canvas, arrowX + 2, arrowY - 2, arrowX + 2, arrowY + 4, 1,
                    AuroraBackdrop.ACCENT);
        } else {
            int color = hover > 0.2F || focused ? AuroraBackdrop.TEXT : AuroraBackdrop.MUTED;
            float labelWidth = SkijaUi.textWidth(label, 8);
            float labelX = box.x() + (box.width() - labelWidth) * 0.5F;
            SkijaUi.text(canvas, label, labelX, box.y(), box.height(), color, 8);
        }
    }

    private static String label(Action action) {
        return switch (action) {
            case SINGLEPLAYER -> tr("singleplayer", "Singleplayer");
            case MULTIPLAYER -> tr("multiplayer", "Multiplayer");
            case ACCOUNTS -> tr("accounts", "Accounts");
            case OPTIONS -> tr("options", "Settings");
            case CHANGELOG -> tr("changelog", "What's new");
            case VANILLA -> tr("vanilla", "Minecraft menu");
            case QUIT -> tr("quit", "Quit game");
        };
    }

    private static String tr(String key, String fallback) {
        return TranslationKey.of("setsuna.menu." + key, fallback).get();
    }
}
