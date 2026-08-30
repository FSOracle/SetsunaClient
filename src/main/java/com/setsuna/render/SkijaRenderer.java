package com.setsuna.render;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.setsuna.Setsuna;
import com.setsuna.event.EventBus;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.ui.SkijaScreen;
import com.setsuna.ui.screen.LoadingScreenDrawer;
import com.setsuna.ui.screen.VanillaScreenTheme;
import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.BackendTexture;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.FramebufferFormat;
import io.github.humbleui.skija.GLTextureInfo;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceOrigin;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/** Draws Skija UI into the presented window framebuffer before buffer swap. */
public final class SkijaRenderer {

    private static DirectContext context;
    private static BackendRenderTarget renderTarget;
    private static Surface surface;

    private static int targetWidth = -1;
    private static int targetHeight = -1;
    private static int targetFramebuffer = -1;
    private static int targetSamples = -1;
    private static int targetStencilBits = -1;
    private static int themedFramebuffer = -1;
    private static int themedColorTexture = -1;
    private static Image frameBackdropSnapshot;
    private static boolean backdropRequested;

    private static boolean failed;
    private static boolean backdropBlurFailed;

    private SkijaRenderer() {
    }

    /** Draws any screen implemented on the shared Skija surface. */
    public static void render(SkijaScreen screen) {
        paint(screen::renderSkija);
    }

    /**
     * Fires {@link Render2DEvent} so enabled modules can draw a HUD/world overlay
     * with the shared Skija canvas. Called every frame no GUI is open.
     */
    public static void renderOverlay() {
        Window window = Minecraft.getInstance().getWindow();
        float scaledWidth = window.getGuiScaledWidth();
        float scaledHeight = window.getGuiScaledHeight();
        double guiScale = window.getGuiScale();
        boolean captureBackdrop = backdropRequested;
        backdropRequested = false;
        paint(canvas -> EventBus.INSTANCE.post(
                new Render2DEvent(canvas, scaledWidth, scaledHeight, guiScale)), captureBackdrop);
    }

    /** Draws immediately into Minecraft's main render target in GUI-scaled coordinates. */
    public static void renderMainTarget(java.util.function.Consumer<Canvas> painter) {
        if (failed) return;

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.getMainRenderTarget();
        if (!(target.getColorTexture() instanceof GlTexture colorTexture)) return;
        int framebuffer = ensureThemedFramebuffer(colorTexture.glId());
        if (framebuffer <= 0) return;

        Window window = minecraft.getWindow();
        paint(target.width, target.height, framebuffer,
                window.getGuiScaledWidth(), window.getGuiScaledHeight(), painter);
    }

    /**
     * Draws the Spotlight loading overlay on top of the presented frame while the
     * client reloads resources. The vanilla {@code LoadingOverlay} keeps running so
     * its fade/completion lifecycle is untouched; we only replace its visuals.
     */
    public static void renderLoading(float progress) {
        Window window = Minecraft.getInstance().getWindow();
        float scaledWidth = window.getGuiScaledWidth();
        float scaledHeight = window.getGuiScaledHeight();
        paint(canvas -> LoadingScreenDrawer.draw(canvas, scaledWidth, scaledHeight, progress));
    }

    /** Draws the shared Skija screen backdrop behind a themed vanilla screen. */
    public static void renderVanillaScreenTheme() {
        if (failed) return;

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.getMainRenderTarget();
        if (!(target.getColorTexture() instanceof GlTexture colorTexture)) return;
        int framebuffer = ensureThemedFramebuffer(colorTexture.glId());
        if (framebuffer <= 0) return;

        Window window = minecraft.getWindow();
        float mouseX = (float) minecraft.mouseHandler.getScaledXPos(window);
        float mouseY = (float) minecraft.mouseHandler.getScaledYPos(window);
        paint(target.width, target.height, framebuffer,
                window.getGuiScaledWidth(), window.getGuiScaledHeight(),
                canvas -> VanillaScreenTheme.drawSkijaBackdrop(canvas,
                        window.getGuiScaledWidth(), window.getGuiScaledHeight(), mouseX, mouseY));
    }

    /**
     * Shared draw path: preserves GL state, (re)creates the Skija surface bound to
     * the presented framebuffer, scales the canvas into GUI-scaled space, and runs
     * {@code painter}. Any failure disables the renderer for the session.
     */
    private static void paint(java.util.function.Consumer<Canvas> painter) {
        paint(painter, false);
    }

    private static void paint(java.util.function.Consumer<Canvas> painter, boolean captureBackdrop) {
        if (failed) {
            return;
        }

        GlState previous = GlState.capture();
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Window window = minecraft.getWindow();
            int width = window.getWidth();
            int height = window.getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }

            paint(width, height, 0, window.getGuiScaledWidth(),
                    window.getGuiScaledHeight(), painter, captureBackdrop);
        } catch (Throwable throwable) {
            failed = true;
            Setsuna.LOGGER.error("Skija renderer failed; disabling it for this session", throwable);
        } finally {
            previous.restore();
        }
    }

    private static void paint(int width, int height, int framebuffer,
                              float guiWidth, float guiHeight,
                              java.util.function.Consumer<Canvas> painter) {
        paint(width, height, framebuffer, guiWidth, guiHeight, painter, false);
    }

    private static void paint(int width, int height, int framebuffer,
                              float guiWidth, float guiHeight,
                              java.util.function.Consumer<Canvas> painter,
                              boolean captureBackdrop) {
        GlState previous = GlState.capture();
        Image backdrop = null;
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            canonicalizePixelStore();
            ensureSurface(width, height, framebuffer, 0, 0);

            context.resetGLAll();
            if (captureBackdrop) {
                backdrop = surface.makeImageSnapshot();
                frameBackdropSnapshot = backdrop;
            }
            Canvas canvas = surface.getCanvas();
            int save = canvas.save();
            try {
                canvas.scale(width / guiWidth, height / guiHeight);
                painter.accept(canvas);
            } finally {
                canvas.restoreToCount(save);
            }
            context.flushAndSubmit(surface);
            SkijaUi.releaseRetiredFontResources();
        } catch (Throwable throwable) {
            failed = true;
            Setsuna.LOGGER.error("Skija renderer failed; disabling it for this session", throwable);
        } finally {
            frameBackdropSnapshot = null;
            if (backdrop != null) {
                backdrop.close();
            }
            previous.restore();
        }
    }

    private static int ensureThemedFramebuffer(int colorTexture) {
        if (themedFramebuffer != -1 && themedColorTexture == colorTexture) {
            return themedFramebuffer;
        }
        if (themedFramebuffer != -1) {
            if (targetFramebuffer == themedFramebuffer) closeSurface();
            GL30.glDeleteFramebuffers(themedFramebuffer);
        }
        themedFramebuffer = GL30.glGenFramebuffers();
        themedColorTexture = colorTexture;
        int previous = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, themedFramebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, colorTexture, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            GL30.glDeleteFramebuffers(themedFramebuffer);
            themedFramebuffer = -1;
            themedColorTexture = -1;
        }
        return themedFramebuffer;
    }

    public static boolean hasFailed() {
        return failed;
    }

    /**
     * Blurs the immutable game-frame snapshot captured before any HUD is drawn.
     * Reusing one source prevents recursive sampling of earlier HUDs and glyphs.
     */
    public static void drawBlurredBackdrop(Canvas canvas, RRect clip,
                                           float x, float y, float width, float height,
                                           float strength) {
        drawBlurredBackdrop(canvas, clip == null ? null : target -> target.clipRRect(clip, true),
                x, y, width, height, strength);
    }

    /** Path-clipped variant used by fused HUD unions. */
    public static void drawBlurredBackdrop(Canvas canvas, Path clip,
                                           float x, float y, float width, float height,
                                           float strength) {
        drawBlurredBackdrop(canvas, clip == null ? null : target -> target.clipPath(clip, true),
                x, y, width, height, strength);
    }

    private static void drawBlurredBackdrop(Canvas canvas,
                                            java.util.function.Consumer<Canvas> clipper,
                                            float x, float y, float width, float height,
                                            float strength) {
        if (backdropBlurFailed || canvas == null || clipper == null
                || strength <= 0.01F
                || width <= 1.0F || height <= 1.0F || targetWidth <= 0 || targetHeight <= 0) {
            return;
        }
        backdropRequested = true;
        if (frameBackdropSnapshot == null) return;

        Window window = Minecraft.getInstance().getWindow();
        float guiWidth = window.getGuiScaledWidth();
        float guiHeight = window.getGuiScaledHeight();
        if (guiWidth <= 0.0F || guiHeight <= 0.0F) return;

        float scaleX = targetWidth / guiWidth;
        float scaleY = targetHeight / guiHeight;
        float padding = Math.max(2.0F, strength * 2.5F);
        int left = Math.max(0, (int) Math.floor((x - padding) * scaleX));
        int top = Math.max(0, (int) Math.floor((y - padding) * scaleY));
        int right = Math.min(targetWidth, (int) Math.ceil((x + width + padding) * scaleX));
        int bottom = Math.min(targetHeight, (int) Math.ceil((y + height + padding) * scaleY));
        if (right <= left || bottom <= top) return;

        try (ImageFilter filter = ImageFilter.makeBlur(strength, strength, FilterTileMode.CLAMP);
             Paint paint = new Paint().setAntiAlias(true).setImageFilter(filter)) {
            Rect source = Rect.makeLTRB(left, top, right, bottom);
            Rect destination = Rect.makeLTRB(
                    left / scaleX, top / scaleY, right / scaleX, bottom / scaleY);
            int save = canvas.save();
            try {
                clipper.accept(canvas);
                canvas.drawImageRect(frameBackdropSnapshot, source, destination,
                        SamplingMode.LINEAR, paint, true);
            } finally {
                canvas.restoreToCount(save);
            }
        } catch (Throwable throwable) {
            backdropBlurFailed = true;
            Setsuna.LOGGER.warn("Framebuffer snapshot blur is unavailable; disabling HUD blur for this session",
                    throwable);
        }
    }

    /** Borrows an uploaded Minecraft GL texture for drawing on the active Skija frame. */
    public static BorrowedImage borrowTexture(Identifier identifier) {
        if (context == null || identifier == null) return null;
        BackendTexture backend = null;
        try {
            AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(identifier);
            if (!(texture.getTexture() instanceof GlTexture glTexture) || glTexture.isClosed()) return null;
            int width = glTexture.getWidth(0);
            int height = glTexture.getHeight(0);
            if (width <= 0 || height <= 0) return null;

            backend = BackendTexture.makeGL(width, height, false,
                    new GLTextureInfo(GL11.GL_TEXTURE_2D, glTexture.glId(), GL11.GL_RGBA8));
            Image image = Image.borrowTextureFrom(context, backend, SurfaceOrigin.TOP_LEFT,
                    ColorType.RGBA_8888, ColorAlphaType.UNPREMUL, ColorSpace.getSRGB(), () -> { });
            if (image == null) {
                backend.close();
                return null;
            }
            return new BorrowedImage(image, backend);
        } catch (Throwable throwable) {
            if (backend != null) {
                try {
                    backend.close();
                } catch (Throwable ignored) {
                }
            }
            return null;
        }
    }

    public static void close() {
        if (context == null) {
            return;
        }
        backdropRequested = false;
        frameBackdropSnapshot = null;
        closeSurface();
        if (themedFramebuffer != -1) {
            GL30.glDeleteFramebuffers(themedFramebuffer);
            themedFramebuffer = -1;
            themedColorTexture = -1;
        }
        SkijaUi.close();
        context.close();
        context = null;
    }

    private static void ensureSurface(int width, int height, int framebuffer, int samples, int stencilBits) {
        if (context == null) {
            context = DirectContext.makeGL();
        }
        if (surface != null
                && targetWidth == width
                && targetHeight == height
                && targetFramebuffer == framebuffer
                && targetSamples == samples
                && targetStencilBits == stencilBits) {
            return;
        }

        closeSurface();
        renderTarget = BackendRenderTarget.makeGL(
                width,
                height,
                samples,
                stencilBits,
                framebuffer,
                FramebufferFormat.GR_GL_RGBA8);
        surface = Surface.wrapBackendRenderTarget(
                context,
                renderTarget,
                SurfaceOrigin.BOTTOM_LEFT,
                ColorType.RGBA_8888,
                ColorSpace.getSRGB());
        targetWidth = width;
        targetHeight = height;
        targetFramebuffer = framebuffer;
        targetSamples = samples;
        targetStencilBits = stencilBits;
    }

    private static void closeSurface() {
        if (surface != null) {
            surface.close();
            surface = null;
        }
        if (renderTarget != null) {
            renderTarget.close();
            renderTarget = null;
        }
        targetWidth = -1;
        targetHeight = -1;
        targetFramebuffer = -1;
        targetSamples = -1;
        targetStencilBits = -1;
    }

    public static final class BorrowedImage implements AutoCloseable {
        private final Image image;
        private final BackendTexture backend;

        private BorrowedImage(Image image, BackendTexture backend) {
            this.image = image;
            this.backend = backend;
        }

        public Image image() {
            return image;
        }

        @Override
        public void close() {
            try {
                image.close();
            } finally {
                backend.close();
            }
        }
    }

    private static void setEnabled(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }

    private static void canonicalizePixelStore() {
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);

        GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES, GL11.GL_FALSE);
        GL11.glPixelStorei(GL11.GL_PACK_LSB_FIRST, GL11.GL_FALSE);
        GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL12.GL_PACK_IMAGE_HEIGHT, 0);
        GL11.glPixelStorei(GL12.GL_PACK_SKIP_IMAGES, 0);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);

        GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, GL11.GL_FALSE);
        GL11.glPixelStorei(GL11.GL_UNPACK_LSB_FIRST, GL11.GL_FALSE);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
    }

    private record GlState(
            int program,
            int vertexArray,
            int arrayBuffer,
            int elementArrayBuffer,
            int pixelPackBuffer,
            int pixelUnpackBuffer,
            int drawFramebuffer,
            int readFramebuffer,
            int activeTexture,
            int[] texture2d,
            int[] samplers,
            int[] viewport,
            int[] scissorBox,
            boolean blend,
            boolean depthTest,
            boolean scissorTest,
            boolean stencilTest,
            boolean cullFace,
            boolean framebufferSrgb,
            int blendSrcRgb,
            int blendDstRgb,
            int blendSrcAlpha,
            int blendDstAlpha,
            int blendEquationRgb,
            int blendEquationAlpha,
            boolean depthMask,
            int depthFunc,
            int cullFaceMode,
            int frontFace,
            int stencilClearValue,
            StencilFaceState frontStencil,
            StencilFaceState backStencil,
            boolean[] colorMask,
            int[] packStore,
            int[] unpackStore) {

        private static GlState capture() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer viewportBuffer = stack.mallocInt(4);
                IntBuffer scissorBuffer = stack.mallocInt(4);
                ByteBuffer colorMaskBuffer = stack.malloc(4);
                GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewportBuffer);
                GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBuffer);
                GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMaskBuffer);

                int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
                int[] textures = new int[12];
                int[] samplers = new int[12];
                for (int unit = 0; unit < textures.length; unit++) {
                    GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                    textures[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                    samplers[unit] = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
                }
                GL13.glActiveTexture(activeTexture);

                return new GlState(
                        GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                        GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
                        GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),
                        GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING),
                        GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING),
                        GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING),
                        GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                        GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                        activeTexture,
                        textures,
                        samplers,
                        new int[]{viewportBuffer.get(0), viewportBuffer.get(1), viewportBuffer.get(2), viewportBuffer.get(3)},
                        new int[]{scissorBuffer.get(0), scissorBuffer.get(1), scissorBuffer.get(2), scissorBuffer.get(3)},
                        GL11.glIsEnabled(GL11.GL_BLEND),
                        GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                        GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                        GL11.glIsEnabled(GL11.GL_STENCIL_TEST),
                        GL11.glIsEnabled(GL11.GL_CULL_FACE),
                        GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB),
                        GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                        GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                        GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                        GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                        GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),
                        GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA),
                        GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                        GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                        GL11.glGetInteger(GL11.GL_CULL_FACE_MODE),
                        GL11.glGetInteger(GL11.GL_FRONT_FACE),
                        GL11.glGetInteger(GL11.GL_STENCIL_CLEAR_VALUE),
                        captureStencilFace(false),
                        captureStencilFace(true),
                        new boolean[]{
                                colorMaskBuffer.get(0) != 0,
                                colorMaskBuffer.get(1) != 0,
                                colorMaskBuffer.get(2) != 0,
                                colorMaskBuffer.get(3) != 0
                        },
                        capturePackStore(),
                        captureUnpackStore());
            }
        }

        private static int[] capturePackStore() {
            return new int[]{
                    GL11.glGetInteger(GL11.GL_PACK_SWAP_BYTES),
                    GL11.glGetInteger(GL11.GL_PACK_LSB_FIRST),
                    GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH),
                    GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS),
                    GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS),
                    GL11.glGetInteger(GL12.GL_PACK_IMAGE_HEIGHT),
                    GL11.glGetInteger(GL12.GL_PACK_SKIP_IMAGES),
                    GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT)
            };
        }

        private static int[] captureUnpackStore() {
            return new int[]{
                    GL11.glGetInteger(GL11.GL_UNPACK_SWAP_BYTES),
                    GL11.glGetInteger(GL11.GL_UNPACK_LSB_FIRST),
                    GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH),
                    GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS),
                    GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS),
                    GL11.glGetInteger(GL12.GL_UNPACK_IMAGE_HEIGHT),
                    GL11.glGetInteger(GL12.GL_UNPACK_SKIP_IMAGES),
                    GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT)
            };
        }

        private static StencilFaceState captureStencilFace(boolean back) {
            return new StencilFaceState(
                    GL11.glGetInteger(back ? GL20.GL_STENCIL_BACK_FUNC : GL11.GL_STENCIL_FUNC),
                    GL11.glGetInteger(back ? GL20.GL_STENCIL_BACK_REF : GL11.GL_STENCIL_REF),
                    GL11.glGetInteger(back
                            ? GL20.GL_STENCIL_BACK_VALUE_MASK : GL11.GL_STENCIL_VALUE_MASK),
                    GL11.glGetInteger(back
                            ? GL20.GL_STENCIL_BACK_WRITEMASK : GL11.GL_STENCIL_WRITEMASK),
                    GL11.glGetInteger(back ? GL20.GL_STENCIL_BACK_FAIL : GL11.GL_STENCIL_FAIL),
                    GL11.glGetInteger(back
                            ? GL20.GL_STENCIL_BACK_PASS_DEPTH_FAIL
                            : GL11.GL_STENCIL_PASS_DEPTH_FAIL),
                    GL11.glGetInteger(back
                            ? GL20.GL_STENCIL_BACK_PASS_DEPTH_PASS
                            : GL11.GL_STENCIL_PASS_DEPTH_PASS));
        }

        private void restore() {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vertexArray);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementArrayBuffer);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pixelPackBuffer);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pixelUnpackBuffer);
            restorePixelStore();
            for (int unit = 0; unit < texture2d.length; unit++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture2d[unit]);
                GL33.glBindSampler(unit, samplers[unit]);
            }
            GL13.glActiveTexture(activeTexture);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            GL14.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
            GL11.glDepthMask(depthMask);
            GL11.glDepthFunc(depthFunc);
            GL11.glCullFace(cullFaceMode);
            GL11.glFrontFace(frontFace);
            GL11.glClearStencil(stencilClearValue);
            restoreStencilFace(GL11.GL_FRONT, frontStencil);
            restoreStencilFace(GL11.GL_BACK, backStencil);
            GL11.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
            setEnabled(GL11.GL_BLEND, blend);
            setEnabled(GL11.GL_DEPTH_TEST, depthTest);
            setEnabled(GL11.GL_SCISSOR_TEST, scissorTest);
            setEnabled(GL11.GL_STENCIL_TEST, stencilTest);
            setEnabled(GL11.GL_CULL_FACE, cullFace);
            setEnabled(GL30.GL_FRAMEBUFFER_SRGB, framebufferSrgb);
        }

        private static void restoreStencilFace(int face, StencilFaceState state) {
            GL20.glStencilFuncSeparate(face, state.function(), state.reference(), state.valueMask());
            GL20.glStencilMaskSeparate(face, state.writeMask());
            GL20.glStencilOpSeparate(face, state.stencilFail(), state.depthFail(), state.depthPass());
        }

        private void restorePixelStore() {
            GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES, packStore[0]);
            GL11.glPixelStorei(GL11.GL_PACK_LSB_FIRST, packStore[1]);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, packStore[2]);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, packStore[3]);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, packStore[4]);
            GL11.glPixelStorei(GL12.GL_PACK_IMAGE_HEIGHT, packStore[5]);
            GL11.glPixelStorei(GL12.GL_PACK_SKIP_IMAGES, packStore[6]);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, packStore[7]);

            GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, unpackStore[0]);
            GL11.glPixelStorei(GL11.GL_UNPACK_LSB_FIRST, unpackStore[1]);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, unpackStore[2]);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, unpackStore[3]);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, unpackStore[4]);
            GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, unpackStore[5]);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, unpackStore[6]);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, unpackStore[7]);
        }
    }

    private record StencilFaceState(int function, int reference, int valueMask,
                                    int writeMask, int stencilFail,
                                    int depthFail, int depthPass) {
    }
}
