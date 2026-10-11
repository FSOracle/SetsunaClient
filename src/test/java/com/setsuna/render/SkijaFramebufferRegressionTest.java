package com.setsuna.render;

import com.mojang.blaze3d.platform.Window;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real GPU regression checks; enable with -Dsetsuna.test.gpu=true on a desktop host. */
@Tag("gpu")
class SkijaFramebufferRegressionTest {
    private static final int SIZE = 64;
    private static final long CAPTURE_INTERVAL = 16_666_667L;
    private Minecraft previousMinecraft;
    private GLFWErrorCallback errorCallback;
    private long windowHandle;
    private int framebuffer;
    private int texture;
    private int vertexArray;
    private boolean initialized;
    private boolean contextReady;

    @BeforeEach
    void createHiddenGpuContext() throws Exception {
        assumeTrue(Boolean.getBoolean("setsuna.test.gpu"), "Explicit desktop GPU test opt-in");
        previousMinecraft = Minecraft.getInstance();
        errorCallback = GLFWErrorCallback.createPrint(System.err);
        errorCallback.set();
        assertTrue(GLFW.glfwInit(), "GLFW initialization");
        initialized = true;
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        windowHandle = GLFW.glfwCreateWindow(SIZE, SIZE, "Skija framebuffer regression", 0L, 0L);
        assertTrue(windowHandle != 0L, "Hidden OpenGL context");
        GLFW.glfwMakeContextCurrent(windowHandle);
        GL.createCapabilities();
        contextReady = true;
        // Core-profile element-buffer restoration requires a real VAO.
        vertexArray = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vertexArray);
        System.out.println("GPU: " + GL11.glGetString(GL11.GL_VENDOR) + " / "
                + GL11.glGetString(GL11.GL_RENDERER) + " / " + GL11.glGetString(GL11.GL_VERSION));

        Minecraft minecraft = allocate(Minecraft.class);
        Window window = allocate(Window.class);
        field(Window.class, "guiScaledWidth").setInt(window, SIZE);
        field(Window.class, "guiScaledHeight").setInt(window, SIZE);
        field(Window.class, "framebufferWidth").setInt(window, SIZE);
        field(Window.class, "framebufferHeight").setInt(window, SIZE);
        field(Window.class, "guiScale").setInt(window, 1);
        field(Minecraft.class, "window").set(minecraft, window);
        field(Minecraft.class, "instance").set(null, minecraft);

        texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, SIZE, SIZE,
                0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        framebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, texture, 0);
        assertEquals(GL30.GL_FRAMEBUFFER_COMPLETE, GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER));
        assertNoGlError("setup");
    }

    @AfterEach
    void releaseGpuContext() throws Exception {
        try {
            if (contextReady) {
                method("resetGpuBridge").invoke(null);
                GL30.glDeleteFramebuffers(framebuffer);
                GL11.glDeleteTextures(texture);
                GL30.glDeleteVertexArrays(vertexArray);
                GL.setCapabilities(null);
            }
        } finally {
            if (initialized) {
                field(Minecraft.class, "instance").set(null, previousMinecraft);
                if (windowHandle != 0L) GLFW.glfwDestroyWindow(windowHandle);
                GLFW.glfwTerminate();
            }
            if (errorCallback != null) {
                GLFW.glfwSetErrorCallback(null);
                errorCallback.free();
            }
        }
    }

    @Test
    void keepsMultipleNativePanelsAndItemsAcrossCapturedAndSkippedFrames() throws Exception {
        for (int frame = 0; frame < 6; frame++) {
            int background = frame % 2 == 0 ? 0xFF202020 : 0xFF404040;
            externalClear(framebuffer, background);
            expireCapture("mainBackdrop");
            draw(framebuffer, true, canvas -> panel(canvas, 4, 4, 16, 16, 0xFFFF0000));
            Image snapshot = snapshot("mainBackdrop");
            assertNotNull(snapshot, "First native pass captured a GPU backdrop");

            // Force the same timing branch used by additional HUDs/high-FPS frames.
            postponeCapture("mainBackdrop");
            draw(framebuffer, true, canvas -> panel(canvas, 24, 4, 16, 16, 0xFF0000FF));
            assertSame(snapshot, snapshot("mainBackdrop"), "Second panel must reuse the snapshot");
            draw(framebuffer, false, canvas -> panel(canvas, 44, 4, 16, 16, 0xFF00FF00));
            assertPixel(framebuffer, 10, 10, 0xFFFF0000, "first native panel, frame " + frame);
            assertPixel(framebuffer, 30, 10, 0xFF0000FF, "second native panel, frame " + frame);
            assertPixel(framebuffer, 50, 10, 0xFF00FF00, "third native panel, frame " + frame);
            assertPixel(framebuffer, 30, 48, background, "untouched game background, frame " + frame);

            // Vanilla item drawing occurs after all native panel painters.
            externalMarker(framebuffer, 8, 32, 8, 8, 0xFFFFFF00);
            assertPixel(framebuffer, 10, 34, 0xFFFFFF00, "vanilla item survives, frame " + frame);
            externalClear(0, 0xFF080808);
            draw(0, frame % 2 == 0, canvas -> panel(canvas, 4, 4, 12, 12, 0xFFFF00FF));
            assertPixel(0, 10, 10, 0xFFFF00FF, "window overlay, frame " + frame);
            assertPixel(framebuffer, 10, 10, 0xFFFF0000, "main panel after window pass, frame " + frame);
            assertPixel(framebuffer, 10, 34, 0xFFFFFF00, "main item after window pass, frame " + frame);
            assertNoGlError("multi-panel frame " + frame);
        }
    }

    @Test
    void recapturesExternalFramebufferChangesAndKeepsBlurWithinItsPanel() throws Exception {
        externalClear(framebuffer, 0xFFFF0000);
        draw(framebuffer, true, canvas -> { });
        Image first = snapshot("mainBackdrop");
        assertNotNull(first);

        // No Skija drawing occurred between captures: only the game changed the FBO.
        externalClear(framebuffer, 0xFF0000FF);
        expireCapture("mainBackdrop");
        draw(framebuffer, true, canvas -> SkijaRenderer.drawGaussianBackdrop(canvas,
                RRect.makeXYWH(8, 8, 24, 24, 0), 8, 8, 24, 24, 2));
        assertPixel(framebuffer, 16, 16, 0xFF0000FF, "refreshed blur must sample the new game frame");
        assertPixel(framebuffer, 48, 48, 0xFF0000FF, "blur must preserve pixels outside its clip");

        postponeCapture("mainBackdrop");
        Image reused = snapshot("mainBackdrop");
        externalClear(framebuffer, 0xFF00FF00);
        draw(framebuffer, true, canvas -> {
            SkijaRenderer.drawGaussianBackdrop(canvas, RRect.makeXYWH(8, 8, 24, 24, 0),
                    8, 8, 24, 24, 2);
            panel(canvas, 40, 8, 16, 16, 0xFFFFFFFF);
        });
        assertSame(reused, snapshot("mainBackdrop"), "Skipped capture retains its downsampled GPU image");
        assertPixel(framebuffer, 16, 16, 0xFF0000FF, "reused blur contains the captured frame");
        assertPixel(framebuffer, 48, 16, 0xFFFFFFFF, "native HUD draws even without a new capture");
        assertPixel(framebuffer, 48, 48, 0xFF00FF00, "new external background remains outside HUD");
        assertNoGlError("external content/blur");
    }

    @Test
    void mixedOpacityHudPanelsSurviveSuccessivePassesBlitAndWindowOverlay() throws Exception {
        for (int frame = 0; frame < 4; frame++) {
            int shift = frame % 2;
            int inventoryX = 3 + shift;
            int scaffoldY = 4 + shift;
            int targetY = 32 + shift;
            externalClear(framebuffer, 0xFF101010);
            seedExternalHudState();
            if (frame % 2 == 0) expireCapture("mainBackdrop");
            else postponeCapture("mainBackdrop");

            // Use the production bridge/primitives with the three native-HUD
            // compositing patterns, not only flat opaque diagnostic rectangles.
            drawSafely(framebuffer, true, "FULL", canvas ->
                    mixedPanel(canvas, inventoryX, 4, 28, 20, 208, 0xFFFF3030, "INV", true));
            Image captured = snapshot("mainBackdrop");
            assertNotNull(captured);
            byte[] inventoryFrame = readFrame(framebuffer);
            assertDominantColor(inventoryFrame, inventoryX + 8, 19, 0, "inventory opacity layer");

            postponeCapture("mainBackdrop");
            drawSafely(framebuffer, true, "FULL", canvas ->
                    mixedPanel(canvas, 40, scaffoldY, 20, 20, 160, 0xFF30FF30, "64", true));
            assertSame(captured, snapshot("mainBackdrop"));
            byte[] scaffoldFrame = readFrame(framebuffer);
            assertRegionEquals(inventoryFrame, scaffoldFrame, inventoryX, 4, 28, 20,
                    "scaffold pass must not erase the inventory panel");
            assertDominantColor(scaffoldFrame, 47, scaffoldY + 15, 1, "scaffold opacity layer");

            drawSafely(framebuffer, false, "FULL", canvas ->
                    mixedPanel(canvas, 4, targetY, 34, 26, 128, 0xFF3030FF, "TARGET", false));
            byte[] targetFrame = readFrame(framebuffer);
            assertRegionEquals(scaffoldFrame, targetFrame, inventoryX, 4, 28, 20,
                    "target pass must not erase the inventory panel");
            assertRegionEquals(scaffoldFrame, targetFrame, 40, scaffoldY, 20, 20,
                    "target pass must not erase the scaffold panel");
            assertDominantColor(targetFrame, 20, targetY + 17, 2, "target opacity layer");
            assertPixel(framebuffer, 1, 1, 0xFF101010, "rounded HUD clips preserve the game");

            // Native item rendering follows all background painters. These GL
            // markers exercise that external write boundary without game assets.
            externalMarker(framebuffer, inventoryX + 3, 15, 4, 4, 0xFFFFFF00);
            externalMarker(framebuffer, 43, scaffoldY + 13, 4, 4, 0xFF00FFFF);
            byte[] withItems = readFrame(framebuffer);
            blitMainToWindow();
            assertArrayEquals(withItems, readFrame(0), "presentation must include every native panel and item");

            expireCapture("windowBackdrop");
            drawSafely(0, true, "LIGHT", canvas ->
                    mixedPanel(canvas, 44, 43, 16, 16, 192, 0xFF30FF30, "W", true));
            byte[] presented = readFrame(0);
            assertDominantColor(presented, 52, 54, 1, "window overlay stays visible");
            assertRegionEquals(withItems, presented, inventoryX, 4, 28, 20,
                    "window overlay must not erase the presented inventory");
            assertRegionEquals(withItems, presented, 40, scaffoldY, 20, 20,
                    "window overlay must not erase the presented scaffold");
            assertRegionEquals(withItems, presented, 4, targetY, 34, 26,
                    "window overlay must not erase the presented target");
            assertArrayEquals(withItems, readFrame(framebuffer), "window painting must not modify the main target");
            assertPixel(0, inventoryX + 4, 16, 0xFFFFFF00, "presented inventory item");
            assertPixel(0, 44, scaffoldY + 14, 0xFF00FFFF, "presented scaffold item");
            assertNoGlError("mixed native/window frame " + frame);
        }
    }

    private static void mixedPanel(Canvas canvas, int x, int y, int width, int height,
                                   int opacity, int color, String label, boolean blur) {
        int save = canvas.saveLayerAlpha(null, opacity);
        try {
            RRect shape = RRect.makeXYWH(x, y, width, height, 4);
            canvas.clipRRect(shape, true);
            if (blur) SkijaRenderer.drawGaussianBackdrop(canvas, shape, x, y, width, height, 3);
            SkijaUi.rounded(canvas, x, y, width, height, 4, color);
            SkijaUi.outline(canvas, x + 1, y + 1, width - 2, height - 2, 3, 1, 0xFFFFFFFF);
            SkijaUi.boldText(canvas, label, x + 3, y + 2, 8, 0xFFFFFFFF, 5);
            SkijaUi.fill(canvas, x + 3, y + height - 4, width - 6, 1, 0xFFFFFFFF);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private void seedExternalHudState() {
        GL30.glBindVertexArray(vertexArray);
        GL11.glViewport(3, 5, 53, 51);
        GL11.glScissor(2, 7, 41, 39);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDepthFunc(GL11.GL_GREATER);
        GL11.glDepthMask(false);
        GL11.glCullFace(GL11.GL_FRONT);
        GL11.glFrontFace(GL11.GL_CW);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_REVERSE_SUBTRACT, GL14.GL_FUNC_ADD);
        GL11.glColorMask(true, false, true, false);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 8);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 8);
    }

    private static Map<String, Object> externalState(boolean full) {
        Map<String, Object> state = new LinkedHashMap<>();
        int[] parameters = {GL20.GL_CURRENT_PROGRAM, GL30.GL_VERTEX_ARRAY_BINDING, GL15.GL_ARRAY_BUFFER_BINDING,
                GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING, GL30.GL_DRAW_FRAMEBUFFER_BINDING, GL30.GL_READ_FRAMEBUFFER_BINDING,
                GL13.GL_ACTIVE_TEXTURE, GL11.GL_TEXTURE_BINDING_2D, GL33.GL_SAMPLER_BINDING,
                GL14.GL_BLEND_SRC_RGB, GL14.GL_BLEND_DST_RGB, GL14.GL_BLEND_SRC_ALPHA, GL14.GL_BLEND_DST_ALPHA,
                GL20.GL_BLEND_EQUATION_RGB, GL20.GL_BLEND_EQUATION_ALPHA, GL11.GL_DEPTH_FUNC,
                GL11.GL_CULL_FACE_MODE, GL11.GL_FRONT_FACE};
        for (int parameter : parameters) state.put("int:" + parameter, GL11.glGetInteger(parameter));
        for (int capability : new int[]{GL11.GL_BLEND, GL11.GL_DEPTH_TEST, GL11.GL_SCISSOR_TEST,
                GL11.GL_STENCIL_TEST, GL11.GL_CULL_FACE, GL30.GL_FRAMEBUFFER_SRGB}) {
            state.put("enabled:" + capability, GL11.glIsEnabled(capability));
        }
        state.put("depthMask", GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        int[] viewport = new int[4];
        int[] scissor = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
        state.put("viewport", Arrays.toString(viewport));
        state.put("scissor", Arrays.toString(scissor));
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer mask = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            state.put("colorMask", List.of(mask.get(0), mask.get(1), mask.get(2), mask.get(3)));
        }
        if (full) {
            for (int parameter : new int[]{GL21.GL_PIXEL_PACK_BUFFER_BINDING, GL21.GL_PIXEL_UNPACK_BUFFER_BINDING,
                    GL31.GL_UNIFORM_BUFFER_BINDING, GL11.GL_PACK_ALIGNMENT, GL11.GL_UNPACK_ALIGNMENT,
                    GL11.GL_PACK_ROW_LENGTH, GL11.GL_UNPACK_ROW_LENGTH}) {
                state.put("full:" + parameter, GL11.glGetInteger(parameter));
            }
        }
        return state;
    }

    private void blitMainToWindow() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
        GL11.glColorMask(true, true, true, true);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
        GL11.glDrawBuffer(GL11.GL_BACK);
        GL30.glBlitFramebuffer(0, 0, SIZE, SIZE, 0, 0, SIZE, SIZE, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    private static byte[] readFrame(int framebuffer) {
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
        GL11.glReadBuffer(framebuffer == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pixels = stack.malloc(SIZE * SIZE * 4);
            GL11.glReadPixels(0, 0, SIZE, SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            byte[] result = new byte[SIZE * SIZE * 4];
            pixels.get(result);
            return result;
        }
    }

    private static void assertDominantColor(byte[] pixels, int x, int y, int channel, String description) {
        int offset = ((SIZE - 1 - y) * SIZE + x) * 4;
        int dominant = pixels[offset + channel] & 255;
        int other = Math.max(pixels[offset + (channel + 1) % 3] & 255,
                pixels[offset + (channel + 2) % 3] & 255);
        assertTrue(dominant > other + 50, description + " must remain visible: dominant=" + dominant + ", other=" + other);
        assertEquals(255, pixels[offset + 3] & 255, description + " composites over the opaque game");
    }

    private static void assertRegionEquals(byte[] expected, byte[] actual, int x, int y,
                                           int width, int height, String description) {
        for (int row = y; row < y + height; row++) {
            int start = ((SIZE - 1 - row) * SIZE + x) * 4;
            assertArrayEquals(Arrays.copyOfRange(expected, start, start + width * 4),
                    Arrays.copyOfRange(actual, start, start + width * 4), description + ", row " + row);
        }
    }

    private static void drawSafely(int framebuffer, boolean capture, String mode, Consumer<Canvas> painter) throws Exception {
        Class<?> operationType = Class.forName(SkijaRenderer.class.getName() + "$PaintOperation");
        Class<?> modeType = Class.forName(SkijaRenderer.class.getName() + "$GlStateMode");
        Object operation = Proxy.newProxyInstance(operationType.getClassLoader(), new Class<?>[]{operationType},
                (proxy, method, arguments) -> {
                    draw(framebuffer, capture, painter);
                    return true;
                });
        Object stateMode = field(modeType, mode).get(null);
        Map<String, Object> previous = externalState("FULL".equals(mode));
        Object rendered = method("paintSafely", String.class, operationType, modeType)
                .invoke(null, "GPU regression " + mode, operation, stateMode);
        assertEquals(Boolean.TRUE, rendered, "production bridge must not skip the painter or swallow its error");
        assertEquals(previous, externalState("FULL".equals(mode)), mode + " GL state must be restored after every HUD pass");
        assertNoGlError(mode + " state restoration");
    }

    private static void panel(Canvas canvas, float x, float y, float width, float height, int color) {
        try (Paint paint = new Paint().setColor(color)) {
            canvas.drawRect(Rect.makeXYWH(x, y, width, height), paint);
        }
    }

    private static void draw(int framebuffer, boolean capture, Consumer<Canvas> painter) throws Exception {
        try {
            method("drawTarget", int.class, int.class, int.class, float.class, float.class,
                    Consumer.class, boolean.class).invoke(null, SIZE, SIZE, framebuffer,
                    (float) SIZE, (float) SIZE, painter, capture);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof Exception exception) throw exception;
            if (error.getCause() instanceof Error cause) throw cause;
            throw error;
        }
    }

    private static void externalClear(int framebuffer, int color) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL11.glDrawBuffer(framebuffer == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
        GL11.glColorMask(true, true, true, true);
        clearColor(color);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    private static void externalMarker(int framebuffer, int x, int y, int width, int height, int color) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x, SIZE - y - height, width, height);
        GL11.glColorMask(true, true, true, true);
        clearColor(color);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private static void clearColor(int color) {
        GL11.glClearColor(((color >>> 16) & 255) / 255.0F,
                ((color >>> 8) & 255) / 255.0F, (color & 255) / 255.0F, 1);
    }

    private static void assertPixel(int framebuffer, int x, int y, int expected, String description) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
        GL11.glReadBuffer(framebuffer == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pixel = stack.malloc(4);
            GL11.glReadPixels(x, SIZE - 1 - y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            int actual = (pixel.get(3) & 255) << 24 | (pixel.get(0) & 255) << 16
                    | (pixel.get(1) & 255) << 8 | pixel.get(2) & 255;
            assertEquals(expected, actual, description + ": expected="
                    + Integer.toHexString(expected) + ", actual=" + Integer.toHexString(actual));
        }
    }

    private static void assertNoGlError(String description) {
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), description);
    }

    private static Image snapshot(String cacheName) throws Exception {
        Object cache = field(SkijaRenderer.class, cacheName).get(null);
        return (Image) field(cache.getClass(), "snapshot").get(cache);
    }

    private static BackdropCapturePolicy policy(String cacheName) throws Exception {
        Object cache = field(SkijaRenderer.class, cacheName).get(null);
        return (BackdropCapturePolicy) field(cache.getClass(), "policy").get(cache);
    }

    private static void expireCapture(String cacheName) throws Exception {
        policy(cacheName).captured(System.nanoTime() - CAPTURE_INTERVAL * 2);
    }

    private static void postponeCapture(String cacheName) throws Exception {
        policy(cacheName).captured(System.nanoTime() + 60_000_000_000L);
    }

    private static Method method(String name, Class<?>... parameters) throws Exception {
        Method method = SkijaRenderer.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        return type.cast(unsafe.allocateInstance(type));
    }
}
