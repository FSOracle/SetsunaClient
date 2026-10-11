package com.setsuna.ui.screen;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.EncodedImageFormat;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ChangelogScreenTest {
    @Test
    void dialogAndEntranceAnimationStayInsideCompactPortraitAndWideViewports() {
        for (int[] size : new int[][]{{960, 540}, {320, 180}, {320, 540}, {200, 120}, {384, 324}, {1920, 360}}) {
            ChangelogLayout layout = ChangelogLayout.forViewport(size[0], size[1]);
            UiControls.Box panel = layout.panel();
            assertTrue(panel.x() >= 0 && panel.y() >= 0);
            assertTrue((panel.x() + panel.width()) * layout.scale() <= size[0] + .01F);
            assertTrue((panel.y() + panel.height() + 18) * layout.scale() <= size[1] + .01F);
            for (float slide : new float[]{0, 18}) {
                double x = (panel.x() + panel.width() * .5) * layout.scale();
                double y = (panel.y() + panel.height() * .5 + slide) * layout.scale();
                assertTrue(panel.contains(layout.logicalX(x), layout.logicalY(y, slide)));
            }
        }
    }

    @Test
    void rendersAndAcceptsScaledCheckboxConfirmWheelAndScrollbarInput() throws Exception {
        Field loaded = field(ScreenBackdrop.class, "backgroundStateLoaded");
        boolean previous = loaded.getBoolean(null);
        loaded.setBoolean(null, true);
        try {
            boolean testedScrolling = false;
            for (int[] size : new int[][]{{960, 540}, {320, 180}, {320, 540}, {200, 120}}) {
                ChangelogScreen screen = screen(size[0], size[1]);
                render(screen, "top");
                ChangelogLayout layout = ChangelogLayout.forViewport(size[0], size[1]);
                float checkboxX = number(screen, "checkboxX");
                float checkboxY = number(screen, "checkboxY");
                assertTrue(screen.mouseClicked(click(layout, checkboxX + 7, checkboxY + 7), false));
                assertTrue(field(ChangelogScreen.class, "dontShowAgain").getBoolean(screen));
                AtomicInteger confirms = new AtomicInteger();
                float buttonX = number(screen, "buttonX");
                float buttonY = number(screen, "buttonY");
                assertTrue(screen.handlePrimaryClick((buttonX + 66) * layout.scale(),
                        (buttonY + 16) * layout.scale(), confirms::incrementAndGet));
                assertEquals(1, confirms.get());
                assertFalse(screen.handlePrimaryClick(0, 0, confirms::incrementAndGet));
                assertEquals(1, confirms.get());
                float contentY = (number(screen, "contentTop") + number(screen, "contentBottom")) * .5F;
                assertFalse(screen.mouseScrolled(0, 0, 0, -1));
                float overflow = Math.max(0, number(screen, "contentHeight")
                        - (number(screen, "contentBottom") - number(screen, "contentTop")));
                if (overflow <= .5F) {
                    assertFalse(screen.mouseScrolled((number(screen, "contentLeft") + 10) * layout.scale(),
                            contentY * layout.scale(), 0, -2));
                    assertEquals(0, number(screen, "scrollTarget"));
                    continue;
                }
                testedScrolling = true;
                assertTrue(screen.mouseScrolled((number(screen, "contentLeft") + 10) * layout.scale(),
                        contentY * layout.scale(), 0, -2));
                assertTrue(number(screen, "scrollTarget") > 0);
                float scrollbarX = number(screen, "scrollbarX");
                float scrollbarTop = number(screen, "scrollbarTop");
                assertTrue(screen.mouseClicked(click(layout, scrollbarX + 2, scrollbarTop + 10), false));
                assertTrue(screen.mouseDragged(click(layout, scrollbarX + 2,
                        scrollbarTop + number(screen, "scrollbarHeight")), 0, 50));
                assertEquals(overflow, number(screen, "scrollTarget"), .01F);
                field(ChangelogScreen.class, "scrollOffset").setFloat(screen, number(screen, "scrollTarget"));
                render(screen, "bottom");
            }
            assertTrue(testedScrolling, "Compact viewports must exercise scrolling to the end of the changelog");
        } finally {
            loaded.setBoolean(null, previous);
        }
    }

    private static void render(ChangelogScreen screen, String position) throws Exception {
        try (Surface surface = Surface.makeRasterN32Premul(screen.width * 2, screen.height * 2)) {
            Canvas canvas = surface.getCanvas();
            canvas.scale(2, 2);
            int saved = canvas.getSaveCount();
            screen.drawScreen(canvas);
            assertEquals(saved, canvas.getSaveCount());
            Path path = Path.of("build", "ui-previews", "changelog-" + screen.width + "x" + screen.height + "-" + position + ".png");
            Files.createDirectories(path.getParent());
            try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData(EncodedImageFormat.PNG)) {
                assertNotNull(data);
                assertTrue(data.getSize() > 2000);
                Files.write(path, data.getBytes());
            }
        }
    }

    private static ChangelogScreen screen(int width, int height) throws Exception {
        Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        ChangelogScreen screen = (ChangelogScreen) unsafe.allocateInstance(ChangelogScreen.class);
        field(ChangelogScreen.class, "openedAt").setLong(screen, System.nanoTime() - 1_000_000_000L);
        field(ChangelogScreen.class, "lastFrame").setLong(screen, System.nanoTime() - 50_000_000L);
        screen.width = width;
        screen.height = height;
        return screen;
    }

    private static MouseButtonEvent click(ChangelogLayout layout, float x, float y) {
        return new MouseButtonEvent(x * layout.scale(), y * layout.scale(),
                new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
    }

    private static float number(ChangelogScreen screen, String name) throws Exception {
        return field(ChangelogScreen.class, name).getFloat(screen);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
