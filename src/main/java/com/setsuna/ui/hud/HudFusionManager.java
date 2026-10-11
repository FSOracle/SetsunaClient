package com.setsuna.ui.hud;

import com.setsuna.event.Listen;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.module.Module;
import com.setsuna.module.ModuleManager;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.render.SkijaUi;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.skija.PathDirection;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class HudFusionManager {
   public static final HudFusionManager INSTANCE = new HudFusionManager();
   private static final float TOUCH_TOLERANCE = 4.0F;
   private final Set<HUDModuel> fusedThisFrame = Collections.newSetFromMap(new IdentityHashMap());
   private final IdentityHashMap<HUDModuel, FusionSelection> selectionsThisFrame = new IdentityHashMap();
   private final IdentityHashMap<HUDModuel, AnimatedCell> animatedCells = new IdentityHashMap();
   private final Set<HUDModuel> lockedAttachments = Collections.newSetFromMap(new IdentityHashMap());
   private final Set<HUDModuel> detachedAttachments = Collections.newSetFromMap(new IdentityHashMap());
   private Render2DEvent preparedEvent;

   private HudFusionManager() {
   }

   @Listen(
      priority = 200
   )
   private void renderFusedGroups(Render2DEvent event) {
      this.preparedEvent = event;
      this.fusedThisFrame.clear();
      this.selectionsThisFrame.clear();
      Set<HUDModuel> active = Collections.newSetFromMap(new IdentityHashMap());

      for(Group group : this.groups(event.width(), event.height())) {
         this.fusedThisFrame.addAll(group.members());
         active.addAll(group.members());
         FusionSelection selection = this.layout(group, event.width(), event.height());

         for(HUDModuel member : group.members()) {
            this.selectionsThisFrame.put(member, selection);
         }

         drawGroup(event.canvas(), group, selection);
      }

      this.animatedCells.keySet().removeIf((module) -> !active.contains(module));
   }

   static boolean isFused(HUDModuel module, Render2DEvent event) {
      return INSTANCE.preparedEvent == event && INSTANCE.fusedThisFrame.contains(module);
   }

   static FusionSelection selection(HUDModuel module, float screenWidth, float screenHeight) {
      for(Group group : INSTANCE.groups(screenWidth, screenHeight)) {
         if (group.members().contains(module)) {
            return INSTANCE.layout(group, screenWidth, screenHeight);
         }
      }

      Rect bounds = Rect.makeXYWH(module.hudX(screenWidth), module.hudY(screenHeight), module.hudWidth(screenWidth), module.hudHeight(screenHeight));
      return new FusionSelection(module, List.of(module), List.of(new FusionCell(module, bounds)), bounds, false);
   }

   static Rect contentBounds(HUDModuel module, Render2DEvent event) {
      if (INSTANCE.preparedEvent == event) {
         FusionSelection selection = INSTANCE.selectionsThisFrame.get(module);
         // A missing entry means this HUD was not fused in this frame. Its
         // render hook may have updated its dimensions, so use the live bounds
         // without rebuilding every fusion group for each standalone HUD.
         return selection != null ? selection.cellBounds(module)
               : Rect.makeXYWH(module.hudX(event.width()), module.hudY(event.height()),
                     module.hudWidth(event.width()), module.hudHeight(event.height()));
      }

      return selection(module, event.width(), event.height()).cellBounds(module);
   }

   static Edges edges(HUDModuel module, float screenWidth, float screenHeight) {
      for(Group group : INSTANCE.groups(screenWidth, screenHeight)) {
         if (group.members().contains(module)) {
            return attachedEdges(module, group.members(), screenWidth, screenHeight);
         }
      }

      return HudFusionManager.Edges.NONE;
   }

   static void detach(List<HUDModuel> members) {
      INSTANCE.lockedAttachments.removeAll(members);

      for(HUDModuel member : members) {
         INSTANCE.animatedCells.remove(member);
         if (watermarkAttachment(member)) {
            INSTANCE.detachedAttachments.add(member);
         }
      }

   }

   static void armFusion(HUDModuel module) {
      INSTANCE.detachedAttachments.remove(module);
   }

   public static void resetAttachmentMemory() {
      INSTANCE.lockedAttachments.clear();
      INSTANCE.detachedAttachments.clear();
      INSTANCE.animatedCells.clear();
      INSTANCE.fusedThisFrame.clear();
      INSTANCE.selectionsThisFrame.clear();
      INSTANCE.preparedEvent = null;
   }

   public static List<String> capturePresetAttachments(float screenWidth, float screenHeight) {
      List<String> result = new ArrayList();

      for(Group group : INSTANCE.groups(screenWidth, screenHeight)) {
         if (group.members().contains(WatermarkHUD.INSTANCE)) {
            for(HUDModuel member : group.members()) {
               if (watermarkAttachment(member)) {
                  result.add(member.id());
               }
            }
         }
      }

      result.sort(String.CASE_INSENSITIVE_ORDER);
      return List.copyOf(result);
   }

   public static void restorePresetAttachments(Collection<String> moduleIds) {
      Set<String> selected = moduleIds == null ? Set.of() : (Set)moduleIds.stream().filter((id) -> id != null && !id.isBlank()).collect(Collectors.toSet());
      INSTANCE.lockedAttachments.clear();
      INSTANCE.detachedAttachments.clear();
      INSTANCE.animatedCells.clear();

      for(Module module : ModuleManager.INSTANCE.modules()) {
         if (module instanceof HUDModuel hud) {
            if (watermarkAttachment(hud)) {
               if (selected.contains(hud.id())) {
                  INSTANCE.lockedAttachments.add(hud);
               } else {
                  INSTANCE.detachedAttachments.add(hud);
               }
            }
         }
      }

   }

   private static void drawGroup(Canvas canvas, Group group, FusionSelection selection) {
      HUDModuel owner = group.owner();
      FusionStyle style = owner.hudFusionStyle();
      if (style != null) {
         Rect bounds = selection.bounds();
         Path shape = groupPath(bounds, style.radius());

         label147: {
            try {
               if (shape == null) {
                  break label147;
               }

               int layer = owner.opacityAlpha() >= 255 ? canvas.save() : canvas.saveLayerAlpha(bounds, owner.opacityAlpha());

               try {
                  float blurStrength = owner.hudBlurStrength(style.blur(), style.blurStrength(),
                        style.background(), style.backgroundColor());
                  if (blurStrength > 0.01F) {
                     SkijaRenderer.drawGaussianBackdrop(canvas, shape, bounds.getLeft(), bounds.getTop(), bounds.getWidth(), bounds.getHeight(), blurStrength);
                  }

                  if (style.shadow()) {
                     SkijaUi.dropShadowPath(canvas, shape, style.shadowStrength(), -1677721600);
                  }

                  if (style.background()) {
                     HudRenderUtil.coloredPath(canvas, shape, style.backgroundColor());
                  }

                  if (selection.compactStrip()) {
                     drawSeparators(canvas, selection);
                  }

                  if (style.border()) {
                     HudRenderUtil.borderPath(canvas, shape, bounds, style.borderWidth(), style.borderMode(), style.borderColor(), style.borderStart(), style.borderEnd(), 255);
                  }
               } finally {
                  canvas.restoreToCount(layer);
               }
            } catch (Throwable var14) {
               if (shape != null) {
                  try {
                     shape.close();
                  } catch (Throwable var12) {
                     var14.addSuppressed(var12);
                  }
               }

               throw var14;
            }

            if (shape != null) {
               shape.close();
            }

            return;
         }

         if (shape != null) {
            shape.close();
         }

      }
   }

   private static void drawSeparators(Canvas canvas, FusionSelection selection) {
      float top = selection.bounds().getTop();
      float height = selection.bounds().getHeight();
      int color = UiTheme.withAlpha(UiTheme.TEXT_FAINT, 104);
      float fontSize = barFontSize();
      float separatorWidth = SkijaUi.textWidth("|", fontSize);

      for(int index = 1; index < selection.cells().size(); ++index) {
         float x = ((FusionCell)selection.cells().get(index)).bounds().getLeft();
         SkijaUi.text(canvas, "|", x - separatorWidth * 0.5F, top, height, color, fontSize);
      }

   }

   private List<Group> groups(float screenWidth, float screenHeight) {
      if (!WatermarkHUD.INSTANCE.allowsHudFusion()) {
         this.lockedAttachments.clear();
      }

      List<HUDModuel> candidates = candidates();
      this.lockedAttachments.retainAll(candidates);
      this.detachedAttachments.retainAll(candidates);
      Set<HUDModuel> visited = Collections.newSetFromMap(new IdentityHashMap());
      List<Group> result = new ArrayList();

      for(HUDModuel candidate : candidates) {
         if (visited.add(candidate)) {
            List<HUDModuel> members = new ArrayList();
            ArrayDeque<HUDModuel> pending = new ArrayDeque();
            pending.add(candidate);

            while(!pending.isEmpty()) {
               HUDModuel current = (HUDModuel)pending.removeFirst();
               members.add(current);

               for(HUDModuel other : candidates) {
                  if (!visited.contains(other) && !this.fusionSuppressed(current, other) && (touches(current, other, screenWidth, screenHeight) || this.lockedTogether(current, other))) {
                     visited.add(other);
                     pending.addLast(other);
                  }
               }
            }

            if (members.size() > 1 && members.contains(WatermarkHUD.INSTANCE)) {
               for(HUDModuel member : members) {
                  if (watermarkAttachment(member)) {
                     this.lockedAttachments.add(member);
                     this.detachedAttachments.remove(member);
                  }
               }

               result.add(new Group(List.copyOf(members), WatermarkHUD.INSTANCE));
            }
         }
      }

      return result;
   }

   private boolean lockedTogether(HUDModuel first, HUDModuel second) {
      return first == WatermarkHUD.INSTANCE && this.lockedAttachments.contains(second) || second == WatermarkHUD.INSTANCE && this.lockedAttachments.contains(first);
   }

   private boolean fusionSuppressed(HUDModuel first, HUDModuel second) {
      return this.detachedAttachments.contains(first) || this.detachedAttachments.contains(second);
   }

   private static List<HUDModuel> candidates() {
      List<HUDModuel> result = new ArrayList();

      for(Module module : ModuleManager.INSTANCE.modules()) {
         if (module instanceof HUDModuel hud) {
            if (hud.isEnabled() && compactReadout(hud) && hud.supportsHudFusion() && hud.hudFusionStyle() != null) {
               if (hud instanceof WatermarkHUD) {
                  WatermarkHUD watermark = (WatermarkHUD)hud;
                  if (!watermark.allowsHudFusion()) {
                     continue;
                  }
               }

               result.add(hud);
            }
         }
      }

      return result;
   }

   private static Path groupPath(Rect bounds, float radius) {
      float safeRadius = Math.max(0.0F, Math.min(radius, Math.min(bounds.getWidth(), bounds.getHeight()) * 0.5F));
      PathBuilder builder = new PathBuilder();

      Path var4;
      try {
         builder.addRRect(RRect.makeXYWH(bounds.getLeft(), bounds.getTop(), bounds.getWidth(), bounds.getHeight(), safeRadius), PathDirection.CLOCKWISE, 0);
         var4 = builder.detach();
      } catch (Throwable var7) {
         try {
            builder.close();
         } catch (Throwable var6) {
            var7.addSuppressed(var6);
         }

         throw var7;
      }

      builder.close();
      return var4;
   }

   private FusionSelection layout(Group group, float screenWidth, float screenHeight) {
      boolean compactStrip = group.members().stream().allMatch(HudFusionManager::compactReadout);
      if (!compactStrip) {
         List<FusionCell> cells = new ArrayList();

         for(HUDModuel member : group.members()) {
            cells.add(new FusionCell(member, joinedBounds(member, group.members(), screenWidth, screenHeight)));
         }

         return new FusionSelection(group.owner(), group.members(), List.copyOf(cells), groupBounds(group.members(), screenWidth, screenHeight), false);
      } else {
         List<HUDModuel> ordered = new ArrayList(group.members());
         float minX = Float.POSITIVE_INFINITY;
         float maxX = Float.NEGATIVE_INFINITY;
         float minY = Float.POSITIVE_INFINITY;
         float maxY = Float.NEGATIVE_INFINITY;

         for(HUDModuel member : ordered) {
            float x = member.hudX(screenWidth);
            float y = member.hudY(screenHeight);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
         }

         Comparator<HUDModuel> order = maxX - minX >= maxY - minY ? Comparator.comparingDouble((module) -> (double)module.hudX(screenWidth)) : Comparator.comparingDouble((module) -> (double)module.hudY(screenHeight));
         ordered.sort(order.thenComparing(Module::name));
         float stripHeight = barHeight();
         List<FusionCell> targets = new ArrayList();
         float cursor = minX;

         for(HUDModuel member : ordered) {
            float cellWidth = member.hudFusionContentWidth(screenWidth);
            Rect cell = Rect.makeXYWH(cursor, minY, cellWidth, stripHeight);
            targets.add(new FusionCell(member, cell));
            cursor += cellWidth;
         }

         List<FusionCell> cells = new ArrayList();

         for(FusionCell target : targets) {
            Rect initial = Rect.makeXYWH(target.module().hudX(screenWidth), target.module().hudY(screenHeight), target.module().hudWidth(screenWidth), target.module().hudHeight(screenHeight));
            Rect animated = ((AnimatedCell)this.animatedCells.computeIfAbsent(target.module(), (ignored) -> new AnimatedCell(initial))).approach(target.bounds());
            cells.add(new FusionCell(target.module(), animated));
         }

         Rect bounds = unionBounds(cells);
         return new FusionSelection(group.owner(), List.copyOf(ordered), List.copyOf(cells), bounds, true);
      }
   }

   static float barScale() {
      return ((Double)WatermarkHUD.INSTANCE.scale.get()).floatValue();
   }

   static float barFontSize() {
      return 9.0F * barScale();
   }

   static float barHeight() {
      return 20.0F * barScale();
   }

   static float barPadding() {
      return 5.0F * barScale();
   }

   private static Rect unionBounds(List<FusionCell> cells) {
      float left = Float.POSITIVE_INFINITY;
      float top = Float.POSITIVE_INFINITY;
      float right = Float.NEGATIVE_INFINITY;
      float bottom = Float.NEGATIVE_INFINITY;

      for(FusionCell cell : cells) {
         left = Math.min(left, cell.bounds().getLeft());
         top = Math.min(top, cell.bounds().getTop());
         right = Math.max(right, cell.bounds().getRight());
         bottom = Math.max(bottom, cell.bounds().getBottom());
      }

      return Rect.makeLTRB(left, top, right, bottom);
   }

   private static Rect joinedBounds(HUDModuel module, List<HUDModuel> members, float screenWidth, float screenHeight) {
      float left = module.hudX(screenWidth);
      float top = module.hudY(screenHeight);
      float right = left + module.hudWidth(screenWidth);
      float bottom = top + module.hudHeight(screenHeight);

      for(HUDModuel other : members) {
         if (other != module) {
            float otherLeft = other.hudX(screenWidth);
            float otherTop = other.hudY(screenHeight);
            float otherRight = otherLeft + other.hudWidth(screenWidth);
            float otherBottom = otherTop + other.hudHeight(screenHeight);
            if (sameSpan(top, bottom, otherTop, otherBottom)) {
               if (close(right, otherLeft)) {
                  right = Math.max(right, otherLeft);
               }

               if (close(left, otherRight)) {
                  left = Math.min(left, otherRight);
               }
            }

            if (sameSpan(left, right, otherLeft, otherRight)) {
               if (close(bottom, otherTop)) {
                  bottom = Math.max(bottom, otherTop);
               }

               if (close(top, otherBottom)) {
                  top = Math.min(top, otherBottom);
               }
            }
         }
      }

      return Rect.makeLTRB(left, top, right, bottom);
   }

   private static Rect groupBounds(List<HUDModuel> members, float screenWidth, float screenHeight) {
      float left = Float.POSITIVE_INFINITY;
      float top = Float.POSITIVE_INFINITY;
      float right = Float.NEGATIVE_INFINITY;
      float bottom = Float.NEGATIVE_INFINITY;

      for(HUDModuel member : members) {
         Rect bounds = joinedBounds(member, members, screenWidth, screenHeight);
         left = Math.min(left, bounds.getLeft());
         top = Math.min(top, bounds.getTop());
         right = Math.max(right, bounds.getRight());
         bottom = Math.max(bottom, bounds.getBottom());
      }

      return Rect.makeLTRB(left, top, right, bottom);
   }

   private static Edges attachedEdges(HUDModuel module, List<HUDModuel> members, float screenWidth, float screenHeight) {
      float x = module.hudX(screenWidth);
      float y = module.hudY(screenHeight);
      float width = module.hudWidth(screenWidth);
      float height = module.hudHeight(screenHeight);
      boolean left = false;
      boolean right = false;
      boolean top = false;
      boolean bottom = false;

      for(HUDModuel other : members) {
         if (other != module) {
            float otherX = other.hudX(screenWidth);
            float otherY = other.hudY(screenHeight);
            float otherWidth = other.hudWidth(screenWidth);
            float otherHeight = other.hudHeight(screenHeight);
            if (close(y, otherY) && close(height, otherHeight)) {
               left |= close(x, otherX + otherWidth);
               right |= close(x + width, otherX);
            }

            if (close(x, otherX) && close(width, otherWidth)) {
               top |= close(y, otherY + otherHeight);
               bottom |= close(y + height, otherY);
            }
         }
      }

      return new Edges(left, right, top, bottom);
   }

   private static boolean touches(HUDModuel first, HUDModuel second, float screenWidth, float screenHeight) {
      float x = first.hudX(screenWidth);
      float y = first.hudY(screenHeight);
      float width = first.hudWidth(screenWidth);
      float height = first.hudHeight(screenHeight);
      float otherX = second.hudX(screenWidth);
      float otherY = second.hudY(screenHeight);
      float otherWidth = second.hudWidth(screenWidth);
      float otherHeight = second.hudHeight(screenHeight);
      boolean compactPair = compactReadout(first) && compactReadout(second);
      boolean horizontal = close(y, otherY) && (close(height, otherHeight) || compactPair) && (close(x, otherX + otherWidth) || close(x + width, otherX));
      boolean vertical = close(x, otherX) && (close(width, otherWidth) || compactPair) && (close(y, otherY + otherHeight) || close(y + height, otherY));
      return horizontal || vertical;
   }

   static boolean compactReadout(HUDModuel module) {
      return module instanceof FPSHUD || module instanceof BPSHUD || module instanceof CoordinatesHUD || module instanceof SessionInfoHUD || module instanceof WatermarkHUD;
   }

   static boolean watermarkAttachment(HUDModuel module) {
      return module instanceof FPSHUD || module instanceof BPSHUD || module instanceof CoordinatesHUD || module instanceof SessionInfoHUD;
   }

   private static boolean sameSpan(float start, float end, float otherStart, float otherEnd) {
      return close(start, otherStart) && close(end - start, otherEnd - otherStart);
   }

   private static boolean close(float first, float second) {
      return Math.abs(first - second) <= 4.0F;
   }

   public static record FusionStyle(boolean background, int backgroundColor, boolean blur, float blurStrength, boolean shadow, float shadowStrength, boolean border, float radius, float borderWidth, HudRenderUtil.BorderMode borderMode, int borderColor, int borderStart, int borderEnd) {
   }

   static record FusionSelection(HUDModuel owner, List<HUDModuel> members, List<FusionCell> cells, Rect bounds, boolean compactStrip) {
      boolean fused() {
         return this.members.size() > 1;
      }

      Rect cellBounds(HUDModuel module) {
         for(FusionCell cell : this.cells) {
            if (cell.module() == module) {
               return cell.bounds();
            }
         }

         return this.bounds;
      }
   }

   private static record FusionCell(HUDModuel module, Rect bounds) {
   }

   private static record Group(List<HUDModuel> members, HUDModuel owner) {
   }

   private static final class AnimatedCell {
      private float left;
      private float top;
      private float right;
      private float bottom;
      private long lastUpdateNanos = System.nanoTime();

      private AnimatedCell(Rect initial) {
         this.left = initial.getLeft();
         this.top = initial.getTop();
         this.right = initial.getRight();
         this.bottom = initial.getBottom();
      }

      private Rect approach(Rect target) {
         long now = System.nanoTime();
         float delta = Math.min(0.08F, Math.max(0.0F, (float)(now - this.lastUpdateNanos) / 1.0E9F));
         this.lastUpdateNanos = now;
         float amount = 1.0F - (float)Math.exp((double)(-14.0F * delta));
         this.left += (target.getLeft() - this.left) * amount;
         this.top += (target.getTop() - this.top) * amount;
         this.right += (target.getRight() - this.right) * amount;
         this.bottom += (target.getBottom() - this.bottom) * amount;
         if (Math.abs(target.getLeft() - this.left) < 0.02F) {
            this.left = target.getLeft();
         }

         if (Math.abs(target.getTop() - this.top) < 0.02F) {
            this.top = target.getTop();
         }

         if (Math.abs(target.getRight() - this.right) < 0.02F) {
            this.right = target.getRight();
         }

         if (Math.abs(target.getBottom() - this.bottom) < 0.02F) {
            this.bottom = target.getBottom();
         }

         return Rect.makeLTRB(this.left, this.top, this.right, this.bottom);
      }
   }

   static record Edges(boolean left, boolean right, boolean top, boolean bottom) {
      static final Edges NONE = new Edges(false, false, false, false);

      boolean any() {
         return this.left || this.right || this.top || this.bottom;
      }
   }
}
