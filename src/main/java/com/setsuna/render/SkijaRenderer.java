package com.setsuna.render;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.textures.GpuTexture;
import com.setsuna.Setsuna;
import com.setsuna.event.EventBus;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.ui.SkijaScreen;
import com.setsuna.ui.screen.LoadingScreenDrawer;
import com.setsuna.ui.screen.VanillaScreenTheme;
import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.BackendTexture;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.ContentChangeMode;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.GLTextureInfo;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceOrigin;
import io.github.humbleui.types.RRect;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;

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
   private static boolean backdropRequested;
   private static final long INITIAL_RETRY_NANOS = 100000000L;
   private static final long MAX_RETRY_NANOS = 2000000000L;
   private static final long FAILURE_LOG_INTERVAL_NANOS = 10000000000L;
   private static final long BLUR_RETRY_NANOS = 5000000000L;
   /**
    * Limit GPU snapshot copies, scaling and submission to 60 Hz per source.
    * The blur itself still draws every frame from the most recent snapshot.
    */
   private static final long BACKDROP_CAPTURE_INTERVAL_NANOS = 16_666_667L;
   private static final int BACKDROP_DOWNSAMPLE = 2;
   private static final BackdropCache windowBackdrop = new BackdropCache();
   private static final BackdropCache mainBackdrop = new BackdropCache();
   private static final long SLOW_FRAME_LIMIT_NANOS = 250000000L;
   private static final long GPU_PROTECTION_NANOS = 1500000000L;
   private static volatile int consecutiveFailures;
   private static volatile long retryAfterNanos;
   private static long lastFailureLogNanos;
   private static long backdropBlurRetryAfterNanos;
   private static long gpuProtectionUntilNanos;
   private static long lastSlowFrameLogNanos;
   private static int slowFrameStreak;
   private static final int MAX_BORROWED_TEXTURES = 48;
   private static final Map<Identifier, CachedTexture> borrowedTextures = new LinkedHashMap(16, 0.75F, true);
   private static boolean closed;

   private SkijaRenderer() {
   }

   public static void render(SkijaScreen screen) {
      Objects.requireNonNull(screen);
      boolean captureBackdrop = backdropRequested;
      backdropRequested = false;
      paint(screen::renderSkija, captureBackdrop, SkijaRenderer.GlStateMode.LIGHT);
   }

   public static void renderOverlay() {
      if (!isGpuProtectionActive()) {
         Window window = Minecraft.getInstance().getWindow();
         float scaledWidth = (float)window.getGuiScaledWidth();
         float scaledHeight = (float)window.getGuiScaledHeight();
         double guiScale = (double)window.getGuiScale();
         boolean captureBackdrop = backdropRequested;
         backdropRequested = false;
         paint((canvas) -> EventBus.INSTANCE.post(new Render2DEvent(canvas, scaledWidth, scaledHeight, guiScale)), captureBackdrop, SkijaRenderer.GlStateMode.LIGHT);
      }
   }

   public static void renderMainTarget(Consumer<Canvas> painter) {
      paintMainTarget("main render target", painter);
   }

   public static void renderMainTargetWithBackdrop(Consumer<Canvas> painter) {
      paintMainTarget("main render target with backdrop", painter, true);
   }

   public static void renderLoading(float progress) {
      Window window = Minecraft.getInstance().getWindow();
      float scaledWidth = (float)window.getGuiScaledWidth();
      float scaledHeight = (float)window.getGuiScaledHeight();
      paint((canvas) -> LoadingScreenDrawer.draw(canvas, scaledWidth, scaledHeight, progress));
   }

   public static void renderVanillaScreenTheme() {
      Minecraft minecraft = Minecraft.getInstance();
      Screen screen = minecraft.screen;
      Window window = minecraft.getWindow();
      float mouseX = (float)minecraft.mouseHandler.getScaledXPos(window);
      float mouseY = (float)minecraft.mouseHandler.getScaledYPos(window);
      if (paintMainTarget("vanilla screen theme", (canvas) -> VanillaScreenTheme.drawSkijaBackdrop(canvas, (float)window.getGuiScaledWidth(), (float)window.getGuiScaledHeight(), mouseX, mouseY))) {
         VanillaScreenTheme.markSkijaFrameRendered(screen);
      }

   }

   private static boolean paint(Consumer<Canvas> painter) {
      return paint(painter, false);
   }

   private static boolean paint(Consumer<Canvas> painter, boolean captureBackdrop) {
      return paint(painter, captureBackdrop, SkijaRenderer.GlStateMode.FULL);
   }

   private static boolean paint(Consumer<Canvas> painter, boolean captureBackdrop, boolean preserveGlState) {
      return paint(painter, captureBackdrop, preserveGlState ? SkijaRenderer.GlStateMode.FULL : SkijaRenderer.GlStateMode.NONE);
   }

   private static boolean paint(Consumer<Canvas> painter, boolean captureBackdrop, GlStateMode stateMode) {
      return paintSafely("window framebuffer", () -> {
         Minecraft minecraft = Minecraft.getInstance();
         Window window = minecraft.getWindow();
         int width = window.getWidth();
         int height = window.getHeight();
         if (width > 0 && height > 0) {
            drawTarget(width, height, 0, (float)window.getGuiScaledWidth(), (float)window.getGuiScaledHeight(), painter, captureBackdrop);
            return true;
         } else {
            return false;
         }
      }, stateMode);
   }

   private static boolean paintMainTarget(String operation, Consumer<Canvas> painter) {
      return paintMainTarget(operation, painter, false);
   }

   private static boolean paintMainTarget(String operation, Consumer<Canvas> painter, boolean captureBackdrop) {
      return paintSafely(operation, () -> {
         Minecraft minecraft = Minecraft.getInstance();
         RenderTarget target = minecraft.getMainRenderTarget();
         GpuTexture patt0$temp = target.getColorTexture();
         if (patt0$temp instanceof GlTexture colorTexture) {
            int framebuffer = ensureThemedFramebuffer(colorTexture.glId());
            if (framebuffer <= 0) {
               return false;
            } else {
               Window window = minecraft.getWindow();
               drawTarget(target.width, target.height, framebuffer, (float)window.getGuiScaledWidth(), (float)window.getGuiScaledHeight(), painter, captureBackdrop);
               return true;
            }
         } else {
            return false;
         }
      }, true);
   }

   private static boolean paintSafely(String operation, PaintOperation drawing) {
      return paintSafely(operation, drawing, true);
   }

   private static boolean paintSafely(String operation, PaintOperation drawing, boolean preserveGlState) {
      return paintSafely(operation, drawing, preserveGlState ? SkijaRenderer.GlStateMode.FULL : SkijaRenderer.GlStateMode.NONE);
   }

   private static boolean paintSafely(String operation, PaintOperation drawing, GlStateMode stateMode) {
      if (closed) {
         return false;
      } else {
         long now = System.nanoTime();
         if (now >= retryAfterNanos && now >= gpuProtectionUntilNanos) {
            GlState previous = null;
            LightGlState lightPrevious = null;
            Throwable failure = null;
            boolean rendered = false;

            try {
               if (stateMode == SkijaRenderer.GlStateMode.FULL) {
                  previous = SkijaRenderer.GlState.capture();
               } else if (stateMode == SkijaRenderer.GlStateMode.LIGHT) {
                  lightPrevious = SkijaRenderer.LightGlState.capture();
               }

               rendered = drawing.draw();
            } catch (Throwable throwable) {
               failure = throwable;
            } finally {
               if (previous != null) {
                  try {
                     previous.restore();
                  } catch (Throwable restoreFailure) {
                     if (failure == null) {
                        failure = restoreFailure;
                     } else {
                        failure.addSuppressed(restoreFailure);
                     }
                  }
               } else if (lightPrevious != null) {
                  try {
                     lightPrevious.restore();
                  } catch (Throwable restoreFailure) {
                     if (failure == null) {
                        failure = restoreFailure;
                     } else {
                        failure.addSuppressed(restoreFailure);
                     }
                  }
               }

            }

            if (failure != null) {
               handleFailure(operation, failure);
               return false;
            } else {
               recordFrameCost(operation, System.nanoTime() - now);
               if (rendered) {
                  markSuccessfulFrame();
               }

               return rendered;
            }
         } else {
            return false;
         }
      }
   }

   private static void drawTarget(int width, int height, int framebuffer, float guiWidth, float guiHeight, Consumer<Canvas> painter, boolean captureBackdrop) {
      GL30.glBindFramebuffer(36160, framebuffer);
      canonicalizePixelStore();
      ensureSurface(width, height, framebuffer, 0, 0);
      context.resetGLAll();
      // Minecraft owns this framebuffer and can change it between Skija passes.
      // Keep its pixels, but invalidate Skia's cached view of their contents.
      surface.notifyContentWillChange(ContentChangeMode.RETAIN);
      Minecraft minecraft = Minecraft.getInstance();
      // Invalidate both sources even if only one is used after a scene change.
      windowBackdrop.updateScene(minecraft.screen, minecraft.level);
      mainBackdrop.updateScene(minecraft.screen, minecraft.level);
      BackdropCache backdrop = framebuffer == 0 ? windowBackdrop : mainBackdrop;
      if (backdrop.policy.updateDimensions(width, height)) backdrop.closeSnapshot();
      if (!isGpuProtectionActive()
            && backdrop.policy.shouldCapture(captureBackdrop, backdrop.snapshot != null, System.nanoTime())) {
         context.flushAndSubmit(surface);
         int capturedWidth = Math.max(1, width / BACKDROP_DOWNSAMPLE);
         int capturedHeight = Math.max(1, height / BACKDROP_DOWNSAMPLE);

         Image capturedBackdrop;
         try (Image fullFrame = surface.makeImageSnapshot()) {
            capturedBackdrop = fullFrame.makeScaled(ImageInfo.makeN32Premul(capturedWidth, capturedHeight), SamplingMode.LINEAR);
         }

         if (capturedBackdrop != null) {
            Image previousBackdrop = backdrop.snapshot;
            backdrop.snapshot = capturedBackdrop;
            backdrop.policy.captured(System.nanoTime());
            if (previousBackdrop != null) previousBackdrop.close();
         }
      }

      Canvas canvas = surface.getCanvas();
      int save = canvas.save();

      try {
         canvas.scale((float)width / guiWidth, (float)height / guiHeight);
         painter.accept(canvas);
      } finally {
         canvas.restoreToCount(save);
      }

      context.flushAndSubmit(surface);
      SkijaUi.releaseRetiredFontResources();
   }

   private static void handleFailure(String operation, Throwable throwable) {
      long now = System.nanoTime();
      int failures = ++consecutiveFailures;
      long multiplier = 1L << Math.min(4, failures - 1);
      long delay = Math.min(2000000000L, 100000000L * multiplier);
      retryAfterNanos = now + delay;
      VanillaScreenTheme.invalidateSkijaFrame();
      resetGpuBridge();
      VanillaFontAtlasGuard.requestRefresh();
      if (failures <= 3 || now - lastFailureLogNanos >= 10000000000L) {
         lastFailureLogNanos = now;
         Setsuna.LOGGER.error("Skija {} failed; rebuilding the renderer and retrying in {} ms (failure {})", new Object[]{operation, delay / 1000000L, failures, throwable});
      }

   }

   private static void markSuccessfulFrame() {
      int recoveredFailures = consecutiveFailures;
      consecutiveFailures = 0;
      retryAfterNanos = 0L;
      if (recoveredFailures > 0) {
         Setsuna.LOGGER.info("Skija renderer recovered after {} failed frame(s)", recoveredFailures);
      }

   }

   private static int ensureThemedFramebuffer(int colorTexture) {
      if (themedFramebuffer != -1 && themedColorTexture == colorTexture) {
         return themedFramebuffer;
      } else {
         mainBackdrop.close();
         if (themedFramebuffer != -1) {
            if (targetFramebuffer == themedFramebuffer) {
               closeSurface();
            }

            GL30.glDeleteFramebuffers(themedFramebuffer);
         }

         themedFramebuffer = GL30.glGenFramebuffers();
         themedColorTexture = colorTexture;
         int previous = GL11.glGetInteger(36006);
         GL30.glBindFramebuffer(36160, themedFramebuffer);
         GL30.glFramebufferTexture2D(36160, 36064, 3553, colorTexture, 0);
         int status = GL30.glCheckFramebufferStatus(36160);
         GL30.glBindFramebuffer(36160, previous);
         if (status != 36053) {
            GL30.glDeleteFramebuffers(themedFramebuffer);
            themedFramebuffer = -1;
            themedColorTexture = -1;
         }

         return themedFramebuffer;
      }
   }

   public static boolean hasFailed() {
      return consecutiveFailures > 0;
   }

   public static void drawGaussianBackdrop(Canvas canvas, RRect clip, float x, float y, float width, float height, float strength) {
      drawGaussianBackdrop(canvas, clip == null ? null : (target) -> target.clipRRect(clip, true), x, y, width, height, strength);
   }

   public static void drawGaussianBackdrop(Canvas canvas, Path clip, float x, float y, float width, float height, float strength) {
      drawGaussianBackdrop(canvas, clip == null ? null : (target) -> target.clipPath(clip, true), x, y, width, height, strength);
   }

   public static void drawBlurredBackdrop(Canvas canvas, RRect clip, float x, float y, float width, float height, float strength) {
      drawGaussianBackdrop(canvas, clip, x, y, width, height, strength);
   }

   public static void drawBlurredBackdrop(Canvas canvas, Path clip, float x, float y, float width, float height, float strength) {
      drawGaussianBackdrop(canvas, clip, x, y, width, height, strength);
   }

   private static void drawGaussianBackdrop(Canvas canvas, Consumer<Canvas> clipper, float x, float y, float width, float height, float strength) {
      if (System.nanoTime() < backdropBlurRetryAfterNanos || isGpuProtectionActive()
            || canvas == null || clipper == null || strength <= 0.01F || width <= 1.0F || height <= 1.0F
            || targetWidth <= 0 || targetHeight <= 0) return;
      // Main-target painters request their own capture explicitly. Only a
      // window painter should schedule the next presented-frame snapshot.
      if (targetFramebuffer == 0) backdropRequested = true;
      Image backdropSnapshot = (targetFramebuffer == 0 ? windowBackdrop : mainBackdrop).snapshot;
      if (backdropSnapshot == null) return;
      Window window = Minecraft.getInstance().getWindow();
      float guiWidth = window.getGuiScaledWidth();
      float guiHeight = window.getGuiScaledHeight();
      if (guiWidth <= 0.0F || guiHeight <= 0.0F) return;

      try {
         BackdropBlur.draw(canvas, clipper, backdropSnapshot,
               targetWidth, targetHeight, guiWidth, guiHeight, strength);
         backdropBlurRetryAfterNanos = 0L;
      } catch (Throwable throwable) {
         backdropBlurRetryAfterNanos = System.nanoTime() + 5000000000L;
         Setsuna.LOGGER.warn("Framebuffer snapshot blur failed; retrying in 5 seconds", throwable);
      }
   }

   private static boolean isGpuProtectionActive() {
      return System.nanoTime() < gpuProtectionUntilNanos;
   }

   private static void recordFrameCost(String operation, long elapsedNanos) {
      if (elapsedNanos <= 250000000L) {
         slowFrameStreak = 0;
      } else {
         ++slowFrameStreak;
         long now = System.nanoTime();
         if (slowFrameStreak >= 2) {
            gpuProtectionUntilNanos = now + 1500000000L;
            slowFrameStreak = 0;
            if (now - lastSlowFrameLogNanos >= 10000000000L) {
               lastSlowFrameLogNanos = now;
               Setsuna.LOGGER.warn("Skija {} took {} ms; temporarily pausing custom rendering to protect the render thread", operation, elapsedNanos / 1000000L);
            }

         }
      }
   }

   public static BorrowedImage borrowTexture(Identifier identifier) {
      if (context != null && identifier != null) {
         BackendTexture backend = null;

         try {
            AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(identifier);
            GpuTexture var4 = texture.getTexture();
            if (var4 instanceof GlTexture) {
               GlTexture glTexture = (GlTexture)var4;
               if (!glTexture.isClosed()) {
                  int width = glTexture.getWidth(0);
                  int height = glTexture.getHeight(0);
                  if (width > 0 && height > 0) {
                     CachedTexture cached = (CachedTexture)borrowedTextures.get(identifier);
                     if (cached != null && cached.textureId() == glTexture.glId() && cached.width() == width && cached.height() == height) {
                        return new BorrowedImage(cached.image(), cached.backend(), false);
                     }

                     if (cached != null) {
                        cached.close();
                        borrowedTextures.remove(identifier);
                     }

                     backend = BackendTexture.makeGL(width, height, false, new GLTextureInfo(3553, glTexture.glId(), 32856));
                     Image image = Image.borrowTextureFrom(context, backend, SurfaceOrigin.TOP_LEFT, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL, ColorSpace.getSRGB(), () -> {
                     });
                     if (image == null) {
                        backend.close();
                        return null;
                     }

                     CachedTexture replacement = new CachedTexture(image, backend, glTexture.glId(), width, height);
                     borrowedTextures.put(identifier, replacement);
                     trimBorrowedTextures();
                     BackendTexture var12 = null;
                     return new BorrowedImage(image, replacement.backend(), false);
                  }

                  return null;
               }
            }

            return null;
         } catch (Throwable var10) {
            if (backend != null) {
               try {
                  backend.close();
               } catch (Throwable var9) {
               }
            }

            return null;
         }
      } else {
         return null;
      }
   }

   private static void trimBorrowedTextures() {
      while(borrowedTextures.size() > 48) {
         Iterator<Map.Entry<Identifier, CachedTexture>> iterator = borrowedTextures.entrySet().iterator();
         if (!iterator.hasNext()) {
            return;
         }

         CachedTexture removed = (CachedTexture)((Map.Entry)iterator.next()).getValue();
         iterator.remove();
         removed.close();
      }

   }

   private static void closeBorrowedTextures() {
      for(CachedTexture texture : borrowedTextures.values()) {
         try {
            texture.close();
         } catch (Throwable var3) {
         }
      }

      borrowedTextures.clear();
   }

   private static void closeBackdropSnapshots() {
      windowBackdrop.close();
      mainBackdrop.close();
   }

   public static void close() {
      if (!closed) {
         closed = true;
         backdropRequested = false;
         closeBackdropSnapshots();
         closeBorrowedTextures();
         closeSurface();
         if (themedFramebuffer != -1) {
            GL30.glDeleteFramebuffers(themedFramebuffer);
            themedFramebuffer = -1;
            themedColorTexture = -1;
         }

         if (context != null) {
            context.close();
            context = null;
         }

         SkijaUi.close();
         consecutiveFailures = 0;
         retryAfterNanos = 0L;
         backdropBlurRetryAfterNanos = 0L;
         gpuProtectionUntilNanos = 0L;
         lastSlowFrameLogNanos = 0L;
         slowFrameStreak = 0;
      }
   }

   private static void ensureSurface(int width, int height, int framebuffer, int samples, int stencilBits) {
      if (context == null) {
         context = DirectContext.makeGL();
         if (context == null) {
            throw new IllegalStateException("Skija could not create an OpenGL context");
         }
      }

      if (surface == null || targetWidth != width || targetHeight != height || targetFramebuffer != framebuffer || targetSamples != samples || targetStencilBits != stencilBits) {
         closeSurface();
         renderTarget = BackendRenderTarget.makeGL(width, height, samples, stencilBits, framebuffer, 32856);
         surface = Surface.wrapBackendRenderTarget(context, renderTarget, SurfaceOrigin.BOTTOM_LEFT, ColorType.RGBA_8888, ColorSpace.getSRGB());
         if (surface == null) {
            closeSurface();
            throw new IllegalStateException("Skija could not wrap the active framebuffer");
         } else {
            targetWidth = width;
            targetHeight = height;
            targetFramebuffer = framebuffer;
            targetSamples = samples;
            targetStencilBits = stencilBits;
         }
      }
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

   private static void resetGpuBridge() {
      backdropRequested = false;
      closeBackdropSnapshots();

      try {
         closeSurface();
      } catch (Throwable var6) {
         surface = null;
         renderTarget = null;
         targetWidth = -1;
         targetHeight = -1;
         targetFramebuffer = -1;
         targetSamples = -1;
         targetStencilBits = -1;
      }

      int framebuffer = themedFramebuffer;
      themedFramebuffer = -1;
      themedColorTexture = -1;
      if (framebuffer != -1) {
         try {
            GL30.glDeleteFramebuffers(framebuffer);
         } catch (Throwable var5) {
         }
      }

      DirectContext staleContext = context;
      context = null;
      closeBorrowedTextures();
      if (staleContext != null) {
         try {
            staleContext.abandon();
         } catch (Throwable var4) {
         }

         try {
            staleContext.close();
         } catch (Throwable var3) {
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
      GL15.glBindBuffer(35051, 0);
      GL15.glBindBuffer(35052, 0);
      GL11.glPixelStorei(3328, 0);
      GL11.glPixelStorei(3329, 0);
      GL11.glPixelStorei(3330, 0);
      GL11.glPixelStorei(3331, 0);
      GL11.glPixelStorei(3332, 0);
      GL11.glPixelStorei(32876, 0);
      GL11.glPixelStorei(32875, 0);
      GL11.glPixelStorei(3333, 1);
      GL11.glPixelStorei(3312, 0);
      GL11.glPixelStorei(3313, 0);
      GL11.glPixelStorei(3314, 0);
      GL11.glPixelStorei(3315, 0);
      GL11.glPixelStorei(3316, 0);
      GL11.glPixelStorei(32878, 0);
      GL11.glPixelStorei(32877, 0);
      GL11.glPixelStorei(3317, 1);
   }

   /** Main-target and presented-frame snapshots represent different render stages. */
   private static final class BackdropCache {
      private final BackdropCapturePolicy policy = new BackdropCapturePolicy(BACKDROP_CAPTURE_INTERVAL_NANOS);
      private Image snapshot;

      private void updateScene(Object screen, Object level) {
         if (policy.updateScene(screen, level)) closeSnapshot();
      }

      private void closeSnapshot() {
         Image previous = snapshot;
         snapshot = null;
         if (previous != null) {
            try {
               previous.close();
            } catch (Throwable ignored) {
            }
         }
      }

      private void close() {
         closeSnapshot();
         policy.reset();
      }
   }

   public static final class BorrowedImage implements AutoCloseable {
      private final Image image;
      private final BackendTexture backend;
      private final boolean ownsResources;

      private BorrowedImage(Image image, BackendTexture backend, boolean ownsResources) {
         this.image = image;
         this.backend = backend;
         this.ownsResources = ownsResources;
      }

      public Image image() {
         return this.image;
      }

      public void close() {
         if (this.ownsResources) {
            try {
               this.image.close();
            } finally {
               this.backend.close();
            }

         }
      }
   }

   private static record CachedTexture(Image image, BackendTexture backend, int textureId, int width, int height) implements AutoCloseable {
      public void close() {
         try {
            this.image.close();
         } finally {
            this.backend.close();
         }

      }
   }

   private static enum GlStateMode {
      NONE,
      LIGHT,
      FULL;
   }

   private static record LightGlState(int program, int vertexArray, int arrayBuffer, int elementArrayBuffer, int drawFramebuffer, int readFramebuffer, int activeTexture, int texture2d, int sampler, int[] viewport, int[] scissorBox, boolean blend, boolean depthTest, boolean scissorTest, boolean stencilTest, boolean cullFace, boolean framebufferSrgb, int blendSrcRgb, int blendDstRgb, int blendSrcAlpha, int blendDstAlpha, int blendEquationRgb, int blendEquationAlpha, boolean depthMask, int depthFunc, int cullFaceMode, int frontFace, boolean[] colorMask) {
      private static LightGlState capture() {
         MemoryStack stack = MemoryStack.stackPush();

         LightGlState var5;
         try {
            IntBuffer viewportBuffer = stack.mallocInt(4);
            IntBuffer scissorBuffer = stack.mallocInt(4);
            ByteBuffer colorMaskBuffer = stack.malloc(4);
            GL11.glGetIntegerv(2978, viewportBuffer);
            GL11.glGetIntegerv(3088, scissorBuffer);
            GL11.glGetBooleanv(3107, colorMaskBuffer);
            int activeTexture = GL11.glGetInteger(34016);
            var5 = new LightGlState(GL11.glGetInteger(35725), GL11.glGetInteger(34229), GL11.glGetInteger(34964), GL11.glGetInteger(34965), GL11.glGetInteger(36006), GL11.glGetInteger(36010), activeTexture, GL11.glGetInteger(32873), GL11.glGetInteger(35097), new int[]{viewportBuffer.get(0), viewportBuffer.get(1), viewportBuffer.get(2), viewportBuffer.get(3)}, new int[]{scissorBuffer.get(0), scissorBuffer.get(1), scissorBuffer.get(2), scissorBuffer.get(3)}, GL11.glIsEnabled(3042), GL11.glIsEnabled(2929), GL11.glIsEnabled(3089), GL11.glIsEnabled(2960), GL11.glIsEnabled(2884), GL11.glIsEnabled(36281), GL11.glGetInteger(32969), GL11.glGetInteger(32968), GL11.glGetInteger(32971), GL11.glGetInteger(32970), GL11.glGetInteger(32777), GL11.glGetInteger(34877), GL11.glGetBoolean(2930), GL11.glGetInteger(2932), GL11.glGetInteger(2885), GL11.glGetInteger(2886), new boolean[]{colorMaskBuffer.get(0) != 0, colorMaskBuffer.get(1) != 0, colorMaskBuffer.get(2) != 0, colorMaskBuffer.get(3) != 0});
         } catch (Throwable var7) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var6) {
                  var7.addSuppressed(var6);
               }
            }

            throw var7;
         }

         if (stack != null) {
            stack.close();
         }

         return var5;
      }

      private void restore() {
         GL30.glBindFramebuffer(36009, this.drawFramebuffer);
         GL30.glBindFramebuffer(36008, this.readFramebuffer);
         GL20.glUseProgram(this.program);
         GL30.glBindVertexArray(this.vertexArray);
         GL15.glBindBuffer(34962, this.arrayBuffer);
         GL15.glBindBuffer(34963, this.elementArrayBuffer);
         GL13.glActiveTexture(this.activeTexture);
         GL11.glBindTexture(3553, this.texture2d);
         GL33.glBindSampler(this.activeTexture - '蓀', this.sampler);
         GL11.glViewport(this.viewport[0], this.viewport[1], this.viewport[2], this.viewport[3]);
         GL11.glScissor(this.scissorBox[0], this.scissorBox[1], this.scissorBox[2], this.scissorBox[3]);
         GL14.glBlendFuncSeparate(this.blendSrcRgb, this.blendDstRgb, this.blendSrcAlpha, this.blendDstAlpha);
         GL20.glBlendEquationSeparate(this.blendEquationRgb, this.blendEquationAlpha);
         GL11.glDepthMask(this.depthMask);
         GL11.glDepthFunc(this.depthFunc);
         GL11.glCullFace(this.cullFaceMode);
         GL11.glFrontFace(this.frontFace);
         GL11.glColorMask(this.colorMask[0], this.colorMask[1], this.colorMask[2], this.colorMask[3]);
         SkijaRenderer.setEnabled(3042, this.blend);
         SkijaRenderer.setEnabled(2929, this.depthTest);
         SkijaRenderer.setEnabled(3089, this.scissorTest);
         SkijaRenderer.setEnabled(2960, this.stencilTest);
         SkijaRenderer.setEnabled(2884, this.cullFace);
         SkijaRenderer.setEnabled(36281, this.framebufferSrgb);
      }
   }

   private static record GlState(int program, int vertexArray, int arrayBuffer, int elementArrayBuffer, int pixelPackBuffer, int pixelUnpackBuffer, int uniformBuffer, int drawFramebuffer, int readFramebuffer, int activeTexture, int[] texture2d, int[] texture2dArray, int[] samplers, IndexedBufferState[] uniformBindings, int[] viewport, int[] scissorBox, boolean blend, boolean depthTest, boolean scissorTest, boolean stencilTest, boolean cullFace, boolean framebufferSrgb, int blendSrcRgb, int blendDstRgb, int blendSrcAlpha, int blendDstAlpha, int blendEquationRgb, int blendEquationAlpha, boolean depthMask, int depthFunc, int cullFaceMode, int frontFace, int stencilClearValue, StencilFaceState frontStencil, StencilFaceState backStencil, boolean[] colorMask, int[] packStore, int[] unpackStore) {
      private static GlState capture() {
         MemoryStack stack = MemoryStack.stackPush();

         GlState var11;
         try {
            IntBuffer viewportBuffer = stack.mallocInt(4);
            IntBuffer scissorBuffer = stack.mallocInt(4);
            ByteBuffer colorMaskBuffer = stack.malloc(4);
            GL11.glGetIntegerv(2978, viewportBuffer);
            GL11.glGetIntegerv(3088, scissorBuffer);
            GL11.glGetBooleanv(3107, colorMaskBuffer);
            int activeTexture = GL11.glGetInteger(34016);
            int[] textures = new int[12];
            int[] textureArrays = new int[12];
            int[] samplers = new int[12];

            for(int unit = 0; unit < textures.length; ++unit) {
               GL13.glActiveTexture('蓀' + unit);
               textures[unit] = GL11.glGetInteger(32873);
               textureArrays[unit] = GL11.glGetInteger(35869);
               samplers[unit] = GL11.glGetInteger(35097);
            }

            GL13.glActiveTexture(activeTexture);
            var11 = new GlState(GL11.glGetInteger(35725), GL11.glGetInteger(34229), GL11.glGetInteger(34964), GL11.glGetInteger(34965), GL11.glGetInteger(35053), GL11.glGetInteger(35055), GL11.glGetInteger(35368), GL11.glGetInteger(36006), GL11.glGetInteger(36010), activeTexture, textures, textureArrays, samplers, captureIndexedBuffers(35368, 35369, 35370, 35375), new int[]{viewportBuffer.get(0), viewportBuffer.get(1), viewportBuffer.get(2), viewportBuffer.get(3)}, new int[]{scissorBuffer.get(0), scissorBuffer.get(1), scissorBuffer.get(2), scissorBuffer.get(3)}, GL11.glIsEnabled(3042), GL11.glIsEnabled(2929), GL11.glIsEnabled(3089), GL11.glIsEnabled(2960), GL11.glIsEnabled(2884), GL11.glIsEnabled(36281), GL11.glGetInteger(32969), GL11.glGetInteger(32968), GL11.glGetInteger(32971), GL11.glGetInteger(32970), GL11.glGetInteger(32777), GL11.glGetInteger(34877), GL11.glGetBoolean(2930), GL11.glGetInteger(2932), GL11.glGetInteger(2885), GL11.glGetInteger(2886), GL11.glGetInteger(2961), captureStencilFace(false), captureStencilFace(true), new boolean[]{colorMaskBuffer.get(0) != 0, colorMaskBuffer.get(1) != 0, colorMaskBuffer.get(2) != 0, colorMaskBuffer.get(3) != 0}, capturePackStore(), captureUnpackStore());
         } catch (Throwable var10) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var9) {
                  var10.addSuppressed(var9);
               }
            }

            throw var10;
         }

         if (stack != null) {
            stack.close();
         }

         return var11;
      }

      private static int[] capturePackStore() {
         return new int[]{GL11.glGetInteger(3328), GL11.glGetInteger(3329), GL11.glGetInteger(3330), GL11.glGetInteger(3331), GL11.glGetInteger(3332), GL11.glGetInteger(32876), GL11.glGetInteger(32875), GL11.glGetInteger(3333)};
      }

      private static IndexedBufferState[] captureIndexedBuffers(int bindingTarget, int startTarget, int sizeTarget, int maximumTarget) {
         int count = Math.min(12, Math.max(0, GL11.glGetInteger(maximumTarget)));
         IndexedBufferState[] bindings = new IndexedBufferState[count];

         for(int index = 0; index < count; ++index) {
            bindings[index] = new IndexedBufferState(GL30.glGetIntegeri(bindingTarget, index), GL32.glGetInteger64i(startTarget, index), GL32.glGetInteger64i(sizeTarget, index));
         }

         return bindings;
      }

      private static int[] captureUnpackStore() {
         return new int[]{GL11.glGetInteger(3312), GL11.glGetInteger(3313), GL11.glGetInteger(3314), GL11.glGetInteger(3315), GL11.glGetInteger(3316), GL11.glGetInteger(32878), GL11.glGetInteger(32877), GL11.glGetInteger(3317)};
      }

      private static StencilFaceState captureStencilFace(boolean back) {
         return new StencilFaceState(GL11.glGetInteger(back ? '蠀' : 2962), GL11.glGetInteger(back ? '貣' : 2967), GL11.glGetInteger(back ? '貤' : 2963), GL11.glGetInteger(back ? '貥' : 2968), GL11.glGetInteger(back ? '蠁' : 2964), GL11.glGetInteger(back ? '蠂' : 2965), GL11.glGetInteger(back ? '蠃' : 2966));
      }

      private void restore() {
         GL30.glBindFramebuffer(36009, this.drawFramebuffer);
         GL30.glBindFramebuffer(36008, this.readFramebuffer);
         GL20.glUseProgram(this.program);
         GL30.glBindVertexArray(this.vertexArray);
         GL15.glBindBuffer(34962, this.arrayBuffer);
         GL15.glBindBuffer(34963, this.elementArrayBuffer);
         GL15.glBindBuffer(35051, this.pixelPackBuffer);
         GL15.glBindBuffer(35052, this.pixelUnpackBuffer);
         this.restorePixelStore();

         for(int unit = 0; unit < this.texture2d.length; ++unit) {
            GL13.glActiveTexture('蓀' + unit);
            GL11.glBindTexture(3553, this.texture2d[unit]);
            GL11.glBindTexture(35866, this.texture2dArray[unit]);
            GL33.glBindSampler(unit, this.samplers[unit]);
         }

         GL13.glActiveTexture(this.activeTexture);
         restoreIndexedBuffers(35345, this.uniformBindings);
         GL15.glBindBuffer(35345, this.uniformBuffer);
         GL11.glViewport(this.viewport[0], this.viewport[1], this.viewport[2], this.viewport[3]);
         GL11.glScissor(this.scissorBox[0], this.scissorBox[1], this.scissorBox[2], this.scissorBox[3]);
         GL14.glBlendFuncSeparate(this.blendSrcRgb, this.blendDstRgb, this.blendSrcAlpha, this.blendDstAlpha);
         GL20.glBlendEquationSeparate(this.blendEquationRgb, this.blendEquationAlpha);
         GL11.glDepthMask(this.depthMask);
         GL11.glDepthFunc(this.depthFunc);
         GL11.glCullFace(this.cullFaceMode);
         GL11.glFrontFace(this.frontFace);
         GL11.glClearStencil(this.stencilClearValue);
         restoreStencilFace(1028, this.frontStencil);
         restoreStencilFace(1029, this.backStencil);
         GL11.glColorMask(this.colorMask[0], this.colorMask[1], this.colorMask[2], this.colorMask[3]);
         SkijaRenderer.setEnabled(3042, this.blend);
         SkijaRenderer.setEnabled(2929, this.depthTest);
         SkijaRenderer.setEnabled(3089, this.scissorTest);
         SkijaRenderer.setEnabled(2960, this.stencilTest);
         SkijaRenderer.setEnabled(2884, this.cullFace);
         SkijaRenderer.setEnabled(36281, this.framebufferSrgb);
      }

      private static void restoreIndexedBuffers(int target, IndexedBufferState[] bindings) {
         for(int index = 0; index < bindings.length; ++index) {
            IndexedBufferState binding = bindings[index];
            if (binding.buffer() == 0) {
               GL30.glBindBufferBase(target, index, 0);
            } else if (binding.size() > 0L) {
               GL30.glBindBufferRange(target, index, binding.buffer(), binding.start(), binding.size());
            } else {
               GL30.glBindBufferBase(target, index, binding.buffer());
            }
         }

      }

      private static void restoreStencilFace(int face, StencilFaceState state) {
         GL20.glStencilFuncSeparate(face, state.function(), state.reference(), state.valueMask());
         GL20.glStencilMaskSeparate(face, state.writeMask());
         GL20.glStencilOpSeparate(face, state.stencilFail(), state.depthFail(), state.depthPass());
      }

      private void restorePixelStore() {
         GL11.glPixelStorei(3328, this.packStore[0]);
         GL11.glPixelStorei(3329, this.packStore[1]);
         GL11.glPixelStorei(3330, this.packStore[2]);
         GL11.glPixelStorei(3331, this.packStore[3]);
         GL11.glPixelStorei(3332, this.packStore[4]);
         GL11.glPixelStorei(32876, this.packStore[5]);
         GL11.glPixelStorei(32875, this.packStore[6]);
         GL11.glPixelStorei(3333, this.packStore[7]);
         GL11.glPixelStorei(3312, this.unpackStore[0]);
         GL11.glPixelStorei(3313, this.unpackStore[1]);
         GL11.glPixelStorei(3314, this.unpackStore[2]);
         GL11.glPixelStorei(3315, this.unpackStore[3]);
         GL11.glPixelStorei(3316, this.unpackStore[4]);
         GL11.glPixelStorei(32878, this.unpackStore[5]);
         GL11.glPixelStorei(32877, this.unpackStore[6]);
         GL11.glPixelStorei(3317, this.unpackStore[7]);
      }
   }

   private static record StencilFaceState(int function, int reference, int valueMask, int writeMask, int stencilFail, int depthFail, int depthPass) {
   }

   private static record IndexedBufferState(int buffer, long start, long size) {
   }

   @FunctionalInterface
   private interface PaintOperation {
      boolean draw();
   }
}
